package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.SecureTokenVault
import com.example.data.remote.GeminiChartAnalyzer
import com.example.data.repository.FirestoreTradingRepository
import com.example.data.repository.Mt5MarketRepository
import com.example.domain.ComplianceDisclosures
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricCapability
import com.example.security.TradingNotificationHelper
import com.example.security.configureAppCheck
import com.example.ui.MainViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.charts.ChartsScreen
import com.example.ui.desk.DeskScreen
import com.example.ui.journal.JournalScreen
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ApexDeskTheme
import com.example.ui.theme.BearCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.vault.RiskVaultScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class ApexTab(
    val label: String,
    val icon: ImageVector,
    val tag: String
) {
    DESK("Desk", Icons.Filled.AccountBalance, "nav_desk"),
    CHARTS("Charts", Icons.Filled.CandlestickChart, "nav_charts"),
    JOURNAL("Journal", Icons.Filled.Book, "nav_journal"),
    VAULT("Risk & Vault", Icons.Filled.Shield, "nav_vault")
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ApexDeskTheme {
                ApexRoot()
            }
        }
    }
}

@Composable
private fun ApexRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            val viewModel: MainViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val tokenVault = SecureTokenVault(app)
                        MainViewModel(
                            currentUserId = user.uid,
                            firestoreRepo = FirestoreTradingRepository(db, auth),
                            marketRepo = Mt5MarketRepository(app, tokenVault),
                            aiAnalyzer = GeminiChartAnalyzer(),
                            tokenVault = tokenVault,
                            notificationHelper = TradingNotificationHelper(app)
                        )
                    }
                }
            )
            AuthenticatedTerminalShell(
                userEmail = user.email.orEmpty(),
                viewModel = viewModel
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedTerminalShell(
    userEmail: String,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var activeTab by rememberSaveable { mutableStateOf(ApexTab.DESK) }
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isLocked by viewModel.isSessionLocked.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> viewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    if (activeTab != ApexTab.DESK) {
        BackHandler { activeTab = ApexTab.DESK }
    }

    if (isLocked) {
        BiometricEntryGateScreen(
            userEmail = userEmail,
            pinHint = viewModel.getTwoFactorPin(),
            onUnlockBiometric = { viewModel.unlockSessionWithBiometric() },
            onVerifyPin = { viewModel.unlockSessionWithPin(it) }
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MidnightSurface,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Column {
                            Text("ApexDesk", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            Text(
                                text = "${activeAccount.label} • ${activeAccount.accountType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeAccount.accountType == "LIVE") BearCrimson else BullEmerald
                            )
                        }
                    },
                    actions = {
                        Surface(
                            color = SlateElevated,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "MT5 ${activeAccount.accountType}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeAccount.accountType == "LIVE") BearCrimson else BullEmerald,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setSessionLocked(true) },
                            modifier = Modifier.testTag("top_bar_lock_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Lock terminal session",
                                tint = AiCyan
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = MidnightSurface) {
                        ApexTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AiCyan,
                                    selectedTextColor = AiCyan,
                                    indicatorColor = AiCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.tag)
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
                    NavigationRail(
                        containerColor = MidnightSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        ApexTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = AiCyan,
                                    selectedTextColor = AiCyan,
                                    indicatorColor = AiCyan.copy(alpha = 0.16f),
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activeTab) {
                            ApexTab.DESK -> DeskScreen(
                                viewModel = viewModel,
                                onNavigateToChart = { sym ->
                                    viewModel.selectSymbol(sym)
                                    activeTab = ApexTab.CHARTS
                                },
                                onNavigateToVault = { activeTab = ApexTab.VAULT }
                            )
                            ApexTab.CHARTS -> ChartsScreen(viewModel = viewModel)
                            ApexTab.JOURNAL -> JournalScreen(viewModel = viewModel)
                            ApexTab.VAULT -> RiskVaultScreen(viewModel = viewModel)
                        }
                    }
                    Surface(color = MidnightSurface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = ComplianceDisclosures.SHORT_RISK_BANNER,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricEntryGateScreen(
    userEmail: String,
    pinHint: String,
    onUnlockBiometric: () -> Unit,
    onVerifyPin: (String) -> Boolean
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var capability by remember { mutableStateOf(BiometricAuthHelper.getCapability(context)) }
    var showPinFallback by remember { mutableStateOf(!capability.canPrompt) }
    var pin by remember { mutableStateOf("") }
    var statusError by remember { mutableStateOf<String?>(null) }
    var promptAttempts by remember { mutableIntStateOf(0) }

    fun triggerEntryBiometric() {
        capability = BiometricAuthHelper.getCapability(context)
        if (activity == null || !capability.canPrompt) {
            showPinFallback = true
            return
        }
        statusError = null
        BiometricAuthHelper.authenticate(
            activity = activity,
            title = "Unlock ApexDesk Terminal",
            subtitle = "Verify identity to access MT5 accounts & trading tools",
            description = if (userEmail.isNotBlank()) "Signed in as $userEmail" else null,
            requireExplicitConfirmation = false,
            onSuccess = { onUnlockBiometric() },
            onError = { err -> statusError = err },
            onFallbackRequested = { showPinFallback = true }
        )
    }

    LaunchedEffect(promptAttempts) {
        if (promptAttempts == 0 && capability.canPrompt && activity != null) {
            triggerEntryBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBg, MidnightSurface)
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = MidnightSurface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                .testTag("biometric_entry_gate")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = AiCyan.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (capability.canPrompt) Icons.Filled.Fingerprint else Icons.Filled.Lock,
                            contentDescription = "Biometric application entry lock",
                            tint = AiCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = "ApexDesk Entry Protected",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )

                Text(
                    text = "Authenticate with Android BiometricPrompt or your 2FA Vault PIN before viewing live balances or placing MT5 orders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = if (capability.canPrompt) BullEmerald else AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = capability.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (capability.canPrompt) BullEmerald else AmberGold
                            )
                            if (userEmail.isNotBlank()) {
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                if (statusError != null) {
                    Surface(
                        color = BearCrimson.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statusError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = BearCrimson,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                if (capability.canPrompt) {
                    Button(
                        onClick = {
                            promptAttempts += 1
                            triggerEntryBiometric()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiCyan,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("entry_biometric_unlock_button")
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Unlock with Biometrics", fontWeight = FontWeight.Bold)
                    }
                } else if (capability == BiometricCapability.NOT_ENROLLED) {
                    OutlinedButton(
                        onClick = { BiometricAuthHelper.openEnrollSettings(context) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = AiCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enroll Device Biometrics", color = AiCyan)
                    }
                }

                if (showPinFallback) {
                    HorizontalDivider(color = BorderSubtle.copy(alpha = 0.6f))
                    Text(
                        text = "2FA Vault PIN Fallback (Vault: $pinHint)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 6) {
                                pin = it.filter { ch -> ch.isDigit() }
                                statusError = null
                            }
                        },
                        label = { Text("6-Digit 2FA Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("entry_pin_input")
                    )
                    FilledTonalButton(
                        onClick = {
                            if (!onVerifyPin(pin)) {
                                statusError = "Invalid 6-digit 2FA Vault PIN."
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("entry_pin_unlock_button")
                    ) {
                        Text("Unlock with 2FA PIN", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(
                        onClick = { showPinFallback = true },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Use 2FA Vault PIN Instead", color = TextSecondary)
                    }
                }

                TextButton(
                    onClick = { signOut(credentialManager, scope) },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Sign Out", color = TextMuted)
                }
            }
        }
    }
}
