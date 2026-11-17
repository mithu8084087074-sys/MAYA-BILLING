package com.example.data

import android.util.Log
import com.example.model.Product
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class VoiceParsedItem(
    val productName: String,
    val matchedProductId: String?,
    val qty: Double,
    val unitPrice: Double?,
    val gstRate: Double?
)

data class VoiceCommandResult(
    val action: String, // ADD_ITEMS, UPDATE_ITEM, REMOVE_ITEM, SET_DISCOUNT, SET_CUSTOMER, SAVE_INVOICE, READ_INVOICE, CLEAR_DRAFT
    val items: List<VoiceParsedItem> = emptyList(),
    val customerName: String? = null,
    val discountAmount: Double? = null,
    val spokenReply: String = ""
)

class VoiceBillingAiRepository {

    private val voiceSchema = Schema.obj(
        properties = mapOf(
            "action" to Schema.enumeration(
                values = listOf(
                    "ADD_ITEMS",
                    "UPDATE_ITEM",
                    "REMOVE_ITEM",
                    "SET_DISCOUNT",
                    "SET_CUSTOMER",
                    "SAVE_INVOICE",
                    "READ_INVOICE",
                    "CLEAR_DRAFT"
                ),
                description = "Primary billing action requested by the user"
            ),
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "productName" to Schema.string(description = "Product name extracted from voice command"),
                        "matchedProductId" to Schema.string(
                            description = "ID of matching inventory product if found, else empty string",
                            nullable = true
                        ),
                        "qty" to Schema.double(description = "Quantity as number, e.g. 1.0, 2.0"),
                        "unitPrice" to Schema.double(
                            description = "Unit price in INR if spoken, else null",
                            nullable = true
                        ),
                        "gstRate" to Schema.double(
                            description = "GST percentage if spoken, else null",
                            nullable = true
                        )
                    ),
                    optionalProperties = listOf("matchedProductId", "unitPrice", "gstRate")
                ),
                description = "List of items referenced in the command"
            ),
            "customerName" to Schema.string(
                description = "Customer name if user said to assign customer",
                nullable = true
            ),
            "discountAmount" to Schema.double(
                description = "Discount amount in INR if spoken",
                nullable = true
            ),
            "spokenReply" to Schema.string(
                description = "Short polite confirmation in the same language (Hindi/Hinglish or English) as the user"
            )
        ),
        optionalProperties = listOf("customerName", "discountAmount")
    )

    private val model by lazy {
        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = voiceSchema
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun parseVoiceCommand(
        transcript: String,
        inventory: List<Product>,
        languageHint: String
    ): Result<VoiceCommandResult> {
        return try {
            val catalogSummary = inventory.take(60).joinToString("\n") { p ->
                "- id=${p.id}, name=${p.name}, price=${p.sellingPrice}, gst=${p.gstRate}%, unit=${p.unit}"
            }.ifBlank { "(No inventory products saved yet; extract spoken product names and prices directly.)" }

            val prompt = """
                You are an Indian GST Billing Voice Assistant for BharatBill.
                Parse the shopkeeper's spoken Hindi, Hinglish, or English billing command into structured JSON.
                
                Rules:
                - Understand Hindi numbers: ek=1, do=2, teen=3, char=4, paanch=5, chhah=6, saat=7, aath=8, nau=9, das=10, aadha=0.5, dedh=1.5, dhai=2.5.
                - Understand phrases like "Ek fan 1500 rupaye ka aur do bulb 100 rupaye ke add karo" -> ADD_ITEMS: [{productName: "Fan", qty: 1, unitPrice: 1500}, {productName: "Bulb", qty: 2, unitPrice: 100}].
                - Match against the inventory catalog below when a product closely matches (put matchedProductId). If user did not state a price, use catalog price or null. If user explicitly stated a price, always respect the spoken unitPrice.
                - If user says "quantity 3 karo" or "change fan qty to 4" -> UPDATE_ITEM.
                - If user says "bulb hatao" or "remove bulb" -> REMOVE_ITEM.
                - If user says "bill sunao" or "read invoice" -> READ_INVOICE.
                - If user says "save karo" or "bill save karo" or "confirm" -> SAVE_INVOICE.
                - Preferred reply language hint: $languageHint.

                Inventory Catalog:
                $catalogSummary

                Spoken Command:
                "$transcript"
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("Voice AI returned an empty response."))

            val json = JSONObject(rawJson)
            val action = json.optString("action", "ADD_ITEMS")
            val itemsJson = json.optJSONArray("items")
            val parsedItems = mutableListOf<VoiceParsedItem>()
            if (itemsJson != null) {
                for (i in 0 until itemsJson.length()) {
                    val obj = itemsJson.getJSONObject(i)
                    val matchedId = obj.optString("matchedProductId", "").takeIf { it.isNotBlank() && it != "null" }
                    val unitPrice = if (obj.has("unitPrice") && !obj.isNull("unitPrice")) obj.optDouble("unitPrice") else null
                    val gstRate = if (obj.has("gstRate") && !obj.isNull("gstRate")) obj.optDouble("gstRate") else null
                    parsedItems += VoiceParsedItem(
                        productName = obj.optString("productName", "Item"),
                        matchedProductId = matchedId,
                        qty = obj.optDouble("qty", 1.0).coerceAtLeast(0.01),
                        unitPrice = unitPrice?.takeIf { !it.isNaN() && it >= 0.0 },
                        gstRate = gstRate?.takeIf { !it.isNaN() && it >= 0.0 }
                    )
                }
            }
            val customerName = json.optString("customerName", "").takeIf { it.isNotBlank() && it != "null" }
            val discountAmt = if (json.has("discountAmount") && !json.isNull("discountAmount")) {
                json.optDouble("discountAmount").takeIf { !it.isNaN() && it >= 0.0 }
            } else null
            val reply = json.optString("spokenReply", "Updated draft invoice.")

            Result.success(
                VoiceCommandResult(
                    action = action,
                    items = parsedItems,
                    customerName = customerName,
                    discountAmount = discountAmt,
                    spokenReply = reply
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice AI request timed out", e)
            Result.failure(Exception("Voice AI timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice AI parsing failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "VoiceBillingAiRepo"
    }
}
