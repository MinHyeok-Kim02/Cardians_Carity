package com.example.pleostest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 튜토리얼·시뮬레이션 공용 장면 판단 묶음 검증 */
class SceneAnalysisTest {
    @Test fun defaultAlleyPassesWithComfortableMargin() {
        val a = SceneAnalysis.of(null)
        assertEquals(CorridorPlanner.Outcome.CLEAR, a.plan.outcome)
        assertTrue("min=${a.plan.minClearance}", a.plan.minClearance >= 0.3)
        assertTrue(a.pathSpeech().contains("좌우로 번갈아"))
    }

    @Test fun oncomingPracticeYieldsToTheRight() {
        val a = SceneAnalysis.oncomingPractice()
        assertEquals(CorridorPlanner.Outcome.YIELD, a.plan.outcome)
        assertEquals(1, a.plan.yieldSide)
        assertEquals(AlleyDecisionEngine.Action.YIELD_RIGHT, a.decision.action)
        val lane = TutorialView.passLane(a, a.plan.points.last().x)!!
        // 대향차가 양보한 자차 왼쪽으로 지나갈 공간이 있어야 한다
        assertTrue(lane + 0.9 <= a.plan.points.last().x - 0.9 + 1e-9)
    }

    @Test fun freshWorldStartsAtStartWithSameObstacles() {
        val a = SceneAnalysis.of(null)
        val w = a.freshWorld()
        assertEquals(a.world.parkedCars, w.parkedCars)
        assertEquals(0.0, w.x, 1e-9); assertEquals(4.0, w.y, 1e-9)
    }

    @Test fun speechesAreTtsFriendly() {
        val dir = listOf(File("src/main/assets/label_samples"), File("app/src/main/assets/label_samples")).firstOrNull { it.isDirectory } ?: return
        val idx = MiniJson.arr(MiniJson.obj(MiniJson.parse(File(dir, "index.json").readText()))["samples"]).map { MiniJson.obj(it) }
        for (o in idx) {
            val scene = LabelSceneBuilder.build(LabelClip.parse(File(dir, MiniJson.str(o["file"])).readText()), MiniJson.num(o["t"]))
            val a = SceneAnalysis.of(scene)
            for (s in listOf(a.introSpeech(), a.pathSpeech(), a.doneSpeech(), a.decision.speech)) {
                assertFalse(s, s.contains("NaN") || s.contains("null"))
                assertTrue(s, s.length < 120)
            }
            if (a.plan.outcome == CorridorPlanner.Outcome.CLEAR)
                assertTrue("${a.title} min=${a.plan.minClearance}", a.plan.minClearance >= 0.3)
        }
    }
}
