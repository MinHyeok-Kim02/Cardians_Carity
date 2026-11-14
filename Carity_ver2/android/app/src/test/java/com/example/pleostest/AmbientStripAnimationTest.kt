package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test

class AmbientStripAnimationTest {
    @Test fun wholeStripStartsGreenAndWarningColorBeginsAtDoor() {
        val green=SideClearanceWarning.Level.GREEN.argb
        val red=SideClearanceWarning.Level.RED.argb
        for(i in 0 until 16) assertEquals(green,AmbientStripAnimation.sectionColor(0f,i,green,red))
        assertEquals(green,AmbientStripAnimation.sectionColor(.5f/16,0,green,red))
        val door=AmbientStripAnimation.sectionColor(.5f/16,15,green,red)
        assertNotEquals(green,door); assertNotEquals(red,door)
        for(i in 0 until 16) assertEquals(red,AmbientStripAnimation.sectionColor(1f,i,green,red))
    }
    @Test fun retreatReturnsCenterToGreenBeforeDoorWithoutTurningOff() {
        val green=SideClearanceWarning.Level.GREEN.argb
        val orange=SideClearanceWarning.Level.ORANGE.argb
        assertEquals(green,AmbientStripAnimation.sectionColor(14f/16,0,green,orange))
        assertEquals(orange,AmbientStripAnimation.sectionColor(14f/16,15,green,orange))
        for(i in 0 until 16) {
            val color=AmbientStripAnimation.sectionColor(0f,i,green,orange)
            assertEquals(green,color); assertEquals(255,color ushr 24)
        }
    }
    @Test fun expandsAndFullyRetractsWithIndependentSides() {
        val a=AmbientStripAnimation(); a.resume(100)
        for(i in 1..30) a.advance(100+i*64L,1f,0f)
        assertEquals(1f,a.leftProgress,.0001f); assertEquals(0f,a.rightProgress,0f)
        assertFalse(a.needsFrame(1f,0f))
        for(i in 31..60) a.advance(100+i*64L,0f,1f)
        assertEquals(0f,a.leftProgress,.0001f); assertEquals(1f,a.rightProgress,.0001f)
        assertFalse(a.needsFrame(0f,1f))
    }
    @Test fun successiveSectionsAppearLocallyInsteadOfFadingWholeStrip() {
        assertEquals(.5f,AmbientStripAnimation.sectionOpacity(.5f/16,0),.0001f)
        assertEquals(0f,AmbientStripAnimation.sectionOpacity(.5f/16,1),0f)
        assertEquals(1f,AmbientStripAnimation.sectionOpacity(4.5f/16,3),0f)
        assertEquals(.5f,AmbientStripAnimation.sectionOpacity(4.5f/16,4),.0001f)
        assertEquals(0f,AmbientStripAnimation.sectionOpacity(4.5f/16,5),0f)
    }
    @Test fun retreatRemovesOuterSectionsFirst() {
        assertEquals(1f,AmbientStripAnimation.sectionOpacity(1f,15),0f)
        assertEquals(0f,AmbientStripAnimation.sectionOpacity(14f/16,15),0f)
        assertEquals(1f,AmbientStripAnimation.sectionOpacity(14f/16,0),0f)
    }
    @Test fun resumeDoesNotCountTimeOffScreen() {
        val a=AmbientStripAnimation(); a.resume(100); a.advance(132,1f,0f)
        val before=a.leftProgress
        a.resume(100000); a.advance(100016,1f,0f)
        assertEquals(before+16f/1800,a.leftProgress,.0001f)
    }
    @Test fun longOrReversedClockDoesNotJumpOrRewindProgress() {
        val a=AmbientStripAnimation(); a.resume(100)
        a.advance(100000,1f,0f)
        assertEquals(64f/1800,a.leftProgress,.0001f)
        val before=a.leftProgress; a.advance(99999,1f,0f)
        assertEquals(before,a.leftProgress,0f)
    }
    @Test fun invalidTargetsRetractAndOpacityIsBounded() {
        val a=AmbientStripAnimation(); a.resume(100)
        for(i in 1..30) a.advance(100+i*64L,5f,1f)
        for(i in 31..60) a.advance(100+i*64L,Float.NaN,-1f)
        assertEquals(0f,a.leftProgress,.0001f); assertEquals(0f,a.rightProgress,.0001f)
        assertEquals(0f,AmbientStripAnimation.sectionOpacity(Float.NaN,0),0f)
        assertEquals(1f,AmbientStripAnimation.sectionOpacity(3f,15),0f)
    }
}
