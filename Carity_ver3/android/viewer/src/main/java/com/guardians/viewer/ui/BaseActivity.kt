package com.guardians.viewer.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 공통 화면 처리
 * 1) UI 배율: Pleos 차량 화면(2560x1440, 160dpi)은 선언 밀도가 낮아 글자·버튼이 매우 작게 보임.
 *    가장 짧은 변이 TARGET_SW_DP 보다 크면 밀도를 올려 전체 UI 를 비례 확대 (일반 태블릿은 그대로).
 * 2) 시스템 바: targetSdk 35+ 는 edge-to-edge 강제 → Pleos 상단 상태바·하단 공조 독과 겹치지 않게 inset 만큼 여백.
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val cfg = Configuration(newBase.resources.configuration)
        val sw = cfg.smallestScreenWidthDp
        if (sw > TARGET_SW_DP) {
            val f = sw.toFloat() / TARGET_SW_DP
            cfg.densityDpi = (cfg.densityDpi * f).toInt()
            cfg.smallestScreenWidthDp = TARGET_SW_DP
            cfg.screenWidthDp = (cfg.screenWidthDp / f).toInt()
            cfg.screenHeightDp = (cfg.screenHeightDp / f).toInt()
            super.attachBaseContext(newBase.createConfigurationContext(cfg))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    /** root 의 기존 padding 에 시스템 바/컷아웃 inset 을 더한다 */
    protected fun applySystemBarInsets(root: View) {
        val l = root.paddingLeft
        val t = root.paddingTop
        val r = root.paddingRight
        val b = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val s = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(l + s.left, t + s.top, r + s.right, b + s.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    companion object {
        const val TARGET_SW_DP = 880
    }
}
