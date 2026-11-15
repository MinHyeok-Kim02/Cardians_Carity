package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test

/** Longitudinal centerline posts near the end, with a traversable lane on both sides. */
class EndBollardsTest {
    @Test fun fivePostsFollowCenterlineWithFourMeterSpacing() {
        val world=AlleyWorld()
        assertEquals(5,world.safetyBollards.size)
        world.safetyBollards.forEachIndexed { i,post ->
            assertEquals(0.0,post.x,0.0)
            assertEquals(76.5+i*4.0,post.y,0.0)
        }
        assertEquals(5,world.obstacles().count { it.label=="안전봉" })
        assertTrue(AlleyWorld.END_STOP_Y>world.safetyBollards.last().y+2.1+1.6)
    }
    @Test fun rightLaneDetectsPostsOnLeftAndLeftLaneDetectsPostsOnRight() {
        val world=AlleyWorld(emptyList())
        val warning=SideClearanceWarning()
        world.restore(1.4,80.5,0.0,false)
        var gap=world.clearance()
        assertEquals(.275,gap.leftMeters,.00001)
        assertEquals(1.2,gap.rightMeters,.00001)
        assertEquals("왼쪽 안전봉",gap.side)
        var colors=warning.update(gap.leftMeters,gap.rightMeters,0)
        assertEquals(SideClearanceWarning.Level.RED,colors.left.level)
        assertEquals(SideClearanceWarning.Level.GREEN,colors.right.level)
        world.restore(-1.4,80.5,0.0,false)
        gap=world.clearance(); colors=warning.update(gap.leftMeters,gap.rightMeters,100)
        assertEquals(.275,gap.rightMeters,.00001)
        assertEquals("오른쪽 안전봉",gap.side)
        assertEquals(SideClearanceWarning.Level.GREEN,colors.left.level)
        assertEquals(SideClearanceWarning.Level.RED,colors.right.level)
    }
    @Test fun carCanPassAllFivePostsWithoutOldEndStopOrContact() {
        for(x in listOf(-1.4,1.4)) {
            val world=AlleyWorld()
            world.restore(x,72.0,0.0,false)
            repeat(240) {
                assertFalse(world.shouldStopAtEnd(1.0))
                world.advance(.1,1.0,0.0)
            }
            assertTrue(world.y>world.safetyBollards.last().y+2.1)
            assertFalse(world.contact)
            assertEquals(SideClearanceWarning.Level.GREEN,SideClearanceWarning.level(world.clearance().leftMeters))
            assertEquals(SideClearanceWarning.Level.GREEN,SideClearanceWarning.level(world.clearance().rightMeters))
        }
    }
    @Test fun afterPassingAndReturningToCenterBothLightsReturnToBaseGreen() {
        val world=AlleyWorld()
        world.restore(0.0,97.0,0.0,false)
        val gap=world.clearance()
        val warning=SideClearanceWarning().update(gap.leftMeters,gap.rightMeters,0)
        assertEquals(SideClearanceWarning.Level.GREEN,warning.left.level)
        assertEquals(SideClearanceWarning.Level.GREEN,warning.right.level)
        assertEquals(0f,warning.left.ambientStrength,0f)
        assertEquals(0f,warning.right.ambientStrength,0f)
        assertFalse(world.shouldStopAtEnd(-1.0))
    }
    @Test fun drivingIntoCenterlinePostStillRegistersCollision() {
        val world=AlleyWorld()
        world.restore(0.0,73.5,0.0,false)
        repeat(20) { world.advance(.1,2.0,0.0) }
        assertTrue(world.contact)
        assertTrue(world.y<world.safetyBollards.first().y)
    }
    @Test fun originalStartParkedCarsAndDemoRemainUnchanged() {
        val world=AlleyWorld()
        assertEquals(6,world.parkedCars.size)
        assertEquals(2.6,world.clearance().leftMeters,.0001)
        world.restore(0.0,11.0,0.0,false)
        assertEquals(.7,world.clearance().leftMeters,.0001)
    }
}
