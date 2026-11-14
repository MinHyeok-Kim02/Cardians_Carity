package com.example.pleostest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 튜토리얼 디지털 트윈(위에서 본 골목).
 * 라벨/합성 장면의 장애물, 경로 계획을 노면 화살표로, 미리 움직이는 고스트 차, 실제 자차와 좌우 간격을 그립니다.
 * 상태(위치·단계)는 TutorialActivity 가 정하고, 이 뷰는 그리기만 합니다.
 */
class TutorialView(context: Context) : View(context) {
    data class Pose(val x: Double, val y: Double, val heading: Double)

    var analysis: SceneAnalysis? = null
    var ego: Pose? = null
    var ghost: Pose? = null
    var ghostReverse = false
    /** 노면 화살표 표시 비율 0..1 */
    var pathReveal = 0f
    /** 대향차 이동량(m, 자차 쪽으로) 과 지나갈 차로 x */
    var oncomingTravel = 0.0
    var oncomingLaneX: Double? = null
    var highlight = false
    var gap: AlleyWorld.Clearance? = null
    var stepTitle = ""
    var caption = ""
    var card: String? = null
    var cardSeverity = 0
    var reverseHint = false
    var cameraTargetY = 4.0
    private var cameraY = 4.0
    private var phase = 0f

    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.argb(70, 220, 220, 210)
    }
    private val teal = Color.rgb(68, 224, 191)
    private val amber = Color.rgb(255, 196, 92)
    private val red = Color.rgb(255, 110, 110)
    private val rect = RectF()

    private var ppm = 30f
    private var midX = 0.0

    fun tick() { phase += 0.06f; cameraY += (cameraTargetY - cameraY) * 0.08; invalidate() }
    fun snapCamera() { cameraY = cameraTargetY }

    private fun sx(x: Double) = (width / 2f + ((x - midX) * ppm)).toFloat()
    private fun sy(y: Double) = (height * 0.70f - ((y - cameraY) * ppm)).toFloat()

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.rgb(11, 20, 32))
        val a = analysis ?: run {
            text.textSize = 18 * density; canvas.drawText("장면 준비 중…", 30f, 60f, text); return
        }
        val w = a.world
        val roadW = w.rightWallX - w.leftWallX
        midX = (w.leftWallX + w.rightWallX) / 2
        ppm = min(width / (roadW + 5.0), height / 24.0).toFloat()
        dash.strokeWidth = 2 * density
        dash.pathEffect = DashPathEffect(floatArrayOf(10 * density, 10 * density), 0f)
        drawRoad(canvas, w)
        drawPath(canvas, a)
        drawObstacles(canvas, a)
        ghost?.let { drawCar(canvas, it, Color.argb(70, 68, 224, 191), teal, true) }
        if (reverseHint) drawReverseArrows(canvas, ghost ?: ego)
        ego?.let { e ->
            drawCar(canvas, e, Color.rgb(232, 238, 243), Color.rgb(40, 60, 80), false)
            gap?.let { drawGaps(canvas, e, it) }
        }
        val captionBottom = drawCaption(canvas)
        card?.let { drawCard(canvas, it, captionBottom) }
    }

    private fun drawRoad(c: Canvas, w: AlleyWorld) {
        val top = cameraY + height / ppm
        val bottom = cameraY - height / ppm
        val roadEnd = min(top, w.endLineY)
        fill.color = Color.rgb(29, 43, 54)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.color = Color.rgb(46, 52, 60)
        c.drawRect(sx(w.leftWallX), sy(roadEnd), sx(w.rightWallX), sy(bottom), fill)
        // 차로 이음 점선
        var y = (bottom / 4).toInt() * 4.0
        while (y < roadEnd) {
            c.drawLine(sx(midX), sy(y), sx(midX), sy(min(y + 2, roadEnd)), dash); y += 4
        }
        stroke.color = Color.rgb(201, 195, 176); stroke.strokeWidth = 4 * density
        c.drawLine(sx(w.leftWallX), sy(roadEnd), sx(w.leftWallX), sy(bottom), stroke)
        c.drawLine(sx(w.rightWallX), sy(roadEnd), sx(w.rightWallX), sy(bottom), stroke)
        if (w.endLineY < top) {
            stroke.color = Color.rgb(120, 130, 140)
            c.drawLine(sx(w.leftWallX), sy(w.endLineY), sx(w.rightWallX), sy(w.endLineY), stroke)
        }
    }

    /** 경로 계획 → 자차 폭 띠 + 노면 화살표(쉐브론) + 정차선 */
    private fun drawPath(c: Canvas, a: SceneAnalysis) {
        val pts = a.plan.points
        if (pathReveal <= 0f) return
        if (pts.size >= 2) {
            val last = (1 + ((pts.size - 1) * pathReveal).toInt()).coerceAtMost(pts.size)
            val band = Path()
            for (i in 0 until last) { val p = pts[i]; if (i == 0) band.moveTo(sx(p.x - .9), sy(p.y)) else band.lineTo(sx(p.x - .9), sy(p.y)) }
            for (i in last - 1 downTo 0) { val p = pts[i]; band.lineTo(sx(p.x + .9), sy(p.y)) }
            band.close()
            fill.color = Color.argb(45, 68, 224, 191); c.drawPath(band, fill)
            // 1.5m 마다 쉐브론, 앞으로 흐르는 애니메이션
            stroke.strokeWidth = 5 * density; stroke.strokeCap = Paint.Cap.ROUND
            val flow = (phase * 0.5f) % 1f
            var i = 0
            while (i < last) {
                val p = pts[i]
                val alpha = (140 + 115 * sin((i * 0.35f) - phase * 2f)).toInt().coerceIn(60, 255)
                stroke.color = Color.argb(alpha, 68, 224, 191)
                val yy = p.y + flow * 1.5
                chevron(c, p.x, yy, p.heading, 0.55, false)
                i += 3
            }
        }
        // 정차·양보 지점
        val stopY = a.plan.stopY
        if (stopY != null && pathReveal >= 0.99f) {
            val end = pts.lastOrNull()
            val x = end?.x ?: 0.0
            val color = if (a.plan.outcome == CorridorPlanner.Outcome.YIELD) amber else red
            stroke.color = color; stroke.strokeWidth = 6 * density
            val yLine = stopY + CorridorPlanner.EGO_HALF_L + 0.3
            c.drawLine(sx(x - 1.1), sy(yLine), sx(x + 1.1), sy(yLine), stroke)
            text.color = color; text.textSize = 15 * density
            c.drawText(if (a.plan.outcome == CorridorPlanner.Outcome.YIELD) "양보 정차" else "정차",
                sx(x + 1.25), sy(yLine) + 5 * density, text)
        }
    }

    private fun chevron(c: Canvas, x: Double, y: Double, heading: Double, size: Double, down: Boolean) {
        c.save()
        c.rotate(Math.toDegrees(heading).toFloat(), sx(x), sy(y))
        val s = (size * ppm).toFloat()
        val dir = if (down) -1 else 1
        val cx = sx(x); val cy = sy(y)
        c.drawLine(cx - s, cy + dir * s * 0.6f, cx, cy - dir * s * 0.4f, stroke)
        c.drawLine(cx, cy - dir * s * 0.4f, cx + s, cy + dir * s * 0.6f, stroke)
        c.restore()
    }

    private fun drawObstacles(c: Canvas, a: SceneAnalysis) {
        val frontal = a.frontal
        text.textSize = 12 * density
        for (o in a.world.parkedCars) {
            val isFrontal = frontal.any { it.x == o.x && it.y == o.y }
            var x = o.x; var y = o.y
            if (isFrontal) {
                y -= oncomingTravel
                oncomingLaneX?.let { lane -> x = o.x + (lane - o.x) * min(1.0, oncomingTravel / 6.0) }
            }
            val person = o.label.contains("보행자")
            val heavy = o.label.contains("트럭") || o.label.contains("버스")
            val base = when {
                isFrontal -> red
                person -> Color.rgb(255, 160, 80)
                heavy -> Color.rgb(140, 120, 190)
                o.label.contains("경로상") -> Color.rgb(200, 150, 70)
                else -> Color.rgb(93, 127, 163)
            }
            val pulse = if (highlight) (0.5f + 0.5f * sin(phase * 3f)) else 0f
            if (person) {
                fill.color = base
                c.drawCircle(sx(x), sy(y), (0.35 * ppm).toFloat(), fill)
            } else {
                rect.set(sx(x - o.width / 2), sy(y + o.length / 2), sx(x + o.width / 2), sy(y - o.length / 2))
                fill.color = base; c.drawRoundRect(rect, 6 * density, 6 * density, fill)
                fill.color = Color.argb(90, 10, 20, 30)
                val inset = (o.length * 0.22 * ppm).toFloat()
                c.drawRect(rect.left + 3 * density, rect.top + inset, rect.right - 3 * density, rect.top + inset * 1.6f, fill)
            }
            if (pulse > 0f) {
                stroke.color = Color.argb((pulse * 220).toInt(), 255, 255, 255); stroke.strokeWidth = 3 * density
                c.drawRoundRect(sx(x - o.width / 2 - .15), sy(y + o.length / 2 + .15), sx(x + o.width / 2 + .15),
                    sy(y - o.length / 2 - .15), 8 * density, 8 * density, stroke)
            }
            text.color = Color.WHITE
            val name = o.label.removePrefix("주차 ")
            c.drawText(name, sx(x) - text.measureText(name) / 2, sy(y + o.length / 2) - 4 * density, text)
            if (isFrontal) {
                stroke.color = red; stroke.strokeWidth = 4 * density
                chevron(c, x, y - o.length / 2 - 1.0 - (phase % 1f) * 0.6, 0.0, 0.5, true)
            }
        }
    }

    private fun drawCar(c: Canvas, p: Pose, body: Int, line: Int, ghostStyle: Boolean) {
        c.save()
        c.rotate(Math.toDegrees(p.heading).toFloat(), sx(p.x), sy(p.y))
        rect.set(sx(p.x - .9), sy(p.y + 2.1), sx(p.x + .9), sy(p.y - 2.1))
        fill.color = body; c.drawRoundRect(rect, 10 * density, 10 * density, fill)
        stroke.color = line; stroke.strokeWidth = (if (ghostStyle) 3 else 2) * density
        if (ghostStyle) stroke.pathEffect = DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f)
        c.drawRoundRect(rect, 10 * density, 10 * density, stroke)
        stroke.pathEffect = null
        if (!ghostStyle) {
            fill.color = Color.rgb(40, 60, 80)
            c.drawRoundRect(sx(p.x - .7), sy(p.y + 1.2), sx(p.x + .7), sy(p.y + 0.5), 4 * density, 4 * density, fill)
            fill.color = Color.rgb(255, 236, 160)
            c.drawCircle(sx(p.x - .6), sy(p.y + 2.0), 3 * density, fill)
            c.drawCircle(sx(p.x + .6), sy(p.y + 2.0), 3 * density, fill)
        } else {
            text.color = teal; text.textSize = 12 * density
            val t = if (ghostReverse) "후진 미리보기" else "미리보기"
            c.drawText(t, sx(p.x) - text.measureText(t) / 2, sy(p.y), text)
        }
        c.restore()
    }

    private fun drawReverseArrows(c: Canvas, p: Pose?) {
        p ?: return
        stroke.color = amber; stroke.strokeWidth = 5 * density
        for (k in 0 until 3) chevron(c, p.x, p.y - 3.0 - k * 1.2 - (phase % 1f) * 0.5, p.heading, 0.55, true)
    }

    /** 자차 좌우 간격선과 수치 (0.3m 이하 빨강, 0.8m 이하 주황) */
    private fun drawGaps(c: Canvas, e: Pose, g: AlleyWorld.Clearance) {
        text.textSize = 14 * density
        for ((side, m) in listOf(-1 to g.leftMeters, 1 to g.rightMeters)) {
            if (!m.isFinite()) continue
            val color = when { m <= .3 -> red; m <= .8 -> amber; else -> teal }
            val edge = e.x + side * .9
            val far = edge + side * min(m, 3.0)
            stroke.color = color; stroke.strokeWidth = 3 * density
            c.drawLine(sx(edge), sy(e.y), sx(far), sy(e.y), stroke)
            val t = String.format(Locale.KOREA, "%.2fm", m)
            text.color = color
            val tx = if (side < 0) sx(far) - text.measureText(t) - 4 * density else sx(far) + 4 * density
            c.drawText(t, tx, sy(e.y) - 6 * density, text)
        }
    }

    /** 왼쪽 위 단계·설명 상자. 반환: 상자 아래 y */
    private fun drawCaption(c: Canvas): Float {
        if (stepTitle.isEmpty() && caption.isEmpty()) return 0f
        val pad = 12 * density
        val boxW = (width * 0.62f).toInt()
        text.textSize = 15 * density; text.color = Color.WHITE
        val layout = StaticLayout.Builder.obtain(caption, 0, caption.length, text, boxW - (pad * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val h = layout.height + pad * 2 + 24 * density
        fill.color = Color.argb(215, 9, 16, 26)
        c.drawRoundRect(pad, pad, pad + boxW, pad + h, 12 * density, 12 * density, fill)
        text.color = teal; text.textSize = 14 * density
        c.drawText(stepTitle, pad * 2, pad * 2 + 12 * density, text)
        c.save(); c.translate(pad * 2, pad * 2 + 22 * density)
        text.color = Color.WHITE; text.textSize = 15 * density
        layout.draw(c); c.restore()
        return pad + h
    }

    /** 판단 카드: 설명 상자 바로 아래 (자차·간격 표시를 가리지 않도록 위쪽) */
    private fun drawCard(c: Canvas, value: String, below: Float) {
        val pad = 12 * density
        val boxW = (width * 0.62f).toInt()
        text.textSize = 16 * density; text.color = Color.WHITE
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, text, boxW - (pad * 2).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val h = layout.height + pad * 2
        val left = 12 * density
        val top = below + 8 * density
        fill.color = Color.argb(230, 12, 24, 36)
        c.drawRoundRect(left, top, left + boxW, top + h, 14 * density, 14 * density, fill)
        stroke.color = when (cardSeverity) { 0 -> teal; 1 -> amber; else -> red }
        stroke.strokeWidth = 3 * density
        c.drawRoundRect(left, top, left + boxW, top + h, 14 * density, 14 * density, stroke)
        c.save(); c.translate(left + pad, top + pad); layout.draw(c); c.restore()
    }

    companion object {
        /** 대향차가 양보한 자차 옆으로 지나갈 차로 x */
        fun passLane(a: SceneAnalysis, egoX: Double): Double? {
            val f = a.frontal.firstOrNull() ?: return null
            val egoLeft = egoX - CorridorPlanner.EGO_HALF_W
            val staticLeft = a.world.parkedCars.filter { p -> a.frontal.none { it.x == p.x && it.y == p.y } }
                .filter { it.x + it.width / 2 <= egoLeft }
                .maxOfOrNull { it.x + it.width / 2 } ?: a.world.leftWallX
            val lane = (max(staticLeft, a.world.leftWallX) + egoLeft) / 2
            return if (abs(lane - f.x) < 4) lane else null
        }
    }
}
