package com.example.pleostest

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 라벨 데이터 출처.
 *  - 앱 내장: assets/label_samples/index.json 에 적힌 검증된 장면(파일·시점·기대 결과)
 *  - PC 라벨링 서버(Hyundai Test/labeling_server): /api/files 로 폴더 탐색, /api/labels 로 라벨 JSON
 * 네트워크 함수는 블로킹이므로 백그라운드 스레드에서 호출합니다.
 */
class LabelRepository(private val context: Context, val serverUrl: String = BuildConfig.LABEL_SERVER_URL) {

    /** 장면 하나 = 라벨 파일 + 판단 시점 */
    data class Sample(
        val title: String,
        val file: String,
        val t: Double,
        val expect: String?,
        val fromServer: Boolean,
    ) {
        val sourceText get() = if (fromServer) "PC 라벨" else "앱 내장"
    }

    data class Entry(val name: String, val path: String, val dir: Boolean, val labeled: Boolean)
    data class Listing(val dir: String, val parent: String?, val entries: List<Entry>)

    private val clips = HashMap<String, LabelClip>()

    fun bundledSamples(): List<Sample> = try {
        val text = context.assets.open("label_samples/index.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        MiniJson.arr(MiniJson.obj(MiniJson.parse(text))["samples"]).map { MiniJson.obj(it) }.map {
            Sample(MiniJson.str(it["title"], MiniJson.str(it["file"])), MiniJson.str(it["file"]),
                MiniJson.num(it["t"]), MiniJson.str(it["expect"]).ifBlank { null }, false)
        }
    } catch (e: Exception) {
        android.util.Log.w("OnSenseLabel", "내장 라벨 목록 없음: ${e.message}")
        emptyList()
    }

    /** 내장 라벨 또는 서버 라벨을 읽어 캐시 */
    fun clip(sample: Sample): LabelClip {
        val key = (if (sample.fromServer) "server:" else "asset:") + sample.file
        clips[key]?.let { return it }
        val text = if (sample.fromServer) get("/api/labels?path=" + enc(sample.file))
            else context.assets.open("label_samples/" + sample.file).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return LabelClip.parse(text).also { clips[key] = it }
    }

    fun serverClip(path: String): LabelClip = clip(Sample(path, path, 0.0, null, true))

    /** 서버 폴더 목록 (라벨 있는 파일과 하위 폴더만) */
    fun list(dir: String): Listing {
        val o = MiniJson.obj(MiniJson.parse(get("/api/files?dir=" + enc(dir))))
        val entries = MiniJson.arr(o["entries"]).map { MiniJson.obj(it) }.mapNotNull {
            val type = MiniJson.str(it["type"])
            val e = Entry(MiniJson.str(it["name"]), MiniJson.str(it["path"]), type == "dir", it["labeled"] == true)
            if (e.dir || e.labeled) e else null
        }
        val parent = o["parent"]?.let { MiniJson.str(it) }
        return Listing(MiniJson.str(o["dir"]), parent, entries)
    }

    /** 서버 라벨 파일에서 판단이 필요한 순간(좌우·정면 객체가 가까운 시점)을 추천 */
    fun serverSamples(path: String, count: Int = 4): List<Sample> {
        val clip = serverClip(path)
        val name = path.substringAfterLast('/')
        return LabelSceneBuilder.suggestMoments(clip, count).map {
            Sample("$name · ${String.format(java.util.Locale.KOREA, "%.1f", it.t)}초 · ${it.summary}", path, it.t, null, true)
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(pathAndQuery: String): String {
        val c = URL(serverUrl.trimEnd('/') + pathAndQuery).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 3000; c.readTimeout = 15000
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code ${text.take(120)}")
            return text
        } finally { c.disconnect() }
    }
}
