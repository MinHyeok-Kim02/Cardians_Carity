package com.example.pleostest

import ai.pleos.playground.vehicle.api.model.area.*
import ai.pleos.playground.vehicle.api.model.capability.*
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit

/** Only capability/get calls. No vehicle state changes. */
internal fun PleosControlTest.readOnlyTests(): List<Pair<String, () -> Unit>> = listOf(
    "HVAC.Temperature.READ" to {
        val api = sdk.getHVAC()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Float>>>> { ok, fail ->
            api.checkHvacTemperatureCapabilities({ c, a, _ -> ok(c to a) }, fail)
        }
        log("HVAC.Temperature capability=${config.accessType} areas=$areas")
        if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE) {
            areas.keys.forEach { area ->
                val value = awaitCall<Float?> { ok, fail -> api.getHvacTemperature(area, TemperatureUnit.CELSIUS, ok, fail) }
                log("HVAC.Temperature area=${area.values.joinToString()} value=$value Celsius; readOnly=true")
                val current = awaitCall<Float?> { ok, fail -> api.getHvacTemperatureCurrent(area, ok, fail) }
                log("HVAC.CurrentTemperature area=${area.values.joinToString()} value=$current Celsius; readOnly=true")
            }
        }
    },
    "HVAC.FanSpeed.READ" to {
        val api = sdk.getHVAC()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, Map<SeatArea, AreaBounds<Int>>>> { ok, fail ->
            api.checkHvacFanSpeedCapabilities({ c, a -> ok(c to a) }, fail)
        }
        log("HVAC.FanSpeed capability=${config.accessType} areas=$areas")
        if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE)
            areas.keys.forEach { area ->
                val value = awaitCall<Int?> { ok, fail -> api.getHvacFanSpeed(area, ok, fail) }
                log("HVAC.FanSpeed area=${area.values.joinToString()} value=$value; readOnly=true")
            }
    },
    "HVAC.Ac.READ" to {
        val api = sdk.getHVAC()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, List<SeatArea>>> { ok, fail ->
            api.checkHvacAcCapabilities({ c, a -> ok(c to a) }, fail)
        }
        log("HVAC.Ac capability=${config.accessType}")
        if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE)
            areas.forEach { area ->
                val value = awaitCall<Boolean?> { ok, fail -> api.getHvacAcState(area, ok, fail) }
                log("HVAC.Ac area=${area.values.joinToString()} value=$value; readOnly=true")
            }
    },
    "HVAC.Power.READ" to {
        val api = sdk.getHVAC()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, List<SeatArea>>> { ok, fail ->
            api.checkHvacPowerCapabilities({ c, a -> ok(c to a) }, fail)
        }
        log("HVAC.Power capability=${config.accessType}")
        if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE)
            areas.forEach { area ->
                val value = awaitCall<Boolean?> { ok, fail -> api.getHvacPowerState(area, ok, fail) }
                log("HVAC.Power area=${area.values.joinToString()} value=$value; readOnly=true")
            }
    },
    "GloveBox.READ" to {
        val value = awaitCall<Boolean?> { ok, fail -> sdk.getDoor().getCurrentGloveBoxLockState(ok, fail) }
        log("GloveBox.lock value=$value; readOnly=true")
    },
    "Toll.READ" to {
        val value = awaitCall<Int?> { ok, fail -> sdk.getToll().getCurrentEtcsVoiceGuidanceVolume(ok, fail) }
        log("Toll.volume value=$value; readOnly=true")
    },
    "Window.lock.READ" to {
        val api = sdk.getWindow()
        val (config, areas) = awaitCall<Pair<CapabilityConfig, List<WindowArea>>> { ok, fail ->
            api.checkWindowLockCapabilities({ c, a -> ok(c to a) }, fail)
        }
        log("Window.lock capability=${config.accessType}")
        if (config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE)
            areas.forEach { area ->
                val value = awaitCall<Boolean?> { ok, fail -> api.getCurrentWindowLockState(area, ok, fail) }
                log("Window.lock area=${area.values.joinToString()} value=$value; readOnly=true")
            }
    }
)
