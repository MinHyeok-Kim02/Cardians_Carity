package com.example.pleostest

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Actual AAOS gear readback. Reads run off the UI thread. */
class VehicleGearSource(private val context: Context, private val update: (Int?) -> Unit) {
    private val main=Handler(Looper.getMainLooper())
    private var car: Car?=null
    private var worker: ScheduledExecutorService?=null
    private var generation=0
    fun start() {
        if(car!=null) return
        val token=++generation
        car=Car.createCar(context,null,Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected,ready ->
            main.post {
                if(token!=generation) return@post
                worker?.shutdownNow(); worker=null
                if(!ready) { update(null); return@post }
                val properties=connected.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
                worker=Executors.newSingleThreadScheduledExecutor().apply {
                    scheduleWithFixedDelay({
                        val gear=try {
                            val value=properties.getProperty(Int::class.javaObjectType,VehiclePropertyIds.GEAR_SELECTION,0)
                            if(value.status==CarPropertyValue.STATUS_AVAILABLE) value.value else null
                        } catch(_:Exception) { null }
                        main.post { if(token==generation) update(gear) }
                    },0,500,TimeUnit.MILLISECONDS)
                }
            }
        }
    }
    fun stop() {
        generation++; worker?.shutdownNow(); worker=null
        car?.disconnect(); car=null; update(null)
    }
}
