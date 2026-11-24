package com.example.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricCapability(val label: String, val canPrompt: Boolean) {
    STRONG_BIOMETRIC_READY("Strong Biometric Enrolled", true),
    DEVICE_CREDENTIAL_READY("Device Lock Credential Ready", true),
    NOT_ENROLLED("Biometrics Not Enrolled", false),
    UNAVAILABLE("Biometric Hardware Unavailable", false)
}

object BiometricAuthHelper {

    fun getCapability(context: Context): BiometricCapability {
        val manager = BiometricManager.from(context)
        val strongStatus = manager.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
        if (strongStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricCapability.STRONG_BIOMETRIC_READY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val deviceCredStatus = manager.canAuthenticate(
                Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            )
            if (deviceCredStatus == BiometricManager.BIOMETRIC_SUCCESS) {
                return BiometricCapability.DEVICE_CREDENTIAL_READY
            }
        }

        return when (strongStatus) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            else -> BiometricCapability.UNAVAILABLE
        }
    }

    fun isBiometricAvailable(context: Context): Boolean = getCapability(context).canPrompt

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        description: String? = null,
        requireExplicitConfirmation: Boolean = true,
        negativeButtonLabel: String = "Use 2FA Vault PIN",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFallbackRequested: () -> Unit
    ) {
        val capability = getCapability(activity)
        if (!capability.canPrompt) {
            onFallbackRequested()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_NO_BIOMETRICS,
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE,
                        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                            onFallbackRequested()
                        }
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                            onError("Biometric sensor locked after failed attempts. Verify with your 2FA Vault PIN.")
                            onFallbackRequested()
                        }
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> {
                            onError("Biometric verification cancelled.")
                        }
                        else -> {
                            onError(errString.toString().ifBlank { "Biometric verification could not complete." })
                        }
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )

        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setConfirmationRequired(requireExplicitConfirmation)

        if (!description.isNullOrBlank()) {
            promptBuilder.setDescription(description)
        }

        if (capability == BiometricCapability.STRONG_BIOMETRIC_READY) {
            promptBuilder
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(negativeButtonLabel)
        } else {
            // DEVICE_CREDENTIAL cannot be combined with setNegativeButtonText
            promptBuilder.setAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            )
        }

        try {
            prompt.authenticate(promptBuilder.build())
        } catch (_: Exception) {
            onFallbackRequested()
        }
    }

    fun openEnrollSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        }
    }
}
