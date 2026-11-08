package com.sd.trailmate

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

class HikeListFragment : Fragment(), SensorEventListener {

    private lateinit var progress: CircularProgressIndicator
    private lateinit var tvSteps: TextView
    private lateinit var tvGoal: TextView
    private lateinit var tvHint: TextView
    private lateinit var prefs: SharedPreferences

    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null
    private var todaySteps = 0
    private var goal = 6000

    private var lastSaved = -1

    // Asks for the activity-recognition permission (needed for steps on Android 10+)
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) registerStepSensor()
            else tvHint.text = "Allow activity permission to count steps"
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_hike_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvHikes)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val nav = findNavController()

        val adapter = HikeAdapter { hike ->
            nav.navigate(R.id.action_list_to_detail, bundleOf("hikeId" to hike.id))
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.fabAdd)
            .setOnClickListener { nav.navigate(R.id.action_list_to_add) }
        view.findViewById<Button>(R.id.btnStats)
            .setOnClickListener { nav.navigate(R.id.action_list_to_stats) }

        val seeAll = view.findViewById<Button>(R.id.btnSeeAll)
        seeAll.setOnClickListener { nav.navigate(R.id.action_list_to_all) }

        AppDatabase.get(requireContext()).hikeDao().getRecent()
            .observe(viewLifecycleOwner) { hikes ->
                adapter.submitList(hikes)
                tvEmpty.visibility = if (hikes.isEmpty()) View.VISIBLE else View.GONE
                seeAll.visibility = if (hikes.isEmpty()) View.GONE else View.VISIBLE
            }

        // ---- steps ring ----
        progress = view.findViewById(R.id.progressSteps)
        tvSteps = view.findViewById(R.id.tvSteps)
        tvGoal = view.findViewById(R.id.tvStepGoal)
        tvHint = view.findViewById(R.id.tvStepHint)
        prefs = requireContext().getSharedPreferences("trailmate_prefs", Context.MODE_PRIVATE)
        goal = prefs.getInt("goal", 6000)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        view.findViewById<View>(R.id.cardSteps).setOnClickListener { editGoal() }
        updateRing()

        if (!hasStepPermission()) permLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    private fun hasStepPermission(): Boolean =
        Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED

    private fun registerStepSensor() {
        if (stepSensor == null) {
            tvHint.text = "Step counter not available on this device"
            return
        }
        // SENSOR_DELAY_UI = update rate suited to showing numbers on screen
        sensorManager?.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        tvHint.text = "Tap to change your goal"
    }

    // Called each time the phone reports a new step total
    override fun onSensorChanged(event: SensorEvent) {
        val total = event.values[0].toLong() // steps since the phone last restarted
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        var base = prefs.getLong("baseSteps", -1L)

        if (prefs.getString("baseDate", "") != today || base < 0) {
            // First reading today: remember it so "today" starts from zero
            base = total
            prefs.edit().putString("baseDate", today).putLong("baseSteps", base).apply()
        } else if (total < base) {
            // The phone restarted, so the sensor counter went back to 0
            base = 0
            prefs.edit().putLong("baseSteps", 0).apply()
        }
        todaySteps = (total - base).toInt()
        updateRing()
        // Saving today's total so the history chart can show it (only when it changed)
        if (todaySteps != lastSaved) {
            lastSaved = todaySteps
            viewLifecycleOwner.lifecycleScope.launch {
                AppDatabase.get(requireContext()).stepsDao().save(DailySteps(today, todaySteps))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateRing() {
        tvSteps.text = String.format(Locale.US, "%,d", todaySteps)
        tvGoal.text = String.format(Locale.US, "Goal: %,d steps", goal)
        // setProgressCompat(value, animated): fills the ring from 0 to 100 percent
        progress.setProgressCompat(min(100, todaySteps * 100 / goal), true)
    }

    private fun editGoal() {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(6)) // max 6 digits, so it can't overflow
            setText(goal.toString())
            setSelection(text.length)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Daily step goal")
            .setView(input)
            .setPositiveButton("Save", null)   // real handler is set below, so the dialog can stay open
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim().toIntOrNull()
                if (value == null || value < 1000 || value > 100000) {
                    input.error = "Enter 1,000 to 100,000" // dialog stays open
                } else {
                    goal = value
                    prefs.edit().putInt("goal", value).apply()
                    updateRing()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    // Listen only while the screen is visible, to save battery
    override fun onResume() {
        super.onResume()
        if (hasStepPermission()) registerStepSensor()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }
}