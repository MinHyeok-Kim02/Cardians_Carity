package com.example.pleostest

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * AI 판단 호출을 백그라운드에서 하나씩 처리하고, 가장 최근 요청의 결과만 메인 스레드로 돌려줍니다.
 * .env 의 ANTHROPIC_API_KEY 가 있으면 Claude, 없으면 Qwen(Ollama) — AiJudge 참고.
 */
class AiCoordinator(
    val judge: AiJudge = AiJudge(BuildConfig.ANTHROPIC_API_KEY, BuildConfig.CLAUDE_MODEL,
        BuildConfig.QWEN_BASE_URL, BuildConfig.QWEN_MODEL),
) {
    private val main = Handler(Looper.getMainLooper())
    private var exec: ExecutorService = Executors.newSingleThreadExecutor()
    private var token = 0
    var busy = false; private set

    val providerText get() = judge.providerText +
        if (judge.provider == AiJudge.Provider.QWEN) " (PC Ollama)" else ""

    /** 결과 콜백은 메인 스레드. 더 새 요청이 들어오면 이전 결과는 버립니다. */
    fun request(decision: AlleyDecisionEngine.Decision,
                done: (AiJudge.Result, AiJudge.Final) -> Unit) {
        val t = ++token
        busy = true
        if (exec.isShutdown) exec = Executors.newSingleThreadExecutor()
        exec.execute {
            if (t != token) return@execute
            val result = judge.judge(decision)
            android.util.Log.i("CarityAI", "provider=${result.provider} ok=${result.ok} ${result.latencyMs}ms " +
                "action=${result.action} speech=${result.speech} error=${result.error}")
            main.post {
                if (t != token) return@post
                busy = false
                done(result, AiJudge.merge(decision, result))
            }
        }
    }

    fun cancel() { token++; busy = false }
    fun close() { cancel(); exec.shutdownNow() }
}
