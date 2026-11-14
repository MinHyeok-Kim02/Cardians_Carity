package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test

class AlleyWorldTest {
    @Test fun zeroSpeedDoesNotMoveEvenWhenSteering() {
        val world = AlleyWorld()
        repeat(20) { world.advance(.1, 0.0, 1.0) }
        assertEquals(0.0, world.x, .0001)
        assertEquals(4.0, world.y, .0001)
    }
    @Test fun signedSpeedControlsForwardAndReverseMotion() {
        val forward = AlleyWorld()
        val reverse = AlleyWorld()
        repeat(10) { forward.advance(.1, 2.0, 0.0); reverse.advance(.1, -2.0, 0.0) }
        assertEquals(6.0, forward.y, .001)
        assertEquals(2.0, reverse.y, .001)
    }
    @Test fun steeringChangesPositionInBothDirections() {
        val left = AlleyWorld(); val right = AlleyWorld()
        repeat(15) { left.advance(.1, 1.0, -.8); right.advance(.1, 1.0, .8) }
        assertTrue(left.x < 0)
        assertTrue(right.x > 0)
        assertEquals(-left.x, right.x, .001)
    }
    @Test fun parkedVehicleDistanceFallsAsWeDriveTowardIt() {
        val world = AlleyWorld()
        val before = world.clearance().meters
        repeat(50) { world.advance(.1, 2.0, 0.0) }
        val after = world.clearance()
        assertTrue(after.meters < before)
        assertEquals("왼쪽 주차 차량", after.side)
    }
    @Test fun contactStopsMotionUntilReset() {
        val world = AlleyWorld()
        world.restore(-.8, 14.0, 0.0, false)
        world.advance(.1, 1.0, 0.0)
        assertTrue(world.contact)
        val y = world.y
        world.advance(.1, 10.0, 1.0)
        assertEquals(y, world.y, 0.0)
        world.reset()
        assertFalse(world.contact)
        assertEquals(4.0, world.y, 0.0)
    }
    @Test fun invalidInputsCannotPoisonPose() {
        val world = AlleyWorld()
        world.advance(Double.NaN, 1.0, 0.0)
        world.advance(.1, Double.NaN, 0.0)
        assertEquals(4.0, world.y, 0.0)
    }
}
