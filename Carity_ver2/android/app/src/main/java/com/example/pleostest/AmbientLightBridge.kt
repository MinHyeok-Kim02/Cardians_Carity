package com.example.pleostest

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Color-only parked AVD demonstration. One serial worker owns SDK requests and restoration.
 * Set callback is diagnostic only. Independent GET must match before reporting applied.
 * Original colors are journaled before any SET, including a SET whose callback later fails.
 */
class AmbientLightBridge(context: Context, private val output: (String) -> Unit) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val prefs = this.context.getSharedPreferences("onsense.feature2.restore", Context.MODE_PRIVATE)
    private val closing = AtomicBoolean(false)
    private data class Desired(val enabled: Boolean = false, val visible: Boolean = false,
        val left: SideClearanceWarning.Level = SideClearanceWarning.Level.GREEN,
        val right: SideClearanceWarning.Level = SideClearanceWarning.Level.GREEN)
    @Volatile private var desired = Desired()
    @Volatile private var manager: CarPropertyManager? = null
    private var car: Car? = null
    private var sdk: Vehicle? = null
    private var areas = emptyList<SeatArea>()
    private val lastRequested = mutableMapOf<String, Int>()
    private val results = linkedMapOf<String, String>()
    private var lastNotice = ""
    private val allowed = Build.HARDWARE in listOf("ranchu", "goldfish") &&
        this.context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
        this.context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    init { worker.scheduleWithFixedDelay(::pulse, 0, 400, TimeUnit.MILLISECONDS) }

    fun visible(value: Boolean) { desired = desired.copy(visible = value, enabled = if(value) desired.enabled else false) }
    fun enable(value: Boolean) { desired = desired.copy(enabled = value) }
    fun warnings(left: SideClearanceWarning.Level, right: SideClearanceWarning.Level) {
        desired = desired.copy(left = left, right = right)
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }
    private fun notice(message: String) {
        if (lastNotice == message) return
        lastNotice = message
        main.post { if (!closing.get()) output(message) }
    }
    private fun areaKey(area: SeatArea) = area.values.map { it.name }.sorted().joinToString(",")
    private fun hex(value: Int?) = value?.let { String.format("#%08X", it) } ?: "UNKNOWN"
    private fun same(a: Int?, b: Int) = a != null && (a and 0xFFFFFF) == (b and 0xFFFFFF)

    private fun <T> await(request: ((T) -> Unit, (Exception) -> Unit) -> Unit): T {
        val latch = CountDownLatch(1)
        var result: T? = null; var failure: Exception? = null
        request({ result=it; latch.countDown() }, { failure=it; latch.countDown() })
        check(latch.await(5, TimeUnit.SECONDS)) { "callback timeout; 적용 미확인" }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun connect() {
        if (sdk != null) return
        check(context.checkSelfPermission("pleos.car.permission.CAR_LIGHTS") == PackageManager.PERMISSION_GRANTED) {
            "CAR_LIGHTS 권한 없음"
        }
        val vehicle = Vehicle(context)
        try {
            vehicle.initialize()
            val light = vehicle.getLight()
            val (config, supported) = await<Pair<CapabilityConfig, List<SeatArea>>> { ok, fail ->
                light.checkAmbientLightBrightnessColorCapabilities({ c, a -> ok(c to a) }, fail)
            }
            log("AMBIENT_CAPABILITY permission=CAR_LIGHTS access=${config.accessType} areas=${supported.map(::areaKey)}")
            check(config.accessType == AccessType.READ_WRITE && supported.isNotEmpty()) { "지원되는 읽기·쓰기 영역 없음" }
            val occupied = mutableSetOf<String>()
            areas = supported.distinctBy(::areaKey).sortedBy { it.values.size }.filter { area ->
                val names = area.values.map { it.name }
                val disjoint = names.none { it in occupied }
                if(disjoint) occupied.addAll(names)
                else log("AMBIENT_AREA_SKIPPED area=${areaKey(area)} reason=OVERLAPPING_SUPPORTED_AREA")
                disjoint
            }
            log("AMBIENT_ROUTING areas=${areas.map { areaKey(it) to
                (if(it.values.all { v -> v.name.endsWith("_LEFT") }) "LEFT"
                else if(it.values.all { v -> v.name.endsWith("_RIGHT") }) "RIGHT" else "BOTH_MAX") }}")
            sdk = vehicle
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                manager = if (ready && !closing.get()) connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager else null
            }
        } catch(e: Exception) {
            vehicle.release()
            throw e
        }
    }

    /** Independent real AAOS check before every color write. Synthetic/keyboard input is never used. */
    private fun parked(): Boolean = try {
        val properties = manager
        if (properties == null) false else {
            val speed = properties.getProperty<Float>(Float::class.javaObjectType, VehiclePropertyIds.PERF_VEHICLE_SPEED, 0)
            val gear = properties.getProperty<Int>(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, 0)
            speed.status == CarPropertyValue.STATUS_AVAILABLE && gear.status == CarPropertyValue.STATUS_AVAILABLE &&
                speed.value.isFinite() && kotlin.math.abs(speed.value) < .01f && gear.value == VehicleGear.GEAR_PARK
        }
    } catch (_: Exception) { false }

    private fun read(area: SeatArea): Int? = await { ok, fail ->
        checkNotNull(sdk).getLight().getAmbientLightBrightnessColor(area, ok, fail)
    }
    private fun write(area: SeatArea, value: Int, phase: String) {
        check(parked()) { "P·0km/h 필요; 복원은 정차 후 재시도" }
        val callback = await<Int> { ok, fail ->
            checkNotNull(sdk).getLight().setAmbientLightBrightnessColor(area, value, { returnedArea, returned ->
                log("AMBIENT_SET_CALLBACK phase=$phase area=${areaKey(returnedArea)} value=${hex(returned)}")
                ok(returned)
            }, fail)
        }
        log("AMBIENT_SET_ACK phase=$phase area=${areaKey(area)} callback=${hex(callback)}; independent GET pending")
    }
    private fun readback(area: SeatArea, requested: Int, phase: String): Int? {
        var actual: Int? = null
        // Several independent samples tolerate propagation delay. Never use SET's echoed value.
        for (attempt in 1..3) {
            Thread.sleep(250)
            actual = read(area)
            log("AMBIENT_READBACK phase=$phase area=${areaKey(area)} requested=${hex(requested)} actual=${hex(actual)} attempt=$attempt matched=${same(actual,requested)}")
            if (same(actual, requested)) break
        }
        return actual
    }

    private fun apply(area: SeatArea, target: Int) {
        val key = areaKey(area)
        if (lastRequested[key] == target) return
        lastRequested[key] = target // A mismatch is not retried continuously while staying in the same warning band.
        val before = checkNotNull(read(area)) { "original UNKNOWN; no write" }
        val light = checkNotNull(sdk).getLight()
        if (!prefs.contains(key)) {
            check(prefs.edit().putInt(key, before).commit()) { "원래 색 저장 실패; no write" }
            // Brightness/switch are recorded, but deliberately left untouched.
            try {
                val brightness = await<Int?> { ok, fail -> light.getAmbientLightBrightness(area,ok,fail) }
                val switch = await<ai.pleos.playground.vehicle.constant.control.LightSwitch?> { ok, fail ->
                    light.getAmbientLightSwitch(area,ok,fail)
                }
                log("AMBIENT_ORIGINAL area=$key color=${hex(before)} brightness=$brightness switch=$switch")
            } catch(e: Exception) { log("AMBIENT_ORIGINAL_EXTRA_UNKNOWN area=$key reason=${e.message}; brightness/switch unchanged") }
        }
        log("AMBIENT_REQUEST area=$key BEFORE=${hex(before)} REQUEST=${hex(target)}")
        try {
            check(desired.enabled && desired.visible && !closing.get()) { "시연 종료; no new write" }
            write(area,target,"APPLY")
            val actual = readback(area,target,"APPLY")
            val outcome = when {
                !same(actual,target) -> "적용 미확인"
                same(before,target) -> "현재값 일치(변경 없음)"
                else -> "색 변경 재조회 일치"
            }
            results[key] = "$key $outcome · 요청 ${hex(target)} / 조회 ${hex(actual)}"
            log("AMBIENT_RESULT area=$key outcome=$outcome requested=${hex(target)} actual=${hex(actual)}")
        } catch(e: Exception) {
            results[key] = "$key 적용 미확인 · ${e.message}"
            log("AMBIENT_APPLY_FAILED area=$key reason=${e.message}")
        }
    }

    private fun restore(): Boolean {
        val pending = prefs.all
        if (pending.isEmpty()) return true
        if (!parked()) { notice("복원 대기 · 실제 차량을 P·0km/h로 설정하세요"); return false }
        var success = true
        for ((key, raw) in pending) {
            val original = raw as? Int ?: continue
            val area = areas.find { areaKey(it) == key }
            if (area == null) { success=false; log("AMBIENT_RESTORE_PENDING area=$key unsupported now"); continue }
            try {
                val before = read(area)
                log("AMBIENT_RESTORE_BEFORE area=$key current=${hex(before)} original=${hex(original)}")
                write(area,original,"RESTORE")
                val actual = readback(area,original,"RESTORE")
                check(same(actual,original)) { "restore readback mismatch actual=${hex(actual)}" }
                check(prefs.edit().remove(key).commit()) { "restore journal clear failed" }
                log("AMBIENT_RESTORE_VERIFIED area=$key original=${hex(original)} actual=${hex(actual)}")
                results[key] = "$key 원래 색 복원 재조회 일치 ${hex(actual)}"
            } catch(e: Exception) {
                success=false
                log("AMBIENT_RESTORE_PENDING area=$key original=${hex(original)} reason=${e.message}")
                results[key] = "$key 복원 미확인 · 다음 정차 시 재시도"
            }
        }
        return success
    }

    private var recovering = true
    private fun pulse() {
        if (closing.get()) return
        try {
            val want = desired
            if (!allowed) { if(want.enabled) notice("차량 조명 적용 미확인 · 디버그 Pleos AVD 전용"); return }
            if (!want.enabled && prefs.all.isEmpty()) return
            connect()
            if (manager == null) { notice("AAOS 연결 대기 · 적용 미확인"); return }
            if (recovering) {
                if (!restore()) return
                recovering=false
            }
            if (!want.enabled || !want.visible) {
                if (restore()) {
                    lastRequested.clear()
                    notice(if(results.isEmpty()) "차량 조명 연동 꺼짐" else results.values.joinToString("\n"))
                }
                return
            }
            if (!parked()) {
                // Continuous driving writes are outside this first verification scope.
                desired = desired.copy(enabled=false)
                log("AMBIENT_DEMO_STOPPED reason=NOT_PARKED; restoration pending until P/0")
                notice("차량 조명 연동 중지 · P·0km/h 필요, 원래 색 복원 대기")
                return
            }
            for (area in areas) {
                if (!desired.enabled || !desired.visible || closing.get()) break
                val level = SideClearanceWarning.route(area.values.map { it.name }, want.left, want.right)
                if (level != SideClearanceWarning.Level.UNKNOWN) apply(area,level.argb)
            }
            notice(results.values.joinToString("\n").ifEmpty { "지원 영역 확인 중 · 적용 미확인" })
        } catch(e: Exception) {
            log("AMBIENT_NOT_VERIFIED reason=${e.message}")
            notice("차량 조명 적용 미확인 · ${e.message}")
        }
    }

    fun close() {
        if(!closing.compareAndSet(false,true)) return
        desired = desired.copy(enabled=false,visible=false)
        worker.execute {
            try { if(sdk != null) restore() }
            finally { sdk?.release(); sdk=null; car?.disconnect(); manager=null }
        }
        worker.shutdown() // queued restoration finishes before release; journal survives failure/process death.
    }

    companion object { const val TAG = "CarityLight" }
}
