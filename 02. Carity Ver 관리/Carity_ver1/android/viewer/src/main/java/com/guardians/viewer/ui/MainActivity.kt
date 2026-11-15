package com.guardians.viewer.ui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.guardians.viewer.R
import com.guardians.viewer.model.FileEntry
import com.guardians.viewer.net.Bg
import com.guardians.viewer.net.ServerApi
import java.util.Locale

/**
 * 1) PC 서버 폴더 탐색 (영상·이미지 + 라벨 여부 배지)
 * 2) 태블릿 로컬 파일 열기 → 서버로 업로드 후 동일 흐름
 */
class MainActivity : BaseActivity() {

    private lateinit var etServer: EditText
    private lateinit var tvServerStatus: TextView
    private lateinit var tvBackend: TextView
    private lateinit var tvPath: TextView
    private lateinit var tvSummary: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var pbList: ProgressBar
    private lateinit var btnUp: MaterialButton
    private lateinit var list: ListView
    private lateinit var filterGroup: MaterialButtonToggleGroup
    private val adapter = FileAdapter()

    private var api: ServerApi? = null
    private var currentDir = ""
    private var parentDir: String? = null
    private var allEntries: List<FileEntry> = emptyList()
    private var loading = false
    private var backendText = ""

    // 선택(체크박스) 상태: 현재 폴더 안의 파일 경로
    private val selected = LinkedHashSet<String>()
    private lateinit var cbAll: CheckBox
    private lateinit var tvSelCount: TextView
    private lateinit var btnAnalyze: MaterialButton
    private lateinit var btnPlayAll: MaterialButton
    private var resumed = false
    private val pollRunnable = Runnable { if (resumed && !loading) load(currentDir, quiet = true) }

    private val pickLocal = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) uploadLocal(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarInsets(findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))
        etServer = findViewById(R.id.etServer)
        tvServerStatus = findViewById(R.id.tvServerStatus)
        tvBackend = findViewById(R.id.tvBackend)
        tvPath = findViewById(R.id.tvPath)
        tvSummary = findViewById(R.id.tvSummary)
        tvEmpty = findViewById(R.id.tvEmpty)
        pbList = findViewById(R.id.pbList)
        btnUp = findViewById(R.id.btnUp)
        list = findViewById(R.id.list)
        filterGroup = findViewById(R.id.filterGroup)
        cbAll = findViewById(R.id.cbAll)
        tvSelCount = findViewById(R.id.tvSelCount)
        btnAnalyze = findViewById(R.id.btnAnalyze)
        btnPlayAll = findViewById(R.id.btnPlayAll)
        list.adapter = adapter
        list.emptyView = tvEmpty

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        etServer.setText(prefs.getString(KEY_SERVER, DEFAULT_SERVER))
        currentDir = prefs.getString(KEY_DIR, "") ?: ""

        findViewById<MaterialButton>(R.id.btnConnect).setOnClickListener { connect() }
        etServer.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE) { connect(); true } else false
        }
        btnUp.setOnClickListener { parentDir?.let { load(it) } }
        filterGroup.addOnButtonCheckedListener { _, _, isChecked -> if (isChecked) applyFilter() }
        findViewById<MaterialButton>(R.id.btnLocal).setOnClickListener { openLocalPicker() }
        list.setOnItemClickListener { _, _, pos, _ -> onEntry(adapter.getItem(pos)) }
        cbAll.setOnClickListener { selectAllVisible(cbAll.isChecked) }
        btnAnalyze.setOnClickListener { analyzeSelected() }
        btnPlayAll.setOnClickListener { playSelected() }
        findViewById<MaterialButton>(R.id.btnPageUp).setOnClickListener { page(-1) }
        findViewById<MaterialButton>(R.id.btnPageDown).setOnClickListener { page(1) }
        updateSelectionUi()

        connect()
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        Bg.main.removeCallbacks(pollRunnable)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        // 뷰어에서 라벨링 후 돌아오면 배지 갱신
        if (api != null && !loading) load(currentDir, quiet = true)
    }

    // ------------------------------------------------------------ 서버 연결
    private fun connect() {
        hideKeyboard()
        val url = etServer.text.toString().ifBlank { DEFAULT_SERVER }
        val a = ServerApi(url)
        tvServerStatus.text = "연결 중… ${a.base}"
        tvServerStatus.setTextColor(color(R.color.text_dim))
        Bg.run({ a.health() }) { r ->
            if (isFinishing) return@run
            r.onSuccess { h ->
                api = a
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SERVER, a.base).apply()
                tvServerStatus.text = "연결됨 · ${a.base} · PC 폴더: ${h.optString("root")}"
                tvServerStatus.setTextColor(color(R.color.ok))
                val gpu = h.optBoolean("gpu")
                val model = h.optString("model").removeSuffix(".pt")
                tvBackend.text = if (gpu) {
                    "YOLO: $model · GPU (${h.optString("gpu_name")})"
                } else {
                    "YOLO: $model · CPU  —  ${h.optString("reason")}"
                }
                tvBackend.setTextColor(color(if (gpu) R.color.ok else R.color.warn))
                backendText = if (gpu) "GPU · $model" else "CPU · $model"
                load(currentDir)
            }.onFailure { e ->
                api = null
                tvServerStatus.text = "연결 실패: ${e.message ?: e.javaClass.simpleName}"
                tvServerStatus.setTextColor(color(R.color.err))
                tvBackend.text = "YOLO: -"
                allEntries = emptyList()
                applyFilter()
                tvEmpty.text = "PC 서버에 연결할 수 없습니다.\n\n1) PC에서 labeling_server\\run_server.bat 실행\n" +
                    "2) 에뮬레이터는 http://10.0.2.2:8765 , 실제 태블릿은 http://<PC IP>:8765\n3) [연결]"
            }
        }
    }

    // ------------------------------------------------------------ 폴더 목록
    private fun load(dir: String, quiet: Boolean = false) {
        val a = api ?: return
        loading = true
        if (!quiet) pbList.visibility = View.VISIBLE
        Bg.run({ a.files(dir) }) { r ->
            loading = false
            pbList.visibility = View.GONE
            if (isFinishing) return@run
            r.onSuccess { o ->
                val newDir = o.optString("dir", "")
                if (newDir != currentDir) selected.clear()
                currentDir = newDir
                parentDir = if (o.isNull("parent")) null else o.optString("parent", "")
                val arr = o.getJSONArray("entries")
                allEntries = (0 until arr.length()).map { FileEntry.from(arr.getJSONObject(it)) }
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DIR, currentDir).apply()
                tvPath.text = "PC / " + currentDir.ifEmpty { "" }.replace("/", " / ")
                btnUp.isEnabled = parentDir != null
                tvEmpty.text = "이 폴더에는 영상·이미지가 없습니다."
                val present = allEntries.filter { !it.isDir }.map { it.path }.toSet()
                selected.retainAll(present)
                applyFilter()
                // 처리 중인 작업이 있으면 1.5초마다 목록(진행률 배지) 갱신
                Bg.main.removeCallbacks(pollRunnable)
                if (resumed && allEntries.any { it.jobState != null }) Bg.main.postDelayed(pollRunnable, 1500)
            }.onFailure { e ->
                if (dir.isNotEmpty() && !quiet) {
                    toast("폴더를 열 수 없습니다: ${e.message}")
                    load("")
                }
            }
        }
    }

    private fun applyFilter() {
        val mode = filterGroup.checkedButtonId
        val shown = allEntries.filter {
            it.isDir || when (mode) {
                R.id.fLabeled -> it.labeled
                R.id.fUnlabeled -> !it.labeled
                else -> true
            }
        }
        adapter.items = shown
        adapter.notifyDataSetChanged()
        updateSelectionUi()
        val media = allEntries.filter { !it.isDir }
        val lab = media.count { it.labeled }
        val dirs = allEntries.count { it.isDir }
        tvSummary.text = if (allEntries.isEmpty()) "" else
            "폴더 $dirs · 미디어 ${media.size}개 (라벨됨 $lab · 미라벨 ${media.size - lab})" +
                "   — 라벨된 파일은 박스와 함께 바로 재생, 미라벨 파일은 라벨링 후 재생"
    }

    private fun onEntry(e: FileEntry) {
        if (e.isDir) {
            load(e.path)
            return
        }
        val a = api ?: return
        startActivity(Intent(this, ViewerActivity::class.java)
            .putExtra(ViewerActivity.EXTRA_SERVER, a.base)
            .putExtra(ViewerActivity.EXTRA_PATH, e.path)
            .putExtra(ViewerActivity.EXTRA_NAME, e.name)
            .putExtra(ViewerActivity.EXTRA_KIND, e.type))
    }

    // ------------------------------------------------------------ 선택 · 일괄 분석 · 연속 재생
    /** 한 화면(보이는 행 수 - 1)씩 목록 이동 */
    private fun page(dir: Int) {
        val visible = (list.lastVisiblePosition - list.firstVisiblePosition).coerceAtLeast(1)
        val target = (list.firstVisiblePosition + dir * (visible - 1).coerceAtLeast(1))
            .coerceIn(0, (adapter.count - 1).coerceAtLeast(0))
        list.setSelection(target)
    }

    private fun visibleMedia() = adapter.items.filter { !it.isDir }

    private fun selectedEntries() = allEntries.filter { !it.isDir && it.path in selected }

    private fun selectAllVisible(on: Boolean) {
        val vis = visibleMedia().map { it.path }
        if (on) selected.addAll(vis) else selected.removeAll(vis.toSet())
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun toggle(path: String, on: Boolean) {
        if (on) selected.add(path) else selected.remove(path)
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        if (!::cbAll.isInitialized) return
        val sel = selectedEntries()
        val unl = sel.count { !it.labeled }
        val vis = visibleMedia()
        cbAll.isEnabled = vis.isNotEmpty()
        cbAll.isChecked = vis.isNotEmpty() && vis.all { it.path in selected }
        tvSelCount.text = if (sel.isEmpty()) "선택 0개 — 체크박스로 파일을 고르거나 [전체 선택]" else
            "선택 ${sel.size}개 (라벨됨 ${sel.size - unl} · 미라벨 $unl)"
        btnAnalyze.isEnabled = sel.isNotEmpty() && api != null
        btnPlayAll.isEnabled = sel.isNotEmpty() && api != null
        btnAnalyze.alpha = if (btnAnalyze.isEnabled) 1f else 0.4f
        btnPlayAll.alpha = if (btnPlayAll.isEnabled) 1f else 0.4f
        btnAnalyze.text = if (sel.isEmpty()) "선택 분석" else "선택 분석 (${sel.size})"
        btnPlayAll.text = if (sel.isEmpty()) "연속 재생" else "연속 재생 (${sel.size})"
    }

    /** 선택 파일 중 미라벨 파일을 서버 작업 큐에 등록 (서버가 순서대로 라벨링) */
    private fun analyzeSelected() {
        val a = api ?: return
        val sel = selectedEntries()
        if (sel.isEmpty()) return
        val busy = sel.filter { it.jobState != null }
        val todo = sel.filter { !it.labeled && it.jobState == null }
        val labeled = sel.filter { it.labeled && it.jobState == null }
        if (todo.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("선택 분석")
                .setMessage("선택한 ${sel.size}개는 이미 라벨되었거나 처리 중입니다.\n" +
                    "라벨된 ${labeled.size}개를 현재 설정($backendText)으로 다시 라벨링할까요?")
                .setPositiveButton("다시 라벨링") { _, _ -> enqueue(a, labeled, relabel = true) }
                .setNegativeButton("취소", null).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("선택 분석")
            .setMessage("미라벨 ${todo.size}개를 바로 라벨링합니다 ($backendText).\n" +
                (if (labeled.isNotEmpty()) "이미 라벨된 ${labeled.size}개는 건너뜁니다.\n" else "") +
                (if (busy.isNotEmpty()) "처리 중인 ${busy.size}개는 그대로 진행됩니다.\n" else "") +
                "진행률은 목록 배지에 표시되며, 화면을 떠나도 PC에서 계속 처리됩니다.")
            .setPositiveButton("분석 시작") { _, _ -> enqueue(a, todo, relabel = false) }
            .setNegativeButton("취소", null).show()
    }

    private fun enqueue(a: ServerApi, items: List<FileEntry>, relabel: Boolean) {
        if (items.isEmpty()) return
        btnAnalyze.isEnabled = false
        Bg.run({
            var ok = 0
            val fails = ArrayList<String>()
            for (e in items) {
                try { a.prepare(e.path, relabel); ok++ } catch (ex: Exception) { fails.add(e.name) }
            }
            ok to fails
        }) { r ->
            if (isFinishing) return@run
            r.onSuccess { (ok, fails) ->
                toast("${ok}개 분석 작업을 등록했습니다" + if (fails.isNotEmpty()) " (실패 ${fails.size}개: ${fails.first()} 등)" else "")
            }.onFailure { toast("작업 등록 실패: ${it.message}") }
            load(currentDir, quiet = true)
        }
    }

    /** 선택 파일을 목록 순서대로 연속 재생 (미라벨 파일은 재생 차례에 라벨링, 뒤 파일은 미리 큐에 등록) */
    private fun playSelected() {
        val a = api ?: return
        val sel = selectedEntries()
        if (sel.isEmpty()) return
        startActivity(Intent(this, ViewerActivity::class.java)
            .putExtra(ViewerActivity.EXTRA_SERVER, a.base)
            .putExtra(ViewerActivity.EXTRA_PLAYLIST_PATHS, sel.map { it.path }.toTypedArray())
            .putExtra(ViewerActivity.EXTRA_PLAYLIST_NAMES, sel.map { it.name }.toTypedArray())
            .putExtra(ViewerActivity.EXTRA_PLAYLIST_KINDS, sel.map { it.type }.toTypedArray())
            .putExtra(ViewerActivity.EXTRA_INDEX, 0))
    }

    // ------------------------------------------------------------ 로컬 파일
    private fun openLocalPicker() {
        if (api == null) {
            toast("먼저 PC 서버에 연결하세요 (라벨링은 PC에서 수행됩니다)")
            return
        }
        try {
            pickLocal.launch(arrayOf("video/*", "image/*"))
        } catch (e: ActivityNotFoundException) {
            AlertDialog.Builder(this)
                .setTitle("파일 선택기가 없습니다")
                .setMessage("이 기기(차량용 OS)에는 시스템 파일 선택기가 없습니다.\n" +
                    "PC 폴더에 파일을 넣고 목록에서 선택하세요.")
                .setPositiveButton("확인", null).show()
        }
    }

    private fun uploadLocal(uri: Uri) {
        val a = api ?: return
        var name = "upload"
        var size = -1L
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }
        val kind = when {
            contentResolver.getType(uri)?.startsWith("image") == true -> "image"
            else -> "video"
        }
        val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        val dlg = AlertDialog.Builder(this).setTitle("PC로 업로드 중: $name").setView(pb).setCancelable(false).show()
        Bg.run({
            val input = contentResolver.openInputStream(uri) ?: error("파일을 열 수 없습니다")
            a.upload(input, name, size) { done, total ->
                if (total > 0) Bg.ui { pb.progress = (done * 1000 / total).toInt() }
            }
        }) { r ->
            dlg.dismiss()
            r.onSuccess { path ->
                startActivity(Intent(this, ViewerActivity::class.java)
                    .putExtra(ViewerActivity.EXTRA_SERVER, a.base)
                    .putExtra(ViewerActivity.EXTRA_PATH, path)
                    .putExtra(ViewerActivity.EXTRA_NAME, name)
                    .putExtra(ViewerActivity.EXTRA_KIND, kind))
            }.onFailure { toast("업로드 실패: ${it.message}") }
        }
    }

    // ------------------------------------------------------------ util
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(etServer.windowToken, 0)
        etServer.clearFocus()
    }

    inner class FileAdapter : BaseAdapter() {
        var items: List<FileEntry> = emptyList()
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            val e = items[position]
            val icon = v.findViewById<TextView>(R.id.tvIcon)
            val name = v.findViewById<TextView>(R.id.tvName)
            val meta = v.findViewById<TextView>(R.id.tvMeta)
            val badge = v.findViewById<TextView>(R.id.tvBadge)
            val cb = v.findViewById<CheckBox>(R.id.cbSel)
            name.text = e.name
            cb.setOnClickListener(null)
            if (e.isDir) {
                cb.visibility = View.INVISIBLE
            } else {
                cb.visibility = View.VISIBLE
                cb.isChecked = e.path in selected
                cb.setOnClickListener { toggle(e.path, cb.isChecked) }
            }
            when (e.type) {
                "dir" -> {
                    icon.text = "▤"
                    icon.setTextColor(color(R.color.warn))
                    meta.text = "폴더 · 미디어 ${e.mediaCount}개"
                    badge.visibility = View.GONE
                }
                else -> {
                    icon.text = if (e.type == "video") "▶" else "▣"
                    icon.setTextColor(color(R.color.accent))
                    val src = when (e.labelSource) {
                        "legacy" -> " · 사전 라벨(오전 작업)"
                        "sidecar" -> " · 서버 라벨"
                        "adjacent" -> " · .labels.json"
                        else -> ""
                    }
                    val conv = if (e.type == "video" && !e.previewReady) " · 재생 시 MP4 변환" else ""
                    meta.text = (if (e.type == "video") "영상" else "이미지") + " · " + fmtSize(e.size) + src + conv
                    badge.visibility = View.VISIBLE
                    when {
                        e.jobState == "queued" -> {
                            badge.text = if (e.jobQueue > 0) "대기 ${e.jobQueue}번" else "대기"
                            badge.setTextColor(color(R.color.text_dim))
                        }
                        e.jobState == "running" -> {
                            val what = if (e.jobStep == "transcode") "변환" else "라벨링"
                            badge.text = "$what ${(e.jobProgress * 100).toInt()}%"
                            badge.setTextColor(color(R.color.accent))
                        }
                        e.labeled -> {
                            badge.text = "라벨됨"
                            badge.setTextColor(color(R.color.ok))
                        }
                        else -> {
                            badge.text = "미라벨"
                            badge.setTextColor(color(R.color.warn))
                        }
                    }
                }
            }
            return v
        }
    }

    companion object {
        const val PREFS = "gv"
        const val KEY_SERVER = "server"
        const val KEY_DIR = "dir"
        const val DEFAULT_SERVER = "http://10.0.2.2:8765"

        fun fmtSize(b: Long): String = when {
            b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
            b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
            b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
            else -> "$b B"
        }
    }
}
