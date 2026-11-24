package com.example.security

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

class TradingNotificationHelper(private val context: Context) {

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val executionChannel = NotificationChannel(
                CHANNEL_EXECUTION,
                "Order Fills & Executions",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for MT5 order fills, SL/TP triggers, and margin calls"
            }
            val signalsChannel = NotificationChannel(
                CHANNEL_SIGNALS,
                "AI Signals & Market Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Chart confluence and AI setup notifications"
            }
            manager.createNotificationChannel(executionChannel)
            manager.createNotificationChannel(signalsChannel)
        }
    }

    fun sendNotification(
        title: String,
        body: String,
        isCritical: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = if (isCritical) CHANNEL_EXECUTION else CHANNEL_SIGNALS
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (isCritical) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(
            (System.currentTimeMillis() and 0xFFFFFFF).toInt(),
            notification
        )
    }

    private companion object {
        const val CHANNEL_EXECUTION = "apex_execution_alerts"
        const val CHANNEL_SIGNALS = "apex_signal_alerts"
    }
}
