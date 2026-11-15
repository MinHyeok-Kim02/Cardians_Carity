package com.example.pleostest

/** Pure keyboard motion model. ConsoleDriveModel translates it into emulator commands. */
class AlleyKeyboardDriver {
    enum class Control { ACCELERATE, BRAKE, LEFT, RIGHT, STOP }
    private val held = mutableSetOf<Control>()
    var speedMps = 0.0; private set
    var steering = 0.0; private set
    val brake: Boolean get() = Control.BRAKE in held || Control.STOP in held
    fun press(control: Control) { held.add(control); if (control == Control.STOP) speedMps = 0.0 }
    fun release(control: Control) { held.remove(control) }
    fun clear() { held.clear(); speedMps = 0.0; steering = 0.0 }
    fun holdForMacro() {
        held.remove(Control.ACCELERATE); held.remove(Control.LEFT); held.remove(Control.RIGHT)
        speedMps = 0.0; steering = 0.0
    }
    fun tick(seconds: Double) {
        if (!seconds.isFinite() || seconds <= 0) return
        val dt = seconds.coerceAtMost(.1)
        val acceleration = when {
            brake -> -2.0
            Control.ACCELERATE in held -> .65
            else -> -.35
        }
        // Console receives integer km/h. A short initial key press must not round back to zero.
        // This is an emulator input command only; world movement still waits for AAOS readback.
        val initial = if (!brake && Control.ACCELERATE in held && speedMps == 0.0)
            1.0 / 3.6 else speedMps
        speedMps = (initial + acceleration * dt).coerceIn(0.0, 6.0 / 3.6)
        val target = when {
            (Control.LEFT in held) == (Control.RIGHT in held) -> 0.0
            Control.LEFT in held -> -1.0
            else -> 1.0
        }
        // Apply the key target on the next Console tick; do not add a one-second steering ramp.
        // Vehicle movement still waits for the actual SDK steering readback.
        steering = target
    }
}
