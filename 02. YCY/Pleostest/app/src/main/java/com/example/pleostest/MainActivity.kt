package com.example.pleostest

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var alleyScreen: AlleyPassSimulationView
    private val uxSource by lazy { DrivingUxSource(this) { alleyScreen.updateRestrictions(it) } }
    private val brakeSource by lazy { PleosBrakeTest(this) { alleyScreen.updateBrake(it) } }
    private val speedSource by lazy { VehicleSpeedTest(this) { alleyScreen.updateSpeed(it) } }
    private val steeringSource by lazy { PleosSteeringSource(this) { raw, normalized -> alleyScreen.updateSteering(raw, normalized) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Stay within the window supplied by the Pleos launcher; no overlay or fullscreen launch.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)
        val root = findViewById<ViewGroup>(R.id.main)
        root.clipChildren = true
        root.clipToPadding = true
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        alleyScreen = AlleyPassSimulationView(this, savedInstanceState)
        root.addView(alleyScreen, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    override fun onStart() {
        super.onStart()
        alleyScreen.setActive(true)
        uxSource.start()
        brakeSource.start()
        steeringSource.start()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            if (checkSelfPermission("android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED)
                speedSource.start()
            else requestPermissions(arrayOf("android.car.permission.CAR_SPEED"), 1002)
        }
    }

    override fun onStop() {
        alleyScreen.setActive(false)
        uxSource.stop()
        steeringSource.stop()
        brakeSource.stop()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) speedSource.stop()
        super.onStop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) speedSource.start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        alleyScreen.save(outState)
        super.onSaveInstanceState(outState)
    }
}
