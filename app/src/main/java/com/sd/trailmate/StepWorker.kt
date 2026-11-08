package com.sd.trailmate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class StepWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) return Result.success()

        val total = readStepCounter(ctx) ?: return Result.success()

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val log = ctx.getSharedPreferences("steplog", Context.MODE_PRIVATE)
        val lastDate = log.getString("date", "")
        val lastTotal = log.getFloat("lastTotal", -1f)
        var base = log.getFloat("base", total)
        var offset = log.getInt("offset", 0)

        if (lastDate != today) {
            // new day: start counting from the last reading we saw (or now, on first run)
            base = if (lastTotal >= 0f && lastTotal <= total) lastTotal else total
            offset = 0
        } else if (total < lastTotal) {
            // phone rebooted, sensor restarted from 0: bank what we had
            offset += (lastTotal - base).toInt().coerceAtLeast(0)
            base = 0f
        }
        val steps = offset + (total - base).toInt().coerceAtLeast(0)

        log.edit().putString("date", today).putFloat("lastTotal", total)
            .putFloat("base", base).putInt("offset", offset).apply()

        // save only if it's higher than what the Home screen already stored today
        val dao = AppDatabase.get(ctx).stepsDao()
        val existing = dao.between(today, today).firstOrNull()?.steps ?: 0
        if (steps > existing) dao.save(DailySteps(today, steps))

        notifyIfNeeded(ctx, steps, today)
        return Result.success()
    }

    private suspend fun readStepCounter(ctx: Context): Float? {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine { cont ->
                val l = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0])
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                }
                sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(l) }
            }
        }
    }

    private fun notifyIfNeeded(ctx: Context, steps: Int, today: String) {
        val prefs = ctx.getSharedPreferences("trailmate_prefs", Context.MODE_PRIVATE)
        val goal = prefs.getInt("goal", 8000)
        val log = ctx.getSharedPreferences("steplog", Context.MODE_PRIVATE)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        if (steps >= goal && log.getString("goalNotified", "") != today) {
            show(ctx, 101, "Goal reached! 🎉", "You've walked $steps steps today. Nice work.")
            log.edit().putString("goalNotified", today).apply()
        } else if (steps < goal && hour in 19..21 && log.getString("reminded", "") != today) {
            show(ctx, 102, "Almost there", "${goal - steps} steps to go for today's goal. A short walk will do it.")
            log.edit().putString("reminded", today).apply()
        }
    }

    private fun show(ctx: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("goals", "Daily goals", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, "goals")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true).build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }
}