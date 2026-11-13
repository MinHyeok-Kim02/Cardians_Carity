package com.example.testcar

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

// 안드로이드 기본 화면 기능(AppCompatActivity)과 글자를 읽어주는 기능(OnInitListener)을 가져옵니다.
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private val wheelBase: Double = 2.6 // 내 차의 앞바퀴와 뒷바퀴 사이 거리(축거) - 아반떼 기준 약 2.6m로 가정

    // 앱이 처음 켜질 때 가장 먼저 실행되는 곳입니다.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 사람 목소리로 안내해 줄 TTS 엔진 준비하기
        tts = TextToSpeech(this, this)

        // (나중에는 여기에 자동차 속도와 핸들 각도를 감시하는 코드가 들어갑니다)
    }

    // TTS 준비가 성공적으로 끝났을 때 한국어로 설정하는 부분입니다.
    // TTS 음성 엔진이 준비 완료되었을 때 실행되는 곳입니다.
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.KOREAN

            // 1. 내륜차 계산기로 궤적 편차 수치 계산 (30도 꺾었을 때)
            val testDeviation = calculateInnerWheelDeviation(wheelBase, 30.0)

            // 2. 계산된 testDeviation 수치를 음성 멘트에 활용하여 사용(Use)하게 만듭니다!
            val deviationCm = (testDeviation * 100).toInt() // m 단위를 cm 단위로 변환

            triggerZeroLookCoaching("RIGHT_REAR")
        }
    }

    // [기능 1] 공모전 기획서에 있던 수학 공식! 내륜차 궤적 편차를 계산하는 마법의 계산기입니다.
    private fun calculateInnerWheelDeviation(wheelBase: Double, angleDegree: Double): Double {
        // 핸들을 꺾지 않고 직진 중(0도)일 때는 계산할 필요가 없으니 0을 반환합니다.
        if (abs(angleDegree) < 1.0) return 0.0

        // 각도를 계산하기 쉬운 라디안 값으로 바꿉니다.
        val theta = angleDegree * (PI / 180.0)

        // ΔR = L * (1/sinθ - 1/tanθ) 공식 적용
        val deltaR = wheelBase * (1.0 / sin(theta) - 1.0 / tan(theta))

        return abs(deltaR) // 무조건 양수(절대값)로 편차 거리를 알려줍니다.
    }

    // [기능 2] 위험이 감지되었을 때 화면을 보지 않도록 우측 스피커로만 코칭해 주는 기능입니다.
    private fun triggerZeroLookCoaching(direction: String) {
        val message = if (direction == "RIGHT_REAR") {
            "우측 뒷바퀴가 연석에 너무 가깝습니다. 핸들을 왼쪽으로 반 바퀴 풀고, 앞으로 1미터만 더 나간 뒤 다시 끝까지 꺾으세요."
        } else {
            "좌측 뒷바퀴 주의. 핸들을 우측으로 풀고 전진하세요."
        }

        // 오디오 패닝(소리 방향 조절): 1.0f를 주면 우측 스피커로 소리가 쏠립니다.
        val params = Bundle()
        if (direction == "RIGHT_REAR") {
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_PAN, 1.0f)
        }

        // 준비된 멘트를 우측 스피커 방향으로 읽어줍니다!
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, params, "ZeroLook_ID")
    }

    // 앱이 꺼질 때 목소리 엔진도 깔끔하게 꺼줍니다.
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}