package com.example.pleostest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import kotlin.math.abs
import kotlin.math.max

/** Read only. Normalization is a demo control mapping, not a physical steering calibration. */
class PleosSteeringSource(context: Context, private val update: (Float?, Float?) -> Unit) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var listener: SteeringWheelAngleListener? = null
    private var range = 500f
    private var poll: Runnable? = null

    fun start() {
        if (vehicle != null) return
        val sdk = Vehicle(context)
        vehicle = sdk
        try {
            sdk.initialize()
            val api = sdk.getSteeringWheel()
            fun publish(value: Float?) {
                main.post {
                    if (vehicle === sdk) {
                        val angle = value?.takeIf { it.isFinite() }
                        val normalized = PleosSteeringMapping.toSimulation(angle, range)
                        update(angle, normalized)
                        if ((context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                            Log.d("CarTalkSteering", "raw=$angle normalized=$normalized receivedAt=${android.os.SystemClock.elapsedRealtime()} source=PLEOS")
                    }
                }
            }
            api.checkSteeringWheelAngleCapabilities({ config, bounds ->
                main.post {
                    if (vehicle === sdk) {
                        if (config.accessType != AccessType.READ && config.accessType != AccessType.READ_WRITE) {
                            update(null, null)
                        } else {
                            val reported = max(abs(bounds.minValue), abs(bounds.maxValue))
                            if (!reported.isFinite() || reported <= 0) { update(null, null); return@post }
                            range = reported
                            val callback = object : SteeringWheelAngleListener {
                                override fun onSteeringWheelAngleUpdated(angle: Float?) { publish(angle) }
                                override fun onFailed(e: Exception) { publish(null); Log.e("CarTalkSteering", "Read failed", e) }
                            }
                            listener = callback
                            api.registerSteeringWheelAngle(callback)
                            val refresh = object : Runnable {
                                override fun run() {
                                    if (vehicle !== sdk) return
                                    try { api.getCurrentSteeringWheelAngle({ publish(it) }, { publish(null) }) }
                                    catch (e: Exception) { publish(null); Log.w("CarTalkSteering", "Refresh failed", e) }
                                    main.postDelayed(this, 1000)
                                }
                            }
                            poll = refresh
                            main.post(refresh)
                        }
                    }
                }
            }, { publish(null); Log.e("CarTalkSteering", "Capability failed", it) })
        } catch (e: Exception) {
            Log.e("CarTalkSteering", "Connection failed", e)
            stop()
        }
    }

    fun stop() {
        val sdk = vehicle ?: return
        vehicle = null
        poll?.let { main.removeCallbacks(it) }; poll = null
        try { listener?.let { sdk.getSteeringWheel().unregisterSteeringWheelAngle(it) } }
        catch (e: Exception) { Log.w("CarTalkSteering", "Unregister failed", e) }
        finally { listener = null; sdk.release(); update(null, null) }
    }
}
