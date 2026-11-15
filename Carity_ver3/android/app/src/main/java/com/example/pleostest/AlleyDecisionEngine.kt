package com.example.pleostest

import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 규칙 기반 판단 엔진 (최종 안전 결정).
 * 입력: AlleyWorld 간격(같은 거리 엔진) + 현재 속도 + 경로 계획(CorridorPlanner).
 * 출력: 행동·심각도·화면 문구·TTS 문장. AI(Claude/Qwen)는 이 결과를 근거로 설명·음성 문장만 다듬고,
 * 더 위험한 행동으로 바꿀 수 없습니다(AiJudge.merge).
 */
object AlleyDecisionEngine {
    enum class Action(val ko: String, val danger: Int) {
        PROCEED("천천히 진행", 0),
        KEEP_LEFT("왼쪽으로 조정", 1),
        KEEP_RIGHT("오른쪽으로 조정", 1),
        SLOW("감속", 2),
        YIELD_RIGHT("오른쪽 정차·양보", 3),
        YIELD_LEFT("왼쪽 정차·양보", 3),
        STOP("정차", 4),
        REVERSE("정차 후 후진", 4),
        WAIT("입력 대기", 0);
        companion object {
            fun of(name: String?) = values().firstOrNull { it.name == name?.trim()?.uppercase(Locale.ROOT) }
        }
    }

    data class Decision(
        val action: Action,
        /** 0 안내 · 1 주의 · 2 경고 · 3 긴급 */
        val severity: Int,
        val headline: String,
        val speech: String,
        val reasons: List<String>,
        /** 같은 상황 반복 음성 방지용 키 */
        val key: String,
        /** AI 프롬프트에 넣는 측정값 */
        val facts: Map<String, String>,
    )

    const val NEAR = 0.3
    const val CAUTION = 0.6
    const val SLOW_KMH = 10.0

    /** TTS 로 읽기 좋은 거리: 1m 미만은 5cm 단위 센티미터, 이상은 0.1m 단위 */
    fun spoken(meters: Double): String = when {
        !meters.isFinite() -> "알 수 없는 거리"
        meters < 1.0 -> "${(meters * 20).roundToInt() * 5}센티미터"
        else -> String.format(Locale.KOREA, "%.1f미터", meters)
    }

    private fun m(v: Double) = if (v.isFinite()) String.format(Locale.ROOT, "%.2f", v) else "null"

    fun decide(world: AlleyWorld, gap: AlleyWorld.Clearance, speedMps: Double?,
               plan: CorridorPlanner.Plan?, scene: LabelScene? = null,
               frontalCars: List<AlleyWorld.ParkedCar>? = null): Decision {
        val kmh = speedMps?.let { abs(it) * 3.6 }
        val left = gap.leftMeters; val right = gap.rightMeters; val front = gap.frontMeters
        val nearSide = if (left <= right) "왼쪽" else "오른쪽"
        val farSide = if (left <= right) "오른쪽" else "왼쪽"
        val nearest = min(left, right)
        val frontal = (frontalCars ?: scene?.frontal.orEmpty()).filter { it.y > world.y }
        val frontalDist = frontal.minOfOrNull { it.y - it.length / 2 - (world.y + CorridorPlanner.EGO_HALF_L) }
        val facts = linkedMapOf(
            "left_m" to m(left), "right_m" to m(right), "front_m" to m(front),
            "nearest_obstacle" to gap.side,
            "speed_kmh" to (kmh?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "null"),
            "road_width_m" to m(world.rightWallX - world.leftWallX),
            "oncoming_ahead_m" to (frontalDist?.let { m(it) } ?: "none"),
            "plan" to (plan?.let { "${it.outcome}:${it.note}" } ?: "none"),
            "plan_min_left_m" to (plan?.let { m(it.minLeft) } ?: "null"),
            "plan_min_right_m" to (plan?.let { m(it.minRight) } ?: "null"),
        )
        scene?.let { facts["scene"] = it.describe() }

        fun d(a: Action, sev: Int, head: String, say: String, vararg why: String, keyExtra: String = "") =
            Decision(a, sev, head, say, why.toList(), a.name + keyExtra, facts)

        if (gap.contact)
            return d(Action.STOP, 3, "접촉 · 즉시 정차", "접촉했습니다. 즉시 정차하고 주변을 확인하세요.", "가상 차체가 장애물과 닿음")
        if (gap.meters <= NEAR) {
            val frontNear = front <= gap.meters + 1e-6 && front < min(left, right)
            return if (frontNear) d(Action.STOP, 3, "앞 ${spoken(front)} · 정차",
                "앞이 ${spoken(front)}로 너무 가깝습니다. 정차하세요.", "전방 간격 ${m(front)}m ≤ ${NEAR}m", keyExtra = ":front")
            else d(Action.STOP, 3, "$nearSide ${spoken(nearest)} · 정차",
                "${nearSide}에 너무 가깝게 붙었습니다. 정차 후 핸들을 ${farSide}으로 돌려 천천히 빠져나오세요.",
                "$nearSide 간격 ${m(nearest)}m ≤ ${NEAR}m", keyExtra = ":$nearSide")
        }
        if (plan != null && plan.outcome == CorridorPlanner.Outcome.YIELD && (frontalDist == null || frontalDist < 40)) {
            val toRight = plan.yieldSide > 0
            val sideKo = if (toRight) "오른쪽" else "왼쪽"
            return d(if (toRight) Action.YIELD_RIGHT else Action.YIELD_LEFT, 2,
                "대향차 · $sideKo 정차 후 양보",
                "앞에서 차가 옵니다. $sideKo 빈 곳에 붙어 정차하고 먼저 보내세요.",
                "대향차와 나란히 지나갈 폭 부족", plan.note)
        }
        if (plan != null && plan.outcome == CorridorPlanner.Outcome.BLOCKED) {
            val toStop = (plan.stopY ?: world.y) - world.y
            if (toStop < 8)
                return d(if (frontal.isNotEmpty()) Action.REVERSE else Action.STOP, 2, "통과 불가 · 정차",
                    if (frontal.isNotEmpty()) "비켜설 공간이 없습니다. 정차 후 천천히 후진해 넓은 곳에서 양보하세요."
                    else "앞 통로가 차 폭보다 좁습니다. 정차하고 다른 길을 확인하세요.", plan.note)
            if (toStop < 25)
                return d(Action.SLOW, 1, "앞 ${String.format(Locale.KOREA, "%.0f", toStop)}m 통과 어려움 · 감속",
                    "앞쪽 길이 좁아 지나가기 어렵습니다. 속도를 줄이고 정차를 준비하세요.", plan.note, keyExtra = ":blocked")
        }
        if (kmh != null && kmh > SLOW_KMH && nearest < 1.2)
            return d(Action.SLOW, 2, "좁은 길 · 감속", "좁은 길입니다. 시속 10킬로미터 이하로 천천히 가세요.",
                "속도 ${String.format(Locale.KOREA, "%.0f", kmh)}km/h, 최근접 ${m(nearest)}m")
        if (nearest < CAUTION && abs(left - right) > 0.3) {
            val keep = if (left < right) Action.KEEP_RIGHT else Action.KEEP_LEFT
            return d(keep, 1, "$nearSide ${spoken(nearest)} · ${farSide}으로",
                "${nearSide}에 가깝습니다. ${spoken(nearest)}입니다. 핸들을 ${farSide}으로 살짝 돌리세요.",
                "$nearSide ${m(nearest)}m, $farSide ${m(if (left < right) right else left)}m", keyExtra = ":$nearSide")
        }
        if (nearest < CAUTION)
            return d(Action.SLOW, 1, "양쪽 좁음 · 가운데 유지",
                "양쪽이 모두 좁습니다. 가운데를 유지하며 천천히 지나가세요.", "좌 ${m(left)}m, 우 ${m(right)}m",
                keyExtra = ":both")
        val narrowAhead = plan?.points?.filter { it.y > world.y && it.y < world.y + 12 }?.minByOrNull { it.min }
        if (narrowAhead != null && narrowAhead.min < 0.5) {
            val side = if (narrowAhead.left < narrowAhead.right) "왼쪽" else "오른쪽"
            return d(Action.SLOW, 1, "앞 좁은 구간 · 감속",
                "앞에 좁은 구간이 있습니다. ${side} 간격이 ${spoken(narrowAhead.min)}까지 줄어드니 천천히 가세요.",
                "경로상 최소 ${m(narrowAhead.min)}m (${String.format(Locale.KOREA, "%.0f", narrowAhead.y - world.y)}m 앞)",
                keyExtra = ":ahead")
        }
        return d(Action.PROCEED, 0, "여유 충분 · 천천히 진행", "좌우 여유가 충분합니다. 천천히 진행하세요.",
            "좌 ${m(left)}m, 우 ${m(right)}m")
    }
}
