package com.example.myapplication;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.Consumer;

/** Serial warning queue: three beeps finish before directional tutorial speech. */
final class WarningAudio {
    private static final int SHORT_BEEP_MS = 100;
    private static final int LONG_BEEP_MS = 180;
    private static final int SPEECH_DELAY_MS = 620;
    private static final int SPEECH_TIMEOUT_MS = 8000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<AlleySimulation.Side> queue = new ArrayDeque<>();
    private final AudioManager manager;
    private ToneGenerator tone;
    private TextToSpeech tts;
    private boolean ready, busy, tutorial = true, active = true;
    private int generation;

    WarningAudio(Context context, Consumer<String> status) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        initializeTone();
        initializeSpeech(context, status);
    }

    private void initializeTone() {
        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 85);
        } catch (RuntimeException error) {
            Log.w("OnSense", "Tone unavailable", error);
        }
    }

    private void initializeSpeech(Context context, Consumer<String> status) {
        tts = new TextToSpeech(context.getApplicationContext(),
                result -> onSpeechInitialized(result, status));
        tts.setOnUtteranceProgressListener(createSpeechListener());
    }

    private void onSpeechInitialized(int result, Consumer<String> status) {
        if (tts == null) return;
        ready = result == TextToSpeech.SUCCESS && tts.setLanguage(Locale.KOREAN) >= 0;
        status.accept(ready ? "Android TTS · 한국어 준비" : "음성 미준비 · 한국어 TTS 엔진/데이터 확인");
        Log.i("OnSense", "Android TTS Korean ready=" + ready);
    }

    private UtteranceProgressListener createSpeechListener() {
        return new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { completeUtterance(id); }
            @Override public void onError(String id) { completeUtterance(id); }
        };
    }

    private void completeUtterance(String id) {
        handler.post(() -> {
            if (utteranceId(generation).equals(id)) finish();
        });
    }

    void setTutorial(boolean enabled) {
        tutorial = enabled;
    }

    void enqueue(AlleySimulation.Side side) {
        if (!active) return;
        queue.add(side);
        if (!busy) next();
    }

    private void next() {
        if (!active || queue.isEmpty()) {
            busy = false;
            manager.abandonAudioFocus(null);
            return;
        }
        busy = true;
        startWarning(queue.remove(), ++generation);
    }

    private void startWarning(AlleySimulation.Side side, int token) {
        manager.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        Log.i("OnSense", "AUDIO_START side=" + side + " event=" + token);
        playBeepPattern();
        handler.postDelayed(() -> speakTutorial(side, token), SPEECH_DELAY_MS);
    }

    private void playBeepPattern() {
        beep(SHORT_BEEP_MS);
        handler.postDelayed(() -> beep(SHORT_BEEP_MS), 180);
        handler.postDelayed(() -> beep(LONG_BEEP_MS), 360);
    }

    private void beep(int duration) {
        if (active && tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, duration);
    }

    private void speakTutorial(AlleySimulation.Side side, int token) {
        if (!active || token != generation) return;
        if (!tutorial || !ready) {
            finish();
            return;
        }
        int result = tts.speak(directionMessage(side), TextToSpeech.QUEUE_FLUSH, null, utteranceId(token));
        Log.i("OnSense", "TTS_REQUEST side=" + side + " result=" + result);
        if (result == TextToSpeech.ERROR) finish();
        else handler.postDelayed(() -> stopTimedOutSpeech(token), SPEECH_TIMEOUT_MS);
    }

    private String directionMessage(AlleySimulation.Side side) {
        return side == AlleySimulation.Side.RIGHT ?
                "오른쪽 벽에 가까워졌습니다" : "왼쪽 벽에 가까워졌습니다";
    }

    private String utteranceId(int token) {
        return "warning-" + token;
    }

    private void stopTimedOutSpeech(int token) {
        if (token != generation || !busy) return;
        tts.stop();
        finish();
    }

    private void finish() {
        if (!busy) return;
        busy = false;
        next();
    }

    void cancel() {
        generation++;
        handler.removeCallbacksAndMessages(null);
        queue.clear();
        busy = false;
        stopOutput();
        manager.abandonAudioFocus(null);
    }

    private void stopOutput() {
        if (tone != null) tone.stopTone();
        if (tts != null) tts.stop();
    }

    void resume() {
        active = true;
    }

    void pause() {
        active = false;
        cancel();
    }

    void release() {
        pause();
        releaseTone();
        releaseSpeech();
    }

    private void releaseTone() {
        if (tone == null) return;
        tone.release();
        tone = null;
    }

    private void releaseSpeech() {
        if (tts == null) return;
        tts.shutdown();
        tts = null;
    }
}
