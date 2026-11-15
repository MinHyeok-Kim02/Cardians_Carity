package com.example.pleostest

/** One upper-screen announcement at a time. Risk always takes display priority. */
object AlleyMacroPresentation {
    data class Card(val icon: String, val progress: String, val title: String, val detail: String, val urgent: Boolean = false)
    fun card(stage: Int, gap: Double, approaching: Boolean, contact: Boolean, hazardOn: Boolean): Card? {
        if (contact || gap <= .3) return Card("!", "근접 경고 우선",
            if (contact) "가상 접촉 · 정지" else "매우 가까워요 · 정차 후 확인",
            "주변 간격을 확인하세요. 실제 자동 제동을 실행한 것은 아닙니다.", true)
        if (approaching && gap <= .8) return Card("!", "근접 경고 우선",
            "차량과 가까워지고 있어요", "정차하고 주변 간격을 확인하세요.", true)
        return when (stage) {
            0 -> Card("✓", "사용자 확인 · SIMULATION", "정차 확인 · 골목 패스를 실행할까요?",
                "미러 → 조수석 창문 → 비상등 순서로 가상 재생합니다. 하단에서 확인해 주세요.")
            1 -> Card("↔", "1 / 3 · SIMULATION", "사이드미러 접기 시뮬레이션", "교행 준비 · 실제 미러 폴딩이나 차폭 축소는 실행하지 않습니다.")
            2 -> Card("▱", "2 / 3 · SIMULATION", "조수석 창문 미세 개방 시뮬레이션", "외부 소리 청취 준비 · 실제 창문 개방과 3cm 환산은 미검증입니다.")
            3 -> Card("△", "3 / 3 · SIMULATION", "비상등 3회 점멸 시뮬레이션",
                if (hazardOn) "● ON · 상대 운전자에게 양보 표시" else "○ OFF · 상대 운전자에게 양보 표시")
            4 -> Card("✓", "가상 실행 완료 · SIMULATION", "골목 패스 준비 완료 · 주변을 확인하세요",
                "미러·창문·비상등 실제 차량 제어는 실행하지 않았습니다.")
            else -> null
        }
    }
}
