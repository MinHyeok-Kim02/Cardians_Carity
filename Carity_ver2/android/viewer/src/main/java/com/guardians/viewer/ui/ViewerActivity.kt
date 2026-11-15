package com.guardians.viewer.ui

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import com.google.android.material.button.MaterialButton
import com.guardians.viewer.R
import com.guardians.viewer.model.LabelSet
import com.guardians.viewer.net.Bg
import com.guardians.viewer.net.ServerApi
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * 영상/이미지 화면.
 *  - 라벨된 파일: 라벨 JSON + 재생 파일을 받아 박스·차선을 겹쳐 재생
 *  - 미라벨 파일: 로딩 화면으로 전환 → PC 서버에서 YOLO 라벨링(GPU=YOLO11s / CPU=YOLOv8) → 완료 후 재생
 */
class ViewerActivity : BaseActivity() {

    private lateinit var api: ServerApi
    private lateinit var path: String
    private lateinit var name: String
    private lateinit var kind: String

    private lateinit var videoView: VideoView
    private lateinit var imageView: ImageView
    private lateinit var overlay: OverlayView
    private lateinit var tvTitle: TextView
    private lateinit var tvSource: TextView
    private lateinit var tvCounts: TextView
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnSpeed: MaterialButton
    private lateinit var btnRelabel: MaterialButton
    private lateinit var tvTime: TextView
    private lateinit var seek: SeekBar
    private lateinit var bottomBar: LinearLayout

    private lateinit var loadingPanel: LinearLayout
    private lateinit var pbSpinner: ProgressBar
    private lateinit var pbLoad: ProgressBar
    private lateinit var tvLoadTitle: TextView
    private lateinit var tvLoadSub: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvLoadDetail: TextView
    private lateinit var tvLoadDevice: TextView
    private lateinit var btnCancel: MaterialButton

    private var labels: LabelSet? = null
    private var player: MediaPlayer? = null
    private var jobId: String? = null
    private var polling = false
    private var destroyed = false
    private var syncing = false
    private var userSeeking = false
    private var speed = 1.0f
    private var lastUiMs = 0L
    private var relabelRequested = false

    // ---- 연속 재생(재생 목록)
    private data class Item(val path: String, val name: String, val kind: String)
    private var items: List<Item> = emptyList()
    private var index = 0
    private var gen = 0                       // 파일 전환 시 이전 비동기 콜백 무시용
    private var aheadRequested = false
    private val advance = Runnable { next(auto = true) }
    private lateinit var btnPrev: MaterialButton
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var tvQueue: TextView
    private val isPlaylist get() = items.size > 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)
        applySystemBarInsets(findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))
        api = ServerApi(intent.getStringExtra(EXTRA_SERVER) ?: MainActivity.DEFAULT_SERVER)
        val paths = intent.getStringArrayExtra(EXTRA_PLAYLIST_PATHS)
        items = if (paths != null && paths.isNotEmpty()) {
            val names = intent.getStringArrayExtra(EXTRA_PLAYLIST_NAMES)
            val kinds = intent.getStringArrayExtra(EXTRA_PLAYLIST_KINDS)
            paths.indices.map { i ->
                Item(paths[i], names?.getOrNull(i) ?: paths[i].substringAfterLast('/'), kinds?.getOrNull(i) ?: "video")
            }
        } else {
            val p = intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return }
            listOf(Item(p, intent.getStringExtra(EXTRA_NAME) ?: p.substringAfterLast('/'),
                intent.getStringExtra(EXTRA_KIND) ?: "video"))
        }
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, items.size - 1)

        videoView = findViewById(R.id.videoView)
        imageView = findViewById(R.id.imageView)
        overlay = findViewById(R.id.overlay)
        tvTitle = findViewById(R.id.tvTitle)
        tvSource = findViewById(R.id.tvSource)
        tvCounts = findViewById(R.id.tvCounts)
        btnPlay = findViewById(R.id.btnPlay)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnRelabel = findViewById(R.id.btnRelabel)
        tvTime = findViewById(R.id.tvTime)
        seek = findViewById(R.id.seek)
        bottomBar = findViewById(R.id.bottomBar)
        loadingPanel = findViewById(R.id.loadingPanel)
        pbSpinner = findViewById(R.id.pbSpinner)
        pbLoad = findViewById(R.id.pbLoad)
        tvLoadTitle = findViewById(R.id.tvLoadTitle)
        tvLoadSub = findViewById(R.id.tvLoadSub)
        tvSteps = findViewById(R.id.tvSteps)
        tvLoadDetail = findViewById(R.id.tvLoadDetail)
        tvLoadDevice = findViewById(R.id.tvLoadDevice)
        btnCancel = findViewById(R.id.btnCancel)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnSkip = findViewById(R.id.btnSkip)
        tvQueue = findViewById(R.id.tvQueue)
        btnPrev.setOnClickListener { prev() }
        btnNext.setOnClickListener { next(auto = false) }
        btnSkip.setOnClickListener { next(auto = false) }
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        btnCancel.setOnClickListener { cancelAndClose() }
        btnRelabel.setOnClickListener { confirmRelabel() }
        btnPlay.setOnClickListener { togglePlay() }
        btnSpeed.setOnClickListener { cycleSpeed() }
        bindToggle(R.id.cbVehicle) { overlay.showVehicle = it }
        bindToggle(R.id.cbPerson) { overlay.showPerson = it }
        bindToggle(R.id.cbLane) { overlay.showLane = it }
        bindToggle(R.id.cbIds) { overlay.showIds = it }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    val d = videoView.duration
                    if (d > 0) {
                        videoView.seekTo((d.toLong() * p / 1000).toInt())
                        renderAt(videoView.currentPosition / 1000.0)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar) { userSeeking = false }
        })
        // 실제 영상이 그려진 영역(VideoView 가 비율 맞춰 줄어든 영역)에 오버레이 정렬
        videoView.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            if (v.visibility == View.VISIBLE) overlay.setContentRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
        }

        loadItem(index)
    }

    // ================================================================ 재생 목록
    private fun loadItem(i: Int) {
        Bg.main.removeCallbacks(advance)
        gen++
        polling = false
        jobId = null
        index = i
        val it = items[i]
        path = it.path
        name = it.name
        kind = it.kind
        tvTitle.text = name
        tvSource.text = ""
        tvCounts.text = ""
        updateQueueUi()
        start(relabel = false)
    }

    private fun updateQueueUi() {
        val vis = if (isPlaylist) View.VISIBLE else View.GONE
        btnPrev.visibility = vis
        btnNext.visibility = vis
        tvQueue.visibility = vis
        btnSkip.visibility = if (isPlaylist && index < items.size - 1) View.VISIBLE else View.GONE
        tvQueue.text = "${index + 1} / ${items.size}"
        btnPrev.isEnabled = index > 0
        btnNext.isEnabled = index < items.size - 1
    }

    private fun next(auto: Boolean) {
        if (index < items.size - 1) {
            loadItem(index + 1)
        } else if (auto && isPlaylist) {
            tvQueue.text = "${items.size} / ${items.size} · 연속 재생 끝"
        }
    }

    private fun prev() {
        if (index > 0) loadItem(index - 1)
    }

    /** 연속 재생 중이면 ms 뒤 다음 파일로 */
    private fun scheduleAdvance(ms: Long) {
        if (!isPlaylist || index >= items.size - 1) return
        Bg.main.removeCallbacks(advance)
        Bg.main.postDelayed(advance, ms)
    }

    /** 뒤 파일들을 서버 큐에 미리 등록 → 앞 파일을 보는 동안 라벨링/변환이 진행됨 */
    private fun requestAhead() {
        if (!isPlaylist || aheadRequested) return
        aheadRequested = true
        val rest = items.drop(index + 1)
        Bg.run({ rest.forEach { runCatching { api.prepare(it.path, false) } } }) { }
    }

    private fun bindToggle(id: Int, set: (Boolean) -> Unit) {
        val cb = findViewById<CheckBox>(id)
        cb.setOnCheckedChangeListener { _, c -> set(c); overlay.invalidate() }
    }

    // ================================================================ 준비 흐름
    private fun start(relabel: Boolean) {
        relabelRequested = relabel
        stopPlayback()
        showLoading("파일 확인 중", "PC 서버에 라벨 여부를 묻는 중…", indeterminate = true)
        val g = gen
        Bg.run({ api.prepare(path, relabel) }) { r ->
            if (destroyed || g != gen) return@run
            requestAhead()
            r.onSuccess { o ->
                if (o.optBoolean("ready")) {
                    fetchAndShow(alreadyLabeled = true)
                } else {
                    val job = o.getJSONObject("job")
                    jobId = job.getString("id")
                    updateJob(job)
                    pollJob()
                }
            }.onFailure { showError("서버 요청 실패", it.message ?: it.toString()) }
        }
    }

    private fun pollJob() {
        val id = jobId ?: return
        if (polling || destroyed) return
        polling = true
        val g = gen
        Bg.main.postDelayed({
            if (g != gen) return@postDelayed
            Bg.run({ api.job(id) }) { r ->
                if (destroyed || g != gen) return@run
                polling = false
                r.onSuccess { j ->
                    updateJob(j)
                    when (j.optString("state")) {
                        "done" -> { jobId = null; fetchAndShow(alreadyLabeled = false) }
                        "error" -> { jobId = null; showError("처리 실패", j.optString("error")) }
                        "canceled" -> { jobId = null; if (isPlaylist) next(auto = true) else finish() }
                        else -> pollJob()
                    }
                }.onFailure { pollJob() }   // 일시적 연결 오류는 재시도
            }
        }, 700)
    }

    private fun updateJob(j: JSONObject) {
        val steps = j.optJSONArray("steps")
        val stepList = (0 until (steps?.length() ?: 0)).map { steps!!.getString(it) }
        val step = j.optString("step")
        val labeling = "label" in stepList
        val title = when {
            labeling && relabelRequested -> "다시 라벨링합니다"
            labeling -> if (kind == "image") "라벨링되지 않은 이미지입니다" else "라벨링되지 않은 영상입니다"
            else -> "재생용 영상 변환 중"
        }
        val sub = when {
            labeling -> "PC에서 YOLO 로 차량·사람·차선을 라벨링한 뒤 자동으로 재생합니다"
            else -> "블랙박스 AVI(전·후방 2채널)에서 전방 영상만 MP4 로 변환합니다"
        }
        val idx = j.optInt("step_index")
        val names = stepList.map { if (it == "transcode") "MP4 변환" else "YOLO 라벨링" } + "불러오기"
        val state = j.optString("state")
        tvSteps.text = names.mapIndexed { i, n ->
            val mark = when {
                state == "done" || i < idx -> "✓"
                i == idx && state == "running" -> "●"
                else -> "○"
            }
            "$mark ${i + 1}. $n"
        }.joinToString("     ")

        val prog = j.optDouble("progress", 0.0)
        val qpos = j.optInt("queue_pos")
        val detail = when {
            state == "queued" && qpos > 0 -> "대기열 ${qpos}번째 — 앞 작업이 끝나면 시작합니다"
            step == "label" && j.optInt("total_frames") > 0 -> {
                val eta = if (j.isNull("eta_s")) "" else " · 남은 시간 약 ${fmtSec(j.optDouble("eta_s"))}"
                String.format(Locale.US, "%d / %d 프레임 · %.1f fps%s",
                    j.optInt("done_frames"), j.optInt("total_frames"), j.optDouble("fps"), eta)
            }
            step == "label" -> "모델 불러오는 중…"
            step == "transcode" -> String.format(Locale.US, "변환 %.0f%%", prog * 100)
            else -> j.optString("message")
        }
        showLoading(title, sub, indeterminate = state == "queued" || (step == "label" && prog <= 0.0))
        pbLoad.progress = (prog * 1000).toInt()
        tvLoadDetail.text = detail
        val gpuName = j.optString("gpu_name").takeIf { it.isNotEmpty() && it != "null" }
        val model = j.optString("model").removeSuffix(".pt")
        tvLoadDevice.visibility = if (labeling) View.VISIBLE else View.GONE
        tvLoadDevice.text = if (j.optString("device").startsWith("cuda")) {
            "GPU 인식 → $model · ${gpuName ?: "CUDA"}"
        } else {
            "GPU 미인식 → $model · CPU"
        }
    }

    /** 라벨 JSON + 재생 파일 내려받아 표시 */
    private fun fetchAndShow(alreadyLabeled: Boolean) {
        showLoading("불러오는 중", if (alreadyLabeled) "라벨된 파일입니다 — 박스 데이터와 영상을 받는 중" else "라벨링 완료 — 결과를 받는 중",
            indeterminate = false)
        tvSteps.text = ""
        tvLoadDevice.visibility = View.GONE
        val g = gen
        val p = path
        val k = kind
        Bg.run({
            val ls = LabelSet.parse(api.labels(p))
            val ext = if (k == "image") p.substringAfterLast('.', "jpg") else "mp4"
            val dir = File(cacheDir, "media").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }          // 캐시는 현재 파일만 유지
            val f = File(dir, sha1(api.base + "|" + p) + "." + ext)
            api.downloadMedia(p, f, { done, total ->
                Bg.ui {
                    if (total > 0) pbLoad.progress = (done * 1000 / total).toInt()
                    tvLoadDetail.text = "${MainActivity.fmtSize(done)}" + if (total > 0) " / ${MainActivity.fmtSize(total)}" else ""
                }
            }, { destroyed || g != gen })
            ls to f
        }) { r ->
            if (destroyed || g != gen) return@run
            r.onSuccess { (ls, f) -> show(ls, f) }
                .onFailure { showError("불러오기 실패", it.message ?: it.toString()) }
        }
    }

    // ================================================================ 표시
    private fun show(ls: LabelSet, file: File) {
        labels = ls
        tvSource.text = "라벨: ${ls.sourceText} · ${ls.modelText}" +
            (if (ls.kind == "video") String.format(Locale.US, " · %.0f fps 샘플 · %d 프레임", ls.sampleFps, ls.frames.size) else "")
        if (ls.kind == "image" || kind == "image") showImage(ls, file) else showVideo(file)
    }

    private fun showImage(ls: LabelSet, file: File) {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        var sample = 1
        while (opts.outWidth / sample > 2560 || opts.outHeight / sample > 2560) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp == null) { showError("이미지를 열 수 없습니다", file.name); return }
        videoView.visibility = View.GONE
        imageView.visibility = View.VISIBLE
        imageView.setImageBitmap(bmp)
        listOf(btnPlay, btnSpeed, tvTime, seek).forEach { it.visibility = View.GONE }
        hideLoading()
        overlay.post {
            overlay.fitContent(bmp.width, bmp.height)
            renderAt(0.0)
        }
        scheduleAdvance(IMAGE_SHOW_MS)
    }

    private fun showVideo(file: File) {
        imageView.visibility = View.GONE
        videoView.visibility = View.VISIBLE
        listOf(btnPlay, btnSpeed, tvTime, seek).forEach { it.visibility = View.VISIBLE }
        videoView.setOnPreparedListener { mp ->
            player = mp
            applySpeed()
            hideLoading()
            videoView.start()
            btnPlay.text = "❚❚"
            startSync()
        }
        videoView.setOnCompletionListener {
            btnPlay.text = "▶"
            renderAt(videoView.duration / 1000.0)
            scheduleAdvance(800)
        }
        videoView.setOnErrorListener { _, what, extra ->
            showError("재생 실패", "MediaPlayer 오류 ($what, $extra)")
            true
        }
        videoView.setVideoPath(file.path)
    }

    private fun togglePlay() {
        if (videoView.visibility != View.VISIBLE) return
        if (videoView.isPlaying) {
            videoView.pause()
            btnPlay.text = "▶"
        } else {
            videoView.start()
            applySpeed()
            btnPlay.text = "❚❚"
            startSync()
        }
    }

    private fun cycleSpeed() {
        speed = when (speed) { 1.0f -> 0.5f; 0.5f -> 0.25f; 0.25f -> 2.0f; else -> 1.0f }
        btnSpeed.text = when (speed) { 0.25f -> "0.25x"; 0.5f -> "0.5x"; 2.0f -> "2.0x"; else -> "1.0x" }
        if (videoView.isPlaying) applySpeed()
    }

    private fun applySpeed() {
        val mp = player ?: return
        try {
            val playing = videoView.isPlaying
            mp.playbackParams = mp.playbackParams.setSpeed(speed)
            if (!playing) mp.pause()
        } catch (e: Exception) { /* 일부 기기 미지원 */ }
    }

    // 화면 갱신(vsync)마다 재생 위치에 맞춰 박스 갱신
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!syncing || destroyed) return
            val posMs = videoView.currentPosition
            renderAt(posMs / 1000.0)
            val now = System.currentTimeMillis()
            if (now - lastUiMs > 120) {
                lastUiMs = now
                val d = videoView.duration
                if (d > 0 && !userSeeking) seek.progress = (posMs.toLong() * 1000 / d).toInt()
                tvTime.text = "${fmtClock(posMs)} / ${fmtClock(d)}"
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startSync() {
        if (syncing) return
        syncing = true
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    private fun renderAt(t: Double) {
        val ls = labels ?: return
        val objs = ls.objectsAt(t)
        val lanes = ls.lanesAt(t)
        overlay.setFrame(objs, lanes)
        val v = objs.count { it.group == "vehicle" }
        val p = objs.count { it.group == "person" || it.group == "rider" }
        tvCounts.text = "차량 $v · 사람/이륜 $p · 차선 ${lanes.size}"
    }

    private fun stopPlayback() {
        syncing = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
        if (videoView.visibility == View.VISIBLE) videoView.stopPlayback()
        player = null
        labels = null
        overlay.setFrame(emptyList(), emptyList())
    }

    // ================================================================ 로딩/오류 화면
    private fun showLoading(title: String, sub: String, indeterminate: Boolean) {
        loadingPanel.visibility = View.VISIBLE
        pbSpinner.visibility = View.VISIBLE
        pbLoad.visibility = View.VISIBLE
        pbLoad.isIndeterminate = indeterminate
        tvLoadTitle.text = title
        tvLoadTitle.setTextColor(getColor(R.color.text))
        tvLoadSub.text = sub
        btnCancel.text = "취소"
        btnRelabel.isEnabled = false
    }

    private fun hideLoading() {
        loadingPanel.visibility = View.GONE
        btnRelabel.isEnabled = true
    }

    private fun showError(title: String, msg: String) {
        loadingPanel.visibility = View.VISIBLE
        pbSpinner.visibility = View.GONE
        pbLoad.visibility = View.GONE
        tvLoadTitle.text = title
        tvLoadTitle.setTextColor(getColor(R.color.err))
        tvLoadSub.text = msg
        tvSteps.text = ""
        tvLoadDetail.text = ""
        tvLoadDevice.visibility = View.GONE
        btnCancel.text = "목록으로"
        if (isPlaylist && index < items.size - 1) {
            tvLoadDetail.text = "3초 후 다음 파일로 넘어갑니다"
            scheduleAdvance(3000)
        }
    }

    private fun cancelAndClose() {
        val id = jobId
        if (id != null) Bg.run({ api.cancelJob(id) }) { }
        finish()
    }

    private fun confirmRelabel() {
        AlertDialog.Builder(this)
            .setTitle("다시 라벨링")
            .setMessage("현재 서버 설정(GPU면 YOLO11s, 아니면 YOLOv8 CPU)으로 다시 라벨링합니다.\n" +
                "기존 사전 라벨(오전 작업)은 그대로 두고 서버 라벨이 우선 적용됩니다.")
            .setPositiveButton("라벨링") { _, _ -> start(relabel = true) }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (videoView.isPlaying) {
            videoView.pause()
            btnPlay.text = "▶"
        }
    }

    override fun onDestroy() {
        destroyed = true
        Bg.main.removeCallbacks(advance)
        syncing = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SERVER = "server"
        const val EXTRA_PATH = "path"
        const val EXTRA_NAME = "name"
        const val EXTRA_KIND = "kind"
        const val EXTRA_PLAYLIST_PATHS = "playlist_paths"
        const val EXTRA_PLAYLIST_NAMES = "playlist_names"
        const val EXTRA_PLAYLIST_KINDS = "playlist_kinds"
        const val EXTRA_INDEX = "index"
        const val IMAGE_SHOW_MS = 3000L

        fun sha1(s: String): String =
            MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

        fun fmtClock(ms: Int): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
        }

        fun fmtSec(sec: Double): String {
            val s = sec.toInt()
            return if (s >= 60) "${s / 60}분 ${s % 60}초" else "${s}초"
        }
    }
}
