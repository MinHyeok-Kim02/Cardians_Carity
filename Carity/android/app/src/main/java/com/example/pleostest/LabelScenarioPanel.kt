package com.example.pleostest

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 시뮬레이션 오른쪽 [라벨 장애물·AI 판단] 탭.
 *  ① 라벨 장면 선택(앱 내장 / PC 라벨 서버) → 3D 골목 장애물로 배치
 *  ② 거리 계산 검증 결과(라벨 투영 기대값 vs AlleyWorld 측정값)
 *  ③ 경로 계획 · 규칙 엔진 판단 · AI(Claude/Qwen) 판단과 음성 문장
 */
class LabelScenarioPanel(context: Context, private val host: Host) : ScrollView(context) {
    interface Host {
        /** 분석이 끝난 장면을 3D 시뮬레이션에 적용 */
        fun applyAnalysis(option: ScenarioCatalog.Option, analysis: SceneAnalysis)
        /** 판단 음성을 지금 다시 듣기 */
        fun replayJudgement()
        /** AI 판단 다시 요청 */
        fun requestAi()
    }

    private val ink = Color.rgb(235, 243, 250)
    private val muted = Color.rgb(170, 190, 205)
    private val teal = Color.rgb(68, 224, 191)
    private val red = Color.rgb(255, 127, 127)
    private val amber = Color.rgb(255, 196, 92)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    val catalog = ScenarioCatalog(context)
    var selected: ScenarioCatalog.Option = catalog.options.first(); private set

    private val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(8)) }
    private val provider = label("", 12, muted)
    private val choose = Button(context)
    private val serverButton = Button(context)
    private val defaultButton = Button(context)
    private val tutorialButton = Button(context)
    private val sceneText = label("장면을 선택하면 라벨 객체가 3D 골목 장애물로 배치됩니다.", 13)
    private val distText = label("", 12, muted)
    private val planText = label("", 13)
    private val liveText = label("", 13, muted)
    private val ruleText = label("", 14)
    private val aiText = label("", 13, muted)
    private val speakButton = Button(context)
    private val aiButton = Button(context)
    private val status = label("", 12, muted)

    init {
        isFillViewport = true
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(label("라벨 장애물 · AI 판단", 15, teal).apply { setTypeface(typeface, Typeface.BOLD) })
        body.addView(provider)
        body.addView(row(choose, serverButton))
        body.addView(row(defaultButton, tutorialButton))
        body.addView(section("장면"), wrap()); body.addView(sceneText)
        body.addView(section("거리 계산 검증"), wrap()); body.addView(distText)
        body.addView(section("경로 계획"), wrap()); body.addView(planText); body.addView(liveText)
        body.addView(section("규칙 엔진 판단 (최종 안전 결정)"), wrap()); body.addView(ruleText)
        body.addView(section("AI 판단 · 음성 문장"), wrap()); body.addView(aiText)
        body.addView(row(speakButton, aiButton))
        body.addView(status)
        configure(choose, "장면 선택 ▾") { showChooser() }
        configure(serverButton, "PC 라벨 불러오기") { browseServer("") }
        configure(defaultButton, "기본 골목으로") { select(catalog.options.first()) }
        configure(tutorialButton, "튜토리얼로 보기") { openTutorial() }
        configure(speakButton, "판단 음성 듣기") { host.replayJudgement() }
        configure(aiButton, "AI 다시 판단") { host.requestAi() }
        status.text = if (CarityFeatures.pcServerEnabled) "PC 라벨 서버: ${catalog.repo.serverUrl}" else "라벨 장면: 앱 내장 ${catalog.repo.bundledSamples().size}개"
        if (!CarityFeatures.pcServerEnabled) serverButton.visibility = GONE
        if (!CarityFeatures.aiEnabled) aiButton.visibility = GONE
    }

    private var providerLabel = ""
    private var voiceStatus = ""
    fun setProvider(text: String) { providerLabel = text; updateProvider() }
    fun setVoiceStatus(text: String) { if (voiceStatus != text) { voiceStatus = text; updateProvider() } }
    private fun updateProvider() {
        provider.text = "판단 AI: $providerLabel · 규칙 엔진이 최종 결정\n$voiceStatus"
    }

    fun select(option: ScenarioCatalog.Option) {
        selected = option
        choose.text = option.title
        status.text = "장면 분석 중…"
        io.execute {
            val result = runCatching { catalog.analyze(option) }
            main.post {
                result.onSuccess { a ->
                    status.text = option.subtitle
                    showAnalysis(a)
                    host.applyAnalysis(option, a)
                }.onFailure { e ->
                    status.text = "장면을 불러오지 못했습니다: ${e.message}"
                    status.setTextColor(red)
                }
            }
        }
    }

    fun showAnalysis(a: SceneAnalysis) {
        status.setTextColor(muted)
        sceneText.text = a.scene?.let { s ->
            s.describe() + "\n" + s.projected.joinToString("\n") { p ->
                String.format(Locale.KOREA, "· %s %s %.1fm 앞 · 안쪽 모서리 %+.2fm%s", p.label,
                    if (p.inPath) "가운데" else if (p.centerX < 0) "왼쪽" else "오른쪽", p.zNear, p.xInner,
                    if (p.truncatedBottom) " (보닛에 가려 최소거리)" else "")
            }
        } ?: (a.title + String.format(Locale.KOREA, " · 도로폭 %.1fm · 장애물 %d개",
            a.world.rightWallX - a.world.leftWallX, a.world.parkedCars.size))
        val report = a.report
        distText.text = if (report == null) "라벨 장면이 아니라 검증 생략 (기존 AlleyWorld 거리 엔진 그대로)"
            else report.summary() + "\n" + report.rows.joinToString("\n") { it.text() }
        distText.setTextColor(if (report == null || report.allOk) muted else red)
        planText.text = a.planText()
        planText.setTextColor(when (a.plan.outcome) {
            CorridorPlanner.Outcome.CLEAR -> ink
            CorridorPlanner.Outcome.YIELD -> amber
            CorridorPlanner.Outcome.BLOCKED -> red
        })
        showDecision(a.decision, live = false)
        aiText.text = "AI 판단 대기"
    }

    /** 주행 중 실시간 판단 (렌더 주기마다 호출) */
    fun showDecision(d: AlleyDecisionEngine.Decision, live: Boolean, gap: AlleyWorld.Clearance? = null) {
        val text = "${d.action.ko} · ${d.headline}\n음성: ${d.speech}\n근거: ${d.reasons.joinToString(" / ")}"
        if (ruleText.text.toString() != text) ruleText.text = text
        ruleText.setTextColor(when (d.severity) { 0 -> ink; 1 -> amber; else -> red })
        val l = if (live && gap != null) String.format(Locale.KOREA, "실시간 · 좌 %.2fm · 우 %.2fm · 앞 %.1fm",
            gap.leftMeters, gap.rightMeters, gap.frontMeters) else ""
        if (liveText.text.toString() != l) liveText.text = l
    }

    fun showAiPending(providerText: String) { aiText.text = "$providerText 판단 요청 중…"; aiText.setTextColor(muted) }

    fun showAi(r: AiJudge.Result, f: AiJudge.Final) {
        aiText.text = if (r.ok) "${r.provider} · ${r.latencyMs}ms · AI 권고 ${r.action?.ko ?: "-"}" +
            (if (f.aiAgrees == false) " (규칙과 다름 → 규칙 유지)" else "") +
            "\n음성: ${f.speech}\n근거: ${r.reason ?: "-"}\n재생 출처: ${f.source}"
        else if (r.error == AiJudge.DISABLED) "규칙 엔진 문장으로 안내합니다\n음성: ${f.speech}"
        else "${r.provider} 연결 실패 → 규칙 엔진 문장 사용\n${r.error}\n음성: ${f.speech}"
        aiText.setTextColor(if (r.ok || r.error == AiJudge.DISABLED) ink else amber)
    }

    private fun showChooser() {
        val opts = catalog.options
        AlertDialog.Builder(context).setTitle("라벨 장면 선택")
            .setItems(opts.map { it.title + "\n   " + it.subtitle }.toTypedArray()) { _, i -> select(opts[i]) }
            .setNegativeButton("닫기", null).show()
    }

    /** PC 라벨링 서버 폴더 탐색 → 라벨 파일에서 판단 시점 추천 → 장면 목록에 추가 */
    private fun browseServer(dir: String) {
        status.text = "PC 라벨 서버 조회 중… ${catalog.repo.serverUrl}"
        io.execute {
            val result = runCatching { catalog.repo.list(dir) }
            main.post {
                result.onFailure {
                    status.text = "PC 라벨 서버 연결 실패: ${it.message}\nHyundai Test/labeling_server 실행 여부를 확인하세요."
                    status.setTextColor(red)
                }.onSuccess { listing ->
                    status.setTextColor(muted); status.text = "폴더: /${listing.dir}"
                    val items = ArrayList<Pair<String, () -> Unit>>()
                    val parent = listing.parent
                    if (parent != null) items.add("⬆ 상위 폴더" to { browseServer(parent) })
                    for (e in listing.entries) {
                        if (e.dir) items.add("📁 ${e.name}" to { browseServer(e.path) })
                        else items.add("🏷 ${e.name}" to { loadServerFile(e.path) })
                    }
                    if (items.isEmpty()) { status.text = "/${listing.dir} 에 라벨된 파일이 없습니다"; return@onSuccess }
                    AlertDialog.Builder(context).setTitle("PC 라벨 · /${listing.dir}")
                        .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
                        .setNegativeButton("닫기", null).show()
                }
            }
        }
    }

    private fun loadServerFile(path: String) {
        status.text = "라벨 읽는 중… $path"
        io.execute {
            val result = runCatching { catalog.repo.serverSamples(path) }
            main.post {
                result.onFailure { status.text = "라벨을 읽지 못했습니다: ${it.message}"; status.setTextColor(red) }
                    .onSuccess { samples ->
                        val added = catalog.addServerSamples(samples)
                        val first = added.firstOrNull() ?: catalog.options.lastOrNull { it.sample?.file == path }
                        if (first == null) status.text = "판단이 필요한 순간을 찾지 못했습니다"
                        else {
                            status.text = "${samples.size}개 순간 추가 · 장면 선택에서 다른 순간도 고를 수 있습니다"
                            select(first)
                        }
                    }
            }
        }
    }

    private fun openTutorial() {
        val intent = android.content.Intent(context, TutorialActivity::class.java)
            .putExtra(TutorialActivity.EXTRA_SCENARIO, selected.id)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun close() { io.shutdownNow() }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun wrap() = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
    private fun label(value: String, size: Int, color: Int = ink) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(dp(3), dp(2), dp(3), dp(2))
    }
    private fun section(title: String) = label(title, 12, teal).apply {
        background = GradientDrawable().apply { setColor(Color.argb(60, 68, 224, 191)); cornerRadius = dp(6).toFloat() }
    }
    private fun configure(button: Button, value: String, action: () -> Unit) {
        button.text = value; button.textSize = 13f; button.isAllCaps = false
        button.minWidth = 0; button.minimumWidth = 0; button.maxLines = 2
        button.setOnClickListener { action() }
    }
    private fun row(a: View, b: View) = LinearLayout(context).apply {
        addView(a, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), dp(2), dp(2), 0) })
        addView(b, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), dp(2), dp(2), 0) })
    }
}
