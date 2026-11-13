package com.example.pleostest

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** PCU DrivingActivity's reading cards adapted into our single app window.
 * Display only: uses the existing AAOS/Pleos subscriptions, never creates another receiver.
 */
class VehicleInfoPanel(context: Context) : LinearLayout(context) {
    private val ink = Color.rgb(235, 243, 250)
    private val muted = Color.rgb(170, 190, 205)
    private val teal = Color.rgb(68, 224, 191)
    private val speedValue: TextView
    private val steeringValue: TextView
    private val direction: TextView
    private val gearAndBrake: TextView
    private val state: TextView
    private val source: TextView

    init {
        orientation = VERTICAL
        clipChildren = true
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(Color.rgb(16, 27, 37))
        addView(text("On-sense · 차량 정보", 19, teal).apply {
            setTypeface(typeface, Typeface.BOLD)
        })
        source = text("실제 AAOS · Pleos SDK 수신", 12, muted)
        addView(source)
        speedValue = reading("차량 속도", "km/h", 42)
        steeringValue = reading("조향각 · SDK 원본", "°", 34)
        direction = text("조향 수신 대기", 16, ink)
        addView(direction)
        gearAndBrake = text("기어 -- · 브레이크 --", 16, ink)
        addView(gearAndBrake)
        state = text("차량 상태 확인 중", 12, muted)
        addView(state)
    }

    private fun reading(title: String, unit: String, size: Int): TextView {
        val card = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 43, 57))
                cornerRadius = dp(14).toFloat()
            }
        }
        card.addView(text(title, 16, muted))
        val value = text("--", size, ink).apply { setTypeface(typeface, Typeface.BOLD) }
        card.addView(value)
        card.addView(text(unit, 16, teal))
        addView(card, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            setMargins(0, dp(8), 0, dp(4))
        })
        return value
    }

    fun render(speedKph: Float?, rawSteering: Float?, gear: String?, brake: Boolean?,
               restricted: Boolean, syntheticScene: Boolean) {
        speedValue.show(speedKph?.let { String.format(Locale.KOREA, "%.1f", it) } ?: "--")
        steeringValue.show(rawSteering?.let { String.format(Locale.KOREA, "%+.1f", it) } ?: "--")
        direction.show(when {
            rawSteering == null -> "조향 수신 대기"
            rawSteering > 10f -> "← 왼쪽 조향"
            rawSteering < -10f -> "오른쪽 조향 →"
            else -> "조향 중앙"
        })
        gearAndBrake.show("기어 "+(gear ?: "--")+" · 브레이크 "+
            when(brake) { true -> "눌림"; false -> "해제"; null -> "--" })
        state.show(if (restricted) "플랫폼 주행 제한 적용 중" else "플랫폼 주행 제한 해제")
        source.show(if (syntheticScene) "실제 SDK 값 · 3D는 가상 시연" else "실제 AAOS · Pleos SDK 수신")
    }

    private fun text(value: String, size: Int, color: Int) = TextView(context).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        gravity = Gravity.CENTER
        maxLines = 2
        setPadding(0, dp(3), 0, dp(3))
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
