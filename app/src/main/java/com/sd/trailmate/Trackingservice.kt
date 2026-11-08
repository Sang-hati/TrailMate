package com.sd.trailmate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

// Keeps the app's process alive while a hike is being recorded and shows the notification.
// The recording itself is done by Recorder.
class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "tracking_v2"   // new id: Android remembers a channel's settings once it exists
        const val NOTIF_ID = 201
        const val ACTION_TOGGLE = "com.sd.trailmate.ACTION_TOGGLE"   // pause / resume
        const val ACTION_STOP = "com.sd.trailmate.ACTION_STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, TrackingService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TrackingService::class.java))
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        }

        // Re-draws the notification from the Recorder's current numbers
        fun refresh(ctx: Context) {
            if (Recorder.state == Recorder.State.IDLE) return
            ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, build(ctx))
        }

        private fun broadcast(ctx: Context, action: String, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                ctx, requestCode,
                Intent(action).setPackage(ctx.packageName),   // explicit: only our own app receives it
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun build(ctx: Context): Notification {
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "Hike recording", NotificationManager.IMPORTANCE_LOW
                ).apply { lockscreenVisibility = Notification.VISIBILITY_PUBLIC }   // show on the lock screen
                ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
            // Tapping the notification reopens the running app on the Record Hike screen.
            // SINGLE_TOP + CLEAR_TOP reuse the open MainActivity instead of making a new one.
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN_RECORD, true)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val paused = Recorder.state == Recorder.State.PAUSED
            return NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(Recorder.notifTitle())
                .setContentText(Recorder.notifText())
                .setContentIntent(open)
                .setOngoing(true)          // can't be swiped away while recording
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)   // full text on the lock screen
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)    // updates don't buzz or pop up again
                .addAction(0, if (paused) "Resume" else "Pause", broadcast(ctx, ACTION_TOGGLE, 1))
                .addAction(0, "Stop", broadcast(ctx, ACTION_STOP, 2))
                .build()
        }
    }

    // Pause/Resume and Stop buttons on the notification arrive here, so they work
    // even when the Record screen is closed
    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TOGGLE -> when (Recorder.state) {
                    Recorder.State.RECORDING -> Recorder.pause()
                    Recorder.State.PAUSED -> Recorder.resume()
                    Recorder.State.IDLE -> {}
                }
                ACTION_STOP -> Recorder.stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this, actionReceiver,
            IntentFilter().apply { addAction(ACTION_TOGGLE); addAction(ACTION_STOP) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        try { unregisterReceiver(actionReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = build(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, n)
        }
        return START_NOT_STICKY   // if Android kills the app, don't restart an empty service
    }

    // The user swiped the app out of Recents: end the recording
    override fun onTaskRemoved(rootIntent: Intent?) {
        Recorder.reset()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }
}