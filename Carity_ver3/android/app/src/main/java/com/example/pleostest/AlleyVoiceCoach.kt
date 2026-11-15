package com.example.pleostest

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 음성 안내. 엔진 우선순위: ① Pleos TextToSpeech SDK(한국어, ON_DEVICE) ② Android TTS ③ PC 음성(labeling_server /api/tts).
 * No vehicle or Pleos ambient-light control.
 */
class AlleyVoiceCoach(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    var ready = false; private set
    private var lastKey = ""
    private var spokenAt = -10000L
    private var generation = 0
    // ── ① Pleos TextToSpeech SDK ──
    private var pleos: ai.pleos.playground.tts.TextToSpeech? = null
    var pleosReady = false; private set
    var pleosError = ""; private set
    private var pleosSpeaking = false
    private val pleosEvents = object : EventListener {
        override fun onReady() {}
        override fun onStart() { pleosSpeaking = true }
        override fun onUpdatedRms(rms: Double) {}
        override fun onDone() { pleosSpeaking = false }
        override fun onStop() { pleosSpeaking = false }
        override fun onError(errorType: ErrorType) {
            pleosSpeaking = false; pleosError = errorType.name
            Log.w("CarTalkVoice", "PLEOS_TTS_ERROR $errorType")
        }
    }

    private fun startPleos(token: Int) {
        if (pleos != null) return
        try {
            val engine = ai.pleos.playground.tts.TextToSpeech(appContext, Mode.ON_DEVICE)
            pleos = engine
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (token != generation || pleos !== engine) return@post
                        try {
                            engine.setLocale(Locale.KOREA)
                            engine.addEventListener(pleosEvents)
                            pleosReady = true; pleosError = ""
                            Log.i("CarTalkVoice", "PLEOS_TTS_READY locale=ko-KR mode=ON_DEVICE")
                        } catch (e: Throwable) {
                            pleosError = e.message ?: e.javaClass.simpleName
                            Log.w("CarTalkVoice", "PLEOS_TTS_SETUP_FAIL $pleosError")
                        }
                    }
                }
                override fun onFailure(errorType: ErrorType) {
                    main.post { pleosReady = false; pleosError = "초기화 실패 ${errorType.name}" }
                    Log.w("CarTalkVoice", "PLEOS_TTS_INIT_FAIL $errorType")
                }
            })
        } catch (e: Throwable) {
            pleos = null; pleosError = e.message ?: e.javaClass.simpleName
            Log.w("CarTalkVoice", "PLEOS_TTS_UNAVAILABLE $pleosError")
        }
    }

    private fun stopPleos() {
        val engine = pleos ?: return
        pleos = null; pleosReady = false; pleosSpeaking = false
        runCatching { engine.stop() }
        runCatching { engine.removeEventListener(pleosEvents) }
        runCatching { engine.release() }
    }

    /** Pleos SDK 로 재생. 성공 여부 반환 */
    private fun speakPleos(message: String): Boolean {
        val engine = pleos ?: return false
        if (!pleosReady) return false
        return try {
            if (pleosSpeaking) engine.stop()
            engine.speak(message)
            pleosSpeaking = true
            true
        } catch (e: Throwable) {
            pleosError = e.message ?: e.javaClass.simpleName
            Log.w("CarTalkVoice", "PLEOS_TTS_SPEAK_FAIL $pleosError")
            false
        }
    }

    fun start() {
        if (tts != null) return
        val token = ++generation
        startPleos(token)
        tts = TextToSpeech(appContext) { status ->
            if (token != generation) return@TextToSpeech
            ready = status == TextToSpeech.SUCCESS &&
                (tts?.setLanguage(Locale.KOREAN) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= 0
            Log.i("CarTalkVoice", "Korean TTS ready=$ready")
        }
    }
    fun announce(key: String, message: String, now: Long) {
        if (key == "clear") { lastKey = ""; return }
        if (key == lastKey) return
        val urgent = key == "contact" || key.startsWith("near:")
        if ((!ready && !pleosReady) || now - spokenAt < (if (urgent) 2000 else 6000)) return
        if (pleosReady && speakPleos(message)) {
            lastKey = key; spokenAt = now
            Log.i("CarTalkVoice", "PLEOS_TTS_REQUEST key=$key message=$message")
            return
        }
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, key)
        if (result == TextToSpeech.SUCCESS) {
            lastKey = key; spokenAt = now
            Log.i("CarTalkVoice", "TTS_REQUEST key=$key message=$message")
        }
    }
    /**
     * 규칙·AI 판단 음성: 같은 키를 1.5초 안에 반복하지 않는 것 외에는 바로 재생(이전 안내는 끊음).
     * 반환값: 재생 요청 성공 여부 (TTS 준비 전이면 false)
     */
    fun speakNow(key: String, message: String): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (message.isBlank() || (key == lastKey && now - spokenAt < 1500)) return false
        if (pleosReady && speakPleos(message)) {
            lastKey = key; spokenAt = now
            Log.i("CarTalkVoice", "PLEOS_TTS_JUDGE key=$key message=$message")
            return true
        }
        if (!ready) {   // 기기 TTS 엔진이 없으면(에뮬레이터) PC 라벨링 서버의 Windows 한국어 음성으로 재생
            lastKey = key; spokenAt = now
            speakViaPc(key, message)
            return true
        }
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, key)
        if (result == TextToSpeech.SUCCESS) {
            lastKey = key; spokenAt = now
            Log.i("CarTalkVoice", "TTS_JUDGE key=$key message=$message")
            return true
        }
        return false
    }
    // ── PC 음성 대체 (labeling_server /api/tts → WAV) ──
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var player: MediaPlayer? = null
    private var pcToken = 0
    /** null: 아직 시도 안 함, true: PC 음성 재생 성공, false: 실패 */
    var pcVoiceOk: Boolean? = null; private set
    var pcVoiceError = ""; private set
    val statusText get() = when {
        pleosReady -> "음성: Pleos TTS (한국어 · 기기 내 합성)"
        ready -> "음성: 기기 한국어 TTS (Pleos TTS ${pleosError.ifBlank { "대기" }})"
        pcVoiceOk == true -> "음성: PC 음성(Windows 한국어) 대체 · Pleos TTS ${pleosError.ifBlank { "준비 안 됨" }}"
        pcVoiceOk == false -> "음성: 기기 TTS 없음 · PC 음성 실패 ($pcVoiceError)"
        else -> "음성: 기기 TTS 없음 · PC 음성(labeling_server) 사용 예정"
    }

    private fun speakViaPc(key: String, message: String) {
        val token = ++pcToken
        io.execute {
            if (token != pcToken) return@execute
            val result = runCatching {
                val name = "tts_" + Integer.toHexString(message.hashCode()) + ".wav"
                val file = File(appContext.cacheDir, name)
                if (!file.exists() || file.length() < 100) {
                    val url = URL(BuildConfig.LABEL_SERVER_URL.trimEnd('/') + "/api/tts?text=" +
                        URLEncoder.encode(message, "UTF-8"))
                    val c = url.openConnection() as HttpURLConnection
                    try {
                        c.connectTimeout = 3000; c.readTimeout = 20000
                        if (c.responseCode !in 200..299) {
                            val err = c.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                            throw IllegalStateException("HTTP ${c.responseCode} ${err.take(120)}")
                        }
                        val tmp = File(appContext.cacheDir, "$name.part")
                        c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                        tmp.renameTo(file)
                    } finally { c.disconnect() }
                }
                file
            }
            main.post {
                if (token != pcToken) return@post
                result.onSuccess { file ->
                    try {
                        player?.release()
                        player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener { it.release(); if (player === it) player = null }
                            prepare(); start()
                        }
                        pcVoiceOk = true
                        Log.i("CarTalkVoice", "PC_TTS_PLAY key=$key message=$message")
                    } catch (e: Exception) {
                        pcVoiceOk = false; pcVoiceError = e.message ?: e.javaClass.simpleName
                    }
                }.onFailure { e ->
                    pcVoiceOk = false; pcVoiceError = (e.message ?: e.javaClass.simpleName).take(80)
                    Log.w("CarTalkVoice", "PC_TTS_FAIL key=$key error=$pcVoiceError")
                }
            }
        }
    }

    private fun stopPc() {
        pcToken++
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    fun silence() {
        tts?.stop(); if (player != null) stopPc()
        if (pleosSpeaking) { runCatching { pleos?.stop() }; pleosSpeaking = false }
    }
    fun reset() { silence(); lastKey = ""; spokenAt = -10000L }
    fun stop() {
        generation++; ready = false
        tts?.stop(); tts?.shutdown(); tts = null
        stopPc(); stopPleos()
        reset()
    }
}
