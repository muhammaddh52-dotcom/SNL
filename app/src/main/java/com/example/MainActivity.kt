package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.model.UiLanguage
import com.example.data.repository.FirestorePersistenceConfig
import com.example.data.repository.SnlRepository
import com.example.notifications.SnlFcmNotificationManager
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricSecurityGateOverlay
import com.example.security.configureAppCheck
import com.example.ui.screens.AdminAiPriceListOcrScreen
import com.example.ui.screens.AdminExcelAndSheetsSyncScreen
import com.example.ui.screens.AdminInventoryReportsAndSettingsScreen
import com.example.ui.screens.AdminKpiDashboardScreen
import com.example.ui.screens.AdminOrdersManagementScreen
import com.example.ui.screens.AdminProductsAndPricingScreen
import com.example.ui.screens.AdminUsersAndApprovalsScreen
import com.example.ui.screens.CartScreen
import com.example.ui.screens.CatalogScreen
import com.example.ui.screens.DistributorDashboardScreen
import com.example.ui.screens.OrderHistoryScreen
import com.example.ui.screens.OrderTrackingScreen
import com.example.ui.screens.PendingApprovalScreen
import com.example.ui.screens.ProfileOnboardingScreen
import com.example.ui.screens.SnlAuthScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.SNLLubricantsTheme
import com.example.ui.theme.SnlNavy
import com.example.ui.theme.SnlRed
import com.example.ui.theme.SnlYellow
import com.example.ui.viewmodel.CartViewModel
import com.example.ui.viewmodel.SnlViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore

/**
 * Navigation routes for Jetpack Navigation Compose (`androidx.navigation:navigation-compose`)
 * handling view switching between login, onboarding, home, catalog, cart, customer orders,
 * distributor analytics dashboard, and admin dashboards.
 */
object SnlNavRoutes {
    const val LOGIN = "login"
    const val ONBOARDING = "onboarding"
    const val PENDING_APPROVAL = "pending_approval"
    const val HOME = "home"
    const val CATALOGUE = "CATALOGUE"
    const val CART = "CART"
    const val CUSTOMER_ORDERS = "CUSTOMER_ORDERS"
    const val ORDER_TRACKING = "ORDER_TRACKING"
    const val DISTRIBUTOR_DASHBOARD = "DISTRIBUTOR_DASHBOARD"
    const val ADMIN_DASHBOARD = "ADMIN_DASHBOARD"
    const val ADMIN_ORDERS = "ADMIN_ORDERS"
    const val ADMIN_AI_OCR = "ADMIN_AI_OCR"
    const val ADMIN_EXCEL_SYNC = "ADMIN_EXCEL_SYNC"
    const val ADMIN_PRODUCTS = "ADMIN_PRODUCTS"
    const val ADMIN_USERS = "ADMIN_USERS"
    const val ADMIN_INVENTORY = "ADMIN_INVENTORY"
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must be the very first statement after super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            SnlRootApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        configureAppCheck(this, intent)
    }
}

@Composable
fun SnlRootApp() {
    val auth = remember { Firebase.auth }
    val currentUser by remember { auth.authStateFlow() }.collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val rootNavController = rememberNavController()

    // Local language state while unauthenticated (defaults to Urdu RTL per Section 21)
    var unauthLanguage by rememberSaveable { mutableStateOf(UiLanguage.URDU) }

    val user = currentUser
    LaunchedEffect(user?.uid) {
        val targetRoute = if (user == null) SnlNavRoutes.LOGIN else SnlNavRoutes.HOME
        if (rootNavController.currentDestination?.route != targetRoute) {
            rootNavController.navigate(targetRoute) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = if (user == null) SnlNavRoutes.LOGIN else SnlNavRoutes.HOME
    ) {
        composable(SnlNavRoutes.LOGIN) {
            val systemDark = isSystemInDarkTheme()
            var unauthThemeMode by rememberSaveable { mutableStateOf(AppThemeMode.SYSTEM) }
            var unauthDynamicColor by rememberSaveable { mutableStateOf(true) }
            val effectiveUnauthDark = when (unauthThemeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }
            SNLLubricantsTheme(
                darkTheme = effectiveUnauthDark,
                themeMode = unauthThemeMode,
                dynamicColor = unauthDynamicColor
            ) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (unauthLanguage.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    SnlAuthScreen(
                        language = unauthLanguage,
                        onToggleLanguage = {
                            unauthLanguage = if (unauthLanguage == UiLanguage.URDU) UiLanguage.ENGLISH else UiLanguage.URDU
                        },
                        onAuthSuccess = {
                            rootNavController.navigate(SnlNavRoutes.HOME) {
                                popUpTo(SnlNavRoutes.LOGIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }

        composable(SnlNavRoutes.HOME) {
            val activeUser = currentUser
            if (activeUser != null) {
                val dbId = stringResource(R.string.firestore_database_id)
                val vm: SnlViewModel = viewModel(
                    key = activeUser.uid,
                    factory = viewModelFactory {
                        initializer {
                            val firestore = FirestorePersistenceConfig.getConfiguredFirestore(dbId)
                            SnlViewModel(
                                repository = SnlRepository(firestore),
                                currentUserId = activeUser.uid,
                                currentUserEmail = activeUser.email.orEmpty()
                            )
                        }
                    }
                )
                AuthenticatedSnlPlatform(
                    viewModel = vm,
                    currentEmail = activeUser.email.orEmpty(),
                    currentDisplayName = activeUser.displayName.orEmpty()
                )
            }
        }
    }
}

private fun NavHostController.navigateToSection(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AuthenticatedSnlPlatform(
    viewModel: SnlViewModel,
    currentEmail: String,
    currentDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val language by viewModel.language.collectAsStateWithLifecycle()
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColor.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val effectiveDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val priceGroups by viewModel.priceGroups.collectAsStateWithLifecycle()
    val groupPrices by viewModel.groupPrices.collectAsStateWithLifecycle()
    val vmCartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val cartViewModel: CartViewModel = viewModel()
    val persistedCartItems by cartViewModel.cartItems.collectAsStateWithLifecycle()
    val cartItems = if (persistedCartItems.isNotEmpty()) persistedCartItems else vmCartItems
    val favouriteSkus by viewModel.favouriteSkus.collectAsStateWithLifecycle()
    val customerOrders by viewModel.customerOrders.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val allOrdersForAdmin by viewModel.allOrdersForAdmin.collectAsStateWithLifecycle()
    val allUsersForAdmin by viewModel.allUsersForAdmin.collectAsStateWithLifecycle()
    val inventoryMovements by viewModel.inventoryMovements.collectAsStateWithLifecycle()
    val priceImports by viewModel.priceImports.collectAsStateWithLifecycle()
    val companySettings by viewModel.companySettings.collectAsStateWithLifecycle()
    val draftExtractedRows by viewModel.draftExtractedRows.collectAsStateWithLifecycle()
    val draftTargetPriceGroup by viewModel.draftTargetPriceGroup.collectAsStateWithLifecycle()
    val aiExtractionStatus by viewModel.aiExtractionStatus.collectAsStateWithLifecycle()
    val excelPreview by viewModel.excelPreview.collectAsStateWithLifecycle()

    val dbId = stringResource(R.string.firestore_database_id)
    val isOnline by remember(context) {
        FirestorePersistenceConfig.observeOnlineStatus(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val isSimulatedOffline by FirestorePersistenceConfig.isSimulatedOffline.collectAsStateWithLifecycle()
    val isServingFromCache by FirestorePersistenceConfig.isServingFromOfflineCache.collectAsStateWithLifecycle()

    val isFcmEnabled by SnlFcmNotificationManager.isFcmEnabled.collectAsStateWithLifecycle()
    val latestFcmBannerEvent by SnlFcmNotificationManager.latestBannerPushEvent.collectAsStateWithLifecycle()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Initialize FCM registration token & topic subscriptions for the distributor/customer
    LaunchedEffect(userProfile?.uid, userProfile?.role, currentEmail) {
        val uid = userProfile?.uid?.ifBlank { currentEmail } ?: currentEmail
        val role = userProfile?.role?.ifBlank { "DISTRIBUTOR" } ?: "DISTRIBUTOR"
        SnlFcmNotificationManager.initializeFcmForUser(context, uid, role)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !SnlFcmNotificationManager.hasPostNotificationsPermission(context)
        ) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Observe customer orders for live transitions to 'Dispatched' or 'Delivered' and send FCM push notification
    LaunchedEffect(customerOrders, userProfile?.uid) {
        val uid = userProfile?.uid.orEmpty()
        if (uid.isNotBlank() && customerOrders.isNotEmpty()) {
            SnlFcmNotificationManager.onCustomerOrdersSnapshotUpdated(context, uid, customerOrders)
        }
    }

    // Observe Firestore notifications for FCM_ORDER_DISPATCHED / FCM_ORDER_DELIVERED events
    LaunchedEffect(notifications, userProfile?.uid) {
        val uid = userProfile?.uid.orEmpty()
        if (uid.isNotBlank() && notifications.isNotEmpty()) {
            SnlFcmNotificationManager.onFirestoreNotificationsUpdated(context, uid, notifications)
        }
    }

    // Bind local cart persistence immediately so adding/editing items works seamlessly offline
    LaunchedEffect(userProfile?.uid, userProfile?.priceGroupId, currentEmail) {
        cartViewModel.bindLocalPersistence(
            context = context,
            userId = userProfile?.uid?.ifBlank { currentEmail } ?: currentEmail.ifBlank { "default_user" },
            priceGroupId = userProfile?.priceGroupId?.ifBlank { "DISTRIBUTOR" } ?: "DISTRIBUTOR"
        )
    }

    var isBiometricLocked by rememberSaveable { mutableStateOf(false) }
    var biometricQuickAccessEnabled by remember {
        mutableStateOf(BiometricAuthHelper.isBiometricQuickAccessEnabled(context))
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(statusBanner) {
        statusBanner?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    // Automatically bootstrap Super Admin if signed in as muhammad.dh52@gmail.com and no profile exists yet
    LaunchedEffect(userProfile, currentEmail) {
        if (userProfile == null && currentEmail.equals("muhammad.dh52@gmail.com", ignoreCase = true)) {
            viewModel.initializeAsSuperAdmin(
                fullName = currentDisplayName.ifBlank { "Saifi National Lubricants HQ" },
                email = currentEmail,
                autoImportAttachedList = true
            )
        }
    }

    SNLLubricantsTheme(
        darkTheme = effectiveDarkTheme,
        themeMode = themeMode,
        dynamicColor = useDynamicColor
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        ) {
            val profile = userProfile
            if (isBiometricLocked) {
                BiometricSecurityGateOverlay(
                    language = language,
                    userEmail = currentEmail,
                    businessName = profile?.businessName.orEmpty(),
                    onUnlocked = { isBiometricLocked = false },
                    onSignOutFallback = {
                        isBiometricLocked = false
                        signOut(credentialManager, scope) {}
                    }
                )
            } else {
            when {
                profile == null -> {
                    ProfileOnboardingScreen(
                        currentEmail = currentEmail,
                        currentDisplayName = currentDisplayName,
                        language = language,
                        isBusy = isBusy,
                        onInitializeSuperAdmin = {
                            viewModel.initializeAsSuperAdmin(
                                fullName = currentDisplayName.ifBlank { "SNL Super Admin" },
                                email = currentEmail,
                                autoImportAttachedList = true
                            )
                        },
                        onSubmitSelfRegistration = { fullName, businessName, phone, email, city, address, role ->
                            viewModel.submitSelfRegistration(fullName, businessName, phone, email, city, address, role)
                        },
                        onSignOut = {
                            signOut(credentialManager, scope) {}
                        }
                    )
                }

                profile.accountStatus == "PENDING_APPROVAL" && profile.role !in setOf("SUPER_ADMIN", "STAFF") -> {
                    PendingApprovalScreen(
                        profile = profile,
                        language = language,
                        onPromoteToSuperAdminForTesting = {
                            viewModel.initializeAsSuperAdmin(
                                fullName = profile.fullName.ifBlank { "SNL Super Admin" },
                                email = profile.email.ifBlank { currentEmail },
                                autoImportAttachedList = true
                            )
                        },
                        onSignOut = {
                            signOut(credentialManager, scope) {}
                        }
                    )
                }

                else -> {
                    val isAdminOrStaff = profile.role in setOf("SUPER_ADMIN", "STAFF")
                    val defaultHomeRoute = if (isAdminOrStaff) {
                        SnlNavRoutes.ADMIN_DASHBOARD
                    } else {
                        SnlNavRoutes.CATALOGUE
                    }
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: defaultHomeRoute

                    var showRoleSwitcherBar by rememberSaveable { mutableStateOf(false) }

                    // BackHandler for secondary screens returning to primary home route
                    if (currentRoute != defaultHomeRoute) {
                        BackHandler {
                            navController.navigateToSection(defaultHomeRoute)
                        }
                    }

                    val pendingOrdersCount = remember(allOrdersForAdmin) {
                        allOrdersForAdmin.count {
                            it.orderStatus == "PENDING_ADMIN_APPROVAL" || it.orderStatus == "PENDING_APPROVAL"
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            Column {
                                Surface(
                                    color = SnlNavy,
                                    contentColor = Color.White,
                                    shadowElevation = 4.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Image(
                                                painter = painterResource(id = R.drawable.img_snl_logo),
                                                contentDescription = "SNL Original Logo",
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White)
                                                    .padding(3.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "SNL LUBRICANTS",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = SnlYellow,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "${profile.businessName} • ${profile.role} (${profile.priceGroupId})",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.88f),
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AssistChip(
                                                onClick = { viewModel.toggleLanguage() },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Language,
                                                        contentDescription = "Toggle Language",
                                                        tint = SnlYellow,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = if (language == UiLanguage.URDU) "EN" else "اردو",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                },
                                                modifier = Modifier.testTag("toggle_language_button")
                                            )

                                            IconButton(
                                                onClick = { showRoleSwitcherBar = !showRoleSwitcherBar },
                                                modifier = Modifier.testTag("toggle_role_switcher_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.AdminPanelSettings,
                                                    contentDescription = "Switch Role / Tier",
                                                    tint = SnlYellow
                                                )
                                            }

                                            IconButton(
                                                onClick = { isBiometricLocked = true },
                                                modifier = Modifier.testTag("biometric_lock_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Fingerprint,
                                                    contentDescription = "Biometric Lock Platform (Fingerprint / Face)",
                                                    tint = SnlYellow
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleDynamicColor() },
                                                modifier = Modifier.testTag("toggle_dynamic_color_button")
                                            ) {
                                                Icon(
                                                    Icons.Default.Palette,
                                                    contentDescription = "Toggle Material3 Dynamic Color",
                                                    tint = if (useDynamicColor) SnlYellow else Color.White.copy(alpha = 0.7f)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleDarkMode(systemDark) },
                                                modifier = Modifier.testTag("toggle_dark_mode_button")
                                            ) {
                                                Icon(
                                                    if (effectiveDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                                    contentDescription = "Dark Mode Toggle (${themeMode.name})",
                                                    tint = Color.White
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    signOut(credentialManager, scope) {}
                                                },
                                                modifier = Modifier.testTag("header_sign_out_button")
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Logout,
                                                    contentDescription = "Sign Out",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick Role, Price Group & Theme Mode Switcher bar
                                if (showRoleSwitcherBar) {
                                    Surface(color = SnlRed.copy(alpha = 0.12f)) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Active Role / Price Group:",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                FilterChip(
                                                    selected = profile.role == "SUPER_ADMIN",
                                                    onClick = {
                                                        viewModel.switchCurrentUserRoleForTesting("SUPER_ADMIN", "DISTRIBUTOR")
                                                        navController.navigateToSection(SnlNavRoutes.ADMIN_DASHBOARD)
                                                    },
                                                    label = { Text("Super Admin") }
                                                )
                                                FilterChip(
                                                    selected = profile.role == "STAFF",
                                                    onClick = {
                                                        viewModel.switchCurrentUserRoleForTesting("STAFF", "DISTRIBUTOR")
                                                        navController.navigateToSection(SnlNavRoutes.ADMIN_ORDERS)
                                                    },
                                                    label = { Text("Staff") }
                                                )
                                                FilterChip(
                                                    selected = profile.role == "DISTRIBUTOR",
                                                    onClick = {
                                                        viewModel.switchCurrentUserRoleForTesting("DISTRIBUTOR", "DISTRIBUTOR")
                                                        navController.navigateToSection(SnlNavRoutes.CATALOGUE)
                                                    },
                                                    label = { Text("Distributor (Sole Dist Price)") }
                                                )
                                                FilterChip(
                                                    selected = profile.role == "DEALER" && profile.priceGroupId == "STANDARD_DEALER",
                                                    onClick = {
                                                        viewModel.switchCurrentUserRoleForTesting("DEALER", "STANDARD_DEALER")
                                                        navController.navigateToSection(SnlNavRoutes.CATALOGUE)
                                                    },
                                                    label = { Text("Standard Dealer") }
                                                )
                                                FilterChip(
                                                    selected = profile.role == "DEALER" && profile.priceGroupId == "PREMIUM_DEALER",
                                                    onClick = {
                                                        viewModel.switchCurrentUserRoleForTesting("DEALER", "PREMIUM_DEALER")
                                                        navController.navigateToSection(SnlNavRoutes.CATALOGUE)
                                                    },
                                                    label = { Text("Premium Dealer") }
                                                )
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Theme & Biometric Security:",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                FilterChip(
                                                    selected = themeMode == AppThemeMode.SYSTEM,
                                                    onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                                                    label = { Text("System Auto") },
                                                    modifier = Modifier.testTag("theme_mode_SYSTEM")
                                                )
                                                FilterChip(
                                                    selected = themeMode == AppThemeMode.LIGHT,
                                                    onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                                                    label = { Text("Light") },
                                                    modifier = Modifier.testTag("theme_mode_LIGHT")
                                                )
                                                FilterChip(
                                                    selected = themeMode == AppThemeMode.DARK,
                                                    onClick = { viewModel.setThemeMode(AppThemeMode.DARK) },
                                                    label = { Text("Dark") },
                                                    modifier = Modifier.testTag("theme_mode_DARK")
                                                )
                                                FilterChip(
                                                    selected = useDynamicColor,
                                                    onClick = { viewModel.toggleDynamicColor() },
                                                    label = { Text(if (useDynamicColor) "Dynamic Color: ON" else "Dynamic Color: OFF") },
                                                    modifier = Modifier.testTag("toggle_dynamic_color_chip")
                                                )
                                                FilterChip(
                                                    selected = biometricQuickAccessEnabled,
                                                    onClick = {
                                                        val updated = !biometricQuickAccessEnabled
                                                        biometricQuickAccessEnabled = updated
                                                        BiometricAuthHelper.setBiometricQuickAccessEnabled(context, updated)
                                                    },
                                                    label = {
                                                        Text(
                                                            if (biometricQuickAccessEnabled) {
                                                                "Biometric Auth: ENABLED"
                                                            } else {
                                                                "Biometric Auth: OFF"
                                                            }
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("biometric_quick_access_chip")
                                                )
                                                FilterChip(
                                                    selected = isSimulatedOffline,
                                                    onClick = {
                                                        val firestore = FirestorePersistenceConfig.getConfiguredFirestore(dbId)
                                                        FirestorePersistenceConfig.setFirestoreOfflineMode(
                                                            firestore,
                                                            !isSimulatedOffline
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            if (isSimulatedOffline) {
                                                                "Firestore Mode: OFFLINE CACHE"
                                                            } else {
                                                                "Firestore Mode: ONLINE + CACHE"
                                                            }
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("toggle_offline_persistence_chip")
                                                )
                                                FilterChip(
                                                    selected = isFcmEnabled,
                                                    onClick = {
                                                        SnlFcmNotificationManager.setFcmAlertsEnabled(
                                                            context,
                                                            !isFcmEnabled
                                                        )
                                                    },
                                                    label = {
                                                        Text(
                                                            if (isFcmEnabled) {
                                                                "FCM Push (Dispatched/Delivered): ON"
                                                            } else {
                                                                "FCM Push: OFF"
                                                            }
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("toggle_fcm_push_chip")
                                                )
                                            }
                                        }
                                    }
                                }

                                // Live FCM Push Notification Banner for 'Dispatched' or 'Delivered' order updates
                                latestFcmBannerEvent?.let { pushEvent ->
                                    Surface(
                                        color = SnlNavy,
                                        contentColor = Color.White,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("fcm_live_push_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.NotificationsActive,
                                                    contentDescription = "FCM Push Notification",
                                                    tint = SnlYellow,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = pushEvent.title,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = SnlYellow,
                                                        fontWeight = FontWeight.ExtraBold
                                                    )
                                                    Text(
                                                        text = pushEvent.body,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color.White.copy(alpha = 0.92f),
                                                        maxLines = 2
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                AssistChip(
                                                    onClick = {
                                                        SnlFcmNotificationManager.dismissBannerPushEvent()
                                                        navController.navigateToSection(SnlNavRoutes.ORDER_TRACKING)
                                                    },
                                                    label = {
                                                        Text(
                                                            text = if (language == UiLanguage.URDU) "ٹریک کریں" else "Track",
                                                            color = SnlYellow,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("fcm_banner_track_button")
                                                )
                                                AssistChip(
                                                    onClick = {
                                                        SnlFcmNotificationManager.dismissBannerPushEvent()
                                                    },
                                                    label = {
                                                        Text(
                                                            text = "×",
                                                            color = Color.White,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("fcm_banner_dismiss_button")
                                                )
                                            }
                                        }
                                    }
                                }

                                // Offline Persistence Banner when device is offline or in offline cache mode
                                if (!isOnline || isSimulatedOffline || isServingFromCache) {
                                    Surface(
                                        color = SnlYellow.copy(alpha = 0.22f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("offline_persistence_banner")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CloudOff,
                                                    contentDescription = "Offline Mode Active",
                                                    tint = SnlRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = if (language == UiLanguage.URDU) {
                                                        "آف لائن موڈ فعال: پروڈکٹ کیٹلاگ اور شاپنگ کارٹ مکمل طور پر قابلِ رسائی اور قابلِ ترمیم ہیں"
                                                    } else {
                                                        "Offline Persistence Active: Product Catalog & Cart are accessible and editable offline"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (isSimulatedOffline) {
                                                AssistChip(
                                                    onClick = {
                                                        val firestore = FirestorePersistenceConfig.getConfiguredFirestore(dbId)
                                                        FirestorePersistenceConfig.setFirestoreOfflineMode(firestore, false)
                                                    },
                                                    label = {
                                                        Text(
                                                            text = if (language == UiLanguage.URDU) "آن لائن کریں" else "Go Online",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    },
                                                    modifier = Modifier.testTag("banner_go_online_button")
                                                )
                                            }
                                        }
                                    }
                                }

                                // Secondary Admin Navigation Strip when in Admin mode
                                if (isAdminOrStaff) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(horizontal = 12.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                SnlNavRoutes.ADMIN_DASHBOARD to "KPI Dashboard",
                                                SnlNavRoutes.ADMIN_ORDERS to "Orders ($pendingOrdersCount)",
                                                SnlNavRoutes.ADMIN_AI_OCR to "AI Price List OCR",
                                                SnlNavRoutes.ADMIN_EXCEL_SYNC to "Excel & Sheets Sync",
                                                SnlNavRoutes.ADMIN_PRODUCTS to "Products (${products.size})",
                                                SnlNavRoutes.ADMIN_USERS to "Accounts (${allUsersForAdmin.size})",
                                                SnlNavRoutes.ADMIN_INVENTORY to "Inventory & Reports",
                                                SnlNavRoutes.CATALOGUE to "Customer Catalogue",
                                                SnlNavRoutes.DISTRIBUTOR_DASHBOARD to "Distributor Analytics",
                                                SnlNavRoutes.CART to "Cart (${cartItems.size})",
                                                SnlNavRoutes.CUSTOMER_ORDERS to "My Orders"
                                            ).forEach { (routeKey, label) ->
                                                FilterChip(
                                                    selected = currentRoute == routeKey,
                                                    onClick = { navController.navigateToSection(routeKey) },
                                                    label = { Text(label) },
                                                    modifier = Modifier.testTag("nav_chip_$routeKey")
                                                )
                                            }
                                        }
                                    }
                                }

                                if (isBusy) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = SnlRed
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            NavigationBar {
                                if (isAdminOrStaff) {
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.ADMIN_DASHBOARD,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.ADMIN_DASHBOARD) },
                                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                        label = { Text("HQ") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.ADMIN_ORDERS,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.ADMIN_ORDERS) },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (pendingOrdersCount > 0) {
                                                        Badge { Text("$pendingOrdersCount") }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Orders")
                                            }
                                        },
                                        label = { Text("Orders") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.ADMIN_AI_OCR,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.ADMIN_AI_OCR) },
                                        icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "AI OCR") },
                                        label = { Text("AI OCR") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.ADMIN_EXCEL_SYNC,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.ADMIN_EXCEL_SYNC) },
                                        icon = { Icon(Icons.Default.TableView, contentDescription = "Excel") },
                                        label = { Text("Excel") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.CATALOGUE,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.CATALOGUE) },
                                        icon = { Icon(Icons.Default.Storefront, contentDescription = "Catalogue") },
                                        label = { Text("Store") }
                                    )
                                } else {
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.CATALOGUE,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.CATALOGUE) },
                                        icon = { Icon(Icons.Default.Storefront, contentDescription = "Catalogue") },
                                        label = { Text(if (language == UiLanguage.URDU) "کیٹلاگ" else "Catalogue") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.DISTRIBUTOR_DASHBOARD,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.DISTRIBUTOR_DASHBOARD) },
                                        icon = { Icon(Icons.Default.Analytics, contentDescription = "Distributor Dashboard") },
                                        label = { Text(if (language == UiLanguage.URDU) "ڈیش بورڈ" else "Analytics") },
                                        modifier = Modifier.testTag("nav_bottom_distributor_dashboard")
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.CART,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.CART) },
                                        icon = {
                                            BadgedBox(
                                                badge = {
                                                    if (cartItems.isNotEmpty()) {
                                                        Badge { Text("${cartItems.size}") }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                                            }
                                        },
                                        label = { Text(if (language == UiLanguage.URDU) "کارٹ" else "Cart") }
                                    )
                                    NavigationBarItem(
                                        selected = currentRoute == SnlNavRoutes.CUSTOMER_ORDERS,
                                        onClick = { navController.navigateToSection(SnlNavRoutes.CUSTOMER_ORDERS) },
                                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "My Orders") },
                                        label = { Text(if (language == UiLanguage.URDU) "میرے آرڈرز" else "My Orders") }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = defaultHomeRoute,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            composable(SnlNavRoutes.ADMIN_DASHBOARD) {
                                AdminKpiDashboardScreen(
                                    language = language,
                                    products = products,
                                    allOrders = allOrdersForAdmin,
                                    allUsers = allUsersForAdmin,
                                    companySettings = companySettings,
                                    onNavigateSection = { route -> navController.navigateToSection(route) }
                                )
                            }

                            composable(SnlNavRoutes.ADMIN_ORDERS) {
                                AdminOrdersManagementScreen(
                                    language = language,
                                    allOrders = allOrdersForAdmin,
                                    onAcceptOrder = { viewModel.acceptOrder(it) },
                                    onRejectOrder = { order, reason -> viewModel.rejectOrder(order, reason) },
                                    onUpdateOrderStatus = { order, st, pay ->
                                        viewModel.updateOrderStatus(order, st, pay)
                                    }
                                )
                            }

                            composable(SnlNavRoutes.ADMIN_AI_OCR) {
                                AdminAiPriceListOcrScreen(
                                    language = language,
                                    draftRows = draftExtractedRows,
                                    targetPriceGroup = draftTargetPriceGroup,
                                    aiStatusMessage = aiExtractionStatus,
                                    priceGroups = priceGroups,
                                    onSelectTargetGroup = { viewModel.setDraftTargetPriceGroup(it) },
                                    onUpdateDraftRowPrice = { sku, price -> viewModel.updateDraftRowPrice(sku, price) },
                                    onRunGeminiOnBitmap = { bmp -> viewModel.runGeminiExtractionOnBitmap(bmp) },
                                    onRunGeminiOnAttachedAsset = { viewModel.runGeminiOnAttachedPriceListAsset(context) },
                                    onResetToAttachedList = { viewModel.resetDraftToAttachedPriceList() },
                                    onConfirmCommitImport = { file, src, copyStd, allowStock ->
                                        viewModel.confirmAndCommitDraftImport(file, src, copyStd, allowStock)
                                    }
                                )
                            }

                            composable(SnlNavRoutes.ADMIN_EXCEL_SYNC) {
                                AdminExcelAndSheetsSyncScreen(
                                    language = language,
                                    excelPreview = excelPreview,
                                    priceImports = priceImports,
                                    companySettings = companySettings,
                                    products = products,
                                    groupPrices = groupPrices,
                                    onParseExcelOrCsv = { csv, mode -> viewModel.parseExcelOrCsvPreview(csv, mode) },
                                    onConfirmExcelImport = { file, src, stock ->
                                        viewModel.confirmExcelPreviewImport(file, src, stock)
                                    },
                                    onFetchGoogleSheet = { url, mode -> viewModel.fetchAndPreviewGoogleSheet(url, mode) },
                                    onRollbackImport = { batch -> viewModel.rollbackImport(batch) }
                                )
                            }

                            composable(SnlNavRoutes.ADMIN_PRODUCTS) {
                                AdminProductsAndPricingScreen(
                                    language = language,
                                    products = products,
                                    categories = categories,
                                    priceGroups = priceGroups,
                                    groupPrices = groupPrices,
                                    onSaveProduct = { sku, sNo, name, catId, catName, grade, pack, unit, cQty, minQ, mrp, stock, feat, act, dP, stdP, premP, specP ->
                                        viewModel.saveProduct(
                                            sku, sNo, name, catId, catName, grade, pack, unit, cQty, minQ, mrp, stock, feat, act, dP, stdP, premP, specP
                                        )
                                    },
                                    onDeleteProduct = { viewModel.deleteProduct(it) },
                                    onSaveCategory = { c, en, ur, s -> viewModel.saveCategory(c, en, ur, s) },
                                    onSavePriceGroup = { c, n, d, disc, min -> viewModel.savePriceGroup(c, n, d, disc, min) }
                                )
                            }

                            composable(SnlNavRoutes.ADMIN_USERS) {
                                AdminUsersAndApprovalsScreen(
                                    language = language,
                                    allUsers = allUsersForAdmin,
                                    priceGroups = priceGroups,
                                    onSaveAccount = { uid, fName, bName, ph, em, r, code, c, addr, pg, st, perms, cred ->
                                        viewModel.adminSaveAccount(uid, fName, bName, ph, em, r, code, c, addr, pg, st, perms, cred)
                                    }
                                )
                            }

                            composable(SnlNavRoutes.ADMIN_INVENTORY) {
                                AdminInventoryReportsAndSettingsScreen(
                                    language = language,
                                    products = products,
                                    inventoryMovements = inventoryMovements,
                                    allOrders = allOrdersForAdmin,
                                    companySettings = companySettings,
                                    onAdjustInventory = { prod, qty, note -> viewModel.adjustInventory(prod, qty, note) },
                                    onSaveSettings = { viewModel.saveSettings(it) }
                                )
                            }

                            composable(SnlNavRoutes.CART) {
                                CartScreen(
                                    cartViewModel = cartViewModel,
                                    userProfile = profile,
                                    cartItemsOverride = cartItems.takeIf { it.isNotEmpty() },
                                    deliveryChargesPkr = companySettings.defaultDeliveryChargesPkr,
                                    language = language,
                                    isBusy = isBusy,
                                    onUpdateQuantity = { sku, qty -> viewModel.updateCartQuantity(sku, qty) },
                                    onClearCart = { viewModel.clearCart() },
                                    onSubmitOrder = { addr, city, phone, notes, payMethod, onCreated ->
                                        viewModel.submitOrder(addr, city, phone, notes, payMethod) { created ->
                                            cartViewModel.clearCart()
                                            onCreated(created)
                                        }
                                    }
                                )
                            }

                            composable(SnlNavRoutes.CUSTOMER_ORDERS) {
                                OrderHistoryScreen(
                                    currentUserId = profile.id,
                                    ordersOverride = customerOrders.takeIf { it.isNotEmpty() },
                                    language = language,
                                    onReorder = { order ->
                                        viewModel.reorderFromPastOrder(order)
                                        navController.navigateToSection(SnlNavRoutes.CART)
                                    },
                                    onMessage = { viewModel.postMessage(it) }
                                )
                            }

                            composable(SnlNavRoutes.ORDER_TRACKING) {
                                val latestOrder = customerOrders.firstOrNull() ?: allOrdersForAdmin.firstOrNull()
                                OrderTrackingScreen(
                                    orderId = latestOrder?.id ?: "",
                                    initialOrder = latestOrder,
                                    availableOrders = customerOrders.ifEmpty { allOrdersForAdmin },
                                    language = language,
                                    onBack = { navController.navigateToSection(SnlNavRoutes.CUSTOMER_ORDERS) },
                                    onReorder = { order ->
                                        viewModel.reorderFromPastOrder(order)
                                        navController.navigateToSection(SnlNavRoutes.CART)
                                    },
                                    onMessage = { viewModel.postMessage(it) }
                                )
                            }

                            composable(SnlNavRoutes.DISTRIBUTOR_DASHBOARD) {
                                DistributorDashboardScreen(
                                    userProfile = profile,
                                    ordersOverride = customerOrders.takeIf { it.isNotEmpty() },
                                    language = language,
                                    onNavigateToCatalogue = { navController.navigateToSection(SnlNavRoutes.CATALOGUE) },
                                    onNavigateToOrders = { navController.navigateToSection(SnlNavRoutes.CUSTOMER_ORDERS) }
                                )
                            }

                            composable(SnlNavRoutes.CATALOGUE) {
                                CatalogScreen(
                                    userProfile = profile,
                                    products = products.takeIf { it.isNotEmpty() },
                                    categories = categories,
                                    favouriteSkus = favouriteSkus,
                                    language = language,
                                    getPriceForSku = { sku ->
                                        viewModel.getAuthorizedPriceForCustomer(sku, profile.priceGroupId)
                                    },
                                    onToggleFavourite = { viewModel.toggleFavourite(it) },
                                    onAddToCart = { prod, qty ->
                                        val pricePkr = viewModel.getAuthorizedPriceForCustomer(prod.sku, profile.priceGroupId)
                                        cartViewModel.addToCart(prod, qty, pricePkr)
                                        viewModel.addToCart(prod, qty)
                                    },
                                    onQuickSeedCatalogue = {
                                        viewModel.confirmAndCommitDraftImport(
                                            fileName = "Attached_SNL_Sole_Distributor_Price_List_03-11-26.jpg",
                                            sourceType = "AI_IMAGE_OCR",
                                            copyToStandardDealerIfBlank = true,
                                            allowStockUpdate = false
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
