package com.example.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.BackupRecord
import com.example.model.BusinessConfig
import com.example.model.ReleaseItem
import com.example.model.StaffUser
import com.example.model.UpgradeHistory
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.theme.MonoFontFamily
import com.example.util.BillingCalculator
import com.example.viewmodel.BillingWorkspaceData
import com.example.viewmodel.OwnerAdminState
import java.io.File

@Composable
fun OwnerSecurityStaffTab(
    config: BusinessConfig,
    adminData: OwnerAdminState,
    userEmail: String,
    isProcessing: Boolean,
    onOpenChangePassword: () -> Unit,
    onSaveSecurityOptions: (pin: String?, hint: String?, twoFactorEnabled: Boolean) -> Unit,
    onSaveStaff: (StaffUser, () -> Unit) -> Unit,
    onDeleteStaff: (StaffUser) -> Unit,
    onSignOutGoogle: () -> Unit
) {
    var recoveryPin by remember { mutableStateOf("") }
    var recoveryHint by remember(config.recoveryHint) { mutableStateOf(config.recoveryHint.orEmpty()) }
    var twoFactorEnabled by remember(config.twoFactorEnabled) { mutableStateOf(config.twoFactorEnabled) }
    var showStaffDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<StaffUser?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = "Security", tint = MaterialTheme.colorScheme.primary)
                            Text("Owner Security & Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (config.mustChangeInitialPassword) {
                            StatusPill(status = "ACTION")
                        }
                    }

                    Button(
                        onClick = onOpenChangePassword,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("owner_change_password_button")
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Change Owner Password")
                    }

                    OutlinedTextField(
                        value = recoveryPin,
                        onValueChange = { recoveryPin = it },
                        label = { Text("Set / Update 4-6 Digit Recovery PIN") },
                        placeholder = { Text(if (config.recoveryPinHash.isNullOrBlank()) "Not configured yet" else "PIN configured (enter to update)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = recoveryHint,
                        onValueChange = { recoveryHint = it },
                        label = { Text("Recovery Hint (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Two-Factor Authentication (2FA)", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Require an additional verification OTP when unlocking Owner Admin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = twoFactorEnabled,
                            onCheckedChange = { twoFactorEnabled = it },
                            modifier = Modifier.testTag("owner_2fa_switch")
                        )
                    }

                    OutlinedButton(
                        onClick = { onSaveSecurityOptions(recoveryPin.ifBlank { null }, recoveryHint.ifBlank { null }, twoFactorEnabled) },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_security_options_button")
                    ) {
                        Text("Save Recovery & 2FA Settings")
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "Users & Permissions (${adminData.staffUsers.size})")
                Button(
                    onClick = {
                        editingStaff = null
                        showStaffDialog = true
                    },
                    modifier = Modifier.testTag("add_staff_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Staff")
                }
            }
        }

        items(adminData.staffUsers, key = { it.id }) { staff ->
            ElevatedCard(
                onClick = {
                    editingStaff = staff
                    showStaffDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(staff.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            StatusPill(status = staff.role)
                            if (!staff.active) StatusPill(status = "FAILED")
                        }
                        Text(
                            staff.emailOrPhone,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val perms = buildList {
                            if (staff.canManageBilling) add("Billing")
                            if (staff.canManageInventory) add("Inventory")
                            if (staff.canManagePurchases) add("Purchases")
                            if (staff.canViewReports) add("Reports")
                        }.joinToString(" • ").ifBlank { "View Only" }
                        Text(perms, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (staff.role != "OWNER") {
                        IconButton(onClick = { onDeleteStaff(staff) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove Staff", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Cloud Account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Signed in as: ${userEmail.ifBlank { "Google Account" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onSignOutGoogle,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("google_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sign Out Google Account")
                    }
                }
            }
        }
    }

    if (showStaffDialog) {
        StaffEditorDialog(
            initial = editingStaff,
            onDismiss = { showStaffDialog = false },
            onSave = { staff ->
                onSaveStaff(staff) { showStaffDialog = false }
            }
        )
    }
}

@Composable
fun OwnerUpgradeCenterTab(
    workspace: BillingWorkspaceData,
    adminData: OwnerAdminState,
    isProcessing: Boolean,
    onCheckForUpdates: () -> Unit,
    onOneClickUpgrade: (ReleaseItem) -> Unit,
    onRollback: (UpgradeHistory) -> Unit,
    onUpdateControls: (autoUpdate: Boolean, maintenance: Boolean) -> Unit
) {
    val config = workspace.config
    val latestRelease = adminData.releaseCatalog.firstOrNull()
    val hasNewer = latestRelease != null && latestRelease.version != config.appVersion

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "BharatBill Upgrade Center",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Current: v${config.appVersion} • Latest: v${latestRelease?.version ?: config.appVersion}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        StatusPill(status = if (hasNewer) "PENDING" else "ACTIVE")
                    }

                    Button(
                        onClick = onCheckForUpdates,
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("check_updates_button")
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Check for Updates")
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Owner Update Controls", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Owner-Controlled Auto Updates", fontWeight = FontWeight.Medium)
                            Text(
                                "Stage verified patches automatically with pre-update cloud backup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.autoUpdateEnabled,
                            onCheckedChange = { onUpdateControls(it, config.maintenanceMode) },
                            modifier = Modifier.testTag("auto_update_switch")
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Maintenance Mode", fontWeight = FontWeight.Medium)
                            Text(
                                "Display maintenance notice banner across billing screens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = config.maintenanceMode,
                            onCheckedChange = { onUpdateControls(config.autoUpdateEnabled, it) },
                            modifier = Modifier.testTag("maintenance_mode_switch")
                        )
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Available Releases (${adminData.releaseCatalog.size})")
        }

        if (adminData.releaseCatalog.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.NewReleases,
                    title = "No Release Metadata",
                    subtitle = "Tap 'Check for Updates' above to fetch live release packages."
                )
            }
        } else {
            items(adminData.releaseCatalog, key = { it.id }) { release ->
                val isCurrent = release.version == config.appVersion
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "v${release.version}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                    fontWeight = FontWeight.Bold
                                )
                                StatusPill(status = if (release.securityUpdate) "SECURITY" else release.channel)
                                if (isCurrent) StatusPill(status = "ACTIVE")
                            }
                            Text(
                                BillingCalculator.formatShortDate(release.releaseEpochMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(release.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.NewReleases, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Features: ${release.features}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text("Bug Fixes: ${release.bugFixes}", style = MaterialTheme.typography.bodySmall)
                        }

                        if (!isCurrent) {
                            Button(
                                onClick = { onOneClickUpgrade(release) },
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("one_click_upgrade_${release.version}")
                            ) {
                                Icon(Icons.Rounded.SystemUpdate, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("One-Click Upgrade (Auto Backup & Install)")
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Update History & Rollback (${adminData.upgradeHistory.size})")
        }

        if (adminData.upgradeHistory.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.History,
                    title = "No Upgrade History Yet",
                    subtitle = "All upgrades automatically snapshot your cloud database for 1-tap rollback."
                )
            }
        } else {
            items(adminData.upgradeHistory, key = { it.id }) { hist ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "v${hist.fromVersion} -> v${hist.toVersion}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                                fontWeight = FontWeight.Bold
                            )
                            StatusPill(status = hist.status)
                        }
                        Text(hist.releaseNotes, style = MaterialTheme.typography.bodySmall)
                        Text(
                            BillingCalculator.formatDate(hist.upgradeEpochMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hist.status == "SUCCESS" && hist.toVersion == config.appVersion) {
                            OutlinedButton(
                                onClick = { onRollback(hist) },
                                enabled = !isProcessing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("rollback_upgrade_${hist.id}")
                            ) {
                                Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restore Previous Version (v${hist.fromVersion})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OwnerBackupLogsTab(
    adminData: OwnerAdminState,
    isProcessing: Boolean,
    onCreateBackup: (String) -> Unit,
    onRestoreBackup: (BackupRecord) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var backupLabel by remember { mutableStateOf("Manual Snapshot") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Cloud Backup & Restore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = backupLabel,
                        onValueChange = { backupLabel = it },
                        label = { Text("Backup Label") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onCreateBackup(backupLabel.ifBlank { "Manual Backup" }) },
                        enabled = !isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_cloud_backup_button")
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create Encrypted Cloud Snapshot")
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Saved Cloud Backups (${adminData.backups.size})")
        }

        if (adminData.backups.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Backup,
                    title = "No Backups Created",
                    subtitle = "Create a cloud snapshot above before bulk edits or upgrades."
                )
            }
        } else {
            items(adminData.backups, key = { it.id }) { bkp ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(bkp.label, fontWeight = FontWeight.Bold)
                            Text("v${bkp.appVersion}", style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily))
                        }
                        Text(
                            "${bkp.recordCount} records • ${BillingCalculator.formatDate(bkp.backupEpochMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { shareBackupJson(context, bkp, onShowMessage) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Export JSON")
                            }
                            Button(
                                onClick = { onRestoreBackup(bkp) },
                                enabled = !isProcessing,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Restore")
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Owner Activity Logs (${adminData.activityLogs.size})")
        }

        items(adminData.activityLogs.take(40), key = { it.id }) { log ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${log.action} (${log.actor})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(BillingCalculator.formatDate(log.logEpochMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(status = log.category)
                }
            }
        }
    }
}

private fun shareBackupJson(context: Context, backup: BackupRecord, onShowMessage: (String) -> Unit) {
    try {
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val file = File(dir, "BharatBill_Backup_${backup.id}.json")
        file.writeText(backup.payloadJson)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "BharatBill Cloud Backup: ${backup.label}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Backup JSON"))
    } catch (e: Exception) {
        onShowMessage(e.localizedMessage ?: "Failed to export backup")
    }
}

@Composable
fun ChangeOwnerPasswordDialog(
    isMandatoryInitial: Boolean,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onChangePassword: (current: String, next: String) -> Unit
) {
    var currentPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    val mismatch = confirmPw.isNotEmpty() && newPw != confirmPw

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isMandatoryInitial) "Change Initial Owner Password" else "Update Owner Password",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMandatoryInitial) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(
                            "For your store security, please replace the initial setup password with a strong personal password (min 6 characters).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = currentPw,
                    onValueChange = { currentPw = it },
                    label = { Text("Current Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pw_current_input")
                )
                OutlinedTextField(
                    value = newPw,
                    onValueChange = { newPw = it },
                    label = { Text("New Password (min 6 chars)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pw_new_input")
                )
                OutlinedTextField(
                    value = confirmPw,
                    onValueChange = { confirmPw = it },
                    label = { Text("Confirm New Password") },
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_pw_confirm_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onChangePassword(currentPw, newPw) },
                enabled = !isProcessing && currentPw.isNotBlank() && newPw.length >= 6 && newPw == confirmPw,
                modifier = Modifier.testTag("confirm_change_pw_button")
            ) {
                Text("Update Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}

@Composable
private fun StaffEditorDialog(
    initial: StaffUser?,
    onDismiss: () -> Unit,
    onSave: (StaffUser) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var contact by remember { mutableStateOf(initial?.emailOrPhone.orEmpty()) }
    var role by remember { mutableStateOf(initial?.role ?: "BILLER") }
    var canBill by remember { mutableStateOf(initial?.canManageBilling ?: true) }
    var canInv by remember { mutableStateOf(initial?.canManageInventory ?: false) }
    var canPurch by remember { mutableStateOf(initial?.canManagePurchases ?: false) }
    var canRep by remember { mutableStateOf(initial?.canViewReports ?: false) }
    var active by remember { mutableStateOf(initial?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Staff Member" else "Edit Staff & Permissions") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Phone or Email") }, modifier = Modifier.fillMaxWidth())
                Text("Role", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("BILLER", "MANAGER", "ACCOUNTANT").forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                    }
                }
                PermissionSwitchRow("Manage Billing & Invoices", canBill) { canBill = it }
                PermissionSwitchRow("Manage Inventory & Stock", canInv) { canInv = it }
                PermissionSwitchRow("Manage Purchases & Suppliers", canPurch) { canPurch = it }
                PermissionSwitchRow("View Reports & Profit/Loss", canRep) { canRep = it }
                PermissionSwitchRow("Account Active", active) { active = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        (initial ?: StaffUser()).copy(
                            name = name.trim(),
                            emailOrPhone = contact.trim(),
                            role = role,
                            canManageBilling = canBill,
                            canManageInventory = canInv,
                            canManagePurchases = canPurch,
                            canViewReports = canRep,
                            active = active
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Staff")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PermissionSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
