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
    /** 기본 골목 또는 라벨 장면으로 만든 세계 (장면 적용 시 교체) */
    private var world = AlleyWorld()
    private val gate = AlleyMacroGate()
    private val map = Alley3dView(context)
    private var diagnostics = false
    private val diagnosticsButton = Button(context)
    private val diagnosticsPanel = LinearLayout(context).apply { orientation=LinearLayout.VERTICAL; visibility=GONE }
    private val locationBar = LinearLayout(context)
    private val sdkDiagnostics = label("",12,muted)
    private val sceneCaption = label("3D 가상 거리 · 실내 조명 연출",12,muted)
    private val vehicleInfo = VehicleInfoPanel(context, compact = true)
    private val locationStatus = label("위치 SDK 확인 대기 · 외부 전송 없음", 13, muted).apply { maxLines = 3 }
    private val locationControl = Button(context)
    private var locationAction: (() -> Unit)? = null
    private val coach = AlleyVoiceCoach(context)
    private val sideWarning = SideClearanceWarning()
    private val interior = InteriorDemoView(context)
    private val guides = ClearanceGuideView(context)
    private val ambientStatus = label("차량 조명 연동 꺼짐 · 적용 미확인", 12, muted).apply { maxLines = 3 }
    private val ambientToggle = androidx.appcompat.widget.SwitchCompat(context)
    private val ambientDiagnostics=label("",11,muted).apply { maxLines=6 }
    private val ambient=AmbientLightBridge(context,::showAmbientStatus)
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
    private val virtualKeyboard = Button(context)
    private val virtualDrive = VirtualKeyboardDrive()
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
    private var virtualKeyboardMode = false
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

    // ── 라벨 장애물 · 규칙/AI 판단 · 판단 음성 ──
    private val ai = AiCoordinator()
    /** 적용된 라벨/연습 장면. null 이면 기존 기본 골목 동작(기존 음성 안내) 그대로 */
    private var analysis: SceneAnalysis? = null
    /** 패널에 표시 중인 마지막 정적 분석 (기본 골목 포함) */
    private var shownAnalysis: SceneAnalysis? = null
    private var livePlan: CorridorPlanner.Plan? = null
    private var planAt = 0L
    private var decision: AlleyDecisionEngine.Decision? = null
    private var decisionKey = ""
    private var keySince = 0L
    private var aiAskedKey = ""
    private var spokenKey = ""
    private var lastSpeech = ""
    /** 라벨 장면 자동 시연: 경로 계획을 따라 이동 */
    private var planDemo = false
    private var planDemoS = 0.0
    private var planDemoSpeed = 0.0
    private val sceneHeader = label("3D 골목 · 좌우 간격은 가상 벽 기준", 14, muted)
    private val tabInterior = Button(context)
    private val tabLabel = Button(context)
    private val interiorPage = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val labelPanel = LabelScenarioPanel(context, object : LabelScenarioPanel.Host {
        override fun applyAnalysis(option: ScenarioCatalog.Option, analysis: SceneAnalysis) =
            applyScene(option, analysis)
        override fun replayJudgement() {
            val text = lastSpeech.ifBlank { decision?.speech ?: shownAnalysis?.decision?.speech ?: "" }
            coach.reset(); speak("replay", text)
        }
        override fun requestAi() {
            val d = decision ?: shownAnalysis?.decision ?: return
            askAi(d, speakResult = true)
        }
    })

    private data class Motion(val speed: Double?, val steering: Double?, val brake: Boolean?)
    private fun motion(now: Long): Motion {
        if(virtualKeyboardMode) {
            val input=virtualDrive.input()
            return Motion(input.speedMps,input.steering,input.brake)
        }
        if (keyboardMode) {
            if (!consoleDriver.connected || freshGear(now) == null) return Motion(null, null, null)
            val fresh = speed?.takeIf { now - speedAt <= 3000 }
            // Preserve valid stopped brake history during the brief HTTP -> AAOS gear transition.
            if (freshGear(now) != consoleDriver.gear && (fresh == null || abs(fresh) > .01f))
                return Motion(null, null, null)
        }
        if (demoMode && analysis != null) return Motion(planDemoSpeed, 0.0, planDemoSpeed <= .01)
        if (demoMode) {
            val input = AlleyDemoScenario.sample(demoSeconds)
            return Motion(input.speedMps, input.steering, input.brake)
        }
        val receivedSpeed = speed?.takeIf { now - speedAt <= 3000 }?.toDouble()
        val signedSpeed = VehicleMotionInput.signedSpeed(receivedSpeed,freshGear(now))
        return Motion(signedSpeed,
            steering?.takeIf { now - steeringAt <= 3000 }?.toDouble(),
            brake?.takeIf { now - brakeAt <= 3000 })
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!active) return
            tickFrame(SystemClock.elapsedRealtime())
            main.postDelayed(this, 50)
        }
    }

    /** One 50ms tick: preserve input -> movement -> warnings -> labels ordering. */
    private fun tickFrame(now: Long) {
        val dt=frameDelta(now)
        if(restricted && !emulator) running=false
        if(running && demoMode) demoSeconds+=dt
        if(running && virtualKeyboardMode) virtualDrive.advance(dt)
        if(running && planDemo) stepPlanDemo(dt)
        val input=motion(now)
        advanceWorld(dt,input)
        finishSceneIfNeeded(input)
        lastGap=displayedClearance()
        map.publish(world,lastGap)
        publishFeature2(now,lastGap)
        logMotion(now,input)
        if(now-lastLabels>=200) { render(now); lastLabels=now }
    }

    private fun frameDelta(now: Long): Double {
        val dt=if(lastFrame==0L) 0.0 else ((now-lastFrame)/1000.0).coerceAtMost(.1)
        lastFrame=now
        return dt
    }

    private fun advanceWorld(dt: Double,input: Motion) {
        if(planDemo) return
        if(running && input.speed!=null && input.steering!=null &&
            (world.simulationSeconds>0 || abs(input.speed)>.01)) world.advance(dt,input.speed,input.steering)
    }

    private fun finishSceneIfNeeded(input: Motion) {
        if(running && demoMode && analysis==null && demoSeconds>=AlleyDemoScenario.DURATION_SECONDS) {
            demoComplete=true; running=false; endAmbientDemo()
        }
        if(running && (world.contact || world.shouldStopAtEnd(input.speed))) {
            running=false; endAmbientDemo()
            if(virtualKeyboardMode) virtualDrive.stop()
            if(keyboardMode && consoleDriver.connected) consoleDriver.stop()
        }
    }

    private fun logMotion(now: Long,input: Motion) {
        if(!running || now-lastLog<1000) return
        android.util.Log.d("CarTalkAlley","x=${world.x} y=${world.y} heading=${world.heading} speed=${input.speed} steering=${input.steering} source=${if(keyboardMode) "CONSOLE_SDK" else if(virtualKeyboardMode) "VIRTUAL_KEYBOARD" else if(demoMode) "SYNTHETIC" else "SDK"}")
        lastLog=now
    }

    init {
        configureRoot()
        restoreState(restored)
        buildLayout()
        render(SystemClock.elapsedRealtime())
    }

    private fun configureRoot() {
        clipChildren = true; clipToPadding = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.rgb(11, 20, 32))
    }

    /** Restores pose and demo timing without automatically resuming motion. */
    private fun restoreState(restored: Bundle?) {
        if (restored != null) {
            world.restore(restored.getDouble("world.x", 0.0), restored.getDouble("world.y", 4.0),
                restored.getDouble("world.heading", 0.0), restored.getBoolean("world.contact", false),
                restored.getDouble("world.seconds", 0.0))
            demoMode = restored.getBoolean("demo.mode", false) && debugEmulator
            demoSeconds = restored.getDouble("demo.seconds", 0.0).takeIf { it.isFinite() }?.coerceIn(0.0, AlleyDemoScenario.DURATION_SECONDS) ?: 0.0
        }
    }

    private fun buildLayout() {
        val layout=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        addView(layout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        layout.addView(vehicleInfo, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        layout.addView(createLocationBar(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        layout.addView(createDiagnosticsPanel())
        layout.addView(createScenePanels(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(createControls(), LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(14), 0, dp(14), dp(12))
        })
    }

    /** 위치 진단과 수신 시작/중지 버튼을 구성합니다. */
    private fun createLocationBar(): LinearLayout {
        locationBar.apply {
            visibility=GONE
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(3), dp(12), dp(3))
            setBackgroundColor(Color.rgb(16, 27, 37))
        }
        locationBar.addView(locationStatus, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        configure(locationControl, "위치 수신 시작") { locationAction?.invoke() }
        locationBar.addView(locationControl, LinearLayout.LayoutParams(dp(150), dp(50)))
        return locationBar
    }

    /** SDK·키 입력·조명 재조회 진단 패널을 구성합니다. */
    private fun createDiagnosticsPanel(): LinearLayout {
        diagnosticsPanel.addView(title); diagnosticsPanel.addView(vehicleText)
        diagnosticsPanel.addView(inputText); diagnosticsPanel.addView(keyboardInputText)
        diagnosticsPanel.addView(sdkDiagnostics); diagnosticsPanel.addView(detail)
        diagnosticsPanel.addView(ambientDiagnostics)
        return diagnosticsPanel
    }

    private fun createScenePanels(): LinearLayout {
        val split=LinearLayout(context)
        val alleyPane=FrameLayout(context)
        val cabinPane=LinearLayout(context).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),dp(8),dp(12),dp(8))
            setBackgroundColor(Color.rgb(9,15,22))
        }
        // 오른쪽 패널 탭: [실내 조명] 기존 화면 · [라벨 장애물·AI 판단] 새 화면
        val tabs=LinearLayout(context)
        configure(tabInterior,"실내 조명") { showTab(false) }
        configure(tabLabel,"라벨 장애물·AI 판단") { showTab(true) }
        tabs.addView(tabInterior,LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),0,dp(2),dp(4)) })
        tabs.addView(tabLabel,LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),0,dp(2),dp(4)) })
        cabinPane.addView(tabs)
        cabinPane.addView(interiorPage,LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        cabinPane.addView(labelPanel,LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        labelPanel.setProvider(ai.providerText)
        showTab(false)
        val leftPane=LinearLayout(context).apply { orientation=LinearLayout.VERTICAL }
        leftPane.addView(createSceneHeader())
        leftPane.addView(alleyPane,LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,0,1f))
        split.addView(leftPane,LinearLayout.LayoutParams(0,LayoutParams.MATCH_PARENT,1f))
        split.addView(cabinPane,LinearLayout.LayoutParams(0,LayoutParams.MATCH_PARENT,1f))
        alleyPane.addView(map,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        alleyPane.addView(guides,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
        populateCabin(interiorPage)
        populateAlleyHud(alleyPane)
        populateMacroBanner(alleyPane)
        return split
    }

    /** 3D 안내와 진단 토글을 구성합니다. */
    private fun createSceneHeader(): LinearLayout {
        val modes = LinearLayout(context)
        configure(diagnosticsButton,"진단 정보",::toggleDiagnostics)
        modes.addView(sceneHeader, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 2f))
        modes.addView(diagnosticsButton, buttonParams())
        return modes
    }

    private fun showTab(labelTab: Boolean) {
        interiorPage.visibility=if(labelTab) GONE else VISIBLE
        labelPanel.visibility=if(labelTab) VISIBLE else GONE
        tabInterior.isEnabled=labelTab; tabLabel.isEnabled=!labelTab
        tabInterior.text=(if(!labelTab) "● " else "")+"실내 조명"
        tabLabel.text=(if(labelTab) "● " else "")+"라벨 장애물·AI 판단"
    }

    /** 라벨/연습/기본 장면을 3D 세계에 적용하고 정차 상태 판단을 AI 로 다듬어 읽어 줍니다. */
    private fun applyScene(option: ScenarioCatalog.Option, a: SceneAnalysis) {
        endAmbientDemo(); sideWarning.reset()
        cancelMacro(); gate.reset(); coach.reset(); consoleDriver.stop(); ai.cancel()
        keyboardMode=false; virtualKeyboardMode=false; virtualDrive.reset()
        demoMode=false; planDemo=false; planDemoSpeed=0.0; demoComplete=false; demoSeconds=0.0
        running=false; scenarioStarted=false; lastFrame=0
        val labelMode = option.kind != ScenarioCatalog.Kind.DEFAULT
        analysis = if (labelMode) a else null
        shownAnalysis = a
        world = a.freshWorld()
        lastGap = world.clearance()
        livePlan = a.plan; planAt = 0L
        decision = a.decision; decisionKey = a.decision.key; keySince = SystemClock.elapsedRealtime()
        aiAskedKey = ""; spokenKey = ""; lastSpeech = a.decision.speech
        sceneHeader.show(if (labelMode) "라벨 장면 · ${a.title}" else "3D 골목 · 좌우 간격은 가상 벽 기준")
        android.util.Log.i("OnSenseLabel", "SCENE_APPLIED title=${a.title} walls=${world.leftWallX}..${world.rightWallX} " +
            "obstacles=${world.parkedCars.size} plan=${a.plan.outcome} distanceCheck=${a.report?.summary()}")
        render(SystemClock.elapsedRealtime())
        // 정차 상태에서 장면 판단: AI 문장(실패 시 규칙 문장)을 읽어 줌
        aiAskedKey = a.decision.key
        askAi(a.decision, speakResult = true)
        // Qwen 이 느리면(PC CPU 10~20초) 3.5초 뒤 규칙 문장을 먼저 읽음. AI 문장은 도착 후 [판단 음성 듣기]로 재생
        postDelayed({
            if (analysis === a && !running && spokenKey != a.decision.key) {
                spokenKey = a.decision.key; speak(a.decision.key, a.decision.speech)
            }
        }, 3500)
    }

    private fun speak(key: String, text: String) {
        if (text.isBlank()) return
        lastSpeech = text
        coach.speakNow(key, text)
    }

    private fun askAi(d: AlleyDecisionEngine.Decision, speakResult: Boolean) {
        labelPanel.showAiPending(ai.providerText)
        ai.request(d) { result, final ->
            labelPanel.showAi(result, final)
            lastSpeech = final.speech
            if (speakResult && spokenKey != d.key && (!running || decisionKey == d.key)) {
                spokenKey = d.key
                speak(d.key, final.speech)
            }
        }
    }

    /** 라벨 장면에서 주행 중 규칙 판단 → (심각하면 즉시 규칙 음성, 아니면 AI 문장) */
    private fun updateJudgement(now: Long, input: Motion, gap: AlleyWorld.Clearance) {
        val a = analysis ?: return
        if (running && now - planAt >= 1000) { livePlan = a.replan(world); planAt = now }
        labelPanel.setVoiceStatus(coach.statusText)
        val d = a.decide(world, gap, input.speed, livePlan)
        decision = d
        labelPanel.showDecision(d, running, gap)
        if (d.key != decisionKey) { decisionKey = d.key; keySince = now }
        if (!active || !running || input.speed == null || spokenKey == d.key) return
        if (d.severity >= 2) {
            spokenKey = d.key; speak(d.key, d.speech)
            if (aiAskedKey != d.key) { aiAskedKey = d.key; askAi(d, speakResult = false) }
        } else if (aiAskedKey != d.key && now - keySince >= 400) {
            aiAskedKey = d.key; askAi(d, speakResult = true)
        } else if (aiAskedKey == d.key && now - keySince > 3500) {
            spokenKey = d.key; speak(d.key, d.speech)   // AI 응답 지연 → 규칙 문장
        }
    }

    /** 라벨 장면 자동 시연: 경로 계획 점을 따라 이동 (좁은 곳 감속, 정차점에서 정지) */
    private fun stepPlanDemo(dt: Double) {
        val a = analysis ?: return
        val pts = a.plan.points
        if (pts.size < 2) { finishPlanDemo(a); return }
        val y0 = pts.first().y
        val total = pts.last().y - y0
        val y = y0 + planDemoS
        val ahead = pts.filter { it.y >= y && it.y <= y + 4 }.minOfOrNull { it.min } ?: 1.0
        val remain = total - planDemoS
        planDemoSpeed = minOf(if (ahead < 0.5) 1.2 else 2.5, maxOf(0.3, remain * 0.8))
        planDemoS = minOf(total, planDemoS + planDemoSpeed * dt)
        val ny = y0 + planDemoS
        val i = pts.indexOfFirst { it.y >= ny }.let { if (it <= 0) 1 else it }
        val p0 = pts[i - 1]; val p1 = pts[i]
        val k = if (p1.y - p0.y <= 1e-9) 1.0 else ((ny - p0.y) / (p1.y - p0.y)).coerceIn(0.0, 1.0)
        world.restore(p0.x + (p1.x - p0.x) * k, ny, p0.heading + (p1.heading - p0.heading) * k, false,
            world.simulationSeconds + dt)
        if (planDemoS >= total - 1e-6) finishPlanDemo(a)
    }

    private fun finishPlanDemo(a: SceneAnalysis) {
        planDemoSpeed = 0.0; demoComplete = true; running = false; planDemo = false
        spokenKey = "done"; speak("done", a.doneSpeech())
    }

    private fun toggleDiagnostics() {
        diagnostics=!diagnostics
        diagnosticsPanel.visibility=if(diagnostics) VISIBLE else GONE
        locationBar.visibility=if(diagnostics) VISIBLE else GONE
        diagnosticsButton.text=if(diagnostics) "진단 접기" else "진단 정보"
    }

    /** 실내 그림과 실제 조명 시연 스위치를 배치합니다. */
    private fun populateCabin(cabinPane: LinearLayout) {
        cabinPane.addView(label("ON-SENSE · 앰비언트 경고", 15, teal))
        cabinPane.addView(interior, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        cabinPane.addView(sceneCaption)
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
    }

    /** 3D 위에 가상 좌우 간격과 주의 문구를 배치합니다. */
    private fun populateAlleyHud(alleyPane: FrameLayout) {
        title.setTypeface(title.typeface, Typeface.BOLD)
        hud.setPadding(dp(8),dp(2),dp(8),dp(2))
        val gaps = LinearLayout(context).apply {
            addView(leftGap, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(rightGap, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        hud.addView(gaps); hud.addView(warning)
        warning.textSize=14f; warning.maxLines=1
        leftGap.textSize=15f; rightGap.textSize=15f
        alleyPane.addView(hud, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(14), dp(12), dp(14), 0)
        })
    }

    /** 매크로 단계·설명·가상 거리 배너를 배치합니다. */
    private fun populateMacroBanner(alleyPane: FrameLayout) {
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
    }

    private fun createControls(): LinearLayout {
        val controls=panel()
        controls.addView(macroStatus)
        controls.addView(createGearControls())
        controls.addView(createMacroConfirmation())
        controls.addView(createActions())
        controls.addView(label("R 선택 후 ↑/W: 후진 · ↓/S: 브레이크 · Space: 정지 · 거리·매크로는 가상입니다.",12,muted))
        return controls
    }

    /** 기존 Console 기어 요청 버튼을 구성합니다. */
    private fun createGearControls(): LinearLayout {
        val gears = LinearLayout(context)
        for (value in ConsoleDriveModel.Gear.values()) {
            val button = Button(context)
            configure(button,value.name) { requestGear(value) }
            gearButtons[value] = button
            gears.addView(button, buttonParams())
        }
        return gears
    }

    /** Shared by gear buttons and the R shortcut; changes direction only with fresh stopped input. */
    private fun requestGear(value: ConsoleDriveModel.Gear) {
        if(!active || macroBusy) return
        if(virtualKeyboardMode) {
            if(virtualDrive.select(value)) {
                if(!world.contact) { running=true; scenarioStarted=true; lastFrame=0 }
                consoleMessage="가상 "+value.name+" 선택 · 실제 기어 "+(freshGear(SystemClock.elapsedRealtime())?.name ?: "--")
                android.util.Log.i("CarTalkKeyboard","VIRTUAL_GEAR_SELECTED gear="+value.name)
            } else consoleMessage="가상 차량 정지 후 기어를 선택하세요"
            requestFocus(); render(SystemClock.elapsedRealtime())
            return
        }
        if(!keyboardMode || !consoleDriver.connected) return
        val now=SystemClock.elapsedRealtime()
        val current=motion(now).speed
        if(current==null || (value!=ConsoleDriveModel.Gear.N && abs(current)>.01)) {
            consoleMessage="정지 후 기어를 선택하세요 · 최신 속도 수신 필요"
        } else {
            consoleDriver.select(value,current)
            consoleMessage=value.name+" 요청 · 수신 확인 중"
            if(!world.contact) { running=true; scenarioStarted=true; lastFrame=0 }
        }
        requestFocus()
        render(now)
    }

    /** 실행 확인과 취소 버튼을 구성합니다. */
    private fun createMacroConfirmation(): LinearLayout {
        confirmation.addView(label("가상 매크로를 실행할까요?\n미러 접기·창문 개방·비상등 3회는 시뮬레이션입니다. 실제 차량은 제어하지 않습니다.", 16))
        val decisions = LinearLayout(context)
        configure(confirm, "가상 실행") { executeMacro() }
        decisions.addView(confirm, buttonParams())
        decisions.addView(Button(context).apply {
            configure(this, "취소") {
                cancelMacro(); render(SystemClock.elapsedRealtime())
            }
        }, buttonParams())
        confirmation.addView(decisions)
        return confirmation
    }

    private fun createActions(): LinearLayout {
        configure(start,"SDK 연동 시작",::toggleSdkSimulation)
        configure(demo,"자동 시연(가상)") { prepareScene(true); render(SystemClock.elapsedRealtime()) }
        configure(keyboard,"Console 키보드 연결") { prepareKeyboard(); render(SystemClock.elapsedRealtime()) }
        configure(virtualKeyboard,"가상 키보드 시연",::prepareVirtualKeyboard)
        configure(reset,"초기화",::resetScene)
        configure(macro,"패스 매크로(SIM)",::requestMacroConfirmation)
        return LinearLayout(context).apply {
            for(button in listOf(start,demo,keyboard,virtualKeyboard,reset,macro)) addView(button,buttonParams())
        }
    }

    private fun toggleSdkSimulation() {
        if(keyboardMode || virtualKeyboardMode || demoMode || world.contact || world.y>=world.endStopY) prepareScene(false)
        else {
            cancelMacro(); gate.reset()
            running=!running; scenarioStarted=true; lastFrame=0
            if(!running) endAmbientDemo()
        }
        render(SystemClock.elapsedRealtime())
    }

    private fun resetScene() {
        endAmbientDemo(); sideWarning.reset()
        cancelMacro(); gate.reset(); coach.reset(); world.reset(); consoleDriver.stop(); virtualDrive.reset()
        running=false; scenarioStarted=false; demoComplete=false; demoSeconds=0.0; lastFrame=0
        resetJudgement()
        render(SystemClock.elapsedRealtime())
    }

    private fun requestMacroConfirmation() {
        render(SystemClock.elapsedRealtime())
        if(!macroState.canConfirm) return
        running=false; coach.silence()
        if(keyboardMode) consoleDriver.release(AlleyKeyboardDriver.Control.ACCELERATE)
        if(virtualKeyboardMode) virtualDrive.stop()
        macroStage=0
        confirmation.visibility=VISIBLE
        render(SystemClock.elapsedRealtime())
    }

    private fun prepareScene(synthetic: Boolean) {
        endAmbientDemo(); sideWarning.reset()
        cancelMacro(); gate.reset(); coach.reset(); world.reset(); consoleDriver.stop()
        keyboardMode = false; virtualKeyboardMode=false; virtualDrive.reset()
        demoMode = synthetic; demoSeconds = 0.0; demoComplete = false
        resetJudgement()
        planDemo = synthetic && analysis != null; planDemoS = 0.0; planDemoSpeed = 0.0
        if (synthetic && analysis == null) world.restore(0.0, 11.0, 0.0, false)
        scenarioStarted = true; running = true; lastFrame = 0
    }
    private fun resetJudgement() {
        ai.cancel(); planDemo = false; planDemoSpeed = 0.0
        livePlan = analysis?.plan; planAt = 0L
        decisionKey = ""; aiAskedKey = ""; spokenKey = ""
    }
    private fun prepareKeyboard() {
        prepareScene(false)
        keyboardMode = true
        lastKeyboardInput = "키 입력 대기 · Running Devices 앱 화면에서 ↑/W 길게"
        consoleDriver.start()
        requestFocus()
    }

    /** Explicit local mode for reverse demos while the actual car stays parked. */
    private fun prepareVirtualKeyboard() {
        if(!active || restricted || !debugEmulator || !parkConfirmed()) return
        prepareScene(false)
        virtualKeyboardMode=true
        virtualDrive.reset()
        consoleMessage="가상 키보드 · 실제 SDK/Console 값 변경 없음"
        lastKeyboardInput="가상 D/R 선택 후 ↑/W 가속 · R 키: 가상 후진 기어"
        requestFocus()
        render(SystemClock.elapsedRealtime())
    }

    fun drivingWindowFocusChanged(focused: Boolean) {
        windowFocused = focused
        if (!focused && (keyboardMode || virtualKeyboardMode)) {
            endAmbientDemo()
            consoleDriver.stop(); virtualDrive.stop(); running = false; scenarioStarted = false
            gate.reset(); cancelMacro("키보드 운전 일시정지 · 버튼으로 다시 시작하세요")
            render(SystemClock.elapsedRealtime())
        }
    }

    fun handleDrivingKey(event: KeyEvent): Boolean {
        if (!active || (!keyboardMode && !virtualKeyboardMode) || !debugEmulator) return false
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
            val accepted = active && windowFocused && (virtualKeyboardMode || consoleDriver.connected)
            val action = if(event.action == KeyEvent.ACTION_UP) "KEY_UP" else "KEY_DOWN"
            lastKeyboardInput = (if(virtualKeyboardMode) "가상 " else "")+"${KeyEvent.keyCodeToString(event.keyCode)} " +
                (if(event.action == KeyEvent.ACTION_UP) "뗌" else "눌림") +
                if(accepted) " · 키 수신됨" else " · 연결/포커스 확인 필요"
            android.util.Log.i("CarTalkKeyboard",
                "$action code=${event.keyCode} control=$control accepted=$accepted running=$running")
        }
        if (event.action == KeyEvent.ACTION_UP) {
            if(control!=null) {
                if(virtualKeyboardMode) virtualDrive.release(control) else consoleDriver.release(control)
            }
            if(event.isCanceled) { virtualDrive.stop(); consoleDriver.stop() }
        } else if (event.action == KeyEvent.ACTION_DOWN && windowFocused && (virtualKeyboardMode || consoleDriver.connected)) {
            if (event.keyCode == KeyEvent.KEYCODE_R && event.repeatCount == 0) requestGear(ConsoleDriveModel.Gear.R)
            else if (control != null && (running ||
                control == AlleyKeyboardDriver.Control.BRAKE || control == AlleyKeyboardDriver.Control.STOP))
                if(virtualKeyboardMode) virtualDrive.press(control) else consoleDriver.press(control)
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
    fun release() { ambient.close(); consoleDriver.close(); ai.close(); labelPanel.close() }

    fun setLocationAction(action: () -> Unit) { locationAction = action }
    fun showLocation(message: String, receiving: Boolean) {
        locationStatus.show(message)
        locationControl.text = if (receiving) "위치 수신 중지" else "위치 수신 시작"
    }

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
        if(value && virtualKeyboardMode) {
            virtualDrive.stop(); running=false; scenarioStarted=false
        }
        if (value && !emulator) running = false
        render(SystemClock.elapsedRealtime())
    }
    fun setActive(value: Boolean) {
        if (active == value) return
        active = value; ambient.visible(value); main.removeCallbacks(frame); lastFrame = 0
        if (value) {
            map.onResume()
            coach.start(); main.post(frame)
        }
        else {
            endAmbientDemo()
            running = false; scenarioStarted = false; gate.reset(); cancelMacro(); consoleDriver.stop(); virtualDrive.stop()
            ai.cancel(); map.onPause(); coach.stop(); speed = null; steering = null; rawSteering = null; brake = null
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
        scheduleMacroStep(token,0) { macroStage = 1; macroMessage = "SIM  1/3 · 사이드미러 접기 시뮬레이션" }
        scheduleMacroStep(token,2200) { macroStage = 2; macroMessage = "SIM  2/3 · 조수석 창문 미세 개방 시뮬레이션" }
        for (i in 0..5) scheduleMacroStep(token,4400L + i * 500) {
            macroStage = 3; hazardOn = i % 2 == 0
            macroMessage = "SIM  3/3 · 비상등 가상 점멸 ${i / 2 + 1}/3"
        }
        scheduleMacroStep(token,7600) {
            macroBusy = false; macroStage = 4
            macroMessage = "가상 매크로 완료 · 미러·창문·비상등 실제 차량 제어 없음"
            android.util.Log.i("CarTalkMacro", "SIMULATION_COMPLETED; actuatorWrites=0")
        }
    }

    /** Token and stopped-state checks apply to every scheduled macro step. */
    private fun scheduleMacroStep(token: Int,delay: Long,action: () -> Unit) {
        main.postDelayed({
            if(token!=macroToken || !macroBusy) return@postDelayed
            if(!stoppedForMacro()) {
                cancelMacro("가상 매크로 취소 · 정차·브레이크 유지 필요")
                render(SystemClock.elapsedRealtime())
                return@postDelayed
            }
            action(); render(SystemClock.elapsedRealtime())
        },delay)
    }

    private fun displayedClearance(): AlleyWorld.Clearance = world.clearance()

    /** Coordinates rendering; each part owns only its labels, controls or presentation. */
    private fun render(now: Long) {
        val input=motion(now)
        val gap=displayedClearance(); lastGap=gap; map.publish(world,world.clearance())
        renderVehicleInfo(now,input)
        publishFeature2(now,gap)
        updateMacroDetection(now,input,gap)
        renderInputDiagnostics(input)
        renderClearanceLabels(gap)
        val guide=if(running && analysis==null && input.speed!=null && input.steering!=null) world.guidance(input.speed) else null
        updateJudgement(now,input,gap)
        val judged=decision?.takeIf { analysis!=null && running && !world.contact }
        val message=judged?.let { it.action.ko+" · "+it.headline } ?: guidanceMessage(input,gap,guide)
        renderGuidance(now,gap,message)
        renderMacroPresentation(gap)
        renderControls(now,input)
        announceGuidance(now,input,gap,guide,message)
    }

    /** 신선한 실제 SDK 값과 시연 값을 구분해 표시합니다. */
    private fun renderVehicleInfo(now: Long,input: Motion) {
        val v=input.speed
        vehicleInfo.render(
            speed?.takeIf { now - speedAt <= 3000 }?.let { abs(it) * 3.6f },
            rawSteering?.takeIf { now - steeringAt <= 3000 },
            if(virtualKeyboardMode) "가상 "+virtualDrive.gear.name else freshGear(now)?.name,
            brake?.takeIf { now - brakeAt <= 3000 },
            restricted, demoMode || virtualKeyboardMode, v?.let { (abs(it)*3.6).toFloat() }
        )
    }

    /** 정차·브레이크·간격 조건을 갱신하고 무효 실행을 취소합니다. */
    private fun updateMacroDetection(now: Long,input: Motion,gap: AlleyWorld.Clearance) {
        val v=input.speed
        if (macroBusy && (world.contact || gap.meters <= .3 || !stoppedForMacro()))
            cancelMacro("가상 매크로 취소 · 근접 경고 또는 정차·브레이크 상태를 확인하세요")
        val detect = active && scenarioStarted && (running || demoComplete || macroState.armed || confirmation.visibility == View.VISIBLE)
        macroState = if (detect) gate.observe(now, v, input.brake, gap.meters, !restricted && (!keyboardMode || parkConfirmed()), parkConfirmed())
            else { gate.reset(); AlleyMacroGate.State(0, false, false, false) }
        if (world.contact || gap.meters <= .3) macroState = macroState.copy(canConfirm = false)
        if (confirmation.visibility == View.VISIBLE && !macroState.armed)
            cancelMacro("감지 유효시간 만료 · 다시 시연하거나 조건을 확인하세요")
    }

    /** 입력 출처·조향·브레이크·키 상태를 진단에 표시합니다. */
    private fun renderInputDiagnostics(input: Motion) {
        val v=input.speed; val angle=input.steering
        vehicleText.show((v?.let { String.format(Locale.KOREA, "%.1f km/h", abs(it) * 3.6) } ?: "속도 수신 대기") +
            if (virtualKeyboardMode) "  ·  가상 키보드 (실제 차량 변경 없음)" else if (keyboardMode) "  ·  Console → SDK 수신" else if (demoMode) "  ·  가상 입력" else "  ·  차량 SDK 입력")
        keyboardInputText.visibility = if (keyboardMode || virtualKeyboardMode) View.VISIBLE else View.GONE
        keyboardInputText.show(lastKeyboardInput)
        inputText.show("조향 " + when {
            angle == null -> "미확인"; angle < -.02 -> "왼쪽"; angle > .02 -> "오른쪽"; else -> "중앙"
        } + "  ·  브레이크 " + when (input.brake) { true -> "눌림"; false -> "해제"; null -> "미확인" } +
            if (coach.ready) "  ·  음성 준비됨" else "  ·  음성 준비 안 됨")
    }

    /** 가상 좌우 간격과 등급 색을 표시합니다. */
    private fun renderClearanceLabels(gap: AlleyWorld.Clearance) {
        fun gapText(side: String, meters: Double) = side + " 가상 " +
            if(meters.isFinite()) String.format(Locale.KOREA,"%.2fm",meters) else "--"
        leftGap.show(gapText("왼쪽",gap.leftMeters)); rightGap.show(gapText("오른쪽",gap.rightMeters))
        sdkDiagnostics.show("SDK 차속 "+(speed?.let { it*3.6f }?.toString() ?: "--")+"km/h · 조향각 "+(rawSteering?.toString() ?: "--")+"°")
        sceneCaption.show("3D 가상 거리 · 실내 조명 연출")
        leftGap.setTextColor(SideClearanceWarning.level(gap.leftMeters).argb)
        rightGap.setTextColor(SideClearanceWarning.level(gap.rightMeters).argb)
    }

    /** 입력·근접·매크로 상태에 맞는 안내 문구를 선택합니다. */
    private fun guidanceMessage(input: Motion,gap: AlleyWorld.Clearance,guide: AlleyWorld.Guide?): String {
        val v=input.speed; val angle=input.steering
        return when {
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
    }

    /** 근접 경고와 상세 입력 안내를 표시합니다. */
    private fun renderGuidance(now: Long,gap: AlleyWorld.Clearance,message: String) {
        warning.visibility=if(gap.meters<=.8 || world.contact || (analysis!=null && running)) VISIBLE else GONE
        warning.show(message); warning.setTextColor(if (world.contact || gap.meters <= .3) Color.rgb(255, 127, 127) else ink)
        detail.show(if(virtualKeyboardMode) consoleMessage+" · ↑/W 전진·후진 · ↓/S 브레이크 · Space 정지"
            else if (keyboardMode) consoleMessage + " · 기어 수신 " + (freshGear(now)?.name ?: "미확인") + " · ↑/W 길게 가속 · ↓/S 브레이크 · ←A/→D 조향 · Space 정지"
            else if (demoMode) "앱 내부 가상 시연 · 차량 SDK 값이나 Console 상태를 바꾸지 않습니다."
            else "가상 최근접 ${String.format(Locale.KOREA, "%.2f", gap.meters)}m · ${gap.side}")
    }

    /** 매크로 단계 카드와 일반 HUD의 표시를 전환합니다. */
    private fun renderMacroPresentation(gap: AlleyWorld.Clearance) {
        macroStatus.show(if (macroMessage.isNotEmpty()) macroMessage else when {
            macroState.canConfirm -> "매크로 준비됨 · 사용자 확인 후 가상 실행"
            macroState.armed -> "조건 감지됨 · 정차하고 브레이크를 유지하세요"
            else -> "패스 조건: 10km/h 이하 · 브레이크 ${macroState.presses}/3회(15초) · 가상 근접 1.2m 이하"
        })
        val card = if(macroStage<0) null else AlleyMacroPresentation.card(macroStage, gap.meters, gap.approaching, world.contact, hazardOn)
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
    }

    /** 주행 제한·입력 신선도·매크로 상태에 따라 버튼을 갱신합니다. */
    private fun renderControls(now: Long,input: Motion) {
        val v=input.speed
        start.show(if (!keyboardMode && !virtualKeyboardMode && !demoMode && running) "SDK 일시정지" else "SDK 연동 시작")
        start.isEnabled = active && !restricted && !macroBusy
        demo.isEnabled = active && !restricted && debugEmulator && !macroBusy
        keyboard.isEnabled = active && !restricted && debugEmulator && !macroBusy
        virtualKeyboard.isEnabled=active && !restricted && debugEmulator && !macroBusy && parkConfirmed()
        reset.isEnabled = active && !restricted
        for ((value,button) in gearButtons) {
            val selected=if(virtualKeyboardMode) virtualDrive.gear else freshGear(now)
            button.text=(if(virtualKeyboardMode) "가상 " else "")+value.name+if(selected==value) " ✓" else ""
            button.isEnabled=active && debugEmulator && !macroBusy && selected!=value && v!=null &&
                ((virtualKeyboardMode && !restricted) || (keyboardMode && consoleDriver.connected)) &&
                (value==ConsoleDriveModel.Gear.N || abs(v)<=.01)
        }
        demo.text="3D 자동 시연"
        reset.text="3D 초기화"
        macroStatus.visibility=if(diagnostics || macroState.armed || macroBusy) VISIBLE else GONE
        macro.isEnabled = active && !macroBusy && macroState.canConfirm
        confirm.isEnabled = active && !macroBusy && macroState.canConfirm
    }

    /** 기존 음성 안내 키와 재생 조건을 유지합니다. */
    private fun announceGuidance(now: Long,input: Motion,gap: AlleyWorld.Clearance,guide: AlleyWorld.Guide?,message: String) {
        if (analysis != null) return   // 라벨 장면은 규칙·AI 판단 음성(updateJudgement)이 담당
        val v=input.speed; val angle=input.steering
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

    /** Actual API readback remains the source of vehicle-light application status. */
    private fun showAmbientStatus(message: String) {
        ambientDiagnostics.show(message)
        ambientStatus.show(when {
            message.contains("복원 재조회 일치") -> "차량 조명 · 원래 색 복원 확인"
            message.contains("색 변경 재조회 일치") -> "차량 조명 · 변경 재조회 일치"
            message.contains("현재값 일치") -> "차량 조명 · 현재 색 일치"
            message.contains("적용 미확인") -> "차량 조명 · 적용 미확인"
            message.contains("복원 대기") -> "차량 조명 · 정차 후 복원 대기"
            message.contains("꺼짐") -> "차량 조명 · 연동 꺼짐"
            else -> "차량 조명 · 상태 확인 중 (진단 정보)"
        })
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
