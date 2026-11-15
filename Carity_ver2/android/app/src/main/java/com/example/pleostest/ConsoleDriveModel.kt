package com.example.pleostest

/** Console input generator; actual app motion still uses AAOS/Pleos readback. */
class ConsoleDriveModel {
    enum class Gear(val wire: String) { D("Drive"), R("Reverse"), N("Neutral"), P("Park") }
    data class Command(val speedKph: Double, val accelerator: Int, val brake: Int, val steering: Int)
    private val driver = AlleyKeyboardDriver()
    private val held = mutableSetOf<AlleyKeyboardDriver.Control>()
    var gear = Gear.P; private set
    @Synchronized fun press(key: AlleyKeyboardDriver.Control) { held.add(key); if(key==AlleyKeyboardDriver.Control.STOP) driver.press(key) }
    @Synchronized fun release(key: AlleyKeyboardDriver.Control) { held.remove(key); driver.release(key) }
    @Synchronized fun clear() { held.clear(); driver.clear() }
    @Synchronized fun select(value: Gear, actualSpeedMps: Double?): Boolean {
        if(actualSpeedMps==null || !actualSpeedMps.isFinite()) return false
        if(value!=Gear.N && value!=gear && kotlin.math.abs(actualSpeedMps)>.01) return false
        gear=value
        return true
    }
    @Synchronized fun tick(dt: Double): Command {
        val driving=gear==Gear.D || gear==Gear.R
        val footBrake=AlleyKeyboardDriver.Control.BRAKE in held || AlleyKeyboardDriver.Control.STOP in held
        for(key in AlleyKeyboardDriver.Control.values()) {
            val down=key in held && (key!=AlleyKeyboardDriver.Control.ACCELERATE || driving)
            if(down) driver.press(key) else driver.release(key)
        }
        if(gear==Gear.P) driver.press(AlleyKeyboardDriver.Control.STOP)
        driver.tick(dt)
        return Command(driver.speedMps*3.6,
            if(driving && !footBrake && AlleyKeyboardDriver.Control.ACCELERATE in held) 15 else 0,
            if(footBrake) 100 else 0, (driver.steering*500).toInt())
    }
}
