package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.InvoiceLineItem
import com.example.model.Product
import com.example.ui.theme.MonoFontFamily
import com.example.util.AppLanguage
import com.example.util.BillingCalculator
import com.example.viewmodel.VoiceBillingViewModel
import com.example.viewmodel.VoiceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceBillingSheet(
    voiceVm: VoiceBillingViewModel,
    inventory: List<Product>,
    isGstEnabled: Boolean,
    taxMode: String,
    initialLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onConfirmToInvoice: (items: List<InvoiceLineItem>, customerName: String, discount: Double) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var voiceLang by remember { mutableStateOf(initialLanguage) }
    val voiceState by voiceVm.voiceState.collectAsStateWithLifecycle()
    val transcript by voiceVm.transcript.collectAsStateWithLifecycle()
    val draftItems by voiceVm.draftItems.collectAsStateWithLifecycle()
    val draftCustomer by voiceVm.draftCustomerName.collectAsStateWithLifecycle()
    val draftDiscount by voiceVm.draftDiscount.collectAsStateWithLifecycle()
    val shouldAutoSave by voiceVm.shouldAutoSave.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        voiceVm.initTts(context)
    }

    LaunchedEffect(shouldAutoSave) {
        if (shouldAutoSave && draftItems.isNotEmpty()) {
            voiceVm.consumeAutoSaveTrigger()
            onConfirmToInvoice(draftItems, draftCustomer, draftDiscount)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceVm.startListening(context, voiceLang, inventory, isGstEnabled, taxMode)
        } else {
            voiceVm.updateTranscript("Ek fan 1500 rupaye ka aur do bulb 100 rupaye ke add karo")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_voice_billing_art),
                    contentDescription = "Voice Billing AI",
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Voice Billing (Hindi & English)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Text(
                        text = "Try: \"Ek fan 1500 rupaye ka aur do bulb 100 rupaye ke add karo\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // Language Chips + Read Aloud
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = voiceLang == AppLanguage.HINDI,
                        onClick = { voiceLang = AppLanguage.HINDI },
                        label = { Text("हिन्दी / Hinglish") }
                    )
                    FilterChip(
                        selected = voiceLang == AppLanguage.ENGLISH,
                        onClick = { voiceLang = AppLanguage.ENGLISH },
                        label = { Text("English") }
                    )
                }
                OutlinedButton(
                    onClick = { voiceVm.readDraftAloud(voiceLang, isGstEnabled, taxMode) },
                    modifier = Modifier.testTag("voice_read_aloud_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Read Bill")
                }
            }

            // Mic + Sample Quick Command
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isListening = voiceState is VoiceUiState.Listening
                Button(
                    onClick = {
                        if (isListening) {
                            voiceVm.stopListening()
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                voiceVm.startListening(context, voiceLang, inventory, isGstEnabled, taxMode)
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("voice_mic_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "Microphone"
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isListening) "Listening... Tap to Stop" else "Speak Voice Command")
                }

                OutlinedButton(
                    onClick = {
                        val sample = "Ek fan 1500 rupaye ka aur do bulb 100 rupaye ke add karo"
                        voiceVm.updateTranscript(sample)
                        voiceVm.processCommand(sample, inventory, voiceLang, isGstEnabled, taxMode)
                    },
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Sample")
                }
            }

            // Command Input Box (Also supports direct typed edit/command)
            OutlinedTextField(
                value = transcript,
                onValueChange = { voiceVm.updateTranscript(it) },
                label = { Text("Voice Transcript / Hinglish Command") },
                placeholder = { Text("e.g. Fan ki quantity 2 karo / 50 rupaye discount") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_transcript_input"),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            voiceVm.processCommand(transcript, inventory, voiceLang, isGstEnabled, taxMode)
                        },
                        enabled = transcript.isNotBlank() && voiceState !is VoiceUiState.ProcessingAi
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Parse Command")
                    }
                }
            )

            // AI Status Banner
            when (val st = voiceState) {
                is VoiceUiState.ProcessingAi -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Understanding Hindi/English billing items with AI...")
                    }
                }
                is VoiceUiState.Ready -> {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = st.message,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is VoiceUiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = st.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                else -> {}
            }

            // Draft Items Preview & Confirmation
            if (draftItems.isNotEmpty()) {
                val lines = draftItems.map {
                    BillingCalculator.calculateLineItem(it.qty, it.unitPrice, it.discountPercent, it.gstRate, isGstEnabled, taxMode)
                }
                val totals = BillingCalculator.calculateInvoiceTotals(lines, draftDiscount, 0.0)

                Text(
                    text = "Draft Bill for $draftCustomer (${draftItems.size} items)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                draftItems.forEachIndexed { idx, item ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${BillingCalculator.formatInr(item.unitPrice)} x ${item.qty} (GST ${item.gstRate.toInt()}%)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { voiceVm.updateDraftItemQty(idx, item.qty - 1.0, isGstEnabled, taxMode) }
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "Decrease")
                                }
                                Text("${item.qty.toInt()}", fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { voiceVm.updateDraftItemQty(idx, item.qty + 1.0, isGstEnabled, taxMode) }
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Increase")
                                }
                                Text(
                                    BillingCalculator.formatInr(item.total),
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Estimated Grand Total", fontWeight = FontWeight.Bold)
                    Text(
                        BillingCalculator.formatInr(totals.grandTotal),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { voiceVm.clearDraft() },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                    Button(
                        onClick = { onConfirmToInvoice(draftItems, draftCustomer, draftDiscount) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("voice_confirm_save_button")
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm & Load into Invoice")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
