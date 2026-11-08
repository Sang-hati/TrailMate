package com.sd.trailmate

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Calendar
import java.util.Locale
import com.sd.trailmate.Recorder.State

class AddHikeFragment : Fragment() {

    private lateinit var map: MapView
    private lateinit var tvLive: TextView
    private lateinit var btnStartStop: Button
    private lateinit var btnPause: Button
    private lateinit var etName: EditText
    private lateinit var etDate: EditText
    private lateinit var etNotes: EditText
    private lateinit var ivThumb: ImageView
    private lateinit var routeLine: Polyline
    private lateinit var meMarker: Marker
    private lateinit var tilName: TextInputLayout
    private var stepsOk = false   // true when the phone has a step counter and the permission is granted

    // The recording lives in Recorder, so closing this screen doesn't stop it.
    // These shortcuts keep the rest of the file readable.
    private val points get() = Recorder.points
    private val state: State get() = Recorder.state
    private var photoFile: File?       // file the camera is writing into
        get() = Recorder.photoFile
        set(v) { Recorder.photoFile = v }
    private var photoPath: String      // photo that will be saved with the hike
        get() = Recorder.photoPath
        set(v) { Recorder.photoPath = v }

    // Recorder calls these while this screen is open
    private val recListener = object : Recorder.Listener {
        override fun onPoint(p: GeoPoint) {
            routeLine.addPoint(p)
            meMarker.position = p
            meMarker.isEnabled = true
            if (points.size == 1) map.controller.setZoom(17.0)
            map.controller.animateTo(p)
            map.invalidate()
            updateLive()
        }
        override fun onTick() = updateLive()
        override fun onStateChanged() { updateLive(); updateButtons() }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startTracking()
            else toast("Location permission is needed to record a hike")
        }

    // TakePicture: the camera app writes the photo into the Uri we give it, then reports true/false
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val file = photoFile
            if (ok && file != null && file.exists() && file.length() > 0) {
                // A retake replaces the earlier photo
                if (photoPath.isNotBlank() && photoPath != file.absolutePath) File(photoPath).delete()
                photoPath = file.absolutePath
                showThumb()
            } else {
                file?.delete()
                toast("No photo was taken")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().load(
            requireContext(), requireContext().getSharedPreferences("osm", 0)
        )
        Configuration.getInstance().userAgentValue = requireContext().packageName
        return inflater.inflate(R.layout.fragment_add_hike, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        stepsOk = Recorder.canCountSteps(requireContext())
        map = view.findViewById(R.id.map)
        tvLive = view.findViewById(R.id.tvLive)
        btnStartStop = view.findViewById(R.id.btnStartStop)
        btnPause = view.findViewById(R.id.btnPause)
        etName = view.findViewById(R.id.etName)
        tilName = etName.parent.parent as TextInputLayout
        etDate = view.findViewById(R.id.etDate)
        etNotes = view.findViewById(R.id.etNotes)
        ivThumb = view.findViewById(R.id.ivThumb)

        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(5.0)
        map.controller.setCenter(GeoPoint(20.59, 78.96)) // India, until the first GPS fix

        routeLine = Polyline().apply {
            outlinePaint.color = 0xFF1E88E5.toInt()
            outlinePaint.strokeWidth = 10f
        }
        map.overlays.add(routeLine)
        meMarker = Marker(map).apply {
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_me_dot)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER) // centre the dot on the position
            setInfoWindow(null)   // no popup bubble when tapped
            isEnabled = false     // hidden until the first GPS fix
        }
        map.overlays.add(meMarker)

        // Put back what was typed before (the Recorder keeps it while this screen is closed)
        if (Recorder.name.isNotEmpty()) etName.setText(Recorder.name)
        if (Recorder.notes.isNotEmpty()) etNotes.setText(Recorder.notes)
        if (Recorder.date.isNotEmpty()) etDate.setText(Recorder.date) else setTodayAsDate()
        // ...and keep it saved from now on
        etName.doOnTextChanged { text, _, _, _ ->
            tilName.error = null                       // clear the error once they type
            Recorder.name = text?.toString() ?: ""
        }
        etNotes.doOnTextChanged { text, _, _, _ -> Recorder.notes = text?.toString() ?: "" }
        etDate.setOnClickListener { pickDate() }

        btnStartStop.setOnClickListener {
            if (state == State.IDLE) requestStart() else Recorder.stop()
        }
        btnPause.setOnClickListener {
            if (state == State.RECORDING) Recorder.pause()
            else Recorder.resume()?.let { toast(it) }
        }
        view.findViewById<Button>(R.id.btnPhoto).setOnClickListener { launchCamera() }
        view.findViewById<Button>(R.id.btnSave).setOnClickListener { saveHike() }
        view.findViewById<Button>(R.id.btnDiscard).setOnClickListener { discard() }

        // If a hike is already being recorded (screen was closed and reopened, or rotated), redraw it
        if (points.isNotEmpty()) {
            routeLine.setPoints(points.toList())  // toList() = a copy, so the two lists stay separate
            val last = points.last()
            meMarker.position = last
            meMarker.isEnabled = true
            map.controller.setZoom(17.0)
            map.controller.setCenter(last)
        }
        if (photoPath.isNotBlank()) showThumb()
        updateButtons()
        updateLive()
        Recorder.listener = recListener   // start receiving live updates
    }

    private fun toast(msg: String) =
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

    // ---------- date ----------
    private fun setTodayAsDate() {
        val c = Calendar.getInstance()
        etDate.setText(
            String.format(
                Locale.US, "%02d-%02d-%d",
                c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR)
            )
        )
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        val dialog = DatePickerDialog(
            requireContext(),
            { _, y, m, d ->
                etDate.setText(String.format(Locale.US, "%02d-%02d-%d", d, m + 1, y))
                Recorder.date = etDate.text.toString()
                etDate.error = null
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.maxDate = System.currentTimeMillis() // no future dates
        dialog.show()
    }

    // ---------- tracking (the work is done by Recorder) ----------
    private fun requestStart() {
        val name = etName.text.toString().trim()
        if (name.length < 3) {
            tilName.error = if (name.isEmpty()) "Enter a hike name before starting"
            else "Name must be at least 3 characters"
            etName.requestFocus()
            toast("Give your hike a name first")
            return
        }

        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startTracking()
        else permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun startTracking() {
        val error = Recorder.start(requireContext())
        if (error != null) { toast(error); return }
        routeLine.setPoints(emptyList<GeoPoint>())
        meMarker.isEnabled = false
        tvLive.text = "Waiting for GPS signal..."
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
        etName.clearFocus()
        etNotes.clearFocus()
    }

    private fun updateButtons() {
        btnStartStop.text = if (state == State.IDLE) "Start" else "Stop"
        btnPause.visibility = if (state == State.IDLE) View.GONE else View.VISIBLE
        btnPause.text = if (state == State.PAUSED) "Resume" else "Pause"
    }

    private fun updateLive() {
        val main = String.format(
            Locale.US, "%.2f km  |  %s",
            Recorder.distanceMeters / 1000.0, Utils.formatDuration(Recorder.elapsedSec)
        )
        if (!stepsOk) { tvLive.text = main; return }
        val text =
            SpannableString(main + "\n" + String.format(Locale.US, "%,d steps", Recorder.currentSteps()))
        // The second line is drawn at 55% of the size of the first
        text.setSpan(RelativeSizeSpan(0.55f), main.length + 1, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvLive.text = text
    }
    // ---------- discard ----------
    private fun discard() {
        val hasData = state != State.IDLE || points.isNotEmpty()
        if (!hasData) {
            findNavController().popBackStack()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Discard this hike?")
            .setMessage("The recorded trail will not be saved.")
            .setPositiveButton("Discard") { _, _ ->
                if (photoPath.isNotBlank()) File(photoPath).delete()
                Recorder.reset()   // stops GPS, the timer and the notification, and forgets the hike
                findNavController().popBackStack()
            }
            .setNegativeButton("Keep hike", null)
            .show()
    }

    // ---------- camera ----------
    private fun launchCamera() {
        try {
            val dir = File(requireContext().filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "hike_${System.currentTimeMillis()}.jpg")
            photoFile = file
            // Camera apps can't write into our private folder directly, so we hand
            // them a temporary permission through the FileProvider declared in the manifest
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file
            )
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            toast("No camera app found on this device")
        } catch (e: Exception) {
            toast("Could not open the camera")
        }
    }

    // Copies the photo into Pictures/TrailMate so it shows up in the Gallery app (Android 10+)
    private fun copyToGallery(ctx: Context, path: String) {
        if (path.isBlank() || Build.VERSION.SDK_INT < 29) return
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, File(path).name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TrailMate") // album folder
            }
            val uri = ctx.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                File(path).inputStream().use { it.copyTo(out) }
            }
        } catch (e: Exception) {
            // the gallery copy is a bonus; the hike still saves without it
        }
    }

    private fun showThumb() {
        try {
            // inSampleSize = 8 loads the photo at 1/8 width and height: small and memory-safe
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            ivThumb.setImageBitmap(BitmapFactory.decodeFile(photoPath, opts))
        } catch (e: Exception) {
            // thumbnail is only a preview; the photo is still saved
        }
    }

    // ---------- Geocoder ----------
    private suspend fun placeName(ctx: Context, p: GeoPoint): String =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) return@withContext ""
                @Suppress("DEPRECATION")
                val list = Geocoder(ctx, Locale.getDefault())
                    .getFromLocation(p.latitude, p.longitude, 1)
                val a = list?.firstOrNull()
                a?.locality ?: a?.subLocality ?: a?.subAdminArea ?: a?.adminArea ?: ""
            } catch (e: Exception) {
                "" // no internet or no geocoder: place names are optional
            }
        }

    // ---------- save with validation ----------
    private fun saveHike() {
        val name = etName.text.toString().trim()
        val date = etDate.text.toString().trim()

        if (name.isEmpty()) { tilName.error = "Hike name is required"; return }
        if (name.length < 3) { tilName.error = "Name must be at least 3 characters"; return }
        if (date.isEmpty()) { etDate.error = "Please pick a date"; return }
        if (points.size < 2 || Recorder.distanceMeters < 10) {
            toast("No hike recorded yet. Tap Start and walk at least 10 metres.")
            return
        }
        if (state != State.IDLE) Recorder.stop() // saving mid-hike (recording or paused) ends it automatically

        val ctx = requireContext().applicationContext
        val dao = AppDatabase.get(ctx).hikeDao()
        val routeString = points.joinToString(";") { "${it.latitude},${it.longitude}" }
        val first = points.first()
        val last = points.last()
        val km = Recorder.distanceMeters / 1000.0
        val notes = etNotes.text.toString().trim()
        val secs = Recorder.elapsedSec
        val steps = if (stepsOk) Recorder.currentSteps() else 0
        val photo = photoPath

        view?.findViewById<Button>(R.id.btnSave)?.isEnabled = false // stop double-taps
        lifecycleScope.launch {
            try {
                val startPlace = placeName(ctx, first)
                val endPlace = placeName(ctx, last)
                withContext(Dispatchers.IO) { copyToGallery(ctx, photo) }
                dao.insert(
                    Hike(
                        name = name, date = date, distanceKm = km, durationSec = secs,
                        startPlace = startPlace, endPlace = endPlace, steps = steps,
                        notes = notes, photoPath = photo, routePoints = routeString
                    )
                )
                Toast.makeText(ctx, "Hike saved!", Toast.LENGTH_SHORT).show()
                Recorder.reset()   // the hike is in the database now
                clearForm()
                findNavController().popBackStack()
            } catch (e: Exception) {
                view?.findViewById<Button>(R.id.btnSave)?.isEnabled = true
                Toast.makeText(ctx, "Could not save the hike. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    //----------- clear form (screen only; Recorder.reset() clears the data) -------------
    private fun clearForm() {
        etName.text.clear()
        tilName.error = null
        etNotes.text.clear()
        setTodayAsDate()
        ivThumb.setImageDrawable(null)
        routeLine.setPoints(emptyList<GeoPoint>())
        map.invalidate()
        updateLive()
        meMarker.isEnabled = false
    }

    // ---------- lifecycle ----------
    override fun onResume() { super.onResume(); map.onResume() }
    override fun onPause() { super.onPause(); map.onPause() }

    override fun onDestroyView() {
        // Only stop listening. The hike keeps recording in the background
        Recorder.listener = null
        map.onDetach() // frees the map's memory and tile-loading threads
        super.onDestroyView()
    }
}