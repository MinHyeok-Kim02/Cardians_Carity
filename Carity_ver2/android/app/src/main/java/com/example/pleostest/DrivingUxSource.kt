package com.example.pleostest

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Observes platform restrictions; unknown state is treated as restricted. */
class DrivingUxSource(context: Context, private val update: (Boolean) -> Unit) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var active = false
    fun start() {
        if (active) return
        active = true
        update(true)
        try {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (active) {
                    if (!ready) update(true)
                    else try {
                        val service = connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                        manager = service
                        service.registerListener { state ->
                            main.post { if (active) update(state.isRequiresDistractionOptimization) }
                        }
                        update(service.currentCarUxRestrictions.isRequiresDistractionOptimization)
                    } catch (e: Exception) { update(true); Log.w("CarityUx", "Restrictions unavailable", e) }
                }
            }
        } catch (e: Exception) { update(true); Log.w("CarityUx", "Connection failed", e) }
    }
    fun stop() {
        active = false
        try { manager?.unregisterListener() }
        catch (e: Exception) { Log.w("CarityUx", "Cleanup failed", e) }
        finally { manager = null; car?.disconnect(); car = null }
    }
}
