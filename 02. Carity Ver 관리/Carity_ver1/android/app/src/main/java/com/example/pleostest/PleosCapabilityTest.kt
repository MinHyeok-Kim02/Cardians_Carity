package com.example.pleostest

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.area.MirrorArea

/** Queries capability and current values only. Never issues a vehicle write. */
class PleosCapabilityTest(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null

    private fun readable(config: CapabilityConfig) =
        config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE

    private fun granted(name: String) =
        context.checkSelfPermission("pleos.car.permission.$name") == PackageManager.PERMISSION_GRANTED

    private fun result(name: String, config: CapabilityConfig) {
        Log.i(TAG, "$name access=${config.accessType}; source=PLEOS; writeTest=NOT_RUN")
    }

    private fun failure(name: String): (Exception) -> Unit = {
        Log.e(TAG, "$name query failed; support=UNKNOWN", it)
    }

    private fun query(sdk: Vehicle, action: () -> Unit) {
        handler.post {
            if (vehicle === sdk) {
                try {
                    action()
                } catch (e: Exception) {
                    Log.e(TAG, "Capability/read request failed; support=UNKNOWN", e)
                }
            }
        }
    }

    fun start() {
        if (vehicle != null) return
        val sdk = Vehicle(context)
        vehicle = sdk
        try {
            sdk.initialize()
            Log.i(TAG, "Capability audit started; no vehicle writes")
            listOf("CAR_WINDOWS", "CONTROL_CAR_WINDOWS", "CAR_LIGHTS",
                "CONTROL_CAR_LIGHTS", "CAR_MIRRORS", "CONTROL_CAR_MIRRORS").forEach {
                Log.i(TAG, "Permission $it granted=${granted(it)}")
            }
            if (granted("CAR_WINDOWS")) {
                sdk.getWindow().checkWindowPositionCapabilities(
                    onComplete = { config: CapabilityConfig, areas: Map<WindowArea, AreaBounds<Int>> ->
                        query(sdk) {
                            result("Window.position", config)
                            areas.forEach { (area, bounds) ->
                                val name = area.values.joinToString()
                                Log.i(TAG, "Window area=$name bounds=$bounds")
                                if (readable(config)) sdk.getWindow().getCurrentWindowPosition(
                                    area,
                                    { Log.i(TAG, "Window area=$name position=$it; source=PLEOS") },
                                    failure("Window.position")
                                )
                            }
                        }
                    },
                    onFailed = failure("Window.capability")
                )
            }
            if (granted("CAR_LIGHTS")) {
                val light = sdk.getLight()
                light.checkAmbientLightBrightnessCapabilities(
                    onComplete = { config, areas -> query(sdk) {
                        result("Ambient.brightness", config)
                        areas.forEach { (area, bounds) ->
                            val name = area.values.joinToString()
                            Log.i(TAG, "Ambient area=$name brightnessBounds=$bounds")
                            if (readable(config)) light.getAmbientLightBrightness(area,
                                { Log.i(TAG, "Ambient area=$name brightness=$it") }, failure("Ambient.brightness"))
                        }
                    } },
                    onFailed = failure("Ambient.brightness.capability")
                )
                light.checkAmbientLightBrightnessColorCapabilities(
                    onComplete = { config, areas -> query(sdk) {
                        result("Ambient.color", config)
                        areas.forEach { area ->
                            val name = area.values.joinToString()
                            if (readable(config)) light.getAmbientLightBrightnessColor(area,
                                { Log.i(TAG, "Ambient area=$name colorArgb=$it") }, failure("Ambient.color"))
                        }
                    } },
                    onFailed = failure("Ambient.color.capability")
                )
                light.checkAmbientLightSwitchCapabilities(
                    onComplete = { config, areas -> query(sdk) {
                        result("Ambient.switch", config)
                        Log.i(TAG, "Ambient switch areas=${areas.map { it.key.values.joinToString() to it.value }}")
                    } },
                    onFailed = failure("Ambient.switch.capability")
                )
                sdk.getTurnSignal().checkTurnSignalStateCapabilities(
                    { config -> query(sdk) { result("TurnSignal.state", config) } },
                    failure("TurnSignal.capability")
                )
            }
            if (granted("CAR_MIRRORS")) {
                sdk.getSideMirror().checkHvacSideMirrorHeatCapabilities(
                    onComplete = { config: CapabilityConfig, areas: Map<MirrorArea, AreaBounds<Int>> ->
                        query(sdk) {
                            result("Mirror.heat", config)
                            areas.forEach { (area, bounds) ->
                                val name = area.values.joinToString()
                                Log.i(TAG, "Mirror area=$name heatBounds=$bounds")
                                if (readable(config)) sdk.getSideMirror().getHvacSideMirrorHeatState(area,
                                    { Log.i(TAG, "Mirror area=$name heat=$it") }, failure("Mirror.heat"))
                            }
                        }
                    },
                    onFailed = failure("Mirror.heat.capability")
                )
            }
            sdk.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                { config, bounds -> query(sdk) {
                    result("FrontWheel.angle", config)
                    Log.i(TAG, "FrontWheel angleBounds=$bounds")
                    if (readable(config)) sdk.getSteeringWheel().getCurrentSteeringWheelAngle(
                        { Log.i(TAG, "FrontWheel angleDegrees=$it") }, failure("FrontWheel.angle"))
                } },
                failure("FrontWheel.capability")
            )
            Log.i(TAG, "Mirror folding / hazard control / obstacle distance: no API in inspected Vehicle 2.3.2 interfaces")
        } catch (e: Exception) {
            Log.e(TAG, "Capability audit setup failed", e)
            stop()
        }
    }

    fun stop() {
        val sdk = vehicle ?: return
        vehicle = null
        try {
            sdk.release()
        } catch (e: Exception) {
            Log.e(TAG, "Capability audit cleanup failed", e)
        }
    }

    private companion object {
        const val TAG = "CarTalkCapability"
    }
}
