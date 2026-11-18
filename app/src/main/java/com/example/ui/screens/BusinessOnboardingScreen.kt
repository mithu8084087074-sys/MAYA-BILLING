package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BusinessConfig
import com.example.ui.components.SignaturePadCard
import com.example.util.GstinHelper

@Composable
fun BusinessOnboardingScreen(
    config: BusinessConfig,
    userEmail: String,
    defaultDisplayName: String,
    pendingPhone: String,
    pendingGstin: String,
    isSaving: Boolean,
    onSaveProfile: (BusinessConfig) -> Unit,
    onSkip: () -> Unit
) {
    val initialGstin = pendingGstin.ifBlank { config.gstin }.uppercase()
    val decodedInitial = remember(initialGstin) { GstinHelper.decode(initialGstin) }

    var businessName by remember(config.businessName, defaultDisplayName) {
        val seed = when {
            config.businessName.isNotBlank() && config.businessName != "Maya Bill Store" -> config.businessName
            defaultDisplayName.isNotBlank() && defaultDisplayName != "Maya Bill Store" -> defaultDisplayName
            else -> ""
        }
        mutableStateOf(seed)
    }
    var businessPhone by remember(pendingPhone, config.businessPhone) {
        mutableStateOf(pendingPhone.ifBlank { config.businessPhone })
    }
    var businessAddress by remember(config.businessAddress) {
        mutableStateOf(config.businessAddress)
    }
    var gstin by remember(initialGstin) { mutableStateOf(initialGstin) }
    var stateName by remember(config.stateName, decodedInitial) {
        mutableStateOf(decodedInitial?.stateName ?: config.stateName)
    }
    var gstEntityDetails by remember(config.gstEntityDetails, decodedInitial) {
        mutableStateOf(decodedInitial?.registrationSummary ?: config.gstEntityDetails)
    }
    var ownerSignatureStrokes by remember(config.ownerSignatureStrokes) {
        mutableStateOf(config.ownerSignatureStrokes)
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(gstin) {
        val decoded = GstinHelper.decode(gstin)
        if (decoded != null) {
            stateName = decoded.stateName
            gstEntityDetails = decoded.registrationSummary
        }
    }

    val decodedPreview = remember(gstin) { GstinHelper.decode(gstin) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ElevatedCard(
                modifier = Modifier.widthIn(max = 560.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_store_logo),
                            contentDescription = "Maya Bill App Logo",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Maya Bill App • Business Setup",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (userEmail.isNotBlank()) "Signed in: $userEmail" else "Complete your shop profile for PDF invoices",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // GSTIN Auto-Fetch Card
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "GSTIN Lookup & Tax Profile (Optional)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = gstin,
                                    onValueChange = { gstin = it.uppercase().take(15) },
                                    label = { Text("15-Digit GSTIN") },
                                    placeholder = { Text("e.g. 29ABCDE1234F1Z5") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("onboarding_gstin_input")
                                )
                                FilledTonalButton(
                                    onClick = {
                                        val decoded = GstinHelper.decode(gstin)
                                        if (decoded != null) {
                                            stateName = decoded.stateName
                                            gstEntityDetails = decoded.registrationSummary
                                            errorText = null
                                        } else {
                                            errorText = "Enter a valid 15-character Indian GSTIN (e.g. 29ABCDE1234F1Z5)."
                                        }
                                    },
                                    modifier = Modifier.testTag("onboarding_fetch_gst_button")
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Fetch GST")
                                }
                            }

                            if (decodedPreview != null) {
                                ElevatedCard(
                                    colors = CardDefaults.elevatedCardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "GST Verified",
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "GST Details Auto-Fetched",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                        Text(
                                            text = "State: ${decodedPreview.stateName} (Code ${decodedPreview.stateCode})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "PAN: ${decodedPreview.panNumber} • ${decodedPreview.entityType}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Business / Shop Name *") },
                        placeholder = { Text("e.g. Maya Electronics & Traders") },
                        leadingIcon = { Icon(Icons.Rounded.Business, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_business_name_input")
                    )

                    OutlinedTextField(
                        value = businessPhone,
                        onValueChange = { businessPhone = it.take(15) },
                        label = { Text("Business Phone Number *") },
                        placeholder = { Text("10-digit mobile number") },
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_phone_input")
                    )

                    OutlinedTextField(
                        value = businessAddress,
                        onValueChange = { businessAddress = it },
                        label = { Text("Business Full Address *") },
                        placeholder = { Text("Shop No, Market/Street, City, Pincode") },
                        leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_address_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = stateName,
                            onValueChange = { stateName = it },
                            label = { Text("State") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = gstEntityDetails,
                            onValueChange = { gstEntityDetails = it },
                            label = { Text("Business / GST Details") },
                            placeholder = { Text("Proprietorship / PAN / Tagline") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                    }

                    // Business Owner Signature Pad
                    SignaturePadCard(
                        encodedStrokes = ownerSignatureStrokes,
                        onStrokesChanged = { ownerSignatureStrokes = it }
                    )

                    if (!errorText.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorText.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            when {
                                businessName.isBlank() -> {
                                    errorText = "Please enter your Business / Shop Name."
                                }
                                !GstinHelper.isValidIndianPhone(businessPhone) -> {
                                    errorText = "Please enter a valid 10-digit Phone Number."
                                }
                                businessAddress.isBlank() -> {
                                    errorText = "Please enter your Business Address."
                                }
                                gstin.isNotBlank() && !GstinHelper.isValidFormat(gstin) -> {
                                    errorText = "GSTIN must be 15 characters (or leave blank for Non-GST)."
                                }
                                ownerSignatureStrokes.isBlank() -> {
                                    errorText = "Please draw the Business Owner Signature above."
                                }
                                else -> {
                                    errorText = null
                                    onSaveProfile(
                                        config.copy(
                                            businessName = businessName.trim(),
                                            upiMerchantName = businessName.trim(),
                                            businessPhone = businessPhone.trim(),
                                            businessAddress = businessAddress.trim(),
                                            gstin = gstin.trim().uppercase(),
                                            stateName = stateName.trim().ifBlank { "Karnataka" },
                                            gstEntityDetails = gstEntityDetails.trim(),
                                            ownerSignatureStrokes = ownerSignatureStrokes,
                                            gstBillingEnabled = gstin.isNotBlank() || config.gstBillingEnabled,
                                            profileCompleted = true
                                        )
                                    )
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_save_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.VerifiedUser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isSaving) "Saving Profile..." else "Save Profile & Open Dashboard",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = onSkip,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("onboarding_skip_button")
                        ) {
                            Text("Skip for now (Complete later in Owner Admin)")
                        }
                    }
                }
            }
        }
    }
}
