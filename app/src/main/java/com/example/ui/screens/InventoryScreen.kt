package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Product
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoFontFamily
import com.example.util.BillingCalculator
import com.example.viewmodel.BillingWorkspaceData

@Composable
fun InventoryScreen(
    workspace: BillingWorkspaceData,
    onSaveProduct: (Product, () -> Unit) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onAdjustStock: (Product, Double, String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var adjustProduct by remember { mutableStateOf<Product?>(null) }

    val categories = remember(workspace.products) {
        listOf("All", "Low Stock") + workspace.products.map { it.category }.filter { it.isNotBlank() }.distinct()
    }

    val filtered = remember(workspace.products, search, selectedCategory) {
        workspace.products.filter { p ->
            val matchSearch = search.isBlank() ||
                p.name.contains(search, ignoreCase = true) ||
                p.barcode.contains(search, ignoreCase = true) ||
                p.sku.contains(search, ignoreCase = true)
            val matchCat = when (selectedCategory) {
                "All" -> true
                "Low Stock" -> p.stockQty <= p.lowStockThreshold
                else -> p.category.equals(selectedCategory, ignoreCase = true)
            }
            matchSearch && matchCat
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Products (${workspace.products.size})") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Stock History (${workspace.stockAdjustments.size})") })
        }

        if (selectedTab == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search name, SKU or barcode...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        editingProduct = null
                        showProductDialog = true
                    },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("inventory_add_product_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filtered.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Inventory2,
                            title = "No Products Yet",
                            subtitle = "Tap '+ Add' to add unlimited products with barcode, HSN/SAC, GST slab, and photo."
                        )
                    }
                } else {
                    items(filtered, key = { it.id }) { prod ->
                        val isLow = prod.stockQty <= prod.lowStockThreshold
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!prod.imageUri.isNullOrBlank()) {
                                    AsyncImage(
                                        model = prod.imageUri,
                                        contentDescription = prod.name,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Inventory2,
                                            contentDescription = prod.name,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(14.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(prod.name, fontWeight = FontWeight.Bold)
                                        if (isLow) StatusPill("LOW")
                                    }
                                    Text(
                                        "Sell ${BillingCalculator.formatInr(prod.sellingPrice)} • Buy ${BillingCalculator.formatInr(prod.purchasePrice)} • GST ${prod.gstRate.toInt()}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Stock: ${prod.stockQty} ${prod.unit}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                                            color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                        )
                                        if (prod.barcode.isNotBlank()) {
                                            Text("Barcode: ${prod.barcode}", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    IconButton(onClick = { adjustProduct = prod }) {
                                        Icon(Icons.Rounded.Exposure, contentDescription = "Adjust Stock")
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            editingProduct = prod
                                            showProductDialog = true
                                        }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                                        }
                                        IconButton(onClick = { onDeleteProduct(prod) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (workspace.stockAdjustments.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.Exposure,
                            title = "No Stock Adjustments Yet",
                            subtitle = "Automatic invoice deductions, purchases, and manual adjustments appear here."
                        )
                    }
                } else {
                    items(workspace.stockAdjustments, key = { it.id }) { adj ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(adj.productName, fontWeight = FontWeight.Bold)
                                    Text(adj.reason, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        BillingCalculator.formatDate(adj.adjustmentEpochMs),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val sign = if (adj.deltaQty >= 0) "+" else ""
                                    Text(
                                        "$sign${adj.deltaQty}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                        color = if (adj.deltaQty >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("${adj.previousQty} → ${adj.newQty}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProductDialog) {
        ProductEditorDialog(
            existing = editingProduct,
            onDismiss = { showProductDialog = false },
            onSave = { prod ->
                onSaveProduct(prod) { showProductDialog = false }
            }
        )
    }

    adjustProduct?.let { prod ->
        var deltaStr by remember { mutableStateOf("") }
        var reasonStr by remember { mutableStateOf("Manual Stock Count") }
        AlertDialog(
            onDismissRequest = { adjustProduct = null },
            title = { Text("Adjust Stock • ${prod.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Current Stock: ${prod.stockQty} ${prod.unit}")
                    OutlinedTextField(
                        value = deltaStr,
                        onValueChange = { deltaStr = it },
                        label = { Text("Quantity Change (+10 or -5)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reasonStr,
                        onValueChange = { reasonStr = it },
                        label = { Text("Adjustment Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val d = deltaStr.toDoubleOrNull()
                    if (d != null && d != 0.0) {
                        onAdjustStock(prod, d, reasonStr)
                        adjustProduct = null
                    }
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { adjustProduct = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProductEditorDialog(
    existing: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var barcode by remember { mutableStateOf(existing?.barcode ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "General") }
    var hsnSac by remember { mutableStateOf(existing?.hsnSac ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "PCS") }
    var purchasePrice by remember { mutableStateOf(existing?.purchasePrice?.toString() ?: "") }
    var sellingPrice by remember { mutableStateOf(existing?.sellingPrice?.toString() ?: "") }
    var gstRate by remember { mutableStateOf(existing?.gstRate ?: 18.0) }
    var stockQty by remember { mutableStateOf(existing?.stockQty?.toString() ?: "10") }
    var lowThreshold by remember { mutableStateOf(existing?.lowStockThreshold?.toString() ?: "5") }
    var imageUri by remember { mutableStateOf(existing?.imageUri) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) imageUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Product" else "Edit Product") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (imageUri.isNullOrBlank()) "Pick Product Photo" else "Change Product Photo")
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("SKU") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = barcode,
                        onValueChange = { barcode = it },
                        label = { Text("Barcode") },
                        trailingIcon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = hsnSac,
                        onValueChange = { hsnSac = it },
                        label = { Text("HSN/SAC") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePrice,
                        onValueChange = { purchasePrice = it },
                        label = { Text("Purchase ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = sellingPrice,
                        onValueChange = { sellingPrice = it },
                        label = { Text("Selling ₹ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("product_price_input")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stockQty,
                        onValueChange = { stockQty = it },
                        label = { Text("Stock Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lowThreshold,
                        onValueChange = { lowThreshold = it },
                        label = { Text("Low Alert") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.uppercase() },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(0.8f)
                    )
                }
                Text("GST Slab %", style = MaterialTheme.typography.labelSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(0.0, 5.0, 12.0, 18.0, 28.0)) { slab ->
                        FilterChip(
                            selected = gstRate == slab,
                            onClick = { gstRate = slab },
                            label = { Text("${slab.toInt()}%") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        Product(
                            id = existing?.id ?: "",
                            userId = existing?.userId ?: "",
                            name = name.trim(),
                            sku = sku.trim(),
                            barcode = barcode.trim(),
                            category = category.trim().ifBlank { "General" },
                            hsnSac = hsnSac.trim(),
                            unit = unit.trim().ifBlank { "PCS" },
                            purchasePrice = (purchasePrice.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                            sellingPrice = (sellingPrice.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                            gstRate = gstRate,
                            stockQty = (stockQty.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                            lowStockThreshold = (lowThreshold.toDoubleOrNull() ?: 5.0).coerceAtLeast(0.0),
                            imageUri = imageUri
                        )
                    )
                },
                enabled = name.isNotBlank() && sellingPrice.toDoubleOrNull() != null,
                modifier = Modifier.testTag("save_product_button")
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
