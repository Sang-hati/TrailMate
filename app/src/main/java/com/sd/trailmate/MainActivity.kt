package com.sd.trailmate

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    companion object {
        // Sent by the "Recording hike" notification so a tap opens the Record Hike screen
        const val EXTRA_OPEN_RECORD = "open_record"
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // The toolbar title and back arrow follow the navigation graph automatically
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        findViewById<MaterialToolbar>(R.id.toolbar).setupWithNavController(navHost.navController)

        // Keep content clear of the status bar, nav bar and keyboard
        val root = findViewById<View>(R.id.root)
        val host = findViewById<FragmentContainerView>(R.id.nav_host)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            host.setPadding(0, 0, 0, maxOf(bars.bottom, ime.bottom))
            insets
        }

        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "step_log",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StepWorker>(15, TimeUnit.MINUTES).build()
        )

        // Only on a fresh launch: after a rotation the same Intent comes back and must not re-navigate
        if (savedInstanceState == null) openRecordIfRequested(intent)
    }

    // The app is already open and the notification was tapped
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRecordIfRequested(intent)
    }

    private fun openRecordIfRequested(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_RECORD, false) != true) return
        intent.removeExtra(EXTRA_OPEN_RECORD)   // handle each tap once
        val nav = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
        if (nav.currentDestination?.id != R.id.addHikeFragment) nav.navigate(R.id.addHikeFragment)
    }
}