package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OwnerRepository
import com.example.data.PreferencesManager
import com.example.model.ActivityLog
import com.example.model.BackupRecord
import com.example.model.BusinessConfig
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.Product
import com.example.model.ReleaseItem
import com.example.model.StaffUser
import com.example.model.Supplier
import com.example.model.UpgradeHistory
import com.example.util.SecurityUtils
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OwnerAdminState(
    val staffUsers: List<StaffUser> = emptyList(),
    val activityLogs: List<ActivityLog> = emptyList(),
    val backups: List<BackupRecord> = emptyList(),
    val upgradeHistory: List<UpgradeHistory> = emptyList(),
    val releaseCatalog: List<ReleaseItem> = emptyList()
)

class OwnerAdminViewModel(
    private val ownerRepo: OwnerRepository,
    private val prefs: PreferencesManager,
    private val currentUserId: String
) : ViewModel() {

    private val _isOwnerUnlocked = MutableStateFlow(false)
    val isOwnerUnlocked: StateFlow<Boolean> = _isOwnerUnlocked.asStateFlow()

    private val _pendingTwoFactorChallenge = MutableStateFlow(false)
    val pendingTwoFactorChallenge: StateFlow<Boolean> = _pendingTwoFactorChallenge.asStateFlow()

    private val _generatedTwoFactorOtp = MutableStateFlow<String?>(null)
    val generatedTwoFactorOtp: StateFlow<String?> = _generatedTwoFactorOtp.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.ownerSessionExpiryFlow.collect { expiry ->
                if (expiry > System.currentTimeMillis()) {
                    _isOwnerUnlocked.value = true
                }
            }
        }
    }

    private fun <T> Flow<T>.wrapStream(name: String): Flow<T> = catch { error ->
        val fsErr = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsErr == null && error is CancellationException) throw error
        Log.w(TAG, "Stream error in $name", fsErr ?: error)
        throw (fsErr ?: error)
    }

    val adminData: StateFlow<OwnerAdminState> = combine(
        ownerRepo.observeStaffUsers(currentUserId).wrapStream("staff"),
        ownerRepo.observeActivityLogs(currentUserId).wrapStream("logs"),
        ownerRepo.observeBackups(currentUserId).wrapStream("backups"),
        ownerRepo.observeUpgradeHistory(currentUserId).wrapStream("upgrades"),
        ownerRepo.observeReleaseCatalog(currentUserId).wrapStream("releases")
    ) { staff, logs, backups, history, releases ->
        OwnerAdminState(
            staffUsers = staff,
            activityLogs = logs,
            backups = backups,
            upgradeHistory = history,
            releaseCatalog = releases
        )
    }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "AdminData stream error", fsErr ?: error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = OwnerAdminState()
        )

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun attemptOwnerLogin(password: String, config: BusinessConfig) {
        val verified = SecurityUtils.verifySecret(
            input = password,
            salt = config.ownerPasswordSalt,
            expectedHash = config.ownerPasswordHash
        )
        if (!verified) {
            viewModelScope.launch {
                ownerRepo.logActivity("Unknown", "Failed Owner Login", "Invalid owner password attempt", "SECURITY")
            }
            _statusMessage.value = "Incorrect Owner password. Please try again."
            return
        }

        if (config.twoFactorEnabled) {
            val otp = SecurityUtils.generateTwoFactorCode()
            _generatedTwoFactorOtp.value = otp
            _pendingTwoFactorChallenge.value = true
            viewModelScope.launch {
                ownerRepo.updateOwnerSecurityCredentials(
                    twoFactorEnabled = true,
                    twoFactorCode = otp
                )
            }
        } else {
            grantOwnerSession()
        }
    }

    fun verifyTwoFactorCode(inputCode: String, config: BusinessConfig) {
        val expected = _generatedTwoFactorOtp.value ?: config.twoFactorCode
        if (!expected.isNullOrBlank() && inputCode.trim() == expected) {
            _pendingTwoFactorChallenge.value = false
            _generatedTwoFactorOtp.value = null
            grantOwnerSession()
        } else {
            _statusMessage.value = "Invalid 2FA verification code."
        }
    }

    private fun grantOwnerSession() {
        _isOwnerUnlocked.value = true
        viewModelScope.launch {
            // 30-minute active owner session
            prefs.setOwnerSessionExpiry(System.currentTimeMillis() + 30 * 60 * 1000L)
            ownerRepo.logActivity("Owner", "Owner Dashboard Unlocked", "Authenticated owner session started", "SECURITY")
        }
    }

    fun lockOwnerSession() {
        _isOwnerUnlocked.value = false
        _pendingTwoFactorChallenge.value = false
        _generatedTwoFactorOtp.value = null
        viewModelScope.launch {
            prefs.setOwnerSessionExpiry(0L)
            ownerRepo.logActivity("Owner", "Owner Session Locked", "Manual session lock", "SECURITY")
        }
    }

    fun recoverPasswordWithPin(
        recoveryPin: String,
        newPassword: String,
        config: BusinessConfig,
        onRecovered: () -> Unit
    ) {
        val storedPinHash = config.recoveryPinHash
        if (storedPinHash.isNullOrBlank()) {
            _statusMessage.value = "No Recovery PIN set yet. Sign in with your current password and configure a Recovery PIN in Security Settings."
            return
        }
        val pinValid = SecurityUtils.verifySecret(
            input = recoveryPin.trim(),
            salt = SecurityUtils.INITIAL_OWNER_SALT,
            expectedHash = storedPinHash
        )
        if (!pinValid) {
            _statusMessage.value = "Recovery PIN did not match."
            return
        }
        if (newPassword.length < 6) {
            _statusMessage.value = "New password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.updateOwnerSecurityCredentials(
                newPassword = newPassword,
                twoFactorEnabled = config.twoFactorEnabled
            ).onSuccess {
                _statusMessage.value = "Owner password recovered and updated!"
                onRecovered()
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Failed to reset password"
            }
            _isProcessing.value = false
        }
    }

    fun changeOwnerPassword(
        currentPassword: String,
        newPassword: String,
        config: BusinessConfig,
        onChanged: () -> Unit = {}
    ) {
        if (!SecurityUtils.verifySecret(currentPassword, config.ownerPasswordSalt, config.ownerPasswordHash)) {
            _statusMessage.value = "Current password is incorrect."
            return
        }
        if (newPassword.length < 6) {
            _statusMessage.value = "New password must be at least 6 characters."
            return
        }
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.updateOwnerSecurityCredentials(
                newPassword = newPassword,
                twoFactorEnabled = config.twoFactorEnabled
            ).onSuccess {
                _statusMessage.value = "Owner password updated securely!"
                onChanged()
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Password update failed"
            }
            _isProcessing.value = false
        }
    }

    fun saveSecurityOptions(
        recoveryPin: String?,
        recoveryHint: String?,
        twoFactorEnabled: Boolean
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.updateOwnerSecurityCredentials(
                recoveryPin = recoveryPin?.takeIf { it.isNotBlank() },
                recoveryHint = recoveryHint?.takeIf { it.isNotBlank() },
                twoFactorEnabled = twoFactorEnabled
            ).onSuccess {
                _statusMessage.value = "Security preferences saved."
            }.onFailure {
                _statusMessage.value = it.localizedMessage ?: "Failed to update security settings"
            }
            _isProcessing.value = false
        }
    }

    fun saveBusinessConfig(config: BusinessConfig) {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.updateBusinessSettings(config)
                .onSuccess { _statusMessage.value = "Business & feature settings saved!" }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Update failed" }
            _isProcessing.value = false
        }
    }

    fun saveStaff(staff: StaffUser, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.saveStaffUser(staff)
                .onSuccess {
                    _statusMessage.value = "Staff member '${staff.name}' saved."
                    onDone()
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to save staff" }
            _isProcessing.value = false
        }
    }

    fun deleteStaff(staff: StaffUser) {
        viewModelScope.launch {
            ownerRepo.deleteStaffUser(staff.id)
                .onSuccess { _statusMessage.value = "Removed '${staff.name}'." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Failed to remove staff" }
        }
    }

    fun createBackup(
        label: String,
        config: BusinessConfig,
        products: List<Product>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        expenses: List<Expense>
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.createCloudBackup(label, config, products, customers, suppliers, expenses)
                .onSuccess { bkp ->
                    _statusMessage.value = "Cloud backup '${bkp.label}' saved (${bkp.recordCount} records)."
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Backup failed" }
            _isProcessing.value = false
        }
    }

    fun restoreBackup(backup: BackupRecord) {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.restoreFromBackup(backup.payloadJson)
                .onSuccess { count ->
                    _statusMessage.value = "Restored $count records from '${backup.label}'."
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Restore failed" }
            _isProcessing.value = false
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.checkForUpdatesFromServer()
                .onSuccess { releases ->
                    val latest = releases.firstOrNull()?.version ?: "2.4.0"
                    _statusMessage.value = "Update check complete. Latest available release: v$latest"
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Update check failed" }
            _isProcessing.value = false
        }
    }

    fun oneClickUpgrade(
        target: ReleaseItem,
        config: BusinessConfig,
        products: List<Product>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        expenses: List<Expense>
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.executeOneClickUpgrade(target, config, products, customers, suppliers, expenses)
                .onSuccess {
                    _statusMessage.value = "Upgraded to v${target.version} with automatic pre-update backup!"
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Upgrade failed" }
            _isProcessing.value = false
        }
    }

    fun rollbackUpgrade(history: UpgradeHistory, backups: List<BackupRecord>) {
        viewModelScope.launch {
            _isProcessing.value = true
            ownerRepo.rollbackToPreviousVersion(history, backups)
                .onSuccess {
                    _statusMessage.value = "Restored previous version v${history.fromVersion} safely."
                }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Rollback failed" }
            _isProcessing.value = false
        }
    }

    fun updateUpgradeControls(autoUpdate: Boolean, maintenanceMode: Boolean) {
        viewModelScope.launch {
            ownerRepo.updateUpgradeToggles(autoUpdate, maintenanceMode)
                .onSuccess { _statusMessage.value = "Upgrade & maintenance controls updated." }
                .onFailure { _statusMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    private companion object {
        const val TAG = "OwnerAdminVM"
    }
}
