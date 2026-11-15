package com.example.pleostest

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 라벨 장애물 배치에서 거리 계산이 맞게 동작하는지 검증 (앱 화면 + 단위 테스트 공용).
 * 기대값 = 라벨 투영 좌표로 직접 계산한 차체 옆면~객체 안쪽 모서리 거리,
 * 측정값 = 같은 배치를 AlleyWorld 에 넣고 자차를 곧게 통과시키며 clearance() 로 잰 최소 간격.
 */
object LabelDistanceCheck {
    data class Row(
        val label: String, val side: String, val zNear: Double,
        val egoX: Double, val expected: Double, val measured: Double,
    ) {
        val diff get() = measured - expected
        val ok get() = abs(diff) <= TOLERANCE
        fun text() = String.format(Locale.KOREA, "%s %s %.1fm앞 · 기대 %.2f / 측정 %.2f m %s",
            side, label, zNear, expected, measured, if (ok) "✓" else "✗ 차이 %.2f".format(diff))
    }

    data class Report(val rows: List<Row>, val pathMinLeft: Double, val pathMinRight: Double) {
        val passed get() = rows.count { it.ok }
        val allOk get() = rows.all { it.ok }
        fun summary() = if (rows.isEmpty()) "측면 객체 없음 · 벽 간격만 계산"
            else "거리 검증 ${passed}/${rows.size} 일치 (허용 ${(TOLERANCE * 100).toInt()}cm)"
    }

    const val TOLERANCE = 0.02

    /**
     * 객체마다 그 객체만 있는 세계(벽은 멀리)에서 자차를 곧게 통과시키며 간격을 재고,
     * 투영 좌표로 계산한 기대값(안쪽 모서리와 차체 옆면 사이)과 비교합니다.
     * 경로에 걸친 객체는 반대쪽으로 0.5m 비켜 지나가도록 자차 x 를 정합니다.
     * pathMin*: 전체 장면에서 경로 계획을 따라갈 때의 좌우 최소 간격(같은 거리 엔진).
     */
    fun run(scene: LabelScene, plan: CorridorPlanner.Plan? = null): Report {
        val halfW = CorridorPlanner.EGO_HALF_W
        val halfL = CorridorPlanner.EGO_HALF_L
        val rows = ArrayList<Row>()
        for ((i, p) in scene.projected.withIndex()) {
            if (p.frontal) continue
            val car = scene.parkedCars[i]
            val left = car.x < 0
            val inner = if (left) car.x + car.width / 2 else car.x - car.width / 2
            // 자차 위치: 객체와 겹치지 않으면 x=0, 겹치면 반대편으로 0.5m 여유
            val egoX = if (left) max(0.0, inner + halfW + 0.5) else min(0.0, inner - halfW - 0.5)
            val expected = if (left) (egoX - halfW) - inner else inner - (egoX + halfW)
            val world = AlleyWorld(listOf(car), emptyList(), -50.0, 50.0, car.y + 50)
            var measured = Double.MAX_VALUE
            var y = car.y - car.length / 2 - halfL + 0.05
            while (y <= car.y + car.length / 2 + halfL - 0.05) {
                world.restore(egoX, y, 0.0, false)
                val g = world.clearance()
                measured = min(measured, if (left) g.leftMeters else g.rightMeters)
                y += 0.1
            }
            rows.add(Row(p.label, if (left) "왼쪽" else "오른쪽", p.zNear, egoX, expected, measured))
        }
        return Report(rows, plan?.minLeft ?: Double.NaN, plan?.minRight ?: Double.NaN)
    }
}
