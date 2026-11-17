package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VoiceBillingAiRepository
import com.example.data.VoiceCommandResult
import com.example.model.InvoiceLineItem
import com.example.model.Product
import com.example.util.AppLanguage
import com.example.util.BillingCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface VoiceUiState {
    data object Idle : VoiceUiState
    data object Listening : VoiceUiState
    data object ProcessingAi : VoiceUiState
    data class Ready(val message: String) : VoiceUiState
    data class Error(val message: String) : VoiceUiState
}

class VoiceBillingViewModel(
    private val aiRepository: VoiceBillingAiRepository = VoiceBillingAiRepository()
) : ViewModel() {

    private val _voiceState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val voiceState: StateFlow<VoiceUiState> = _voiceState.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _draftItems = MutableStateFlow<List<InvoiceLineItem>>(emptyList())
    val draftItems: StateFlow<List<InvoiceLineItem>> = _draftItems.asStateFlow()

    private val _draftCustomerName = MutableStateFlow("Walk-in Customer")
    val draftCustomerName: StateFlow<String> = _draftCustomerName.asStateFlow()

    private val _draftDiscount = MutableStateFlow(0.0)
    val draftDiscount: StateFlow<Double> = _draftDiscount.asStateFlow()

    private val _shouldAutoSave = MutableStateFlow(false)
    val shouldAutoSave: StateFlow<Boolean> = _shouldAutoSave.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun initTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    fun speakText(text: String, language: AppLanguage) {
        if (!ttsReady || text.isBlank()) return
        val locale = if (language == AppLanguage.HINDI) Locale("hi", "IN") else Locale("en", "IN")
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bharatbill_tts")
    }

    fun readDraftAloud(language: AppLanguage, isGstEnabled: Boolean, taxMode: String) {
        val items = _draftItems.value
        if (items.isEmpty()) {
            val emptyMsg = if (language == AppLanguage.HINDI) {
                "बिल में अभी कोई आइटम नहीं है।"
            } else {
                "No items in the draft invoice yet."
            }
            speakText(emptyMsg, language)
            return
        }
        val lines = items.map {
            BillingCalculator.calculateLineItem(it.qty, it.unitPrice, it.discountPercent, it.gstRate, isGstEnabled, taxMode)
        }
        val totals = BillingCalculator.calculateInvoiceTotals(lines, _draftDiscount.value, 0.0)
        val summary = if (language == AppLanguage.HINDI) {
            val itemText = items.joinToString(", ") { "${it.qty.toInt()} ${it.name} ${it.total.toInt()} रुपये" }
            "ग्राहक ${_draftCustomerName.value}। आइटम: $itemText। कुल बिल ${totals.grandTotal.toInt()} रुपये है।"
        } else {
            val itemText = items.joinToString(", ") { "${it.qty} ${it.name} for ${it.total.toInt()} rupees" }
            "Invoice for ${_draftCustomerName.value}. Items: $itemText. Grand total is ${totals.grandTotal.toInt()} rupees."
        }
        speakText(summary, language)
    }

    fun startListening(
        context: Context,
        language: AppLanguage,
        inventory: List<Product>,
        isGstEnabled: Boolean,
        taxMode: String
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _voiceState.value = VoiceUiState.Error(
                "Speech recognition service unavailable on this device. You can type your Hindi/English voice command below."
            )
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = VoiceUiState.Listening
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    _voiceState.value = VoiceUiState.Error(
                        "Voice capture error (code $error). Speak clearly or enter command below."
                    )
                }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!spoken.isNullOrBlank()) {
                        _transcript.value = spoken
                        processCommand(spoken, inventory, language, isGstEnabled, taxMode)
                    } else {
                        _voiceState.value = VoiceUiState.Error("No speech detected. Please try again.")
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        _transcript.value = partial
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == AppLanguage.HINDI) "hi-IN" else "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        _voiceState.value = VoiceUiState.Listening
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        if (_voiceState.value is VoiceUiState.Listening) {
            _voiceState.value = VoiceUiState.Idle
        }
    }

    fun updateTranscript(text: String) {
        _transcript.value = text
    }

    fun processCommand(
        commandText: String,
        inventory: List<Product>,
        language: AppLanguage,
        isGstEnabled: Boolean,
        taxMode: String
    ) {
        if (commandText.isBlank()) return
        viewModelScope.launch {
            _voiceState.value = VoiceUiState.ProcessingAi
            val langHint = if (language == AppLanguage.HINDI) "Hindi/Hinglish" else "English/Hinglish"
            val result = aiRepository.parseVoiceCommand(commandText, inventory, langHint)
            result.onSuccess { cmd ->
                applyParsedCommand(cmd, inventory, language, isGstEnabled, taxMode)
            }.onFailure { err ->
                Log.e(TAG, "Voice AI failed", err)
                _voiceState.value = VoiceUiState.Error(
                    err.localizedMessage ?: "Voice AI failed to process command."
                )
            }
        }
    }

    private fun applyParsedCommand(
        cmd: VoiceCommandResult,
        inventory: List<Product>,
        language: AppLanguage,
        isGstEnabled: Boolean,
        taxMode: String
    ) {
        val current = _draftItems.value.toMutableList()
        when (cmd.action) {
            "ADD_ITEMS" -> {
                cmd.items.forEach { parsed ->
                    val matched = parsed.matchedProductId?.let { id -> inventory.firstOrNull { it.id == id } }
                        ?: inventory.firstOrNull { it.name.contains(parsed.productName, ignoreCase = true) }
                    val price = parsed.unitPrice ?: matched?.sellingPrice ?: 0.0
                    val gst = parsed.gstRate ?: matched?.gstRate ?: if (isGstEnabled) 18.0 else 0.0
                    val unit = matched?.unit ?: "PCS"
                    val hsn = matched?.hsnSac ?: ""
                    val calc = BillingCalculator.calculateLineItem(
                        qty = parsed.qty,
                        unitPrice = price,
                        discountPercent = 0.0,
                        gstRate = gst,
                        isGstEnabled = isGstEnabled,
                        taxMode = taxMode
                    )
                    val existingIdx = current.indexOfFirst {
                        it.name.equals(matched?.name ?: parsed.productName, ignoreCase = true) && it.unitPrice == price
                    }
                    if (existingIdx >= 0) {
                        val old = current[existingIdx]
                        val newQty = old.qty + parsed.qty
                        val newCalc = BillingCalculator.calculateLineItem(
                            newQty, old.unitPrice, old.discountPercent, old.gstRate, isGstEnabled, taxMode
                        )
                        current[existingIdx] = old.copy(
                            qty = newQty,
                            taxableAmount = newCalc.taxableAmount,
                            cgst = newCalc.cgst,
                            sgst = newCalc.sgst,
                            igst = newCalc.igst,
                            total = newCalc.total
                        )
                    } else {
                        current += InvoiceLineItem(
                            productId = matched?.id ?: "",
                            name = matched?.name ?: parsed.productName,
                            hsnSac = hsn,
                            unit = unit,
                            qty = parsed.qty,
                            unitPrice = price,
                            discountPercent = 0.0,
                            gstRate = gst,
                            taxableAmount = calc.taxableAmount,
                            cgst = calc.cgst,
                            sgst = calc.sgst,
                            igst = calc.igst,
                            total = calc.total
                        )
                    }
                }
            }
            "UPDATE_ITEM" -> {
                cmd.items.forEach { parsed ->
                    val idx = current.indexOfFirst { it.name.contains(parsed.productName, ignoreCase = true) }
                    if (idx >= 0) {
                        val old = current[idx]
                        val newPrice = parsed.unitPrice ?: old.unitPrice
                        val newCalc = BillingCalculator.calculateLineItem(
                            parsed.qty, newPrice, old.discountPercent, old.gstRate, isGstEnabled, taxMode
                        )
                        current[idx] = old.copy(
                            qty = parsed.qty,
                            unitPrice = newPrice,
                            taxableAmount = newCalc.taxableAmount,
                            cgst = newCalc.cgst,
                            sgst = newCalc.sgst,
                            igst = newCalc.igst,
                            total = newCalc.total
                        )
                    }
                }
            }
            "REMOVE_ITEM" -> {
                cmd.items.forEach { parsed ->
                    current.removeAll { it.name.contains(parsed.productName, ignoreCase = true) }
                }
            }
            "SET_DISCOUNT" -> {
                cmd.discountAmount?.let { _draftDiscount.value = it.coerceAtLeast(0.0) }
            }
            "SET_CUSTOMER" -> {
                cmd.customerName?.let { _draftCustomerName.value = it }
            }
            "CLEAR_DRAFT" -> {
                current.clear()
                _draftDiscount.value = 0.0
            }
            "READ_INVOICE" -> {
                readDraftAloud(language, isGstEnabled, taxMode)
            }
            "SAVE_INVOICE" -> {
                _shouldAutoSave.value = true
            }
        }
        _draftItems.value = current
        val reply = cmd.spokenReply.ifBlank { "Draft updated." }
        _voiceState.value = VoiceUiState.Ready(reply)
        if (cmd.action != "READ_INVOICE") {
            speakText(reply, language)
        }
    }

    fun consumeAutoSaveTrigger() {
        _shouldAutoSave.value = false
    }

    fun updateDraftItemQty(index: Int, newQty: Double, isGstEnabled: Boolean, taxMode: String) {
        val list = _draftItems.value.toMutableList()
        if (index !in list.indices) return
        if (newQty <= 0.0) {
            list.removeAt(index)
        } else {
            val old = list[index]
            val calc = BillingCalculator.calculateLineItem(newQty, old.unitPrice, old.discountPercent, old.gstRate, isGstEnabled, taxMode)
            list[index] = old.copy(
                qty = newQty,
                taxableAmount = calc.taxableAmount,
                cgst = calc.cgst,
                sgst = calc.sgst,
                igst = calc.igst,
                total = calc.total
            )
        }
        _draftItems.value = list
    }

    fun clearDraft() {
        _draftItems.value = emptyList()
        _draftDiscount.value = 0.0
        _draftCustomerName.value = "Walk-in Customer"
        _transcript.value = ""
        _voiceState.value = VoiceUiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    private companion object {
        const val TAG = "VoiceBillingVM"
    }
}
