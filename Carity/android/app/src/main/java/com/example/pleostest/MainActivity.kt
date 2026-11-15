package com.example.pleostest

import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import android.os.Bundle
import android.view.ViewGroup
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var alleyScreen: AlleyPassSimulationView
    private var locationWanted = true
    private var restricted = true
    private val locationSource by lazy { PleosLocationProbe(this) { message, active ->
        alleyScreen.showLocation(message, active)
    } }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            locationWanted = true
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) locationSource.start()
        } else {
            locationWanted = false
            alleyScreen.showLocation(
                if (shouldShowRequestPermissionRationale(PleosLocationProbe.PERMISSION))
                    "위치 권한 거부 · 버튼으로 다시 요청할 수 있습니다"
                else "위치 권한 거부 · 다시 누르면 앱 권한 설정을 안내합니다", false)
        }
    }
    private val uxSource by lazy { DrivingUxSource(this) {
        restricted = it
        alleyScreen.updateRestrictions(it)
    } }
    private val gearSource by lazy { VehicleGearSource(this) { alleyScreen.updateGear(it) } }
    private val brakeSource by lazy { PleosBrakeTest(this) { alleyScreen.updateBrake(it) } }
    private val speedSource by lazy { VehicleSpeedTest(this) { alleyScreen.updateSpeed(it) } }
    private val steeringSource by lazy { PleosSteeringSource(this) { raw, normalized -> alleyScreen.updateSteering(raw, normalized) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attachSimulation(createWindowContent(),savedInstanceState)
    }

    /** Keeps rendering inside the Pleos app window and applies system-bar insets. */
    private fun createWindowContent(): ViewGroup {
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
        return root
    }

    private fun attachSimulation(root: ViewGroup,savedInstanceState: Bundle?) {
        alleyScreen = AlleyPassSimulationView(this, savedInstanceState)
        alleyScreen.setLocationAction(::locationAction)
        root.addView(alleyScreen, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))    }

    override fun onStart() {
        super.onStart()
        startVehicleSources()
        startSpeedSourceIfPermitted()
    }

    /** Each source retains its existing registration and failure handling. */
    private fun startVehicleSources() {
        alleyScreen.setActive(true)
        uxSource.start()
        gearSource.start()
        brakeSource.start()
        steeringSource.start()
        if (locationWanted) locationSource.start()
    }

    private fun startSpeedSourceIfPermitted() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            if (checkSelfPermission("android.car.permission.CAR_SPEED") == PackageManager.PERMISSION_GRANTED)
                speedSource.start()
            else requestPermissions(arrayOf("android.car.permission.CAR_SPEED"), 1002)
        }    }

    private fun locationAction() {
        if (!CarityFeatures.locationEnabled) {
            alleyScreen.showLocation("이 버전에서는 위치 기능을 사용하지 않습니다", false)
            return
        }
        if (locationSource.active) {
            locationWanted = false
            locationSource.stop()
            return
        }
        if (checkSelfPermission(PleosLocationProbe.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            locationWanted = true
            locationSource.start()
            return
        }
        if (!locationSource.isSupported()) {
            alleyScreen.showLocation("이 환경에서 Pleos 위치 SDK 서비스를 찾을 수 없습니다", false)
            return
        }
        if (restricted) {
            alleyScreen.showLocation("정차 후 위치 수신 시작을 눌러 권한을 허용하세요", false)
            return
        }
        showLocationPermissionPrompt()
    }

    private fun showLocationPermissionPrompt() {
        val history = getSharedPreferences("location.permission.history", MODE_PRIVATE)
        if (shouldShowRequestPermissionRationale(PleosLocationProbe.PERMISSION)) {
            AlertDialog.Builder(this)
                .setTitle("차량 위치 수신 실험")
                .setMessage("Pleos의 위도·경도·방향을 앱 화면과 Logcat에서 확인합니다. 외부 지도에는 전송하지 않습니다.")
                .setPositiveButton("계속") { _, _ ->
                    history.edit().putBoolean("asked", true).apply()
                    locationPermission.launch(PleosLocationProbe.PERMISSION)
                }
                .setNegativeButton("취소", null).show()
        } else if (history.getBoolean("asked", false)) {
            AlertDialog.Builder(this)
                .setTitle("위치 권한 설정")
                .setMessage("위치 권한이 허용되지 않았습니다. 앱 설정에서 허용한 뒤 위치 수신 시작을 눌러주세요.")
                .setPositiveButton("앱 설정") { _, _ ->
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")))
                }.setNegativeButton("취소", null).show()
        } else {
            history.edit().putBoolean("asked", true).apply()
            locationPermission.launch(PleosLocationProbe.PERMISSION)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::alleyScreen.isInitialized && alleyScreen.handleDrivingKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (::alleyScreen.isInitialized) alleyScreen.drivingWindowFocusChanged(hasFocus)
    }

    override fun onStop() {
        stopVehicleSources()
        super.onStop()
    }

    /** Stops subscriptions and simulation before Activity teardown, in the original order. */
    private fun stopVehicleSources() {
        locationSource.stop()
        alleyScreen.setActive(false)
        uxSource.stop()
        gearSource.stop()
        steeringSource.stop()
        brakeSource.stop()
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) speedSource.stop()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1002 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) speedSource.start()
    }

    override fun onDestroy() {
        if (::alleyScreen.isInitialized) alleyScreen.release()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        alleyScreen.save(outState)
        super.onSaveInstanceState(outState)
    }
}
