package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test
import com.example.pleostest.ConsoleDriveModel.Gear

class VehicleMotionInputTest {
    @Test fun reverseAcceptsMagnitudeAndAlreadySignedReadback() {
        assertEquals(-1.0,VehicleMotionInput.signedSpeed(1.0,Gear.R)!!,0.0)
        assertEquals(-1.0,VehicleMotionInput.signedSpeed(-1.0,Gear.R)!!,0.0)
        assertNull(VehicleMotionInput.signedSpeed(null,Gear.R))
        assertNull(VehicleMotionInput.signedSpeed(Double.NaN,Gear.R))
    }
    @Test fun consoleReverseThrottleReadbackMovesWorldBackAndBrakeStops() {
        val console=ConsoleDriveModel(); val world=AlleyWorld()
        assertTrue(console.select(Gear.R,0.0)); console.press(AlleyKeyboardDriver.Control.ACCELERATE)
        repeat(10) {
            val command=console.tick(.1)
            val simulatedAaosReadback=Math.round(command.speedKph)/3.6
            world.advance(.1,VehicleMotionInput.signedSpeed(simulatedAaosReadback,Gear.R)!!,0.0)
        }
        assertTrue(world.y<4.0)
        console.press(AlleyKeyboardDriver.Control.STOP)
        val stopped=console.tick(.1)
        assertEquals(0.0,stopped.speedKph,0.0)
        val before=world.y
        world.advance(.1,VehicleMotionInput.signedSpeed(stopped.speedKph/3.6,Gear.R)!!,0.0)
        assertEquals(before,world.y,0.0)
    }
}
