package com.example.pleostest

import java.net.HttpURLConnection
import java.net.URL

/**
 * AI 판단(설명·음성 문장) 클라이언트.
 *  - .env 에 ANTHROPIC_API_KEY 가 있으면 Claude(Messages API)
 *  - 없으면 Qwen (Ollama 등 OpenAI 호환 서버, 기본 http://10.0.2.2:11434)
 * 규칙 엔진 결정이 최종이며, AI 는 TTS 로 읽을 문장과 근거를 만듭니다. (merge 참고)
 * 순수 JVM 코드(java.net)라 단위 테스트에서 프롬프트·응답 파싱을 검증할 수 있습니다.
 */
class AiJudge(
    private val claudeKey: String,
    private val claudeModel: String = "claude-haiku-5-5",
    private val qwenBaseUrl: String = "http://10.0.2.2:11434",
    private val qwenModel: String = "qwen2.5:3b",
) {
    enum class Provider(val label: String) { CLAUDE("Claude"), QWEN("Qwen"), NONE("규칙 엔진") }

    data class Result(
        val provider: String, val ok: Boolean,
        val action: AlleyDecisionEngine.Action?, val speech: String?, val reason: String?,
        val latencyMs: Long, val error: String? = null,
    )

    /** 규칙 + AI 결합 결과 */
    data class Final(val action: AlleyDecisionEngine.Action, val speech: String, val source: String, val aiAgrees: Boolean?)

    val provider get() = when {
        claudeKey.isNotBlank() -> Provider.CLAUDE
        qwenBaseUrl.isNotBlank() -> Provider.QWEN
        else -> Provider.NONE   // release(단독 실행) 빌드: AI 호출 없이 규칙 엔진 문장 사용
    }
    val providerText get() = when (provider) {
        Provider.CLAUDE -> "Claude · $claudeModel"
        Provider.QWEN -> "Qwen · $qwenModel"
        Provider.NONE -> "규칙 엔진"
    }

    /** 네트워크 호출 (백그라운드 스레드에서 호출) */
    fun judge(decision: AlleyDecisionEngine.Decision): Result {
        if (provider == Provider.NONE) return Result(providerText, false, null, null, null, 0, DISABLED)
        val started = System.currentTimeMillis()
        return try {
            val text = if (provider == Provider.CLAUDE) callClaude(decision) else callQwen(decision)
            val parsed = parseReply(text)
            Result(providerText, parsed != null, parsed?.first, parsed?.second, parsed?.third,
                System.currentTimeMillis() - started, if (parsed == null) "응답 JSON 해석 실패: ${text.take(120)}" else null)
        } catch (e: Exception) {
            Result(providerText, false, null, null, null, System.currentTimeMillis() - started,
                e.message ?: e.javaClass.simpleName)
        }
    }

    private fun callClaude(decision: AlleyDecisionEngine.Decision): String {
        val body = "{\"model\":${MiniJson.quote(claudeModel)},\"max_tokens\":300," +
            "\"system\":${MiniJson.quote(SYSTEM_PROMPT)}," +
            "\"messages\":[{\"role\":\"user\",\"content\":${MiniJson.quote(userPrompt(decision))}}]}"
        val reply = post("https://api.anthropic.com/v1/messages", body, mapOf(
            "x-api-key" to claudeKey, "anthropic-version" to "2023-06-01"))
        val o = MiniJson.obj(MiniJson.parse(reply))
        return MiniJson.arr(o["content"]).map { MiniJson.obj(it) }
            .filter { MiniJson.str(it["type"]) == "text" }.joinToString("") { MiniJson.str(it["text"]) }
    }

    private fun callQwen(decision: AlleyDecisionEngine.Decision): String {
        val user = userPrompt(decision) + if (qwenModel.contains("qwen3", true)) "\n/no_think" else ""
        // max_tokens 로 답 길이를 제한해 PC CPU 에서도 응답 시간을 줄임
        val body = "{\"model\":${MiniJson.quote(qwenModel)},\"temperature\":0.2,\"stream\":false,\"max_tokens\":150," +
            "\"messages\":[{\"role\":\"system\",\"content\":${MiniJson.quote(SYSTEM_PROMPT)}}," +
            "{\"role\":\"user\",\"content\":${MiniJson.quote(user)}}]}"
        val reply = post(qwenBaseUrl.trimEnd('/') + "/v1/chat/completions", body, emptyMap(), QWEN_TIMEOUT_MS)
        val o = MiniJson.obj(MiniJson.parse(reply))
        val choice = MiniJson.obj(MiniJson.arr(o["choices"]).firstOrNull())
        return MiniJson.str(MiniJson.obj(choice["message"])["content"])
    }

    private fun post(url: String, body: String, headers: Map<String, String>, readTimeoutMs: Int = 20000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 4000; c.readTimeout = readTimeoutMs; c.doOutput = true
            c.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${text.take(160)}")
            return text
        } finally { c.disconnect() }
    }

    companion object {
        /** PC CPU 의 Qwen 2.5 3B 는 10~20초 걸릴 수 있음 (처음 호출은 모델 적재로 더 김) */
        const val QWEN_TIMEOUT_MS = 45000
        /** AI 를 쓰지 않는 빌드에서 Result.error 로 돌려주는 표시 */
        const val DISABLED = "AI 사용 안 함"
        /** TTS 프롬프팅: 차 안에서 바로 읽을 짧은 한국어 한 문장을 만들도록 지시 */
        val SYSTEM_PROMPT = """
당신은 현대자동차 Pleos 차량의 초보 운전자 음성 코치입니다.
좁은 골목, 양쪽 주차 차량, 마주 오는 차 상황에서 측정값과 규칙 엔진 판단(rule)을 보고 운전자에게 들려줄 음성 안내를 만듭니다.
지켜야 할 규칙:
1. 안전 판단은 rule.action 이 최종입니다. rule 보다 덜 조심스러운 행동(진행, 가속)을 권하지 마세요.
2. speech 는 TTS 로 바로 읽을 한국어 존댓말 한 문장, 45자 이내. 순서: 어느 쪽인지 → 얼마나 가까운지 → 무엇을 할지(핸들 방향, 감속, 정차).
3. speech 에 영어, 기호, 괄호, 이모지, 소수점 숫자를 쓰지 마세요. 거리는 '30센티미터', '1.2미터'처럼 읽는 말로 쓰세요.
4. action 은 PROCEED, KEEP_LEFT, KEEP_RIGHT, SLOW, YIELD_RIGHT, YIELD_LEFT, STOP, REVERSE 중 하나.
5. reason 은 화면에 표시할 판단 근거 한 문장.
JSON 객체 하나만 출력하세요: {"action":"...","speech":"...","reason":"..."}
""".trim()

        fun userPrompt(decision: AlleyDecisionEngine.Decision): String {
            val facts = decision.facts.entries.joinToString(",") { "${MiniJson.quote(it.key)}:${MiniJson.quote(it.value)}" }
            return "{\"measurements\":{$facts}," +
                "\"rule\":{\"action\":${MiniJson.quote(decision.action.name)},\"severity\":${decision.severity}," +
                "\"headline\":${MiniJson.quote(decision.headline)},\"speech\":${MiniJson.quote(decision.speech)}," +
                "\"reasons\":[${decision.reasons.joinToString(",") { MiniJson.quote(it) }}]}}"
        }

        /** 모델 응답에서 JSON 을 찾아 (action, speech, reason) 으로. 생각 과정(<think>)·코드펜스 제거 */
        fun parseReply(text: String): Triple<AlleyDecisionEngine.Action?, String, String>? {
            val clean = text.replace(Regex("(?s)<think>.*?</think>"), "").replace("```json", "").replace("```", "")
            val start = clean.indexOf('{'); val end = clean.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                val o = MiniJson.obj(MiniJson.parse(clean.substring(start, end + 1)))
                val speech = sanitizeSpeech(MiniJson.str(o["speech"]))
                if (speech.isBlank()) null
                else Triple(AlleyDecisionEngine.Action.of(MiniJson.str(o["action"])), speech, MiniJson.str(o["reason"]).trim())
            } catch (e: Exception) { null }
        }

        /** TTS 로 읽기 어색한 문자 제거, 길이 제한 */
        fun sanitizeSpeech(s: String): String =
            s.replace(Regex("[()\\[\\]{}<>*#_~`\"]"), " ").replace(Regex("\\s+"), " ").trim().take(80)

        /**
         * 규칙과 AI 결합. 규칙 심각도 3(접촉·초근접)은 항상 규칙 문장.
         * AI 행동이 규칙보다 덜 조심스러우면 규칙 행동·문장 유지, 아니면 AI 문장을 음성으로 사용.
         */
        fun merge(rule: AlleyDecisionEngine.Decision, ai: Result?): Final {
            if (ai == null || !ai.ok || ai.speech.isNullOrBlank())
                return Final(rule.action, rule.speech, "규칙 엔진", null)
            val agrees = ai.action == null || ai.action == rule.action
            if (rule.severity >= 3) return Final(rule.action, rule.speech, "규칙 엔진(긴급)", agrees)
            if (ai.action != null && ai.action.danger < rule.action.danger)
                return Final(rule.action, rule.speech, "규칙 엔진(AI 완화 거부)", false)
            return Final(rule.action, ai.speech, ai.provider, agrees)
        }
    }
}
