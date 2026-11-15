package com.example.pleostest

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import java.util.Locale

/** Foreground, read-only Pleos location experiment. Does not use mock fixes or send data to a map API. */
class PleosLocationProbe(context: Context, private val output: (String, Boolean) -> Unit) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var sdk: FusedLocation? = null
    private var listener: FusedLocationListener? = null
    private var generation = 0
    private var startedAt = 0L
    private var receivedAt = 0L
    private var updates = 0
    private var changes = 0
    private var previousStream: LocationData? = null
    private var last: LocationData? = null
    private var lastSource = ""
    private var error = ""
    private var timeoutLogged = false
    val active get() = sdk != null

    fun isSupported(): Boolean = try {
        FusedLocation(context).isSupported().also {
            Log.i(TAG,"LOCATION_SUPPORT supported=$it")
        }
    } catch(e: Exception) {
        Log.e(TAG,"LOCATION_SUPPORT_FAILED",e); false
    }

    fun start() {
        if (active) return
        val granted = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
        Log.i(TAG,"LOCATION_PERMISSION name=$PERMISSION granted=$granted")
        if (!isSupported()) { output("위치 SDK 서비스 미지원/조회 불가 · Logcat 확인",false); return }
        if (!granted) { output("위치 권한 필요 · 정차 후 '위치 수신 시작'을 눌러 허용하세요",false); return }
        val current = FusedLocation(context)
        val token = ++generation
        sdk=current
        startedAt=SystemClock.elapsedRealtime(); receivedAt=0
        updates=0; changes=0; previousStream=null; last=null; error=""; timeoutLogged=false
        fun callback(source: String) = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                main.post {
                    if (generation != token || sdk !== current) return@post
                    receive(source,location)
                }
            }
            override fun onError(exception: Exception) {
                main.post {
                    if (generation != token || sdk !== current) return@post
                    error = "$source 오류: ${exception.message}"
                    Log.e(TAG,"LOCATION_ERROR source=$source",exception)
                    refresh()
                }
            }
        }
        val stream = callback("UPDATE")
        listener=stream
        try {
            current.initialize()
            Log.i(TAG,"LOCATION_INITIALIZE_REQUESTED; service connection not yet proven")
            current.getLastKnownLocation(callback("LAST_KNOWN"))
            current.registerFusedLocationCallback(stream)
            Log.i(TAG,"LOCATION_SUBSCRIBE_REQUESTED; success requires actual non-null callbacks")
            refresh()
            main.postDelayed(ticker,1000)
        } catch(e: Exception) {
            Log.e(TAG,"LOCATION_START_FAILED",e)
            stop()
            output("위치 초기화/구독 실패: ${e.message}",false)
        }
    }

    private fun receive(source: String, location: LocationData?) {
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG,"LOCATION_PERMISSION_REVOKED")
            stop(); output("위치 권한이 해제돼 수신을 중지했습니다",false); return
        }
        if (location == null) {
            Log.w(TAG,"LOCATION_NULL source=$source; no position success")
            error="$source: 위치 없음(null)"
            refresh(); return
        }
        if (!location.latitude.isFinite() || !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            Log.w(TAG,"LOCATION_INVALID source=$source lat=${location.latitude} lon=${location.longitude}")
            error="$source: 유효하지 않은 좌표"; refresh(); return
        }
        Log.i(TAG,"LOCATION_RECEIVED source=$source latitude=${location.latitude} longitude=${location.longitude} headingRaw=${location.heading} altitude=${location.altitude} confidence=${location.confidence} sdkTimestampMs=${location.timestampMillis}")
        if(source=="LAST_KNOWN" && updates>0) return // A delayed cached response cannot replace a live update.
        if(source=="UPDATE") {
            updates++
            previousStream?.let { previous ->
                if(previous.latitude!=location.latitude || previous.longitude!=location.longitude) {
                    changes++
                    Log.i(TAG,"LOCATION_COORDINATE_CHANGED count=$changes latitude=${location.latitude} longitude=${location.longitude}; change is not proof of real vehicle motion")
                }
            }
            previousStream=location
        }
        last=location; lastSource=source; receivedAt=SystemClock.elapsedRealtime(); error=""
        refresh()
    }

    private fun refresh() {
        if (!active) return
        val now=SystemClock.elapsedRealtime()
        val data=last
        val age=if(receivedAt==0L) null else (now-receivedAt)/1000
        if(updates==0 && now-startedAt>=10000 && !timeoutLogged) {
            timeoutLogged=true
            Log.w(TAG,"LOCATION_UPDATE_TIMEOUT waitedMs=${now-startedAt}; updates=0; cached fixes do not prove ongoing subscription")
        }
        val text = if(data==null) {
            "위치 SDK 수신 대기 · ${(now-startedAt)/1000}초 · UPDATE ${updates}회" +
                if(error.isEmpty()) "" else "\n$error"
        } else String.format(Locale.US,
            "위치 SDK %s · 위도 %.6f · 경도 %.6f · heading(raw) %.2f\nconfidence=%s · SDK시각(ms)=%d · UPDATE %d회 · 좌표변경 %d회 · 수신 %s초 전",
            lastSource,data.latitude,data.longitude,data.heading,data.confidence.toString(),
            data.timestampMillis,updates,changes,age) +
            (if(age!=null && age>=10) " · 최근 수신 지연" else "") +
            (if(error.isNotEmpty()) "\n$error" else "")
        output(text,true)
    }

    private val ticker=object: Runnable {
        override fun run() {
            if(!active) return
            refresh()
            main.postDelayed(this,1000)
        }
    }

    fun stop() {
        val current=sdk ?: return
        sdk=null; generation++
        main.removeCallbacks(ticker)
        val stream=listener; listener=null
        try {
            if(stream!=null && context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED)
                current.unregisterFusedLocationCallback(stream)
        } catch(e: Exception) { Log.w(TAG,"LOCATION_UNSUBSCRIBE_FAILED",e) }
        finally {
            try { current.release() } catch(e: Exception) { Log.w(TAG,"LOCATION_RELEASE_FAILED",e) }
        }
        Log.i(TAG,"LOCATION_STOPPED validUpdates=$updates coordinateChanges=$changes")
        last=null; previousStream=null
        output("위치 수신 중지 · 외부 전송 없음",false)
    }

    companion object {
        const val PERMISSION="pleos.car.permission.FUSED_LOCATION"
        const val TAG="CarityLocation"
    }
}
