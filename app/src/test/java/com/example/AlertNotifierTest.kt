package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.work.AlertNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric test for the 3.1 notification channel: an Extreme alert
 * must go through a high-importance channel with sound. The notification
 * itself (title, category, deep link) is built by the same object and is
 * verified by inspection — Robolectric's NotificationManager shadow
 * records posted notifications, but the channel is the acceptance
 * criterion the plan names.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertNotifierTest {

    @Test
    fun testChannel_isHighImportanceWithSound() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AlertNotifier.createChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.getNotificationChannel(AlertNotifier.CHANNEL_ID)
        assertNotNull("The alert channel must exist", channel)
        assertEquals(
            "Extreme alerts need a high-importance channel",
            NotificationManager.IMPORTANCE_HIGH,
            channel.importance
        )
        assertNotNull("The channel must carry a sound", channel.sound)
    }
}
