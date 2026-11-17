package com.example.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

data class LineCalculationResult(
    val taxableAmount: Double,
    val cgst: Double,
    val sgst: Double,
    val igst: Double,
    val total: Double
)

data class InvoiceTotalsResult(
    val subtotal: Double,
    val discountAmount: Double,
    val taxableTotal: Double,
    val cgstTotal: Double,
    val sgstTotal: Double,
    val igstTotal: Double,
    val grandTotal: Double,
    val balanceDue: Double,
    val paymentStatus: String
)

object BillingCalculator {

    fun round2(value: Double): Double {
        return (value * 100.0).roundToLong() / 100.0
    }

    fun calculateLineItem(
        qty: Double,
        unitPrice: Double,
        discountPercent: Double,
        gstRate: Double,
        isGstEnabled: Boolean,
        taxMode: String
    ): LineCalculationResult {
        val safeQty = qty.coerceAtLeast(0.0)
        val safePrice = unitPrice.coerceAtLeast(0.0)
        val safeDiscount = discountPercent.coerceIn(0.0, 100.0)
        val gross = safeQty * safePrice
        val discountAmt = gross * (safeDiscount / 100.0)
        val taxable = round2((gross - discountAmt).coerceAtLeast(0.0))

        if (!isGstEnabled || taxMode == "NONE" || gstRate <= 0.0) {
            return LineCalculationResult(
                taxableAmount = taxable,
                cgst = 0.0,
                sgst = 0.0,
                igst = 0.0,
                total = taxable
            )
        }

        val totalTax = taxable * (gstRate / 100.0)
        return if (taxMode == "INTER_STATE") {
            val igst = round2(totalTax)
            LineCalculationResult(
                taxableAmount = taxable,
                cgst = 0.0,
                sgst = 0.0,
                igst = igst,
                total = round2(taxable + igst)
            )
        } else {
            val halfTax = round2(totalTax / 2.0)
            LineCalculationResult(
                taxableAmount = taxable,
                cgst = halfTax,
                sgst = halfTax,
                igst = 0.0,
                total = round2(taxable + halfTax + halfTax)
            )
        }
    }

    fun calculateInvoiceTotals(
        lines: List<LineCalculationResult>,
        extraDiscountAmount: Double,
        paidAmount: Double
    ): InvoiceTotalsResult {
        val subtotal = round2(lines.sumOf { it.taxableAmount })
        val clampedDiscount = round2(extraDiscountAmount.coerceIn(0.0, subtotal))
        val discountFactor = if (subtotal > 0.0) (subtotal - clampedDiscount) / subtotal else 1.0

        val taxableTotal = round2((subtotal - clampedDiscount).coerceAtLeast(0.0))
        val cgstTotal = round2(lines.sumOf { it.cgst } * discountFactor)
        val sgstTotal = round2(lines.sumOf { it.sgst } * discountFactor)
        val igstTotal = round2(lines.sumOf { it.igst } * discountFactor)
        val grandTotal = round2(taxableTotal + cgstTotal + sgstTotal + igstTotal)
        val clampedPaid = round2(paidAmount.coerceIn(0.0, grandTotal))
        val balanceDue = round2((grandTotal - clampedPaid).coerceAtLeast(0.0))

        val status = when {
            grandTotal <= 0.0 -> "PAID"
            balanceDue <= 0.01 -> "PAID"
            clampedPaid > 0.0 -> "PARTIAL"
            else -> "UNPAID"
        }

        return InvoiceTotalsResult(
            subtotal = subtotal,
            discountAmount = clampedDiscount,
            taxableTotal = taxableTotal,
            cgstTotal = cgstTotal,
            sgstTotal = sgstTotal,
            igstTotal = igstTotal,
            grandTotal = grandTotal,
            balanceDue = balanceDue,
            paymentStatus = status
        )
    }

    fun formatInr(amount: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        formatter.maximumFractionDigits = 2
        formatter.minimumFractionDigits = 2
        return formatter.format(amount)
    }

    fun formatCompactInr(amount: Double): String {
        return when {
            amount >= 10000000.0 -> "₹%.2f Cr".format(Locale.US, amount / 10000000.0)
            amount >= 100000.0 -> "₹%.2f L".format(Locale.US, amount / 100000.0)
            else -> formatInr(amount)
        }
    }

    fun formatDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale("en", "IN"))
        return sdf.format(Date(epochMs))
    }

    fun formatShortDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("en", "IN"))
        return sdf.format(Date(epochMs))
    }

    fun startOfTodayEpochMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun startOfWeekEpochMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun startOfMonthEpochMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun startOfYearEpochMs(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
