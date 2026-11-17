package com.example.util

enum class AppLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी")
}

object AppStrings {
    fun get(key: String, lang: AppLanguage): String {
        val hi = lang == AppLanguage.HINDI
        return when (key) {
            "nav_dashboard" -> if (hi) "डैशबोर्ड" else "Dashboard"
            "nav_billing" -> if (hi) "बिलिंग" else "Billing"
            "nav_inventory" -> if (hi) "स्टॉक" else "Inventory"
            "nav_parties" -> if (hi) "ग्राहक व खरीद" else "Parties"
            "nav_reports" -> if (hi) "रिपोर्ट्स" else "Reports"
            "nav_owner" -> if (hi) "ओनर एडमिन" else "Owner"
            "total_sales" -> if (hi) "कुल बिक्री" else "Total Sales"
            "todays_sales" -> if (hi) "आज की बिक्री" else "Today's Sales"
            "monthly_sales" -> if (hi) "मासिक बिक्री" else "Monthly Sales"
            "total_purchases" -> if (hi) "कुल खरीद" else "Total Purchases"
            "total_profit" -> if (hi) "शुद्ध लाभ" else "Net Profit"
            "total_expenses" -> if (hi) "कुल खर्च" else "Total Expenses"
            "pending_payments" -> if (hi) "बकाया भुगतान" else "Pending Payments"
            "total_customers" -> if (hi) "कुल ग्राहक" else "Customers"
            "total_products" -> if (hi) "कुल उत्पाद" else "Products"
            "low_stock_alerts" -> if (hi) "कम स्टॉक अलर्ट" else "Low Stock Alerts"
            "recent_transactions" -> if (hi) "हाल के लेनदेन" else "Recent Transactions"
            "new_invoice" -> if (hi) "नया बिल बनाएं" else "New Invoice"
            "voice_billing" -> if (hi) "वॉइस बिलिंग" else "Voice Bill"
            "gst_billing" -> if (hi) "जीएसटी बिल" else "GST Bill"
            "non_gst_billing" -> if (hi) "नॉन-जीएसटी बिल" else "Non-GST"
            "pay_upi_qr" -> if (hi) "UPI QR भुगतान" else "UPI QR Pay"
            "export_pdf" -> if (hi) "PDF निर्यात" else "Export PDF"
            "export_excel" -> if (hi) "Excel निर्यात" else "Export Excel"
            "owner_dashboard" -> if (hi) "ओनर एडमिन डैशबोर्ड" else "Owner Admin Dashboard"
            "upgrade_center" -> if (hi) "अपग्रेड सेंटर" else "Upgrade Center"
            else -> key
        }
    }
}
