package com.example.util

data class DecodedGstinDetails(
    val gstin: String,
    val stateCode: String,
    val stateName: String,
    val panNumber: String,
    val entityType: String,
    val entityNumber: String,
    val registrationSummary: String
)

object GstinHelper {

    private val GSTIN_REGEX = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")

    private val STATE_CODES = mapOf(
        "01" to "Jammu & Kashmir",
        "02" to "Himachal Pradesh",
        "03" to "Punjab",
        "04" to "Chandigarh",
        "05" to "Uttarakhand",
        "06" to "Haryana",
        "07" to "Delhi",
        "08" to "Rajasthan",
        "09" to "Uttar Pradesh",
        "10" to "Bihar",
        "11" to "Sikkim",
        "12" to "Arunachal Pradesh",
        "13" to "Nagaland",
        "14" to "Manipur",
        "15" to "Mizoram",
        "16" to "Tripura",
        "17" to "Meghalaya",
        "18" to "Assam",
        "19" to "West Bengal",
        "20" to "Jharkhand",
        "21" to "Odisha",
        "22" to "Chhattisgarh",
        "23" to "Madhya Pradesh",
        "24" to "Gujarat",
        "25" to "Daman & Diu",
        "26" to "Dadra & Nagar Haveli and Daman & Diu",
        "27" to "Maharashtra",
        "28" to "Andhra Pradesh (Old)",
        "29" to "Karnataka",
        "30" to "Goa",
        "31" to "Lakshadweep",
        "32" to "Kerala",
        "33" to "Tamil Nadu",
        "34" to "Puducherry",
        "35" to "Andaman & Nicobar Islands",
        "36" to "Telangana",
        "37" to "Andhra Pradesh",
        "38" to "Ladakh",
        "97" to "Other Territory",
        "99" to "Centre Jurisdiction"
    )

    private val ENTITY_TYPES = mapOf(
        'P' to "Proprietorship / Individual",
        'C' to "Company (Pvt Ltd / Ltd)",
        'F' to "Partnership Firm / LLP",
        'H' to "Hindu Undivided Family (HUF)",
        'A' to "Association of Persons (AOP)",
        'B' to "Body of Individuals (BOI)",
        'T' to "Trust",
        'G' to "Government Department",
        'L' to "Local Authority",
        'J' to "Artificial Juridical Person"
    )

    fun isValidFormat(rawGstin: String): Boolean {
        val clean = rawGstin.trim().uppercase()
        if (!GSTIN_REGEX.matches(clean)) return false
        return STATE_CODES.containsKey(clean.substring(0, 2))
    }

    fun decode(rawGstin: String): DecodedGstinDetails? {
        val clean = rawGstin.trim().uppercase()
        if (!isValidFormat(clean)) return null
        val stateCode = clean.substring(0, 2)
        val stateName = STATE_CODES[stateCode] ?: return null
        val pan = clean.substring(2, 12)
        val entityChar = pan[3]
        val entityType = ENTITY_TYPES[entityChar] ?: "Registered GST Business"
        val entityNumber = clean[12].toString()
        val summary = "PAN: $pan • State: $stateName ($stateCode) • $entityType"
        return DecodedGstinDetails(
            gstin = clean,
            stateCode = stateCode,
            stateName = stateName,
            panNumber = pan,
            entityType = entityType,
            entityNumber = entityNumber,
            registrationSummary = summary
        )
    }

    fun isValidIndianPhone(rawPhone: String): Boolean {
        val digits = rawPhone.filter { it.isDigit() }
        return digits.length in 10..13
    }
}
