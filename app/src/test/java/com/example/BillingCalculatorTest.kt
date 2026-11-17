package com.example

import com.example.util.BillingCalculator
import com.example.util.SecurityUtils
import com.example.util.UpiQrGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingCalculatorTest {

    @Test
    fun `intra-state GST splits equally into CGST and SGST`() {
        val line = BillingCalculator.calculateLineItem(
            qty = 2.0,
            unitPrice = 1000.0,
            discountPercent = 0.0,
            gstRate = 18.0,
            isGstEnabled = true,
            taxMode = "INTRA_STATE"
        )
        assertEquals(2000.0, line.taxableAmount, 0.01)
        assertEquals(180.0, line.cgst, 0.01)
        assertEquals(180.0, line.sgst, 0.01)
        assertEquals(0.0, line.igst, 0.01)
        assertEquals(2360.0, line.total, 0.01)
    }

    @Test
    fun `inter-state GST applies full IGST`() {
        val line = BillingCalculator.calculateLineItem(
            qty = 1.0,
            unitPrice = 1500.0,
            discountPercent = 10.0,
            gstRate = 18.0,
            isGstEnabled = true,
            taxMode = "INTER_STATE"
        )
        // 1500 - 10% = 1350; 18% of 1350 = 243
        assertEquals(1350.0, line.taxableAmount, 0.01)
        assertEquals(0.0, line.cgst, 0.01)
        assertEquals(0.0, line.sgst, 0.01)
        assertEquals(243.0, line.igst, 0.01)
        assertEquals(1593.0, line.total, 0.01)
    }

    @Test
    fun `non-GST calculation applies zero tax and tracks partial payment`() {
        val line = BillingCalculator.calculateLineItem(
            qty = 3.0,
            unitPrice = 500.0,
            discountPercent = 0.0,
            gstRate = 18.0,
            isGstEnabled = false,
            taxMode = "NONE"
        )
        assertEquals(1500.0, line.total, 0.01)
        val totals = BillingCalculator.calculateInvoiceTotals(listOf(line), extraDiscountAmount = 100.0, paidAmount = 600.0)
        assertEquals(1400.0, totals.grandTotal, 0.01)
        assertEquals(800.0, totals.balanceDue, 0.01)
        assertEquals("PARTIAL", totals.paymentStatus)
    }

    @Test
    fun `UPI QR URI contains exact merchant VPA and invoice amount`() {
        val uri = UpiQrGenerator.buildUpiUri(
            upiId = "merchant@okaxis",
            merchantName = "BharatBill Store",
            amount = 1750.5,
            invoiceNumber = "INV-1001",
            transactionRef = "INV-1001"
        )
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=merchant@okaxis"))
        assertTrue(uri.contains("am=1750.50"))
        assertTrue(uri.contains("tr=INV-1001"))
        assertTrue(uri.contains("cu=INR"))
    }

    @Test
    fun `salted SHA-256 secret hashing verifies valid secret and rejects wrong input`() {
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashSecret("MySecureStore#2026", salt)
        assertTrue(SecurityUtils.verifySecret("MySecureStore#2026", salt, hash))
        assertFalse(SecurityUtils.verifySecret("WrongPass", salt, hash))
    }
}
