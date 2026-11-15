package com.example.pleostest

/** Demo thresholds for synthetic side clearance; not calibrated vehicle safety limits. */
object AlleyPassEngine {
    enum class Level { OFF, INFO, CAUTION, STOP }
    data class Guidance(val level: Level, val title: String, val detail: String)

    fun evaluate(enabled: Boolean, leftCm: Int, rightCm: Int, speedMps: Float?): Guidance {
        if (!enabled) return Guidance(Level.OFF, "시나리오 대기", "좁은 골목 시나리오를 켜고 가상 거리를 조절하세요.")
        val side = when {
            leftCm == rightCm -> "좌우"
            leftCm < rightCm -> "왼쪽"
            else -> "오른쪽"
        }
        val nearest = minOf(leftCm, rightCm)
        if (nearest <= 30) return Guidance(Level.STOP, "$side 근접 · 정차 후 확인", "가상 거리 기준 근접 예시입니다. 주변을 직접 확인하세요.")
        if (speedMps == null || !speedMps.isFinite())
            return Guidance(Level.CAUTION, "차량 속도 수신 대기", "가상 거리는 조절할 수 있습니다. 현재 속도·정차 여부는 확인할 수 없습니다.")
        if (kotlin.math.abs(speedMps) * 3.6f > 10f)
            return Guidance(Level.CAUTION, "속도를 낮추고 주변 확인", "시연 기준 10 km/h 초과입니다. 교행 전 여유 공간을 직접 확인하세요.")
        if (nearest <= 60) return Guidance(Level.CAUTION, "$side 공간 주의", "가상 거리 기준 주의 예시입니다. 서두르지 말고 주변을 확인하세요.")
        return Guidance(Level.INFO, "주변을 확인하며 교행 준비", "거리 여유를 가정한 시나리오입니다. 통과 가능이나 충돌 방지를 보장하지 않습니다.")
    }
}
