package com.example.pleostest

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.*
import ai.pleos.playground.vehicle.api.model.capability.*
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Debug emulator diagnostic; explicit button only, sequential writes with readback and restore. */
class PleosControlTest(context: Context, private val output: (String) -> Unit) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)
    @Volatile private var visible = false
    @Volatile private var closed = false
    @Volatile private var restorationFailed = false
    @Volatile private var properties: CarPropertyManager? = null
    internal val sdk = Vehicle(this.context)
    private var car: Car? = null

    init {
        sdk.initialize()
        car = Car.createCar(this.context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (!closed) properties = if (ready)
                connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager else null
        }
    }

    fun setVisible(value: Boolean) { visible = value }

    internal fun log(message: String) {
        Log.i("CarityControl", message)
        main.post { if (!closed) output(message) }
    }

    internal fun <T> awaitCall(request: ((T) -> Unit, (Exception) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: Exception? = null
        request({ value = it; latch.countDown() }, { error = it; latch.countDown() })
        check(latch.await(6, TimeUnit.SECONDS)) { "SDK callback timeout; result UNKNOWN" }
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun parked() {
        val manager = checkNotNull(properties) { "AAOS not connected" }
        val speed = manager.getProperty<Float>(Float::class.javaObjectType, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
        val gear = manager.getProperty<Int>(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, 0)
        check(speed.status == CarPropertyValue.STATUS_AVAILABLE &&
            gear.status == CarPropertyValue.STATUS_AVAILABLE &&
            speed.value.isFinite() && kotlin.math.abs(speed.value) < 0.01f &&
            gear.value == VehicleGear.GEAR_PARK) {
            "BLOCKED: speed=${speed.value}, gear=${gear.value}; requires speed=0 and P"
        }
        log("Safety speed=${speed.value} m/s gear=P")
    }

    internal fun supported(config: CapabilityConfig) {
        log("Capability access=${config.accessType}")
        check(config.accessType == AccessType.READ_WRITE) { "Read/write unsupported" }
    }

    internal fun nextValue(old: Int, bounds: AreaBounds<Int>): Int {
        check(old in bounds.minValue..bounds.maxValue && bounds.minValue < bounds.maxValue)
        return if (old == bounds.maxValue) old - 1 else old + 1
    }

    internal fun <T : Any> transaction(
        name: String, read: () -> T?, target: (T) -> T,
        write: (T) -> Unit, equivalent: (T?, T) -> Boolean = { a, b -> a == b }
    ) {
        check(visible && !closed) { "Screen inactive" }
        parked()
        val original = checkNotNull(read()) { "$name original UNKNOWN; no write" }
        val requested = target(original)
        check(!equivalent(original, requested)) { "$name target must change original" }
        log("$name BEFORE=$original REQUEST=$requested")
        var attempted = false
        try {
            parked()
            attempted = true
            write(requested)
            log("$name SET_CALLBACK received; verifying independent readback")
            var matched = false
            repeat(16) {
                Thread.sleep(250)
                val actual = read()
                matched = equivalent(actual, requested)
                if (!matched && it == 15) log("$name requested=$requested actual=$actual")
            }
            check(matched) { "$name readback did not match" }
            log("$name WRITE_VERIFIED requested=$requested")
        } finally {
            if (attempted) {
                try {
                    parked()
                    write(original)
                    var restored = false
                    repeat(16) {
                        Thread.sleep(250)
                        restored = equivalent(read(), original)
                    }
                    check(restored) { "restore readback mismatch" }
                    log("$name RESTORE_VERIFIED original=$original")
                } catch (e: Exception) {
                    restorationFailed = true
                    log("$name RESTORE_FAILED original=$original: ${e.message}; inspect Console")
                    throw e
                }
            }
        }
    }

    fun runAll(extended: Boolean = false, temperatureOnly: Boolean = false, fanOnly: Boolean = false) {
        val emulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"
        if (!emulator || context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            log("BLOCKED: debug emulator only")
            return
        }
        if (closed || !visible || restorationFailed || !busy.compareAndSet(false, true)) return
        worker.execute {
            try {
                val tests = if (fanOnly) listOf("HVAC.FanSpeed" to { hvacFanSpeed() }) else if (temperatureOnly) listOf("HVAC.Temperature" to { hvacTemperature() }) else if (extended) readOnlyTests() else listOf<Pair<String, () -> Unit>>(
                    "Window.position" to ::window,
                    "Ambient.brightness" to ::brightness,
                    "Ambient.color" to ::color,
                    "Ambient.switch" to ::lightSwitch,
                    "Mirror.heat" to ::mirror,
                    "Door.lock" to ::doorLock,
                    "Door.position" to ::doorPosition
                )
                tests.forEach { (name, action) ->
                    if (visible && !closed && !restorationFailed) try { action() } catch (e: Exception) {
                        log("$name NOT_VERIFIED: ${e.message}")
                    }
                }
                log("Tests finished; check WRITE_VERIFIED and RESTORE_VERIFIED per feature")
            } finally { busy.set(false) }
        }
    }

    private fun hvacFanSpeed() {
        val api = sdk.getHVAC()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>> { ok, fail ->
            api.checkHvacFanSpeedCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.keys.first()
        log("HVAC.FanSpeed area=${area.values.joinToString()} bounds=${areas.getValue(area)}")
        transaction("HVAC.FanSpeed",
            { awaitCall<Int?> { ok, fail -> api.getHvacFanSpeed(area, ok, fail) } },
            { original ->
                check(original == 3) { "Expected original fan=3; current=$original; no write" }
                val bounds = areas.getValue(area)
                check(3 in bounds.minValue..bounds.maxValue && 4 in bounds.minValue..bounds.maxValue)
                4
            },
            { value -> awaitCall<Int?> { ok, fail ->
                api.setHvacFanSpeed(area, value, { _, actual -> ok(actual) }, fail)
            } })
    }

    private fun hvacTemperature() {
        val api = sdk.getHVAC()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Float>>>> { ok, fail ->
            api.checkHvacTemperatureCapabilities({ c, a, _ -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.keys.first()
        val unit = ai.pleos.playground.vehicle.constant.unit.TemperatureUnit.CELSIUS
        transaction("HVAC.Temperature",
            { awaitCall<Float?> { ok, fail -> api.getHvacTemperature(area, unit, ok, fail) } },
            { old ->
                val bounds = areas.getValue(area)
                check(old.isFinite() && old in bounds.minValue..bounds.maxValue)
                val target = if (old + 1f <= bounds.maxValue) old + 1f else old - 1f
                check(target in bounds.minValue..bounds.maxValue)
                target
            },
            { value -> awaitCall<Float?> { ok, fail ->
                api.setHvacTemperature(area, value, unit, { _, actual -> ok(actual) }, fail)
            } })
    }

    private fun window() {
        val api = sdk.getWindow()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<WindowArea, AreaBounds<Int>>>> { ok, fail ->
            api.checkWindowPositionCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.keys.first { it.values.joinToString() == "ROW_1_RIGHT" }
        log("Window area=${area.values.joinToString()} bounds=${areas.getValue(area)}")
        transaction("Window.position",
            { awaitCall<Int?> { ok, fail -> api.getCurrentWindowPosition(area, ok, fail) } },
            { if (it == areas.getValue(area).maxValue) areas.getValue(area).minValue else areas.getValue(area).maxValue },
            { value -> awaitCall<Int?> { ok, fail -> api.setWindowPosition(area, value, { _, v -> ok(v) }, fail) } })
    }

    private fun brightness() {
        val api = sdk.getLight()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>> { ok, fail ->
            api.checkAmbientLightBrightnessCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.keys.first()
        transaction("Ambient.brightness",
            { awaitCall<Int?> { ok, fail -> api.getAmbientLightBrightness(area, ok, fail) } },
            { old -> val bounds = areas.getValue(area); val mid = bounds.minValue + (bounds.maxValue - bounds.minValue) / 2; if (old == mid) bounds.maxValue else mid },
            { value -> awaitCall<Int> { ok, fail -> api.setAmbientLightBrightness(area, value, { _, v -> ok(v) }, fail) } })
    }

    private fun color() {
        val api = sdk.getLight()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, List<SeatArea>>> { ok, fail ->
            api.checkAmbientLightBrightnessColorCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.first()
        transaction("Ambient.color",
            { awaitCall<Int?> { ok, fail -> api.getAmbientLightBrightnessColor(area, ok, fail) } },
            { if (it and 0xFFFFFF == 0xFF0000) 0x00FF00 else 0xFF0000 },
            { value -> awaitCall<Int> { ok, fail -> api.setAmbientLightBrightnessColor(area, value, { _, v -> ok(v) }, fail) } },
            { a, b -> a != null && (a and 0xFFFFFF) == (b and 0xFFFFFF) })
    }

    private fun lightSwitch() {
        val api = sdk.getLight()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<SeatArea, List<LightSwitch>>>> { ok, fail ->
            api.checkAmbientLightSwitchCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.keys.first()
        transaction("Ambient.switch",
            { awaitCall<LightSwitch?> { ok, fail -> api.getAmbientLightSwitch(area, ok, fail) } },
            { old -> areas.getValue(area).first { it != old } },
            { value -> awaitCall<LightSwitch?> { ok, fail -> api.setAmbientLightSwitch(area, value, { _, v -> ok(v) }, fail) } })
    }

    private fun mirror() {
        val api = sdk.getSideMirror()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<MirrorArea, AreaBounds<Int>>>> { ok, fail ->
            api.checkHvacSideMirrorHeatCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.keys.first { it.values.size == 1 }
        transaction("Mirror.heat",
            { awaitCall<Int?> { ok, fail -> api.getHvacSideMirrorHeatState(area, ok, fail) } },
            { nextValue(it, areas.getValue(area)) },
            { value -> awaitCall<Int?> { ok, fail -> api.setHvacSideMirrorHeat(area, value, { _, v -> ok(v) }, fail) } })
    }

    private fun doorLock() {
        val api = sdk.getDoor()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, List<DoorArea>>> { ok, fail ->
            api.checkDoorLockCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        log("Door lock supported areas=${areas.map { it.values.joinToString() }}")
        val supportedArea = areas.first { it.values.any { value -> value.name == "ROW_1_RIGHT" } }
        val area = DoorArea(*supportedArea.values.filter { it.name == "ROW_1_RIGHT" }.toTypedArray())
        log("Door lock test area=${area.values.joinToString()}")
        transaction("Door.lock",
            { awaitCall<Boolean?> { ok, fail -> api.getDoorLockState(area, ok, fail) } },
            { !it },
            { value -> awaitCall<Boolean?> { ok, fail -> api.setDoorLock(area, value, { _, v -> ok(v) }, fail) } })
    }

    private fun doorPosition() {
        val api = sdk.getDoor()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<DoorArea, AreaBounds<Int>>>> { ok, fail ->
            api.checkDoorPositionCapabilities({ c, a -> ok(c to a) }, fail)
        }
        supported(config)
        val area = areas.keys.first { it.values.joinToString() == "ROW_1_RIGHT" }
        log("Door area=${area.values.joinToString()} bounds=${areas.getValue(area)}")
        transaction("Door.position",
            { awaitCall<Int?> { ok, fail -> api.getCurrentDoorPosition(area, ok, fail) } },
            { nextValue(it, areas.getValue(area)) },
            { value -> awaitCall<Int?> { ok, fail -> api.setDoorPosition(area, value, { _, v -> ok(v) }, fail) } })
    }

    fun close() {
        closed = true
        visible = false
        // Let any in-flight request finish its restoration before releasing services.
        worker.execute { sdk.release(); car?.disconnect(); properties = null }
        worker.shutdown()
    }
}
