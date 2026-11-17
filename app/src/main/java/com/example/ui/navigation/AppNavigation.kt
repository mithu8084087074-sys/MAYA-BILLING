package com.example.ui.navigation

import android.app.Application
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.BillingRepository
import com.example.data.OwnerRepository
import com.example.data.PreferencesManager
import com.example.data.ThemeMode
import com.example.model.Invoice
import com.example.model.InvoiceLineItem
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BillingScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.OwnerAdminScreen
import com.example.ui.screens.PartiesScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.UpiPaymentDialog
import com.example.ui.screens.VoiceBillingSheet
import com.example.ui.screens.signOutUser
import com.example.util.AppLanguage
import com.example.util.AppStrings
import com.example.viewmodel.BillingWorkspaceData
import com.example.viewmodel.MainBillingViewModel
import com.example.viewmodel.OwnerAdminViewModel
import com.example.viewmodel.UiState
import com.example.viewmodel.VoiceBillingViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object RouteDashboard
@Serializable data object RouteBilling
@Serializable data object RouteInventory
@Serializable data object RouteParties
@Serializable data object RouteReports
@Serializable data object RouteOwner

private data class NavDestinationItem(
    val route: Any,
    val labelKey: String,
    val icon: ImageVector,
    val tag: String
)

private val TopNavItems = listOf(
    NavDestinationItem(RouteDashboard, "nav_dashboard", Icons.Rounded.Dashboard, "nav_dashboard"),
    NavDestinationItem(RouteBilling, "nav_billing", Icons.Rounded.ReceiptLong, "nav_billing"),
    NavDestinationItem(RouteInventory, "nav_inventory", Icons.Rounded.Inventory2, "nav_inventory"),
    NavDestinationItem(RouteParties, "nav_parties", Icons.Rounded.Group, "nav_parties"),
    NavDestinationItem(RouteReports, "nav_reports", Icons.Rounded.Assessment, "nav_reports"),
    NavDestinationItem(RouteOwner, "nav_owner", Icons.Rounded.AdminPanelSettings, "nav_owner")
)

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    prefs: PreferencesManager
) {
    val currentUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedAppShell(
                user = user,
                prefs = prefs
            )
        }
    }
}

@Composable
private fun AuthenticatedAppShell(
    user: FirebaseUser,
    prefs: PreferencesManager
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val language by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH)

    val mainVm: MainBillingViewModel = viewModel(
        key = "main_${user.uid}",
        factory = viewModelFactory {
            initializer {
                MainBillingViewModel(
                    billingRepo = BillingRepository(app),
                    ownerRepo = OwnerRepository(app),
                    currentUserId = user.uid,
                    defaultDisplayName = user.displayName ?: "BharatBill Enterprises"
                )
            }
        }
    )

    val ownerVm: OwnerAdminViewModel = viewModel(
        key = "owner_${user.uid}",
        factory = viewModelFactory {
            initializer {
                OwnerAdminViewModel(
                    ownerRepo = OwnerRepository(app),
                    prefs = prefs,
                    currentUserId = user.uid
                )
            }
        }
    )

    val voiceVm: VoiceBillingViewModel = viewModel(
        key = "voice_${user.uid}",
        factory = viewModelFactory {
            initializer { VoiceBillingViewModel() }
        }
    )

    val workspaceUiState by mainVm.workspaceState.collectAsStateWithLifecycle()
    val actionMessage by mainVm.actionMessage.collectAsStateWithLifecycle()

    val adminData by ownerVm.adminData.collectAsStateWithLifecycle()
    val isOwnerUnlocked by ownerVm.isOwnerUnlocked.collectAsStateWithLifecycle()
    val pending2Fa by ownerVm.pendingTwoFactorChallenge.collectAsStateWithLifecycle()
    val generatedOtp by ownerVm.generatedTwoFactorOtp.collectAsStateWithLifecycle()
    val ownerStatusMsg by ownerVm.statusMessage.collectAsStateWithLifecycle()
    val ownerBusy by ownerVm.isProcessing.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            mainVm.clearMessage()
        }
    }
    LaunchedEffect(ownerStatusMsg) {
        val msg = ownerStatusMsg
        if (!msg.isNullOrBlank() && isOwnerUnlocked) {
            snackbarHostState.showSnackbar(msg)
            ownerVm.clearStatusMessage()
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    var showVoiceSheet by remember { mutableStateOf(false) }
    var openBillBuilderOnNav by remember { mutableStateOf(false) }
    var voicePrefillItems by remember { mutableStateOf<List<InvoiceLineItem>?>(null) }
    var voicePrefillCustomer by remember { mutableStateOf<String?>(null) }
    var voicePrefillDiscount by remember { mutableStateOf<Double?>(null) }
    var pendingUpiInvoiceDialog by remember { mutableStateOf<Invoice?>(null) }

    val navigateTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val isDarkResolved = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val workspaceOrNull = (workspaceUiState as? UiState.Success)?.data

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                val cfg = workspaceOrNull?.config
                val showFab = cfg?.voiceBillingEnabled == true &&
                    (currentDest?.hasRoute<RouteDashboard>() == true || currentDest?.hasRoute<RouteBilling>() == true)
                if (showFab) {
                    FloatingActionButton(
                        onClick = { showVoiceSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.testTag("fab_voice_billing")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Voice Billing"
                        )
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        TopNavItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = AppStrings.get(item.labelKey, language)
                                    )
                                },
                                label = {
                                    Text(
                                        text = AppStrings.get(item.labelKey, language),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        Spacer(Modifier.height(12.dp))
                        TopNavItems.forEach { item ->
                            val selected = currentDest?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = AppStrings.get(item.labelKey, language)
                                    )
                                },
                                label = {
                                    Text(
                                        text = AppStrings.get(item.labelKey, language),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag(item.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = workspaceUiState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text("Syncing BharatBill Cloud Workspace...")
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        signOutUser(credentialManager, scope)
                                    }
                                ) {
                                    Text("Switch Google Account")
                                }
                            }
                        }

                        is UiState.Success -> {
                            val workspace = state.data
                            NavHost(
                                navController = navController,
                                startDestination = RouteDashboard,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable<RouteDashboard> {
                                    DashboardScreen(
                                        workspace = workspace,
                                        language = language,
                                        themeMode = if (isDarkResolved) ThemeMode.DARK else ThemeMode.LIGHT,
                                        onToggleLanguage = {
                                            val next = if (language == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH
                                            scope.launch { prefs.setLanguage(next) }
                                        },
                                        onCycleTheme = {
                                            val next = if (isDarkResolved) ThemeMode.LIGHT else ThemeMode.DARK
                                            scope.launch { prefs.setThemeMode(next) }
                                        },
                                        onOpenNewBill = {
                                            openBillBuilderOnNav = true
                                            navigateTopLevel(RouteBilling)
                                        },
                                        onOpenVoiceBill = { showVoiceSheet = true },
                                        onOpenInventory = { navigateTopLevel(RouteInventory) },
                                        onOpenParties = { navigateTopLevel(RouteParties) },
                                        onOpenOwnerAdmin = { navigateTopLevel(RouteOwner) },
                                        onOpenInvoiceDetail = { inv ->
                                            pendingUpiInvoiceDialog = inv
                                        },
                                        onOpenPendingUpi = {
                                            val pendingPay = workspace.payments.firstOrNull { it.verificationStatus == "PENDING" }
                                            val matchingInv = pendingPay?.let { p -> workspace.invoices.firstOrNull { it.id == p.invoiceId } }
                                                ?: workspace.invoices.firstOrNull { it.balanceDue > 0.0 }
                                            if (matchingInv != null) {
                                                pendingUpiInvoiceDialog = matchingInv
                                            } else {
                                                navigateTopLevel(RouteBilling)
                                            }
                                        }
                                    )
                                }

                                composable<RouteBilling> {
                                    BillingScreen(
                                        workspace = workspace,
                                        initialBuilderOpen = openBillBuilderOnNav || voicePrefillItems != null,
                                        voicePrefillItems = voicePrefillItems,
                                        voicePrefillCustomer = voicePrefillCustomer,
                                        voicePrefillDiscount = voicePrefillDiscount,
                                        onConsumeBuilderTrigger = {
                                            openBillBuilderOnNav = false
                                            voicePrefillItems = null
                                            voicePrefillCustomer = null
                                            voicePrefillDiscount = null
                                        },
                                        onOpenVoiceSheet = { showVoiceSheet = true },
                                        onCreateInvoice = { inv, upiApp, onCreated ->
                                            mainVm.createInvoice(inv, workspace, upiApp, onCreated)
                                        },
                                        onUpdateInvoice = { inv, onDone ->
                                            mainVm.updateInvoice(inv, onDone)
                                        },
                                        onDeleteInvoice = { inv ->
                                            mainVm.deleteInvoice(inv)
                                        },
                                        onCreatePendingUpi = { inv, amt, appName, upiId, notes ->
                                            mainVm.createPendingUpiPayment(inv, amt, appName, upiId, notes)
                                        },
                                        onVerifyPayment = { pay, inv, cust, utr, status, notes ->
                                            mainVm.verifyPayment(pay, inv, cust, utr, status, notes)
                                        },
                                        onRecordDirectPayment = { inv, cust, amt, method, ref ->
                                            mainVm.recordDirectPayment(inv, cust, amt, method, ref)
                                        },
                                        onShowMessage = { msg -> mainVm.showMessage(msg) }
                                    )
                                }

                                composable<RouteInventory> {
                                    InventoryScreen(
                                        workspace = workspace,
                                        onSaveProduct = { prod, onDone -> mainVm.saveProduct(prod, onDone) },
                                        onDeleteProduct = { prod -> mainVm.deleteProduct(prod) },
                                        onAdjustStock = { prod, delta, reason -> mainVm.adjustStock(prod, delta, reason) }
                                    )
                                }

                                composable<RouteParties> {
                                    PartiesScreen(
                                        workspace = workspace,
                                        onSaveCustomer = { cust, onDone -> mainVm.saveCustomer(cust) { onDone() } },
                                        onDeleteCustomer = { cust -> mainVm.deleteCustomer(cust) },
                                        onSaveSupplier = { sup, onDone -> mainVm.saveSupplier(sup, onDone) },
                                        onCreatePurchase = { purch, inv, sup, onDone ->
                                            mainVm.createPurchase(purch, inv, sup, onDone)
                                        },
                                        onPaySupplierPurchase = { purch, sup, amt ->
                                            mainVm.paySupplierPurchase(purch, sup, amt)
                                        },
                                        onSaveExpense = { exp, onDone -> mainVm.saveExpense(exp, onDone) },
                                        onDeleteExpense = { exp -> mainVm.deleteExpense(exp) }
                                    )
                                }

                                composable<RouteReports> {
                                    ReportsScreen(workspace = workspace)
                                }

                                composable<RouteOwner> {
                                    OwnerAdminScreen(
                                        workspace = workspace,
                                        adminData = adminData,
                                        userEmail = user.email.orEmpty(),
                                        isUnlocked = isOwnerUnlocked,
                                        pendingTwoFactor = pending2Fa,
                                        generatedOtp = generatedOtp,
                                        statusMessage = ownerStatusMsg,
                                        isProcessing = ownerBusy,
                                        onAttemptLogin = { pw, cfg -> ownerVm.attemptOwnerLogin(pw, cfg) },
                                        onVerifyTwoFactor = { code, cfg -> ownerVm.verifyTwoFactorCode(code, cfg) },
                                        onLockSession = { ownerVm.lockOwnerSession() },
                                        onRecoverPassword = { pin, newPw, onDone ->
                                            ownerVm.recoverPasswordWithPin(pin, newPw, workspace.config, onDone)
                                        },
                                        onChangePassword = { curr, next, onDone ->
                                            ownerVm.changeOwnerPassword(curr, next, workspace.config, onDone)
                                        },
                                        onSaveSecurityOptions = { pin, hint, twoFa ->
                                            ownerVm.saveSecurityOptions(pin, hint, twoFa)
                                        },
                                        onSaveBusinessConfig = { cfg -> ownerVm.saveBusinessConfig(cfg) },
                                        onSaveStaff = { staff, onDone -> ownerVm.saveStaff(staff, onDone) },
                                        onDeleteStaff = { staff -> ownerVm.deleteStaff(staff) },
                                        onCreateBackup = { label ->
                                            ownerVm.createBackup(
                                                label = label,
                                                config = workspace.config,
                                                products = workspace.products,
                                                customers = workspace.customers,
                                                suppliers = workspace.suppliers,
                                                expenses = workspace.expenses
                                            )
                                        },
                                        onRestoreBackup = { bkp -> ownerVm.restoreBackup(bkp) },
                                        onCheckForUpdates = { ownerVm.checkForUpdates() },
                                        onOneClickUpgrade = { rel ->
                                            ownerVm.oneClickUpgrade(
                                                target = rel,
                                                config = workspace.config,
                                                products = workspace.products,
                                                customers = workspace.customers,
                                                suppliers = workspace.suppliers,
                                                expenses = workspace.expenses
                                            )
                                        },
                                        onRollbackUpgrade = { hist ->
                                            ownerVm.rollbackUpgrade(hist, adminData.backups)
                                        },
                                        onUpdateUpgradeControls = { autoUpd, maint ->
                                            ownerVm.updateUpgradeControls(autoUpd, maint)
                                        },
                                        onSignOutGoogle = {
                                            ownerVm.lockOwnerSession()
                                            signOutUser(credentialManager, scope)
                                        },
                                        onShowMessage = { msg -> mainVm.showMessage(msg) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Voice Billing Modal Sheet
    if (showVoiceSheet && workspaceOrNull != null) {
        VoiceBillingSheet(
            voiceVm = voiceVm,
            inventory = workspaceOrNull.products,
            isGstEnabled = workspaceOrNull.config.gstBillingEnabled,
            taxMode = "INTRA_STATE",
            initialLanguage = language,
            onDismiss = { showVoiceSheet = false },
            onConfirmToInvoice = { items, customerName, discount ->
                showVoiceSheet = false
                voicePrefillItems = items
                voicePrefillCustomer = customerName
                voicePrefillDiscount = discount
                openBillBuilderOnNav = true
                navigateTopLevel(RouteBilling)
            }
        )
    }

    // Quick UPI Payment / Verification Dialog launched from Dashboard
    val dialogInv = pendingUpiInvoiceDialog
    if (dialogInv != null && workspaceOrNull != null) {
        val latestInv = workspaceOrNull.invoices.firstOrNull { it.id == dialogInv.id } ?: dialogInv
        val cust = workspaceOrNull.customers.firstOrNull { it.id == latestInv.customerId }
        UpiPaymentDialog(
            invoice = latestInv,
            business = workspaceOrNull.config,
            customer = cust,
            existingPayments = workspaceOrNull.payments,
            onDismiss = { pendingUpiInvoiceDialog = null },
            onCreatePendingUpi = { amt, appName, notes ->
                mainVm.createPendingUpiPayment(latestInv, amt, appName, workspaceOrNull.config.upiId, notes)
            },
            onVerifyPayment = { pay, utr, status, notes ->
                mainVm.verifyPayment(pay, latestInv, cust, utr, status, notes)
            },
            onShowMessage = { msg -> mainVm.showMessage(msg) }
        )
    }
}
