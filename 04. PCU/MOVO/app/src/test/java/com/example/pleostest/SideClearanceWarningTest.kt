package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test
import com.example.pleostest.SideClearanceWarning.Level.*

class SideClearanceWarningTest {
    @Test fun thresholdsAndInvalidSignals() {
        assertEquals(GREEN, SideClearanceWarning.level(.80001))
        assertEquals(ORANGE, SideClearanceWarning.level(.8))
        assertEquals(ORANGE, SideClearanceWarning.level(.30001))
        assertEquals(RED, SideClearanceWarning.level(.3))
        assertEquals(RED, SideClearanceWarning.level(0.0))
        assertEquals(UNKNOWN, SideClearanceWarning.level(Double.NaN))
        assertEquals(UNKNOWN, SideClearanceWarning.level(-1.0))
    }
    @Test fun independentSidesBlinkThreeFullHalfSecondCycles() {
        val model = SideClearanceWarning()
        for (time in 0L..1750L step 250) {
            val frame = model.update(.2, 1.2, time)
            assertEquals(RED, frame.left.level)
            assertEquals(time >= 1500 || time % 500 == 0L, frame.left.visible)
            assertEquals(GREEN, frame.right.level)
            assertTrue(frame.right.visible)
        }
        assertTrue(model.update(.1, 1.0, 10250).left.visible) // deeper in red never restarts.
    }
    @Test fun exitAndReentryRestartOnlyTheAffectedSide() {
        val model = SideClearanceWarning()
        model.update(.2,.2,0)
        assertFalse(model.update(.2,.2,250).left.visible)
        model.update(.5,.2,300)
        assertTrue(model.update(.2,.2,400).left.visible)
        assertFalse(model.update(.2,.2,650).left.visible)
        assertTrue(model.update(.2,.2,650).right.visible) // original right cycle remains.
    }
    @Test fun mixedAndCenterAreasUseWorstSeverity() {
        assertEquals(GREEN, SideClearanceWarning.route(listOf("ROW_1_LEFT"),GREEN,RED))
        assertEquals(RED, SideClearanceWarning.route(listOf("ROW_1_RIGHT"),GREEN,RED))
        assertEquals(RED, SideClearanceWarning.route(listOf("ROW_1_LEFT","ROW_1_RIGHT"),GREEN,RED))
        assertEquals(ORANGE, SideClearanceWarning.route(listOf("ROW_1_CENTER"),ORANGE,GREEN))
        assertEquals(UNKNOWN, SideClearanceWarning.route(listOf("ROW_1_CENTER"),UNKNOWN,RED))
    }
    @Test fun warningReadsExistingWorldSideClearance() {
        val world = AlleyWorld(emptyList())
        val model = SideClearanceWarning()
        world.restore(2.4,4.0,0.0,false)
        val gap = world.clearance()
        val frame = model.update(gap.leftMeters,gap.rightMeters,0)
        assertEquals(GREEN,frame.left.level)
        assertEquals(RED,frame.right.level)
        // Vehicle/world geometry is untouched by warning rendering or routing.
        assertEquals(2.4,world.x,0.0)
        assertEquals(gap.rightMeters,world.clearance().rightMeters,0.0)
    }
}
