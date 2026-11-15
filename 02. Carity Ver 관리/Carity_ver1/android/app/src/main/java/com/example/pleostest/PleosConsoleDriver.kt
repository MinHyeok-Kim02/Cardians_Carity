package com.example.pleostest

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/** Debug emulator loopback only. HTTP controls -> emulator -> AAOS/Pleos input. */
class PleosConsoleDriver(context: Context, private val update: (String) -> Unit) {
    private val permitted = Build.HARDWARE in listOf("ranchu","goldfish") &&
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val worker=Executors.newSingleThreadScheduledExecutor()
    private val main=Handler(Looper.getMainLooper())
    private val generation=AtomicLong()
    private val model=ConsoleDriveModel()
    @Volatile private var readAt=0L
    @Volatile var gear: ConsoleDriveModel.Gear?=null; private set
    @Volatile private var selected: Pair<ConsoleDriveModel.Gear,Double?>?=null
    private var session: String?=null
    private var previousMode: String?=null
    private var ownsControls=false
    private var lastLoggedSteering: Int?=null
    private var lastLoggedControls: String?=null
    val connected get()=gear!=null && SystemClock.elapsedRealtime()-readAt<2500
    fun press(key: AlleyKeyboardDriver.Control) { if(connected) model.press(key) }
    fun release(key: AlleyKeyboardDriver.Control) { model.release(key) }
    fun select(value: ConsoleDriveModel.Gear, speed: Double?) { if(connected) selected=value to speed }
    private fun report(token: Long, text: String) { main.post { if(generation.get()==token) update(text) } }
    private fun request(path: String, method: String="GET"): String {
        val connection=URL("http://127.0.0.1:8088"+path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method
            connection.connectTimeout=1200; connection.readTimeout=1200
            connection.setRequestProperty("Connection","close")
            val status=connection.responseCode
            val body=(if(status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "Console HTTP "+status+" "+body.take(120) }
            return body
        } finally { connection.disconnect() }
    }
    private fun state()=JSONObject(request("/vehicle/state"))
    private fun controls(command: ConsoleDriveModel.Command) {
        val requestedAt=SystemClock.elapsedRealtime()
        val commandKey = "${Math.round(command.speedKph)}/${command.accelerator}/${command.brake}/${command.steering}"
        if (lastLoggedControls != commandKey) {
            Log.i("CarTalkKeyboard", "CONTROL_REQUEST speedKmh=${Math.round(command.speedKph)} accelerator=${command.accelerator} brake=${command.brake} steering=${command.steering}")
            lastLoggedControls=commandKey
        }
        request("/vehicle/controls?speed="+Math.round(command.speedKph).toString()+
            "&accelerator="+command.accelerator+"&brake="+command.brake+"&steering="+command.steering,"PUT")
        if(lastLoggedSteering!=command.steering) {
            Log.d("CarTalkConsole","STEERING_REQUEST value="+command.steering+" requestedAt="+requestedAt)
            lastLoggedSteering=command.steering
        }
    }
    private fun read(s: JSONObject) {
        check(s.getJSONObject("gear").optBoolean("bridge_ready") &&
            s.getJSONObject("controls").getJSONObject("dds_ready").optBoolean("all")) { "DDS 연결 대기" }
        val actual=s.getJSONObject("gear").getJSONObject("actual").getString("value")
        gear=ConsoleDriveModel.Gear.values().firstOrNull { it.wire==actual }
        check(gear!=null) { "기어 미확인" }
        readAt=SystemClock.elapsedRealtime()
    }
    private fun cleanup() {
        try {
            if(ownsControls) {
                controls(ConsoleDriveModel.Command(0.0,0,100,0))
                val stopped=state().getJSONObject("controls").getDouble("speed_kph")
                check(abs(stopped)<.01)
                request("/vehicle/gear?gear=Park","PUT")
                check(state().getJSONObject("gear").getJSONObject("actual").getString("value")=="Park")
                if(previousMode!=null) request("/vehicle/hvac/mode?mode="+previousMode,"PUT")
                Log.i("CarTalkConsole","STOP_VERIFIED speed=0 gear=P")
            }
        } catch(e: Exception) { Log.e("CarTalkConsole","STOP_NOT_VERIFIED; inspect Console",e) }
        finally {
            session?.let { try { request("/browser/session?client="+it+"&closing=1","POST") } catch(_:Exception) {} }
            session=null; previousMode=null; ownsControls=false; gear=null; readAt=0; selected=null; model.clear()
        }
    }
    fun start() {
        if(!permitted) { update("debug Pleos 에뮬레이터 전용"); return }
        val token=generation.incrementAndGet()
        worker.execute {
            cleanup()
            try {
                check(request("/").contains("Pleos Connect Virtual Console")) { "Console 페이지 미확인" }
                session="alley-app-"+SystemClock.elapsedRealtime()
                request("/browser/session?client="+session,"POST")
                var initial: JSONObject?=null
                repeat(15) {
                    if(initial==null) {
                        if(generation.get()!=token) return@execute
                        try { val s=state(); read(s); initial=s } catch(_:Exception) { Thread.sleep(200) }
                    }
                }
                val s=checkNotNull(initial) { "DDS 연결 실패" }
                check(gear==ConsoleDriveModel.Gear.P && abs(s.getJSONObject("controls").getDouble("speed_kph"))<.01) {
                    "속도0·P단에서 연결하세요"
                }
                previousMode=s.getJSONObject("hvac").optString("dds_mode","subscribe_hvac")
                request("/vehicle/hvac/mode?mode=subscribe_hvac_base","PUT")
                ownsControls=true
                model.select(ConsoleDriveModel.Gear.P,0.0)
                controls(model.tick(.1))
                report(token,"Console 연결됨 · 기어를 선택하세요")
                var previous=SystemClock.elapsedRealtime()
                var heartbeat=0L
                var lastRead=0L
                val loop=object:Runnable {
                    override fun run() {
                        if(generation.get()!=token) return
                        try {
                            val now=SystemClock.elapsedRealtime()
                            if(now-heartbeat>=800) { request("/browser/session?client="+session,"POST"); heartbeat=now }
                            val pending=selected; selected=null
                            if(pending!=null) {
                                val actual=state().getJSONObject("controls").getDouble("speed_kph")/3.6
                                check(pending.second!=null) { "SDK 속도 미확인" }
                                if(pending.first!=ConsoleDriveModel.Gear.N)
                                    check(abs(actual)<.01 && abs(pending.second!!)<.01) { "정지 후 기어를 전환하세요" }
                                request("/vehicle/gear?gear="+pending.first.wire,"PUT")
                                read(state())
                                check(gear==pending.first) { "기어 변경 미확인" }
                                check(model.select(pending.first,actual))
                                report(token,"Console 기어 "+pending.first.name+" 확인")
                            }
                            val dt=((now-previous)/1000.0).coerceIn(.001,.1); previous=now
                            if(generation.get()!=token) return
                            val command=model.tick(dt)
                            controls(command)
                            if(now-lastRead>=300) {
                                val current=state(); read(current)
                                val c=current.getJSONObject("controls")
                                check(abs(c.getDouble("speed_kph")-command.speedKph)<=1.1 &&
                                    abs(c.getDouble("steering_deg")-command.steering)<=2) { "Console 재조회 불일치 · 다른 탭 조작을 중지하세요" }
                                lastRead=now
                            }
                            if(generation.get()==token) worker.schedule(this,100,TimeUnit.MILLISECONDS)
                        } catch(e:Exception) {
                            gear=null; model.clear(); cleanup()
                            report(token,"연결 중단: "+(e.message?:"미확인"))
                        }
                    }
                }
                worker.execute(loop)
            } catch(e:Exception) {
                cleanup(); report(token,"Console 연결 실패: "+(e.message?:"미확인"))
            }
        }
    }
    fun stop() {
        generation.incrementAndGet(); gear=null; model.clear(); selected=null
        if(!worker.isShutdown) worker.execute { cleanup() }
    }
    fun close() { stop(); worker.shutdown() }
}
