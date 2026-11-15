package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test

class AlleyMacroPresentationTest {
    @Test fun eachStageHasAnExplicitSimulationTitleAndProgress() {
        for(stage in 1..3) {
            val card=AlleyMacroPresentation.card(stage,.7,false,false,true)!!
            assertTrue(card.title.contains("시뮬레이션"))
            assertTrue(card.progress.startsWith("$stage / 3"))
            assertFalse(card.urgent)
        }
    }
    @Test fun criticalAndNewApproachWarningsOverrideEvenCompletion() {
        for(stage in 0..4) {
            assertTrue(AlleyMacroPresentation.card(stage,.2,false,false,true)!!.urgent)
            assertTrue(AlleyMacroPresentation.card(stage,.7,true,false,true)!!.urgent)
            assertTrue(AlleyMacroPresentation.card(stage,1.0,false,true,true)!!.urgent)
        }
    }
    @Test fun idleHasNoAnnouncementAndHazardDistinguishesOnOff() {
        assertNull(AlleyMacroPresentation.card(-1,2.0,false,false,false))
        assertTrue(AlleyMacroPresentation.card(3,.7,false,false,true)!!.detail.contains("ON"))
        assertTrue(AlleyMacroPresentation.card(3,.7,false,false,false)!!.detail.contains("OFF"))
    }
}
