package com.example.model

import androidx.annotation.Keep
import com.example.util.SecurityUtils
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.serialization.Serializable

@Keep
@IgnoreExtraProperties
data class BusinessConfig(
    @DocumentId val id: String = "",
    val userId: String = "",
    val businessName: String = "BharatBill Enterprises",
    val businessPhone: String = "",
    val businessAddress: String = "",
    val gstin: String = "",
    val stateName: String = "Karnataka",
    val upiId: String = "",
    val upiMerchantName: String = "BharatBill Enterprises",
    val invoicePrefix: String = "INV",
    val nextInvoiceNumber: Long = 1001L,
    val invoiceTemplate: String = "MODERN",
    val logoUri: String? = null,
    val ownerPasswordHash: String = SecurityUtils.INITIAL_OWNER_PASSWORD_HASH,
    val ownerPasswordSalt: String = SecurityUtils.INITIAL_OWNER_SALT,
    val mustChangeInitialPassword: Boolean = true,
    val recoveryPinHash: String? = null,
    val recoveryHint: String? = null,
    val twoFactorEnabled: Boolean = false,
    val twoFactorCode: String? = null,
    val appVersion: String = "2.4.0",
    val autoUpdateEnabled: Boolean = true,
    val maintenanceMode: Boolean = false,
    val voiceBillingEnabled: Boolean = true,
    val gstBillingEnabled: Boolean = true,
    val upiQrEnabled: Boolean = true,
    val purchaseModuleEnabled: Boolean = true,
    val autoBackupEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "businessName" to businessName.ifBlank { "BharatBill Enterprises" },
            "businessPhone" to businessPhone,
            "businessAddress" to businessAddress,
            "gstin" to gstin,
            "stateName" to stateName,
            "upiId" to upiId,
            "upiMerchantName" to upiMerchantName.ifBlank { businessName },
            "invoicePrefix" to invoicePrefix.ifBlank { "INV" },
            "nextInvoiceNumber" to nextInvoiceNumber.coerceAtLeast(1L),
            "invoiceTemplate" to invoiceTemplate,
            "logoUri" to logoUri,
            "ownerPasswordHash" to ownerPasswordHash,
            "ownerPasswordSalt" to ownerPasswordSalt,
            "mustChangeInitialPassword" to mustChangeInitialPassword,
            "recoveryPinHash" to recoveryPinHash,
            "recoveryHint" to recoveryHint,
            "twoFactorEnabled" to twoFactorEnabled,
            "twoFactorCode" to twoFactorCode,
            "appVersion" to appVersion,
            "autoUpdateEnabled" to autoUpdateEnabled,
            "maintenanceMode" to maintenanceMode,
            "voiceBillingEnabled" to voiceBillingEnabled,
            "gstBillingEnabled" to gstBillingEnabled,
            "upiQrEnabled" to upiQrEnabled,
            "purchaseModuleEnabled" to purchaseModuleEnabled,
            "autoBackupEnabled" to autoBackupEnabled,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val sku: String = "",
    val barcode: String = "",
    val category: String = "General",
    val hsnSac: String = "",
    val unit: String = "PCS",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val gstRate: Double = 18.0,
    val stockQty: Double = 0.0,
    val lowStockThreshold: Double = 5.0,
    val imageUri: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mapOf(
            "userId" to uid,
            "name" to name.trim().take(120),
            "sku" to sku.trim().take(50),
            "barcode" to barcode.trim().take(64),
            "category" to category.trim().take(60),
            "hsnSac" to hsnSac.trim().take(16),
            "unit" to unit.ifBlank { "PCS" }.take(20),
            "purchasePrice" to purchasePrice.coerceAtLeast(0.0),
            "sellingPrice" to sellingPrice.coerceAtLeast(0.0),
            "gstRate" to gstRate.coerceIn(0.0, 100.0),
            "stockQty" to stockQty.coerceAtLeast(0.0),
            "lowStockThreshold" to lowStockThreshold.coerceAtLeast(0.0),
            "imageUri" to imageUri?.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val gstin: String = "",
    val stateName: String = "",
    val totalPurchases: Double = 0.0,
    val outstandingBalance: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(100),
        "phone" to phone.trim().take(20),
        "address" to address.trim().take(250),
        "gstin" to gstin.trim().take(20),
        "stateName" to stateName.trim().take(60),
        "totalPurchases" to totalPurchases.coerceAtLeast(0.0),
        "outstandingBalance" to outstandingBalance.coerceAtLeast(0.0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
@Serializable
data class InvoiceLineItem(
    val productId: String = "",
    val name: String = "",
    val hsnSac: String = "",
    val unit: String = "PCS",
    val qty: Double = 1.0,
    val unitPrice: Double = 0.0,
    val discountPercent: Double = 0.0,
    val gstRate: Double = 18.0,
    val taxableAmount: Double = 0.0,
    val cgst: Double = 0.0,
    val sgst: Double = 0.0,
    val igst: Double = 0.0,
    val total: Double = 0.0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "name" to name.take(120),
        "hsnSac" to hsnSac.take(16),
        "unit" to unit.ifBlank { "PCS" },
        "qty" to qty,
        "unitPrice" to unitPrice,
        "discountPercent" to discountPercent,
        "gstRate" to gstRate,
        "taxableAmount" to taxableAmount,
        "cgst" to cgst,
        "sgst" to sgst,
        "igst" to igst,
        "total" to total
    )
}

@Keep
@IgnoreExtraProperties
data class Invoice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceNumber: String = "",
    val invoiceType: String = "GST",
    val taxMode: String = "INTRA_STATE",
    val customerId: String = "",
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val customerGstin: String = "",
    val items: List<InvoiceLineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxableTotal: Double = 0.0,
    val cgstTotal: Double = 0.0,
    val sgstTotal: Double = 0.0,
    val igstTotal: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val paymentStatus: String = "UNPAID",
    val paymentMethod: String = "CASH",
    val upiTransactionRef: String = "",
    val notes: String = "",
    val templateStyle: String = "MODERN",
    val invoiceEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "invoiceNumber" to invoiceNumber.take(40),
        "invoiceType" to invoiceType,
        "taxMode" to taxMode,
        "customerId" to customerId.take(128),
        "customerName" to customerName.ifBlank { "Walk-in Customer" }.take(100),
        "customerPhone" to customerPhone.take(20),
        "customerAddress" to customerAddress.take(250),
        "customerGstin" to customerGstin.take(20),
        "items" to items.take(100).map { it.toMap() },
        "subtotal" to subtotal.coerceAtLeast(0.0),
        "discountAmount" to discountAmount.coerceAtLeast(0.0),
        "taxableTotal" to taxableTotal.coerceAtLeast(0.0),
        "cgstTotal" to cgstTotal.coerceAtLeast(0.0),
        "sgstTotal" to sgstTotal.coerceAtLeast(0.0),
        "igstTotal" to igstTotal.coerceAtLeast(0.0),
        "grandTotal" to grandTotal.coerceAtLeast(0.0),
        "paidAmount" to paidAmount.coerceAtLeast(0.0),
        "balanceDue" to balanceDue.coerceAtLeast(0.0),
        "paymentStatus" to paymentStatus,
        "paymentMethod" to paymentMethod,
        "upiTransactionRef" to upiTransactionRef.take(100),
        "notes" to notes.take(300),
        "templateStyle" to templateStyle,
        "invoiceEpochMs" to invoiceEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val invoiceId: String = "",
    val invoiceNumber: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val amount: Double = 0.0,
    val method: String = "UPI_QR",
    val upiApp: String = "GPay",
    val upiId: String = "",
    val transactionRef: String = "",
    val verificationStatus: String = "PENDING",
    val verifiedByOwner: Boolean = false,
    val notes: String = "",
    val paymentEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "invoiceId" to invoiceId,
        "invoiceNumber" to invoiceNumber.take(40),
        "customerId" to customerId,
        "customerName" to customerName.ifBlank { "Customer" }.take(100),
        "amount" to amount.coerceAtLeast(0.0),
        "method" to method,
        "upiApp" to upiApp.take(40),
        "upiId" to upiId.take(100),
        "transactionRef" to transactionRef.take(100),
        "verificationStatus" to verificationStatus,
        "verifiedByOwner" to verifiedByOwner,
        "notes" to notes.take(250),
        "paymentEpochMs" to paymentEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Supplier(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val gstin: String = "",
    val totalPurchased: Double = 0.0,
    val outstandingPayable: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(100),
        "phone" to phone.trim().take(20),
        "address" to address.trim().take(250),
        "gstin" to gstin.trim().take(20),
        "totalPurchased" to totalPurchased.coerceAtLeast(0.0),
        "outstandingPayable" to outstandingPayable.coerceAtLeast(0.0),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
@Serializable
data class PurchaseLineItem(
    val productId: String = "",
    val name: String = "",
    val qty: Double = 1.0,
    val unitPrice: Double = 0.0,
    val gstRate: Double = 18.0,
    val total: Double = 0.0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "name" to name.take(120),
        "qty" to qty,
        "unitPrice" to unitPrice,
        "gstRate" to gstRate,
        "total" to total
    )
}

@Keep
@IgnoreExtraProperties
data class PurchaseRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val purchaseNumber: String = "",
    val supplierId: String = "",
    val supplierName: String = "",
    val recordType: String = "PURCHASE",
    val items: List<PurchaseLineItem> = emptyList(),
    val taxableTotal: Double = 0.0,
    val gstTotal: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val paymentStatus: String = "PAID",
    val notes: String = "",
    val purchaseEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "purchaseNumber" to purchaseNumber.take(40),
        "supplierId" to supplierId,
        "supplierName" to supplierName.ifBlank { "Supplier" }.take(100),
        "recordType" to recordType,
        "items" to items.take(100).map { it.toMap() },
        "taxableTotal" to taxableTotal.coerceAtLeast(0.0),
        "gstTotal" to gstTotal.coerceAtLeast(0.0),
        "grandTotal" to grandTotal.coerceAtLeast(0.0),
        "paidAmount" to paidAmount.coerceAtLeast(0.0),
        "paymentStatus" to paymentStatus,
        "notes" to notes.take(250),
        "purchaseEpochMs" to purchaseEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val category: String = "Operations",
    val amount: Double = 0.0,
    val paymentMode: String = "CASH",
    val notes: String = "",
    val expenseEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(120),
        "category" to category.trim().ifBlank { "Operations" }.take(60),
        "amount" to amount.coerceAtLeast(0.0),
        "paymentMode" to paymentMode.ifBlank { "CASH" }.take(30),
        "notes" to notes.take(250),
        "expenseEpochMs" to expenseEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StockAdjustment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val deltaQty: Double = 0.0,
    val previousQty: Double = 0.0,
    val newQty: Double = 0.0,
    val reason: String = "",
    val adjustmentEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "productId" to productId,
        "productName" to productName.take(120),
        "deltaQty" to deltaQty,
        "previousQty" to previousQty.coerceAtLeast(0.0),
        "newQty" to newQty.coerceAtLeast(0.0),
        "reason" to reason.ifBlank { "Adjustment" }.take(120),
        "adjustmentEpochMs" to adjustmentEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StaffUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val emailOrPhone: String = "",
    val role: String = "BILLER",
    val canManageBilling: Boolean = true,
    val canManageInventory: Boolean = false,
    val canManagePurchases: Boolean = false,
    val canViewReports: Boolean = false,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(80),
        "emailOrPhone" to emailOrPhone.trim().take(100),
        "role" to role,
        "canManageBilling" to canManageBilling,
        "canManageInventory" to canManageInventory,
        "canManagePurchases" to canManagePurchases,
        "canViewReports" to canViewReports,
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actor: String = "Owner",
    val action: String = "",
    val details: String = "",
    val category: String = "SYSTEM",
    val logEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "actor" to actor.take(80),
        "action" to action.take(120),
        "details" to details.take(300),
        "category" to category.take(40),
        "logEpochMs" to logEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class BackupRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val appVersion: String = "2.4.0",
    val recordCount: Long = 0L,
    val payloadJson: String = "{}",
    val backupEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "label" to label.ifBlank { "Manual Backup" }.take(100),
        "appVersion" to appVersion.take(24),
        "recordCount" to recordCount.coerceAtLeast(0L),
        "payloadJson" to payloadJson.take(900000),
        "backupEpochMs" to backupEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class UpgradeHistory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fromVersion: String = "",
    val toVersion: String = "",
    val status: String = "SUCCESS",
    val releaseNotes: String = "",
    val backupId: String = "",
    val upgradeEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "fromVersion" to fromVersion.take(24),
        "toVersion" to toVersion.take(24),
        "status" to status,
        "releaseNotes" to releaseNotes.take(400),
        "backupId" to backupId.take(128),
        "upgradeEpochMs" to upgradeEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ReleaseItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val version: String = "",
    val title: String = "",
    val channel: String = "STABLE",
    val securityUpdate: Boolean = false,
    val features: String = "",
    val bugFixes: String = "",
    val releaseEpochMs: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "version" to version.take(24),
        "title" to title.take(120),
        "channel" to channel.take(30),
        "securityUpdate" to securityUpdate,
        "features" to features.take(500),
        "bugFixes" to bugFixes.take(500),
        "releaseEpochMs" to releaseEpochMs.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

// --- Backup Serialization Models ---

@Serializable
data class BackupProductDto(
    val id: String,
    val name: String,
    val sku: String,
    val barcode: String,
    val category: String,
    val hsnSac: String,
    val unit: String,
    val purchasePrice: Double,
    val sellingPrice: Double,
    val gstRate: Double,
    val stockQty: Double,
    val lowStockThreshold: Double
)

@Serializable
data class BackupCustomerDto(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val gstin: String,
    val stateName: String,
    val totalPurchases: Double,
    val outstandingBalance: Double
)

@Serializable
data class BackupSupplierDto(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val gstin: String,
    val totalPurchased: Double,
    val outstandingPayable: Double
)

@Serializable
data class BackupExpenseDto(
    val id: String,
    val title: String,
    val category: String,
    val amount: Double,
    val paymentMode: String,
    val notes: String,
    val expenseEpochMs: Long
)

@Serializable
data class AppBackupPayload(
    val appVersion: String,
    val createdEpochMs: Long,
    val businessName: String,
    val gstin: String,
    val upiId: String,
    val products: List<BackupProductDto> = emptyList(),
    val customers: List<BackupCustomerDto> = emptyList(),
    val suppliers: List<BackupSupplierDto> = emptyList(),
    val expenses: List<BackupExpenseDto> = emptyList()
)
