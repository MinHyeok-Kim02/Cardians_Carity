package com.example.pleostest

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Carity 튜토리얼 (별도 런처 앱).
 * 라벨/합성 장면에서 규칙 엔진 + AI(Claude/Qwen) 판단을 보여 주고,
 * 어디로 움직여야 하는지 노면 화살표로 표시한 뒤, 차가 미리 움직이는 모습(고스트)과 실제 따라 주행을 음성과 함께 보여 줍니다.
 * 단계: 상황 파악 → AI 판단 → 노면 방향 표시 → 미리 보기 → 따라 주행 → 정리
 */
class TutorialActivity : AppCompatActivity() {
    companion object { const val EXTRA_SCENARIO = "scenario" }

    private enum class Step(val title: String) {
        INTRO("1 상황 파악"), JUDGE("2 AI 판단"), PATH("3 노면 방향 표시"),
        GHOST("4 미리 보기"), FOLLOW("5 따라 주행"), DONE("6 정리")
    }

    private val ink = Color.rgb(235, 243, 250)
    private val muted = Color.rgb(170, 190, 205)
    private val teal = Color.rgb(68, 224, 191)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var catalog: ScenarioCatalog
    private lateinit var view: TutorialView
    private lateinit var coach: AlleyVoiceCoach
    private val ai = AiCoordinator()

    private var option: ScenarioCatalog.Option? = null
    private var analysis: SceneAnalysis? = null
    private var world: AlleyWorld? = null
    private var step = Step.INTRO
    private var stepStart = 0L
    private var lastTick = 0L
    private var paused = false
    private var voiceOn = true
    private var aiFinal: AiJudge.Final? = null
    private var aiResultAt = 0L
    // 고스트·자차 진행 (경로 계획 위 거리)
    private var ghostS = 0.0
    private var egoS = 0.0
    private var backS = 0.0
    private var waitAt = 0L
    private var lastKey = ""
    private var endSpoken = false
    private var judgeFallbackSpoken = false
    private var firstPass = 0L
    private var lastSpokenAt = 0L

    private val sceneButton by lazy { Button(this) }
    private val pauseButton by lazy { Button(this) }
    private val voiceButton by lazy { Button(this) }
    private val stepViews = LinkedHashMap<Step, TextView>()
    private val judgeText by lazy { label("", 14, ink) }
    private val aiText by lazy { label("", 13, muted) }
    private val infoText by lazy { label("", 12, muted) }
    private val voiceText by lazy { label("", 12, muted) }

    private val ticker = object : Runnable {
        override fun run() { tick(); main.postDelayed(this, 33) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        catalog = ScenarioCatalog(this)
        coach = AlleyVoiceCoach(this)
        view = TutorialView(this)
        setContentView(buildLayout())
        val initial = catalog.find(intent.getStringExtra(EXTRA_SCENARIO))
            ?: catalog.options.firstOrNull { it.kind == ScenarioCatalog.Kind.LABEL } ?: catalog.options.first()
        load(initial)
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply { setBackgroundColor(Color.rgb(11, 20, 32)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        root.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.55f))
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.rgb(9, 15, 22))
        }
        side.addView(label("CARITY 튜토리얼 · 좁은 길 통과", 16, teal).apply { setTypeface(typeface, Typeface.BOLD) })
        side.addView(label(if (CarityFeatures.aiEnabled) "판단 AI: ${ai.providerText} · 규칙 엔진이 최종 결정" else "판단: 규칙 엔진 (기기 안에서 즉시 판단)", 12, muted))
        side.addView(voiceText)
        configure(sceneButton, "장면 선택 ▾") { chooseScene() }
        side.addView(sceneButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        val steps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(6)) }
        for (s in Step.values()) {
            val t = label(s.title, 14, muted)
            stepViews[s] = t; steps.addView(t)
        }
        side.addView(steps)
        val scroll = ScrollView(this)
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(section("규칙 엔진 판단")); texts.addView(judgeText)
        texts.addView(section("AI 판단 · 음성 문장")); texts.addView(aiText)
        texts.addView(section("장면 정보")); texts.addView(infoText)
        scroll.addView(texts)
        side.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val restart = Button(this); configure(restart, "처음부터") { restart() }
        configure(pauseButton, "일시정지") { togglePause() }
        val next = Button(this); configure(next, "다음 단계 ▶") { nextStep() }
        configure(voiceButton, "음성 켬") { voiceOn = !voiceOn; voiceButton.text = if (voiceOn) "음성 켬" else "음성 끔"; if (!voiceOn) coach.silence() }
        side.addView(row(restart, pauseButton)); side.addView(row(next, voiceButton))
        root.addView(side, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return root
    }

    override fun onStart() {
        super.onStart()
        coach.start()
        lastTick = 0L
        main.post(ticker)
    }

    override fun onStop() {
        main.removeCallbacks(ticker)
        coach.stop(); ai.cancel()
        super.onStop()
    }

    override fun onDestroy() { ai.close(); io.shutdownNow(); super.onDestroy() }

    private fun chooseScene() {
        val opts = catalog.options
        AlertDialog.Builder(this).setTitle("튜토리얼 장면")
            .setItems(opts.map { it.title + "\n   " + it.subtitle }.toTypedArray()) { _, i -> load(opts[i]) }
            .setNegativeButton("닫기", null).show()
    }

    private fun load(o: ScenarioCatalog.Option) {
        option = o; sceneButton.text = o.title
        analysis = null; view.analysis = null; view.caption = "장면 분석 중…"; view.invalidate()
        io.execute {
            val r = runCatching { catalog.analyze(o) }
            main.post {
                r.onSuccess { a ->
                    analysis = a
                    infoText.text = (a.scene?.describe() ?: a.title) + "\n" + a.planText() +
                        (a.report?.let { "\n" + it.summary() } ?: "")
                    restart()
                }.onFailure { view.caption = "장면을 불러오지 못했습니다: ${it.message}"; view.invalidate() }
            }
        }
    }

    private fun restart() {
        val a = analysis ?: return
        world = a.freshWorld()
        ghostS = 0.0; egoS = 0.0; backS = 0.0; waitAt = 0L; lastKey = ""
        aiFinal = null; ai.cancel()
        view.analysis = a
        view.ego = null; view.ghost = null; view.ghostReverse = false; view.reverseHint = false
        view.pathReveal = 0f; view.oncomingTravel = 0.0; view.oncomingLaneX = null; view.gap = null; view.card = null
        view.cameraTargetY = startPoint(a).y + 2; view.snapCamera()
        judgeText.text = ""; aiText.text = ""
        paused = false; pauseButton.text = "일시정지"
        enter(Step.INTRO)
    }

    private fun startPoint(a: SceneAnalysis) = a.plan.points.firstOrNull()
        ?.let { TutorialView.Pose(it.x, it.y, it.heading) } ?: TutorialView.Pose(0.0, LabelSceneBuilder.EGO_START_Y, 0.0)

    private fun togglePause() {
        paused = !paused; pauseButton.text = if (paused) "계속" else "일시정지"
        if (paused) coach.silence()
    }

    private fun nextStep() {
        if (analysis == null) return
        val n = Step.values().getOrNull(step.ordinal + 1) ?: return
        enter(n)
    }

    private fun say(key: String, text: String) {
        if (!voiceOn || text.isBlank()) return
        coach.reset(); coach.speakNow(key, text)
        lastSpokenAt = SystemClock.elapsedRealtime()
    }

    private fun enter(s: Step) {
        val a = analysis ?: return
        step = s; stepStart = SystemClock.elapsedRealtime()
        for ((k, v) in stepViews) {
            v.setTextColor(if (k == s) teal else if (k.ordinal < s.ordinal) ink else muted)
            v.text = (if (k == s) "▶ " else if (k.ordinal < s.ordinal) "✓ " else "   ") + k.title
        }
        view.stepTitle = s.title
        when (s) {
            Step.INTRO -> {
                view.ego = startPoint(a); view.highlight = true
                view.caption = a.introSpeech()
                say("intro", a.introSpeech())
            }
            Step.JUDGE -> {
                view.highlight = false
                judgeFallbackSpoken = false
                val d = a.decision
                showRule(d)
                view.caption = "규칙 엔진과 ${ai.judge.provider.label} 이(가) 지금 상황을 판단합니다…"
                view.card = "규칙 판단: ${d.action.ko}\n${d.speech}"; view.cardSeverity = d.severity
                aiText.text = "${ai.providerText} 판단 요청 중…"
                ai.request(d) { r, f ->
                    aiFinal = f; aiResultAt = SystemClock.elapsedRealtime()
                    aiText.text = if (r.ok) "${r.provider} · ${r.latencyMs}ms · AI 권고 ${r.action?.ko ?: "-"}" +
                        (if (f.aiAgrees == false) " (규칙과 다름 → 규칙 유지)" else "") +
                        "\n음성: ${f.speech}\n근거: ${r.reason ?: "-"}"
                    else "${r.provider} 연결 실패 → 규칙 엔진 문장 사용\n${r.error}"
                    if (step == Step.JUDGE) {
                        view.caption = "판단 결과 (${f.source})"
                        view.card = "${f.action.ko}\n${f.speech}"
                        if (!judgeFallbackSpoken || f.speech != a.decision.speech) say("judge2", f.speech)
                    }
                }
            }
            Step.PATH -> {
                view.card = null
                view.caption = a.pathSpeech()
                say("path", a.pathSpeech())
            }
            Step.GHOST -> {
                view.pathReveal = 1f
                ghostS = 0.0; backS = 0.0; waitAt = 0L; endSpoken = false; firstPass = 0L
                view.ghost = startPoint(a); view.ghostReverse = false; view.oncomingTravel = 0.0; view.oncomingLaneX = null
                view.caption = "투명한 차가 먼저 움직입니다. 화살표를 따라 어떻게 지나가는지 보세요."
                say("ghost", "투명한 차가 먼저 지나가는 길을 보여 드릴게요.")
            }
            Step.FOLLOW -> {
                view.ghost = null; view.reverseHint = false
                egoS = 0.0; backS = 0.0; waitAt = 0L; lastKey = ""; endSpoken = false; firstPass = 0L
                view.ego = startPoint(a); view.oncomingTravel = 0.0; view.oncomingLaneX = null
                view.caption = "이제 직접 따라가 봅니다. 좌우 간격과 음성 안내를 확인하세요."
                say("follow", "이제 같은 길로 천천히 따라가 보겠습니다.")
            }
            Step.DONE -> {
                view.gap = null; view.reverseHint = false
                view.caption = a.doneSpeech()
                view.card = String.format(Locale.KOREA, "%s\n최소 간격 · 왼쪽 %.2fm / 오른쪽 %.2fm",
                    a.planText(), a.plan.minLeft, a.plan.minRight)
                view.cardSeverity = when (a.plan.outcome) {
                    CorridorPlanner.Outcome.CLEAR -> 0; CorridorPlanner.Outcome.YIELD -> 1; else -> 2 }
                say("done", a.doneSpeech())
            }
        }
    }

    private fun showRule(d: AlleyDecisionEngine.Decision) {
        judgeText.text = "${d.action.ko} · ${d.headline}\n음성: ${d.speech}\n근거: ${d.reasons.joinToString(" / ")}"
        judgeText.setTextColor(when (d.severity) { 0 -> ink; 1 -> Color.rgb(255, 196, 92); else -> Color.rgb(255, 127, 127) })
    }

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTick == 0L) 0.0 else ((now - lastTick) / 1000.0).coerceAtMost(0.1)
        lastTick = now
        val a = analysis
        if (a != null && !paused) advance(a, now, dt)
        view.tick()
        val v = coach.statusText
        if (voiceText.text.toString() != v) voiceText.text = v
    }

    private fun elapsed(now: Long) = (now - stepStart) / 1000.0

    private fun advance(a: SceneAnalysis, now: Long, dt: Double) {
        val t = elapsed(now)
        when (step) {
            Step.INTRO -> {
                // 장면 전체를 훑어보도록 카메라를 앞쪽 장애물까지 이동했다가 돌아옴
                val far = (a.world.parkedCars.maxOfOrNull { it.y } ?: 20.0).coerceAtMost(startPoint(a).y + 30)
                val k = (sin01(t / 5.0))
                view.cameraTargetY = startPoint(a).y + 2 + (far - startPoint(a).y - 6).coerceAtLeast(0.0) * k
                if (t > 6.5) enter(Step.JUDGE)
            }
            Step.JUDGE -> {
                // AI 가 3.5초 안에 답하지 않으면 규칙 문장을 먼저 읽음
                if (aiFinal == null && !judgeFallbackSpoken && t > 3.5) {
                    judgeFallbackSpoken = true
                    view.caption = "규칙 엔진 판단 (AI 응답 대기 중)"
                    say("judge", a.decision.speech)
                }
                view.cameraTargetY = startPoint(a).y + 2
                val done = aiFinal != null && now - aiResultAt > 4500
                if (done || t > 14) enter(Step.PATH)
            }
            Step.PATH -> {
                view.pathReveal = (t / 3.0).toFloat().coerceIn(0f, 1f)
                val pts = a.plan.points
                val len = if (pts.size < 2) 0.0 else pts.last().y - pts.first().y
                view.cameraTargetY = startPoint(a).y + 2 + (len - 8).coerceAtLeast(0.0) * view.pathReveal * 0.8
                if (t > 7) enter(Step.GHOST)
            }
            Step.GHOST -> {
                val finished = drive(a, now, dt, isGhost = true)
                view.cameraTargetY = (view.ghost?.y ?: 0.0) + 2
                if (finished) enter(Step.FOLLOW)
            }
            Step.FOLLOW -> {
                val finished = drive(a, now, dt, isGhost = false)
                view.cameraTargetY = (view.ego?.y ?: 0.0) + 2
                if (finished) enter(Step.DONE)
            }
            Step.DONE -> Unit
        }
    }

    private fun sin01(x: Double) = if (x >= 1) 0.0 else kotlin.math.sin(x * Math.PI)

    /**
     * 고스트/자차 이동 공통: 경로를 따라 이동 → (양보) 정차 후 대향차 통과 → (막힘+대향차) 후진.
     * 반환: 이 단계가 끝났는지
     */
    private fun drive(a: SceneAnalysis, now: Long, dt: Double, isGhost: Boolean): Boolean {
        val pts = a.plan.points
        val len = if (pts.size < 2) 0.0 else pts.last().y - pts.first().y
        fun place(p: TutorialView.Pose) { if (isGhost) view.ghost = p else view.ego = p }
        var s = if (isGhost) ghostS else egoS
        var speed = 0.0
        if (s < len - 1e-6) {
            val (pose, ahead) = moveAlong(a, s)
            val base = if (isGhost) 4.0 else 2.0
            speed = minOf(if (ahead < 0.5) base * 0.5 else base, maxOf(0.4, (len - s) * 1.2))
            s = minOf(len, s + speed * dt)
            if (isGhost) ghostS = s else egoS = s
            place(pose)
            if (!isGhost) updateGap(a, pose, speed, speak = true)
            return false
        }
        // 경로 끝: 결과에 따라 마무리 동작
        if (waitAt == 0L) waitAt = now
        val waited = (now - waitAt) / 1000.0
        val end = moveAlong(a, len).first
        place(end)
        val firstFrame = !endSpoken
        endSpoken = true
        return when (a.plan.outcome) {
            CorridorPlanner.Outcome.CLEAR -> {
                if (!isGhost) updateGap(a, end, 0.0, speak = false)
                waited > 0.6
            }
            CorridorPlanner.Outcome.YIELD -> {
                if (view.oncomingLaneX == null) view.oncomingLaneX = TutorialView.passLane(a, end.x)
                if (firstFrame && !isGhost) say("yield", "여기서 정차하고 마주 오는 차를 먼저 보내세요.")
                if (waited > 0.8) view.oncomingTravel += 5.0 * dt
                if (!isGhost) updateGap(a, end, 0.0, speak = false)
                val f = a.frontal.firstOrNull()
                val passed = f == null || f.y - view.oncomingTravel + f.length / 2 < end.y - 6
                if (passed && firstPass == 0L) firstPass = now
                passed && (now - firstPass) > 1000
            }
            CorridorPlanner.Outcome.BLOCKED -> {
                if (a.frontal.isEmpty()) {
                    if (firstFrame && !isGhost) say("stop", "여기서 정차하세요. 앞이 차 폭보다 좁습니다.")
                    if (!isGhost) updateGap(a, end, 0.0, speak = false)
                    return waited > 2.5
                }
                // 대향차 + 비켜설 곳 없음 → 5m 후진 시연
                view.reverseHint = true
                if (isGhost) view.ghostReverse = true
                if (firstFrame && !isGhost) say("reverse", "비켜설 곳이 없습니다. 천천히 후진해서 넓은 곳에서 양보하세요.")
                backS = minOf(5.0, backS + 1.2 * dt)
                val p = TutorialView.Pose(end.x, end.y - backS, end.heading)
                place(p)
                if (!isGhost) updateGap(a, p, -1.2, speak = false)
                backS >= 5.0 && waited > 4.5
            }
        }
    }

    /** 경로 위 거리 s 의 자세와 앞 4m 안 최소 간격 */
    private fun moveAlong(a: SceneAnalysis, s: Double): Pair<TutorialView.Pose, Double> {
        val pts = a.plan.points
        if (pts.size < 2) return startPoint(a) to 1.0
        val y = pts.first().y + s
        val i = pts.indexOfFirst { it.y >= y }.let { if (it <= 0) 1 else it }
        val p0 = pts[i - 1]; val p1 = pts[i]
        val k = if (p1.y - p0.y <= 1e-9) 1.0 else ((y - p0.y) / (p1.y - p0.y)).coerceIn(0.0, 1.0)
        val ahead = pts.filter { it.y >= y && it.y <= y + 4 }.minOfOrNull { it.min } ?: 1.0
        return TutorialView.Pose(p0.x + (p1.x - p0.x) * k, y, p0.heading + (p1.heading - p0.heading) * k) to ahead
    }

    /** 자차 간격 계산(AlleyWorld 같은 거리 엔진) → 표시 + 규칙 판단 음성 */
    private fun updateGap(a: SceneAnalysis, p: TutorialView.Pose, speed: Double, speak: Boolean) {
        val w = world ?: return
        w.restore(p.x, p.y, p.heading, false)
        val g = w.clearance()
        view.gap = g
        if (!speak) return
        val d = a.decide(w, g, speed, a.plan)
        showRule(d)
        val now = SystemClock.elapsedRealtime()
        if (d.key != lastKey && (d.severity >= 2 || now - lastSpokenAt > 2500)) {
            lastKey = d.key
            view.card = "${d.action.ko}\n${d.speech}"; view.cardSeverity = d.severity
            if (d.action != AlleyDecisionEngine.Action.PROCEED) say("f:" + d.key, d.speech)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Int, color: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(dp(3), dp(2), dp(3), dp(2))
    }
    private fun section(title: String) = label(title, 12, teal).apply {
        background = GradientDrawable().apply { setColor(Color.argb(60, 68, 224, 191)); cornerRadius = dp(6).toFloat() }
    }
    private fun configure(button: Button, value: String, action: () -> Unit) {
        button.text = value; button.textSize = 14f; button.isAllCaps = false
        button.minWidth = 0; button.minimumWidth = 0
        button.setOnClickListener { action() }
    }
    private fun row(a: View, b: View) = LinearLayout(this).apply {
        addView(a, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(2), dp(2), dp(2), 0) })
        addView(b, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(2), dp(2), dp(2), 0) })
    }
}
