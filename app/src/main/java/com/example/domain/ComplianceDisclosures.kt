package com.example.domain

object ComplianceDisclosures {

    const val SHORT_RISK_BANNER =
        "Risk Notice: Leveraged trading involves substantial risk of loss. AI signals are probabilistic decision-support only — never guaranteed profit or financial advice."

    const val FULL_LEGAL_DISCLAIMER =
        """1. SUBSTANTIAL RISK OF LOSS
Trading foreign exchange (FX), Contracts for Difference (CFDs), precious metals, indices, and digital assets on margin via MetaTrader 5 (MT5) carries a high level of risk and may not be suitable for all investors. High leverage can work both for and against you. Before deciding to trade, carefully consider your investment objectives, level of experience, and risk appetite. You could sustain a loss of some or all of your initial investment.

2. DECISION-SUPPORT TOOL — NOT FINANCIAL ADVICE
ApexDesk is a technical charting, risk-management, and execution-assistance software platform. It is NOT a registered investment advisor, broker-dealer, fiduciary, or commodity trading advisor. Nothing displayed in this application — including technical indicators, candlestick pattern detections, risk/reward projections, or Gemini AI trade setups — constitutes personalized financial, investment, tax, or legal advice.

3. NO GUARANTEED PROFITABILITY
Past performance of any technical indicator, historical market structure, or AI-generated setup does not guarantee future results. AI confidence scores reflect statistical/structural confluence observations only and are subject to market slippage, liquidity gaps, macroeconomic shocks, and model uncertainty.

4. USER EXECUTION RESPONSIBILITY
All live account orders require explicit user confirmation. You are solely responsible for verifying order parameters (instrument, direction, lot size, stop-loss, take-profit, and margin impact) prior to transmission to your MT5 broker bridge."""

    val SYSTEM_ARCHITECTURE_SECTIONS = listOf(
        "High-Level System Architecture" to
            "Modular 4-layer Clean/MVVM design: (1) Presentation Layer (Jetpack Compose Material 3 adaptive UI + Canvas Charting), (2) Domain Engines (TechnicalAnalysisEngine, RiskEngine), (3) Security & Auth Layer (Google Sign-In via Credential Manager, BiometricPrompt + 2FA gate, Keystore AES-256-GCM Token Vault, Firebase App Check), and (4) Data Layer (Retrofit MT5 WebAPI/Bridge client, Room offline OHLCV cache, Firebase AI Logic Gemini 3.8 Flash analyzer, and Cloud Firestore user-scoped journal/audit store).",
        "MT5 Integration & Security" to
            "Connects to broker-supported HTTPS MT5 WebAPI / MetaApi-compatible REST bridges with bearer token authentication, plus a built-in Demo Sandbox engine for risk-free strategy evaluation. Broker tokens are encrypted locally via Android Keystore AES-256-GCM and are never persisted in plain text or synced to cloud databases.",
        "AI / ML Analysis Layer" to
            "Powered by Firebase AI Logic (gemini-3.8-flash) with enforced JSON Schema output. Receives computed indicator telemetry (EMA 20/50, Bollinger Bands, RSI 14, MACD, ATR, S/R zones, patterns) rather than raw guesswork, producing entry, SL, TP, R:R, invalidation conditions, and plain-language reasoning without claiming guaranteed outcomes.",
        "Compliance & Auditability" to
            "Every account link, risk rule update, AI signal, blocked risk violation, and trade execution writes an immutable append-only record to Firestore (/users/{uid}/audit_logs), enforced by zero-trust security rules that block client updates or deletions."
    )
}
