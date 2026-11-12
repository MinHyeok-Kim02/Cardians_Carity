package com.example.pleostest

import org.junit.Assert.assertEquals
import org.junit.Test

class AlleyPassEngineTest {
    @Test fun scenarioOffDoesNotWarn() {
        assertEquals(AlleyPassEngine.Level.OFF, AlleyPassEngine.evaluate(false, 0, 0, null).level)
    }
    @Test fun proximityBoundaryAndSide() {
        val result = AlleyPassEngine.evaluate(true, 70, 30, null)
        assertEquals(AlleyPassEngine.Level.STOP, result.level)
        assertEquals("오른쪽 근접 · 정차 후 확인", result.title)
    }
    @Test fun cautionBoundary() {
        assertEquals(AlleyPassEngine.Level.CAUTION, AlleyPassEngine.evaluate(true, 60, 90, 0f).level)
        assertEquals(AlleyPassEngine.Level.INFO, AlleyPassEngine.evaluate(true, 61, 90, 0f).level)
    }
    @Test fun unknownSpeedDoesNotBecomeStopped() {
        assertEquals("차량 속도 수신 대기", AlleyPassEngine.evaluate(true, 120, 120, null).title)
        assertEquals("차량 속도 수신 대기", AlleyPassEngine.evaluate(true, 120, 120, Float.NaN).title)
    }
    @Test fun reverseSpeedAlsoTriggersSpeedGuidance() {
        assertEquals("속도를 낮추고 주변 확인", AlleyPassEngine.evaluate(true, 120, 120, -5f).title)
    }
}
