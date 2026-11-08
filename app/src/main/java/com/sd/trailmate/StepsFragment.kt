package com.sd.trailmate

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class StepsFragment : Fragment() {

    private enum class Range { WEEK, MONTH, THREE_MONTHS, YEAR }

    // One bar: a day, a week or a month
    private class Bucket(
        val label: String,   // text under the bar ("" = none)
        val title: String,   // shown when the bar is tapped
        val value: Int,      // bar height: steps (daily bars) or average steps per day (week/month bars)
        val detail: String
    )

    private lateinit var chart: BarChartView
    private lateinit var tvAvg: TextView
    private lateinit var tvRange: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvDetail: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvBest: TextView
    private lateinit var tvBestLabel: TextView
    private lateinit var btnSample: Button

    private var range = Range.WEEK
    private var buckets: List<Bucket> = emptyList()
    private val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US) // how days are stored

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_steps, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        chart = view.findViewById(R.id.chart)
        tvAvg = view.findViewById(R.id.tvAvg)
        tvRange = view.findViewById(R.id.tvRange)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvDetail = view.findViewById(R.id.tvDetail)
        tvTotal = view.findViewById(R.id.tvTotal)
        tvBest = view.findViewById(R.id.tvBest)
        tvBestLabel = view.findViewById(R.id.tvBestLabel)
        btnSample = view.findViewById(R.id.btnSample)

        chart.onBarSelected = { index -> showBucket(index) }

        val toggle = view.findViewById<MaterialButtonToggleGroup>(R.id.toggleRange)
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            range = when (checkedId) {
                R.id.btnMonth -> Range.MONTH
                R.id.btn3Months -> Range.THREE_MONTHS
                R.id.btnYear -> Range.YEAR
                else -> Range.WEEK
            }
            load()
        }
        toggle.check(R.id.btnWeek)

        btnSample.setOnClickListener { toggleSample() }
        refreshSampleButton()
    }

    private fun n(v: Int) = String.format(Locale.US, "%,d", v)

    private fun showBucket(i: Int) {
        val b = buckets.getOrNull(i) ?: return
        tvTitle.text = b.title
        tvDetail.text = b.detail
    }

    private fun load() {
        val ctx = requireContext()
        val dao = AppDatabase.get(ctx).stepsDao()
        val goal = ctx.getSharedPreferences("trailmate_prefs", Context.MODE_PRIVATE).getInt("goal", 6000)
        val chosen = range
        val today = dayKey.format(Date())

        // First day of the chosen period
        val startCal = Calendar.getInstance()
        when (chosen) {
            Range.WEEK -> startCal.add(Calendar.DAY_OF_YEAR, -6)
            Range.MONTH -> startCal.add(Calendar.DAY_OF_YEAR, -29)
            Range.THREE_MONTHS -> startCal.add(Calendar.DAY_OF_YEAR, -90)   // 91 days = 13 weeks
            Range.YEAR -> {
                startCal.add(Calendar.MONTH, -11)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val start = dayKey.format(startCal.time)

        viewLifecycleOwner.lifecycleScope.launch {
            val saved = dao.between(start, today).associate { it.date to it.steps }
            if (chosen != range) return@launch // the user switched tabs while this was loading

            buckets = buildBuckets(chosen, startCal, today, saved)
            chart.setData(
                buckets.map { BarChartView.Bar(it.label, it.value.toFloat()) },
                goal, buckets.lastIndex
            )

            val total = saved.values.sum()
            val avg = if (saved.isNotEmpty()) total / saved.size else 0 // average over days that have data
            tvAvg.text = n(avg)
            tvTotal.text = n(total)
            tvRange.text = when (chosen) {
                Range.WEEK -> "Last 7 days"
                Range.MONTH -> "Last 30 days"
                Range.THREE_MONTHS -> "Last 13 weeks"
                Range.YEAR -> "Last 12 months"
            }
            val best = saved.maxByOrNull { it.value }
            tvBest.text = if (best == null) "-" else n(best.value)
            tvBestLabel.text = if (best == null) "Best day" else {
                val date = dayKey.parse(best.key)
                "Best day · " + (date?.let { SimpleDateFormat("d MMM", Locale.getDefault()).format(it) } ?: best.key)
            }

            if (saved.isEmpty()) {
                tvTitle.text = "No steps recorded in this period"
                tvDetail.text = "Open the Home screen while walking, or load demo data below"
            } else showBucket(buckets.lastIndex)
        }
    }

    private fun buildBuckets(r: Range, startCal: Calendar, today: String, saved: Map<String, Int>): List<Bucket> {
        val loc = Locale.getDefault()
        val dayName = SimpleDateFormat("EEE", loc)
        val dayMonth = SimpleDateFormat("d MMM", loc)
        val fullDay = SimpleDateFormat("EEE, d MMM yyyy", loc)
        val monthName = SimpleDateFormat("MMM", loc)
        val monthFull = SimpleDateFormat("MMMM yyyy", loc)
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US)
        val out = mutableListOf<Bucket>()
        val c = startCal.clone() as Calendar // work on a copy, so startCal stays unchanged

        when (r) {
            Range.WEEK, Range.MONTH -> {
                var i = 0
                while (dayKey.format(c.time) <= today) {
                    val steps = saved[dayKey.format(c.time)] ?: 0
                    val label = when {
                        r == Range.WEEK -> dayName.format(c.time)
                        i % 7 == 0 -> dayMonth.format(c.time)
                        else -> ""
                    }
                    out.add(Bucket(label, fullDay.format(c.time), steps, n(steps) + " steps"))
                    c.add(Calendar.DAY_OF_YEAR, 1)
                    i++
                }
            }
            Range.THREE_MONTHS -> {
                // One bar per week: height = average steps per day (days with data only)
                var weekStart = c.time
                var total = 0
                var counted = 0
                var daysInWeek = 0
                var weekIndex = 0
                while (dayKey.format(c.time) <= today) {
                    saved[dayKey.format(c.time)]?.let { total += it; counted++ }
                    daysInWeek++
                    if (daysInWeek == 7 || dayKey.format(c.time) == today) {
                        val avg = if (counted > 0) total / counted else 0
                        val label = if (weekIndex % 4 == 0) dayMonth.format(weekStart) else ""
                        val title = dayMonth.format(weekStart) + " – " + dayMonth.format(c.time)
                        out.add(Bucket(label, title, avg, "avg " + n(avg) + " steps / day"))
                        c.add(Calendar.DAY_OF_YEAR, 1)
                        weekStart = c.time
                        total = 0; counted = 0; daysInWeek = 0; weekIndex++
                    } else {
                        c.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
            Range.YEAR -> {
                // One bar per calendar month: height = average steps per day (days with data only)
                for (m in 0 until 12) {
                    val prefix = monthKey.format(c.time)
                    var total = 0
                    var counted = 0
                    for ((day, steps) in saved) {
                        if (day.startsWith(prefix)) { total += steps; counted++ }
                    }
                    val avg = if (counted > 0) total / counted else 0
                    val label = if ((11 - m) % 2 == 0) monthName.format(c.time) else "" // every other month, ending with this one
                    out.add(Bucket(label, monthFull.format(c.time), avg, "avg " + n(avg) + " steps / day"))
                    c.add(Calendar.MONTH, 1)
                }
            }
        }
        return out
    }

    // ---------- demo data (clearly labelled, removable) ----------
    private fun toggleSample() {
        val dao = AppDatabase.get(requireContext()).stepsDao()
        viewLifecycleOwner.lifecycleScope.launch {
            if (dao.sampleCount() > 0) dao.deleteSamples() else seedSample(dao)
            refreshSampleButton()
            load()
        }
    }

    private fun refreshSampleButton() {
        val dao = AppDatabase.get(requireContext()).stepsDao()
        viewLifecycleOwner.lifecycleScope.launch {
            btnSample.text = if (dao.sampleCount() > 0) "Remove demo data" else "Load demo data"
        }
    }

    private suspend fun seedSample(dao: StepsDao) {
        val rnd = Random(7)             // fixed seed: the same demo data every time
        val cal = Calendar.getInstance()
        val days = ArrayList<DailySteps>()
        for (i in 1..365) {
            cal.add(Calendar.DAY_OF_YEAR, -1)   // yesterday, the day before, ...
            days.add(DailySteps(dayKey.format(cal.time), 2500 + rnd.nextInt(9500), 1))
        }
        dao.insertAllIgnore(days)
    }
}