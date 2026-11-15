package com.example.pleostest

import org.junit.Assert.*
import org.junit.Test

/** 경로 계획 · 규칙 엔진 · AI 응답 처리 검증 */
class AlleyDecisionTest {
    private fun car(x: Double, y: Double, label: String = "주차 차량") = AlleyWorld.ParkedCar(x, y, 1.8, 4.4, label)

    @Test fun emptyRoadPlanGoesStraightToTheEnd() {
        val world = AlleyWorld(emptyList(), emptyList(), -3.0, 3.0, 40.0)
        val plan = CorridorPlanner.plan(world)
        assertEquals(CorridorPlanner.Outcome.CLEAR, plan.outcome)
        assertTrue(plan.points.all { kotlin.math.abs(it.x) < 1e-9 })
        assertEquals(3.0 - .9, plan.minLeft, 1e-6)
    }

    @Test fun planShiftsAwayFromParkedCarWithinSteeringLimit() {
        // 오른쪽 차가 차로 안쪽까지 들어와 있다: 왼쪽으로 비켜야 함
        val world = AlleyWorld(listOf(car(1.6, 20.0)), emptyList(), -3.0, 3.0, 40.0)
        val plan = CorridorPlanner.plan(world)
        assertEquals(CorridorPlanner.Outcome.CLEAR, plan.outcome)
        val beside = plan.points.first { kotlin.math.abs(it.y - 20.0) < .26 }
        assertTrue("비켜야 함 x=${beside.x}", beside.x < 0)
        assertTrue(beside.right >= CorridorPlanner.MARGIN - 1e-6)
        plan.points.zipWithNext().forEach { (a, b) ->
            assertTrue(kotlin.math.abs(b.x - a.x) <= CorridorPlanner.MAX_SLOPE * CorridorPlanner.STEP + 1e-6)
        }
        assertTrue(plan.points.all { it.min > 0 })
    }

    @Test fun blockedCorridorStopsBeforeObstacle() {
        val world = AlleyWorld(listOf(car(-1.2, 20.0), car(1.2, 20.0)), emptyList(), -2.8, 2.8, 40.0)
        val plan = CorridorPlanner.plan(world)
        assertEquals(CorridorPlanner.Outcome.BLOCKED, plan.outcome)
        assertTrue(plan.stopY!! < 20.0 - 2.2 - 2.1)
    }

    @Test fun oncomingCarInNarrowRoadLeadsToRightYield() {
        // 폭 5.6m, 정면 대향차(가운데), 오른쪽 앞쪽에 빈 공간 → 오른쪽 정차 양보
        val oncoming = car(-.3, 24.0, "정면 차량")
        val world = AlleyWorld(listOf(oncoming), emptyList(), -2.8, 2.8, 40.0)
        val plan = CorridorPlanner.plan(world, listOf(oncoming))
        assertEquals(CorridorPlanner.Outcome.YIELD, plan.outcome)
        assertEquals(1, plan.yieldSide)
        val last = plan.points.last()
        assertTrue("오른쪽으로 붙어야 함 x=${last.x}", last.x > .5)
        // 대향차가 지나갈 공간: 자차 왼쪽 모서리와 왼쪽 벽 사이에 대향차 폭 + 양쪽 여유가 남는다
        assertTrue((last.x - .9) - world.leftWallX >= 1.8 + 2 * CorridorPlanner.PASS_MARGIN - 1e-6)
        assertTrue(last.right >= 0.0 && last.min > 0.0)
        val d = AlleyDecisionEngine.decide(world, world.clearance(), 1.5, plan,
            LabelScene(LabelClip("", "t", 1280, 720, 10.0, 1.0, "", "", emptyList()), 0.0, emptyList(),
                world.parkedCars, -2.8, 2.8, 40.0, listOf(oncoming)))
        assertEquals(AlleyDecisionEngine.Action.YIELD_RIGHT, d.action)
        assertTrue(d.speech.contains("오른쪽"))
    }

    @Test fun wideRoadLetsEgoPassBesideOncomingCar() {
        val oncoming = car(-1.6, 24.0, "정면 차량")
        val world = AlleyWorld(listOf(oncoming), emptyList(), -3.6, 3.6, 40.0)
        val plan = CorridorPlanner.plan(world, listOf(oncoming))
        assertEquals(CorridorPlanner.Outcome.CLEAR, plan.outcome)
    }

    @Test fun nearLeftObstacleStopsWithSteeringAdvice() {
        val world = AlleyWorld(listOf(AlleyWorld.ParkedCar(-1.25, 4.0, .2, .5)))
        val d = AlleyDecisionEngine.decide(world, world.clearance(), 1.0, null)
        assertEquals(AlleyDecisionEngine.Action.STOP, d.action)
        assertEquals(3, d.severity)
        assertTrue(d.speech.contains("왼쪽") && d.speech.contains("오른쪽"))
    }

    @Test fun cautionSideKeepsAwayAndSpeaksCentimeters() {
        val world = AlleyWorld(listOf(AlleyWorld.ParkedCar(-1.85, 4.0, .2, 3.0)), emptyList(), -3.5, 3.5)
        val gap = world.clearance()
        assertEquals(.85, gap.leftMeters, 1e-6)
        world.restore(-.35, 4.0, 0.0, false)
        val g2 = world.clearance()
        assertEquals(.5, g2.leftMeters, 1e-6)
        val d = AlleyDecisionEngine.decide(world, g2, 1.0, null)
        assertEquals(AlleyDecisionEngine.Action.KEEP_RIGHT, d.action)
        assertTrue(d.speech, d.speech.contains("50센티미터"))
    }

    @Test fun fastInNarrowRoadAsksToSlowDown() {
        val world = AlleyWorld(emptyList(), emptyList(), -2.0, 2.0)
        val d = AlleyDecisionEngine.decide(world, world.clearance(), 15 / 3.6, null)
        assertEquals(AlleyDecisionEngine.Action.SLOW, d.action)
    }

    @Test fun spokenDistanceIsReadable() {
        assertEquals("30센티미터", AlleyDecisionEngine.spoken(.31))
        assertEquals("1.2미터", AlleyDecisionEngine.spoken(1.24))
    }

    @Test fun aiReplyParsingAndSafetyMerge() {
        val reply = "<think>생각</think>```json\n{\"action\":\"keep_right\",\"speech\":\"왼쪽에 가깝습니다 (40cm). 오른쪽으로!\",\"reason\":\"좌측 근접\"}\n```"
        val parsed = AiJudge.parseReply(reply)!!
        assertEquals(AlleyDecisionEngine.Action.KEEP_RIGHT, parsed.first)
        assertFalse(parsed.second.contains("("))
        assertNull(AiJudge.parseReply("모르겠습니다"))

        val world = AlleyWorld(listOf(AlleyWorld.ParkedCar(-1.25, 4.0, .2, .5)))
        val rule = AlleyDecisionEngine.decide(world, world.clearance(), 1.0, null)   // STOP sev3
        val ai = AiJudge.Result("Claude", true, AlleyDecisionEngine.Action.PROCEED, "괜찮습니다 진행하세요", "", 10)
        val merged = AiJudge.merge(rule, ai)
        assertEquals(AlleyDecisionEngine.Action.STOP, merged.action)
        assertEquals(rule.speech, merged.speech)

        val calm = AlleyDecisionEngine.decide(AlleyWorld(emptyList()), AlleyWorld(emptyList()).clearance(), 1.0, null)
        val aiSlow = AiJudge.Result("Qwen", true, AlleyDecisionEngine.Action.SLOW, "천천히 가세요", "", 10)
        assertEquals("천천히 가세요", AiJudge.merge(calm, aiSlow).speech)
        val failed = AiJudge.Result("Qwen", false, null, null, null, 10, "timeout")
        assertEquals(calm.speech, AiJudge.merge(calm, failed).speech)
        // 프롬프트에 측정값과 규칙이 들어간다
        val prompt = AiJudge.userPrompt(rule)
        assertTrue(prompt.contains("\"left_m\"") && prompt.contains("\"STOP\""))
        MiniJson.parse(prompt)   // 유효한 JSON
    }
}
