package com.example.data

import android.content.Context
import com.example.R
import com.example.model.ActivityLog
import com.example.model.AppBackupPayload
import com.example.model.BackupCustomerDto
import com.example.model.BackupExpenseDto
import com.example.model.BackupProductDto
import com.example.model.BackupRecord
import com.example.model.BackupSupplierDto
import com.example.model.BusinessConfig
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.Product
import com.example.model.ReleaseItem
import com.example.model.StaffUser
import com.example.model.Supplier
import com.example.model.UpgradeHistory
import com.example.util.OperationType
import com.example.util.SecurityUtils
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class OwnerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) { "User must be signed in with Google." }
        return uid
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeStaffUsers(userId: String): Flow<List<StaffUser>> =
        userDoc(userId).collection("staff_users").snapshots().map { snap ->
            snap.toObjects(StaffUser::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name.lowercase() }
        }

    fun observeActivityLogs(userId: String): Flow<List<ActivityLog>> =
        userDoc(userId).collection("activity_logs").snapshots().map { snap ->
            snap.toObjects(ActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.logEpochMs }
        }

    fun observeBackups(userId: String): Flow<List<BackupRecord>> =
        userDoc(userId).collection("backups").snapshots().map { snap ->
            snap.toObjects(BackupRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.backupEpochMs }
        }

    fun observeUpgradeHistory(userId: String): Flow<List<UpgradeHistory>> =
        userDoc(userId).collection("upgrade_history").snapshots().map { snap ->
            snap.toObjects(UpgradeHistory::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.upgradeEpochMs }
        }

    fun observeReleaseCatalog(userId: String): Flow<List<ReleaseItem>> =
        userDoc(userId).collection("release_catalog").snapshots().map { snap ->
            snap.toObjects(ReleaseItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.releaseEpochMs }
        }

    suspend fun ensureWorkspaceInitialized(defaultName: String): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            val snap = ref.get().await()
            if (!snap.exists()) {
                val initialConfig = BusinessConfig(
                    userId = uid,
                    businessName = defaultName.ifBlank { "Maya Bill Store" },
                    upiMerchantName = defaultName.ifBlank { "Maya Bill Store" },
                    ownerPasswordHash = SecurityUtils.INITIAL_OWNER_PASSWORD_HASH,
                    ownerPasswordSalt = SecurityUtils.INITIAL_OWNER_SALT,
                    mustChangeInitialPassword = true,
                    profileCompleted = false,
                    appVersion = "2.4.0"
                )
                ref.set(initialConfig.toCreateMap(uid)).await()

                val ownerStaff = StaffUser(
                    userId = uid,
                    name = defaultName.ifBlank { "Store Owner" },
                    emailOrPhone = auth.currentUser?.email ?: "owner@mayabill.in",
                    role = "OWNER",
                    canManageBilling = true,
                    canManageInventory = true,
                    canManagePurchases = true,
                    canViewReports = true,
                    active = true
                )
                userDoc(uid).collection("staff_users").document("staff_owner")
                    .set(ownerStaff.toCreateMap(uid)).await()

                seedReleaseCatalogInternal(uid)
                logActivityInternal(uid, "System", "Workspace Initialized", "Created secure cloud workspace v2.4.0", "SYSTEM")
            } else {
                val relSnap = userDoc(uid).collection("release_catalog").get().await()
                if (relSnap.isEmpty) {
                    seedReleaseCatalogInternal(uid)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedReleaseCatalogInternal(uid: String) {
        val now = System.currentTimeMillis()
        val releases = listOf(
            ReleaseItem(
                id = "rel_2_4_0",
                userId = uid,
                version = "2.4.0",
                title = "Maya Bill App Core GST & Voice Suite",
                channel = "STABLE",
                securityUpdate = false,
                features = "GST/Non-GST invoicing, Hindi & English Voice Billing, NPCI Dynamic UPI QR verification.",
                bugFixes = "Initial production baseline.",
                releaseEpochMs = now - 86400000L * 14
            ),
            ReleaseItem(
                id = "rel_2_5_0",
                userId = uid,
                version = "2.5.0",
                title = "Hinglish Voice v2 & Security Hardening",
                channel = "STABLE",
                securityUpdate = true,
                features = "Enhanced Hinglish voice parser, Thermal 58mm PDF receipts, Automated pre-upgrade cloud snapshots.",
                bugFixes = "Fixed IGST rounding edge cases on multi-slab items and faster UPI QR rendering.",
                releaseEpochMs = now - 86400000L * 2
            ),
            ReleaseItem(
                id = "rel_2_5_1",
                userId = uid,
                version = "2.5.1",
                title = "Critical Security Patch & GSTR Summary",
                channel = "SECURITY",
                securityUpdate = true,
                features = "Improved Owner session token checks, Excel UTF-8 BOM GST export, faster low-stock alerts.",
                bugFixes = "Resolved rare duplicate invoice counter race condition during rapid billing.",
                releaseEpochMs = now - 3600000L * 6
            )
        )
        releases.forEach { rel ->
            userDoc(uid).collection("release_catalog").document(rel.id)
                .set(rel.toCreateMap(uid)).await()
        }
    }

    suspend fun checkForUpdatesFromServer(): Result<List<ReleaseItem>> {
        var path = "users/unknown/release_catalog"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("release_catalog")
            path = col.path
            val snap = try {
                col.get(Source.SERVER).await()
            } catch (_: Exception) {
                col.get().await()
            }
            val list = snap.toObjects(ReleaseItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.releaseEpochMs }
            logActivityInternal(uid, "Owner", "Checked for Updates", "Latest version found: ${list.firstOrNull()?.version ?: "2.4.0"}", "UPGRADE")
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun updateBusinessSettings(config: BusinessConfig): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            val updates = mutableMapOf<String, Any>(
                "businessName" to config.businessName.ifBlank { "Maya Bill Store" }.take(100),
                "businessPhone" to config.businessPhone.take(25),
                "businessAddress" to config.businessAddress.take(250),
                "gstin" to config.gstin.uppercase().take(20),
                "stateName" to config.stateName.take(60),
                "gstEntityDetails" to config.gstEntityDetails.take(250),
                "ownerSignatureStrokes" to config.ownerSignatureStrokes.take(12000),
                "profileCompleted" to config.profileCompleted,
                "upiId" to config.upiId.trim().take(100),
                "upiMerchantName" to config.upiMerchantName.ifBlank { config.businessName }.take(100),
                "invoicePrefix" to config.invoicePrefix.ifBlank { "INV" }.take(15),
                "invoiceTemplate" to config.invoiceTemplate,
                "voiceBillingEnabled" to config.voiceBillingEnabled,
                "gstBillingEnabled" to config.gstBillingEnabled,
                "upiQrEnabled" to config.upiQrEnabled,
                "purchaseModuleEnabled" to config.purchaseModuleEnabled,
                "autoBackupEnabled" to config.autoBackupEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            config.logoUri?.let { updates["logoUri"] = it.take(500) }
            ref.update(updates).await()
            logActivityInternal(uid, "Owner", "Updated Business Settings", "Updated Business Profile, Signature, GST/UPI & Settings", "SETTINGS")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateOwnerSecurityCredentials(
        newPassword: String? = null,
        recoveryPin: String? = null,
        recoveryHint: String? = null,
        twoFactorEnabled: Boolean,
        twoFactorCode: String? = null
    ): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            val updates = mutableMapOf<String, Any>(
                "twoFactorEnabled" to twoFactorEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!newPassword.isNullOrBlank()) {
                val salt = SecurityUtils.generateSalt()
                val hash = SecurityUtils.hashSecret(newPassword, salt)
                updates["ownerPasswordSalt"] = salt
                updates["ownerPasswordHash"] = hash
                updates["mustChangeInitialPassword"] = false
            }
            if (!recoveryPin.isNullOrBlank()) {
                updates["recoveryPinHash"] = SecurityUtils.hashSecret(recoveryPin, SecurityUtils.INITIAL_OWNER_SALT)
            }
            if (!recoveryHint.isNullOrBlank()) {
                updates["recoveryHint"] = recoveryHint.take(100)
            }
            if (!twoFactorCode.isNullOrBlank()) {
                updates["twoFactorCode"] = twoFactorCode.take(12)
            }
            ref.update(updates).await()
            logActivityInternal(uid, "Owner", "Security Settings Updated", "Updated Owner password/2FA/recovery configuration", "SECURITY")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveStaffUser(staff: StaffUser): Result<Unit> {
        var path = "users/unknown/staff_users"
        return try {
            val uid = requireUserId()
            val id = if (staff.id.isBlank()) "staff_${UUID.randomUUID().toString().take(10)}" else staff.id
            val ref = userDoc(uid).collection("staff_users").document(id)
            path = ref.path
            if (staff.id.isBlank()) {
                ref.set(staff.toCreateMap(uid)).await()
            } else {
                ref.update(
                    mapOf(
                        "name" to staff.name.trim().take(80),
                        "emailOrPhone" to staff.emailOrPhone.trim().take(100),
                        "role" to staff.role,
                        "canManageBilling" to staff.canManageBilling,
                        "canManageInventory" to staff.canManageInventory,
                        "canManagePurchases" to staff.canManagePurchases,
                        "canViewReports" to staff.canViewReports,
                        "active" to staff.active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            logActivityInternal(uid, "Owner", "Staff Permissions Updated", "${staff.name} (${staff.role})", "USERS")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStaffUser(staffId: String): Result<Unit> {
        var path = "users/unknown/staff_users/$staffId"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("staff_users").document(staffId)
            path = ref.path
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createCloudBackup(
        label: String,
        config: BusinessConfig,
        products: List<Product>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        expenses: List<Expense>
    ): Result<BackupRecord> {
        var path = "users/unknown/backups"
        return try {
            val uid = requireUserId()
            val payload = AppBackupPayload(
                appVersion = config.appVersion,
                createdEpochMs = System.currentTimeMillis(),
                businessName = config.businessName,
                gstin = config.gstin,
                upiId = config.upiId,
                products = products.map {
                    BackupProductDto(
                        it.id, it.name, it.sku, it.barcode, it.category, it.hsnSac,
                        it.unit, it.purchasePrice, it.sellingPrice, it.gstRate, it.stockQty, it.lowStockThreshold
                    )
                },
                customers = customers.map {
                    BackupCustomerDto(
                        it.id, it.name, it.phone, it.address, it.gstin, it.stateName,
                        it.totalPurchases, it.outstandingBalance
                    )
                },
                suppliers = suppliers.map {
                    BackupSupplierDto(
                        it.id, it.name, it.phone, it.address, it.gstin,
                        it.totalPurchased, it.outstandingPayable
                    )
                },
                expenses = expenses.map {
                    BackupExpenseDto(
                        it.id, it.title, it.category, it.amount, it.paymentMode, it.notes, it.expenseEpochMs
                    )
                }
            )
            val jsonStr = json.encodeToString(payload)
            val count = (products.size + customers.size + suppliers.size + expenses.size).toLong()
            val backupId = "bkp_${UUID.randomUUID().toString().take(12)}"
            val record = BackupRecord(
                id = backupId,
                userId = uid,
                label = label,
                appVersion = config.appVersion,
                recordCount = count,
                payloadJson = jsonStr,
                backupEpochMs = System.currentTimeMillis()
            )
            val ref = userDoc(uid).collection("backups").document(backupId)
            path = ref.path
            ref.set(record.toCreateMap(uid)).await()
            logActivityInternal(uid, "Owner", "Cloud Backup Created", "$label ($count records)", "BACKUP")
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackup(payloadJson: String): Result<Int> {
        var path = "users/unknown/backups/restore"
        return try {
            val uid = requireUserId()
            val payload = json.decodeFromString<AppBackupPayload>(payloadJson)
            var restored = 0
            payload.products.forEach { dto ->
                val p = Product(
                    id = dto.id, userId = uid, name = dto.name, sku = dto.sku,
                    barcode = dto.barcode, category = dto.category, hsnSac = dto.hsnSac,
                    unit = dto.unit, purchasePrice = dto.purchasePrice, sellingPrice = dto.sellingPrice,
                    gstRate = dto.gstRate, stockQty = dto.stockQty, lowStockThreshold = dto.lowStockThreshold
                )
                val ref = userDoc(uid).collection("products").document(dto.id)
                path = ref.path
                val existingSnap = ref.get().await()
                if (!existingSnap.exists()) {
                    ref.set(p.toCreateMap(uid)).await()
                } else {
                    ref.update(
                        mapOf(
                            "name" to p.name, "sellingPrice" to p.sellingPrice,
                            "purchasePrice" to p.purchasePrice, "stockQty" to p.stockQty,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                restored++
            }
            payload.customers.forEach { dto ->
                val c = Customer(
                    id = dto.id, userId = uid, name = dto.name, phone = dto.phone,
                    address = dto.address, gstin = dto.gstin, stateName = dto.stateName,
                    totalPurchases = dto.totalPurchases, outstandingBalance = dto.outstandingBalance
                )
                val ref = userDoc(uid).collection("customers").document(dto.id)
                if (!ref.get().await().exists()) {
                    ref.set(c.toCreateMap(uid)).await()
                }
                restored++
            }
            payload.suppliers.forEach { dto ->
                val s = Supplier(
                    id = dto.id, userId = uid, name = dto.name, phone = dto.phone,
                    address = dto.address, gstin = dto.gstin,
                    totalPurchased = dto.totalPurchased, outstandingPayable = dto.outstandingPayable
                )
                val ref = userDoc(uid).collection("suppliers").document(dto.id)
                if (!ref.get().await().exists()) {
                    ref.set(s.toCreateMap(uid)).await()
                }
                restored++
            }
            logActivityInternal(uid, "Owner", "Data Restored", "Restored $restored records from backup", "BACKUP")
            Result.success(restored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun executeOneClickUpgrade(
        targetRelease: ReleaseItem,
        config: BusinessConfig,
        products: List<Product>,
        customers: List<Customer>,
        suppliers: List<Supplier>,
        expenses: List<Expense>
    ): Result<UpgradeHistory> {
        var path = "users/unknown/upgrade_history"
        return try {
            val uid = requireUserId()
            // 1. Automatic pre-upgrade backup
            val backupRes = createCloudBackup(
                label = "Pre-Upgrade v${config.appVersion} -> v${targetRelease.version}",
                config = config,
                products = products,
                customers = customers,
                suppliers = suppliers,
                expenses = expenses
            )
            val backupId = backupRes.getOrNull()?.id ?: ""

            // 2. Apply version upgrade on BusinessConfig
            userDoc(uid).update(
                mapOf(
                    "appVersion" to targetRelease.version,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // 3. Record upgrade history
            val histId = "upg_${UUID.randomUUID().toString().take(12)}"
            val history = UpgradeHistory(
                id = histId,
                userId = uid,
                fromVersion = config.appVersion,
                toVersion = targetRelease.version,
                status = "SUCCESS",
                releaseNotes = "${targetRelease.title}: ${targetRelease.features}",
                backupId = backupId,
                upgradeEpochMs = System.currentTimeMillis()
            )
            val ref = userDoc(uid).collection("upgrade_history").document(histId)
            path = ref.path
            ref.set(history.toCreateMap(uid)).await()
            logActivityInternal(
                uid, "Owner", "Upgraded to v${targetRelease.version}",
                "Auto backup $backupId saved before upgrade", "UPGRADE"
            )
            Result.success(history)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun rollbackToPreviousVersion(
        historyItem: UpgradeHistory,
        backups: List<BackupRecord>
    ): Result<Unit> {
        var path = "users/unknown/upgrade_history/${historyItem.id}"
        return try {
            val uid = requireUserId()
            val backup = backups.firstOrNull { it.id == historyItem.backupId }
            if (backup != null) {
                restoreFromBackup(backup.payloadJson)
            }
            userDoc(uid).update(
                mapOf(
                    "appVersion" to historyItem.fromVersion,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val ref = userDoc(uid).collection("upgrade_history").document(historyItem.id)
            path = ref.path
            ref.update(
                mapOf(
                    "status" to "ROLLED_BACK",
                    "releaseNotes" to "${historyItem.releaseNotes} [Rolled back safely]",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logActivityInternal(
                uid, "Owner", "Rolled Back Version",
                "Restored v${historyItem.fromVersion} from backup ${historyItem.backupId}", "UPGRADE"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUpgradeToggles(autoUpdateEnabled: Boolean, maintenanceMode: Boolean): Result<Unit> {
        var path = "users/unknown"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            ref.update(
                mapOf(
                    "autoUpdateEnabled" to autoUpdateEnabled,
                    "maintenanceMode" to maintenanceMode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logActivityInternal(
                uid, "Owner", "Upgrade Controls Updated",
                "AutoUpdate=$autoUpdateEnabled, MaintenanceMode=$maintenanceMode", "UPGRADE"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun logActivity(actor: String, action: String, details: String, category: String) {
        try {
            val uid = requireUserId()
            logActivityInternal(uid, actor, action, details, category)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-blocking audit log
        }
    }

    private suspend fun logActivityInternal(
        uid: String,
        actor: String,
        action: String,
        details: String,
        category: String
    ) {
        val logId = "log_${UUID.randomUUID().toString().take(12)}"
        val log = ActivityLog(
            userId = uid,
            actor = actor,
            action = action,
            details = details,
            category = category,
            logEpochMs = System.currentTimeMillis()
        )
        userDoc(uid).collection("activity_logs").document(logId)
            .set(log.toCreateMap(uid)).await()
    }
}
