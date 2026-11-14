package com.guardians.viewer.net

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * PC 라벨링 서버(labeling_server/server.py) HTTP 클라이언트.
 * 모든 메서드는 블로킹이므로 [Bg.run] 등 백그라운드 스레드에서 호출하세요.
 */
class ServerApi(baseUrl: String) {
    val base: String = baseUrl.trim().trimEnd('/').let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "http://$it"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun url(path: String, params: Map<String, Any?> = emptyMap()): String {
        val q = params.filterValues { it != null }.entries
            .joinToString("&") { "${enc(it.key)}=${enc(it.value.toString())}" }
        return if (q.isEmpty()) "$base$path" else "$base$path?$q"
    }

    private fun open(u: String, method: String = "GET", readTimeoutMs: Int = 10_000): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 5_000
        c.readTimeout = readTimeoutMs
        c.useCaches = false
        return c
    }

    private fun readBody(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val detail = try { JSONObject(text).optString("detail", text) } catch (e: Exception) { text }
            throw ApiException(code, "HTTP $code: $detail")
        }
        return text
    }

    fun getJson(path: String, params: Map<String, Any?> = emptyMap(), readTimeoutMs: Int = 10_000): JSONObject {
        val c = open(url(path, params), "GET", readTimeoutMs)
        try {
            return JSONObject(readBody(c))
        } finally {
            c.disconnect()
        }
    }

    fun postJson(path: String, params: Map<String, Any?> = emptyMap()): JSONObject {
        val c = open(url(path, params), "POST")
        try {
            c.doOutput = true
            c.setFixedLengthStreamingMode(0)
            c.outputStream.close()
            return JSONObject(readBody(c))
        } finally {
            c.disconnect()
        }
    }

    fun health() = getJson("/api/health")
    fun files(dir: String) = getJson("/api/files", mapOf("dir" to dir))
    fun info(path: String) = getJson("/api/info", mapOf("path" to path))
    fun prepare(path: String, relabel: Boolean) =
        postJson("/api/prepare", mapOf("path" to path, "relabel" to if (relabel) 1 else 0))
    fun job(id: String) = getJson("/api/jobs/$id")
    fun cancelJob(id: String) = postJson("/api/jobs/$id/cancel")
    fun labels(path: String) = getJson("/api/labels", mapOf("path" to path), readTimeoutMs = 60_000)

    /** 재생용 파일을 [dest] 로 내려받기. onProgress(받은 바이트, 전체 바이트(-1=모름)). */
    fun downloadMedia(path: String, dest: File, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean) {
        val c = open(url("/api/media", mapOf("path" to path)), "GET", 60_000)
        try {
            val code = c.responseCode
            if (code !in 200..299) readBody(c)
            val total = c.contentLengthLong
            dest.parentFile?.mkdirs()
            val tmp = File(dest.path + ".part")
            c.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        if (cancelled()) throw InterruptedException("취소됨")
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 512 * 1024) { onProgress(done, total); last = done }
                    }
                    onProgress(done, total)
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw IllegalStateException("파일 저장 실패")
        } finally {
            c.disconnect()
        }
    }

    /** 태블릿 로컬 파일 업로드 → 서버 _uploads/ 상대경로 반환. */
    fun upload(input: InputStream, name: String, size: Long, onProgress: (Long, Long) -> Unit): String {
        val c = open(url("/api/upload", mapOf("name" to name)), "PUT", 120_000)
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/octet-stream")
            if (size > 0) c.setFixedLengthStreamingMode(size) else c.setChunkedStreamingMode(256 * 1024)
            c.outputStream.use { out ->
                val buf = ByteArray(256 * 1024)
                var done = 0L
                var last = 0L
                input.use {
                    while (true) {
                        val n = it.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - last > 512 * 1024) { onProgress(done, size); last = done }
                    }
                }
                onProgress(done, size)
            }
            return JSONObject(readBody(c)).getString("path")
        } finally {
            c.disconnect()
        }
    }
}

/** 아주 단순한 백그라운드 실행기 (외부 라이브러리 없이). */
object Bg {
    private val pool = Executors.newCachedThreadPool()
    val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, done: (Result<T>) -> Unit) {
        pool.execute {
            val r = runCatching(work)
            main.post { done(r) }
        }
    }

    fun ui(block: () -> Unit) = main.post(block)
}
