package com.example.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.AlertSeverity
import com.example.domain.model.WeatherAlert

/**
 * Posts 3.1 alert notifications and owns the channel. The channel is
 * high-importance with sound + vibration; Extreme/Severe notifications
 * additionally carry CATEGORY_ALARM. A tap opens the app on the Alerts
 * screen, deep-linked to that alert (MainActivity reads the extras).
 *
 * One notification per alert id (the id's hash is the notification id),
 * so re-notifying the same alert updates its card instead of stacking
 * duplicates. Nothing here invents an alert: the worker only ever passes
 * real NWS alerts, and expired ones never reach this class.
 */
object AlertNotifier {
    const val CHANNEL_ID = "aura_weather_alerts"
    const val EXTRA_DESTINATION = "com.example.extra.DESTINATION"
    const val EXTRA_ALERT_ID = "com.example.extra.ALERT_ID"
    const val DESTINATION_ALERTS = "alerts"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Weather Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Severe weather watches, warnings, and advisories for your selected place."
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                null
            )
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun notify(context: Context, alert: WeatherAlert, placeName: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, DESTINATION_ALERTS)
            putExtra(EXTRA_ALERT_ID, alert.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val category = if (alert.severity == AlertSeverity.Extreme ||
            alert.severity == AlertSeverity.Severe
        ) {
            Notification.CATEGORY_ALARM
        } else {
            Notification.CATEGORY_EVENT
        }
        val title = if (placeName.isNullOrBlank()) alert.event else "${alert.event} — $placeName"
        val text = alert.headline.ifBlank { alert.description }.ifBlank { alert.areaDesc }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alert_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.description.ifBlank { text }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        NotificationManagerCompat.from(context).notify(alert.id.hashCode(), notification)
    }
}
