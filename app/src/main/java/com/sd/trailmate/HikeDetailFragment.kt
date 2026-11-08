package com.sd.trailmate

import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider

class HikeDetailFragment : Fragment() {

    private lateinit var map: MapView
    private var currentHike: Hike? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // osmdroid needs a user agent, or the tile server may block the app
        Configuration.getInstance().load(
            requireContext(),
            requireContext().getSharedPreferences("osm", 0)
        )
        Configuration.getInstance().userAgentValue = requireContext().packageName
        return inflater.inflate(R.layout.fragment_hike_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        map = view.findViewById(R.id.mapDetail)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        val hikeId = arguments?.getInt("hikeId", -1) ?: -1
        val dao = AppDatabase.get(requireContext()).hikeDao()

        lifecycleScope.launch {
            val hike = dao.getById(hikeId)
            if (hike == null) {
                Toast.makeText(requireContext(), "Hike not found", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return@launch
            }
            currentHike = hike
            showHike(view, hike)
        }

        view.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            val hike = currentHike ?: return@setOnClickListener
            // Ask before deleting so a stray tap can't wipe a hike
            AlertDialog.Builder(requireContext())
                .setTitle("Delete hike?")
                .setMessage("\"${hike.name}\" will be permanently removed.")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        dao.delete(hike)
                        findNavController().popBackStack()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        view.findViewById<Button>(R.id.btnEditNotes).setOnClickListener { editNotes() }

        // Share a picture of the hike, or export its route as a GPX file
        view.findViewById<Button>(R.id.btnShareCard).setOnClickListener {
            currentHike?.let { HikeExporter.shareCard(requireContext(), it) }
        }
        view.findViewById<Button>(R.id.btnExportGpx).setOnClickListener {
            currentHike?.let { HikeExporter.shareGpx(requireContext(), it) }
        }
    }

    override fun onDestroyView() {
        map.onDetach() // releases tile-loading threads and memory
        super.onDestroyView()
    }

    private fun showHike(view: View, hike: Hike) {
        view.findViewById<TextView>(R.id.tvDetailName).text = hike.name
        view.findViewById<TextView>(R.id.tvDetailInfo).text = String.format(
            Locale.US, "%s  |  %.2f km  |  %s",
            hike.date, hike.distanceKm, Utils.formatDuration(hike.durationSec)
        ) + ((if (hike.steps > 0) String.format(Locale.US, "  |  %,d steps", hike.steps) else ""))

        if (hike.startPlace.isNotBlank() || hike.endPlace.isNotBlank()) {
            view.findViewById<TextView>(R.id.tvDetailPlaces).text =
                "From ${hike.startPlace.ifBlank { "?" }} to ${hike.endPlace.ifBlank { "?" }}"
        }
        showNotes(hike)

        // Photo: decode at 1/4 size to avoid out-of-memory errors; fail quietly if missing
        if (hike.photoPath.isNotBlank() && File(hike.photoPath).exists()) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = BitmapFactory.decodeFile(hike.photoPath, opts)
                val iv = view.findViewById<ImageView>(R.id.ivDetailPhoto)
                iv.setImageBitmap(bmp)
                iv.visibility = View.VISIBLE
                iv.setOnClickListener { openPhoto(hike.photoPath) }
            } catch (e: Exception) {
                // photo unreadable: just don't show it
            }
        }

        // Route: draw the saved GPS points as a line, with start and end markers
        val points = Utils.parseRoute(hike.routePoints)
        if (points.isEmpty()) {
            Toast.makeText(requireContext(), "No route recorded for this hike", Toast.LENGTH_SHORT).show()
            return
        }
        val line = Polyline().apply {
            setPoints(points)
            outlinePaint.color = 0xFF1E88E5.toInt()
            outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(line)
        map.overlays.add(Marker(map).apply { position = points.first(); title = "Start" })
        map.overlays.add(Marker(map).apply { position = points.last(); title = "End" })

        // Wait until the map has a size, then zoom to fit the whole route
        map.post {
            val border = 80 // pixels of padding around the route
            if (points.size > 1 && map.width > 2 * border && map.height > 2 * border) {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, border)
                if (map.zoomLevelDouble > 18.0) map.controller.setZoom(18.0)
            } else {
                // Map too small to fit the route, or only one point: just centre on the start
                map.controller.setZoom(17.0)
                map.controller.setCenter(points.first())
            }
        }
    }

    // Shows the notes line and switches the button between "Add notes" and "Edit notes"
    private fun showNotes(hike: Hike) {
        val v = view ?: return
        v.findViewById<TextView>(R.id.tvDetailNotes).text =
            if (hike.notes.isBlank()) "" else "Notes: ${hike.notes}"
        v.findViewById<Button>(R.id.btnEditNotes).text =
            if (hike.notes.isBlank()) "Add notes" else "Edit notes"
    }

    private fun editNotes() {
        val hike = currentHike ?: return
        val input = EditText(requireContext()).apply {
            setText(hike.notes)
            setSelection(text.length) // cursor at the end
            hint = "Notes"
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            filters = arrayOf(InputFilter.LengthFilter(300)) // validation: max 300 characters
        }
        val pad = (20 * resources.displayMetrics.density).toInt() // 20 dp in pixels
        val box = FrameLayout(requireContext()).apply {
            setPadding(pad, 8, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit notes")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val newNotes = input.text.toString().trim()
                lifecycleScope.launch {
                    try {
                        val updated = hike.copy(notes = newNotes) // same hike, only notes changed
                        AppDatabase.get(requireContext()).hikeDao().update(updated)
                        currentHike = updated
                        showNotes(updated)
                        Toast.makeText(requireContext(), "Notes updated", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Could not update notes", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Opens the photo in the phone's own image viewer (Photos / Gallery)
    private fun openPhoto(path: String) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", File(path)
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/jpeg")                  // what kind of file it is
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)    // lets the viewer read our private file
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "No app found to open photos", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Could not open the photo", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }
}