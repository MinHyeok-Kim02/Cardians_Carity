package com.example.pleostest

import android.content.Context
import android.car.VehicleGear
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import java.util.Locale
import kotlin.math.abs

/** Shared YCY vehicle information bar, 50:50 alley/cabin and existing bottom controls. */
class AlleyPassSimulationView(context: Context, restored: Bundle?) : FrameLayout(context) {
    private val ink = Color.rgb(235, 243, 250)
    private val muted = Color.rgb(170, 190, 205)
    private val teal = Color.rgb(68, 224, 191)
    private val world = AlleyWorld()
    private val gate = AlleyMacroGate()
    private val map = Alley3dView(context)
    private val vehicleInfo = VehicleInfoPanel(context, compact = true)
    private val coach = AlleyVoiceCoach(context)
    private val sideWarning = SideClearanceWarning()
    private val interior = InteriorDemoView(context)
    private val guides = ClearanceGuideView(context)
    private val ambientStatus = label("차량 조명 연동 꺼짐 · 적용 미확인", 12, muted).apply { maxLines = 8 }
    private val ambientToggle = androidx.appcompat.widget.SwitchCompat(context)
    private val ambient = AmbientLightBridge(context) { ambientStatus.show(it) }
    private var lastWarningPair: Pair<SideClearanceWarning.Level, SideClearanceWarning.Level>? = null
    private var consoleMessage = "Console 연결 대기"
    private var sdkGear: ConsoleDriveModel.Gear? = null
    private var gearAt = 0L
    private val gearButtons = linkedMapOf<ConsoleDriveModel.Gear, Button>()
    private val consoleDriver = PleosConsoleDriver(context) {
        consoleMessage = it
        render(SystemClock.elapsedRealtime())
    }
    private val hud = panel()
    private val macroBanner = panel()
    private val bannerProgress = label("", 16, teal)
    private val bannerIcon = label("", 56, teal)
    private val bannerTitle = label("", 30)
    private val bannerDetail = label("", 18)
    private val bannerFooter = label("", 14, muted)
    private val title = label("ALLEY PASS  ·  SIMULATION", 15, teal)
    private val vehicleText = label("", 23)
    private val inputText = label("", 14, muted)
    private val keyboardInputText = label("", 13, teal).apply { visibility = View.GONE }
    private var lastKeyboardInput = "키 입력 대기 · 앱 화면 클릭 후 ↑/W를 길게 누르세요"
    private val warning = label("", 22)
    private val detail = label("", 14, muted)
    private val leftGap = label("", 18)
    private val rightGap = label("", 18)
    private val macroStatus = label("", 15)
    private val confirmation = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
    private val start = Button(context)
    private val demo = Button(context)
    private val keyboard = Button(context)
    private val reset = Button(context)
    private val macro = Button(context)
    private val confirm = Button(context)
    private var restricted = true
    private val emulator = android.os.Build.HARDWARE in listOf("ranchu", "goldfish")
    private val debugEmulator = emulator &&
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var running = false
    private var scenarioStarted = false
    private var demoMode = false
    private var keyboardMode = false
    private var windowFocused = true
    private var demoSeconds = 0.0
    private var demoComplete = false
    private var speed: Float? = null
    private var steering: Float? = null
    private var rawSteering: Float? = null
    private var brake: Boolean? = null
    private var speedAt = 0L
    private var steeringAt = 0L
    private var brakeAt = 0L
    private var lastFrame = 0L
    private var lastLabels = 0L
    private var lastLog = 0L
    private var lastGap = world.clearance()
    private var macroState = AlleyMacroGate.State(0, false, false, false)
    private var macroBusy = false
    private var macroMessage = ""
    private var macroToken = 0
    private var macroStage = -1
    private var hazardOn = false

    private data class Motion(val speed: Double?, val steering: Double?, val brake: Boolean?)
    private fun motion(now: Long): Motion {
        if (keyboardMode) {
            if (!consoleDriver.connected || freshGear(now) == null) return Motion(null, null, null)
            val fresh = speed?.takeIf { now - speedAt <= 3000 }
            // Preserve valid stopped brake history during the brief HTTP -> AAOS gear transition.
            if (freshGear(now) != consoleDriver.gear && (fresh == null || abs(fresh) > .01f))
                return Motion(null, null, null)
        }
        if (demoMode) {
            val input = AlleyDemoScenario.sample(demoSeconds)
            return Motion(input.speedMps, input.steering, input.brake)
        }
        val receivedSpeed = speed?.takeIf { now - speedAt <= 3000 }?.toDouble()
        val signedSpeed = if (freshGear(now) == ConsoleDriveModel.Gear.R) receivedSpeed?.let { -abs(it) } else receivedSpeed
        return Motion(signedSpeed,
            steering?.takeIf { now - steeringAt <= 3000 }?.toDouble(),
            brake?.takeIf { now - brakeAt <= 3000 })
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            val dt = if (lastFrame == 0L) 0.0 else ((now - lastFrame) / 1000.0).coerceAtMost(.1)
            lastFrame = now
            if (restricted && !emulator) running = false
            if (running && demoMode) demoSeconds += dt
            val input = motion(now)
            if (running && input.speed != null && input.steering != null &&
                (world.simulationSeconds > 0 || abs(input.speed) > .01))
                world.advance(dt, input.speed, input.steering)
            if (demoMode && demoSeconds >= AlleyDemoScenario.DURATION_SECONDS) {
                demoComplete = true; running = false; endAmbientDemo()
            }
            if (world.contact || world.y >= 74) {
                running = false; endAmbientDemo()
                if (keyboardMode && consoleDriver.connected) consoleDriver.stop()
            }
            map.publish(world, lastGap)
            publishFeature2(now, lastGap)
            if (running && now - lastLog >= 1000) {
                android.util.Log.d("CarTalkAlley", "x=${world.x} y=${world.y} heading=${world.heading} speed=${input.speed} steering=${input.steering} source=${if (keyboardMode) "CONSOLE_SDK" else if (demoMode) "SYNTHETIC" else "SDK"}")
                lastLog = now
            }
            if (now - lastLabels >= 200) { render(now); lastLabels = now }
            main.postDelayed(this, 50)
        }
    }

    init {
        clipChildren = true; clipToPadding = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.rgb(11, 20, 32))
        if (restored != null) {
            world.restore(restored.getDouble("world.x", 0.0), restored.getDouble("world.y", 4.0),
                restored.getDouble("world.heading", 0.0), restored.getBoolean("world.contact", false),
                restored.getDouble("world.seconds", 0.0))
            demoMode = restored.getBoolean("demo.mode", false) && debugEmulator
            demoSeconds = restored.getDouble("demo.seconds", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, 10.0) ?: 0.0
        }
        val layout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        layout.addView(vehicleInfo, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val split = LinearLayout(context)
        layout.addView(split, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val alleyPane = FrameLayout(context)
        val cabinPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(9, 15, 22))
        }
        split.addView(alleyPane, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        split.addView(cabinPane, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        alleyPane.addView(map, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        alleyPane.addView(guides, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        cabinPane.addView(label("ON-SENSE  ·  기능 2", 15, teal))
        cabinPane.addView(label("실내 조명 연출", 20))
        cabinPane.addView(interior, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        cabinPane.addView(label("좌우 간격은 가상 벽·주차 차량 기준입니다.", 12, muted))
        cabinPane.addView(ambientStatus)
        ambientToggle.text = "차량 무드라이트 연동 · 정차 AVD"
        ambientToggle.textSize = 13f; ambientToggle.setTextColor(ink)
        ambientToggle.setOnCheckedChangeListener { _, enabled ->
            if (enabled && (!active || !parkConfirmed() || !debugEmulator)) {
                ambientToggle.isChecked = false
                ambientStatus.show("연동 보류 · 실제 P·0km/h와 최신 차속·기어 수신 필요")
            } else {
                ambient.enable(enabled)
                if (enabled) ambientStatus.show("지원 영역 조회 중 · 적용 미확인")
            }
        }
        cabinPane.addView(ambientToggle)
        title.setTypeface(title.typeface, Typeface.BOLD)
        hud.addView(title); hud.addView(vehicleText); hud.addView(inputText); hud.addView(keyboardInputText)
        val gaps = LinearLayout(context).apply {
            addView(leftGap, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(rightGap, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        hud.addView(gaps); hud.addView(warning); hud.addView(detail)
        alleyPane.addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(14), dp(12), dp(14), 0)
        })
        macroBanner.addView(bannerProgress)
        val headline = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        headline.addView(bannerIcon, LinearLayout.LayoutParams(dp(90), LayoutParams.WRAP_CONTENT))
        headline.addView(bannerTitle, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        macroBanner.addView(headline)
        macroBanner.addView(bannerDetail); macroBanner.addView(bannerFooter)
        macroBanner.visibility = View.GONE
        alleyPane.addView(macroBanner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(14), dp(12), dp(14), 0)
        })
        val controls = panel()
        controls.addView(macroStatus)
        val gears = LinearLayout(context)
        for (value in ConsoleDriveModel.Gear.values()) {
            val button = Button(context)
            configure(button, value.name) {
                val current = motion(SystemClock.elapsedRealtime()).speed
                consoleDriver.select(value, current)
                consoleMessage = value.name + " 요청 · 수신 확인 중"
                if (keyboardMode) requestFocus()
                render(SystemClock.elapsedRealtime())
            }
            gearButtons[value] = button
            gears.addView(button, buttonParams())
        }
        controls.addView(gears)
        confirmation.addView(label("가상 매크로를 실행할까요?\n미러 접기·창문 개방·비상등 3회는 시뮬레이션입니다. 실제 차량은 제어하지 않습니다.", 16))
        val decisions = LinearLayout(context)
        configure(confirm, "가상 실행") { executeMacro() }
        decisions.addView(confirm, buttonParams())
        decisions.addView(Button(context).apply {
            configure(this, "취소") {
                cancelMacro(); render(SystemClock.elapsedRealtime())
            }
        }, buttonParams())
        confirmation.addView(decisions); controls.addView(confirmation)
        val actions = LinearLayout(context)
        configure(start, "SDK 연동 시작") {
            if (keyboardMode || demoMode || world.contact || world.y >= 74) prepareScene(false)
            else {
                cancelMacro(); gate.reset()
                running = !running; scenarioStarted = true; lastFrame = 0
                if (!running) endAmbientDemo()
            }
            render(SystemClock.elapsedRealtime())
        }
        configure(demo, "자동 시연(가상)") { prepareScene(true); render(SystemClock.elapsedRealtime()) }
        configure(keyboard, "Console 키보드 연결") { prepareKeyboard(); render(SystemClock.elapsedRealtime()) }
        configure(reset, "초기화") {
            endAmbientDemo(); sideWarning.reset()
            cancelMacro(); gate.reset(); coach.reset(); world.reset(); consoleDriver.stop()
            running = false; scenarioStarted = false; demoComplete = false; demoSeconds = 0.0; lastFrame = 0
            render(SystemClock.elapsedRealtime())
        }
        configure(macro, "패스 매크로(SIM)") {
            render(SystemClock.elapsedRealtime())
            if (macroState.canConfirm) {
                running = false; coach.silence()
                if (keyboardMode) consoleDriver.release(AlleyKeyboardDriver.Control.ACCELERATE)
                macroStage = 0
                confirmation.visibility = View.VISIBLE
                render(SystemClock.elapsedRealtime())
            }
        }
        for (button in listOf(start, demo, keyboard, reset, macro)) actions.addView(button, buttonParams())
        controls.addView(actions)
        controls.addView(label("키보드는 Pleos 에뮬레이터에 반영됩니다. 거리·미러·창문·비상등 매크로는 가상입니다.", 12, muted))
        layout.addView(controls, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(14), 0, dp(14), dp(12))
        })
        render(SystemClock.elapsedRealtime())
    }

    private fun prepareScene(synthetic: Boolean) {
        endAmbientDemo(); sideWarning.reset()
        cancelMacro(); gate.reset(); coach.reset(); world.reset(); consoleDriver.stop()
        keyboardMode = false
        demoMode = synthetic; demoSeconds = 0.0; demoComplete = false
        if (synthetic) world.restore(0.0, 11.0, 0.0, false)
        scenarioStarted = true; running = true; lastFrame = 0
    }
    private fun prepareKeyboard() {
        prepareScene(false)
        keyboardMode = true
        lastKeyboardInput = "키 입력 대기 · Running Devices 앱 화면에서 ↑/W 길게"
        consoleDriver.start()
        requestFocus()
    }

    fun drivingWindowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (!focused && keyboardMode) {
            endAmbientDemo()
            consoleDriver.stop(); running = false; scenarioStarted = false
            gate.reset(); cancelMacro("키보드 운전 일시정지 · 버튼으로 다시 시작하세요")
            render(SystemClock.elapsedRealtime())
        }
    }

    fun handleDrivingKey(event: KeyEvent): Boolean {
        if (!active || !keyboardMode || !debugEmulator) return false
        val control = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> AlleyKeyboardDriver.Control.ACCELERATE
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> AlleyKeyboardDriver.Control.BRAKE
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> AlleyKeyboardDriver.Control.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> AlleyKeyboardDriver.Control.RIGHT
            KeyEvent.KEYCODE_SPACE -> AlleyKeyboardDriver.Control.STOP
            else -> null
        }
        if (control == null && event.keyCode != KeyEvent.KEYCODE_R) return false
        if (event.repeatCount == 0 || event.action == KeyEvent.ACTION_UP) {
            val accepted = active && windowFocused && consoleDriver.connected
            val action = if(event.action == KeyEvent.ACTION_UP) "KEY_UP" else "KEY_DOWN"
            lastKeyboardInput = "${KeyEvent.keyCodeToString(event.keyCode)} " +
                (if(event.action == KeyEvent.ACTION_UP) "뗌" else "눌림") +
                if(accepted) " · 키 수신됨" else " · 연결/포커스 확인 필요"
            android.util.Log.i("CarTalkKeyboard",
                "$action code=${event.keyCode} control=$control accepted=$accepted running=$running")
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if (control != null) consoleDriver.release(control)
            if (event.isCanceled) consoleDriver.stop()
        } else if (event.action == KeyEvent.ACTION_DOWN && windowFocused && consoleDriver.connected) {
            if (event.keyCode == KeyEvent.KEYCODE_R && event.repeatCount == 0 && parkConfirmed()) prepareKeyboard()
            else if (control != null && (running ||
                control == AlleyKeyboardDriver.Control.BRAKE || control == AlleyKeyboardDriver.Control.STOP))
                consoleDriver.press(control)
        }
        render(SystemClock.elapsedRealtime())
        return true
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Int, color: Int = ink) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); maxLines = 2
        setPadding(dp(3), dp(3), dp(3), dp(3))
    }
    private fun panel() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply { setColor(Color.argb(222, 11, 23, 36)); cornerRadius = dp(14).toFloat() }
    }
    private fun configure(button: Button, value: String, action: () -> Unit) {
        button.text = value; button.textSize = 15f; button.isAllCaps = false
        button.minWidth = 0; button.minimumWidth = 0; button.maxLines = 2
        button.setOnClickListener { action() }
    }
    private fun buttonParams() = LinearLayout.LayoutParams(0, dp(60), 1f).apply { setMargins(dp(3), dp(3), dp(3), 0) }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }

    private fun freshGear(now: Long) = sdkGear?.takeIf { now - gearAt <= 3000 }
    private fun parkConfirmed(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val v = speed?.takeIf { now - speedAt <= 3000 } ?: return false
        return abs(v) <= .01f && freshGear(now) == ConsoleDriveModel.Gear.P &&
            (!keyboardMode || (consoleDriver.connected && consoleDriver.gear == ConsoleDriveModel.Gear.P))
    }
    fun updateGear(value: Int?) {
        sdkGear = when(value) {
            VehicleGear.GEAR_DRIVE -> ConsoleDriveModel.Gear.D
            VehicleGear.GEAR_REVERSE -> ConsoleDriveModel.Gear.R
            VehicleGear.GEAR_NEUTRAL -> ConsoleDriveModel.Gear.N
            VehicleGear.GEAR_PARK -> ConsoleDriveModel.Gear.P
            else -> null
        }
        gearAt = SystemClock.elapsedRealtime()
    }
    fun release() { ambient.close(); consoleDriver.close() }

    fun updateSpeed(value: Float?) { speed = value?.takeIf { it.isFinite() }; speedAt = SystemClock.elapsedRealtime() }
    fun updateBrake(value: Boolean?) { brake = value; brakeAt = SystemClock.elapsedRealtime() }
    fun updateSteering(raw: Float?, normalized: Float?) {
        rawSteering = raw?.takeIf { it.isFinite() }
        steering = normalized?.takeIf { rawSteering != null && it.isFinite() }
        steeringAt = SystemClock.elapsedRealtime()
    }
    fun updateRestrictions(value: Boolean) {
        restricted = value
        if (value) { confirmation.visibility = View.GONE; if (macroBusy) cancelMacro("매크로 취소 · 주행 제한") }
        if (value && !emulator) running = false
        render(SystemClock.elapsedRealtime())
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value; ambient.visible(value); main.removeCallbacks(frame); lastFrame = 0
        if (value) { map.onResume(); coach.start(); main.post(frame) }
        else {
            endAmbientDemo()
            running = false; scenarioStarted = false; gate.reset(); cancelMacro(); consoleDriver.stop()
            map.onPause(); coach.stop(); speed = null; steering = null; rawSteering = null; brake = null
        }
    }

    private fun cancelMacro(message: String = "") {
        macroToken++; macroBusy = false; macroMessage = message; macroStage = -1; hazardOn = false
        confirmation.visibility = View.GONE
    }
    private fun stoppedForMacro(): Boolean {
        val input = motion(SystemClock.elapsedRealtime())
        return active && !restricted && input.speed != null &&
            abs(input.speed) <= .01 && (if(keyboardMode) parkConfirmed() else input.brake == true || parkConfirmed())
    }
    private fun executeMacro() {
        val now = SystemClock.elapsedRealtime()
        val input = motion(now)
        if (world.contact || world.clearance().meters <= .3) {
            cancelMacro("근접 경고 우선 · 주변을 확인하세요"); render(now); return
        }
        if (!gate.consume(now, input.speed, input.brake, world.clearance().meters, active && !restricted && (!keyboardMode || parkConfirmed()), parkConfirmed())) {
            cancelMacro("실행 보류 · 최신 정차·브레이크·감지 조건을 다시 확인하세요")
            render(now); return
        }
        confirmation.visibility = View.GONE
        macroBusy = true; running = false
        val token = ++macroToken
        fun later(delay: Long, action: () -> Unit) {
            main.postDelayed({
                if (token != macroToken || !macroBusy) return@postDelayed
                if (!stoppedForMacro()) { cancelMacro("가상 매크로 취소 · 정차·브레이크 유지 필요"); render(SystemClock.elapsedRealtime()); return@postDelayed }
                action(); render(SystemClock.elapsedRealtime())
            }, delay)
        }
        later(0) { macroStage = 1; macroMessage = "SIM  1/3 · 사이드미러 접기 시뮬레이션" }
        later(2200) { macroStage = 2; macroMessage = "SIM  2/3 · 조수석 창문 미세 개방 시뮬레이션" }
        for (i in 0..5) later(4400L + i * 500) {
            macroStage = 3; hazardOn = i % 2 == 0
            macroMessage = "SIM  3/3 · 비상등 가상 점멸 ${i / 2 + 1}/3"
        }
        later(7600) {
            macroBusy = false; macroStage = 4
            macroMessage = "가상 매크로 완료 · 미러·창문·비상등 실제 차량 제어 없음"
            android.util.Log.i("CarTalkMacro", "SIMULATION_COMPLETED; actuatorWrites=0")
        }
    }

    private fun render(now: Long) {
        val input = motion(now)
        val v = input.speed; val angle = input.steering
        val gap = world.clearance(); lastGap = gap; map.publish(world, gap)
        vehicleInfo.render(
            speed?.takeIf { now - speedAt <= 3000 }?.let { abs(it) * 3.6f },
            rawSteering?.takeIf { now - steeringAt <= 3000 },
            freshGear(now)?.name,
            brake?.takeIf { now - brakeAt <= 3000 },
            restricted, demoMode
        )
        publishFeature2(now, gap)
        if (macroBusy && (world.contact || gap.meters <= .3 || !stoppedForMacro()))
            cancelMacro("가상 매크로 취소 · 근접 경고 또는 정차·브레이크 상태를 확인하세요")
        val detect = active && scenarioStarted && (running || demoComplete || macroState.armed || confirmation.visibility == View.VISIBLE)
        macroState = if (detect) gate.observe(now, v, input.brake, gap.meters, !restricted && (!keyboardMode || parkConfirmed()), parkConfirmed())
            else { gate.reset(); AlleyMacroGate.State(0, false, false, false) }
        if (world.contact || gap.meters <= .3) macroState = macroState.copy(canConfirm = false)
        if (confirmation.visibility == View.VISIBLE && !macroState.armed)
            cancelMacro("감지 유효시간 만료 · 다시 시연하거나 조건을 확인하세요")
        vehicleText.show((v?.let { String.format(Locale.KOREA, "%.1f km/h", abs(it) * 3.6) } ?: "속도 수신 대기") +
            if (keyboardMode) "  ·  Console → SDK 수신" else if (demoMode) "  ·  가상 입력" else "  ·  차량 SDK 입력")
        keyboardInputText.visibility = if (keyboardMode) View.VISIBLE else View.GONE
        keyboardInputText.show(lastKeyboardInput)
        inputText.show("조향 " + when {
            angle == null -> "미확인"; angle < -.02 -> "왼쪽"; angle > .02 -> "오른쪽"; else -> "중앙"
        } + "  ·  브레이크 " + when (input.brake) { true -> "눌림"; false -> "해제"; null -> "미확인" } +
            if (coach.ready) "  ·  음성 준비됨" else "  ·  음성 준비 안 됨")
        leftGap.show(String.format(Locale.KOREA, "왼쪽 측면  %.2f m", gap.leftMeters))
        rightGap.show(String.format(Locale.KOREA, "오른쪽 측면  %.2f m", gap.rightMeters))
        leftGap.setTextColor(SideClearanceWarning.level(gap.leftMeters).argb)
        rightGap.setTextColor(SideClearanceWarning.level(gap.rightMeters).argb)
        val guide = if (running && v != null && angle != null) world.guidance(v) else null
        val message = when {
            world.contact -> "가상 접촉 · 시뮬레이션 정지"
            macroState.armed && !macroState.stopped -> "골목 패스 감지 · 정차 후 매크로 확인"
            macroState.canConfirm -> "정차 확인 · 패스 매크로를 선택하세요"
            demoComplete -> "가상 시연 완료 · 브레이크 유지 중"
            !running -> "하단 버튼으로 시뮬레이션을 시작하세요"
            v == null || angle == null -> "차량 입력 대기 · 이동 보류"
            gap.meters <= .3 -> "${gap.side} 근접 · 정차 후 확인"
            abs(v) * 3.6 > 10 -> "골목에서는 속도를 낮추세요"
            guide?.advice == AlleyWorld.Advice.STOP -> guide.message
            guide?.advice == AlleyWorld.Advice.LEFT -> "왼쪽 여유를 확인하세요"
            guide?.advice == AlleyWorld.Advice.RIGHT -> "오른쪽 여유를 확인하세요"
            gap.meters <= 1.2 && gap.approaching -> "${gap.side}에 가까워지고 있어요"
            gap.meters <= .8 -> "${gap.side} 공간 주의"
            else -> "주변을 확인하며 천천히 이동하세요"
        }
        warning.show(message); warning.setTextColor(if (world.contact || gap.meters <= .3) Color.rgb(255, 127, 127) else ink)
        detail.show(if (keyboardMode) consoleMessage + " · 기어 수신 " + (freshGear(now)?.name ?: "미확인") + " · ↑/W 길게 가속 · ↓/S 브레이크 · ←A/→D 조향 · Space 정지"
            else if (demoMode) "앱 내부 가상 시연 · 차량 SDK 값이나 Console 상태를 바꾸지 않습니다."
            else "가상 최근접 ${String.format(Locale.KOREA, "%.2f", gap.meters)}m · ${gap.side}")
        macroStatus.show(if (macroMessage.isNotEmpty()) macroMessage else when {
            macroState.canConfirm -> "매크로 준비됨 · 사용자 확인 후 가상 실행"
            macroState.armed -> "조건 감지됨 · 정차하고 브레이크를 유지하세요"
            else -> "패스 조건: 10km/h 이하 · 브레이크 ${macroState.presses}/3회(15초) · 가상 근접 1.2m 이하"
        })
        val card = AlleyMacroPresentation.card(macroStage, gap.meters, gap.approaching, world.contact, hazardOn)
        macroBanner.visibility = if (card == null) View.GONE else View.VISIBLE
        hud.visibility = if (card == null) View.VISIBLE else View.GONE
        if (card != null) {
            bannerProgress.show(card.progress); bannerIcon.show(card.icon)
            bannerTitle.show(card.title); bannerDetail.show(card.detail)
            bannerFooter.show(String.format(Locale.KOREA,
                "왼쪽 %.2fm · 오른쪽 %.2fm  |  실제 차량 제어 없음", gap.leftMeters, gap.rightMeters))
            bannerTitle.setTextColor(if (card.urgent) Color.rgb(255, 127, 127) else ink)
            bannerIcon.setTextColor(if (card.urgent) Color.rgb(255, 127, 127) else teal)
        }
        start.show(if (!keyboardMode && !demoMode && running) "SDK 일시정지" else "SDK 연동 시작")
        start.isEnabled = active && !restricted && !macroBusy
        demo.isEnabled = active && !restricted && debugEmulator && !macroBusy
        keyboard.isEnabled = active && !restricted && debugEmulator && !macroBusy
        reset.isEnabled = active && !restricted
        for ((value,button) in gearButtons) {
            button.text = value.name + if(freshGear(now) == value) " ✓" else ""
            button.isEnabled = active && debugEmulator && keyboardMode && consoleDriver.connected &&
                !macroBusy && freshGear(now) != value && v != null &&
                (value == ConsoleDriveModel.Gear.N || abs(v) <= .01)
        }
        macro.isEnabled = active && !macroBusy && macroState.canConfirm
        confirm.isEnabled = active && !macroBusy && macroState.canConfirm
        if (active && running && v != null && angle != null) {
            val key = when {
                gap.meters <= .3 -> "near:" + gap.side
                guide?.advice == AlleyWorld.Advice.STOP -> "stop"
                guide?.advice == AlleyWorld.Advice.LEFT -> "left"
                guide?.advice == AlleyWorld.Advice.RIGHT -> "right"
                gap.meters <= .8 -> "caution:" + gap.side
                else -> "clear"
            }
            coach.announce(key, message, now)
        } else coach.silence()
    }

    private fun endAmbientDemo() {
        if (ambientToggle.isChecked) ambientToggle.isChecked = false
        ambient.enable(false)
    }

    private fun publishFeature2(now: Long, gap: AlleyWorld.Clearance) {
        val warningFrame = sideWarning.update(gap.leftMeters, gap.rightMeters, now)
        guides.publish(warningFrame); interior.publish(warningFrame)
        ambient.warnings(warningFrame.left.level, warningFrame.right.level)
        val pair = warningFrame.left.level to warningFrame.right.level
        if (pair != lastWarningPair) {
            android.util.Log.i("OnSensePleosF2",
                "CLEARANCE_STATE leftMeters=${gap.leftMeters} rightMeters=${gap.rightMeters} left=${pair.first} right=${pair.second}; source=AlleyWorld.clearance")
            if (pair.first == SideClearanceWarning.Level.RED && lastWarningPair?.first != pair.first)
                android.util.Log.i("OnSensePleosF2", "RED_ENTRY side=LEFT blinkPeriodMs=500 cycles=3")
            if (pair.second == SideClearanceWarning.Level.RED && lastWarningPair?.second != pair.second)
                android.util.Log.i("OnSensePleosF2", "RED_ENTRY side=RIGHT blinkPeriodMs=500 cycles=3")
            lastWarningPair = pair
        }
        // Follow actual received gear/speed, never the synthetic or keyboard requested values.
        if (ambientToggle.isChecked && !parkConfirmed()) endAmbientDemo()
    }

    fun save(out: Bundle) {
        out.putDouble("world.x", world.x); out.putDouble("world.y", world.y)
        out.putDouble("world.heading", world.heading); out.putBoolean("world.contact", world.contact)
        out.putDouble("world.seconds", world.simulationSeconds)
        out.putBoolean("demo.mode", demoMode); out.putDouble("demo.seconds", demoSeconds)
    }
    override fun onDetachedFromWindow() { setActive(false); super.onDetachedFromWindow() }
}
