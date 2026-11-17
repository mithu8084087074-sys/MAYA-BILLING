package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoneyOff
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ThemeMode
import com.example.model.Invoice
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SalesBarChart
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoFontFamily
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.util.BillingCalculator
import com.example.viewmodel.BillingWorkspaceData

@Composable
fun DashboardScreen(
    workspace: BillingWorkspaceData,
    language: AppLanguage,
    themeMode: ThemeMode,
    onToggleLanguage: () -> Unit,
    onCycleTheme: () -> Unit,
    onOpenNewBill: () -> Unit,
    onOpenVoiceBill: () -> Unit,
    onOpenInventory: () -> Unit,
    onOpenParties: () -> Unit,
    onOpenOwnerAdmin: () -> Unit,
    onOpenInvoiceDetail: (Invoice) -> Unit,
    onOpenPendingUpi: () -> Unit
) {
    val m = workspace.metrics
    val pendingUpiCount = workspace.payments.count { it.verificationStatus == "PENDING" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Branded Header Banner
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    Color(0xFF17446E)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_store_logo),
                                    contentDescription = "Business Logo",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column {
                                    Text(
                                        text = workspace.config.businessName,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = buildString {
                                            append("v${workspace.config.appVersion}")
                                            if (workspace.config.gstin.isNotBlank()) {
                                                append(" • GSTIN ${workspace.config.gstin}")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Row {
                                IconButton(
                                    onClick = onToggleLanguage,
                                    modifier = Modifier.testTag("toggle_language_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Translate,
                                        contentDescription = "Switch Language",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = onCycleTheme,
                                    modifier = Modifier.testTag("toggle_theme_button")
                                ) {
                                    Icon(
                                        imageVector = if (themeMode == ThemeMode.DARK) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                        contentDescription = "Toggle Dark Mode",
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenNewBill,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dashboard_new_bill_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(AppStrings.get("new_invoice", language))
                            }
                            if (workspace.config.voiceBillingEnabled) {
                                FilledTonalButton(
                                    onClick = onOpenVoiceBill,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("dashboard_voice_bill_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(AppStrings.get("voice_billing", language))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Maintenance Mode or Pending UPI Alert
        if (workspace.config.maintenanceMode) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = "Maintenance Mode",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Maintenance Mode is ACTIVE. Some billing mutations are restricted by Owner.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        if (pendingUpiCount > 0) {
            item {
                ElevatedCard(
                    onClick = onOpenPendingUpi,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pending_upi_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Rounded.QrCodeScanner,
                                contentDescription = "Pending UPI Verification",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column {
                                Text(
                                    text = "$pendingUpiCount UPI Payment(s) Awaiting Owner Verification",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Tap to verify UTR reference before marking Paid",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Primary Sales & Profit Grid (All 11 Required Metrics!)
        item {
            SectionHeader(title = "Business Overview (${language.label})")
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricCard(
                    title = AppStrings.get("total_sales", language),
                    value = BillingCalculator.formatCompactInr(m.totalSales),
                    subtitle = "${workspace.invoices.size} invoices",
                    icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = AppStrings.get("todays_sales", language),
                    value = BillingCalculator.formatCompactInr(m.todaysSales),
                    subtitle = "Today",
                    icon = Icons.Rounded.Today,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricCard(
                    title = AppStrings.get("monthly_sales", language),
                    value = BillingCalculator.formatCompactInr(m.monthlySales),
                    subtitle = "This month",
                    icon = Icons.Rounded.CalendarMonth,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = AppStrings.get("total_purchases", language),
                    value = BillingCalculator.formatCompactInr(m.totalPurchases),
                    subtitle = "${workspace.purchases.size} entries",
                    icon = Icons.Rounded.ShoppingBag,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenParties
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricCard(
                    title = AppStrings.get("total_profit", language),
                    value = BillingCalculator.formatCompactInr(m.netProfit),
                    subtitle = "Gross ${BillingCalculator.formatCompactInr(m.grossProfit)}",
                    icon = Icons.Rounded.AccountBalanceWallet,
                    accentColor = if (m.netProfit >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = AppStrings.get("total_expenses", language),
                    value = BillingCalculator.formatCompactInr(m.totalExpenses),
                    subtitle = "${workspace.expenses.size} bills",
                    icon = Icons.Rounded.MoneyOff,
                    accentColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenParties
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiMetricCard(
                    title = AppStrings.get("pending_payments", language),
                    value = BillingCalculator.formatCompactInr(m.pendingPayments),
                    subtitle = "Receivables",
                    icon = Icons.Rounded.PendingActions,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPendingUpi
                )
                KpiMetricCard(
                    title = "${AppStrings.get("total_customers", language)} / ${AppStrings.get("total_products", language)}",
                    value = "${m.totalCustomers} / ${m.totalProducts}",
                    subtitle = "${m.lowStockProducts.size} low stock",
                    icon = Icons.Rounded.Group,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenInventory
                )
            }
        }

        // 7-Day Sales Bar Chart
        item {
            SalesBarChart(points = m.dailyChartPoints)
        }

        // Low Stock Alerts
        item {
            SectionHeader(
                title = "${AppStrings.get("low_stock_alerts", language)} (${m.lowStockProducts.size})",
                actionLabel = "Manage Stock",
                onActionClick = onOpenInventory
            )
        }

        if (m.lowStockProducts.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = if (workspace.products.isEmpty()) {
                                "Add products in Inventory to track real-time stock levels."
                            } else {
                                "All ${workspace.products.size} products are well stocked!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(m.lowStockProducts, key = { it.id }) { prod ->
                        ElevatedCard(
                            onClick = onOpenInventory,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = prod.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Left: ${prod.stockQty} ${prod.unit} (Min ${prod.lowStockThreshold})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Owner Quick Access Card
        item {
            ElevatedCard(
                onClick = onOpenOwnerAdmin,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_owner_admin_card"),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.AdminPanelSettings,
                                contentDescription = "Owner Dashboard",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = AppStrings.get("owner_dashboard", language),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "GST, UPI QR, Staff Roles, Cloud Backup & Upgrade Center",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        // Recent Transactions
        item {
            SectionHeader(
                title = AppStrings.get("recent_transactions", language),
                actionLabel = "View All",
                onActionClick = onOpenNewBill
            )
        }

        if (m.recentInvoices.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "No Invoices Yet",
                    subtitle = "Tap 'New Invoice' or 'Voice Bill' above to create your first GST or Non-GST bill."
                )
            }
        } else {
            items(m.recentInvoices, key = { it.id }) { inv ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInvoiceDetail(inv) },
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = inv.invoiceNumber,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFontFamily),
                                    fontWeight = FontWeight.Bold
                                )
                                StatusPill(status = inv.paymentStatus)
                            }
                            Text(
                                text = inv.customerName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${BillingCalculator.formatDate(inv.invoiceEpochMs)} • ${inv.invoiceType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = BillingCalculator.formatInr(inv.grandTotal),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (inv.balanceDue > 0.0) {
                                Text(
                                    text = "Due ${BillingCalculator.formatInr(inv.balanceDue)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
