package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BillingRepository
import com.example.data.OwnerRepository
import com.example.model.BusinessConfig
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.Invoice
import com.example.model.PaymentRecord
import com.example.model.Product
import com.example.model.PurchaseRecord
import com.example.model.StockAdjustment
import com.example.model.Supplier
import com.example.util.BillingCalculator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class DashboardMetrics(
    val totalSales: Double = 0.0,
    val todaysSales: Double = 0.0,
    val monthlySales: Double = 0.0,
    val totalPurchases: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val grossProfit: Double = 0.0,
    val netProfit: Double = 0.0,
    val pendingPayments: Double = 0.0,
    val totalCustomers: Int = 0,
    val totalProducts: Int = 0,
    val lowStockProducts: List<Product> = emptyList(),
    val recentInvoices: List<Invoice> = emptyList(),
    val dailyChartPoints: List<Pair<String, Double>> = emptyList()
)

data class BillingWorkspaceData(
    val config: BusinessConfig = BusinessConfig(),
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val invoices: List<Invoice> = emptyList(),
    val payments: List<PaymentRecord> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val purchases: List<PurchaseRecord> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val stockAdjustments: List<StockAdjustment> = emptyList(),
    val metrics: DashboardMetrics = DashboardMetrics()
)

class MainBillingViewModel(
    private val billingRepo: BillingRepository,
    private val ownerRepo: OwnerRepository,
    private val currentUserId: String,
    defaultDisplayName: String
) : ViewModel() {

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            ownerRepo.ensureWorkspaceInitialized(defaultDisplayName)
        }
    }

    private fun <T> Flow<T>.wrapErrors(label: String): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, "Stream error in $label", root)
        throw root
    }

    private val coreStreams = combine(
        billingRepo.observeBusinessConfig(currentUserId).wrapErrors("config"),
        billingRepo.observeProducts(currentUserId).wrapErrors("products"),
        billingRepo.observeCustomers(currentUserId).wrapErrors("customers"),
        billingRepo.observeInvoices(currentUserId).wrapErrors("invoices"),
        billingRepo.observePayments(currentUserId).wrapErrors("payments")
    ) { config, products, customers, invoices, payments ->
        listOf(config ?: BusinessConfig(userId = currentUserId), products, customers, invoices, payments)
    }

    private val partyStreams = combine(
        billingRepo.observeSuppliers(currentUserId).wrapErrors("suppliers"),
        billingRepo.observePurchases(currentUserId).wrapErrors("purchases"),
        billingRepo.observeExpenses(currentUserId).wrapErrors("expenses"),
        billingRepo.observeStockAdjustments(currentUserId).wrapErrors("adjustments")
    ) { suppliers, purchases, expenses, adjustments ->
        listOf(suppliers, purchases, expenses, adjustments)
    }

    @Suppress("UNCHECKED_CAST")
    val workspaceState: StateFlow<UiState<BillingWorkspaceData>> = combine(coreStreams, partyStreams) { core, party ->
        val config = core[0] as BusinessConfig
        val products = core[1] as List<Product>
        val customers = core[2] as List<Customer>
        val invoices = core[3] as List<Invoice>
        val payments = core[4] as List<PaymentRecord>
        val suppliers = party[0] as List<Supplier>
        val purchases = party[1] as List<PurchaseRecord>
        val expenses = party[2] as List<Expense>
        val adjustments = party[3] as List<StockAdjustment>

        val todayStart = BillingCalculator.startOfTodayEpochMs()
        val monthStart = BillingCalculator.startOfMonthEpochMs()

        val totalSales = invoices.sumOf { it.grandTotal }
        val todaysSales = invoices.filter { it.invoiceEpochMs >= todayStart }.sumOf { it.grandTotal }
        val monthlySales = invoices.filter { it.invoiceEpochMs >= monthStart }.sumOf { it.grandTotal }

        val purchaseTotal = purchases.filter { it.recordType == "PURCHASE" }.sumOf { it.grandTotal } -
            purchases.filter { it.recordType == "RETURN" }.sumOf { it.grandTotal }
        val expenseTotal = expenses.sumOf { it.amount }
        val grossProfit = totalSales - purchaseTotal.coerceAtLeast(0.0)
        val netProfit = grossProfit - expenseTotal
        val pendingPayments = invoices.sumOf { it.balanceDue }
        val lowStock = products.filter { it.stockQty <= it.lowStockThreshold }

        // Last 7 days bar points
        val dayMs = 86_400_000L
        val labels = listOf("6d", "5d", "4d", "3d", "2d", "Yest", "Today")
        val points = (6 downTo 0).mapIndexed { idx, daysAgo ->
            val start = todayStart - daysAgo * dayMs
            val end = start + dayMs
            val sum = invoices.filter { it.invoiceEpochMs in start until end }.sumOf { it.grandTotal }
            labels[idx] to sum
        }

        val metrics = DashboardMetrics(
            totalSales = totalSales,
            todaysSales = todaysSales,
            monthlySales = monthlySales,
            totalPurchases = purchaseTotal.coerceAtLeast(0.0),
            totalExpenses = expenseTotal,
            grossProfit = grossProfit,
            netProfit = netProfit,
            pendingPayments = pendingPayments,
            totalCustomers = customers.size,
            totalProducts = products.size,
            lowStockProducts = lowStock,
            recentInvoices = invoices.take(8),
            dailyChartPoints = points
        )

        BillingWorkspaceData(
            config = config,
            products = products,
            customers = customers,
            invoices = invoices,
            payments = payments,
            suppliers = suppliers,
            purchases = purchases,
            expenses = expenses,
            stockAdjustments = adjustments,
            metrics = metrics
        )
    }
        .map<BillingWorkspaceData, UiState<BillingWorkspaceData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(TAG, "Workspace observation error", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to load business workspace"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun showMessage(msg: String) {
        _actionMessage.value = msg
    }

    fun saveProduct(product: Product, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.saveProduct(product)
            _isBusy.value = false
            res.onSuccess {
                ownerRepo.logActivity("Billing/Staff", "Saved Product", product.name, "INVENTORY")
                _actionMessage.value = "Product '${product.name}' saved."
                onSuccess()
            }.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Failed to save product"
            }
        }
    }

    fun deleteProduct(product: Product) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.deleteProduct(product.id)
            _isBusy.value = false
            res.onSuccess {
                ownerRepo.logActivity("Owner/Staff", "Deleted Product", product.name, "INVENTORY")
                _actionMessage.value = "Deleted '${product.name}'."
            }.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Delete failed"
            }
        }
    }

    fun adjustStock(product: Product, deltaQty: Double, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.adjustProductStock(product, deltaQty, reason)
            _isBusy.value = false
            res.onSuccess {
                _actionMessage.value = "Stock adjusted for ${product.name}."
            }.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Stock adjustment failed"
            }
        }
    }

    fun saveCustomer(customer: Customer, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.saveCustomer(customer)
            _isBusy.value = false
            res.onSuccess { id ->
                _actionMessage.value = "Customer '${customer.name}' saved."
                onSuccess(id)
            }.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Failed to save customer"
            }
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            _isBusy.value = true
            billingRepo.deleteCustomer(customer.id)
                .onSuccess { _actionMessage.value = "Deleted customer '${customer.name}'." }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to delete customer" }
            _isBusy.value = false
        }
    }

    fun createInvoice(
        invoice: Invoice,
        workspace: BillingWorkspaceData,
        upiApp: String = "GPay",
        onCreated: (Invoice) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.createInvoice(
                invoice = invoice,
                config = workspace.config,
                inventory = workspace.products,
                customers = workspace.customers,
                upiAppSelected = upiApp
            )
            _isBusy.value = false
            res.onSuccess { created ->
                ownerRepo.logActivity(
                    "Biller",
                    "Created Invoice ${created.invoiceNumber}",
                    "${created.customerName} • ${BillingCalculator.formatInr(created.grandTotal)} (${created.paymentStatus})",
                    "BILLING"
                )
                _actionMessage.value = if (created.paymentMethod == "UPI_QR") {
                    "Invoice ${created.invoiceNumber} created. UPI QR payment is PENDING owner UTR verification."
                } else {
                    "Invoice ${created.invoiceNumber} saved!"
                }
                onCreated(created)
            }.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Failed to create invoice"
            }
        }
    }

    fun updateInvoice(invoice: Invoice, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.updateExistingInvoice(invoice)
            _isBusy.value = false
            res.onSuccess {
                ownerRepo.logActivity("Owner/Biller", "Edited Invoice ${invoice.invoiceNumber}", invoice.customerName, "BILLING")
                _actionMessage.value = "Invoice ${invoice.invoiceNumber} updated."
                onSuccess()
            }.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Failed to update invoice"
            }
        }
    }

    fun deleteInvoice(invoice: Invoice) {
        viewModelScope.launch {
            _isBusy.value = true
            billingRepo.deleteInvoice(invoice.id)
                .onSuccess {
                    ownerRepo.logActivity("Owner", "Deleted Invoice ${invoice.invoiceNumber}", invoice.customerName, "BILLING")
                    _actionMessage.value = "Invoice ${invoice.invoiceNumber} deleted."
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Delete failed" }
            _isBusy.value = false
        }
    }

    fun createPendingUpiPayment(
        invoice: Invoice,
        amount: Double,
        upiApp: String,
        upiId: String,
        notes: String,
        onCreated: (PaymentRecord) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.createPendingUpiPayment(invoice, amount, upiApp, upiId, notes)
            _isBusy.value = false
            res.onSuccess { rec ->
                _actionMessage.value = "UPI Payment pending verification. Confirm UTR reference once received."
                onCreated(rec)
            }.onFailure {
                _actionMessage.value = it.localizedMessage ?: "Failed to initiate UPI payment"
            }
        }
    }

    fun verifyPayment(
        payment: PaymentRecord,
        invoice: Invoice?,
        customer: Customer?,
        transactionRef: String,
        newStatus: String,
        notes: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = billingRepo.verifyOrUpdatePayment(
                payment = payment,
                invoice = invoice,
                customer = customer,
                transactionRef = transactionRef,
                newStatus = newStatus,
                notes = notes
            )
            _isBusy.value = false
            res.onSuccess {
                ownerRepo.logActivity(
                    "Owner",
                    "Payment $newStatus (${payment.invoiceNumber})",
                    "Amt ${BillingCalculator.formatInr(payment.amount)} • Ref: $transactionRef",
                    "PAYMENTS"
                )
                _actionMessage.value = "Payment marked $newStatus."
                onDone()
            }.onFailure { e ->
                _actionMessage.value = e.localizedMessage ?: "Verification failed"
            }
        }
    }

    fun recordDirectPayment(
        invoice: Invoice,
        customer: Customer?,
        amount: Double,
        method: String,
        reference: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            billingRepo.recordCashOrBankPayment(invoice, customer, amount, method, reference)
                .onSuccess {
                    ownerRepo.logActivity("Biller", "Recorded $method Payment", "${invoice.invoiceNumber} • ${BillingCalculator.formatInr(amount)}", "PAYMENTS")
                    _actionMessage.value = "Payment recorded & invoice updated."
                    onDone()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Payment failed" }
            _isBusy.value = false
        }
    }

    fun saveSupplier(supplier: Supplier, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            billingRepo.saveSupplier(supplier)
                .onSuccess {
                    _actionMessage.value = "Supplier '${supplier.name}' saved."
                    onSuccess()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save supplier" }
            _isBusy.value = false
        }
    }

    fun createPurchase(
        purchase: PurchaseRecord,
        inventory: List<Product>,
        supplier: Supplier?,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            billingRepo.createPurchaseOrReturn(purchase, inventory, supplier)
                .onSuccess {
                    ownerRepo.logActivity(
                        "Manager",
                        "${purchase.recordType} ${purchase.purchaseNumber}",
                        "${purchase.supplierName} • ${BillingCalculator.formatInr(purchase.grandTotal)}",
                        "PURCHASES"
                    )
                    _actionMessage.value = "${purchase.recordType} saved & inventory updated."
                    onSuccess()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save purchase" }
            _isBusy.value = false
        }
    }

    fun paySupplierPurchase(purchase: PurchaseRecord, supplier: Supplier?, payAmount: Double) {
        viewModelScope.launch {
            _isBusy.value = true
            billingRepo.paySupplierPurchase(purchase, supplier, payAmount)
                .onSuccess { _actionMessage.value = "Supplier payment recorded." }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to record supplier payment" }
            _isBusy.value = false
        }
    }

    fun saveExpense(expense: Expense, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            billingRepo.saveExpense(expense)
                .onSuccess {
                    ownerRepo.logActivity("Owner/Staff", "Logged Expense", "${expense.title} • ${BillingCalculator.formatInr(expense.amount)}", "EXPENSES")
                    _actionMessage.value = "Expense saved."
                    onSuccess()
                }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Failed to save expense" }
            _isBusy.value = false
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            billingRepo.deleteExpense(expense.id)
                .onSuccess { _actionMessage.value = "Expense deleted." }
                .onFailure { _actionMessage.value = it.localizedMessage ?: "Delete failed" }
        }
    }

    private companion object {
        const val TAG = "MainBillingVM"
    }
}
