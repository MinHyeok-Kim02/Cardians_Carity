package com.example.pleostest

import android.content.Context

/**
 * 시뮬레이션 탭과 튜토리얼 앱이 함께 쓰는 장면 목록.
 * 기본 골목 · 합성 대향차 연습 · 앱 내장 라벨 · PC 라벨 서버에서 불러온 라벨.
 */
class ScenarioCatalog(context: Context) {
    enum class Kind { DEFAULT, PRACTICE, LABEL }

    data class Option(val title: String, val kind: Kind, val sample: LabelRepository.Sample? = null) {
        val subtitle get() = when (kind) {
            Kind.DEFAULT -> "기존 3D 골목 (가상 주차 차량)"
            Kind.PRACTICE -> "합성 장면 · 라벨에 없는 대향차 양보 연습"
            Kind.LABEL -> sample!!.sourceText + " · " + sample.file.substringAfterLast('/') +
                String.format(java.util.Locale.KOREA, " · %.1f초", sample.t) +
                (sample.expect?.let { " · 기대 $it" } ?: "")
        }
        /** 튜토리얼 Intent 로 넘기는 식별자 */
        val id get() = when (kind) {
            Kind.DEFAULT -> "default"
            Kind.PRACTICE -> "practice"
            Kind.LABEL -> (if (sample!!.fromServer) "server|" else "asset|") + sample.file + "|" + sample.t + "|" + sample.title
        }
    }

    val repo = LabelRepository(context)
    val options: MutableList<Option> = ArrayList<Option>().apply {
        add(Option("기본 골목 · 양쪽 주차 차량", Kind.DEFAULT))
        add(Option("합성 연습 · 대향차 양보", Kind.PRACTICE))
        repo.bundledSamples().forEach { add(Option(it.title, Kind.LABEL, it)) }
    }

    /** 장면 분석 (서버 라벨이면 네트워크 사용 → 백그라운드 스레드에서 호출) */
    fun analyze(option: Option): SceneAnalysis = when (option.kind) {
        Kind.DEFAULT -> SceneAnalysis.of(null)
        Kind.PRACTICE -> SceneAnalysis.oncomingPractice()
        Kind.LABEL -> {
            val s = option.sample!!
            SceneAnalysis.of(LabelSceneBuilder.build(repo.clip(s), s.t))
        }
    }

    fun addServerSamples(samples: List<LabelRepository.Sample>): List<Option> {
        val added = samples.map { Option(it.title, Kind.LABEL, it) }
            .filter { o -> options.none { it.id == o.id } }
        options.addAll(added)
        return added
    }

    fun find(id: String?): Option? {
        if (id == null) return null
        options.firstOrNull { it.id == id }?.let { return it }
        val parts = id.split("|")
        if (parts.size >= 3 && (parts[0] == "server" || parts[0] == "asset")) {
            val sample = LabelRepository.Sample(parts.getOrElse(3) { parts[1] }, parts[1],
                parts[2].toDoubleOrNull() ?: 0.0, null, parts[0] == "server")
            return Option(sample.title, Kind.LABEL, sample).also { options.add(it) }
        }
        return null
    }
}
