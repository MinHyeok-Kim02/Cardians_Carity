package com.example.pleostest

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Explicit parked AVD integration diagnostic, NOT a claim of physical light application.
 * Uses existing AlleyWorld side geometry to generate the red warning; no production replay button.
 * Run only with real Console P/0 held throughout. Bridge independently verifies P/0 before each SET.
 */
@RunWith(AndroidJUnit4::class)
class AmbientLightParkedTest {
    @Test fun redWarningRequestAndOriginalColorReadback() {
        assumeTrue(Build.HARDWARE in listOf("ranchu","goldfish"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageManager.hasSystemFeature("android.hardware.type.automotive"))
        val gap = AlleyWorld(emptyList()).apply { restore(-2.4,4.0,0.0,false) }.clearance()
        val warning = SideClearanceWarning().update(gap.leftMeters,gap.rightMeters,0)
        assertEquals(SideClearanceWarning.Level.RED, warning.left.level)
        val requested = CountDownLatch(1)
        val restored = CountDownLatch(1)
        val bridge = AmbientLightBridge(context) { message ->
            Log.i("OnSensePleosF2", "PARKED_TEST_STATUS $message")
            if (message.contains("요청 #FFFF7272")) requested.countDown()
            if (message.contains("원래 색 복원 재조회 일치")) restored.countDown()
        }
        try {
            Log.i("OnSensePleosF2", "PARKED_TEST_SOURCE AlleyWorld.clearance left=${gap.leftMeters} right=${gap.rightMeters}; synthetic fixture; real Console remains parked")
            bridge.visible(true)
            bridge.warnings(warning.left.level,warning.right.level)
            bridge.enable(true)
            assertTrue("No independently read-back red request result. Check P/0 and OnSensePleosF2.",
                requested.await(45,TimeUnit.SECONDS))
            bridge.enable(false)
            assertTrue("Original color restoration not verified; inspect Console and saved journal",
                restored.await(30,TimeUnit.SECONDS))
            assertTrue(context.getSharedPreferences("onsense.feature2.restore",Context.MODE_PRIVATE).all.isEmpty())
        } finally {
            bridge.enable(false)
            bridge.close()
        }
    }
}
