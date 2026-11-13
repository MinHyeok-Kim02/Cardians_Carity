package com.example.pleostest

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/** Android TTS demo. No vehicle or Pleos ambient-light control. */
class AlleyVoiceCoach(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    var ready = false; private set
    private var lastKey = ""
    private var spokenAt = -10000L
    private var generation = 0
    fun start() {
        if (tts != null) return
        val token = ++generation
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
        if (!ready || now - spokenAt < (if (urgent) 2000 else 6000)) return
        val result = tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, key)
        if (result == TextToSpeech.SUCCESS) {
            lastKey = key; spokenAt = now
            Log.i("CarTalkVoice", "TTS_REQUEST key=$key message=$message")
        }
    }
    fun silence() { tts?.stop() }
    fun reset() { silence(); lastKey = ""; spokenAt = -10000L }
    fun stop() {
        generation++; ready = false
        tts?.stop(); tts?.shutdown(); tts = null
        reset()
    }
}
