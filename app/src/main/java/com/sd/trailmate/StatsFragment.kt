package com.sd.trailmate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Locale

class StatsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_stats, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val dao = AppDatabase.get(requireContext()).hikeDao()

        lifecycleScope.launch {
            val count = dao.count()
            val distance = dao.totalDistance()
            val duration = dao.totalDuration()
            val longest = dao.longest()
            // Guard against dividing by zero when there are no hikes yet
            val avg = if (count > 0) distance / count else 0.0
            val hours = duration / 3600.0
            val speed = if (hours > 0) distance / hours else 0.0

            fun set(id: Int, text: String) { view.findViewById<TextView>(id).text = text }
            set(R.id.tvTotalHikes, "$count")
            set(R.id.tvTotalDistance, String.format(Locale.US, "%.2f km", distance))
            set(R.id.tvTotalTime, Utils.formatDuration(duration))
            set(R.id.tvAvgDistance, String.format(Locale.US, "%.2f km", avg))
            set(R.id.tvLongest, String.format(Locale.US, "%.2f km", longest))
            set(R.id.tvAvgSpeed, String.format(Locale.US, "%.1f km/h", speed))
        }
    }
}