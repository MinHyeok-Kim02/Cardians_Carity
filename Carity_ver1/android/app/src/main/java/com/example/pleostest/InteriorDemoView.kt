package com.example.pleostest

import android.content.Context
import android.graphics.*
import android.view.View

/** Cabin illustration only. Feature 2 supplies all warnings; drawing never calculates clearance. */
class InteriorDemoView(context: Context) : View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame=SideClearanceWarning().update(2.0,2.0,0)
    private val animation=AmbientStripAnimation()

    init { contentDescription="실내 조명 연출 · 디스플레이 기준선과 대시보드·도어 무드라이트" }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animation.resume(android.os.SystemClock.uptimeMillis())
    }

    fun publish(value: SideClearanceWarning.Frame) {
        frame=value
        invalidate()
    }

    /** Draw order keeps lighting behind the wheel/display, exactly as in Version 1. */
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        updateAnimation()
        c.drawColor(Color.rgb(9,15,22))
        val scale=minOf(width/1000f,height/760f)
        c.save()
        c.translate((width-1000*scale)/2,(height-760*scale)/2)
        c.scale(scale,scale)
        drawWindshield(c)
        drawCabin(c)
        drawAmbientStrips(c)
        drawSteeringWheel(c)
        drawPleosDisplay(c)
        drawCaptions(c)
        c.restore()
        scheduleAnimationFrame()
    }

    private fun updateAnimation() {
        animation.advance(android.os.SystemClock.uptimeMillis(),frame.left.ambientStrength,frame.right.ambientStrength)
    }

    private fun scheduleAnimationFrame() {
        if(isShown && animation.needsFrame(frame.left.ambientStrength,frame.right.ambientStrength))
            postInvalidateOnAnimation()
    }

    private fun drawWindshield(c: Canvas) {
        // Windshield and the alley outside: decorative backdrop, no obstacle sensing.
        fillColor(0xFF314455); p.shader = LinearGradient(0f,100f,0f,375f,0xFF233341.toInt(),0xFF78909C.toInt(),Shader.TileMode.CLAMP)
        c.drawRect(90f, 95f, 910f, 365f, p); p.shader = null
        drawPolygon(c,floatArrayOf(395f,240f,605f,240f,840f,365f,160f,365f),0xFF343E45)
        drawPolygon(c,floatArrayOf(165f,130f,285f,155f,355f,290f,95f,355f),0xFF465661)
        drawPolygon(c,floatArrayOf(835f,130f,715f,155f,645f,290f,905f,355f),0xFF465661)
        fillColor(0xFF90A2A9); p.strokeWidth=3f
        c.drawLine(488f,270f,468f,345f,p); c.drawLine(512f,270f,532f,345f,p)
    }


    private fun drawCabin(c: Canvas) {
        // Roof, pillars, doors and dashboard form the cabin silhouette.
        drawPolygon(c,floatArrayOf(0f,60f,1000f,60f,925f,135f,75f,135f),0xFF141B23)
        drawPolygon(c,floatArrayOf(35f,70f,100f,110f,178f,365f,100f,420f),0xFF1C2631)
        drawPolygon(c,floatArrayOf(965f,70f,900f,110f,822f,365f,900f,420f),0xFF1C2631)
        drawPolygon(c,floatArrayOf(0f,315f,145f,380f,230f,650f,0f,710f),0xFF202B36)
        drawPolygon(c,floatArrayOf(1000f,315f,855f,380f,770f,650f,1000f,710f),0xFF202B36)
        drawPolygon(c,floatArrayOf(145f,345f,855f,345f,930f,505f,70f,505f),0xFF2D3946)
        drawPolygon(c,floatArrayOf(70f,505f,930f,505f,785f,640f,215f,640f),0xFF111A25)
        drawRect(c,460f,510f,610f,735f,0xFF202D3A,25f)
        drawRect(c,480f,552f,590f,675f,0xFF0B121B,15f)
        drawRect(c,515f,572f,555f,624f,0xFF627785,10f)
    }


    private fun drawAmbientStrip(c: Canvas,points: FloatArray,side: SideClearanceWarning.Side,progress: Float) {
        val path=Path(); path.moveTo(points[0],points[1])
        for(i in 2 until points.size step 2) path.lineTo(points[i],points[i+1])
        p.shader=null; p.style=Paint.Style.STROKE
        p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND
        p.color=0xFF10161E.toInt(); p.strokeWidth=4f; c.drawPath(path,p)
        val measure=PathMeasure(path,false)
        val segments=AmbientStripAnimation.SEGMENT_COUNT
        val length=measure.length/segments
        val segment=Path()
        for(index in 0 until segments) {
            // Keep every section lit green; only its color changes, door -> center.
            val base=if(side.level==SideClearanceWarning.Level.UNKNOWN)
                SideClearanceWarning.Level.UNKNOWN.argb else SideClearanceWarning.Level.GREEN.argb
            val tint=AmbientStripAnimation.sectionColor(progress,index,base,side.ambientArgb)
            val opacity=1f
            segment.rewind()
            measure.getSegment(index*length+3f,(index+1)*length-3f,segment,true)
            p.color=tint
            var outerOpacity=0f
            for(layer in 8 downTo 1) {
                val radius=layer*3f
                val glow=.42f*opacity*kotlin.math.exp(-radius*radius/(2f*8f*8f))
                p.alpha=(((glow-outerOpacity)/(1f-outerOpacity))*255f).toInt().coerceIn(0,255)
                p.strokeWidth=2.4f+radius*2f
                c.drawPath(segment,p)
                outerOpacity=glow
            }
            p.alpha=(255*opacity).toInt().coerceIn(0,255)
            p.strokeWidth=2.4f; c.drawPath(segment,p)
            p.color=Color.rgb((Color.red(tint)*3+255)/4,
                (Color.green(tint)*3+255)/4,(Color.blue(tint)*3+255)/4)
            p.alpha=(210*opacity).toInt().coerceIn(0,255)
            p.strokeWidth=.9f; c.drawPath(segment,p)
        }
        p.alpha=255; p.style=Paint.Style.FILL
    }

    private fun drawAmbientStrips(c: Canvas) {
        // One continuous route per side: dashboard center, outer trim, then door.
        // Distance controls warning-color coverage; the whole strip stays lit in base green.
        drawAmbientStrip(c,floatArrayOf(485f,430f,385f,430f,155f,419f,77f,421f,185f,456f,220f,530f),frame.left,animation.leftProgress)
        drawAmbientStrip(c,floatArrayOf(585f,430f,685f,430f,845f,419f,923f,421f,815f,456f,780f,530f),frame.right,animation.rightProgress)
        drawRect(c,115f,474f,183f,486f,0xFF91A1AA,5f)
        drawRect(c,817f,474f,885f,486f,0xFF91A1AA,5f)
    }


    private fun drawSteeringWheel(c: Canvas) {
        // Left steering wheel and cluster.
        drawRect(c,190f,354f,385f,418f,0xFF09111A,12f)
        drawText(c,"On-sense",215f,394f,23f)
        fillColor(0xFF0A1018); p.style=Paint.Style.STROKE; p.strokeWidth=23f
        c.drawCircle(285f,495f,104f,p); p.style=Paint.Style.FILL
        drawPolygon(c,floatArrayOf(196f,477f,374f,477f,320f,540f,250f,540f),0xFF293743)
        drawRect(c,268f,524f,301f,592f,0xFF293743,8f)
        drawText(c,"O",274f,516f,24f)
    }


    private fun drawPleosDisplay(c: Canvas) {
        // Pleos display, using precisely the same left/right warning frame as the alley.
        drawRect(c,429f,296f,785f,410f,0xFF050B10,12f)
        drawRect(c,438f,305f,776f,401f,0xFF112432,7f)
        drawText(c,"PLEOS  |  On-sense",466f,330f,18f)
        drawRect(c,561f,349f,595f,386f,0xFFB9CAD4,6f)
        if(frame.left.visible) drawRect(c,450f,340f,458f,389f,frame.left.ambientArgb.toLong())
        if(frame.right.visible) drawRect(c,756f,340f,764f,389f,frame.right.ambientArgb.toLong())
        drawText(c,"L",472f,379f,17f,frame.left.ambientArgb.toLong())
        drawText(c,"R",728f,379f,17f,frame.right.ambientArgb.toLong())
    }


    private fun drawCaptions(c: Canvas) {
        drawText(c,"실내 조명 연출",37f,38f,28f)
        drawText(c,"시연 경고 색 · 차량 적용 여부는 아래 재조회 결과",37f,748f,23f,0xFF9CB0BF)
    }


    private fun fillColor(hex: Long) {
        p.shader=null; p.color=hex.toInt(); p.style=Paint.Style.FILL; p.strokeWidth=1f
    }

    private fun drawRect(c: Canvas,l: Float,t: Float,r: Float,b: Float,hex: Long,radius: Float=0f) {
        fillColor(hex); c.drawRoundRect(l,t,r,b,radius,radius,p)
    }

    private fun drawPolygon(c: Canvas,points: FloatArray,hex: Long) {
        fillColor(hex)
        val polygon=Path(); polygon.moveTo(points[0],points[1])
        for(i in 2 until points.size step 2) polygon.lineTo(points[i],points[i+1])
        polygon.close(); c.drawPath(polygon,p)
    }

    private fun drawText(c: Canvas,value: String,x: Float,y: Float,size: Float,hex: Long=0xFFDDE8F0) {
        fillColor(hex); p.textSize=size; p.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
        c.drawText(value,x,y,p)
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
