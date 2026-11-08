package com.sd.trailmate

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.util.GeoPoint
import java.io.File
import java.util.Locale

// Owns the hike that is being recorded: GPS, timer, steps and the route.
// It is an `object` (one copy for the whole app), so it keeps working when the Record screen
// is closed, and the foreground service (TrackingService) keeps the app alive in the background.
object Recorder {

    enum class State { IDLE, RECORDING, PAUSED }

    // The Record screen implements this to get live updates while it is open
    interface Listener {
        fun onPoint(p: GeoPoint)
        fun onTick()
        fun onStateChanged()
    }

    var listener: Listener? = null

    var state = State.IDLE
        private set
    val points = mutableListOf<GeoPoint>()
    var distanceMeters = 0.0
        private set
    var elapsedSec = 0L
        private set

    // Form data, kept here so it survives leaving the screen
    var name = ""
    var notes = ""
    var date = ""
    var photoPath = ""
    var photoFile: File? = null

    private var stepsDone = 0        // steps from finished stretches (before pauses)
    private var stepBase = -1f       // sensor total when the current stretch began (-1 = not read yet)
    private var stepLast = 0f        // most recent sensor total
    private var stepsAvailable = false

    private lateinit var app: Context
    private var fused: FusedLocationProviderClient? = null
    private var sensorManager: SensorManager? = null
    private var lastLocation: Location? = null
    private var segmentStart = 0L
    private var accumulatedSec = 0L
    private val handler = Handler(Looper.getMainLooper())

    // ---------- timer: runs every second while recording ----------
    private val ticker = object : Runnable {
        override fun run() {
            elapsedSec = accumulatedSec + (SystemClock.elapsedRealtime() - segmentStart) / 1000
            listener?.onTick()
            if (elapsedSec % 5 == 0L) TrackingService.refresh(app)   // keep the notification fresh
            handler.postDelayed(this, 1000)
        }
    }

    // ---------- GPS ----------
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            // Ignore inaccurate readings (worse than 25 m) so the line doesn't zigzag
            if (loc.hasAccuracy() && loc.accuracy > 25f) return
            // Ignore jitter: standing still, GPS wanders a few metres. A real move is 5 m or more
            val prev = lastLocation
            if (prev != null && prev.distanceTo(loc) < 5f) return

            if (prev != null) distanceMeters += prev.distanceTo(loc)
            lastLocation = loc

            val gp = GeoPoint(loc.latitude, loc.longitude)
            points.add(gp)
            listener?.onPoint(gp)
            TrackingService.refresh(app)
        }
    }

    private fun client(ctx: Context): FusedLocationProviderClient =
        fused ?: LocationServices.getFusedLocationProviderClient(ctx).also { fused = it }

    // Returns null when GPS updates started, or a message to show the user
    @SuppressLint("MissingPermission") // the screen checks the permission before calling start()
    private fun beginUpdates(ctx: Context): String? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
            return "GPS is turned off. Please enable Location and try again."
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        return try {
            client(ctx).requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            null
        } catch (e: SecurityException) {
            "Could not access location: permission denied"
        }
    }

    // ---------- steps ----------
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val total = event.values[0]                 // steps since the phone last restarted
            if (stepBase < 0f) stepBase = total         // first reading of this stretch = starting point
            stepLast = total
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun canCountSteps(ctx: Context): Boolean {
        val allowed = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return allowed && sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    // Steps so far: finished stretches + the stretch in progress
    fun currentSteps(): Int {
        val running = if (stepBase >= 0f) (stepLast - stepBase).toInt() else 0
        return stepsDone + running.coerceAtLeast(0)
    }

    private fun registerStepSensor() {
        if (!stepsAvailable) return
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sensorManager = sm
        sm.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    // On pause and stop: save this stretch's steps and stop listening
    private fun bankSteps() {
        if (stepBase >= 0f) stepsDone += (stepLast - stepBase).toInt().coerceAtLeast(0)
        stepBase = -1f
        sensorManager?.unregisterListener(stepListener)
    }

    // ---------- controls ----------
    // Each returns null on success, or an error message for the screen to show
    fun start(ctx: Context): String? {
        app = ctx.applicationContext
        beginUpdates(app)?.let { return it }
        // A fresh start clears any earlier recording
        points.clear()
        distanceMeters = 0.0
        lastLocation = null
        accumulatedSec = 0
        elapsedSec = 0
        segmentStart = SystemClock.elapsedRealtime()
        stepsDone = 0; stepBase = -1f; stepLast = 0f
        stepsAvailable = canCountSteps(app)
        registerStepSensor()
        state = State.RECORDING
        TrackingService.start(app)            // keeps the app alive when the screen turns off
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        listener?.onStateChanged()
        return null
    }

    fun pause() {
        if (state != State.RECORDING) return
        client(app).removeLocationUpdates(locationCallback)
        handler.removeCallbacks(ticker)
        // Bank the time of this stretch so the timer can continue from here later
        accumulatedSec += (SystemClock.elapsedRealtime() - segmentStart) / 1000
        elapsedSec = accumulatedSec
        lastLocation = null // so the walk between pause and resume isn't counted as distance
        bankSteps()
        state = State.PAUSED
        TrackingService.refresh(app)
        listener?.onStateChanged()
    }

    fun resume(): String? {
        if (state != State.PAUSED) return null
        beginUpdates(app)?.let { return it }
        segmentStart = SystemClock.elapsedRealtime()
        stepBase = -1f      // the first reading after resuming is the new starting point
        registerStepSensor()
        state = State.RECORDING
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        TrackingService.refresh(app)
        listener?.onStateChanged()
        return null
    }

    // Ends the recording but keeps the route, so it can still be saved
    fun stop() {
        if (state == State.IDLE) return
        if (state == State.RECORDING) {
            accumulatedSec += (SystemClock.elapsedRealtime() - segmentStart) / 1000
        }
        client(app).removeLocationUpdates(locationCallback)
        handler.removeCallbacks(ticker)
        elapsedSec = accumulatedSec
        bankSteps()
        state = State.IDLE
        TrackingService.stop(app)
        listener?.onStateChanged()
    }

    // Forgets everything: used after saving, discarding, or when the app is swiped away
    fun reset() {
        stop()
        points.clear()
        distanceMeters = 0.0
        lastLocation = null
        accumulatedSec = 0
        elapsedSec = 0
        stepsDone = 0; stepBase = -1f; stepLast = 0f
        name = ""; notes = ""; date = ""
        photoPath = ""; photoFile = null
    }

    // ---------- text for the notification ----------
    fun notifTitle() = if (state == State.PAUSED) "Hike paused" else "Recording hike"

    fun notifText(): String {
        val base = String.format(
            Locale.US, "%.2f km  |  %s", distanceMeters / 1000.0, Utils.formatDuration(elapsedSec)
        )
        return if (stepsAvailable) base + String.format(Locale.US, "  |  %,d steps", currentSteps()) else base
    }
}