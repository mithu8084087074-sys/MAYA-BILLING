package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.ReceiptLong
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.model.Invoice
import com.example.model.InvoiceLineItem
import com.example.model.PaymentRecord
import com.example.model.Product
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoFontFamily
import com.example.util.BillingCalculator
import com.example.util.PdfInvoiceGenerator
import com.example.viewmodel.BillingWorkspaceData

@Composable
fun BillingScreen(
    workspace: BillingWorkspaceData,
    initialBuilderOpen: Boolean,
    voicePrefillItems: List<InvoiceLineItem>?,
    voicePrefillCustomer: String?,
    voicePrefillDiscount: Double?,
    onConsumeBuilderTrigger: () -> Unit,
    onOpenVoiceSheet: () -> Unit,
    onCreateInvoice: (Invoice, String, (Invoice) -> Unit) -> Unit,
    onUpdateInvoice: (Invoice, () -> Unit) -> Unit,
    onDeleteInvoice: (Invoice) -> Unit,
    onCreatePendingUpi: (Invoice, Double, String, String, String) -> Unit,
    onVerifyPayment: (PaymentRecord, Invoice?, Customer?, String, String, String) -> Unit,
    onRecordDirectPayment: (Invoice, Customer?, Double, String, String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var showBuilder by remember(initialBuilderOpen) { mutableStateOf(initialBuilderOpen) }
    var editingInvoice by remember { mutableStateOf<Invoice?>(null) }
    var selectedInvoice by remember { mutableStateOf<Invoice?>(null) }
    var upiDialogInvoice by remember { mutableStateOf<Invoice?>(null) }
    var cashPaymentInvoice by remember { mutableStateOf<Invoice?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") }

    if (voicePrefillItems != null && voicePrefillItems.isNotEmpty() && !showBuilder) {
        showBuilder = true
    }

    if (showBuilder || editingInvoice != null) {
        BackHandler {
            showBuilder = false
            editingInvoice = null
            onConsumeBuilderTrigger()
        }
        InvoiceBuilderScreen(
            workspace = workspace,
            existingInvoice = editingInvoice,
            prefillItems = voicePrefillItems,
            prefillCustomer = voicePrefillCustomer,
            prefillDiscount = voicePrefillDiscount,
            onCancel = {
                showBuilder = false
                editingInvoice = null
                onConsumeBuilderTrigger()
            },
            onOpenVoice = onOpenVoiceSheet,
            onSave = { built, upiApp ->
                if (editingInvoice != null) {
                    onUpdateInvoice(built) {
                        editingInvoice = null
                        showBuilder = false
                        onConsumeBuilderTrigger()
                    }
                } else {
                    onCreateInvoice(built, upiApp) { created ->
                        showBuilder = false
                        onConsumeBuilderTrigger()
                        if (created.paymentMethod == "UPI_QR") {
                            upiDialogInvoice = created
                        } else {
                            selectedInvoice = created
                        }
                    }
                }
            }
        )
        return
    }

    val filteredInvoices = remember(workspace.invoices, searchQuery, statusFilter) {
        workspace.invoices.filter { inv ->
            val matchesQuery = searchQuery.isBlank() ||
                inv.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                inv.customerName.contains(searchQuery, ignoreCase = true) ||
                inv.customerPhone.contains(searchQuery, ignoreCase = true)
            val matchesStatus = statusFilter == "ALL" || inv.paymentStatus == statusFilter
            matchesQuery && matchesStatus
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showBuilder = true },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("billing_new_invoice_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New Invoice")
            }
            if (workspace.config.voiceBillingEnabled) {
                OutlinedButton(
                    onClick = onOpenVoiceSheet,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("billing_voice_button")
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Voice Bill")
                }
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search invoice #, customer, phone...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(listOf("ALL", "PAID", "PARTIAL", "UNPAID", "FAILED")) { st ->
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { statusFilter = st },
                    label = { Text(st) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (filteredInvoices.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.ReceiptLong,
                        title = "No Invoices Found",
                        subtitle = "Create GST or Non-GST bills manually or using Hindi & English Voice Billing."
                    )
                }
            } else {
                items(filteredInvoices, key = { it.id }) { inv ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedInvoice = inv },
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        inv.invoiceNumber,
                                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                        fontWeight = FontWeight.Bold
                                    )
                                    StatusPill(status = inv.paymentStatus)
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            inv.invoiceType,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    BillingCalculator.formatInr(inv.grandTotal),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "${inv.customerName} (${inv.items.size} items)",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    BillingCalculator.formatShortDate(inv.invoiceEpochMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(onClick = {
                                    val file = PdfInvoiceGenerator.generateInvoicePdf(context, inv, workspace.config)
                                    PdfInvoiceGenerator.sharePdf(context, file, "Invoice ${inv.invoiceNumber}")
                                }) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("PDF")
                                }
                                TextButton(onClick = {
                                    val file = PdfInvoiceGenerator.generateInvoicePdf(context, inv, workspace.config)
                                    val msg = "Invoice ${inv.invoiceNumber} from ${workspace.config.businessName}: Total ${BillingCalculator.formatInr(inv.grandTotal)}, Balance Due ${BillingCalculator.formatInr(inv.balanceDue)}."
                                    PdfInvoiceGenerator.shareOnWhatsApp(context, file, inv.customerPhone, msg)
                                }) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("WhatsApp")
                                }
                                if (inv.balanceDue > 0.0 && workspace.config.upiQrEnabled) {
                                    TextButton(onClick = { upiDialogInvoice = inv }) {
                                        Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("UPI QR")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Selected Invoice Detail Dialog
    selectedInvoice?.let { currentInv ->
        val liveInv = workspace.invoices.firstOrNull { it.id == currentInv.id } ?: currentInv
        AlertDialog(
            onDismissRequest = { selectedInvoice = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(liveInv.invoiceNumber, fontWeight = FontWeight.Bold)
                    StatusPill(status = liveInv.paymentStatus)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Customer: ${liveInv.customerName}", fontWeight = FontWeight.SemiBold)
                    if (liveInv.customerPhone.isNotBlank()) Text("Mobile: ${liveInv.customerPhone}", style = MaterialTheme.typography.bodySmall)
                    if (liveInv.customerAddress.isNotBlank()) Text("Address: ${liveInv.customerAddress}", style = MaterialTheme.typography.bodySmall)
                    Text("Template: ${liveInv.templateStyle} • Mode: ${liveInv.taxMode}", style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    liveInv.items.forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.name} (${item.qty} x ${item.unitPrice})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(BillingCalculator.formatInr(item.total), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Taxable Total"); Text(BillingCalculator.formatInr(liveInv.taxableTotal))
                    }
                    if (liveInv.cgstTotal > 0 || liveInv.sgstTotal > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CGST + SGST"); Text(BillingCalculator.formatInr(liveInv.cgstTotal + liveInv.sgstTotal))
                        }
                    }
                    if (liveInv.igstTotal > 0) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("IGST"); Text(BillingCalculator.formatInr(liveInv.igstTotal))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total", fontWeight = FontWeight.Bold)
                        Text(BillingCalculator.formatInr(liveInv.grandTotal), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid / Due")
                        Text("${BillingCalculator.formatInr(liveInv.paidAmount)} / ${BillingCalculator.formatInr(liveInv.balanceDue)}")
                    }

                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                val pdf = PdfInvoiceGenerator.generateInvoicePdf(context, liveInv, workspace.config)
                                PdfInvoiceGenerator.printPdf(context, pdf, "Invoice_${liveInv.invoiceNumber}")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Print")
                        }
                        OutlinedButton(
                            onClick = {
                                val pdf = PdfInvoiceGenerator.generateInvoicePdf(context, liveInv, workspace.config)
                                PdfInvoiceGenerator.sharePdf(context, pdf, "Invoice ${liveInv.invoiceNumber}")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("PDF")
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (liveInv.balanceDue > 0.0) {
                            Button(
                                onClick = {
                                    selectedInvoice = null
                                    upiDialogInvoice = liveInv
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("UPI QR")
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedInvoice = null
                                    cashPaymentInvoice = liveInv
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cash/Bank")
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                editingInvoice = liveInv
                                selectedInvoice = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit")
                        }
                        TextButton(
                            onClick = {
                                onDeleteInvoice(liveInv)
                                selectedInvoice = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedInvoice = null }) { Text("Close") }
            }
        )
    }

    // Dynamic UPI QR & Verification Dialog
    upiDialogInvoice?.let { targetInv ->
        val liveInv = workspace.invoices.firstOrNull { it.id == targetInv.id } ?: targetInv
        val linkedCust = workspace.customers.firstOrNull { it.id == liveInv.customerId }
        UpiPaymentDialog(
            invoice = liveInv,
            business = workspace.config,
            customer = linkedCust,
            existingPayments = workspace.payments,
            onDismiss = { upiDialogInvoice = null },
            onCreatePendingUpi = { amt, app, notes ->
                onCreatePendingUpi(liveInv, amt, app, workspace.config.upiId, notes)
            },
            onVerifyPayment = { pay, utr, status, notes ->
                onVerifyPayment(pay, liveInv, linkedCust, utr, status, notes)
            },
            onShowMessage = onShowMessage
        )
    }

    // Cash / Bank Payment Dialog
    cashPaymentInvoice?.let { targetInv ->
        var payAmt by remember { mutableStateOf("%.2f".format(targetInv.balanceDue)) }
        var method by remember { mutableStateOf("CASH") }
        var ref by remember { mutableStateOf("") }
        val linkedCust = workspace.customers.firstOrNull { it.id == targetInv.customerId }

        AlertDialog(
            onDismissRequest = { cashPaymentInvoice = null },
            title = { Text("Record Payment • ${targetInv.invoiceNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = payAmt,
                        onValueChange = { payAmt = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("CASH", "BANK").forEach { m ->
                            FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) })
                        }
                    }
                    OutlinedTextField(
                        value = ref,
                        onValueChange = { ref = it },
                        label = { Text("Receipt / Reference #") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = payAmt.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) {
                        onRecordDirectPayment(targetInv, linkedCust, amt, method, ref)
                        cashPaymentInvoice = null
                    }
                }) { Text("Save Payment") }
            },
            dismissButton = {
                TextButton(onClick = { cashPaymentInvoice = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InvoiceBuilderScreen(
    workspace: BillingWorkspaceData,
    existingInvoice: Invoice?,
    prefillItems: List<InvoiceLineItem>?,
    prefillCustomer: String?,
    prefillDiscount: Double?,
    onCancel: () -> Unit,
    onOpenVoice: () -> Unit,
    onSave: (Invoice, String) -> Unit
) {
    var isGst by remember(existingInvoice) {
        mutableStateOf(
            existingInvoice?.let { it.invoiceType == "GST" } ?: workspace.config.gstBillingEnabled
        )
    }
    var taxMode by remember(existingInvoice) {
        mutableStateOf(existingInvoice?.taxMode ?: if (isGst) "INTRA_STATE" else "NONE")
    }
    var templateStyle by remember(existingInvoice) {
        mutableStateOf(existingInvoice?.templateStyle ?: workspace.config.invoiceTemplate)
    }
    var selectedCustomer by remember(existingInvoice) {
        mutableStateOf(workspace.customers.firstOrNull { it.id == existingInvoice?.customerId })
    }
    var customerName by remember(existingInvoice, prefillCustomer) {
        mutableStateOf(existingInvoice?.customerName ?: prefillCustomer ?: "Walk-in Customer")
    }
    var customerPhone by remember(existingInvoice) { mutableStateOf(existingInvoice?.customerPhone ?: "") }
    var customerAddress by remember(existingInvoice) { mutableStateOf(existingInvoice?.customerAddress ?: "") }
    var customerGstin by remember(existingInvoice) { mutableStateOf(existingInvoice?.customerGstin ?: "") }

    var items by remember(existingInvoice, prefillItems) {
        mutableStateOf(
            existingInvoice?.items ?: prefillItems ?: emptyList()
        )
    }
    var discountInput by remember(existingInvoice, prefillDiscount) {
        mutableStateOf(
            (existingInvoice?.discountAmount ?: prefillDiscount ?: 0.0).takeIf { it > 0 }?.toString() ?: ""
        )
    }
    var paymentMethod by remember(existingInvoice) { mutableStateOf(existingInvoice?.paymentMethod ?: "CASH") }
    var paidInput by remember(existingInvoice) {
        mutableStateOf(existingInvoice?.paidAmount?.toString() ?: "")
    }
    var payFullToggle by remember(existingInvoice) {
        mutableStateOf(existingInvoice == null || existingInvoice.paymentStatus == "PAID")
    }
    var showAddProductDialog by remember { mutableStateOf(false) }

    val recalculatedLines = remember(items, isGst, taxMode) {
        items.map { item ->
            val calc = BillingCalculator.calculateLineItem(
                item.qty, item.unitPrice, item.discountPercent, item.gstRate, isGst, taxMode
            )
            item.copy(
                taxableAmount = calc.taxableAmount,
                cgst = calc.cgst,
                sgst = calc.sgst,
                igst = calc.igst,
                total = calc.total
            )
        }
    }

    val extraDiscount = discountInput.toDoubleOrNull() ?: 0.0
    val lineCalcs = recalculatedLines.map {
        com.example.util.LineCalculationResult(it.taxableAmount, it.cgst, it.sgst, it.igst, it.total)
    }
    val preliminaryTotals = BillingCalculator.calculateInvoiceTotals(lineCalcs, extraDiscount, 0.0)
    val effectivePaid = when {
        paymentMethod == "CREDIT" -> 0.0
        payFullToggle -> preliminaryTotals.grandTotal
        else -> (paidInput.toDoubleOrNull() ?: 0.0).coerceIn(0.0, preliminaryTotals.grandTotal)
    }
    val totals = BillingCalculator.calculateInvoiceTotals(lineCalcs, extraDiscount, effectivePaid)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (existingInvoice != null) "Edit ${existingInvoice.invoiceNumber}" else "New Invoice",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            if (workspace.config.voiceBillingEnabled) {
                OutlinedButton(onClick = onOpenVoice) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Voice")
                }
            }
        }

        // GST / Tax Mode / Template Chips
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GST Billing", fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = isGst,
                        onCheckedChange = { enabled ->
                            isGst = enabled
                            taxMode = if (enabled) "INTRA_STATE" else "NONE"
                        }
                    )
                }
                if (isGst) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = taxMode == "INTRA_STATE",
                            onClick = { taxMode = "INTRA_STATE" },
                            label = { Text("CGST + SGST") }
                        )
                        FilterChip(
                            selected = taxMode == "INTER_STATE",
                            onClick = { taxMode = "INTER_STATE" },
                            label = { Text("IGST") }
                        )
                    }
                }
                Text("Invoice Template", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MODERN", "CLASSIC", "THERMAL").forEach { tpl ->
                        FilterChip(
                            selected = templateStyle == tpl,
                            onClick = { templateStyle = tpl },
                            label = { Text(tpl) }
                        )
                    }
                }
            }
        }

        // Customer Section
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Customer Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (workspace.customers.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(workspace.customers, key = { it.id }) { cust ->
                            FilterChip(
                                selected = selectedCustomer?.id == cust.id,
                                onClick = {
                                    selectedCustomer = cust
                                    customerName = cust.name
                                    customerPhone = cust.phone
                                    customerAddress = cust.address
                                    customerGstin = cust.gstin
                                },
                                label = { Text(cust.name) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Customer Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("invoice_customer_name")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Mobile") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customerGstin,
                        onValueChange = { customerGstin = it.uppercase() },
                        label = { Text("GSTIN (Optional)") },
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = customerAddress,
                    onValueChange = { customerAddress = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Line Items
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Products (${recalculatedLines.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = { showAddProductDialog = true },
                modifier = Modifier.testTag("invoice_add_item_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add Item")
            }
        }

        recalculatedLines.forEachIndexed { idx, item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${item.qty} ${item.unit} × ${BillingCalculator.formatInr(item.unitPrice)} • GST ${item.gstRate.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            BillingCalculator.formatInr(item.total),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = {
                            items = items.toMutableList().apply { removeAt(idx) }
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove Item", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Totals & Payment
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = discountInput,
                    onValueChange = { discountInput = it },
                    label = { Text("Extra Discount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Payment Mode", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "UPI_QR", "BANK", "CREDIT").forEach { mode ->
                        FilterChip(
                            selected = paymentMethod == mode,
                            onClick = { paymentMethod = mode },
                            label = { Text(mode.replace("_", " ")) }
                        )
                    }
                }
                if (paymentMethod == "CASH" || paymentMethod == "BANK") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Full Payment Received")
                        Switch(checked = payFullToggle, onCheckedChange = { payFullToggle = it })
                    }
                    if (!payFullToggle) {
                        OutlinedTextField(
                            value = paidInput,
                            onValueChange = { paidInput = it },
                            label = { Text("Partial Amount Received (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (paymentMethod == "UPI_QR") {
                    Text(
                        "Dynamic UPI QR will be generated after saving. Status stays UNPAID until Owner verifies UTR.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Taxable Value"); Text(BillingCalculator.formatInr(totals.taxableTotal))
                }
                if (totals.cgstTotal > 0 || totals.sgstTotal > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CGST + SGST"); Text(BillingCalculator.formatInr(totals.cgstTotal + totals.sgstTotal))
                    }
                }
                if (totals.igstTotal > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("IGST"); Text(BillingCalculator.formatInr(totals.igstTotal))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grand Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        BillingCalculator.formatInr(totals.grandTotal),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Button(
            onClick = {
                val built = Invoice(
                    id = existingInvoice?.id ?: "",
                    userId = existingInvoice?.userId ?: "",
                    invoiceNumber = existingInvoice?.invoiceNumber ?: "",
                    invoiceType = if (isGst) "GST" else "NON_GST",
                    taxMode = if (isGst) taxMode else "NONE",
                    customerId = selectedCustomer?.id ?: "",
                    customerName = customerName.ifBlank { "Walk-in Customer" },
                    customerPhone = customerPhone,
                    customerAddress = customerAddress,
                    customerGstin = customerGstin,
                    items = recalculatedLines,
                    subtotal = totals.subtotal,
                    discountAmount = totals.discountAmount,
                    taxableTotal = totals.taxableTotal,
                    cgstTotal = totals.cgstTotal,
                    sgstTotal = totals.sgstTotal,
                    igstTotal = totals.igstTotal,
                    grandTotal = totals.grandTotal,
                    paidAmount = effectivePaid,
                    balanceDue = totals.balanceDue,
                    paymentStatus = totals.paymentStatus,
                    paymentMethod = paymentMethod,
                    templateStyle = templateStyle,
                    invoiceEpochMs = existingInvoice?.invoiceEpochMs ?: System.currentTimeMillis()
                )
                onSave(built, "GPay")
            },
            enabled = recalculatedLines.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("invoice_save_button")
        ) {
            Text(if (existingInvoice != null) "Update Invoice" else "Save Invoice & Generate Bill")
        }
    }

    if (showAddProductDialog) {
        AddInvoiceItemDialog(
            inventory = workspace.products,
            isGstEnabled = isGst,
            taxMode = taxMode,
            onDismiss = { showAddProductDialog = false },
            onAdd = { newItem ->
                items = items + newItem
                showAddProductDialog = false
            }
        )
    }
}

@Composable
private fun AddInvoiceItemDialog(
    inventory: List<Product>,
    isGstEnabled: Boolean,
    taxMode: String,
    onDismiss: () -> Unit,
    onAdd: (InvoiceLineItem) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedProd by remember { mutableStateOf<Product?>(null) }
    var itemName by remember { mutableStateOf("") }
    var hsn by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("PCS") }
    var qtyStr by remember { mutableStateOf("1") }
    var priceStr by remember { mutableStateOf("") }
    var discountPctStr by remember { mutableStateOf("0") }
    var gstRate by remember { mutableStateOf(if (isGstEnabled) 18.0 else 0.0) }

    val matchedProducts = remember(inventory, search) {
        inventory.filter {
            search.isBlank() ||
                it.name.contains(search, ignoreCase = true) ||
                it.barcode.contains(search, ignoreCase = true) ||
                it.sku.contains(search, ignoreCase = true)
        }.take(6)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Line Item") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (inventory.isNotEmpty()) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search Inventory / Barcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    matchedProducts.forEach { prod ->
                        Surface(
                            onClick = {
                                selectedProd = prod
                                itemName = prod.name
                                hsn = prod.hsnSac
                                unit = prod.unit
                                priceStr = prod.sellingPrice.toString()
                                gstRate = if (isGstEnabled) prod.gstRate else 0.0
                            },
                            color = if (selectedProd?.id == prod.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(prod.name, fontWeight = FontWeight.Medium)
                                Text("${BillingCalculator.formatInr(prod.sellingPrice)} (${prod.stockQty.toInt()} ${prod.unit})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("line_item_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyStr,
                        onValueChange = { qtyStr = it },
                        label = { Text("Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it },
                        label = { Text("Price (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("line_item_price_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = discountPctStr,
                        onValueChange = { discountPctStr = it },
                        label = { Text("Disc %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hsn,
                        onValueChange = { hsn = it },
                        label = { Text("HSN/SAC") },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isGstEnabled) {
                    Text("GST Slab %", style = MaterialTheme.typography.labelSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf(0.0, 5.0, 12.0, 18.0, 28.0)) { slab ->
                            FilterChip(
                                selected = gstRate == slab,
                                onClick = { gstRate = slab },
                                label = { Text("${slab.toInt()}%") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = (qtyStr.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.01)
                    val p = (priceStr.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    val d = (discountPctStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 100.0)
                    val calc = BillingCalculator.calculateLineItem(q, p, d, gstRate, isGstEnabled, taxMode)
                    onAdd(
                        InvoiceLineItem(
                            productId = selectedProd?.id ?: "",
                            name = itemName.trim(),
                            hsnSac = hsn.trim(),
                            unit = unit,
                            qty = q,
                            unitPrice = p,
                            discountPercent = d,
                            gstRate = gstRate,
                            taxableAmount = calc.taxableAmount,
                            cgst = calc.cgst,
                            sgst = calc.sgst,
                            igst = calc.igst,
                            total = calc.total
                        )
                    )
                },
                enabled = itemName.isNotBlank() && (priceStr.toDoubleOrNull() != null),
                modifier = Modifier.testTag("confirm_add_line_item_button")
            ) { Text("Add to Bill") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
