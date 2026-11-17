package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.BusinessConfig
import com.example.model.Customer
import com.example.model.Invoice
import com.example.model.PaymentRecord
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoFontFamily
import com.example.util.BillingCalculator
import com.example.util.PdfInvoiceGenerator
import com.example.util.UpiAppTarget
import com.example.util.UpiQrGenerator

@Composable
fun UpiPaymentDialog(
    invoice: Invoice,
    business: BusinessConfig,
    customer: Customer?,
    existingPayments: List<PaymentRecord>,
    onDismiss: () -> Unit,
    onCreatePendingUpi: (amount: Double, upiApp: String, notes: String) -> Unit,
    onVerifyPayment: (payment: PaymentRecord, utrRef: String, status: String, notes: String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val invoicePayments = remember(existingPayments, invoice.id) {
        existingPayments.filter { it.invoiceId == invoice.id }
    }
    val pendingPayment = invoicePayments.firstOrNull { it.verificationStatus == "PENDING" }

    var amountInput by remember(invoice.balanceDue) {
        mutableStateOf("%.2f".format(invoice.balanceDue.coerceAtLeast(0.0)))
    }
    var selectedApp by remember { mutableStateOf(UpiAppTarget.GPAY) }
    var utrInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    val parsedAmount = amountInput.toDoubleOrNull()?.coerceIn(0.0, invoice.balanceDue.coerceAtLeast(invoice.grandTotal))
        ?: invoice.balanceDue
    val upiId = business.upiId.trim()
    val qrUri = remember(upiId, parsedAmount, invoice.invoiceNumber) {
        if (upiId.isNotBlank() && parsedAmount > 0.0) {
            UpiQrGenerator.buildUpiUri(
                upiId = upiId,
                merchantName = business.upiMerchantName.ifBlank { business.businessName },
                amount = parsedAmount,
                invoiceNumber = invoice.invoiceNumber,
                transactionRef = invoice.invoiceNumber
            )
        } else ""
    }
    val qrBitmap = remember(qrUri) {
        if (qrUri.isNotBlank()) UpiQrGenerator.generateQrBitmap(qrUri, 420) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("UPI QR Payment", fontWeight = FontWeight.Bold)
                    Text(
                        "${invoice.invoiceNumber} • Due ${BillingCalculator.formatInr(invoice.balanceDue)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(status = invoice.paymentStatus)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (upiId.isBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Owner UPI ID is not configured yet. Please set it in Owner Admin > Business & UPI Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (invoice.balanceDue > 0.0) {
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it },
                        label = { Text("Payment Amount (Full or Partial ₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Dynamic UPI QR Code",
                                    modifier = Modifier.size(180.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "$upiId • ${BillingCalculator.formatInr(parsedAmount)}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                    color = Color.Black
                                )
                            }
                        }
                    }

                    Text("Open Customer UPI App", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UpiAppTarget.entries.take(4).forEach { app ->
                            FilterChip(
                                selected = selectedApp == app,
                                onClick = { selectedApp = app },
                                label = { Text(app.label.split(" ").first()) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (qrUri.isNotBlank()) {
                                    val launched = UpiQrGenerator.launchUpiApp(context, qrUri, selectedApp)
                                    if (!launched) {
                                        onShowMessage("No UPI app installed on this device; customer can scan the QR above.")
                                    }
                                    if (pendingPayment == null) {
                                        onCreatePendingUpi(parsedAmount, selectedApp.label, "Launched ${selectedApp.label}")
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pay App")
                        }
                        Button(
                            onClick = {
                                onCreatePendingUpi(parsedAmount, selectedApp.label, "Dynamic QR generated")
                            },
                            enabled = parsedAmount > 0.0 && pendingPayment == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (pendingPayment == null) "Log Pending" else "Pending Logged")
                        }
                    }
                }

                // Explicit Owner Verification Section (NEVER auto-marks verified!)
                if (pendingPayment != null) {
                    HorizontalDivider()
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Owner Verification Required (${BillingCalculator.formatInr(pendingPayment.amount)})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Confirm funds in your bank/UPI app and enter the 12-digit UTR / Reference number before marking Paid.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            OutlinedTextField(
                                value = utrInput,
                                onValueChange = { utrInput = it },
                                label = { Text("UPI Transaction / UTR Reference *") },
                                placeholder = { Text("e.g. 432198765432") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upi_utr_input")
                            )
                            OutlinedTextField(
                                value = notesInput,
                                onValueChange = { notesInput = it },
                                label = { Text("Verification Note") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onVerifyPayment(pendingPayment, utrInput, "FAILED", notesInput.ifBlank { "Payment not received" })
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Failed")
                                }
                                Button(
                                    onClick = {
                                        onVerifyPayment(pendingPayment, utrInput, "VERIFIED", notesInput.ifBlank { "Verified by Owner" })
                                    },
                                    enabled = utrInput.trim().length >= 4,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("upi_verify_paid_button")
                                ) {
                                    Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Verify Paid")
                                }
                            }
                        }
                    }
                }

                // Payment History for this Invoice + Receipt Download
                if (invoicePayments.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Payment History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    invoicePayments.forEach { pay ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${BillingCalculator.formatInr(pay.amount)} • ${pay.method}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Ref: ${pay.transactionRef.ifBlank { "Pending" }} • ${pay.verificationStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    val receiptFile = PdfInvoiceGenerator.generatePaymentReceiptPdf(context, pay, business)
                                    PdfInvoiceGenerator.sharePdf(context, receiptFile, "Payment Receipt ${pay.invoiceNumber}")
                                }
                            ) {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Receipt", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Receipt")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
