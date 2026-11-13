package com.example.pleostest

import android.content.Context
import android.graphics.*
import android.view.View

/** Rear-seat view of a stylized cabin. All colors come from Feature 2; never infers distances. */
class InteriorDemoView(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = SideClearanceWarning().update(2.0, 2.0, 0)
    fun publish(value: SideClearanceWarning.Frame) { frame = value; invalidate() }
    init { contentDescription = "실내 조명 연출 · 디스플레이 기준선과 대시보드·도어 무드라이트" }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.rgb(9, 15, 22))
        val scale = minOf(width / 1000f, height / 760f)
        c.save(); c.translate((width - 1000 * scale) / 2, (height - 760 * scale) / 2); c.scale(scale, scale)
        fun color(hex: Long) { p.shader = null; p.color = hex.toInt(); p.style = Paint.Style.FILL; p.strokeWidth = 1f }
        fun rect(l: Float, t: Float, r: Float, b: Float, hex: Long, radius: Float = 0f) {
            color(hex); c.drawRoundRect(l, t, r, b, radius, radius, p)
        }
        fun path(points: FloatArray, hex: Long) {
            color(hex); val path = Path(); path.moveTo(points[0], points[1])
            for (i in 2 until points.size step 2) path.lineTo(points[i], points[i+1])
            path.close(); c.drawPath(path, p)
        }
        fun text(value: String, x: Float, y: Float, size: Float, hex: Long = 0xFFDDE8F0) {
            color(hex); p.textSize = size; p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            c.drawText(value, x, y, p)
        }
        // Windshield and the alley outside: decorative backdrop, no obstacle sensing.
        color(0xFF314455); p.shader = LinearGradient(0f,100f,0f,375f,0xFF233341.toInt(),0xFF78909C.toInt(),Shader.TileMode.CLAMP)
        c.drawRect(90f, 95f, 910f, 365f, p); p.shader = null
        path(floatArrayOf(395f,240f,605f,240f,840f,365f,160f,365f),0xFF343E45)
        path(floatArrayOf(165f,130f,285f,155f,355f,290f,95f,355f),0xFF465661)
        path(floatArrayOf(835f,130f,715f,155f,645f,290f,905f,355f),0xFF465661)
        color(0xFF90A2A9); p.strokeWidth=3f
        c.drawLine(488f,270f,468f,345f,p); c.drawLine(512f,270f,532f,345f,p)
        // Roof, pillars, doors and dashboard form the cabin silhouette.
        path(floatArrayOf(0f,60f,1000f,60f,925f,135f,75f,135f),0xFF141B23)
        path(floatArrayOf(35f,70f,100f,110f,178f,365f,100f,420f),0xFF1C2631)
        path(floatArrayOf(965f,70f,900f,110f,822f,365f,900f,420f),0xFF1C2631)
        path(floatArrayOf(0f,315f,145f,380f,230f,650f,0f,710f),0xFF202B36)
        path(floatArrayOf(1000f,315f,855f,380f,770f,650f,1000f,710f),0xFF202B36)
        path(floatArrayOf(145f,345f,855f,345f,930f,505f,70f,505f),0xFF2D3946)
        path(floatArrayOf(70f,505f,930f,505f,785f,640f,215f,640f),0xFF111A25)
        rect(460f,510f,610f,735f,0xFF202D3A,25f)
        rect(480f,552f,590f,675f,0xFF0B121B,15f)
        rect(515f,572f,555f,624f,0xFF627785,10f)
        fun ambient(points: FloatArray, side: SideClearanceWarning.Side) {
            val path = Path(); path.moveTo(points[0],points[1])
            for(i in 2 until points.size step 2) path.lineTo(points[i],points[i+1])
            p.shader=null; p.style=Paint.Style.STROKE; p.strokeCap=Paint.Cap.ROUND
            p.color=side.level.argb; p.alpha=45; p.strokeWidth=23f; c.drawPath(path,p)
            p.alpha=255; p.strokeWidth=6f; c.drawPath(path,p); p.style=Paint.Style.FILL
        }
        // Ambient colors remain steady; exact physical/display blink sync is a later validation.
        ambient(floatArrayOf(77f,421f,185f,456f,220f,530f),frame.left)
        ambient(floatArrayOf(923f,421f,815f,456f,780f,530f),frame.right)
        ambient(floatArrayOf(155f,419f,385f,430f,485f,430f),frame.left)
        ambient(floatArrayOf(845f,419f,685f,430f,585f,430f),frame.right)
        rect(115f,474f,183f,486f,0xFF91A1AA,5f)
        rect(817f,474f,885f,486f,0xFF91A1AA,5f)
        // Left steering wheel and cluster.
        rect(190f,354f,385f,418f,0xFF09111A,12f)
        text("On-sense",215f,394f,23f)
        color(0xFF0A1018); p.style=Paint.Style.STROKE; p.strokeWidth=23f
        c.drawCircle(285f,495f,104f,p); p.style=Paint.Style.FILL
        path(floatArrayOf(196f,477f,374f,477f,320f,540f,250f,540f),0xFF293743)
        rect(268f,524f,301f,592f,0xFF293743,8f)
        text("O",274f,516f,24f)
        // Pleos display, using precisely the same left/right warning frame as the alley.
        rect(429f,296f,785f,410f,0xFF050B10,12f)
        rect(438f,305f,776f,401f,0xFF112432,7f)
        text("PLEOS  |  On-sense",466f,330f,18f)
        rect(561f,349f,595f,386f,0xFFB9CAD4,6f)
        if(frame.left.visible) rect(450f,340f,458f,389f,frame.left.level.argb.toLong())
        if(frame.right.visible) rect(756f,340f,764f,389f,frame.right.level.argb.toLong())
        text("L",472f,379f,17f,frame.left.level.argb.toLong())
        text("R",728f,379f,17f,frame.right.level.argb.toLong())
        text("실내 조명 연출",37f,38f,28f)
        text("가상 거리 기반 · 차량 적용 여부는 아래 재조회 결과",37f,748f,23f,0xFF9CB0BF)
        c.restore()
    }
}

/** Application display edge markers. Shares the cabin's exact warning and blink state. */
class ClearanceGuideView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = SideClearanceWarning().update(2.0,2.0,0)
    fun publish(value: SideClearanceWarning.Frame) { frame=value; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val top = height * .48f; val bottom = height - 12f * resources.displayMetrics.density
        val margin = 7f * resources.displayMetrics.density
        paint.strokeWidth = 4f * resources.displayMetrics.density
        if(frame.left.visible) { paint.color=frame.left.level.argb; canvas.drawLine(margin,top,margin,bottom,paint) }
        if(frame.right.visible) { paint.color=frame.right.level.argb; canvas.drawLine(width-margin,top,width-margin,bottom,paint) }
    }
}
