package com.brit.swiftinstaller.utils

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import com.brit.swiftinstaller.library.R
import com.brit.swiftinstaller.ui.activities.RebootActivity

class EnableOverlaysActivity : Activity() {

    private var notificationManager: NotificationManager? = null

    override fun onResume() {
        super.onResume()

        val shouldNotify = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("should_notify", false)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        Utils.enableAllOverlays(this)
        if (shouldNotify) {
            sendNotification()
            PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("should_notify", false).apply()
        }
        finish()
    }

    private fun sendNotification() {

        val notificationID = 101
        val rebootIntent = Intent(this, RebootActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                rebootIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelID = "com.brit.swiftinstaller"

        val notification = Notification.Builder(this,
                channelID)
                .setContentTitle(getString(R.string.reboot_notif_title))
                .setStyle(Notification.BigTextStyle()
                        .bigText(getString(R.string.reboot_notif_msg)))
                .setSmallIcon(R.drawable.notif)
                .setChannelId(channelID)
                .setContentIntent(pendingIntent)
                .build()

        notificationManager?.notify(notificationID, notification)
    }
}