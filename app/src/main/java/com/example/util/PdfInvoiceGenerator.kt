package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.model.BusinessConfig
import com.example.model.Invoice
import com.example.model.PaymentRecord
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder

object PdfInvoiceGenerator {

    internal const val PDF_SIDE_BRANDING = "MAYA BILLING APP"

    fun generateInvoicePdf(
        context: Context,
        invoice: Invoice,
        business: BusinessConfig
    ): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val safeNum = invoice.invoiceNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val file = File(dir, "Invoice_$safeNum.pdf")

        val isThermal = invoice.templateStyle == "THERMAL"
        val pageWidth = if (isThermal) 320 else 595
        val pageHeight = if (isThermal) (560 + invoice.items.size * 34).coerceAtLeast(620) else 842

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        if (isThermal) {
            drawThermalTemplate(canvas, pageWidth, pageHeight, invoice, business)
        } else {
            drawA4Template(
                canvas = canvas,
                width = pageWidth,
                height = pageHeight,
                invoice = invoice,
                business = business,
                isModern = invoice.templateStyle != "CLASSIC"
            )
        }

        document.finishPage(page)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    private fun drawA4Template(
        canvas: Canvas,
        width: Int,
        height: Int,
        invoice: Invoice,
        business: BusinessConfig,
        isModern: Boolean
    ) {
        val primaryColor = if (isModern) Color.rgb(12, 43, 71) else Color.rgb(30, 41, 59)
        val accentColor = if (isModern) Color.rgb(217, 119, 6) else Color.rgb(71, 85, 105)
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val headerPaint = Paint().apply { color = primaryColor }
        if (isModern) {
            canvas.drawRect(0f, 0f, width.toFloat(), 108f, headerPaint)
        } else {
            val borderPaint = Paint().apply {
                color = primaryColor
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRect(24f, 24f, width - 24f, height - 24f, borderPaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isModern) Color.WHITE else primaryColor
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isModern) Color.rgb(226, 232, 240) else Color.DKGRAY
            textSize = 10.5f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 10.5f
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val leftX = 36f
        val rightX = width - 36f
        var y = if (isModern) 38f else 50f

        canvas.drawText(business.businessName.ifBlank { "BharatBill Store" }, leftX, y, titlePaint)
        y += 16f
        if (business.businessAddress.isNotBlank()) {
            canvas.drawText(business.businessAddress.take(62), leftX, y, subHeaderPaint)
            y += 14f
        }
        val contactLine = buildString {
            if (business.businessPhone.isNotBlank()) append("Ph: ${business.businessPhone}  ")
            if (business.gstin.isNotBlank()) append("GSTIN: ${business.gstin}")
        }
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, leftX, y, subHeaderPaint)
        }

        val badgeText = if (invoice.invoiceType == "GST") "TAX INVOICE" else "BILL OF SUPPLY"
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isModern) Color.rgb(251, 191, 36) else primaryColor
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(badgeText, rightX, if (isModern) 42f else 52f, badgePaint)
        val invNumPaint = Paint(subHeaderPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("#${invoice.invoiceNumber}", rightX, if (isModern) 60f else 70f, invNumPaint)
        canvas.drawText(
            BillingCalculator.formatShortDate(invoice.invoiceEpochMs),
            rightX,
            if (isModern) 76f else 86f,
            invNumPaint
        )

        y = 132f
        canvas.drawText("BILL TO", leftX, y, labelPaint)
        canvas.drawText("PAYMENT STATUS", 370f, y, labelPaint)
        y += 16f
        canvas.drawText(invoice.customerName, leftX, y, boldPaint)
        canvas.drawText("${invoice.paymentStatus} (${invoice.paymentMethod})", 370f, y, boldPaint)
        y += 14f
        if (invoice.customerPhone.isNotBlank()) {
            canvas.drawText("Mobile: ${invoice.customerPhone}", leftX, y, textPaint)
        }
        if (invoice.upiTransactionRef.isNotBlank()) {
            canvas.drawText("Ref: ${invoice.upiTransactionRef}", 370f, y, textPaint)
        }
        y += 14f
        if (invoice.customerAddress.isNotBlank()) {
            canvas.drawText(invoice.customerAddress.take(55), leftX, y, textPaint)
            y += 14f
        }
        if (invoice.customerGstin.isNotBlank()) {
            canvas.drawText("Customer GSTIN: ${invoice.customerGstin}", leftX, y, textPaint)
            y += 14f
        }

        y += 12f
        val tableHeaderPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(leftX, y, rightX, y + 24f, tableHeaderPaint)
        val thText = Paint(labelPaint).apply { color = primaryColor }
        canvas.drawText("#", leftX + 6f, y + 16f, thText)
        canvas.drawText("ITEM / HSN", leftX + 26f, y + 16f, thText)
        canvas.drawText("QTY", 285f, y + 16f, thText)
        canvas.drawText("RATE", 335f, y + 16f, thText)
        canvas.drawText("GST", 415f, y + 16f, thText)
        val rightBold = Paint(thText).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("TOTAL", rightX - 6f, y + 16f, rightBold)
        y += 38f

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        val rightText = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }

        invoice.items.take(18).forEachIndexed { idx, item ->
            canvas.drawText("${idx + 1}", leftX + 6f, y, textPaint)
            val label = if (item.hsnSac.isNotBlank()) "${item.name.take(26)} (${item.hsnSac})" else item.name.take(32)
            canvas.drawText(label, leftX + 26f, y, textPaint)
            canvas.drawText("${item.qty} ${item.unit}", 285f, y, textPaint)
            canvas.drawText("%.2f".format(item.unitPrice), 335f, y, textPaint)
            val taxVal = item.cgst + item.sgst + item.igst
            canvas.drawText("%.0f%% (%.1f)".format(item.gstRate, taxVal), 415f, y, textPaint)
            canvas.drawText(BillingCalculator.formatInr(item.total), rightX - 6f, y, rightText)
            y += 8f
            canvas.drawLine(leftX, y, rightX, y, linePaint)
            y += 18f
        }

        y += 10f
        // QR Code on bottom-left if UPI ID configured
        if (business.upiId.isNotBlank() && business.upiQrEnabled) {
            val qrAmt = if (invoice.balanceDue > 0.0) invoice.balanceDue else invoice.grandTotal
            val upiUri = UpiQrGenerator.buildUpiUri(
                upiId = business.upiId,
                merchantName = business.upiMerchantName.ifBlank { business.businessName },
                amount = qrAmt,
                invoiceNumber = invoice.invoiceNumber,
                transactionRef = invoice.invoiceNumber
            )
            val qrBitmap = UpiQrGenerator.generateQrBitmap(upiUri, 220)
            if (qrBitmap != null) {
                canvas.drawText("SCAN TO PAY VIA UPI", leftX, y, labelPaint)
                val destRect = Rect(leftX.toInt(), (y + 8).toInt(), (leftX + 104).toInt(), (y + 112).toInt())
                canvas.drawBitmap(qrBitmap, null, destRect, null)
                canvas.drawText("UPI: ${business.upiId}", leftX, y + 126f, textPaint)
            }
        }

        // Totals on bottom-right
        val totalsX = 360f
        var ty = y
        fun drawTotalRow(label: String, value: String, bold: Boolean = false) {
            val p = if (bold) boldPaint else textPaint
            val rp = Paint(p).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(label, totalsX, ty, p)
            canvas.drawText(value, rightX - 6f, ty, rp)
            ty += 18f
        }

        drawTotalRow("Taxable Value", BillingCalculator.formatInr(invoice.taxableTotal))
        if (invoice.discountAmount > 0) {
            drawTotalRow("Discount", "- ${BillingCalculator.formatInr(invoice.discountAmount)}")
        }
        if (invoice.cgstTotal > 0 || invoice.sgstTotal > 0) {
            drawTotalRow("CGST", BillingCalculator.formatInr(invoice.cgstTotal))
            drawTotalRow("SGST", BillingCalculator.formatInr(invoice.sgstTotal))
        }
        if (invoice.igstTotal > 0) {
            drawTotalRow("IGST", BillingCalculator.formatInr(invoice.igstTotal))
        }
        canvas.drawLine(totalsX, ty - 8f, rightX, ty - 8f, linePaint)
        val grandPaint = Paint(boldPaint).apply {
            textSize = 13f
            color = accentColor
        }
        val grandRight = Paint(grandPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("GRAND TOTAL", totalsX, ty + 6f, grandPaint)
        canvas.drawText(BillingCalculator.formatInr(invoice.grandTotal), rightX - 6f, ty + 6f, grandRight)
        ty += 24f
        drawTotalRow("Paid Amount", BillingCalculator.formatInr(invoice.paidAmount))
        drawTotalRow("Balance Due", BillingCalculator.formatInr(invoice.balanceDue), bold = true)

        val footerPaint = Paint(labelPaint).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(
            "Generated by ${business.businessName.ifBlank { "BharatBill" }} • Thank you for your business!",
            width / 2f,
            height - 34f,
            footerPaint
        )

        drawSideBranding(canvas, x = width - 10f, centerY = height / 2f, textSize = 10f)
    }

    private fun drawSideBranding(
        canvas: Canvas,
        x: Float,
        centerY: Float,
        textSize: Float
    ) {
        val sidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105)
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.14f
            textAlign = Paint.Align.CENTER
        }
        canvas.save()
        canvas.rotate(-90f, x, centerY)
        canvas.drawText(PDF_SIDE_BRANDING, x, centerY, sidePaint)
        canvas.restore()
    }

    private fun drawThermalTemplate(
        canvas: Canvas,
        width: Int,
        height: Int,
        invoice: Invoice,
        business: BusinessConfig
    ) {
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val centerBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val centerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val leftText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        val rightText = Paint(leftText).apply { textAlign = Paint.Align.RIGHT }

        val cx = width / 2f
        var y = 28f
        canvas.drawText(business.businessName.ifBlank { "BharatBill" }, cx, y, centerBold)
        y += 15f
        if (business.businessPhone.isNotBlank()) {
            canvas.drawText("Ph: ${business.businessPhone}", cx, y, centerText)
            y += 13f
        }
        if (business.gstin.isNotBlank()) {
            canvas.drawText("GSTIN: ${business.gstin}", cx, y, centerText)
            y += 13f
        }
        canvas.drawText("----------------------------------------", cx, y, centerText)
        y += 14f
        canvas.drawText("${invoice.invoiceType} BILL #${invoice.invoiceNumber}", cx, y, centerBold)
        y += 14f
        canvas.drawText("Customer: ${invoice.customerName}", 16f, y, leftText)
        y += 13f
        canvas.drawText("Date: ${BillingCalculator.formatShortDate(invoice.invoiceEpochMs)}", 16f, y, leftText)
        y += 13f
        canvas.drawText("----------------------------------------", cx, y, centerText)
        y += 16f

        invoice.items.forEach { item ->
            canvas.drawText(item.name.take(26), 16f, y, leftText)
            y += 12f
            canvas.drawText("  ${item.qty} x ${item.unitPrice}", 16f, y, leftText)
            canvas.drawText("%.2f".format(item.total), width - 16f, y, rightText)
            y += 15f
        }

        canvas.drawText("----------------------------------------", cx, y, centerText)
        y += 16f
        canvas.drawText("Taxable:", 16f, y, leftText)
        canvas.drawText("%.2f".format(invoice.taxableTotal), width - 16f, y, rightText)
        y += 14f
        val gstSum = invoice.cgstTotal + invoice.sgstTotal + invoice.igstTotal
        if (gstSum > 0) {
            canvas.drawText("GST:", 16f, y, leftText)
            canvas.drawText("%.2f".format(gstSum), width - 16f, y, rightText)
            y += 14f
        }
        canvas.drawText("TOTAL: ${BillingCalculator.formatInr(invoice.grandTotal)}", cx, y + 6f, centerBold)
        y += 24f
        canvas.drawText("Status: ${invoice.paymentStatus} | Paid: ${BillingCalculator.formatInr(invoice.paidAmount)}", cx, y, centerText)
        y += 18f

        if (business.upiId.isNotBlank() && business.upiQrEnabled) {
            val qrAmt = if (invoice.balanceDue > 0) invoice.balanceDue else invoice.grandTotal
            val uri = UpiQrGenerator.buildUpiUri(
                business.upiId,
                business.upiMerchantName.ifBlank { business.businessName },
                qrAmt,
                invoice.invoiceNumber,
                invoice.invoiceNumber
            )
            UpiQrGenerator.generateQrBitmap(uri, 180)?.let { bmp ->
                val left = (width - 110) / 2
                canvas.drawBitmap(bmp, null, Rect(left, y.toInt(), left + 110, (y + 110).toInt()), null)
                y += 124f
            }
        }
        canvas.drawText("Thank You! Visit Again", cx, y, centerText)
        drawSideBranding(canvas, x = width - 6f, centerY = height / 2f, textSize = 7.5f)
    }

    fun generatePaymentReceiptPdf(
        context: Context,
        payment: PaymentRecord,
        business: BusinessConfig
    ): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "Receipt_${payment.invoiceNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.pdf")
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(420, 520, 1).create()
        val page = doc.startPage(pageInfo)
        val c = page.canvas
        c.drawColor(Color.WHITE)

        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(12, 43, 71)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42)
            textSize = 12f
        }
        val gPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(14, 159, 110)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 44f
        c.drawText(business.businessName.ifBlank { "BharatBill" }, 28f, y, hPaint)
        y += 24f
        c.drawText("PAYMENT RECEIPT", 28f, y, hPaint)
        y += 24f
        c.drawText("Status: ${payment.verificationStatus}", 28f, y, gPaint)
        y += 26f
        c.drawText("Invoice #: ${payment.invoiceNumber}", 28f, y, bPaint)
        y += 20f
        c.drawText("Customer: ${payment.customerName}", 28f, y, bPaint)
        y += 20f
        c.drawText("Amount: ${BillingCalculator.formatInr(payment.amount)}", 28f, y, bPaint)
        y += 20f
        c.drawText("Method: ${payment.method} (${payment.upiApp.ifBlank { "Direct" }})", 28f, y, bPaint)
        y += 20f
        c.drawText("Transaction / UTR Ref: ${payment.transactionRef.ifBlank { "Pending" }}", 28f, y, bPaint)
        y += 20f
        c.drawText("Owner Verified: ${if (payment.verifiedByOwner) "YES" else "PENDING"}", 28f, y, bPaint)
        y += 20f
        c.drawText("Date: ${BillingCalculator.formatDate(payment.paymentEpochMs)}", 28f, y, bPaint)
        drawSideBranding(c, x = 410f, centerY = 260f, textSize = 9.5f)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun getFileUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun sharePdf(context: Context, file: File, subject: String) {
        val uri = getFileUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun shareOnWhatsApp(context: Context, file: File, phone: String, message: String) {
        val uri = getFileUri(context, file)
        val cleanPhone = phone.filter { it.isDigit() }.let {
            if (it.length == 10) "91$it" else it
        }
        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            if (cleanPhone.isNotBlank()) {
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pm = context.packageManager
        val waPkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (waPkg != null) {
            whatsappIntent.setPackage(waPkg)
            context.startActivity(whatsappIntent)
        } else if (cleanPhone.isNotBlank()) {
            // Fallback to WhatsApp web click-to-chat with summary text
            val encoded = URLEncoder.encode(message, "UTF-8")
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanPhone?text=$encoded")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (webIntent.resolveActivity(pm) != null) {
                context.startActivity(webIntent)
            } else {
                sharePdf(context, file, message)
            }
        } else {
            sharePdf(context, file, message)
        }
    }

    fun printPdf(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
