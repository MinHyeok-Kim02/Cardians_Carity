package com.example.pleostest

/** Explicit in-app synthetic inputs. Never sent to AAOS, Pleos or Virtual Console. */
object AlleyDemoScenario {
    const val DURATION_SECONDS = 10.0
    data class Input(val speedMps: Double, val brake: Boolean, val steering: Double = 0.0)
    fun sample(seconds: Double): Input {
        val t = seconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        return when {
            t < 1 -> Input(0.0, false)
            t < 3 -> Input(3.0 / 3.6, false)
            t < 4 -> Input(0.0, true)
            t < 6 -> Input(2.0 / 3.6, false)
            t < 7 -> Input(0.0, true)
            t < 9 -> Input(2.0 / 3.6, false)
            else -> Input(0.0, true)
        }
    }
}
