package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import com.example.R
import com.example.model.BusinessConfig
import com.example.model.Invoice
import com.example.model.PaymentRecord
import com.example.ui.components.SignatureSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLEncoder

object PdfInvoiceGenerator {

    internal const val PDF_SIDE_BRANDING = "MAYA BILL APP"

    private fun loadAppLogoBitmap(context: Context): Bitmap? = try {
        BitmapFactory.decodeResource(context.resources, R.drawable.img_store_logo)
    } catch (_: Exception) {
        null
    }

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
        val pageHeight = if (isThermal) (640 + invoice.items.size * 34).coerceAtLeast(680) else 842
        val appLogo = loadAppLogoBitmap(context)

        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        if (isThermal) {
            drawThermalTemplate(canvas, pageWidth, pageHeight, invoice, business, appLogo)
        } else {
            drawA4Template(
                canvas = canvas,
                width = pageWidth,
                height = pageHeight,
                invoice = invoice,
                business = business,
                appLogo = appLogo,
                isModern = invoice.templateStyle != "CLASSIC"
            )
        }

        document.finishPage(page)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        appLogo?.recycle()
        return file
    }

    private fun drawA4Template(
        canvas: Canvas,
        width: Int,
        height: Int,
        invoice: Invoice,
        business: BusinessConfig,
        appLogo: Bitmap?,
        isModern: Boolean
    ) {
        val primaryColor = if (isModern) Color.rgb(12, 43, 71) else Color.rgb(30, 41, 59)
        val accentColor = if (isModern) Color.rgb(217, 119, 6) else Color.rgb(71, 85, 105)
        val bgPaint = Paint().apply { color = Color.WHITE }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val headerHeight = 122f
        val headerPaint = Paint().apply { color = primaryColor }
        if (isModern) {
            canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerPaint)
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
            textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isModern) Color.rgb(226, 232, 240) else Color.DKGRAY
            textSize = 10f
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
        var y = if (isModern) 34f else 48f

        // 1. Business Name, Address/Details, Phone & GSTIN at Top
        canvas.drawText(business.businessName.ifBlank { "Maya Bill Store" }, leftX, y, titlePaint)
        y += 16f
        if (business.businessAddress.isNotBlank()) {
            canvas.drawText(business.businessAddress.take(64), leftX, y, subHeaderPaint)
            y += 14f
        }
        val contactLine = buildString {
            if (business.businessPhone.isNotBlank()) append("Phone: ${business.businessPhone}  ")
            if (business.gstin.isNotBlank()) append("GSTIN: ${business.gstin}")
        }
        if (contactLine.isNotBlank()) {
            canvas.drawText(contactLine, leftX, y, subHeaderPaint)
            y += 14f
        }
        val extraDetails = buildString {
            if (business.stateName.isNotBlank()) append("State: ${business.stateName}")
            if (business.gstEntityDetails.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(business.gstEntityDetails.take(56))
            }
        }
        if (extraDetails.isNotBlank()) {
            canvas.drawText(extraDetails, leftX, y, subHeaderPaint)
        }

        val badgeText = if (invoice.invoiceType == "GST") "TAX INVOICE" else "BILL OF SUPPLY"
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isModern) Color.rgb(251, 191, 36) else primaryColor
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(badgeText, rightX, if (isModern) 38f else 50f, badgePaint)
        val invNumPaint = Paint(subHeaderPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("#${invoice.invoiceNumber}", rightX, if (isModern) 56f else 68f, invNumPaint)
        canvas.drawText(
            BillingCalculator.formatShortDate(invoice.invoiceEpochMs),
            rightX,
            if (isModern) 72f else 84f,
            invNumPaint
        )

        // 2. Customer Details Directly Below Business Section
        y = 142f
        canvas.drawText("CUSTOMER DETAILS (BILL TO)", leftX, y, labelPaint)
        canvas.drawText("PAYMENT STATUS", 370f, y, labelPaint)
        y += 16f
        canvas.drawText(invoice.customerName, leftX, y, boldPaint)
        canvas.drawText("${invoice.paymentStatus} (${invoice.paymentMethod})", 370f, y, boldPaint)
        y += 14f
        if (invoice.customerPhone.isNotBlank()) {
            canvas.drawText("Phone: ${invoice.customerPhone}", leftX, y, textPaint)
        }
        if (invoice.upiTransactionRef.isNotBlank()) {
            canvas.drawText("Ref: ${invoice.upiTransactionRef}", 370f, y, textPaint)
        }
        y += 14f
        if (invoice.customerAddress.isNotBlank()) {
            canvas.drawText("Address: ${invoice.customerAddress.take(52)}", leftX, y, textPaint)
            y += 14f
        }
        if (invoice.customerGstin.isNotBlank()) {
            canvas.drawText("Customer GSTIN: ${invoice.customerGstin}", leftX, y, textPaint)
            y += 14f
        }

        // 3. Complete Products, Discount & Totals Table
        y += 10f
        val tableHeaderPaint = Paint().apply { color = Color.rgb(241, 245, 249) }
        canvas.drawRect(leftX, y, rightX, y + 24f, tableHeaderPaint)
        val thText = Paint(labelPaint).apply { color = primaryColor }
        canvas.drawText("#", leftX + 6f, y + 16f, thText)
        canvas.drawText("PRODUCT / HSN", leftX + 26f, y + 16f, thText)
        canvas.drawText("QTY", 250f, y + 16f, thText)
        canvas.drawText("RATE", 300f, y + 16f, thText)
        canvas.drawText("DISC", 358f, y + 16f, thText)
        canvas.drawText("GST", 412f, y + 16f, thText)
        val rightBold = Paint(thText).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("TOTAL", rightX - 6f, y + 16f, rightBold)
        y += 38f

        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        val rightText = Paint(textPaint).apply { textAlign = Paint.Align.RIGHT }

        var totalItemDiscounts = 0.0
        invoice.items.take(16).forEachIndexed { idx, item ->
            val grossLine = item.qty * item.unitPrice
            val itemDiscAmt = grossLine * (item.discountPercent.coerceIn(0.0, 100.0) / 100.0)
            totalItemDiscounts += itemDiscAmt

            canvas.drawText("${idx + 1}", leftX + 6f, y, textPaint)
            val label = if (item.hsnSac.isNotBlank()) "${item.name.take(24)} (${item.hsnSac})" else item.name.take(30)
            canvas.drawText(label, leftX + 26f, y, textPaint)
            canvas.drawText("${item.qty} ${item.unit}", 250f, y, textPaint)
            canvas.drawText("%.2f".format(item.unitPrice), 300f, y, textPaint)
            canvas.drawText(
                if (item.discountPercent > 0) "%.0f%%".format(item.discountPercent) else "-",
                358f,
                y,
                textPaint
            )
            val taxVal = item.cgst + item.sgst + item.igst
            canvas.drawText("%.0f%% (%.1f)".format(item.gstRate, taxVal), 412f, y, textPaint)
            canvas.drawText(BillingCalculator.formatInr(item.total), rightX - 6f, y, rightText)
            y += 8f
            canvas.drawLine(leftX, y, rightX, y, linePaint)
            y += 18f
        }

        val combinedDiscount = totalItemDiscounts + invoice.discountAmount
        y += 8f
        // QR Code below items on left
        if (business.upiId.isNotBlank() && business.upiQrEnabled) {
            val qrAmt = if (invoice.balanceDue > 0.0) invoice.balanceDue else invoice.grandTotal
            val upiUri = UpiQrGenerator.buildUpiUri(
                upiId = business.upiId,
                merchantName = business.upiMerchantName.ifBlank { business.businessName },
                amount = qrAmt,
                invoiceNumber = invoice.invoiceNumber,
                transactionRef = invoice.invoiceNumber
            )
            val qrBitmap = UpiQrGenerator.generateQrBitmap(upiUri, 200)
            if (qrBitmap != null) {
                canvas.drawText("SCAN TO PAY VIA UPI", leftX, y, labelPaint)
                val destRect = Rect(leftX.toInt(), (y + 6).toInt(), (leftX + 94).toInt(), (y + 100).toInt())
                canvas.drawBitmap(qrBitmap, null, destRect, null)
                canvas.drawText("UPI: ${business.upiId}", leftX, y + 114f, textPaint)
            }
        }

        // Totals & Discount Summary on right
        val totalsX = 352f
        var ty = y
        fun drawTotalRow(label: String, value: String, bold: Boolean = false) {
            val p = if (bold) boldPaint else textPaint
            val rp = Paint(p).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(label, totalsX, ty, p)
            canvas.drawText(value, rightX - 6f, ty, rp)
            ty += 17f
        }

        drawTotalRow("Items Subtotal", BillingCalculator.formatInr(invoice.subtotal))
        drawTotalRow("Total Discount", "- ${BillingCalculator.formatInr(combinedDiscount)}")
        drawTotalRow("Taxable Value", BillingCalculator.formatInr(invoice.taxableTotal))
        if (invoice.cgstTotal > 0 || invoice.sgstTotal > 0) {
            drawTotalRow("CGST", BillingCalculator.formatInr(invoice.cgstTotal))
            drawTotalRow("SGST", BillingCalculator.formatInr(invoice.sgstTotal))
        }
        if (invoice.igstTotal > 0) {
            drawTotalRow("IGST", BillingCalculator.formatInr(invoice.igstTotal))
        }
        canvas.drawLine(totalsX, ty - 7f, rightX, ty - 7f, linePaint)
        val grandPaint = Paint(boldPaint).apply {
            textSize = 13f
            color = accentColor
        }
        val grandRight = Paint(grandPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("GRAND TOTAL", totalsX, ty + 6f, grandPaint)
        canvas.drawText(BillingCalculator.formatInr(invoice.grandTotal), rightX - 6f, ty + 6f, grandRight)
        ty += 23f
        drawTotalRow("Paid Amount", BillingCalculator.formatInr(invoice.paidAmount))
        drawTotalRow("Balance Due", BillingCalculator.formatInr(invoice.balanceDue), bold = true)

        // 4. Bottom-of-Page Dual Footer (Slightly Above Bottom Edge):
        //    Left Side: App Logo + "Maya Bill App"
        //    Right Side: Business Owner Signature + Business Name
        drawBottomAppAndSignatureBand(
            canvas = canvas,
            leftX = leftX,
            rightX = rightX,
            bandTopY = height - 104f,
            business = business,
            appLogo = appLogo,
            primaryColor = primaryColor,
            boldPaint = boldPaint,
            labelPaint = labelPaint
        )

        val footerPaint = Paint(labelPaint).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(
            "Generated with Maya Bill App • ${business.businessName.ifBlank { "Maya Bill Store" }}",
            width / 2f,
            height - 26f,
            footerPaint
        )

        drawSideBranding(canvas, x = width - 10f, centerY = height / 2f, textSize = 10f)
    }

    private fun drawBottomAppAndSignatureBand(
        canvas: Canvas,
        leftX: Float,
        rightX: Float,
        bandTopY: Float,
        business: BusinessConfig,
        appLogo: Bitmap?,
        primaryColor: Int,
        boldPaint: Paint,
        labelPaint: Paint
    ) {
        val dividerPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
        }
        canvas.drawLine(leftX, bandTopY - 8f, rightX, bandTopY - 8f, dividerPaint)

        // Left Side: App Logo + "Maya Bill App"
        val logoSize = 38
        if (appLogo != null) {
            val dest = Rect(
                leftX.toInt(),
                (bandTopY + 4f).toInt(),
                (leftX + logoSize).toInt(),
                (bandTopY + 4f + logoSize).toInt()
            )
            canvas.drawBitmap(appLogo, null, dest, null)
        }
        val textOffsetX = if (appLogo != null) leftX + logoSize + 10f else leftX
        val appTitlePaint = Paint(boldPaint).apply {
            color = primaryColor
            textSize = 12.5f
        }
        canvas.drawText("Maya Bill App", textOffsetX, bandTopY + 21f, appTitlePaint)
        canvas.drawText("Smart Indian GST & Billing", textOffsetX, bandTopY + 35f, labelPaint)

        // Right Side: Business Owner Signature
        val sigBoxWidth = 152f
        val sigBoxHeight = 40f
        val sigLeft = rightX - sigBoxWidth
        val sigTop = bandTopY
        drawSignatureStrokes(
            canvas = canvas,
            encodedStrokes = business.ownerSignatureStrokes,
            left = sigLeft,
            top = sigTop,
            width = sigBoxWidth,
            height = sigBoxHeight,
            strokeColor = primaryColor
        )
        canvas.drawLine(sigLeft, sigTop + sigBoxHeight + 2f, rightX, sigTop + sigBoxHeight + 2f, dividerPaint)
        val sigLabelPaint = Paint(labelPaint).apply {
            textAlign = Paint.Align.RIGHT
            color = Color.rgb(30, 41, 59)
        }
        canvas.drawText(
            "Business Owner Signature",
            rightX,
            sigTop + sigBoxHeight + 14f,
            sigLabelPaint
        )
        val ownerNamePaint = Paint(labelPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(
            business.businessName.ifBlank { "Authorized Signatory" }.take(28),
            rightX,
            sigTop + sigBoxHeight + 25f,
            ownerNamePaint
        )
    }

    private fun drawSignatureStrokes(
        canvas: Canvas,
        encodedStrokes: String,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        strokeColor: Int
    ) {
        val strokes = SignatureSerializer.decode(encodedStrokes)
        if (strokes.isEmpty()) return
        val sigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = strokeColor
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { pts ->
            if (pts.isNotEmpty()) {
                val path = Path()
                pts.forEachIndexed { idx, (nx, ny) ->
                    val px = left + nx * width
                    val py = top + ny * height
                    if (idx == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                canvas.drawPath(path, sigPaint)
            }
        }
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
        business: BusinessConfig,
        appLogo: Bitmap?
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
        var y = 26f
        canvas.drawText(business.businessName.ifBlank { "Maya Bill Store" }, cx, y, centerBold)
        y += 14f
        if (business.businessAddress.isNotBlank()) {
            canvas.drawText(business.businessAddress.take(36), cx, y, centerText)
            y += 13f
        }
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
        y += 12f
        if (invoice.customerPhone.isNotBlank()) {
            canvas.drawText("Cust Ph: ${invoice.customerPhone}", 16f, y, leftText)
            y += 12f
        }
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
        if (invoice.discountAmount > 0) {
            canvas.drawText("Discount:", 16f, y, leftText)
            canvas.drawText("-%.2f".format(invoice.discountAmount), width - 16f, y, rightText)
            y += 14f
        }
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
        y += 22f
        canvas.drawText("Status: ${invoice.paymentStatus} | Paid: ${BillingCalculator.formatInr(invoice.paidAmount)}", cx, y, centerText)
        y += 16f

        if (business.upiId.isNotBlank() && business.upiQrEnabled) {
            val qrAmt = if (invoice.balanceDue > 0) invoice.balanceDue else invoice.grandTotal
            val uri = UpiQrGenerator.buildUpiUri(
                business.upiId,
                business.upiMerchantName.ifBlank { business.businessName },
                qrAmt,
                invoice.invoiceNumber,
                invoice.invoiceNumber
            )
            UpiQrGenerator.generateQrBitmap(uri, 160)?.let { bmp ->
                val left = (width - 96) / 2
                canvas.drawBitmap(bmp, null, Rect(left, y.toInt(), left + 96, (y + 96).toInt()), null)
                y += 108f
            }
        }

        // Bottom Owner Signature & Maya Bill App Logo + Name
        val bandY = (height - 76f).coerceAtLeast(y + 8f)
        if (appLogo != null) {
            canvas.drawBitmap(appLogo, null, Rect(16, bandY.toInt(), 42, (bandY + 26).toInt()), null)
        }
        canvas.drawText("Maya Bill App", if (appLogo != null) 48f else 16f, bandY + 18f, leftText)

        drawSignatureStrokes(
            canvas = canvas,
            encodedStrokes = business.ownerSignatureStrokes,
            left = width - 116f,
            top = bandY - 4f,
            width = 96f,
            height = 28f,
            strokeColor = Color.BLACK
        )
        canvas.drawText("Owner Sign", width - 18f, bandY + 34f, rightText)

        drawSideBranding(canvas, x = width - 6f, centerY = height / 2f, textSize = 7.5f)
    }

    fun generatePaymentReceiptPdf(
        context: Context,
        payment: PaymentRecord,
        business: BusinessConfig
    ): File {
        val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
        val file = File(dir, "Receipt_${payment.invoiceNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.pdf")
        val appLogo = loadAppLogoBitmap(context)
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(420, 560, 1).create()
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
        val lPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(100, 116, 139)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val gPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(14, 159, 110)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 42f
        c.drawText(business.businessName.ifBlank { "Maya Bill Store" }, 28f, y, hPaint)
        y += 18f
        if (business.businessPhone.isNotBlank()) {
            c.drawText("Phone: ${business.businessPhone}", 28f, y, bPaint)
            y += 16f
        }
        if (business.businessAddress.isNotBlank()) {
            c.drawText(business.businessAddress.take(45), 28f, y, bPaint)
            y += 20f
        }
        c.drawText("PAYMENT RECEIPT", 28f, y, hPaint)
        y += 22f
        c.drawText("Status: ${payment.verificationStatus}", 28f, y, gPaint)
        y += 24f
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

        drawBottomAppAndSignatureBand(
            canvas = c,
            leftX = 28f,
            rightX = 390f,
            bandTopY = 470f,
            business = business,
            appLogo = appLogo,
            primaryColor = Color.rgb(12, 43, 71),
            boldPaint = bPaint,
            labelPaint = lPaint
        )
        drawSideBranding(c, x = 410f, centerY = 280f, textSize = 9.5f)

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        appLogo?.recycle()
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
