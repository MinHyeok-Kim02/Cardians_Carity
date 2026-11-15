package com.example.pleostest

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/** AAOS read-only speed diagnostic; independent of the Pleos brake API. */
class VehicleSpeedTest(private val context: Context, private val onStatus: (Float?) -> Unit = {}) {
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    @Volatile private var active = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private fun publish(value: Float?) {
        main.post { if (active) onStatus(value) }
    }
    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (!active || value.propertyId != VehiclePropertyIds.PERF_VEHICLE_SPEED) return
            val speed = value.value as? Float
            if (value.status != CarPropertyValue.STATUS_AVAILABLE ||
                speed == null || !speed.isFinite()) {
                publish(null)
                Log.w(TAG, "Speed = UNKNOWN; status=${value.status}; source=AAOS")
                return
            }
            publish(speed)
            Log.i(TAG, "Speed = $speed m/s; ${speed * 3.6f} km/h; event=UPDATE; source=AAOS")
        }

        override fun onErrorEvent(propId: Int, areaId: Int) {
            publish(null)
            Log.e(TAG, "Speed = UNKNOWN; property=$propId; area=$areaId; source=AAOS")
        }
    }

    fun start() {
        if (active) return
        if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CAR_SPEED permission missing; Speed = UNKNOWN")
            return
        }
        active = true
        try {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                if (!active) return@createCar
                if (!ready) {
                    manager = null
                    publish(null)
                    Log.w(TAG, "Car service disconnected; Speed = UNKNOWN")
                    return@createCar
                }
                try {
                    val properties = connectedCar.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                    manager = properties
                    val config = properties.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED)
                    if (config == null) {
                        Log.w(TAG, "Speed property unavailable; Speed = UNKNOWN")
                    } else {
                        Log.i(TAG, "Speed capability access=${config.access}; source=AAOS")
                        val registered = properties.registerCallback(
                            callback, VehiclePropertyIds.PERF_VEHICLE_SPEED,
                            CarPropertyManager.SENSOR_RATE_NORMAL
                        )
                        Log.i(TAG, "Speed listener registered=$registered; source=AAOS")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Speed setup failed; Speed = UNKNOWN", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Car connection failed; Speed = UNKNOWN", e)
            stop()
        }
    }

    fun stop() {
        active = false
        onStatus(null)
        try {
            manager?.unregisterCallback(callback)
        } catch (e: Exception) {
            Log.e(TAG, "Speed callback cleanup failed", e)
        } finally {
            manager = null
            car?.disconnect()
            car = null
        }
    }

    private companion object {
        const val TAG = "CaritySpeed"
    }
}
