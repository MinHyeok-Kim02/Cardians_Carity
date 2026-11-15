package com.example.pleostest

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

/** Draws only inside this View. No display-sized canvas, overlay window or predicted path. */
class AlleyMapView(context: Context, private val world: AlleyWorld) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun fill(color: Int) { paint.color = color; paint.style = Paint.Style.FILL }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.drawColor(Color.rgb(15, 25, 37))
        val scale = min(width / 9f, height / 14f)
        val cx = width / 2f
        val cy = height * .73f
        fun px(x: Double) = cx + x.toFloat() * scale
        fun py(y: Double) = cy - (y - world.y).toFloat() * scale
        fill(Color.rgb(40, 54, 67))
        canvas.drawRect(px(-3.5), 0f, px(3.5), height.toFloat(), paint)
        paint.color = Color.rgb(119, 141, 157); paint.strokeWidth = 2f
        canvas.drawLine(px(-3.5), 0f, px(-3.5), height.toFloat(), paint)
        canvas.drawLine(px(3.5), 0f, px(3.5), height.toFloat(), paint)
        // Background blocks are buildings, not sensed obstacles.
        fill(Color.rgb(26, 41, 55))
        for (i in -2..8) {
            val top = py(floor(world.y / 5) * 5 + i * 5.0)
            canvas.drawRoundRect(8f, top, px(-3.8), top + scale * 3.8f, 6f, 6f, paint)
            canvas.drawRoundRect(px(3.8), top, width - 8f, top + scale * 3.8f, 6f, 6f, paint)
        }
        for ((index, car) in world.parkedCars.withIndex()) {
            val left = px(car.x - car.width / 2); val top = py(car.y + car.length / 2)
            val right = px(car.x + car.width / 2); val bottom = py(car.y - car.length / 2)
            if (bottom < 0 || top > height) continue
            fill(if (index % 2 == 0) Color.rgb(106, 130, 158) else Color.rgb(183, 157, 119))
            canvas.drawRoundRect(left, top, right, bottom, scale * .18f, scale * .18f, paint)
            fill(Color.rgb(31, 51, 71))
            canvas.drawRoundRect(left + scale * .18f, top + scale * .8f, right - scale * .18f,
                bottom - scale * .8f, 3f, 3f, paint)
        }
        canvas.save()
        canvas.rotate(Math.toDegrees(world.heading).toFloat(), px(world.x), cy)
        val carX = px(world.x)
        fill(if (world.contact) Color.rgb(247, 111, 116) else Color.rgb(65, 217, 188))
        canvas.drawRoundRect(carX - .9f * scale, cy - 2.1f * scale,
            carX + .9f * scale, cy + 2.1f * scale, scale * .2f, scale * .2f, paint)
        fill(Color.rgb(15, 62, 64))
        canvas.drawRoundRect(carX - .65f * scale, cy - 1.15f * scale,
            carX + .65f * scale, cy + .9f * scale, 3f, 3f, paint)
        fill(Color.WHITE)
        canvas.drawRect(carX - .6f * scale, cy - 1.75f * scale, carX + .6f * scale, cy - 1.65f * scale, paint)
        canvas.restore()
        fill(Color.rgb(175, 195, 210)); paint.textSize = 13f * resources.displayMetrics.scaledDensity
        canvas.drawText("주차 차량이 있는 가상 골목", 12f, 24f * resources.displayMetrics.density, paint)
        canvas.restore()
    }
}
