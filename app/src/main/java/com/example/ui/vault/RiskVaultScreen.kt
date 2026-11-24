package com.example.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.ComplianceDisclosures
import com.example.security.BiometricAuthHelper
import com.example.security.BiometricCapability
import com.example.ui.CloudUiState
import com.example.ui.MainViewModel
import com.example.ui.auth.signOut
import com.example.ui.components.SensitiveActionRequest
import com.example.ui.components.SensitiveVerificationDialog
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BearCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MonoPriceSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

private enum class VaultSubPage { MAIN, ARCHITECTURE, LEGAL }

@Composable
fun RiskVaultScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val riskSettings by viewModel.currentRiskSettings.collectAsStateWithLifecycle()
    val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
    val entryBioEnabled by viewModel.appEntryBiometricEnabled.collectAsStateWithLifecycle()
    val autoLockBgEnabled by viewModel.autoLockOnBackgroundEnabled.collectAsStateWithLifecycle()
    val sensitiveTradeBioEnabled by viewModel.sensitiveTradeBiometricEnabled.collectAsStateWithLifecycle()

    val bioCapability = remember(context) { BiometricAuthHelper.getCapability(context) }

    var subPage by remember { mutableStateOf(VaultSubPage.MAIN) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var pendingChallenge by remember { mutableStateOf<SensitiveActionRequest?>(null) }

    if (subPage != VaultSubPage.MAIN) {
        BackHandler { subPage = VaultSubPage.MAIN }
        VaultReferenceSubPage(
            subPage = subPage,
            onBack = { subPage = VaultSubPage.MAIN },
            modifier = modifier
        )
        return
    }

    var maxRiskTrade by remember(riskSettings) { mutableFloatStateOf(riskSettings.maxRiskPerTradePct.toFloat()) }
    var maxDailyLoss by remember(riskSettings) { mutableFloatStateOf(riskSettings.maxDailyLossPct.toFloat()) }
    var maxPositions by remember(riskSettings) { mutableFloatStateOf(riskSettings.maxOpenPositions.toFloat()) }
    var minRr by remember(riskSettings) { mutableFloatStateOf(riskSettings.minRiskRewardRatio.toFloat()) }
    var requireSl by remember(riskSettings) { mutableStateOf(riskSettings.requireStopLoss) }
    var requireBio by remember(riskSettings) { mutableStateOf(riskSettings.requireBiometricForLive) }
    var require2fa by remember(riskSettings) { mutableStateOf(riskSettings.requireTwoFactorForLive) }
    var pinInput by remember { mutableStateOf(viewModel.getTwoFactorPin()) }
    var pinFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // MT5 Broker Bridge Accounts Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = BullEmerald)
                        Column {
                            Text("MT5 Broker Accounts & Vault", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("Keystore AES-256-GCM encrypted tokens", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                    OutlinedButton(
                        onClick = { showLinkDialog = true },
                        modifier = Modifier.testTag("link_mt5_account_button")
                    ) {
                        Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(" Link")
                    }
                }

                val accounts = (accountsState as? CloudUiState.Success)?.data.orEmpty()
                accounts.forEach { acc ->
                    val hasTok = viewModel.hasEncryptedToken(acc.id)
                    Surface(
                        color = SlateElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${acc.label} (${acc.accountType})", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    "${acc.brokerName} • #${acc.accountLogin}${if (hasTok) " • Token Encrypted" else " • Sandbox"}",
                                    style = MonoPriceSmall,
                                    color = TextSecondary
                                )
                            }
                            if (acc.active) {
                                Surface(color = BullEmerald.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = BullEmerald, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            } else {
                                TextButton(onClick = { viewModel.switchActiveAccount(acc) }) {
                                    Text("Activate", color = AiCyan)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Strict Risk-Management Rules Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = AmberGold)
                    Text("Pre-Trade Risk Management Guardrails", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                }

                RiskSliderRow("Max Risk per Trade", "${String.format(Locale.US, "%.1f", maxRiskTrade)}%", maxRiskTrade, 0.25f..5.0f) {
                    maxRiskTrade = it
                }
                RiskSliderRow("Max Daily Drawdown", "${String.format(Locale.US, "%.1f", maxDailyLoss)}%", maxDailyLoss, 1.0f..15.0f) {
                    maxDailyLoss = it
                }
                RiskSliderRow("Max Open Positions", "${maxPositions.toInt()}", maxPositions, 1f..10f, steps = 8) {
                    maxPositions = it
                }
                RiskSliderRow("Minimum Target R:R", "${String.format(Locale.US, "%.1f", minRr)}:1", minRr, 1.0f..4.0f) {
                    minRr = it
                }

                ToggleRow("Require Stop-Loss on All Orders", requireSl) { requireSl = it }
                ToggleRow("Biometric Prompt for LIVE Orders", requireBio) { requireBio = it }
                ToggleRow("Require 2FA PIN for LIVE Orders", require2fa) { require2fa = it }

                Button(
                    onClick = {
                        val updatedDoc = riskSettings.copy(
                            maxRiskPerTradePct = ((maxRiskTrade * 10).toInt() / 10.0),
                            maxDailyLossPct = ((maxDailyLoss * 10).toInt() / 10.0),
                            maxOpenPositions = maxPositions.toLong().coerceIn(1L, 50L),
                            minRiskRewardRatio = ((minRr * 10).toInt() / 10.0),
                            requireStopLoss = requireSl,
                            requireBiometricForLive = requireBio,
                            requireTwoFactorForLive = require2fa
                        )
                        if (sensitiveTradeBioEnabled) {
                            pendingChallenge = SensitiveActionRequest(
                                title = "Authorize Risk Rule Changes",
                                subtitle = "Update pre-trade risk & safety limits",
                                summary = "Max risk ${updatedDoc.maxRiskPerTradePct}%/trade • Max daily loss ${updatedDoc.maxDailyLossPct}% • Max open ${updatedDoc.maxOpenPositions}",
                                confirmLabel = "Save Risk Rules",
                                isHighRisk = true,
                                onAuthorized = { bioOk ->
                                    viewModel.updateRiskSettings(updatedDoc, biometricVerified = bioOk)
                                }
                            )
                        } else {
                            viewModel.updateRiskSettings(updatedDoc, biometricVerified = false)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BullEmerald, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_risk_rules_button")
                ) {
                    Text("Save Risk Rules to Cloud", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Security, BiometricPrompt & 2FA Lock Controls
        Card(
            colors = CardDefaults.cardColors(containerColor = MidnightSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = AiCyan)
                        Text("BiometricPrompt & 2FA Vault", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    }
                    Surface(
                        color = (if (bioCapability.canPrompt) BullEmerald else AmberGold).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (bioCapability.canPrompt) "READY" else "PIN FALLBACK",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bioCapability.canPrompt) BullEmerald else AmberGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Hardware Status: ${bioCapability.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                if (bioCapability == BiometricCapability.NOT_ENROLLED) {
                    OutlinedButton(
                        onClick = { BiometricAuthHelper.openEnrollSettings(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = AiCyan, modifier = Modifier.size(18.dp))
                        Text("  Enroll Device Fingerprint / Face", color = AiCyan)
                    }
                }

                ToggleRow(
                    label = "Secure App Entry with BiometricPrompt",
                    checked = entryBioEnabled,
                    onCheckedChange = { viewModel.setAppEntryBiometricEnabled(it) }
                )
                ToggleRow(
                    label = "Auto-Lock When App Returns from Background",
                    checked = autoLockBgEnabled,
                    onCheckedChange = { viewModel.setAutoLockOnBackgroundEnabled(it) }
                )
                ToggleRow(
                    label = "Verify Sensitive Trade Actions (Orders, Closes & Risk)",
                    checked = sensitiveTradeBioEnabled,
                    onCheckedChange = { viewModel.setSensitiveTradeBiometricEnabled(it) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6) pinInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("6-Digit 2FA Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (pinInput.length != 6) {
                                pinFeedback = "PIN must be 6 digits."
                            } else {
                                val nextPin = pinInput
                                pendingChallenge = SensitiveActionRequest(
                                    title = "Authorize 2FA PIN Change",
                                    subtitle = "Update Keystore-backed 2FA Vault PIN",
                                    summary = "Verify current identity before rotating your 6-digit fallback PIN.",
                                    confirmLabel = "Update PIN",
                                    isHighRisk = true,
                                    onAuthorized = {
                                        pinFeedback = if (viewModel.updateTwoFactorPin(nextPin)) {
                                            "2FA PIN updated in Vault."
                                        } else {
                                            "PIN must be 6 digits."
                                        }
                                    }
                                )
                            }
                        },
                        modifier = Modifier.height(54.dp)
                    ) {
                        Text("Update")
                    }
                }
                if (pinFeedback != null) {
                    Text(pinFeedback.orEmpty(), style = MaterialTheme.typography.bodySmall, color = AiCyan)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (activity != null) {
                                BiometricAuthHelper.authenticate(
                                    activity = activity,
                                    title = "Verify ApexDesk Identity",
                                    subtitle = "BiometricPrompt hardware verification check",
                                    requireExplicitConfirmation = false,
                                    onSuccess = { pinFeedback = "BiometricPrompt verification succeeded." },
                                    onError = { pinFeedback = it },
                                    onFallbackRequested = {
                                        pinFeedback = "Biometric hardware unavailable or bypassed; 2FA PIN active."
                                    }
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Test Prompt")
                    }
                    OutlinedButton(
                        onClick = { viewModel.setSessionLocked(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("lock_terminal_now_button")
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" Lock Now")
                    }
                }
            }
        }

        // Architecture Blueprint & Legal Disclosures Navigation
        OutlinedButton(
            onClick = { subPage = VaultSubPage.ARCHITECTURE },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_architecture_button")
        ) {
            Icon(Icons.Filled.AccountTree, contentDescription = null, tint = AiCyan)
            Text("  System Architecture & Tech Stack Blueprint", color = TextPrimary)
        }

        OutlinedButton(
            onClick = { subPage = VaultSubPage.LEGAL },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("open_legal_button")
        ) {
            Icon(Icons.Filled.Gavel, contentDescription = null, tint = AmberGold)
            Text("  Legal & Regulatory Risk Disclosures", color = TextPrimary)
        }

        Button(
            onClick = { signOut(credentialManager, scope) },
            colors = ButtonDefaults.buttonColors(containerColor = BearCrimson.copy(alpha = 0.2f), contentColor = BearCrimson),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
            Text("  Sign Out of ApexDesk")
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showLinkDialog) {
        LinkMt5AccountDialog(
            onDismiss = { showLinkDialog = false },
            onLink = { label, broker, login, accType, bridge, token, bal, lev, onDone ->
                if (accType == "LIVE" || bridge.isNotBlank()) {
                    pendingChallenge = SensitiveActionRequest(
                        title = "Authorize MT5 Account Link",
                        subtitle = "$broker • #$login ($accType)",
                        summary = if (token.isNotBlank()) "Encrypt broker API token in Android Keystore and activate account." else "Activate MT5 account configuration.",
                        confirmLabel = "Authorize Link",
                        isHighRisk = accType == "LIVE",
                        onCancelled = { onDone(Result.failure(Exception("Authorization cancelled."))) },
                        onAuthorized = {
                            viewModel.linkMt5Account(label, broker, login, accType, bridge, token, bal, lev) { res ->
                                onDone(res)
                                if (res.isSuccess) showLinkDialog = false
                            }
                        }
                    )
                } else {
                    viewModel.linkMt5Account(label, broker, login, accType, bridge, token, bal, lev) { res ->
                        onDone(res)
                        if (res.isSuccess) showLinkDialog = false
                    }
                }
            }
        )
    }

    pendingChallenge?.let { req ->
        SensitiveVerificationDialog(
            request = req,
            verifyTwoFactorPin = { viewModel.verifyTwoFactorCode(it) },
            twoFactorPinHint = viewModel.getTwoFactorPin(),
            onDismiss = {
                req.onCancelled()
                pendingChallenge = null
            }
        )
    }
}

@Composable
private fun VaultReferenceSubPage(
    subPage: VaultSubPage,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                text = if (subPage == VaultSubPage.ARCHITECTURE) "System Architecture & Tech Stack"
                else "Legal & Risk Disclosures",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
        }

        if (subPage == VaultSubPage.ARCHITECTURE) {
            ComplianceDisclosures.SYSTEM_ARCHITECTURE_SECTIONS.forEach { (title, body) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = AiCyan)
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AmberGold.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            ) {
                Text(
                    text = ComplianceDisclosures.FULL_LEGAL_DISCLAIMER,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RiskSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(valueText, style = MonoPriceSmall, color = AiCyan)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = AiCyan, activeTrackColor = AiCyan)
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LinkMt5AccountDialog(
    onDismiss: () -> Unit,
    onLink: (
        label: String,
        broker: String,
        login: String,
        accountType: String,
        bridgeUrl: String,
        token: String,
        balance: Double,
        leverage: Long,
        onDone: (Result<Unit>) -> Unit
    ) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var broker by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("DEMO") }
    var bridgeUrl by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var balanceText by remember { mutableStateOf("25000") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        title = { Text("Link MetaTrader 5 Account", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DEMO", "LIVE").forEach { t ->
                        FilterChip(selected = accountType == t, onClick = { accountType = t }, label = { Text(t) })
                    }
                }
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Account Label") }, singleLine = true)
                OutlinedTextField(value = broker, onValueChange = { broker = it }, label = { Text("Broker / Server Name") }, singleLine = true)
                OutlinedTextField(value = login, onValueChange = { login = it }, label = { Text("MT5 Login ID") }, singleLine = true)
                OutlinedTextField(
                    value = bridgeUrl,
                    onValueChange = { bridgeUrl = it },
                    label = { Text("HTTPS Bridge URL (optional for Demo)") },
                    placeholder = { Text("https://mt-client-api.example.com") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Bridge API Token (Keystore Encrypted)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Starting Balance (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                if (errorMsg != null) {
                    Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = BearCrimson)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && login.isNotBlank() && broker.isNotBlank(),
                onClick = {
                    saving = true
                    errorMsg = null
                    onLink(
                        label,
                        broker,
                        login,
                        accountType,
                        bridgeUrl,
                        token,
                        balanceText.toDoubleOrNull() ?: 10000.0,
                        100L
                    ) { res ->
                        saving = false
                        res.onFailure { errorMsg = it.localizedMessage ?: "Failed to link account." }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AiCyan, contentColor = ObsidianBg)
            ) {
                Text(if (saving) "Linking..." else "Save & Encrypt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
