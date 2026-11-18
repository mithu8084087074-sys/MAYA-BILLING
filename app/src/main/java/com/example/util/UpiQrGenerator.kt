package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.util.Locale

enum class UpiAppTarget(val label: String, val packageName: String?) {
    GPAY("Google Pay", "com.google.android.apps.nbu.paisa.user"),
    PHONEPE("PhonePe", "com.phonepe.app"),
    PAYTM("Paytm", "net.one97.paytm"),
    BHIM("BHIM UPI", "in.org.npci.upiapp"),
    ANY("Other UPI App", null)
}

object UpiQrGenerator {

    fun buildUpiUri(
        upiId: String,
        merchantName: String,
        amount: Double,
        invoiceNumber: String,
        transactionRef: String
    ): String {
        val cleanVpa = upiId.trim()
        val encodedName = URLEncoder.encode(merchantName.ifBlank { "Maya Bill Store" }, "UTF-8")
        val encodedNote = URLEncoder.encode("Bill $invoiceNumber", "UTF-8")
        val encodedRef = URLEncoder.encode(transactionRef, "UTF-8")
        val formattedAmount = String.format(Locale.US, "%.2f", amount.coerceAtLeast(0.01))
        return "upi://pay?pa=$cleanVpa&pn=$encodedName&tr=$encodedRef&tn=$encodedNote&am=$formattedAmount&cu=INR"
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun launchUpiApp(
        context: Context,
        upiUri: String,
        target: UpiAppTarget
    ): Boolean {
        val uri = Uri.parse(upiUri)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (target.packageName != null) {
                setPackage(target.packageName)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (target.packageName != null && intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_VIEW, uri),
                    "Pay via UPI App"
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (chooser.resolveActivity(context.packageManager) != null) {
                    context.startActivity(chooser)
                    true
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
