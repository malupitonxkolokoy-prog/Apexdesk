package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class Mt5AccountDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val brokerName: String = "",
    val accountLogin: String = "",
    val accountType: String = "DEMO",
    val bridgeUrl: String? = null,
    val currency: String = "USD",
    val balance: Double = 25000.0,
    val equity: Double = 25000.0,
    val marginUsed: Double = 0.0,
    val leverage: Long = 100L,
    val active: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "label" to label.trim().take(50),
            "brokerName" to brokerName.trim().take(80),
            "accountLogin" to accountLogin.trim().take(40),
            "accountType" to if (accountType == "LIVE") "LIVE" else "DEMO",
            "bridgeUrl" to bridgeUrl?.trim()?.takeIf { it.isNotEmpty() }?.take(300),
            "currency" to currency.trim().uppercase().take(6).ifBlank { "USD" },
            "balance" to balance.coerceAtLeast(0.0),
            "equity" to equity.coerceAtLeast(0.0),
            "marginUsed" to marginUsed.coerceAtLeast(0.0),
            "leverage" to leverage.coerceIn(1L, 2000L),
            "active" to active,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class RiskSettingsDoc(
    @DocumentId val id: String = "default",
    val userId: String = "",
    val maxRiskPerTradePct: Double = 1.0,
    val maxDailyLossPct: Double = 3.0,
    val maxOpenPositions: Long = 3L,
    val minRiskRewardRatio: Double = 1.5,
    val requireStopLoss: Boolean = true,
    val requireBiometricForLive: Boolean = true,
    val requireTwoFactorForLive: Boolean = true,
    val disclaimerAccepted: Boolean = false,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "maxRiskPerTradePct" to maxRiskPerTradePct.coerceIn(0.1, 25.0),
        "maxDailyLossPct" to maxDailyLossPct.coerceIn(0.5, 50.0),
        "maxOpenPositions" to maxOpenPositions.coerceIn(1L, 50L),
        "minRiskRewardRatio" to minRiskRewardRatio.coerceIn(0.5, 10.0),
        "requireStopLoss" to requireStopLoss,
        "requireBiometricForLive" to requireBiometricForLive,
        "requireTwoFactorForLive" to requireTwoFactorForLive,
        "disclaimerAccepted" to disclaimerAccepted,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "maxRiskPerTradePct" to maxRiskPerTradePct.coerceIn(0.1, 25.0),
        "maxDailyLossPct" to maxDailyLossPct.coerceIn(0.5, 50.0),
        "maxOpenPositions" to maxOpenPositions.coerceIn(1L, 50L),
        "minRiskRewardRatio" to minRiskRewardRatio.coerceIn(0.5, 10.0),
        "requireStopLoss" to requireStopLoss,
        "requireBiometricForLive" to requireBiometricForLive,
        "requireTwoFactorForLive" to requireTwoFactorForLive,
        "disclaimerAccepted" to disclaimerAccepted,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class JournalEntryDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val direction: String = "BUY",
    val accountType: String = "DEMO",
    val entryPrice: Double = 0.0,
    val exitPrice: Double = 0.0,
    val stopLoss: Double = 0.0,
    val takeProfit: Double = 0.0,
    val lotSize: Double = 0.01,
    val pnlAmount: Double = 0.0,
    val setupTag: String = "",
    val notes: String = "",
    val emotion: String = "Disciplined",
    val status: String = "OPEN",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "symbol" to symbol.trim().take(20),
        "direction" to if (direction == "SELL") "SELL" else "BUY",
        "accountType" to if (accountType == "LIVE") "LIVE" else "DEMO",
        "entryPrice" to entryPrice.coerceAtLeast(0.0),
        "exitPrice" to exitPrice.coerceAtLeast(0.0),
        "stopLoss" to stopLoss.coerceAtLeast(0.0),
        "takeProfit" to takeProfit.coerceAtLeast(0.0),
        "lotSize" to lotSize.coerceIn(0.01, 500.0),
        "pnlAmount" to pnlAmount,
        "setupTag" to setupTag.trim().take(60),
        "notes" to notes.trim().take(1000),
        "emotion" to emotion.trim().take(40),
        "status" to when (status) {
            "CLOSED", "CANCELLED" -> status
            else -> "OPEN"
        },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AuditLogDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventType: String = "AI_SIGNAL",
    val symbol: String = "",
    val accountType: String = "DEMO",
    val summary: String = "",
    val metadataSummary: String = "",
    val biometricVerified: Boolean = false,
    val createdAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "eventType" to eventType,
        "symbol" to symbol.trim().take(20),
        "accountType" to when (accountType) {
            "LIVE", "SYSTEM" -> accountType
            else -> "DEMO"
        },
        "summary" to summary.trim().take(300).ifBlank { "System audit event" },
        "metadataSummary" to metadataSummary.trim().take(500),
        "biometricVerified" to biometricVerified,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
