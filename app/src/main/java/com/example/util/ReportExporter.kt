package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.model.BusinessConfig
import com.example.model.Customer
import com.example.model.Expense
import com.example.model.Invoice
import com.example.model.PaymentRecord
import com.example.model.Product
import com.example.model.PurchaseRecord
import java.io.File
import java.io.FileOutputStream

object ReportExporter {

    fun exportExcelCsvReport(
        context: Context,
        periodLabel: String,
        business: BusinessConfig,
        invoices: List<Invoice>,
        purchases: List<PurchaseRecord>,
        expenses: List<Expense>,
        products: List<Product>,
        customers: List<Customer>,
        payments: List<PaymentRecord>
    ): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "BharatBill_Report_${periodLabel.replace(" ", "_")}.csv")

        val sb = StringBuilder()
        // UTF-8 BOM for clean Excel opening
        sb.append('\uFEFF')
        sb.appendLine("${business.businessName.ifBlank { "BharatBill" }} - $periodLabel Financial & GST Report")
        sb.appendLine("Generated,${BillingCalculator.formatDate(System.currentTimeMillis())}")
        sb.appendLine()

        val totalSales = invoices.sumOf { it.grandTotal }
        val totalTaxable = invoices.sumOf { it.taxableTotal }
        val cgst = invoices.sumOf { it.cgstTotal }
        val sgst = invoices.sumOf { it.sgstTotal }
        val igst = invoices.sumOf { it.igstTotal }
        val totalPurchases = purchases.filter { it.recordType == "PURCHASE" }.sumOf { it.grandTotal }
        val totalExpenses = expenses.sumOf { it.amount }
        val grossProfit = totalSales - totalPurchases
        val netProfit = grossProfit - totalExpenses

        sb.appendLine("SUMMARY METRICS,AMOUNT (INR)")
        sb.appendLine("Total Sales,$totalSales")
        sb.appendLine("Taxable Sales,$totalTaxable")
        sb.appendLine("CGST Collected,$cgst")
        sb.appendLine("SGST Collected,$sgst")
        sb.appendLine("IGST Collected,$igst")
        sb.appendLine("Total Purchases,$totalPurchases")
        sb.appendLine("Total Expenses,$totalExpenses")
        sb.appendLine("Gross Profit,$grossProfit")
        sb.appendLine("Net Profit,$netProfit")
        sb.appendLine()

        sb.appendLine("INVOICES")
        sb.appendLine("Invoice #,Date,Customer,Type,Taxable,CGST,SGST,IGST,Grand Total,Paid,Balance,Status")
        invoices.forEach { inv ->
            sb.appendLine(
                "${inv.invoiceNumber},\"${BillingCalculator.formatShortDate(inv.invoiceEpochMs)}\"," +
                    "\"${inv.customerName.replace("\"", "'")}\",${inv.invoiceType},${inv.taxableTotal}," +
                    "${inv.cgstTotal},${inv.sgstTotal},${inv.igstTotal},${inv.grandTotal},${inv.paidAmount},${inv.balanceDue},${inv.paymentStatus}"
            )
        }
        sb.appendLine()

        sb.appendLine("CUSTOMER OUTSTANDING")
        sb.appendLine("Customer,Phone,Total Purchases,Outstanding Balance")
        customers.forEach { c ->
            sb.appendLine("\"${c.name.replace("\"", "'")}\",${c.phone},${c.totalPurchases},${c.outstandingBalance}")
        }
        sb.appendLine()

        sb.appendLine("STOCK REPORT")
        sb.appendLine("Product,Category,HSN,Stock Qty,Unit,Purchase Price,Selling Price,Stock Value")
        products.forEach { p ->
            sb.appendLine(
                "\"${p.name.replace("\"", "'")}\",${p.category},${p.hsnSac},${p.stockQty},${p.unit}," +
                    "${p.purchasePrice},${p.sellingPrice},${p.stockQty * p.purchasePrice}"
            )
        }
        sb.appendLine()

        sb.appendLine("PAYMENT REPORT")
        sb.appendLine("Invoice #,Customer,Amount,Method,App,UTR Ref,Verification Status")
        payments.forEach { p ->
            sb.appendLine(
                "${p.invoiceNumber},\"${p.customerName.replace("\"", "'")}\",${p.amount},${p.method},${p.upiApp},${p.transactionRef},${p.verificationStatus}"
            )
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportPdfSummaryReport(
        context: Context,
        periodLabel: String,
        business: BusinessConfig,
        invoices: List<Invoice>,
        purchases: List<PurchaseRecord>,
        expenses: List<Expense>,
        products: List<Product>,
        customers: List<Customer>
    ): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "BharatBill_Summary_${periodLabel.replace(" ", "_")}.pdf")

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 43, 71)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 119, 6)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
        }

        var y = 48f
        canvas.drawText("${business.businessName.ifBlank { "BharatBill" }} — $periodLabel Report", 36f, y, headerPaint)
        y += 20f
        canvas.drawText("GSTIN: ${business.gstin.ifBlank { "N/A" }} | Generated: ${BillingCalculator.formatDate(System.currentTimeMillis())}", 36f, y, textPaint)
        y += 30f

        val totalSales = invoices.sumOf { it.grandTotal }
        val cgst = invoices.sumOf { it.cgstTotal }
        val sgst = invoices.sumOf { it.sgstTotal }
        val igst = invoices.sumOf { it.igstTotal }
        val totalPurchases = purchases.filter { it.recordType == "PURCHASE" }.sumOf { it.grandTotal }
        val totalExpenses = expenses.sumOf { it.amount }
        val netProfit = totalSales - totalPurchases - totalExpenses
        val pendingReceivables = customers.sumOf { it.outstandingBalance }
        val stockValuation = products.sumOf { it.stockQty * it.purchasePrice }

        canvas.drawText("PROFIT & LOSS & GST SUMMARY", 36f, y, sectionPaint)
        y += 20f
        listOf(
            "Total Sales (${invoices.size} bills)" to BillingCalculator.formatInr(totalSales),
            "Total Purchases" to BillingCalculator.formatInr(totalPurchases),
            "Total Expenses" to BillingCalculator.formatInr(totalExpenses),
            "Net Profit" to BillingCalculator.formatInr(netProfit),
            "CGST Collected" to BillingCalculator.formatInr(cgst),
            "SGST Collected" to BillingCalculator.formatInr(sgst),
            "IGST Collected" to BillingCalculator.formatInr(igst),
            "Customer Outstanding Receivables" to BillingCalculator.formatInr(pendingReceivables),
            "Inventory Valuation (${products.size} SKUs)" to BillingCalculator.formatInr(stockValuation)
        ).forEach { (k, v) ->
            canvas.drawText(k, 36f, y, textPaint)
            canvas.drawText(v, 360f, y, textPaint)
            y += 18f
        }

        y += 18f
        canvas.drawText("TOP RECENT INVOICES", 36f, y, sectionPaint)
        y += 20f
        invoices.take(12).forEach { inv ->
            canvas.drawText(
                "${inv.invoiceNumber} | ${inv.customerName.take(22)} | ${inv.paymentStatus} | ${BillingCalculator.formatInr(inv.grandTotal)}",
                36f,
                y,
                textPaint
            )
            y += 16f
        }

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun shareReportFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
