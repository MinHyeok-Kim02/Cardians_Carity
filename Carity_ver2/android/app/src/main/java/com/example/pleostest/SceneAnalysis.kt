package com.example.pleostest

import java.util.Locale
import kotlin.math.abs

/**
 * 장면 하나의 판단 묶음: 장애물 배치(라벨 또는 기본 골목) → 경로 계획 → 거리 검증 → 출발 시점 규칙 판단.
 * 시뮬레이션 화면의 [라벨 장애물·AI 판단] 탭과 튜토리얼 앱이 같은 결과를 씁니다.
 */
class SceneAnalysis private constructor(
    private val customTitle: String?,
    private val customFrontal: List<AlleyWorld.ParkedCar>,
    val scene: LabelScene?,
    val world: AlleyWorld,
    val plan: CorridorPlanner.Plan,
    val report: LabelDistanceCheck.Report?,
    val decision: AlleyDecisionEngine.Decision,
) {
    val title get() = customTitle ?: scene?.title ?: "기본 골목 · 양쪽 주차 차량"
    val frontal get() = scene?.frontal ?: customFrontal

    /** 시뮬레이션·튜토리얼이 움직일 새 세계 (같은 장애물·벽, 자차는 출발점) */
    fun freshWorld() = AlleyWorld(world.parkedCars, world.safetyBollards, world.leftWallX, world.rightWallX, world.endLineY)

    /** 현재 자세에서의 규칙 판단 */
    fun decide(w: AlleyWorld, gap: AlleyWorld.Clearance, speedMps: Double?, plan: CorridorPlanner.Plan?) =
        AlleyDecisionEngine.decide(w, gap, speedMps, plan, scene, frontal)

    /** 현재 자세에서 다시 계획 */
    fun replan(w: AlleyWorld) = CorridorPlanner.plan(w, frontal, w.x, w.y)

    /** 튜토리얼 첫 안내: 상황 설명 */
    fun introSpeech(): String {
        if (customTitle != null) return "왼쪽에 주차 차량이 있고, 앞에서 차가 다가오는 좁은 길입니다."
        val s = scene ?: return "양쪽에 주차 차량이 번갈아 있는 골목입니다. 차 사이 간격을 보며 지나가 보겠습니다."
        val l = s.projected.count { !it.inPath && it.centerX < 0 }
        val r = s.projected.count { !it.inPath && it.centerX > 0 }
        val f = s.projected.count { it.frontal }
        val p = s.projected.count { it.inPath && !it.frontal }
        val parts = ArrayList<String>()
        if (l > 0) parts.add("왼쪽에 ${count(l)}")
        if (r > 0) parts.add("오른쪽에 ${count(r)}")
        if (p > 0) parts.add("길 가운데에 ${count(p)}")
        val objects = if (parts.isEmpty()) "주변에 큰 장애물은 없습니다." else parts.joinToString(", ") + " 장애물이 있습니다."
        val oncoming = if (f > 0) " 앞에서 차가 다가옵니다." else ""
        return "길 폭은 ${AlleyDecisionEngine.spoken(world.rightWallX - world.leftWallX)}입니다. $objects$oncoming"
    }

    /** 노면 화살표 단계 안내: 경로가 어느 쪽으로 붙는지 */
    fun pathSpeech(): String {
        val pts = plan.points
        if (pts.size < 2) return when {
            frontal.isNotEmpty() -> "비켜설 공간이 없습니다. 그 자리에 정차하고 뒤로 물러나 양보하세요."
            else -> "바로 앞이 막혀 있습니다. 출발하지 말고 정차하세요."
        }
        val narrow = plan.narrowest
        val x0 = pts.first().x
        val goesLeft = pts.any { it.x - x0 < -0.3 }
        val goesRight = pts.any { it.x - x0 > 0.3 }
        val dir = when {
            goesLeft && goesRight -> "장애물을 피해 좌우로 번갈아"
            goesLeft -> "왼쪽으로 붙어서"
            goesRight -> "오른쪽으로 붙어서"
            else -> "가운데를 지키며"
        }
        val tail = when (plan.outcome) {
            CorridorPlanner.Outcome.CLEAR -> "끝까지 지나갈 수 있습니다."
            CorridorPlanner.Outcome.YIELD -> (if (plan.yieldSide > 0) "오른쪽" else "왼쪽") + " 빈 곳에 정차해 마주 오는 차를 먼저 보내세요."
            CorridorPlanner.Outcome.BLOCKED -> "좁은 곳 앞에서 정차해야 합니다."
        }
        val narrowText = narrow?.let {
            val side = if (it.left < it.right) "왼쪽" else "오른쪽"
            " 가장 좁은 곳은 $side ${AlleyDecisionEngine.spoken(it.min)}입니다."
        } ?: ""
        return "노면 화살표를 따라 $dir 천천히 가세요.$narrowText $tail"
    }

    /** 통과(또는 정차) 후 요약 */
    fun doneSpeech(): String = when (plan.outcome) {
        CorridorPlanner.Outcome.CLEAR -> "잘 지나갔습니다. 가장 가까웠던 간격은 왼쪽 ${AlleyDecisionEngine.spoken(plan.minLeft)}, 오른쪽 ${AlleyDecisionEngine.spoken(plan.minRight)}입니다."
        CorridorPlanner.Outcome.YIELD -> "양보 후 마주 오는 차가 지나가면 다시 천천히 출발하세요."
        CorridorPlanner.Outcome.BLOCKED -> "이 길은 차 폭보다 좁습니다. 무리하게 지나가지 말고 다른 길을 이용하세요."
    }

    fun planText(): String = String.format(Locale.KOREA, "경로 %s · %s · 최소 좌 %.2fm / 우 %.2fm%s",
        when (plan.outcome) {
            CorridorPlanner.Outcome.CLEAR -> "통과 가능"
            CorridorPlanner.Outcome.YIELD -> "양보 필요"
            CorridorPlanner.Outcome.BLOCKED -> "통과 불가"
        }, plan.note, plan.minLeft, plan.minRight,
        plan.stopY?.let { String.format(Locale.KOREA, " · 정차 지점 %.1fm 앞", it - world.y) } ?: "")

    companion object {
        fun of(scene: LabelScene?): SceneAnalysis {
            val world = scene?.toWorld() ?: AlleyWorld()
            val plan = CorridorPlanner.plan(world, scene?.frontal.orEmpty())
            val report = scene?.let { LabelDistanceCheck.run(it, plan) }
            val decision = AlleyDecisionEngine.decide(world, world.clearance(), 0.0, plan, scene)
            return SceneAnalysis(null, emptyList(), scene, world, plan, report, decision)
        }

        /** 라벨에 없는 상황을 연습하기 위한 합성 장면: 대향차가 오고 오른쪽 빈 곳에서 양보 */
        fun oncomingPractice(): SceneAnalysis {
            val oncoming = AlleyWorld.ParkedCar(0.4, 42.0, 1.8, 4.4, "대향 차량")
            val world = AlleyWorld(listOf(
                AlleyWorld.ParkedCar(-1.9, 16.0), AlleyWorld.ParkedCar(-1.9, 27.0),
                AlleyWorld.ParkedCar(1.9, 33.0), AlleyWorld.ParkedCar(-1.9, 38.0), oncoming),
                emptyList(), -2.9, 2.9, 60.0)
            val plan = CorridorPlanner.plan(world, listOf(oncoming))
            val decision = AlleyDecisionEngine.decide(world, world.clearance(), 0.0, plan, null, listOf(oncoming))
            return SceneAnalysis("합성 연습 · 대향차 양보", listOf(oncoming), null, world, plan, null, decision)
        }

        private fun count(n: Int) = when (n) {
            1 -> "한 개"; 2 -> "두 개"; 3 -> "세 개"; 4 -> "네 개"; 5 -> "다섯 개"; else -> "${n}개"
        }
    }
}
