package com.example.pleostest
import org.junit.Assert.*
import org.junit.Test
import com.example.pleostest.ConsoleDriveModel.Gear.*
import com.example.pleostest.AlleyKeyboardDriver.Control.*

class ConsoleDriveModelTest {
    @Test fun briefThrottleProducesVisibleConsoleSpeedAndThenCoastsToRest() {
        val m=ConsoleDriveModel()
        assertTrue(m.select(D,0.0))
        m.press(ACCELERATE)
        assertTrue(Math.round(m.tick(.1).speedKph)>=1)
        m.release(ACCELERATE)
        assertTrue(Math.round(m.tick(.1).speedKph)>=1)
        repeat(40) { m.tick(.1) }
        assertEquals(0.0,m.tick(.1).speedKph,0.0)
    }
    @Test fun brakePreventsInitialThrottleKick() {
        val m=ConsoleDriveModel(); m.select(D,0.0)
        m.press(ACCELERATE); m.press(BRAKE)
        assertEquals(0.0,m.tick(.1).speedKph,0.0)
        assertEquals(100,m.tick(.1).brake)
    }
    @Test fun selectingDriveAloneDoesNotAccelerate() {
        val m=ConsoleDriveModel(); assertTrue(m.select(D,0.0))
        repeat(20) { assertEquals(0.0,m.tick(.1).speedKph,0.0) }
    }
    @Test fun driveSelectionIsLatchedWithoutHoldingGear() {
        val m=ConsoleDriveModel(); assertTrue(m.select(D,0.0)); m.press(ACCELERATE)
        repeat(20) { m.tick(.1) }
        assertEquals(D,m.gear); assertTrue(m.tick(.1).speedKph>0)
    }
    @Test fun parkDoesNotAccelerateAndReverseUsesConsoleMagnitude() {
        val m=ConsoleDriveModel(); m.press(ACCELERATE)
        repeat(10) { assertEquals(0.0,m.tick(.1).speedKph,0.0) }
        assertTrue(m.select(R,0.0))
        assertTrue(m.tick(.1).speedKph>0); assertEquals(R,m.gear)
    }
    @Test fun movingOrUnknownDataBlocksParkAndDirectionChange() {
        val m=ConsoleDriveModel(); m.select(D,0.0)
        assertFalse(m.select(P,1.0)); assertFalse(m.select(R,1.0)); assertFalse(m.select(P,null))
        assertEquals(D,m.gear); assertTrue(m.select(N,1.0))
    }
    @Test fun neutralCoastsAndCannotAccelerate() {
        val m=ConsoleDriveModel(); m.select(D,0.0); m.press(ACCELERATE)
        repeat(20) { m.tick(.1) }
        val before=m.tick(.1).speedKph
        assertTrue(m.select(N,before/3.6))
        val after=m.tick(.1)
        assertTrue(after.speedKph<before); assertTrue(after.speedKph>0); assertEquals(0,after.accelerator)
    }
    @Test fun steeringWorksWhileCoastingAndBrakeTakesPriority() {
        val m=ConsoleDriveModel(); m.select(D,0.0); m.press(ACCELERATE)
        repeat(20) { m.tick(.1) }
        m.release(ACCELERATE); m.press(RIGHT)
        val coasting=m.tick(.1)
        assertTrue(coasting.speedKph>0); assertTrue(coasting.steering>0)
        m.press(ACCELERATE); m.press(BRAKE)
        val braking=m.tick(.1)
        assertEquals(0,braking.accelerator); assertEquals(100,braking.brake)
    }
    @Test fun spaceStopsAndReleasedInputsDoNotRestartMotion() {
        val m=ConsoleDriveModel(); m.select(D,0.0); m.press(ACCELERATE)
        repeat(10) { m.tick(.1) }
        m.press(STOP); assertEquals(0.0,m.tick(.1).speedKph,0.0)
        m.clear(); assertEquals(0.0,m.tick(.1).speedKph,0.0)
    }
}
