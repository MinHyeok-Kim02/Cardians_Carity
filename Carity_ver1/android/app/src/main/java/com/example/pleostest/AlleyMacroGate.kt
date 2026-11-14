package com.example.pleostest

/** Detection only. No vehicle actuator calls. Unknown data always blocks confirmation. */
class AlleyMacroGate {
    data class State(val presses: Int, val armed: Boolean, val stopped: Boolean, val canConfirm: Boolean)
    private val presses = ArrayDeque<Long>()
    private var previousBrake: Boolean? = null
    private var armedUntil = -1L

    fun reset() { presses.clear(); previousBrake = null; armedUntil = -1L }

    fun observe(now: Long, speedMps: Double?, brake: Boolean?, gapMeters: Double?,
                interactionAllowed: Boolean, parked: Boolean = false): State {
        if (speedMps == null || !speedMps.isFinite() || brake == null ||
            gapMeters == null || !gapMeters.isFinite() || gapMeters < 0) {
            reset()
            return State(0, false, false, false)
        }
        val lowSpeed = speedMps in 0.0..(10.0 / 3.6)
        if (!lowSpeed) {
            reset(); previousBrake = brake
            return State(0, false, false, false)
        }
        while (presses.isNotEmpty() && now - presses.first() > 15000) presses.removeFirst()
        // Count release -> press edges, never repeated PRESSED callbacks or initial UNKNOWN -> PRESSED.
        if (previousBrake == false && brake) presses.addLast(now)
        previousBrake = brake
        if (now > armedUntil && presses.size >= 3 && gapMeters <= AlleyPassDistances.APPROACH + AlleyPassDistances.TOLERANCE) armedUntil = now + 30000
        val armed = now <= armedUntil
        val stopped = speedMps <= .01
        return State(presses.size, armed, stopped,
            armed && stopped && (brake || parked) && interactionAllowed)
    }

    fun consume(now: Long, speedMps: Double?, brake: Boolean?, gapMeters: Double?,
                interactionAllowed: Boolean, parked: Boolean = false): Boolean {
        if (!observe(now, speedMps, brake, gapMeters, interactionAllowed, parked).canConfirm) return false
        reset()
        return true
    }
}
