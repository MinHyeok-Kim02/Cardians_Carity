package com.example.myapplication;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;

/** Owns the GL surface, blink entry times and delivery of simulation snapshots. */
final class AlleyView extends GLSurfaceView {
    private final AlleySimulation simulation;
    private final long[] entered = {-1, -1};
    private final AlleyRenderer renderer = new AlleyRenderer(
            () -> postOnAnimation(this::requestRender));

    AlleyView(Context context, AlleySimulation simulation) {
        super(context);
        this.simulation = simulation;
        configureSurface();
        setContentDescription("3D 도심 골목. 건물과 가로수, 차량 후방 추적 카메라, 좌우 담장과 거리 경고선.");
        publish();
    }

    private void configureSurface() {
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    void entered(AlleySimulation.Side side) {
        entered[side.ordinal()] = SystemClock.uptimeMillis();
    }

    void resetBlink() {
        entered[0] = entered[1] = -1;
    }

    void publish() {
        renderer.frame = new AlleyScene(simulation, entered);
        requestRender();
    }

    static int color(AlleySimulation.Level level) {
        return level == AlleySimulation.Level.NEAR ? Color.rgb(255, 70, 80) :
                level == AlleySimulation.Level.CAUTION ? Color.rgb(255, 176, 65) :
                        Color.rgb(65, 220, 150);
    }
}
