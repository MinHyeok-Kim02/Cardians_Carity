package com.guardians.viewer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.guardians.viewer.model.Det
import com.guardians.viewer.model.Lane

/**
 * 영상/이미지 위에 YOLO 박스와 차선을 그리는 뷰.
 * [setContentRect] 로 실제 영상이 그려진 영역(레터박스 제외)을 받아 정규화 좌표를 변환합니다.
 */
class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    var showVehicle = true
    var showPerson = true
    var showLane = true
    var showIds = true

    private val content = RectF()
    private var objects: List<Det> = emptyList()
    private var lanes: List<Lane> = emptyList()

    private val dp = resources.displayMetrics.density
    private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * dp }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 13f * dp; isFakeBoldText = true
    }
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f * dp; strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val tmp = RectF()

    fun setContentRect(l: Float, t: Float, r: Float, b: Float) {
        content.set(l, t, r, b)
        invalidate()
    }

    /** 컨테이너(이 뷰) 안에 srcW x srcH 를 fitCenter 했을 때의 영역 */
    fun fitContent(srcW: Int, srcH: Int) {
        if (srcW <= 0 || srcH <= 0 || width == 0 || height == 0) return
        val s = minOf(width.toFloat() / srcW, height.toFloat() / srcH)
        val w = srcW * s
        val h = srcH * s
        setContentRect((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    fun setFrame(objs: List<Det>, lns: List<Lane>) {
        objects = objs
        lanes = lns
        invalidate()
    }

    private fun colorOf(d: Det) = when (d.cls) {
        "car" -> COLOR_CAR
        "bus", "truck" -> COLOR_TRUCK
        "person" -> COLOR_PERSON
        "bicycle", "motorcycle" -> COLOR_RIDER
        else -> Color.LTGRAY
    }

    private fun nameOf(cls: String) = when (cls) {
        "car" -> "승용"
        "bus" -> "버스"
        "truck" -> "트럭"
        "person" -> "사람"
        "bicycle" -> "자전거"
        "motorcycle" -> "오토바이"
        else -> cls
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (content.width() <= 0f) return
        val L = content.left
        val T = content.top
        val W = content.width()
        val H = content.height()
        canvas.save()
        canvas.clipRect(content)

        if (showLane) {
            for (ln in lanes) {
                if (ln.pts.size < 4) continue
                lanePaint.color = if (ln.side == "center") COLOR_LANE_CENTER else COLOR_LANE
                lanePaint.alpha = (110 + 145 * ln.conf.coerceIn(0f, 1f)).toInt()
                path.reset()
                path.moveTo(L + ln.pts[0] * W, T + ln.pts[1] * H)
                var i = 2
                while (i + 1 < ln.pts.size) {
                    path.lineTo(L + ln.pts[i] * W, T + ln.pts[i + 1] * H)
                    i += 2
                }
                canvas.drawPath(path, lanePaint)
            }
        }

        for (d in objects) {
            val visible = when (d.group) {
                "vehicle" -> showVehicle
                "person", "rider" -> showPerson
                else -> true
            }
            if (!visible) continue
            val c = colorOf(d)
            tmp.set(L + d.x1 * W, T + d.y1 * H, L + d.x2 * W, T + d.y2 * H)
            fill.color = c
            fill.alpha = 34
            canvas.drawRect(tmp, fill)
            box.color = c
            canvas.drawRect(tmp, box)
            if (showIds) {
                val label = buildString {
                    append(nameOf(d.cls))
                    if (d.id >= 0) append(" #").append(d.id)
                    append(" ").append((d.conf * 100).toInt()).append('%')
                }
                val tw = text.measureText(label)
                val th = text.textSize
                val ty = if (tmp.top - th - 6 * dp > T) tmp.top - th - 6 * dp else tmp.top
                fill.alpha = 220
                canvas.drawRect(tmp.left, ty, tmp.left + tw + 10 * dp, ty + th + 6 * dp, fill)
                canvas.drawText(label, tmp.left + 5 * dp, ty + th, text)
            }
        }
        canvas.restore()
    }

    companion object {
        val COLOR_CAR = Color.parseColor("#29B6F6")
        val COLOR_TRUCK = Color.parseColor("#B388FF")
        val COLOR_PERSON = Color.parseColor("#FF7043")
        val COLOR_RIDER = Color.parseColor("#FFCA28")
        val COLOR_LANE = Color.parseColor("#3DDC97")
        val COLOR_LANE_CENTER = Color.parseColor("#FFE14D")
    }
}
