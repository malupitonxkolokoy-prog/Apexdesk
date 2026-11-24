package com.example.ui.journal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AuditLogDoc
import com.example.data.model.JournalEntryDoc
import com.example.data.model.MarketSymbol
import com.example.ui.CloudUiState
import com.example.ui.MainViewModel
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AmberGold
import com.example.ui.theme.BearCrimson
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.BullEmerald
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.MonoPriceMedium
import com.example.ui.theme.MonoPriceSmall
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun JournalScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val journalState by viewModel.journalState.collectAsStateWithLifecycle()
    val auditLogsState by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val analytics by viewModel.analytics.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var editingEntry by remember { mutableStateOf<JournalEntryDoc?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MidnightSurface,
            contentColor = AiCyan
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Analytics & Journal") },
                modifier = Modifier.testTag("journal_tab")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Compliance Audit Log") },
                modifier = Modifier.testTag("audit_tab")
            )
        }

        if (selectedTab == 0) {
            // Analytics Overview Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MidnightSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Portfolio Performance", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        OutlinedButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.testTag("log_trade_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Log Trade")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatCell(
                            "Net P&L",
                            "${if (analytics.netPnl >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", analytics.netPnl)}",
                            if (analytics.netPnl >= 0) BullEmerald else BearCrimson
                        )
                        StatCell(
                            "Win Rate",
                            "${String.format(Locale.US, "%.1f", analytics.winRatePct)}%",
                            AiCyan
                        )
                        StatCell(
                            "Profit Factor",
                            String.format(Locale.US, "%.2f", analytics.profitFactor),
                            TextPrimary
                        )
                        StatCell(
                            "Closed",
                            "${analytics.totalTrades}",
                            TextSecondary
                        )
                    }

                    // Mini Equity Curve Canvas
                    val pts = analytics.equityCurvePoints
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianBg)
                    ) {
                        if (pts.size >= 2) {
                            val minV = pts.min()
                            val maxV = pts.max()
                            val range = max(maxV - minV, 1.0)
                            val path = Path()
                            pts.forEachIndexed { idx, v ->
                                val x = (idx / (pts.size - 1).toFloat()) * (size.width - 16f) + 8f
                                val y = size.height - (((v - minV) / range).toFloat() * (size.height - 16f) + 8f)
                                if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = if (analytics.netPnl >= 0) BullEmerald else BearCrimson,
                                style = Stroke(width = 3.5f)
                            )
                        } else {
                            drawLine(
                                color = BorderSubtle,
                                start = Offset(12f, size.height / 2f),
                                end = Offset(size.width - 12f, size.height / 2f),
                                strokeWidth = 2f
                            )
                        }
                    }
                }
            }

            // Journal Entries List
            when (val state = journalState) {
                is CloudUiState.Loading -> CircularProgressIndicator(color = AiCyan)
                is CloudUiState.Error -> Text(state.message, color = BearCrimson)
                is CloudUiState.Success -> {
                    if (state.data.isEmpty()) {
                        Surface(
                            color = MidnightSurface,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.HistoryEdu, contentDescription = null, tint = AiCyan, modifier = Modifier.size(32.dp))
                                Text(
                                    "Your cloud Trade Journal is empty. Execute a setup or tap Log Trade to track edge & discipline.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        state.data.forEach { entry ->
                            JournalEntryCard(entry = entry, onEditClick = { editingEntry = entry })
                        }
                    }
                }
            }
        } else {
            // Compliance Audit Log Viewer
            Surface(
                color = SlateElevated,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = BullEmerald)
                    Text(
                        "Append-only compliance ledger. Trade decisions, risk gate rejections, AI signals, and executions cannot be altered or deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            when (val logs = auditLogsState) {
                is CloudUiState.Loading -> CircularProgressIndicator(color = AiCyan)
                is CloudUiState.Error -> Text(logs.message, color = BearCrimson)
                is CloudUiState.Success -> {
                    if (logs.data.isEmpty()) {
                        Text("No audit records recorded yet.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        logs.data.forEach { log ->
                            AuditLogCard(log)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    editingEntry?.let { entry ->
        EditJournalDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { tag, notes, emotion ->
                viewModel.updateJournalNotes(entry.id, tag, notes, emotion)
                editingEntry = null
            }
        )
    }

    if (showAddDialog) {
        AddManualTradeDialog(
            onDismiss = { showAddDialog = false },
            onSave = { sym, dir, entryP, exitP, lots, pnl, tag, notes, emotion ->
                viewModel.addManualJournalEntry(sym, dir, entryP, exitP, lots, pnl, tag, notes, emotion)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MonoPriceMedium, color = color)
    }
}

@Composable
private fun JournalEntryCard(entry: JournalEntryDoc, onEditClick: () -> Unit) {
    val pnlColor = when {
        entry.status == "OPEN" -> AiCyan
        entry.pnlAmount >= 0 -> BullEmerald
        else -> BearCrimson
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.symbol} • ${entry.direction}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Surface(color = SlateElevated, shape = RoundedCornerShape(6.dp)) {
                        Text(entry.status, style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (entry.status == "OPEN") "ACTIVE" else "${if (entry.pnlAmount >= 0) "+" else ""}$${String.format(Locale.US, "%.2f", entry.pnlAmount)}",
                        style = MonoPriceMedium,
                        color = pnlColor
                    )
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Filled.EditNote, contentDescription = "Edit journal notes", tint = TextSecondary)
                    }
                }
            }
            Text(
                "Lots ${entry.lotSize}  •  Entry ${entry.entryPrice}  •  Setup: ${entry.setupTag.ifBlank { "—" }}  •  Mood: ${entry.emotion}",
                style = MonoPriceSmall,
                color = TextSecondary
            )
            if (entry.notes.isNotBlank()) {
                Text(entry.notes, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}

@Composable
private fun AuditLogCard(log: AuditLogDoc) {
    val dateStr = remember(log.createdAt) {
        val d = log.createdAt?.toDate() ?: Date()
        SimpleDateFormat("MM/dd HH:mm:ss", Locale.US).format(d)
    }
    val badgeColor = when (log.eventType) {
        "RISK_CHECK_BLOCKED" -> BearCrimson
        "ORDER_EXECUTED", "ORDER_CONFIRMED" -> BullEmerald
        "AI_SIGNAL" -> AiCyan
        else -> AmberGold
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = badgeColor.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
                        Text(
                            log.eventType,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (log.biometricVerified) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = "Biometric verified", tint = BullEmerald, modifier = Modifier.size(15.dp))
                    }
                }
                Text(dateStr, style = MonoPriceSmall, color = TextMuted)
            }
            Text(log.summary, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            if (log.metadataSummary.isNotBlank()) {
                Text(log.metadataSummary, style = MonoPriceSmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun EditJournalDialog(
    entry: JournalEntryDoc,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var setupTag by remember { mutableStateOf(entry.setupTag) }
    var notes by remember { mutableStateOf(entry.notes) }
    var emotion by remember { mutableStateOf(entry.emotion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        title = { Text("Review Trade • ${entry.symbol}", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = setupTag, onValueChange = { setupTag = it }, label = { Text("Setup Tag") }, singleLine = true)
                OutlinedTextField(value = emotion, onValueChange = { emotion = it }, label = { Text("Execution Mindset") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Post-Trade Notes") }, minLines = 3)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(setupTag, notes, emotion) },
                colors = ButtonDefaults.buttonColors(containerColor = AiCyan, contentColor = ObsidianBg)
            ) {
                Text("Save Review")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddManualTradeDialog(
    onDismiss: () -> Unit,
    onSave: (MarketSymbol, String, Double, Double, Double, Double, String, String, String) -> Unit
) {
    var symbol by remember { mutableStateOf(MarketSymbol.EURUSD) }
    var direction by remember { mutableStateOf("BUY") }
    var entryText by remember { mutableStateOf("1.0840") }
    var exitText by remember { mutableStateOf("1.0890") }
    var lotsText by remember { mutableStateOf("0.20") }
    var pnlText by remember { mutableStateOf("100.0") }
    var tag by remember { mutableStateOf("EMA Pullback") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MidnightSurface,
        title = { Text("Log Completed Trade", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("BUY", "SELL").forEach { dir ->
                        FilterChip(selected = direction == dir, onClick = { direction = dir }, label = { Text(dir) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = entryText, onValueChange = { entryText = it }, label = { Text("Entry") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = exitText, onValueChange = { exitText = it }, label = { Text("Exit") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = lotsText, onValueChange = { lotsText = it }, label = { Text("Lots") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = pnlText, onValueChange = { pnlText = it }, label = { Text("P&L ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = tag, onValueChange = { tag = it }, label = { Text("Setup Tag") }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        symbol,
                        direction,
                        entryText.toDoubleOrNull() ?: symbol.defaultPrice,
                        exitText.toDoubleOrNull() ?: symbol.defaultPrice,
                        lotsText.toDoubleOrNull() ?: 0.1,
                        pnlText.toDoubleOrNull() ?: 0.0,
                        tag,
                        notes,
                        "Disciplined"
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = BullEmerald, contentColor = ObsidianBg)
            ) {
                Text("Log to Cloud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
