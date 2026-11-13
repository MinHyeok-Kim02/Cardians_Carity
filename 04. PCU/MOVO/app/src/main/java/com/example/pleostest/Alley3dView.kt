package com.example.pleostest

import android.content.Context
import android.opengl.GLSurfaceView

/** Own surface only: never draws outside the application window. */
class Alley3dView(context: Context) : GLSurfaceView(context) {
    private val sceneRenderer = Alley3dRenderer()
    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 0, 16, 0)
        preserveEGLContextOnPause = true
        setRenderer(sceneRenderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        contentDescription = "시뮬레이션 3D 골목 · 주차 차량 · 좌우 측면 경고"
    }
    fun publish(world: AlleyWorld, gap: AlleyWorld.Clearance) {
        val cars = world.parkedCars.map { floatArrayOf(it.x.toFloat(), it.y.toFloat(), it.width.toFloat(), it.length.toFloat(), 0f) }
        sceneRenderer.frame = Alley3dFrame(world.x.toFloat(), world.y.toFloat(), world.heading.toFloat(),
            floatArrayOf(gap.leftMeters.toFloat(), gap.rightMeters.toFloat()), cars.toTypedArray())
        requestRender()
    }
}
