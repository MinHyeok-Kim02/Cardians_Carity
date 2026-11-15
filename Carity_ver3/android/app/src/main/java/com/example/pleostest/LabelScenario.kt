package com.example.pleostest

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * YOLO 라벨(블랙박스 영상) → AlleyWorld 장애물 변환.
 *
 * 라벨 JSON 은 labeling_server 의 version 2 형식(오전 data/raw 형식과 호환)이며 좌표는 0~1 정규화입니다.
 * 한 시점(프레임)의 박스를 평지 핀홀 카메라 모델로 지면에 투영해 위에서 본 장애물 배치를 만듭니다.
 *   전방거리 Z = 카메라높이 · 초점(가로비) · 화면비 / (박스하단y − 지평선y)
 *   좌우위치 X = (x − 0.5) · Z / 초점(가로비)
 * 카메라 값은 오전 작업에서 블랙박스 화면으로 맞춘 값(지평선 0.53, 초점 0.384, 높이 1.3m, 보닛 0.82)입니다.
 * 단안 카메라 근사이므로 실측이 아니라 "라벨 기반 가상 배치"입니다.
 */
data class LabelBox(
    val id: Int, val cls: String, val conf: Double,
    val x1: Double, val y1: Double, val x2: Double, val y2: Double,
) {
    val cx get() = (x1 + x2) / 2
    val vehicle get() = cls == "car" || cls == "truck" || cls == "bus"
}

data class LabelFrame(val t: Double, val objects: List<LabelBox>)

data class LabelClip(
    val source: String, val clip: String, val width: Int, val height: Int,
    val sampleFps: Double, val duration: Double, val model: String, val device: String,
    val frames: List<LabelFrame>,
) {
    fun frameAt(t: Double): LabelFrame? = frames.minByOrNull { abs(it.t - t) }

    companion object {
        fun parse(json: String): LabelClip {
            val o = MiniJson.obj(MiniJson.parse(json))
            val frames = MiniJson.arr(o["frames"]).map { fv ->
                val f = MiniJson.obj(fv)
                LabelFrame(MiniJson.num(f["t"]), MiniJson.arr(f["objects"]).mapNotNull { ov ->
                    val b = MiniJson.obj(ov)
                    val box = MiniJson.arr(b["box"]).map { MiniJson.num(it) }
                    if (box.size < 4) null else LabelBox(
                        MiniJson.num(b["id"], -1.0).toInt(), MiniJson.str(b["cls"], "?"), MiniJson.num(b["conf"]),
                        box[0], box[1], box[2], box[3])
                })
            }.sortedBy { it.t }
            val clip = MiniJson.str(o["clip"], MiniJson.str(o["source"], "label"))
            return LabelClip(
                source = MiniJson.str(o["source"], clip), clip = clip,
                width = MiniJson.num(o["width"], 1280.0).toInt(), height = MiniJson.num(o["height"], 720.0).toInt(),
                sampleFps = MiniJson.num(o["sample_fps"], 10.0),
                duration = MiniJson.num(o["duration"], frames.lastOrNull()?.t ?: 0.0),
                model = MiniJson.str(o["model"], "?"), device = MiniJson.str(o["device"], "?"),
                frames = frames)
        }
    }
}

/** 블랙박스 카메라 (정규화 좌표 기준). */
data class DashCamera(
    val horizon: Double = 0.53,
    val focalW: Double = 0.384,
    val heightM: Double = 1.3,
    val hood: Double = 0.82,
    val aspect: Double = 16.0 / 9.0,
) {
    /** 박스 하단(정규화 y)의 지면 거리(m). 지평선 위면 null. */
    fun groundDistance(yBottom: Double): Double? {
        val dy = yBottom - horizon
        if (dy <= 0.004) return null
        return heightM * focalW * aspect / dy
    }
    /** 정규화 x 를 거리 Z 에서의 좌우 위치(m, 오른쪽 +)로 변환 */
    fun lateral(xNorm: Double, z: Double) = (xNorm - 0.5) * z / focalW
    /** 보닛에 가려지기 시작하는 가장 가까운 지면 거리 */
    val nearestVisible get() = groundDistance(hood)!!
}

/** 라벨 객체 하나를 지면에 투영한 결과 (자차 카메라 기준, x 오른쪽+, z 전방+) */
data class ProjectedObject(
    val box: LabelBox,
    val zNear: Double,          // 객체 앞면까지 전방 거리
    val xInner: Double,         // 자차 쪽(안쪽) 모서리 좌우 위치
    val centerX: Double,
    val width: Double, val length: Double,
    val frontal: Boolean,       // 진행 경로 정면에서 다가오는 차량 (대향차)
    val inPath: Boolean,        // 박스가 자차 진행 폭(±1.1m)에 걸침 (정차·주차 차량 포함)
    val growth: Double?,        // 1초 전 대비 박스 높이 비율
    val truncatedBottom: Boolean,
    val label: String,
)

data class LabelMoment(val t: Double, val score: Double, val summary: String)

/** 라벨 기반 장면: AlleyWorld 생성 정보 + 설명 */
data class LabelScene(
    val clip: LabelClip, val t: Double,
    val projected: List<ProjectedObject>,
    val parkedCars: List<AlleyWorld.ParkedCar>,
    val leftWallX: Double, val rightWallX: Double, val endLineY: Double,
    val frontal: List<AlleyWorld.ParkedCar>,
) {
    fun toWorld() = AlleyWorld(parkedCars, emptyList(), leftWallX, rightWallX, endLineY)
    val title get() = "${clip.clip} · ${String.format(java.util.Locale.KOREA, "%.1f", t)}초"
    fun describe(): String {
        val l = projected.count { !it.inPath && it.centerX < 0 }
        val r = projected.count { !it.inPath && it.centerX > 0 }
        val f = projected.count { it.frontal }
        val p = projected.count { it.inPath && !it.frontal }
        return "왼쪽 ${l}개 · 오른쪽 ${r}개 · 대향 ${f}개 · 경로상 ${p}개 · 도로폭 " +
            String.format(java.util.Locale.KOREA, "%.1fm", rightWallX - leftWallX)
    }
}

object LabelSceneBuilder {
    const val EGO_START_Y = 4.0
    /** 판단 시점보다 앞에서 접근을 시작하도록 장면 전체를 앞으로 미는 거리 */
    const val LEAD = 6.0
    /** 자차 진행 폭(차체 반폭 0.9 + 여유 0.2) */
    const val PATH_HALF = 1.1
    /** 블랙박스(앞유리) 위치: 차량 중심에서 전방 0.6m */
    const val CAMERA_FORWARD = 0.6
    const val MAX_RANGE = 32.0
    const val MAX_LATERAL = 7.0
    const val MIN_CONF = 0.4
    /** 보닛에 잘린 박스는 오검출이 많아 더 높은 신뢰도 요구 */
    const val MIN_CONF_TRUNCATED = 0.5

    fun size(cls: String): Pair<Double, Double> = when (cls) {
        "truck" -> 2.0 to 6.0
        "bus" -> 2.3 to 9.0
        "person" -> 0.6 to 0.6
        "bicycle" -> 0.6 to 1.7
        "motorcycle" -> 0.8 to 2.0
        else -> 1.8 to 4.4
    }

    fun koreanLabel(cls: String) = when (cls) {
        "car" -> "차량"; "truck" -> "트럭"; "bus" -> "버스"; "person" -> "보행자"
        "bicycle" -> "자전거"; "motorcycle" -> "오토바이"; else -> cls
    }

    /** 자차 보닛·화면 하단 반사를 차량으로 오검출한 박스 */
    fun isHoodOrGiant(b: LabelBox) =
        (b.x2 - b.x1 > 0.8) || (b.y1 > 0.55 && b.y2 > 0.9 && b.x2 - b.x1 > 0.3) ||
            (b.x2 - b.x1 > 0.75 && b.y2 - b.y1 > 0.75)

    /**
     * 박스 하나를 투영. 너무 멀거나 신뢰도가 낮거나 지평선 위면 null.
     * growth: 같은 추적 ID 의 1초 전 대비 박스 높이 비율, staticGrowth: 같은 시점 측면(정지) 객체들의 중앙값.
     */
    fun project(box: LabelBox, cam: DashCamera = DashCamera(), growth: Double? = null,
                staticGrowth: Double? = null): ProjectedObject? {
        if (box.conf < MIN_CONF || isHoodOrGiant(box)) return null
        val truncated = box.y2 >= cam.hood - 0.01
        if (truncated && box.conf < MIN_CONF_TRUNCATED) return null
        val z = (if (truncated) cam.nearestVisible else cam.groundDistance(box.y2)) ?: return null
        if (z > MAX_RANGE) return null
        val (w, len) = size(box.cls)
        val xl = cam.lateral(box.x1, z)
        val xr = cam.lateral(box.x2, z)
        // 자차 진행 폭에 0.3m 이상 걸치면 경로상 객체
        val overlap = min(xr, PATH_HALF) - max(xl, -PATH_HALF)
        val inPath = overlap >= 0.3
        // 정지 객체보다 뚜렷하게 빨리 커지면 다가오는 차 (자차가 서 있으면 기준 1.0)
        val approaching = growth != null && growth >= max(1.08, (staticGrowth ?: 1.0) * 1.1)
        val frontal = inPath && box.vehicle && approaching
        val centerX: Double
        val inner: Double
        val width: Double
        if (frontal) {
            width = max(w, min(xr - xl, w + 0.6))
            centerX = cam.lateral(box.cx, z)
            inner = if (centerX < 0) centerX + width / 2 else centerX - width / 2
        } else if (inPath && xl < -0.5 && xr > 0.5) {
            // 경로 한가운데를 막은 정지 객체: 보이는 범위(대각선이면 길이 포함, 최대 차폭+1m)를 막힌 것으로
            width = min(xr - xl, w + 1.0).coerceAtLeast(w.coerceAtMost(0.6))
            centerX = (xl + xr) / 2
            inner = if (centerX < 0) centerX + width / 2 else centerX - width / 2
        } else if (box.cx < 0.5) {
            inner = xr                       // 왼쪽 객체: 오른쪽 모서리가 자차 쪽
            width = w
            centerX = inner - w / 2
        } else {
            inner = xl                       // 오른쪽 객체: 왼쪽 모서리가 자차 쪽
            width = w
            centerX = inner + w / 2
        }
        if (abs(centerX) > MAX_LATERAL) return null
        return ProjectedObject(box, z, inner, centerX, width, len, frontal, inPath, growth, truncated,
            if (frontal) "대향 " + koreanLabel(box.cls) else if (inPath) "경로상 " + koreanLabel(box.cls) else koreanLabel(box.cls))
    }

    /** 시점 t 의 프레임으로 장면 생성 */
    fun build(clip: LabelClip, t: Double, cam: DashCamera = DashCamera()): LabelScene {
        val frame = clip.frameAt(t) ?: LabelFrame(t, emptyList())
        val growth = growthOf(clip, frame)
        val staticGrowth = frame.objects.filter { it.id >= 0 && (it.cx < 0.35 || it.cx > 0.65) }
            .mapNotNull { growth[it.id] }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val projected = frame.objects.mapNotNull { project(it, cam, growth[it.id], staticGrowth) }
            .let { dedupe(it) }
        val cars = projected.map { p ->
            val y = EGO_START_Y + LEAD + CAMERA_FORWARD + p.zNear + p.length / 2
            AlleyWorld.ParkedCar(p.centerX, y, p.width, p.length, p.label)
        }
        // 도로 경계: 각 방향 가장 바깥 객체의 바깥 모서리 + 0.3m (객체가 없으면 기본 2.8m, 최소 2.4m)
        // 벽이 객체를 관통하지 않도록 상한은 두지 않음
        val sideObjects = projected
        val leftWall = -max(2.4, sideObjects.filter { it.centerX < 0 }.maxOfOrNull { -(it.centerX - it.width / 2) + 0.3 } ?: 2.8)
        val rightWall = max(2.4, sideObjects.filter { it.centerX >= 0 }.maxOfOrNull { it.centerX + it.width / 2 + 0.3 } ?: 2.8)
        val farthest = cars.maxOfOrNull { it.y + it.length / 2 } ?: 20.0
        val endLine = max(30.0, farthest + 10.0)
        return LabelScene(clip, frame.t, projected, cars, leftWall, rightWall, endLine,
            cars.filterIndexed { i, _ -> projected[i].frontal })
    }

    /** 추적 ID 별 1초 전 대비 박스 높이 비율 */
    fun growthOf(clip: LabelClip, frame: LabelFrame): Map<Int, Double> {
        val prev = clip.frames.filter { it.t < frame.t - 0.5 }.minByOrNull { abs(it.t - (frame.t - 1.0)) } ?: return emptyMap()
        val out = HashMap<Int, Double>()
        for (b in frame.objects) {
            if (b.id < 0) continue
            val p = prev.objects.firstOrNull { it.id == b.id } ?: continue
            val h0 = p.y2 - p.y1
            if (h0 > 0.01) out[b.id] = (b.y2 - b.y1) / h0
        }
        return out
    }

    /** 같은 위치에 겹친 박스(car/truck 중복 검출) 제거: 겹침 큰 쪽 중 신뢰도 높은 것만 */
    private fun dedupe(list: List<ProjectedObject>): List<ProjectedObject> {
        val sorted = list.sortedByDescending { it.box.conf }
        val kept = ArrayList<ProjectedObject>()
        for (p in sorted) {
            val dup = kept.any { k ->
                val ix = max(0.0, min(k.box.x2, p.box.x2) - max(k.box.x1, p.box.x1))
                val iy = max(0.0, min(k.box.y2, p.box.y2) - max(k.box.y1, p.box.y1))
                val area = (p.box.x2 - p.box.x1) * (p.box.y2 - p.box.y1)
                area > 0 && ix * iy / area > 0.7
            }
            if (!dup) kept.add(p)
        }
        return kept
    }

    /**
     * 판단이 필요한 시점 추천: 진행 경로 근처(좌우 3m, 전방 20m)에 객체가 많고 가까울수록 높은 점수.
     * 3초 이상 떨어진 상위 n개.
     */
    fun suggestMoments(clip: LabelClip, n: Int = 6, cam: DashCamera = DashCamera()): List<LabelMoment> {
        val scored = clip.frames.map { f ->
            val g = growthOf(clip, f)
            val ps = f.objects.mapNotNull { project(it, cam, g[it.id]) }.filter { it.zNear < 20 }
            var score = 0.0
            var front = 0; var left = 0; var right = 0
            for (p in ps) {
                val lateralGap = abs(p.xInner) - 0.9
                if (p.frontal || p.inPath) { score += (if (p.frontal) 3.0 else 1.5) * (20 - p.zNear) / 20; front++ }
                else if (lateralGap < 2.0) {
                    score += (2.0 - lateralGap) * (20 - p.zNear) / 20
                    if (p.centerX < 0) left++ else right++
                }
            }
            LabelMoment(f.t, score, "왼쪽 $left · 오른쪽 $right · 정면 $front")
        }
        val out = ArrayList<LabelMoment>()
        for (m in scored.sortedByDescending { it.score }) {
            if (m.score <= 0.2) break
            if (out.none { abs(it.t - m.t) < 3.0 }) out.add(m)
            if (out.size >= n) break
        }
        return out.sortedBy { it.t }
    }
}
