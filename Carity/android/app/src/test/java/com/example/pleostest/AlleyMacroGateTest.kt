package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test

class AlleyMacroGateTest {
    private fun press(gate: AlleyMacroGate, at: Long, speed: Double = 1.0, gap: Double = .7) {
        gate.observe(at - 1, speed, false, gap, true)
        gate.observe(at, speed, true, gap, true)
    }
    private fun armedGate(): AlleyMacroGate = AlleyMacroGate().apply {
        press(this,1000); press(this,3000); press(this,5000)
    }

    @Test fun verifiedParkAllowsConfirmationAfterFootBrakeRelease() {
        val gate=armedGate()
        assertFalse(gate.observe(6000,0.0,false,.7,false,false).canConfirm)
        assertTrue(gate.observe(6100,0.0,false,.7,true,true).canConfirm)
        assertTrue(gate.consume(6200,0.0,false,.7,true,true))
    }

    @Test fun heldBrakeAndInitialPressedDoNotCountAsRepeatedPresses() {
        val gate=AlleyMacroGate()
        repeat(50) { gate.observe(it.toLong(),1.0,true,.7,true) }
        assertEquals(0,gate.observe(60,1.0,true,.7,true).presses)
        press(gate,100)
        repeat(50) { gate.observe(101L+it,1.0,true,.7,true) }
        assertEquals(1,gate.observe(200,1.0,true,.7,true).presses)
    }

    @Test fun threeLowSpeedPressesAndProximityArmButMovingBlocksConfirmation() {
        val gate=armedGate()
        val moving=gate.observe(6000,1.0,true,.7,true)
        assertTrue(moving.armed); assertFalse(moving.canConfirm)
        assertTrue(gate.observe(6100,0.0,true,.7,true).canConfirm)
    }

    @Test fun oldPressesAndDistantObstaclesDoNotArm() {
        val gate=AlleyMacroGate()
        press(gate,1000); press(gate,3000); press(gate,19000)
        assertFalse(gate.observe(20000,0.0,true,.7,true).armed)
        gate.reset()
        press(gate,1000,gap=3.0); press(gate,3000,gap=3.0); press(gate,5000,gap=3.0)
        assertFalse(gate.observe(6000,0.0,true,3.0,true).armed)
    }

    @Test fun missingInputsOverspeedAndReverseInvalidateDetection() {
        for(invalidate in listOf<(AlleyMacroGate)->Unit>(
            { it.observe(6000,null,true,.7,true) },
            { it.observe(6000,0.0,null,.7,true) },
            { it.observe(6000,0.0,true,Double.NaN,true) },
            { it.observe(6000,4.0,true,.7,true) },
            { it.observe(6000,-1.0,true,.7,true) }
        )) {
            val gate=armedGate(); invalidate(gate)
            assertFalse(gate.observe(6100,0.0,true,.7,true).canConfirm)
        }
    }

    @Test fun restrictionBrakeReleaseAndExpiryBlockConfirmation() {
        val gate=armedGate()
        assertFalse(gate.observe(6000,0.0,true,.7,false).canConfirm)
        assertFalse(gate.observe(6100,0.0,false,.7,true).canConfirm)
        assertFalse(gate.observe(36001,0.0,true,.7,true).canConfirm)
    }

    @Test fun confirmationRechecksCurrentInputsAndConsumesOnlyOnce() {
        val gate=armedGate()
        assertTrue(gate.observe(6000,0.0,true,.7,true).canConfirm)
        assertFalse(gate.consume(6100,1.0,true,.7,true))
        assertTrue(gate.consume(6200,0.0,true,.7,true))
        assertFalse(gate.consume(6300,0.0,true,.7,true))
    }

    @Test fun fullButtonDemoEndsStoppedWithMacroReadyWithoutSyntheticCollision() {
        val world=AlleyWorld()
        world.restore(0.0,11.0,0.0,false)
        val gate=AlleyMacroGate()
        var result=AlleyMacroGate.State(0,false,false,false)
        for(i in 0..100) {
            val input=AlleyDemoScenario.sample(i/10.0)
            if(world.simulationSeconds>0 || input.speedMps>.01)
                world.advance(.1,input.speedMps,input.steering)
            result=gate.observe(i*100L,input.speedMps,input.brake,world.clearance().meters,true)
        }
        assertFalse(world.contact)
        assertEquals(3,result.presses)
        assertTrue(result.canConfirm)
        assertEquals(0.0,AlleyDemoScenario.sample(10.0).speedMps,0.0)
    }
}
