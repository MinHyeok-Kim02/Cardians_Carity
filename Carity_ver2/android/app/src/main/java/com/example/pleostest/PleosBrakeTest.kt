package com.example.pleostest

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Brake
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener

/** Read-only SDK diagnostic. No simulated values or vehicle control requests. */
class PleosBrakeTest(private val context: Context, private val onStatus: (Boolean?) -> Unit = {}) {
    private val handler = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var brake: Brake? = null
    private var listener: BrakePedalStatusListener? = null
    private var poll: Runnable? = null

    fun start() {
        if (vehicle != null) return
        if (context.checkSelfPermission("pleos.car.permission.CAR_DRIVE") !=
            PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CAR_DRIVE permission missing; Brake = UNKNOWN")
            return
        }
        try {
            val sdk = Vehicle(context)
            vehicle = sdk
            sdk.initialize()
            Log.i(TAG, "Vehicle initialize requested; source=PLEOS")
            val api = sdk.getBrake()
            brake = api
            api.checkBrakePedalStatusCapabilities(
                onComplete = { config ->
                    handler.post {
                        if (vehicle !== sdk) return@post
                        Log.i(TAG, "Brake capability = ${config.accessType}")
                        if (config.accessType == AccessType.READ ||
                            config.accessType == AccessType.READ_WRITE) {
                            subscribe(sdk, api)
                        } else {
                            Log.w(TAG, "Brake read unsupported; Brake = UNKNOWN")
                        }
                    }
                },
                onFailed = { Log.e(TAG, "Brake capability failed; Brake = UNKNOWN", it) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle initialization failed; Brake = UNKNOWN", e)
            stop()
        }
    }

    private fun subscribe(sdk: Vehicle, api: Brake) {
        try {
            val callback = object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(status: Boolean?) {
                    handler.post {
                        if (vehicle === sdk) logStatus(status, "UPDATE")
                    }
                }

                override fun onFailed(e: Exception) {
                    handler.post { if (vehicle === sdk) onStatus(null) }
                    Log.e(TAG, "Brake subscription failed; Brake = UNKNOWN", e)
                }
            }
            listener = callback
            api.registerBrakePedalStatus(callback)
            Log.i(TAG, "Brake listener registration requested")
            api.getCurrentBrakePedalStatus(
                onComplete = { status ->
                    handler.post {
                        if (vehicle === sdk) logStatus(status, "INITIAL")
                    }
                },
                onFailed = { Log.e(TAG, "Brake initial read failed; Brake = UNKNOWN", it) }
            )
            // Refresh held states too; only release -> press edges count in the macro gate.
            val refresh = object : Runnable {
                override fun run() {
                    if (vehicle !== sdk) return
                    fun received(status: Boolean?) {
                        handler.post {
                            if (vehicle === sdk) {
                                logStatus(status, "REFRESH")
                                handler.postDelayed(this, 1000)
                            }
                        }
                    }
                    try {
                        api.getCurrentBrakePedalStatus(
                            onComplete = { received(it) },
                            onFailed = { received(null); Log.w(TAG, "Brake refresh failed", it) }
                        )
                    } catch (e: Exception) {
                        received(null); Log.w(TAG, "Brake refresh failed", e)
                    }
                }
            }
            poll = refresh
            handler.postDelayed(refresh, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Brake read/registration failed; Brake = UNKNOWN", e)
        }
    }

    private fun logStatus(status: Boolean?, event: String) {
        val value = when (status) {
            true -> "PRESSED"
            false -> "RELEASED"
            null -> "UNKNOWN"
        }
        onStatus(status)
        Log.i(TAG, "Brake = $value; event=$event; source=PLEOS")
    }

    fun stop() {
        val sdk = vehicle ?: return
        vehicle = null
        poll?.let { handler.removeCallbacks(it) }; poll = null
        onStatus(null)
        try {
            listener?.let { brake?.unregisterBrakePedalStatus(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Brake listener cleanup failed", e)
        } finally {
            listener = null
            brake = null
            try {
                sdk.release()
            } catch (e: Exception) {
                Log.e(TAG, "Vehicle release failed", e)
            }
            Log.i(TAG, "Brake test stopped")
        }
    }

    private companion object {
        const val TAG = "CarTalkBrake"
    }
}
