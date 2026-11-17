package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.BackupRecord
import com.example.model.BusinessConfig
import com.example.model.ReleaseItem
import com.example.model.StaffUser
import com.example.model.UpgradeHistory
import com.example.ui.theme.MonoFontFamily
import com.example.viewmodel.BillingWorkspaceData
import com.example.viewmodel.OwnerAdminState

@Composable
fun OwnerAdminScreen(
    workspace: BillingWorkspaceData,
    adminData: OwnerAdminState,
    userEmail: String,
    isUnlocked: Boolean,
    pendingTwoFactor: Boolean,
    generatedOtp: String?,
    statusMessage: String?,
    isProcessing: Boolean,
    onAttemptLogin: (String, BusinessConfig) -> Unit,
    onVerifyTwoFactor: (String, BusinessConfig) -> Unit,
    onLockSession: () -> Unit,
    onRecoverPassword: (pin: String, newPw: String, () -> Unit) -> Unit,
    onChangePassword: (current: String, next: String, () -> Unit) -> Unit,
    onSaveSecurityOptions: (pin: String?, hint: String?, twoFactor: Boolean) -> Unit,
    onSaveBusinessConfig: (BusinessConfig) -> Unit,
    onSaveStaff: (StaffUser, () -> Unit) -> Unit,
    onDeleteStaff: (StaffUser) -> Unit,
    onCreateBackup: (String) -> Unit,
    onRestoreBackup: (BackupRecord) -> Unit,
    onCheckForUpdates: () -> Unit,
    onOneClickUpgrade: (ReleaseItem) -> Unit,
    onRollbackUpgrade: (UpgradeHistory) -> Unit,
    onUpdateUpgradeControls: (autoUpdate: Boolean, maintenance: Boolean) -> Unit,
    onSignOutGoogle: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val config = workspace.config
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    if (!isUnlocked) {
        OwnerLoginGate(
            config = config,
            pendingTwoFactor = pendingTwoFactor,
            generatedOtp = generatedOtp,
            statusMessage = statusMessage,
            isProcessing = isProcessing,
            onAttemptLogin = { pw -> onAttemptLogin(pw, config) },
            onVerifyTwoFactor = { code -> onVerifyTwoFactor(code, config) },
            onRecoverPassword = onRecoverPassword,
            onSignOutGoogle = onSignOutGoogle
        )
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Business & UPI", "Security & Staff", "Upgrade Center", "Backup & Logs")

    Column(modifier = Modifier.fillMaxSize()) {
        // Owner Status Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = "Owner Unlocked", tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Owner Admin Dashboard", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Version ${config.appVersion} • Active Owner Session",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onLockSession,
                    modifier = Modifier.testTag("owner_lock_session_button")
                ) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Lock")
                }
            }
        }

        // First-login Password Change Prompt
        if (config.mustChangeInitialPassword) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.WarningAmber, contentDescription = "Security Action", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(
                            "Security Notice: Change the initial Owner password after your first login.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    TextButton(
                        onClick = { showChangePasswordDialog = true },
                        modifier = Modifier.testTag("prompt_change_initial_pw_button")
                    ) {
                        Text("Change Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> BusinessSettingsTab(
                config = config,
                isProcessing = isProcessing,
                onSaveConfig = onSaveBusinessConfig
            )
            1 -> OwnerSecurityStaffTab(
                config = config,
                adminData = adminData,
                userEmail = userEmail,
                isProcessing = isProcessing,
                onOpenChangePassword = { showChangePasswordDialog = true },
                onSaveSecurityOptions = onSaveSecurityOptions,
                onSaveStaff = onSaveStaff,
                onDeleteStaff = onDeleteStaff,
                onSignOutGoogle = onSignOutGoogle
            )
            2 -> OwnerUpgradeCenterTab(
                workspace = workspace,
                adminData = adminData,
                isProcessing = isProcessing,
                onCheckForUpdates = onCheckForUpdates,
                onOneClickUpgrade = onOneClickUpgrade,
                onRollback = onRollbackUpgrade,
                onUpdateControls = onUpdateUpgradeControls
            )
            else -> OwnerBackupLogsTab(
                adminData = adminData,
                isProcessing = isProcessing,
                onCreateBackup = onCreateBackup,
                onRestoreBackup = onRestoreBackup,
                onShowMessage = onShowMessage
            )
        }
    }

    if (showChangePasswordDialog) {
        ChangeOwnerPasswordDialog(
            isMandatoryInitial = config.mustChangeInitialPassword,
            isProcessing = isProcessing,
            onDismiss = { showChangePasswordDialog = false },
            onChangePassword = { curr, next ->
                onChangePassword(curr, next) {
                    showChangePasswordDialog = false
                }
            }
        )
    }
}

@Composable
private fun OwnerLoginGate(
    config: BusinessConfig,
    pendingTwoFactor: Boolean,
    generatedOtp: String?,
    statusMessage: String?,
    isProcessing: Boolean,
    onAttemptLogin: (String) -> Unit,
    onVerifyTwoFactor: (String) -> Unit,
    onRecoverPassword: (pin: String, newPw: String, () -> Unit) -> Unit,
    onSignOutGoogle: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var showRecoveryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ElevatedCard(
            modifier = Modifier.widthIn(max = 480.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = "Owner Login",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Owner Admin Authentication",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (pendingTwoFactor) {
                        "Two-Factor Verification enabled. Enter the 6-digit security OTP."
                    } else {
                        "Protected area for GST config, UPI settings, staff permissions, backups, and Upgrade Center."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!statusMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (pendingTwoFactor) {
                    if (!generatedOtp.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Device 2FA Security Code: $generatedOtp",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { otpCode = it },
                        label = { Text("Enter 6-digit 2FA Code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_2fa_otp_input")
                    )
                    Button(
                        onClick = { onVerifyTwoFactor(otpCode) },
                        enabled = otpCode.trim().length == 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("owner_verify_2fa_button")
                    ) {
                        Text("Verify & Unlock Dashboard")
                    }
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Owner Dashboard Password") },
                        placeholder = { Text("Enter owner password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_password_input")
                    )
                    Button(
                        onClick = { onAttemptLogin(password) },
                        enabled = password.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("owner_unlock_button")
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock Owner Dashboard")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { showRecoveryDialog = true },
                            modifier = Modifier.testTag("owner_forgot_password_button")
                        ) {
                            Text("Forgot Password?")
                        }
                        TextButton(onClick = onSignOutGoogle) {
                            Text("Switch Google Account")
                        }
                    }
                }
            }
        }
    }

    if (showRecoveryDialog) {
        var pin by remember { mutableStateOf("") }
        var newPw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text("Recover Owner Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!config.recoveryHint.isNullOrBlank()) {
                        Text(
                            "Hint: ${config.recoveryHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it },
                        label = { Text("Recovery PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPw,
                        onValueChange = { newPw = it },
                        label = { Text("New Password (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRecoverPassword(pin, newPw) {
                            showRecoveryDialog = false
                        }
                    },
                    enabled = pin.isNotBlank() && newPw.length >= 6
                ) {
                    Text("Reset Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecoveryDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BusinessSettingsTab(
    config: BusinessConfig,
    isProcessing: Boolean,
    onSaveConfig: (BusinessConfig) -> Unit
) {
    var businessName by remember(config.businessName) { mutableStateOf(config.businessName) }
    var phone by remember(config.businessPhone) { mutableStateOf(config.businessPhone) }
    var address by remember(config.businessAddress) { mutableStateOf(config.businessAddress) }
    var gstin by remember(config.gstin) { mutableStateOf(config.gstin) }
    var stateName by remember(config.stateName) { mutableStateOf(config.stateName) }
    var upiId by remember(config.upiId) { mutableStateOf(config.upiId) }
    var upiMerchant by remember(config.upiMerchantName) { mutableStateOf(config.upiMerchantName) }
    var invoicePrefix by remember(config.invoicePrefix) { mutableStateOf(config.invoicePrefix) }
    var template by remember(config.invoiceTemplate) { mutableStateOf(config.invoiceTemplate) }
    var logoUri by remember(config.logoUri) { mutableStateOf(config.logoUri) }

    var voiceEnabled by remember(config.voiceBillingEnabled) { mutableStateOf(config.voiceBillingEnabled) }
    var gstEnabled by remember(config.gstBillingEnabled) { mutableStateOf(config.gstBillingEnabled) }
    var upiEnabled by remember(config.upiQrEnabled) { mutableStateOf(config.upiQrEnabled) }
    var purchaseEnabled by remember(config.purchaseModuleEnabled) { mutableStateOf(config.purchaseModuleEnabled) }
    var autoBackupEnabled by remember(config.autoBackupEnabled) { mutableStateOf(config.autoBackupEnabled) }

    val logoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) logoUri = uri.toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Business Profile & Logo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!logoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = logoUri,
                                contentDescription = "Business Logo",
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (logoUri.isNullOrBlank()) "Pick Store Logo" else "Change Logo")
                        }
                    }

                    OutlinedTextField(value = businessName, onValueChange = { businessName = it }, label = { Text("Business / Store Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Business Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Registered Address") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = gstin, onValueChange = { gstin = it.uppercase() }, label = { Text("GSTIN") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = stateName, onValueChange = { stateName = it }, label = { Text("State") }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("UPI Payment Gateway & Invoice Design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("Owner UPI ID (VPA)") },
                        placeholder = { Text("e.g. yourshop@okaxis") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_upi_id_input")
                    )
                    OutlinedTextField(
                        value = upiMerchant,
                        onValueChange = { upiMerchant = it },
                        label = { Text("UPI Merchant Display Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = invoicePrefix,
                        onValueChange = { invoicePrefix = it.uppercase() },
                        label = { Text("Invoice Prefix") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Default PDF Invoice Template", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MODERN", "CLASSIC", "THERMAL").forEach { tpl ->
                            FilterChip(selected = template == tpl, onClick = { template = tpl }, label = { Text(tpl) })
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Enable & Disable App Features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    FeatureToggleRow("AI Voice Billing (Hindi & English)", voiceEnabled) { voiceEnabled = it }
                    HorizontalDivider()
                    FeatureToggleRow("GST Tax Engine (CGST / SGST / IGST)", gstEnabled) { gstEnabled = it }
                    HorizontalDivider()
                    FeatureToggleRow("Dynamic UPI QR Code Payments", upiEnabled) { upiEnabled = it }
                    HorizontalDivider()
                    FeatureToggleRow("Purchases & Supplier Ledger", purchaseEnabled) { purchaseEnabled = it }
                    HorizontalDivider()
                    FeatureToggleRow("Automatic Cloud Backup Sync", autoBackupEnabled) { autoBackupEnabled = it }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onSaveConfig(
                        config.copy(
                            businessName = businessName,
                            businessPhone = phone,
                            businessAddress = address,
                            gstin = gstin,
                            stateName = stateName,
                            upiId = upiId,
                            upiMerchantName = upiMerchant,
                            invoicePrefix = invoicePrefix,
                            invoiceTemplate = template,
                            logoUri = logoUri,
                            voiceBillingEnabled = voiceEnabled,
                            gstBillingEnabled = gstEnabled,
                            upiQrEnabled = upiEnabled,
                            purchaseModuleEnabled = purchaseEnabled,
                            autoBackupEnabled = autoBackupEnabled
                        )
                    )
                },
                enabled = !isProcessing && businessName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_business_config_button")
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save Business & Feature Settings")
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
