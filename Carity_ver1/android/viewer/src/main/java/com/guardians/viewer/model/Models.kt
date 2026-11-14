package com.guardians.viewer.model

import org.json.JSONObject

/** 서버 폴더 목록 항목 */
data class FileEntry(
    val name: String,
    val path: String,
    val type: String,            // dir | video | image
    val size: Long,
    val labeled: Boolean,
    val labelSource: String?,    // sidecar | adjacent | legacy | null
    val previewReady: Boolean,
    val mediaCount: Int,
    val jobId: String?,
    val jobState: String? = null,     // queued | running
    val jobStep: String? = null,      // transcode | label
    val jobProgress: Double = 0.0,    // 0~1 (전체 단계 기준)
    val jobQueue: Int = 0,            // 대기열 순번
) {
    val isDir get() = type == "dir"

    companion object {
        fun from(o: JSONObject) = FileEntry(
            name = o.getString("name"),
            path = o.getString("path"),
            type = o.getString("type"),
            size = o.optLong("size", 0),
            labeled = o.optBoolean("labeled", false),
            labelSource = o.optString("label_source").takeIf { it.isNotEmpty() && it != "null" },
            previewReady = o.optBoolean("preview_ready", false),
            mediaCount = o.optInt("media_count", 0),
            jobId = o.optString("job").takeIf { it.isNotEmpty() && it != "null" },
            jobState = o.optString("job_state").takeIf { it.isNotEmpty() && it != "null" },
            jobStep = o.optString("job_step").takeIf { it.isNotEmpty() && it != "null" },
            jobProgress = o.optDouble("job_progress", 0.0).let { if (it.isNaN()) 0.0 else it },
            jobQueue = o.optInt("job_queue", 0),
        )
    }
}

/** YOLO 사람/차량 박스. 좌표는 0~1 정규화 */
data class Det(val id: Int, val cls: String, val conf: Float,
               val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    val group: String
        get() = when (cls) {
            "car", "bus", "truck" -> "vehicle"
            "person" -> "person"
            "bicycle", "motorcycle" -> "rider"
            else -> "other"
        }
}

/** 차선 (pts: x0,y0,x1,y1,... 정규화) */
data class Lane(val side: String, val pts: FloatArray, val conf: Float)

data class LabelFrame(val t: Double, val objects: List<Det>, val lanes: List<Lane>)

/** 라벨 JSON (서버 version 2 / 오전 작업 data/raw 형식 모두 지원) */
class LabelSet(
    val kind: String,
    val width: Int,
    val height: Int,
    val sampleFps: Double,
    val duration: Double,
    val model: String,
    val device: String,
    val gpuName: String?,
    val labelSource: String,
    val created: String,
    val frames: List<LabelFrame>,
) {
    private val times = DoubleArray(frames.size) { frames[it].t }
    private val holdSec = if (sampleFps > 0) 1.5 / sampleFps else 0.5

    val sourceText: String
        get() = when (labelSource) {
            "legacy" -> "사전 라벨(오전 작업 data/raw)"
            "sidecar" -> "서버 라벨링 결과"
            "adjacent" -> "영상 옆 .labels.json"
            else -> labelSource
        }

    val modelText: String
        get() {
            val m = model.removeSuffix(".pt")
            val dev = when {
                device.startsWith("cuda") -> "GPU" + (gpuName?.let { " · $it" } ?: "")
                device == "cpu" -> "CPU"
                else -> device
            }
            return "$m · $dev"
        }

    /** 시각 t(초)에 표시할 객체: 앞뒤 샘플 프레임 사이를 같은 추적 ID 기준으로 선형 보간 */
    fun objectsAt(t: Double): List<Det> {
        if (frames.isEmpty()) return emptyList()
        if (kind == "image") return frames[0].objects
        val i = floorIndex(t)
        if (i < 0) return emptyList()
        val a = frames[i]
        if (t - a.t > holdSec) return emptyList()
        val b = frames.getOrNull(i + 1) ?: return a.objects
        val span = b.t - a.t
        if (span <= 0 || span > holdSec * 2) return a.objects
        val r = ((t - a.t) / span).toFloat().coerceIn(0f, 1f)
        val byId = b.objects.filter { it.id >= 0 }.associateBy { it.id }
        return a.objects.map { o ->
            val n = if (o.id >= 0) byId[o.id] else null
            if (n == null) o else o.copy(
                x1 = o.x1 + (n.x1 - o.x1) * r, y1 = o.y1 + (n.y1 - o.y1) * r,
                x2 = o.x2 + (n.x2 - o.x2) * r, y2 = o.y2 + (n.y2 - o.y2) * r)
        }
    }

    fun lanesAt(t: Double): List<Lane> {
        if (frames.isEmpty()) return emptyList()
        if (kind == "image") return frames[0].lanes
        val i = floorIndex(t)
        if (i < 0 || t - frames[i].t > holdSec) return emptyList()
        return frames[i].lanes
    }

    private fun floorIndex(t: Double): Int {
        var lo = 0
        var hi = times.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (times[mid] <= t + 1e-6) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    companion object {
        fun parse(o: JSONObject): LabelSet {
            val arr = o.getJSONArray("frames")
            val frames = ArrayList<LabelFrame>(arr.length())
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val objs = ArrayList<Det>()
                f.optJSONArray("objects")?.let { oa ->
                    for (j in 0 until oa.length()) {
                        val d = oa.getJSONObject(j)
                        val b = d.getJSONArray("box")
                        objs.add(Det(d.optInt("id", -1), d.optString("cls", "?"), d.optDouble("conf", 0.0).toFloat(),
                            b.getDouble(0).toFloat(), b.getDouble(1).toFloat(),
                            b.getDouble(2).toFloat(), b.getDouble(3).toFloat()))
                    }
                }
                val lanes = ArrayList<Lane>()
                f.optJSONArray("lanes")?.let { la ->
                    for (j in 0 until la.length()) {
                        val l = la.getJSONObject(j)
                        val p = l.getJSONArray("pts")
                        val pts = FloatArray(p.length() * 2)
                        for (k in 0 until p.length()) {
                            val xy = p.getJSONArray(k)
                            pts[k * 2] = xy.getDouble(0).toFloat()
                            pts[k * 2 + 1] = xy.getDouble(1).toFloat()
                        }
                        lanes.add(Lane(l.optString("side", "?"), pts, l.optDouble("conf", 0.0).toFloat()))
                    }
                }
                frames.add(LabelFrame(f.optDouble("t", 0.0), objs, lanes))
            }
            frames.sortBy { it.t }
            val gpu = o.optString("gpu_name")
            return LabelSet(
                kind = o.optString("kind", "video"),
                width = o.optInt("width", 0),
                height = o.optInt("height", 0),
                sampleFps = o.optDouble("sample_fps", 10.0),
                duration = o.optDouble("duration", frames.lastOrNull()?.t ?: 0.0),
                model = o.optString("model", "?"),
                device = o.optString("device", "unknown"),
                gpuName = gpu.takeIf { it.isNotEmpty() && it != "null" },
                labelSource = o.optString("label_source", "sidecar"),
                created = o.optString("created", ""),
                frames = frames,
            )
        }
    }
}
