package com.sd.trailmate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HikeAdapter(private val onClick: (Hike) -> Unit) :
    ListAdapter<Hike, HikeAdapter.VH>(Diff) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.tvName)
        val date: TextView = v.findViewById(R.id.tvDate)
        val summary: TextView = v.findViewById(R.id.tvSummary)
        val thumb: RouteThumbView = v.findViewById(R.id.routeThumb)
    }

    // DiffUtil lets the list animate only the rows that actually changed
    object Diff : DiffUtil.ItemCallback<Hike>() {
        override fun areItemsTheSame(a: Hike, b: Hike) = a.id == b.id
        override fun areContentsTheSame(a: Hike, b: Hike) = a == b
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hike, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val hike = getItem(position)
        holder.name.text = hike.name
        holder.date.text =
            if (hike.startPlace.isNotBlank()) "${hike.date}  •  ${hike.startPlace}" else hike.date
        val steps = if (hike.steps > 0) String.format(Locale.US, "  |  %,d steps", hike.steps) else ""
        holder.summary.text = String.format(
            Locale.US, "%.2f km  |  %s",
            hike.distanceKm, Utils.formatDuration(hike.durationSec)
        ) + steps
        val route = Utils.parseRoute(hike.routePoints)
        holder.thumb.visibility = if (route.size >= 2) View.VISIBLE else View.GONE // no route, no preview
        holder.thumb.setRoute(route)
        holder.itemView.setOnClickListener { onClick(hike) }
    }
}