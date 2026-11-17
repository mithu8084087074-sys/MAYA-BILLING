package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.Product
import com.example.model.PurchaseLineItem
import com.example.model.PurchaseRecord
import com.example.model.Supplier
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoFontFamily
import com.example.util.BillingCalculator
import com.example.util.PdfInvoiceGenerator
import com.example.viewmodel.BillingWorkspaceData

@Composable
fun PartiesScreen(
    workspace: BillingWorkspaceData,
    onSaveCustomer: (Customer, () -> Unit) -> Unit,
    onDeleteCustomer: (Customer) -> Unit,
    onSaveSupplier: (Supplier, () -> Unit) -> Unit,
    onCreatePurchase: (PurchaseRecord, List<Product>, Supplier?, () -> Unit) -> Unit,
    onPaySupplierPurchase: (PurchaseRecord, Supplier?, Double) -> Unit,
    onSaveExpense: (Expense, () -> Unit) -> Unit,
    onDeleteExpense: (Expense) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    var showCustomerDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }
    var detailCustomer by remember { mutableStateOf<Customer?>(null) }

    var showSupplierDialog by remember { mutableStateOf(false) }
    var showPurchaseDialog by remember { mutableStateOf(false) }
    var purchaseType by remember { mutableStateOf("PURCHASE") }
    var payingPurchase by remember { mutableStateOf<PurchaseRecord?>(null) }

    var showExpenseDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Customers") })
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Purchases") }
            )
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Expenses") })
        }

        when (selectedTab) {
            // CUSTOMERS TAB
            0 -> {
                val filtered = remember(workspace.customers, search) {
                    workspace.customers.filter {
                        search.isBlank() ||
                            it.name.contains(search, ignoreCase = true) ||
                            it.phone.contains(search, ignoreCase = true)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = { Text("Search customers...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            editingCustomer = null
                            showCustomerDialog = true
                        },
                        modifier = Modifier.height(52.dp).testTag("add_customer_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Rounded.Group,
                                title = "No Customers Yet",
                                subtitle = "Add customers to track purchase history, Khata balances, and WhatsApp bills."
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { cust ->
                            ElevatedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { detailCustomer = cust },
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(cust.name, fontWeight = FontWeight.Bold)
                                        if (cust.phone.isNotBlank()) {
                                            Text(cust.phone, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            "Total Sales: ${BillingCalculator.formatInr(cust.totalPurchases)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            BillingCalculator.formatInr(cust.outstandingBalance),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                            fontWeight = FontWeight.Bold,
                                            color = if (cust.outstandingBalance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                        )
                                        Text("Due", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SUPPLIERS & PURCHASES TAB
            1 -> {
                if (!workspace.config.purchaseModuleEnabled) {
                    EmptyStateCard(
                        icon = Icons.Rounded.LocalShipping,
                        title = "Purchase Module Disabled",
                        subtitle = "Enable Purchase Management inside Owner Admin > Feature Controls.",
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { showSupplierDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Supplier")
                                }
                                OutlinedButton(
                                    onClick = {
                                        purchaseType = "PURCHASE"
                                        showPurchaseDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ Purchase")
                                }
                                OutlinedButton(
                                    onClick = {
                                        purchaseType = "RETURN"
                                        showPurchaseDialog = true
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Return")
                                }
                            }
                        }

                        if (workspace.suppliers.isNotEmpty()) {
                            item {
                                Text("Suppliers (${workspace.suppliers.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            items(workspace.suppliers, key = { it.id }) { sup ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(sup.name, fontWeight = FontWeight.Bold)
                                            Text("Ph: ${sup.phone.ifBlank { "N/A" }} • GSTIN: ${sup.gstin.ifBlank { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(BillingCalculator.formatInr(sup.outstandingPayable), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                            Text("Payable", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Text("Purchase & Return History (${workspace.purchases.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                        if (workspace.purchases.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Rounded.LocalShipping,
                                    title = "No Purchases Recorded",
                                    subtitle = "Log supplier purchases or returns to automatically update product stock."
                                )
                            }
                        } else {
                            items(workspace.purchases, key = { it.id }) { pur ->
                                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(pur.purchaseNumber, fontWeight = FontWeight.Bold)
                                                StatusPill(pur.recordType)
                                                StatusPill(pur.paymentStatus)
                                            }
                                            Text(
                                                BillingCalculator.formatInr(pur.grandTotal),
                                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text("${pur.supplierName} • Paid: ${BillingCalculator.formatInr(pur.paidAmount)}", style = MaterialTheme.typography.bodySmall)
                                        if (pur.paymentStatus != "PAID" && pur.recordType == "PURCHASE") {
                                            OutlinedButton(onClick = { payingPurchase = pur }) {
                                                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Pay Supplier")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // EXPENSES TAB
            2 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Button(
                            onClick = { showExpenseDialog = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_expense_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Record Business Expense")
                        }
                    }
                    if (workspace.expenses.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Rounded.MoneyOff,
                                title = "No Expenses Logged",
                                subtitle = "Track rent, electricity, salaries, and transport for true Net Profit reports."
                            )
                        }
                    } else {
                        items(workspace.expenses, key = { it.id }) { exp ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exp.title, fontWeight = FontWeight.Bold)
                                        Text("${exp.category} • ${exp.paymentMode} • ${BillingCalculator.formatShortDate(exp.expenseEpochMs)}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            BillingCalculator.formatInr(exp.amount),
                                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(onClick = { onDeleteExpense(exp) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Expense", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Customer Detail & Customer-wise Report Dialog
    detailCustomer?.let { cust ->
        val custInvoices = workspace.invoices.filter {
            it.customerId == cust.id || it.customerName.equals(cust.name, ignoreCase = true)
        }
        val custPayments = workspace.payments.filter {
            it.customerId == cust.id || it.customerName.equals(cust.name, ignoreCase = true)
        }
        AlertDialog(
            onDismissRequest = { detailCustomer = null },
            title = { Text("${cust.name} — Khata Report", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Mobile: ${cust.phone.ifBlank { "N/A" }}")
                    Text("Address: ${cust.address.ifBlank { "N/A" }}")
                    Text("Total Purchases: ${BillingCalculator.formatInr(cust.totalPurchases)}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Outstanding Balance: ${BillingCalculator.formatInr(cust.outstandingBalance)}",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider()
                    Text("Invoices (${custInvoices.size})", fontWeight = FontWeight.Bold)
                    custInvoices.take(8).forEach { inv ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${inv.invoiceNumber} (${inv.paymentStatus})", style = MaterialTheme.typography.bodySmall)
                            Text(BillingCalculator.formatInr(inv.grandTotal), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                    Text("Payments (${custPayments.size})", fontWeight = FontWeight.Bold)
                    custPayments.take(8).forEach { pay ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${pay.method} (${pay.verificationStatus})", style = MaterialTheme.typography.bodySmall)
                            Text(BillingCalculator.formatInr(pay.amount), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (custInvoices.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val latest = custInvoices.first()
                                val pdf = PdfInvoiceGenerator.generateInvoicePdf(context, latest, workspace.config)
                                val msg = "Hello ${cust.name}, your latest invoice ${latest.invoiceNumber} is ${BillingCalculator.formatInr(latest.grandTotal)}. Total balance due: ${BillingCalculator.formatInr(cust.outstandingBalance)}."
                                PdfInvoiceGenerator.shareOnWhatsApp(context, pdf, cust.phone, msg)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Share Statement on WhatsApp")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editingCustomer = cust
                    detailCustomer = null
                    showCustomerDialog = true
                }) { Text("Edit") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDeleteCustomer(cust)
                        detailCustomer = null
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { detailCustomer = null }) { Text("Close") }
                }
            }
        )
    }

    if (showCustomerDialog) {
        var name by remember { mutableStateOf(editingCustomer?.name ?: "") }
        var phone by remember { mutableStateOf(editingCustomer?.phone ?: "") }
        var address by remember { mutableStateOf(editingCustomer?.address ?: "") }
        var gstin by remember { mutableStateOf(editingCustomer?.gstin ?: "") }
        var stateName by remember { mutableStateOf(editingCustomer?.stateName ?: workspace.config.stateName) }

        AlertDialog(
            onDismissRequest = { showCustomerDialog = false },
            title = { Text(if (editingCustomer == null) "Add Customer" else "Edit Customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Customer Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Mobile") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gstin, onValueChange = { gstin = it.uppercase() }, label = { Text("GSTIN (Optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = stateName, onValueChange = { stateName = it }, label = { Text("State") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveCustomer(
                            Customer(
                                id = editingCustomer?.id ?: "",
                                userId = editingCustomer?.userId ?: "",
                                name = name.trim(),
                                phone = phone.trim(),
                                address = address.trim(),
                                gstin = gstin.trim(),
                                stateName = stateName.trim(),
                                totalPurchases = editingCustomer?.totalPurchases ?: 0.0,
                                outstandingBalance = editingCustomer?.outstandingBalance ?: 0.0
                            )
                        ) { showCustomerDialog = false }
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCustomerDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSupplierDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var gstin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSupplierDialog = false },
            title = { Text("Add Supplier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Supplier Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gstin, onValueChange = { gstin = it.uppercase() }, label = { Text("GSTIN") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveSupplier(Supplier(name = name, phone = phone, address = address, gstin = gstin)) {
                            showSupplierDialog = false
                        }
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSupplierDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPurchaseDialog) {
        var selectedSup by remember { mutableStateOf(workspace.suppliers.firstOrNull()) }
        var supName by remember { mutableStateOf(selectedSup?.name ?: "") }
        var selectedProd by remember { mutableStateOf(workspace.products.firstOrNull()) }
        var itemName by remember { mutableStateOf(selectedProd?.name ?: "") }
        var qtyStr by remember { mutableStateOf("10") }
        var priceStr by remember { mutableStateOf(selectedProd?.purchasePrice?.toString() ?: "") }
        var gstRateStr by remember { mutableStateOf(selectedProd?.gstRate?.toString() ?: "18") }
        var paidStr by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPurchaseDialog = false },
            title = { Text(if (purchaseType == "RETURN") "New Purchase Return" else "New Purchase Invoice") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (workspace.suppliers.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(workspace.suppliers, key = { it.id }) { sup ->
                                FilterChip(
                                    selected = selectedSup?.id == sup.id,
                                    onClick = {
                                        selectedSup = sup
                                        supName = sup.name
                                    },
                                    label = { Text(sup.name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = supName, onValueChange = { supName = it }, label = { Text("Supplier Name *") }, modifier = Modifier.fillMaxWidth())
                    if (workspace.products.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(workspace.products, key = { it.id }) { prod ->
                                FilterChip(
                                    selected = selectedProd?.id == prod.id,
                                    onClick = {
                                        selectedProd = prod
                                        itemName = prod.name
                                        priceStr = prod.purchasePrice.toString()
                                        gstRateStr = prod.gstRate.toString()
                                    },
                                    label = { Text(prod.name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = itemName, onValueChange = { itemName = it }, label = { Text("Product Name *") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = qtyStr, onValueChange = { qtyStr = it }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = priceStr, onValueChange = { priceStr = it }, label = { Text("Rate ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = gstRateStr, onValueChange = { gstRateStr = it }, label = { Text("GST %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = paidStr, onValueChange = { paidStr = it }, label = { Text("Paid ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.01)
                        val price = (priceStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        val gstPct = (gstRateStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                        val taxable = BillingCalculator.round2(qty * price)
                        val gstAmt = BillingCalculator.round2(taxable * gstPct / 100.0)
                        val grand = BillingCalculator.round2(taxable + gstAmt)
                        val paid = (paidStr.toDoubleOrNull() ?: grand).coerceIn(0.0, grand)
                        val status = when {
                            paid >= grand - 0.01 -> "PAID"
                            paid > 0.0 -> "PARTIAL"
                            else -> "UNPAID"
                        }
                        val pur = PurchaseRecord(
                            purchaseNumber = "${if (purchaseType == "RETURN") "RET" else "PUR"}-${System.currentTimeMillis().toString().takeLast(5)}",
                            supplierId = selectedSup?.id ?: "",
                            supplierName = supName.trim(),
                            recordType = purchaseType,
                            items = listOf(
                                PurchaseLineItem(
                                    productId = selectedProd?.id ?: "",
                                    name = itemName.trim(),
                                    qty = qty,
                                    unitPrice = price,
                                    gstRate = gstPct,
                                    total = grand
                                )
                            ),
                            taxableTotal = taxable,
                            gstTotal = gstAmt,
                            grandTotal = grand,
                            paidAmount = paid,
                            paymentStatus = status,
                            purchaseEpochMs = System.currentTimeMillis()
                        )
                        onCreatePurchase(pur, workspace.products, selectedSup) { showPurchaseDialog = false }
                    },
                    enabled = supName.isNotBlank() && itemName.isNotBlank() && priceStr.toDoubleOrNull() != null
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showPurchaseDialog = false }) { Text("Cancel") } }
        )
    }

    payingPurchase?.let { pur ->
        val due = (pur.grandTotal - pur.paidAmount).coerceAtLeast(0.0)
        var amtStr by remember { mutableStateOf("%.2f".format(due)) }
        val sup = workspace.suppliers.firstOrNull { it.id == pur.supplierId }
        AlertDialog(
            onDismissRequest = { payingPurchase = null },
            title = { Text("Pay Supplier • ${pur.purchaseNumber}") },
            text = {
                OutlinedTextField(
                    value = amtStr,
                    onValueChange = { amtStr = it },
                    label = { Text("Payment Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amtStr.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        onPaySupplierPurchase(pur, sup, amt)
                        payingPurchase = null
                    }
                }) { Text("Confirm Pay") }
            },
            dismissButton = { TextButton(onClick = { payingPurchase = null }) { Text("Cancel") } }
        )
    }

    if (showExpenseDialog) {
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Operations") }
        var amountStr by remember { mutableStateOf("") }
        var mode by remember { mutableStateOf("CASH") }
        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            title = { Text("Log Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Expense Title *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category (Rent, Electricity, Salary)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text("Amount (₹) *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CASH", "UPI", "BANK").forEach { m ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveExpense(
                            Expense(
                                title = title.trim(),
                                category = category.trim(),
                                amount = (amountStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                paymentMode = mode,
                                expenseEpochMs = System.currentTimeMillis()
                            )
                        ) { showExpenseDialog = false }
                    },
                    enabled = title.isNotBlank() && (amountStr.toDoubleOrNull() ?: 0.0) > 0.0
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showExpenseDialog = false }) { Text("Cancel") } }
        )
    }
}
