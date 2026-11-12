package com.example.pleostest

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.*
import java.util.Locale
import kotlin.math.abs

/** Simulation scene driven by live speed and normalized steering; no manual distance input. */
class AlleyPassSimulationView(context: Context, restored: Bundle?) : ScrollView(context) {
    private val ink = Color.rgb(230, 238, 247)
    private val muted = Color.rgb(156, 176, 197)
    private val teal = Color.rgb(65, 217, 188)
    private val world = AlleyWorld()
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val vehicleText = label("", 21)
    private val inputText = label("", 14)
    private val warning = label("", 24)
    private val detail = label("", 16)
    private val distance = label("", 18)
    private val warningBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val map = AlleyMapView(context, world)
    private val start = Button(context)
    private val reset = Button(context)
    private var restricted = true
    private val emulator = android.os.Build.HARDWARE in listOf("ranchu", "goldfish")
    private var lastLog = 0L
    private val main = Handler(Looper.getMainLooper())
    private var active = false
    private var running = false
    private var speed: Float? = null
    private var steeringRaw: Float? = null
    private var steering: Float? = null
    private var brake: Boolean? = null
    private var speedAt = 0L
    private var steeringAt = 0L
    private var lastFrame = 0L
    private var lastLabels = 0L

    private val frame = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            val delta = if (lastFrame == 0L) 0.0 else (now - lastFrame) / 1000.0
            lastFrame = now
            val v = freshSpeed(now)
            val angle = freshSteering(now)
            if (restricted && !emulator) running = false
            if (running && v != null && angle != null) world.advance(delta, v.toDouble(), angle.toDouble())
            if (world.contact || world.y >= 74) running = false
            map.invalidate()
            if (running && now - lastLog >= 1000) {
                android.util.Log.d("CarTalkAlley", "x=${world.x} y=${world.y} heading=${world.heading} speed=$v steering=$angle")
                lastLog = now
            }
            if (now - lastLabels >= 200) { render(now); lastLabels = now }
            main.postDelayed(this, 50)
        }
    }

    init {
        clipToPadding = true; clipChildren = true; isFillViewport = true
        setBackgroundColor(Color.rgb(11, 20, 32))
        content.setPadding(dp(18), dp(14), dp(18), dp(18))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (restored != null) world.restore(restored.getDouble("world.x", 0.0),
            restored.getDouble("world.y", 4.0), restored.getDouble("world.heading", 0.0),
            restored.getBoolean("world.contact", false))
        content.addView(label("MOVO  /  ALLEY PASS", 14, teal))
        content.addView(label("골목길 교행 어시스트", 27).apply { setTypeface(typeface, Typeface.BOLD) })
        content.addView(label("SIMULATION  ·  차량과 골목은 가상 환경", 14, muted))
        val metrics = card()
        metrics.addView(vehicleText); metrics.addView(inputText)
        content.addView(metrics, spacing())
        warningBox.setPadding(dp(16), dp(12), dp(16), dp(12))
        warningBox.addView(warning); warningBox.addView(detail)
        content.addView(warningBox, spacing())
        content.addView(map, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(330)).apply { topMargin = dp(12) })
        content.addView(distance)
        val actions = LinearLayout(context)
        start.text = "시뮬레이션 시작"
        start.setOnClickListener {
            if (world.contact || world.y >= 74) { world.reset(); running = false }
            else running = !running
            lastFrame = 0
            render(SystemClock.elapsedRealtime())
        }
        actions.addView(start, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(reset.apply {
            text = "처음 위치로"
            setOnClickListener { running = false; world.reset(); lastFrame = 0; render(SystemClock.elapsedRealtime()); map.invalidate() }
        }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        content.addView(actions)
        content.addView(label("시작 후 Virtual Console의 가속·브레이크·조향으로 움직여 보세요.\n거리·접근 경고는 가상 차량 모델의 계산값이며 실제 장애물 감지가 아닙니다.", 14, muted))
        render(SystemClock.elapsedRealtime())
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(value: String, size: Int, color: Int = ink) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(0, dp(3), 0, dp(3))
    }
    private fun background(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }
    private fun card() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = background(Color.rgb(23, 37, 54))
    }
    private fun spacing() = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
    private fun freshSpeed(now: Long) = speed?.takeIf { now - speedAt <= 3000 }
    private fun freshSteering(now: Long) = steering?.takeIf { now - steeringAt <= 3000 }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }

    fun updateSpeed(value: Float?) { speed = value?.takeIf { it.isFinite() }; speedAt = SystemClock.elapsedRealtime() }
    fun updateBrake(value: Boolean?) { brake = value }
    fun updateSteering(raw: Float?, normalized: Float?) {
        steeringRaw = raw; steering = normalized; steeringAt = SystemClock.elapsedRealtime()
    }

    fun updateRestrictions(value: Boolean) {
        restricted = value
        start.isEnabled = !value
        reset.isEnabled = !value
        detail.visibility = if (value) android.view.View.GONE else android.view.View.VISIBLE
        if (value && !emulator) running = false
    }

    fun setActive(value: Boolean) {
        active = value
        main.removeCallbacks(frame)
        lastFrame = 0
        if (value) main.post(frame)
        else { speed = null; steering = null; steeringRaw = null; brake = null }
    }

    private fun render(now: Long) {
        val v = freshSpeed(now)
        val angle = freshSteering(now)
        vehicleText.show(v?.let { String.format(Locale.KOREA, "%.1f km/h", abs(it) * 3.6f) } ?: "속도 수신 대기")
        inputText.show("조향 " + when {
            angle == null -> "수신 대기"
            angle < -.02f -> "왼쪽"
            angle > .02f -> "오른쪽"
            else -> "중앙"
        } + "  ·  브레이크 " + when (brake) { true -> "눌림"; false -> "해제"; null -> "미확인" })
        val gap = world.clearance()
        distance.show(String.format(Locale.KOREA, "가상 최근접 거리  %.2f m  ·  %s", gap.meters, gap.side))
        val message: String
        val explanation: String
        val caution: Boolean
        when {
            world.contact -> {
                message = "가상 접촉 · 시뮬레이션 정지"
                explanation = "가상 차량 또는 골목 경계에 닿았습니다. 처음 위치로 돌아가 다시 시험하세요."
                caution = true
            }
            world.y >= 74 -> { message = "골목 시연 완료"; explanation = "처음 위치로 돌아가 다른 조향으로 시험할 수 있습니다."; caution = false }
            !running -> { message = "주차 차량이 있는 골목"; explanation = "시뮬레이션 시작 후 속도와 조향을 바꾸어 접근 변화를 확인하세요."; caution = false }
            v == null || angle == null -> { message = "차량 입력 대기 · 이동 일시 보류"; explanation = "속도·조향 수신이 확인되면 가상 차량 이동이 이어집니다."; caution = true }
            gap.meters <= .3 -> { message = "${gap.side} 근접 · 정차 후 확인"; explanation = "가상 차체와의 여유가 매우 작습니다. 브레이크를 밟고 주변을 확인하세요."; caution = true }
            abs(v) * 3.6f > 10 -> { message = "골목에서는 속도를 낮추세요"; explanation = "현재 속도에서 조향하면 가상 차량도 더 빠르게 접근합니다."; caution = true }
            gap.meters <= 1.2 && gap.approaching -> { message = "${gap.side}에 가까워지고 있어요"; explanation = "가상 장애물과의 거리가 줄어들고 있습니다. 정차하고 주변을 확인하세요."; caution = true }
            gap.meters <= .8 -> { message = "${gap.side} 공간 주의"; explanation = "가상 주차 차량과의 간격을 확인하며 천천히 움직이세요."; caution = true }
            else -> { message = "주변을 확인하며 천천히 이동"; explanation = "조향에 따라 내 차 위치와 가상 차량까지의 거리가 달라집니다."; caution = false }
        }
        warning.show(message); detail.show(explanation)
        warning.setTextColor(if (world.contact || gap.meters <= .3) Color.rgb(255, 141, 141) else if (caution) Color.rgb(255, 213, 132) else teal)
        warningBox.background = background(if (world.contact || gap.meters <= .3) Color.rgb(62, 29, 38) else if (caution) Color.rgb(55, 44, 29) else Color.rgb(23, 37, 54))
        start.show(if (world.contact || world.y >= 74) "다시 준비" else if (running) "일시정지" else "시뮬레이션 시작")
    }

    fun save(out: Bundle) {
        out.putDouble("world.x", world.x); out.putDouble("world.y", world.y)
        out.putDouble("world.heading", world.heading); out.putBoolean("world.contact", world.contact)
    }

    override fun onDetachedFromWindow() { setActive(false); super.onDetachedFromWindow() }
}
