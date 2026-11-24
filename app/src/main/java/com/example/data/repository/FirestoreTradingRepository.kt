package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AuditLogDoc
import com.example.data.model.JournalEntryDoc
import com.example.data.model.Mt5AccountDoc
import com.example.data.model.RiskSettingsDoc
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreTradingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing cloud records.")
    }

    fun observeMt5Accounts(userId: String): Flow<List<Mt5AccountDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch for MT5 account stream." }
        val path = "users/$uid/mt5_accounts"
        emitAll(
            db.collection("users").document(uid).collection("mt5_accounts")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Mt5AccountDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.active }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeRiskSettings(userId: String): Flow<RiskSettingsDoc?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch for risk settings stream." }
        val path = "users/$uid/risk_settings/default"
        emitAll(
            db.collection("users").document(uid).collection("risk_settings").document("default")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            RiskSettingsDoc::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    handleStreamError(error, OperationType.GET, path, uid)
                    throw error
                }
        )
    }

    fun observeJournalEntries(userId: String): Flow<List<JournalEntryDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch for journal stream." }
        val path = "users/$uid/journal_entries"
        emitAll(
            db.collection("users").document(uid).collection("journal_entries")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        JournalEntryDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeAuditLogs(userId: String): Flow<List<AuditLogDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User mismatch for audit logs stream." }
        val path = "users/$uid/audit_logs"
        emitAll(
            db.collection("users").document(uid).collection("audit_logs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(60)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AuditLogDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    handleStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    suspend fun ensureDefaultAccountAndSettings(userId: String): Result<Unit> {
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val accountsRef = db.collection("users").document(uid).collection("mt5_accounts")
            val existingAccounts = accountsRef.get().await()
            if (existingAccounts.isEmpty) {
                val demoAccount = Mt5AccountDoc(
                    userId = uid,
                    label = "MT5 Demo Sandbox",
                    brokerName = "ApexDesk Demo Server",
                    accountLogin = "50842910",
                    accountType = "DEMO",
                    bridgeUrl = null,
                    currency = "USD",
                    balance = 25000.0,
                    equity = 25000.0,
                    marginUsed = 0.0,
                    leverage = 100L,
                    active = true
                )
                accountsRef.document("demo_primary").set(demoAccount.toCreateMap(uid)).await()
            }

            val riskRef = db.collection("users").document(uid).collection("risk_settings").document("default")
            val existingRisk = riskRef.get().await()
            if (!existingRisk.exists()) {
                val defaults = RiskSettingsDoc(userId = uid)
                riskRef.set(defaults.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun saveMt5Account(account: Mt5AccountDoc, customDocId: String? = null): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/mt5_accounts"
        return try {
            val uid = requireUserId()
            val collection = db.collection("users").document(uid).collection("mt5_accounts")
            val docRef = if (!customDocId.isNullOrBlank()) collection.document(customDocId) else collection.document()
            docPath = docRef.path
            docRef.set(account.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setActiveMt5Account(accountId: String, allAccounts: List<Mt5AccountDoc>): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/mt5_accounts/$accountId"
        return try {
            val uid = requireUserId()
            require(accountId.isNotBlank())
            val batch = db.batch()
            val collection = db.collection("users").document(uid).collection("mt5_accounts")
            allAccounts.forEach { acc ->
                if (acc.id.isNotBlank()) {
                    batch.update(
                        collection.document(acc.id),
                        mapOf(
                            "active" to (acc.id == accountId),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }
            docPath = collection.document(accountId).path
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAccountMetrics(
        accountId: String,
        balance: Double,
        equity: Double,
        marginUsed: Double
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/mt5_accounts/$accountId"
        return try {
            val uid = requireUserId()
            if (accountId.isBlank()) return Result.success(Unit)
            val docRef = db.collection("users").document(uid).collection("mt5_accounts").document(accountId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "balance" to balance.coerceAtLeast(0.0),
                    "equity" to equity.coerceAtLeast(0.0),
                    "marginUsed" to marginUsed.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveRiskSettings(settings: RiskSettingsDoc): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/risk_settings/default"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("risk_settings").document("default")
            docPath = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(settings.toUpdateMap()).await()
            } else {
                docRef.set(settings.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addJournalEntry(entry: JournalEntryDoc, customDocId: String? = null): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/journal_entries"
        return try {
            val uid = requireUserId()
            val col = db.collection("users").document(uid).collection("journal_entries")
            val docRef = if (!customDocId.isNullOrBlank()) col.document(customDocId) else col.document()
            docPath = docRef.path
            docRef.set(entry.toCreateMap(uid)).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun closeJournalEntry(
        entryId: String,
        exitPrice: Double,
        pnlAmount: Double
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/journal_entries/$entryId"
        return try {
            val uid = requireUserId()
            if (entryId.isBlank()) return Result.success(Unit)
            val docRef = db.collection("users").document(uid).collection("journal_entries").document(entryId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "exitPrice" to exitPrice.coerceAtLeast(0.0),
                    "pnlAmount" to pnlAmount,
                    "status" to "CLOSED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateJournalNotes(
        entryId: String,
        setupTag: String,
        notes: String,
        emotion: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/journal_entries/$entryId"
        return try {
            val uid = requireUserId()
            require(entryId.isNotBlank()) { "Journal entry ID required." }
            val docRef = db.collection("users").document(uid).collection("journal_entries").document(entryId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "setupTag" to setupTag.trim().take(60),
                    "notes" to notes.trim().take(1000),
                    "emotion" to emotion.trim().take(40),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun appendAuditLog(
        eventType: String,
        symbol: String,
        accountType: String,
        summary: String,
        metadataSummary: String = "",
        biometricVerified: Boolean = false
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/audit_logs"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid).collection("audit_logs").document()
            docPath = docRef.path
            val log = AuditLogDoc(
                userId = uid,
                eventType = eventType,
                symbol = symbol,
                accountType = accountType,
                summary = summary,
                metadataSummary = metadataSummary,
                biometricVerified = biometricVerified
            )
            docRef.set(log.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private fun handleStreamError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        uid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
