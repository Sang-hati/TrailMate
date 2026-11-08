package com.sd.trailmate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AllHikesFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_all_hikes, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvAll)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyAll)

        val adapter = HikeAdapter { hike ->
            findNavController().navigate(R.id.action_all_to_detail, bundleOf("hikeId" to hike.id))
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Every hike, newest first
        AppDatabase.get(requireContext()).hikeDao().getAll()
            .observe(viewLifecycleOwner) { hikes ->
                adapter.submitList(hikes)
                tvEmpty.visibility = if (hikes.isEmpty()) View.VISIBLE else View.GONE
            }
    }
}