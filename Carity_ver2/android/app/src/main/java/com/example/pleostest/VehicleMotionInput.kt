package com.example.pleostest

import kotlin.math.abs

/** Preserve the received speed; R supplies direction when AAOS reports only magnitude. */
object VehicleMotionInput {
    fun signedSpeed(receivedMps: Double?,gear: ConsoleDriveModel.Gear?): Double? {
        if(receivedMps==null || !receivedMps.isFinite()) return null
        return if(gear==ConsoleDriveModel.Gear.R) -abs(receivedMps) else receivedMps
    }
}
