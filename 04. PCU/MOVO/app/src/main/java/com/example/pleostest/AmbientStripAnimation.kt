package com.example.pleostest

import kotlin.math.abs

/** Pure strip progress calculation; no Android View, SDK calls, or distance calculation. */
class AmbientStripAnimation {
    var leftProgress=0f; private set
    var rightProgress=0f; private set
    private var lastFrame=0L

    /** Resumes the clock without counting time spent off screen as animation time. */
    fun resume(now: Long) { lastFrame=now }

    /** Moves both sides independently toward the latest warning strength. */
    fun advance(now: Long,leftTarget: Float,rightTarget: Float) {
        val elapsed=if(lastFrame==0L) 16L else (now-lastFrame).coerceIn(0L,MAX_FRAME_MS)
        lastFrame=now
        val step=elapsed/DURATION_MS
        leftProgress=approach(leftProgress,leftTarget,step)
        rightProgress=approach(rightProgress,rightTarget,step)
    }

    fun needsFrame(leftTarget: Float,rightTarget: Float)=
        abs(leftProgress-sanitize(leftTarget))>SETTLE_TOLERANCE ||
            abs(rightProgress-sanitize(rightTarget))>SETTLE_TOLERANCE

    private fun approach(current: Float,target: Float,step: Float)=
        current+(sanitize(target)-current).coerceIn(-step,step)

    companion object {
        const val SEGMENT_COUNT=16
        const val DURATION_MS=1800f
        const val MAX_FRAME_MS=64L
        private const val SETTLE_TOLERANCE=.003f
        private fun sanitize(value: Float)=if(value.isFinite()) value.coerceIn(0f,1f) else 0f

        /** The center section starts first; retreat removes the outer sections first. */
        fun sectionOpacity(progress: Float,index: Int): Float {
            require(index in 0 until SEGMENT_COUNT)
            val reveal=(sanitize(progress)*SEGMENT_COUNT-index).coerceIn(0f,1f)
            return reveal*reveal*(3f-2f*reveal)
        }
    }
}
