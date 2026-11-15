package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test

class PleosSteeringMappingTest {
    @Test fun sdkPositiveLeftAndNegativeRightProduceMatchingForwardCurves() {
        val left = AlleyWorld(emptyList())
        val right = AlleyWorld(emptyList())
        val leftInput = PleosSteeringMapping.toSimulation(250f, 500f)!!.toDouble()
        val rightInput = PleosSteeringMapping.toSimulation(-250f, 500f)!!.toDouble()
        repeat(10) {
            left.advance(.1, 1.0, leftInput)
            right.advance(.1, 1.0, rightInput)
        }
        assertTrue(left.x < 0); assertTrue(right.x > 0)
        assertTrue(left.y > 4); assertTrue(right.y > 4)
        assertEquals(-left.x, right.x, .000001)
    }
    @Test fun invalidReadbackNeverBecomesAUsableSteeringInput() {
        assertNull(PleosSteeringMapping.toSimulation(null, 500f))
        assertNull(PleosSteeringMapping.toSimulation(Float.NaN, 500f))
        assertNull(PleosSteeringMapping.toSimulation(Float.POSITIVE_INFINITY, 500f))
        assertNull(PleosSteeringMapping.toSimulation(100f, 0f))
        assertNull(PleosSteeringMapping.toSimulation(100f, Float.NaN))
        assertEquals(0f, PleosSteeringMapping.toSimulation(0f, 500f)!!, 0f)
        assertEquals(-1f, PleosSteeringMapping.toSimulation(600f, 500f)!!, 0f)
    }
}
