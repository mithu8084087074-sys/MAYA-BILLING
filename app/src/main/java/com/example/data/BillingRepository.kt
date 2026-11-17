package com.example.data

import android.content.Context
import com.example.R
import com.example.model.BusinessConfig
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.Invoice
import com.example.model.InvoiceLineItem
import com.example.model.PaymentRecord
import com.example.model.Product
import com.example.model.PurchaseRecord
import com.example.model.StockAdjustment
import com.example.model.Supplier
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class BillingRepository(
    internal val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    internal fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        require(!uid.isNullOrBlank()) { "User must be signed in with Google before accessing Firestore." }
        return uid
    }

    internal fun userDoc(uid: String) = db.collection("users").document(uid)

    fun observeBusinessConfig(userId: String): Flow<BusinessConfig?> =
        userDoc(userId).snapshots().map { snap ->
            if (snap.exists()) {
                snap.toObject(BusinessConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        }

    fun observeProducts(userId: String): Flow<List<Product>> =
        userDoc(userId).collection("products").snapshots().map { snap ->
            snap.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name.lowercase() }
        }

    fun observeCustomers(userId: String): Flow<List<Customer>> =
        userDoc(userId).collection("customers").snapshots().map { snap ->
            snap.toObjects(Customer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name.lowercase() }
        }

    fun observeInvoices(userId: String): Flow<List<Invoice>> =
        userDoc(userId).collection("invoices").snapshots().map { snap ->
            snap.toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.invoiceEpochMs }
        }

    fun observePayments(userId: String): Flow<List<PaymentRecord>> =
        userDoc(userId).collection("payments").snapshots().map { snap ->
            snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.paymentEpochMs }
        }

    fun observeSuppliers(userId: String): Flow<List<Supplier>> =
        userDoc(userId).collection("suppliers").snapshots().map { snap ->
            snap.toObjects(Supplier::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name.lowercase() }
        }

    fun observePurchases(userId: String): Flow<List<PurchaseRecord>> =
        userDoc(userId).collection("purchases").snapshots().map { snap ->
            snap.toObjects(PurchaseRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.purchaseEpochMs }
        }

    fun observeExpenses(userId: String): Flow<List<Expense>> =
        userDoc(userId).collection("expenses").snapshots().map { snap ->
            snap.toObjects(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.expenseEpochMs }
        }

    fun observeStockAdjustments(userId: String): Flow<List<StockAdjustment>> =
        userDoc(userId).collection("stock_adjustments").snapshots().map { snap ->
            snap.toObjects(StockAdjustment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.adjustmentEpochMs }
        }

    suspend fun saveProduct(product: Product): Result<String> {
        var path = "users/unknown/products"
        return try {
            val uid = requireUserId()
            val isNew = product.id.isBlank()
            val id = if (isNew) "prod_${UUID.randomUUID().toString().take(12)}" else product.id
            val ref = userDoc(uid).collection("products").document(id)
            path = ref.path
            if (isNew) {
                ref.set(product.toCreateMap(uid)).await()
            } else {
                val updates = mutableMapOf<String, Any>(
                    "name" to product.name.trim().take(120),
                    "sku" to product.sku.trim().take(50),
                    "barcode" to product.barcode.trim().take(64),
                    "category" to product.category.trim().take(60),
                    "hsnSac" to product.hsnSac.trim().take(16),
                    "unit" to product.unit.ifBlank { "PCS" }.take(20),
                    "purchasePrice" to product.purchasePrice.coerceAtLeast(0.0),
                    "sellingPrice" to product.sellingPrice.coerceAtLeast(0.0),
                    "gstRate" to product.gstRate.coerceIn(0.0, 100.0),
                    "stockQty" to product.stockQty.coerceAtLeast(0.0),
                    "lowStockThreshold" to product.lowStockThreshold.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                product.imageUri?.let { updates["imageUri"] = it.take(500) }
                ref.update(updates).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        var path = "users/unknown/products/$productId"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("products").document(productId)
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

    suspend fun adjustProductStock(
        product: Product,
        deltaQty: Double,
        reason: String
    ): Result<Unit> {
        var path = "users/unknown/products/${product.id}"
        return try {
            val uid = requireUserId()
            val newQty = (product.stockQty + deltaQty).coerceAtLeast(0.0)
            val prodRef = userDoc(uid).collection("products").document(product.id)
            path = prodRef.path
            prodRef.update(
                mapOf(
                    "stockQty" to newQty,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val adjId = "adj_${UUID.randomUUID().toString().take(12)}"
            val adj = StockAdjustment(
                userId = uid,
                productId = product.id,
                productName = product.name,
                deltaQty = deltaQty,
                previousQty = product.stockQty,
                newQty = newQty,
                reason = reason,
                adjustmentEpochMs = System.currentTimeMillis()
            )
            userDoc(uid).collection("stock_adjustments").document(adjId)
                .set(adj.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveCustomer(customer: Customer): Result<String> {
        var path = "users/unknown/customers"
        return try {
            val uid = requireUserId()
            val isNew = customer.id.isBlank()
            val id = if (isNew) "cust_${UUID.randomUUID().toString().take(12)}" else customer.id
            val ref = userDoc(uid).collection("customers").document(id)
            path = ref.path
            if (isNew) {
                ref.set(customer.toCreateMap(uid)).await()
            } else {
                ref.update(
                    mapOf(
                        "name" to customer.name.trim().take(100),
                        "phone" to customer.phone.trim().take(20),
                        "address" to customer.address.trim().take(250),
                        "gstin" to customer.gstin.trim().take(20),
                        "stateName" to customer.stateName.trim().take(60),
                        "totalPurchases" to customer.totalPurchases.coerceAtLeast(0.0),
                        "outstandingBalance" to customer.outstandingBalance.coerceAtLeast(0.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomer(customerId: String): Result<Unit> {
        var path = "users/unknown/customers/$customerId"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("customers").document(customerId)
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

    suspend fun createInvoice(
        invoice: Invoice,
        config: BusinessConfig,
        inventory: List<Product>,
        customers: List<Customer>,
        upiAppSelected: String = "GPay"
    ): Result<Invoice> {
        var path = "users/unknown/invoices"
        return try {
            val uid = requireUserId()
            val nextNum = config.nextInvoiceNumber.coerceAtLeast(1L)
            val generatedNumber = "${config.invoicePrefix.ifBlank { "INV" }}-$nextNum"
            val invoiceId = "inv_${UUID.randomUUID().toString().take(12)}"

            // Note: For UPI_QR, never mark PAID/VERIFIED automatically until owner verifies UTR!
            val isUpiQr = invoice.paymentMethod == "UPI_QR"
            val effectivePaid = if (isUpiQr) 0.0 else invoice.paidAmount
            val effectiveBalance = if (isUpiQr) invoice.grandTotal else invoice.balanceDue
            val effectiveStatus = if (isUpiQr) "UNPAID" else invoice.paymentStatus

            val finalInvoice = invoice.copy(
                id = invoiceId,
                userId = uid,
                invoiceNumber = generatedNumber,
                paidAmount = effectivePaid,
                balanceDue = effectiveBalance,
                paymentStatus = effectiveStatus,
                templateStyle = invoice.templateStyle.ifBlank { config.invoiceTemplate }
            )

            val invRef = userDoc(uid).collection("invoices").document(invoiceId)
            path = invRef.path
            invRef.set(finalInvoice.toCreateMap(uid)).await()

            // Increment invoice counter
            userDoc(uid).update(
                mapOf(
                    "nextInvoiceNumber" to nextNum + 1L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Deduct stock for matched products and log adjustments
            val nowMs = System.currentTimeMillis()
            finalInvoice.items.forEach { item: InvoiceLineItem ->
                if (item.productId.isNotBlank()) {
                    val prod = inventory.firstOrNull { it.id == item.productId }
                    if (prod != null) {
                        val newStock = (prod.stockQty - item.qty).coerceAtLeast(0.0)
                        userDoc(uid).collection("products").document(prod.id).update(
                            mapOf(
                                "stockQty" to newStock,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        val adjId = "adj_${UUID.randomUUID().toString().take(12)}"
                        val adj = StockAdjustment(
                            userId = uid,
                            productId = prod.id,
                            productName = prod.name,
                            deltaQty = -item.qty,
                            previousQty = prod.stockQty,
                            newQty = newStock,
                            reason = "Sale $generatedNumber",
                            adjustmentEpochMs = nowMs
                        )
                        userDoc(uid).collection("stock_adjustments").document(adjId)
                            .set(adj.toCreateMap(uid)).await()
                    }
                }
            }

            // Update customer totals if linked
            if (finalInvoice.customerId.isNotBlank()) {
                val cust = customers.firstOrNull { it.id == finalInvoice.customerId }
                if (cust != null) {
                    userDoc(uid).collection("customers").document(cust.id).update(
                        mapOf(
                            "totalPurchases" to cust.totalPurchases + finalInvoice.grandTotal,
                            "outstandingBalance" to cust.outstandingBalance + finalInvoice.balanceDue,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Create payment record: UPI_QR always starts PENDING and requires explicit verification!
            if (isUpiQr && finalInvoice.grandTotal > 0.0) {
                val payId = "pay_${UUID.randomUUID().toString().take(12)}"
                val payRecord = PaymentRecord(
                    userId = uid,
                    invoiceId = invoiceId,
                    invoiceNumber = generatedNumber,
                    customerId = finalInvoice.customerId,
                    customerName = finalInvoice.customerName,
                    amount = invoice.paidAmount.takeIf { it > 0.0 } ?: finalInvoice.grandTotal,
                    method = "UPI_QR",
                    upiApp = upiAppSelected,
                    upiId = config.upiId,
                    transactionRef = "",
                    verificationStatus = "PENDING",
                    verifiedByOwner = false,
                    notes = "Awaiting UPI payment confirmation & UTR verification",
                    paymentEpochMs = nowMs
                )
                userDoc(uid).collection("payments").document(payId)
                    .set(payRecord.toCreateMap(uid)).await()
            } else if (effectivePaid > 0.0) {
                val payId = "pay_${UUID.randomUUID().toString().take(12)}"
                val payRecord = PaymentRecord(
                    userId = uid,
                    invoiceId = invoiceId,
                    invoiceNumber = generatedNumber,
                    customerId = finalInvoice.customerId,
                    customerName = finalInvoice.customerName,
                    amount = effectivePaid,
                    method = if (finalInvoice.paymentMethod == "BANK") "BANK" else "CASH",
                    upiApp = "",
                    upiId = "",
                    transactionRef = finalInvoice.upiTransactionRef.ifBlank { "CASH-$generatedNumber" },
                    verificationStatus = "VERIFIED",
                    verifiedByOwner = true,
                    notes = "Recorded at billing",
                    paymentEpochMs = nowMs
                )
                userDoc(uid).collection("payments").document(payId)
                    .set(payRecord.toCreateMap(uid)).await()
            }

            Result.success(finalInvoice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateExistingInvoice(invoice: Invoice): Result<Unit> {
        var path = "users/unknown/invoices/${invoice.id}"
        return try {
            val uid = requireUserId()
            require(invoice.id.isNotBlank())
            val ref = userDoc(uid).collection("invoices").document(invoice.id)
            path = ref.path
            ref.update(
                mapOf(
                    "invoiceType" to invoice.invoiceType,
                    "taxMode" to invoice.taxMode,
                    "customerId" to invoice.customerId,
                    "customerName" to invoice.customerName.ifBlank { "Walk-in Customer" }.take(100),
                    "customerPhone" to invoice.customerPhone.take(20),
                    "customerAddress" to invoice.customerAddress.take(250),
                    "customerGstin" to invoice.customerGstin.take(20),
                    "items" to invoice.items.take(100).map { it.toMap() },
                    "subtotal" to invoice.subtotal.coerceAtLeast(0.0),
                    "discountAmount" to invoice.discountAmount.coerceAtLeast(0.0),
                    "taxableTotal" to invoice.taxableTotal.coerceAtLeast(0.0),
                    "cgstTotal" to invoice.cgstTotal.coerceAtLeast(0.0),
                    "sgstTotal" to invoice.sgstTotal.coerceAtLeast(0.0),
                    "igstTotal" to invoice.igstTotal.coerceAtLeast(0.0),
                    "grandTotal" to invoice.grandTotal.coerceAtLeast(0.0),
                    "paidAmount" to invoice.paidAmount.coerceAtLeast(0.0),
                    "balanceDue" to invoice.balanceDue.coerceAtLeast(0.0),
                    "paymentStatus" to invoice.paymentStatus,
                    "paymentMethod" to invoice.paymentMethod,
                    "upiTransactionRef" to invoice.upiTransactionRef.take(100),
                    "notes" to invoice.notes.take(300),
                    "templateStyle" to invoice.templateStyle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteInvoice(invoiceId: String): Result<Unit> {
        var path = "users/unknown/invoices/$invoiceId"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("invoices").document(invoiceId)
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

    suspend fun createPendingUpiPayment(
        invoice: Invoice,
        amount: Double,
        upiApp: String,
        upiId: String,
        notes: String
    ): Result<PaymentRecord> {
        var path = "users/unknown/payments"
        return try {
            val uid = requireUserId()
            val payId = "pay_${UUID.randomUUID().toString().take(12)}"
            val record = PaymentRecord(
                id = payId,
                userId = uid,
                invoiceId = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                customerId = invoice.customerId,
                customerName = invoice.customerName,
                amount = amount.coerceAtLeast(0.0),
                method = "UPI_QR",
                upiApp = upiApp,
                upiId = upiId,
                transactionRef = "",
                verificationStatus = "PENDING",
                verifiedByOwner = false,
                notes = notes.ifBlank { "UPI QR initiated via $upiApp; awaiting verification" },
                paymentEpochMs = System.currentTimeMillis()
            )
            val ref = userDoc(uid).collection("payments").document(payId)
            path = ref.path
            ref.set(record.toCreateMap(uid)).await()
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun verifyOrUpdatePayment(
        payment: PaymentRecord,
        invoice: Invoice?,
        customer: Customer?,
        transactionRef: String,
        newStatus: String, // VERIFIED or FAILED
        notes: String
    ): Result<Unit> {
        var path = "users/unknown/payments/${payment.id}"
        return try {
            val uid = requireUserId()
            if (payment.method == "UPI_QR" && newStatus == "VERIFIED") {
                require(transactionRef.trim().length >= 4) {
                    "Valid 4+ character UTR / Transaction Reference is required before verifying UPI payment."
                }
            }
            val ref = userDoc(uid).collection("payments").document(payment.id)
            path = ref.path
            ref.update(
                mapOf(
                    "transactionRef" to transactionRef.trim().take(100),
                    "verificationStatus" to newStatus,
                    "verifiedByOwner" to (newStatus == "VERIFIED"),
                    "notes" to notes.take(250),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (invoice != null) {
                if (newStatus == "VERIFIED" && payment.verificationStatus != "VERIFIED") {
                    val updatedPaid = (invoice.paidAmount + payment.amount).coerceAtMost(invoice.grandTotal)
                    val updatedBal = (invoice.grandTotal - updatedPaid).coerceAtLeast(0.0)
                    val invStatus = if (updatedBal <= 0.01) "PAID" else "PARTIAL"
                    userDoc(uid).collection("invoices").document(invoice.id).update(
                        mapOf(
                            "paidAmount" to updatedPaid,
                            "balanceDue" to updatedBal,
                            "paymentStatus" to invStatus,
                            "paymentMethod" to payment.method,
                            "upiTransactionRef" to transactionRef.trim().take(100),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    if (customer != null) {
                        val newCustBal = (customer.outstandingBalance - payment.amount).coerceAtLeast(0.0)
                        userDoc(uid).collection("customers").document(customer.id).update(
                            mapOf(
                                "outstandingBalance" to newCustBal,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                } else if (newStatus == "FAILED" && invoice.paidAmount <= 0.0) {
                    userDoc(uid).collection("invoices").document(invoice.id).update(
                        mapOf(
                            "paidAmount" to invoice.paidAmount,
                            "balanceDue" to invoice.balanceDue,
                            "paymentStatus" to "FAILED",
                            "paymentMethod" to payment.method,
                            "upiTransactionRef" to transactionRef.trim().take(100),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordCashOrBankPayment(
        invoice: Invoice,
        customer: Customer?,
        amount: Double,
        method: String,
        reference: String
    ): Result<Unit> {
        var path = "users/unknown/payments"
        return try {
            val uid = requireUserId()
            val payId = "pay_${UUID.randomUUID().toString().take(12)}"
            val cleanAmt = amount.coerceIn(0.01, invoice.balanceDue.coerceAtLeast(0.01))
            val record = PaymentRecord(
                userId = uid,
                invoiceId = invoice.id,
                invoiceNumber = invoice.invoiceNumber,
                customerId = invoice.customerId,
                customerName = invoice.customerName,
                amount = cleanAmt,
                method = method,
                upiApp = "",
                upiId = "",
                transactionRef = reference.ifBlank { "$method-${invoice.invoiceNumber}" },
                verificationStatus = "VERIFIED",
                verifiedByOwner = true,
                notes = "Verified receipt",
                paymentEpochMs = System.currentTimeMillis()
            )
            val ref = userDoc(uid).collection("payments").document(payId)
            path = ref.path
            ref.set(record.toCreateMap(uid)).await()

            val updatedPaid = (invoice.paidAmount + cleanAmt).coerceAtMost(invoice.grandTotal)
            val updatedBal = (invoice.grandTotal - updatedPaid).coerceAtLeast(0.0)
            val status = if (updatedBal <= 0.01) "PAID" else "PARTIAL"
            userDoc(uid).collection("invoices").document(invoice.id).update(
                mapOf(
                    "paidAmount" to updatedPaid,
                    "balanceDue" to updatedBal,
                    "paymentStatus" to status,
                    "paymentMethod" to method,
                    "upiTransactionRef" to record.transactionRef,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (customer != null) {
                userDoc(uid).collection("customers").document(customer.id).update(
                    mapOf(
                        "outstandingBalance" to (customer.outstandingBalance - cleanAmt).coerceAtLeast(0.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveSupplier(supplier: Supplier): Result<String> {
        var path = "users/unknown/suppliers"
        return try {
            val uid = requireUserId()
            val isNew = supplier.id.isBlank()
            val id = if (isNew) "sup_${UUID.randomUUID().toString().take(12)}" else supplier.id
            val ref = userDoc(uid).collection("suppliers").document(id)
            path = ref.path
            if (isNew) {
                ref.set(supplier.toCreateMap(uid)).await()
            } else {
                ref.update(
                    mapOf(
                        "name" to supplier.name.trim().take(100),
                        "phone" to supplier.phone.trim().take(20),
                        "address" to supplier.address.trim().take(250),
                        "gstin" to supplier.gstin.trim().take(20),
                        "totalPurchased" to supplier.totalPurchased.coerceAtLeast(0.0),
                        "outstandingPayable" to supplier.outstandingPayable.coerceAtLeast(0.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun createPurchaseOrReturn(
        purchase: PurchaseRecord,
        inventory: List<Product>,
        supplier: Supplier?
    ): Result<String> {
        var path = "users/unknown/purchases"
        return try {
            val uid = requireUserId()
            val id = "pur_${UUID.randomUUID().toString().take(12)}"
            val ref = userDoc(uid).collection("purchases").document(id)
            path = ref.path
            ref.set(purchase.copy(userId = uid).toCreateMap(uid)).await()

            val isReturn = purchase.recordType == "RETURN"
            val nowMs = System.currentTimeMillis()
            purchase.items.forEach { item ->
                if (item.productId.isNotBlank()) {
                    val prod = inventory.firstOrNull { it.id == item.productId }
                    if (prod != null) {
                        val delta = if (isReturn) -item.qty else item.qty
                        val newStock = (prod.stockQty + delta).coerceAtLeast(0.0)
                        userDoc(uid).collection("products").document(prod.id).update(
                            mapOf(
                                "stockQty" to newStock,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        val adjId = "adj_${UUID.randomUUID().toString().take(12)}"
                        val adj = StockAdjustment(
                            userId = uid,
                            productId = prod.id,
                            productName = prod.name,
                            deltaQty = delta,
                            previousQty = prod.stockQty,
                            newQty = newStock,
                            reason = "${purchase.recordType} ${purchase.purchaseNumber}",
                            adjustmentEpochMs = nowMs
                        )
                        userDoc(uid).collection("stock_adjustments").document(adjId)
                            .set(adj.toCreateMap(uid)).await()
                    }
                }
            }

            if (supplier != null) {
                val due = (purchase.grandTotal - purchase.paidAmount).coerceAtLeast(0.0)
                val newTotal = if (isReturn) {
                    (supplier.totalPurchased - purchase.grandTotal).coerceAtLeast(0.0)
                } else {
                    supplier.totalPurchased + purchase.grandTotal
                }
                val newPayable = if (isReturn) {
                    (supplier.outstandingPayable - due).coerceAtLeast(0.0)
                } else {
                    supplier.outstandingPayable + due
                }
                userDoc(uid).collection("suppliers").document(supplier.id).update(
                    mapOf(
                        "totalPurchased" to newTotal,
                        "outstandingPayable" to newPayable,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun paySupplierPurchase(
        purchase: PurchaseRecord,
        supplier: Supplier?,
        payAmount: Double
    ): Result<Unit> {
        var path = "users/unknown/purchases/${purchase.id}"
        return try {
            val uid = requireUserId()
            val newPaid = (purchase.paidAmount + payAmount).coerceAtMost(purchase.grandTotal)
            val status = when {
                newPaid >= purchase.grandTotal - 0.01 -> "PAID"
                newPaid > 0.0 -> "PARTIAL"
                else -> "UNPAID"
            }
            val ref = userDoc(uid).collection("purchases").document(purchase.id)
            path = ref.path
            ref.update(
                mapOf(
                    "paidAmount" to newPaid,
                    "paymentStatus" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (supplier != null) {
                userDoc(uid).collection("suppliers").document(supplier.id).update(
                    mapOf(
                        "outstandingPayable" to (supplier.outstandingPayable - payAmount).coerceAtLeast(0.0),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveExpense(expense: Expense): Result<String> {
        var path = "users/unknown/expenses"
        return try {
            val uid = requireUserId()
            val id = if (expense.id.isBlank()) "exp_${UUID.randomUUID().toString().take(12)}" else expense.id
            val ref = userDoc(uid).collection("expenses").document(id)
            path = ref.path
            if (expense.id.isBlank()) {
                ref.set(expense.toCreateMap(uid)).await()
            } else {
                ref.update(
                    mapOf(
                        "title" to expense.title.trim().take(120),
                        "category" to expense.category.trim().take(60),
                        "amount" to expense.amount.coerceAtLeast(0.0),
                        "paymentMode" to expense.paymentMode.take(30),
                        "notes" to expense.notes.take(250),
                        "expenseEpochMs" to expense.expenseEpochMs,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteExpense(expenseId: String): Result<Unit> {
        var path = "users/unknown/expenses/$expenseId"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid).collection("expenses").document(expenseId)
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
}
