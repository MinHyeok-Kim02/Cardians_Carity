package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test
import com.example.pleostest.AlleyKeyboardDriver.Control.*

class AlleyKeyboardDriverTest {
    @Test fun steeringRespondsOnFirstTickAndReversesWithoutLingering() {
        val input = AlleyKeyboardDriver()
        input.press(LEFT); input.tick(.05)
        assertEquals(-1.0, input.steering, 0.0)
        input.release(LEFT); input.press(RIGHT); input.tick(.05)
        assertEquals(1.0, input.steering, 0.0)
        input.release(RIGHT); input.tick(.05)
        assertEquals(0.0, input.steering, 0.0)
        assertEquals(0.0, input.speedMps, 0.0)
        input.press(LEFT); input.press(RIGHT); input.tick(.05)
        assertEquals(0.0, input.steering, 0.0)
    }

    @Test fun simultaneousAccelerationAndSteeringDriveSymmetricCurves() {
        val left=AlleyKeyboardDriver(); val right=AlleyKeyboardDriver()
        val a=AlleyWorld(emptyList()); val b=AlleyWorld(emptyList())
        left.press(ACCELERATE); left.press(LEFT)
        right.press(ACCELERATE); right.press(RIGHT)
        repeat(20) {
            left.tick(.1); right.tick(.1)
            a.advance(.1,left.speedMps,left.steering); b.advance(.1,right.speedMps,right.steering)
        }
        assertTrue(a.x<0); assertTrue(b.x>0)
        assertTrue(a.y>4); assertEquals(-a.x,b.x,.00001)
    }
    @Test fun steeringWithoutSpeedNeverTranslatesTheVehicle() {
        val input=AlleyKeyboardDriver(); val world=AlleyWorld(emptyList())
        input.press(LEFT)
        repeat(20) { input.tick(.1); world.advance(.1,input.speedMps,input.steering) }
        assertTrue(input.steering<0); assertEquals(4.0,world.y,0.0); assertEquals(0.0,world.x,0.0)
    }
    @Test fun repeatedKeyDownDoesNotMultiplyAccelerationAndSpeedIsCapped() {
        val a=AlleyKeyboardDriver(); val b=AlleyKeyboardDriver()
        a.press(ACCELERATE); b.press(ACCELERATE)
        repeat(100) { a.press(ACCELERATE); a.tick(.1); b.tick(.1) }
        assertEquals(b.speedMps,a.speedMps,0.0)
        assertEquals(6.0/3.6,a.speedMps,.00001)
    }
    @Test fun releasingKeysCoastsAndCentersSteering() {
        val driver=AlleyKeyboardDriver()
        driver.press(ACCELERATE); driver.press(RIGHT)
        repeat(15) { driver.tick(.1) }
        val speed=driver.speedMps
        driver.release(ACCELERATE); driver.release(RIGHT)
        driver.tick(.1)
        assertTrue(driver.speedMps in 0.0..speed); assertTrue(driver.speedMps>0)
        repeat(60) { driver.tick(.1) }
        assertEquals(0.0,driver.speedMps,.00001); assertEquals(0.0,driver.steering,.00001)
    }
    @Test fun brakeWinsOverAccelerationAndSpaceStopsImmediately() {
        val driver=AlleyKeyboardDriver(); driver.press(ACCELERATE)
        repeat(20) { driver.tick(.1) }
        driver.press(BRAKE); repeat(10) { driver.tick(.1) }
        assertEquals(0.0,driver.speedMps,0.0); assertTrue(driver.brake)
        driver.release(BRAKE); repeat(10) { driver.tick(.1) }
        driver.press(STOP)
        assertEquals(0.0,driver.speedMps,0.0); assertTrue(driver.brake)
    }
    @Test fun focusLossClearRemovesAllHeldInputsAndMotion() {
        val driver=AlleyKeyboardDriver(); driver.press(ACCELERATE); driver.press(LEFT)
        repeat(10) { driver.tick(.1) }
        driver.clear(); repeat(20) { driver.tick(.1) }
        assertEquals(0.0,driver.speedMps,0.0); assertEquals(0.0,driver.steering,0.0)
        assertFalse(driver.brake)
    }
    @Test fun macroHoldKeepsBrakeButRemovesThrottleAndTurning() {
        val driver=AlleyKeyboardDriver()
        driver.press(ACCELERATE); driver.press(RIGHT); driver.press(BRAKE)
        driver.holdForMacro(); driver.tick(.1)
        assertEquals(0.0,driver.speedMps,0.0); assertEquals(0.0,driver.steering,0.0); assertTrue(driver.brake)
    }
}
