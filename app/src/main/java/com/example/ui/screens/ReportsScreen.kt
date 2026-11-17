package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MonoFontFamily
import com.example.util.BillingCalculator
import com.example.util.ReportExporter
import com.example.viewmodel.BillingWorkspaceData

@Composable
fun ReportsScreen(
    workspace: BillingWorkspaceData
) {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf("Monthly") }
    val periods = listOf("Daily", "Weekly", "Monthly", "Yearly", "All Time")

    val cutoffMs = remember(selectedPeriod) {
        when (selectedPeriod) {
            "Daily" -> BillingCalculator.startOfTodayEpochMs()
            "Weekly" -> BillingCalculator.startOfWeekEpochMs()
            "Monthly" -> BillingCalculator.startOfMonthEpochMs()
            "Yearly" -> BillingCalculator.startOfYearEpochMs()
            else -> 0L
        }
    }

    val filteredInvoices = remember(workspace.invoices, cutoffMs) {
        workspace.invoices.filter { it.invoiceEpochMs >= cutoffMs }
    }
    val filteredPurchases = remember(workspace.purchases, cutoffMs) {
        workspace.purchases.filter { it.purchaseEpochMs >= cutoffMs }
    }
    val filteredExpenses = remember(workspace.expenses, cutoffMs) {
        workspace.expenses.filter { it.expenseEpochMs >= cutoffMs }
    }
    val filteredPayments = remember(workspace.payments, cutoffMs) {
        workspace.payments.filter { it.paymentEpochMs >= cutoffMs }
    }

    val totalSales = filteredInvoices.sumOf { it.grandTotal }
    val taxableSales = filteredInvoices.sumOf { it.taxableTotal }
    val cgst = filteredInvoices.sumOf { it.cgstTotal }
    val sgst = filteredInvoices.sumOf { it.sgstTotal }
    val igst = filteredInvoices.sumOf { it.igstTotal }
    val totalPurchases = filteredPurchases.filter { it.recordType == "PURCHASE" }.sumOf { it.grandTotal }
    val totalExpenses = filteredExpenses.sumOf { it.amount }
    val grossProfit = totalSales - totalPurchases
    val netProfit = grossProfit - totalExpenses
    val verifiedPayments = filteredPayments.filter { it.verificationStatus == "VERIFIED" }.sumOf { it.amount }
    val pendingPayments = filteredPayments.filter { it.verificationStatus == "PENDING" }.sumOf { it.amount }
    val totalOutstanding = workspace.customers.sumOf { it.outstandingBalance }
    val stockValuation = workspace.products.sumOf { it.stockQty * it.purchasePrice }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Reports & GST Analytics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(periods) { p ->
                    FilterChip(
                        selected = selectedPeriod == p,
                        onClick = { selectedPeriod = p },
                        label = { Text(p) }
                    )
                }
            }
        }

        // Export Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val pdf = ReportExporter.exportPdfSummaryReport(
                            context, selectedPeriod, workspace.config,
                            filteredInvoices, filteredPurchases, filteredExpenses,
                            workspace.products, workspace.customers
                        )
                        ReportExporter.shareReportFile(context, pdf, "application/pdf", "BharatBill $selectedPeriod Report (PDF)")
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("export_report_pdf_button")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export PDF")
                }
                OutlinedButton(
                    onClick = {
                        val csv = ReportExporter.exportExcelCsvReport(
                            context, selectedPeriod, workspace.config,
                            filteredInvoices, filteredPurchases, filteredExpenses,
                            workspace.products, workspace.customers, filteredPayments
                        )
                        ReportExporter.shareReportFile(context, csv, "text/csv", "BharatBill $selectedPeriod Report (Excel CSV)")
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("export_report_excel_button")
                ) {
                    Icon(Icons.Rounded.TableChart, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Export Excel")
                }
            }
        }

        // Profit & Loss Report
        item {
            ReportSectionCard("Profit & Loss Statement ($selectedPeriod)") {
                ReportRow("Sales Revenue (${filteredInvoices.size} bills)", BillingCalculator.formatInr(totalSales))
                ReportRow("Purchases", BillingCalculator.formatInr(totalPurchases))
                ReportRow("Gross Profit", BillingCalculator.formatInr(grossProfit), bold = true)
                ReportRow("Operating Expenses", BillingCalculator.formatInr(totalExpenses))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ReportRow(
                    "Net Profit",
                    BillingCalculator.formatInr(netProfit),
                    bold = true,
                    highlightError = netProfit < 0
                )
            }
        }

        // GST Report
        item {
            ReportSectionCard("GST Tax Report ($selectedPeriod)") {
                ReportRow("Taxable Value", BillingCalculator.formatInr(taxableSales))
                ReportRow("CGST Output", BillingCalculator.formatInr(cgst))
                ReportRow("SGST Output", BillingCalculator.formatInr(sgst))
                ReportRow("IGST Output", BillingCalculator.formatInr(igst))
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ReportRow("Total GST Liability", BillingCalculator.formatInr(cgst + sgst + igst), bold = true)
            }
        }

        // Payment & Outstanding Report
        item {
            ReportSectionCard("Payments & Receivables") {
                ReportRow("Verified Collections", BillingCalculator.formatInr(verifiedPayments))
                ReportRow("Pending UPI Verifications", BillingCalculator.formatInr(pendingPayments))
                ReportRow("Total Customer Outstanding", BillingCalculator.formatInr(totalOutstanding), bold = true)
            }
        }

        // Stock Valuation Report
        item {
            ReportSectionCard("Inventory Stock Valuation") {
                ReportRow("Active SKUs", "${workspace.products.size} items")
                ReportRow("Low Stock SKUs", "${workspace.metrics.lowStockProducts.size} items")
                ReportRow("Stock Purchase Value", BillingCalculator.formatInr(stockValuation), bold = true)
            }
        }
    }
}

@Composable
private fun ReportSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ReportRow(
    label: String,
    value: String,
    bold: Boolean = false,
    highlightError: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = if (highlightError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
