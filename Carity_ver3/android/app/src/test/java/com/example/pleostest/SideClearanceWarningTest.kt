package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test
import com.example.pleostest.SideClearanceWarning.Level.*

class SideClearanceWarningTest {
    @Test fun retreatRetractsEverySectionAndReapproachExpandsAgainIndependently() {
        val model=SideClearanceWarning()
        var previous=1f
        for((index,distance) in listOf(.3,.5,.8,1.2,1.6,2.0).withIndex()) {
            val frame=model.update(distance,.2,index.toLong())
            assertTrue(frame.left.ambientStrength<=previous)
            assertEquals(1f,frame.right.ambientStrength,0f)
            previous=frame.left.ambientStrength
        }
        assertEquals(0f,previous,0f)
        val close=model.update(.2,2.0,100)
        assertEquals(1f,close.left.ambientStrength,0f)
        assertEquals(0f,close.right.ambientStrength,0f)
        assertEquals(0f,model.update(Double.NaN,2.0,101).left.ambientStrength,0f)
    }
    @Test fun ambientColorChangesWithinTheSameWarningLevel() {
        val a=SideClearanceWarning().update(.7,1.4,0)
        val b=SideClearanceWarning().update(.5,1.2,1)
        assertEquals(a.left.level,b.left.level)
        assertNotEquals(a.left.ambientArgb,b.left.ambientArgb)
        assertEquals(a.right.level,b.right.level)
        assertNotEquals(a.right.ambientArgb,b.right.ambientArgb)
        assertEquals(SideClearanceWarning.ambientColor(.7),a.left.ambientArgb)
        assertEquals(SideClearanceWarning.ambientColor(1.4),a.right.ambientArgb)
    }
    @Test fun continuousColorHasNoJumpAtStopsAndClampsInvalidInputs() {
        for(distance in listOf(.3,.8,1.04,1.6)) {
            val a=SideClearanceWarning.ambientColor(distance-.000001)
            val b=SideClearanceWarning.ambientColor(distance+.000001)
            for(shift in listOf(0,8,16))
                assertTrue(kotlin.math.abs(((a ushr shift) and 255)-((b ushr shift) and 255))<=1)
        }
        assertEquals(GREEN.argb,SideClearanceWarning.ambientColor(3.0))
        assertEquals(RED.argb,SideClearanceWarning.ambientColor(0.0))
        assertEquals(UNKNOWN.argb,SideClearanceWarning.ambientColor(Double.NaN))
        assertEquals(UNKNOWN.argb,SideClearanceWarning.ambientColor(-1.0))
    }
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
