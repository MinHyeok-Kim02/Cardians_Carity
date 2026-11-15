package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class AlleySurfaceTest {
    @Test fun smallObstacleBesideMiddleOfLeftSideIsDetectedBetweenCorners() {
        val obstacle = AlleyWorld.ParkedCar(-1.25, 4.0, .2, .5)
        val world = AlleyWorld(listOf(obstacle))
        val gap = world.clearance()
        assertEquals(.25, gap.leftMeters, .00001)
        assertEquals(.25, gap.meters, .00001)
        // Every ego corner is over 1m away; a corner-to-corner check would miss this.
        assertTrue(world.corners().all { p ->
            AlleyWorld.rectangle(obstacle).all { q -> hypot(p.x-q.x,p.y-q.y) > 1 }
        })
        assertTrue(gap.side.startsWith("왼쪽"))
        assertEquals(AlleyWorld.Advice.STOP, world.guidance(1.0).advice)
    }

    @Test fun smallObstacleBesideMiddleOfRightSideIsDetected() {
        val world = AlleyWorld(listOf(AlleyWorld.ParkedCar(1.25, 4.0, .2, .5)))
        assertEquals(.25, world.clearance().rightMeters, .00001)
    }

    @Test fun rotatedSideAndCrossingSegmentsAreMeasured() {
        val a=AlleyWorld.Point(-1.0,-1.0); val b=AlleyWorld.Point(1.0,1.0)
        val c=AlleyWorld.Point(-1.0,1.0); val d=AlleyWorld.Point(1.0,-1.0)
        assertEquals(0.0, AlleyWorld.segmentPairDistance(a,b,c,d), 0.0)
        val world = AlleyWorld(emptyList())
        world.restore(0.0,4.0,Math.PI/4,false)
        val expected = 3.5 - 3.0 / kotlin.math.sqrt(2.0)
        assertEquals(expected, world.clearance().meters, .00001)
    }

    @Test fun obstaclesOnBothSidesDoNotResultInBlindOppositeSteering() {
        val world=AlleyWorld(listOf(
            AlleyWorld.ParkedCar(-1.25,4.0,.2,1.0),
            AlleyWorld.ParkedCar(1.25,4.0,.2,1.0)))
        assertEquals(AlleyWorld.Advice.STOP, world.guidance(1.0).advice)
    }



    @Test fun guidanceDoesNotMutateLivePoseOrClock() {
        val world=AlleyWorld()
        val before=world.clearance()
        world.guidance(1.1)
        assertEquals(4.0,world.y,0.0)
        assertEquals(0.0,world.simulationSeconds,0.0)
        assertEquals(before.meters,world.clearance().meters,0.0)
    }
}
