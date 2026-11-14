package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test
import com.example.pleostest.ConsoleDriveModel.Gear
import com.example.pleostest.AlleyKeyboardDriver.Control

class VirtualKeyboardDriveTest {
    @Test fun reverseAcceleratorMovesBackwardWithoutChangingSelection() {
        val driver=VirtualKeyboardDrive(); driver.reset()
        assertTrue(driver.select(Gear.R))
        driver.press(Control.ACCELERATE)
        val world=AlleyWorld()
        repeat(10) { driver.advance(.1); world.advance(.1,driver.input().speedMps,driver.input().steering) }
        assertTrue(world.y<4.0); assertEquals(Gear.R,driver.gear)
    }
    @Test fun stopThenSelectDriveMovesForwardAndBlocksMovingDirectionChange() {
        val driver=VirtualKeyboardDrive(); driver.select(Gear.R); driver.press(Control.ACCELERATE)
        driver.advance(.1); assertFalse(driver.select(Gear.D))
        driver.stop(); assertEquals(0.0,driver.input().speedMps,0.0)
        assertTrue(driver.select(Gear.D)); driver.press(Control.ACCELERATE); driver.advance(.1)
        assertTrue(driver.input().speedMps>0)
    }
    @Test fun resetClearsHeldKeysAndReturnsVirtualPark() {
        val driver=VirtualKeyboardDrive(); driver.select(Gear.R); driver.press(Control.ACCELERATE)
        driver.advance(.1); driver.reset(); driver.advance(.1)
        assertEquals(Gear.P,driver.gear); assertEquals(0.0,driver.input().speedMps,0.0)
    }
}
