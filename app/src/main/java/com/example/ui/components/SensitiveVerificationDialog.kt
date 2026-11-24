package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricAuthHelper
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BearCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class SensitiveActionRequest(
    val title: String,
    val subtitle: String,
    val summary: String,
    val confirmLabel: String = "Authorize Action",
    val isHighRisk: Boolean = true,
    val onCancelled: () -> Unit = {},
    val onAuthorized: (biometricVerified: Boolean) -> Unit
)

@Composable
fun SensitiveVerificationDialog(
    request: SensitiveActionRequest,
    verifyTwoFactorPin: (String) -> Boolean,
    twoFactorPinHint: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val capability = remember(context) { BiometricAuthHelper.getCapability(context) }

    var showPinFallback by remember { mutableStateOf(!capability.canPrompt) }
    var pinInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun launchBiometricPrompt() {
        errorMsg = null
        if (activity == null || !capability.canPrompt) {
            showPinFallback = true
            return
        }
        BiometricAuthHelper.authenticate(
            activity = activity,
            title = request.title,
            subtitle = request.subtitle,
            description = request.summary,
            requireExplicitConfirmation = true,
            onSuccess = {
                onDismiss()
                request.onAuthorized(true)
            },
            onError = { msg ->
                errorMsg = msg
            },
            onFallbackRequested = {
                showPinFallback = true
            }
        )
    }

    LaunchedEffect(request) {
        if (capability.canPrompt && activity != null) {
            launchBiometricPrompt()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        icon = {
            Icon(
                imageVector = if (capability.canPrompt) Icons.Filled.Fingerprint else Icons.Filled.Shield,
                contentDescription = null,
                tint = if (request.isHighRisk) AmberGold else AiCyan,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = SlateElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(request.subtitle, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(request.summary, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = BearCrimson
                    )
                }

                if (showPinFallback) {
                    if (!capability.canPrompt) {
                        Text(
                            text = "${capability.label}. Verify using your 6-digit 2FA Vault PIN (Vault: $twoFactorPinHint).",
                            style = MaterialTheme.typography.bodySmall,
                            color = AmberGold
                        )
                    }
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 6) {
                                pinInput = it.filter { ch -> ch.isDigit() }
                                errorMsg = null
                            }
                        },
                        label = { Text("6-Digit 2FA Vault PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sensitive_action_pin_input")
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Filled.Shield, contentDescription = null, tint = AiCyan, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Confirm via BiometricPrompt or switch to 2FA Vault PIN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showPinFallback) {
                Button(
                    onClick = {
                        if (verifyTwoFactorPin(pinInput)) {
                            onDismiss()
                            request.onAuthorized(false)
                        } else {
                            errorMsg = "Invalid 6-digit 2FA Vault PIN."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (request.isHighRisk) BearCrimson else AiCyan,
                        contentColor = if (request.isHighRisk) TextPrimary else ObsidianBg
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("sensitive_action_confirm_pin_button")
                ) {
                    Text(request.confirmLabel, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { launchBiometricPrompt() },
                    colors = ButtonDefaults.buttonColors(containerColor = AiCyan, contentColor = ObsidianBg),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("sensitive_action_biometric_button")
                ) {
                    Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Verify Biometrics", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!showPinFallback) {
                    OutlinedButton(
                        onClick = { showPinFallback = true },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Use PIN")
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
