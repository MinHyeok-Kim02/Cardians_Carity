package com.example.pleostest

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * 통과 경로 계획 (튜토리얼의 "어디로 움직여야 하는지" 화살표·미리보기 차량의 근거).
 *
 * 0.5m 간격 단면마다 차체가 들어갈 수 있는 차량 중심 x 구간을 구하고,
 * 조향 한계(1m 전진당 0.3m 횡이동)를 지키며 가장 여유 있는 쪽으로 이어 붙입니다.
 * 정면(대향) 차량이 있으면 그 차가 자차 쪽으로 다가오는 차로 전체를 막힌 것으로 보고,
 * 옆으로 비켜 지나갈 수 없으면 차가 지나갈 공간을 남기고 오른쪽(없으면 왼쪽) 빈 곳에 정차하는 양보 경로를 만듭니다.
 * 각 지점의 좌우 간격은 AlleyWorld.clearance() 로 다시 계산합니다(같은 거리 엔진 사용).
 */
object CorridorPlanner {
    const val EGO_HALF_W = 0.9
    const val EGO_HALF_L = 2.1
    const val MARGIN = 0.25
    const val STEP = 0.5
    const val MAX_SLOPE = 0.3
    /** 대향차가 지나갈 때 필요한 옆 간격 */
    const val PASS_MARGIN = 0.3
    /** 여유가 있을 때 지키려는 차체~장애물 간격 */
    const val COMFORT = 0.35

    enum class Outcome { CLEAR, YIELD, BLOCKED }

    data class Point(
        val x: Double, val y: Double, val heading: Double,
        val left: Double, val right: Double, val min: Double, val side: String,
    )

    data class Plan(
        val points: List<Point>,
        val outcome: Outcome,
        /** 양보/막힘 시 정차 위치 y */
        val stopY: Double?,
        /** 양보 방향: +1 오른쪽, -1 왼쪽, 0 없음 */
        val yieldSide: Int,
        val minLeft: Double, val minRight: Double,
        val narrowest: Point?,
        val note: String,
    ) {
        val minClearance get() = min(minLeft, minRight)
        val length get() = if (points.size < 2) 0.0 else points.last().y - points.first().y
    }

    private data class Interval(val lo: Double, val hi: Double) {
        val mid get() = (lo + hi) / 2
        val width get() = hi - lo
        fun contains(x: Double) = x in lo..hi
        fun clamp(x: Double) = x.coerceIn(lo, hi)
    }

    /** 단면 y 에서 차량 중심이 갈 수 있는 x 구간들 */
    private fun feasible(world: AlleyWorld, frontal: List<AlleyWorld.ParkedCar>, startY: Double, y: Double): List<Interval> {
        val domain = Interval(world.leftWallX + EGO_HALF_W + MARGIN, world.rightWallX - EGO_HALF_W - MARGIN)
        if (domain.width < 0) return emptyList()
        val blocked = ArrayList<Interval>()
        for (o in world.obstacles()) {
            val isFrontal = frontal.any { it.x == o.x && it.y == o.y }
            // 대향차는 자차 쪽으로 다가오므로, 두 차가 만나는 지점(현재 간격의 절반)부터 그 차의 끝까지 차로를 차지한다고 본다
            val meetY = startY + (o.y - o.length / 2 - startY) / 2
            val yLo = if (isFrontal) meetY - EGO_HALF_L - 1.0 else o.y - o.length / 2
            val yHi = o.y + o.length / 2
            if (y + EGO_HALF_L < yLo || y - EGO_HALF_L > yHi) continue
            val extra = if (isFrontal) PASS_MARGIN else MARGIN
            blocked.add(Interval(o.x - o.width / 2 - EGO_HALF_W - extra, o.x + o.width / 2 + EGO_HALF_W + extra))
        }
        var free = listOf(domain)
        for (b in blocked) {
            free = free.flatMap { f ->
                if (b.hi <= f.lo || b.lo >= f.hi) listOf(f)
                else listOfNotNull(
                    if (b.lo > f.lo) Interval(f.lo, b.lo) else null,
                    if (b.hi < f.hi) Interval(b.hi, f.hi) else null)
            }
        }
        return free.filter { it.width >= 0 }
    }

    /** 정적 장애물만 고려한 단면 구간 (양보 정차 위치 탐색용) */
    private fun feasibleStatic(world: AlleyWorld, frontal: List<AlleyWorld.ParkedCar>, y: Double): List<Interval> {
        val staticWorld = AlleyWorld(world.parkedCars.filter { p -> frontal.none { it.x == p.x && it.y == p.y } },
            world.safetyBollards, world.leftWallX, world.rightWallX, world.endLineY)
        return feasible(staticWorld, emptyList(), y, y)
    }

    fun plan(world: AlleyWorld, frontal: List<AlleyWorld.ParkedCar> = emptyList(),
             startX: Double = world.x, startY: Double = world.y): Plan {
        val endY = world.endStopY
        val ys = generateSequence(startY) { it + STEP }.takeWhile { it <= endY }.toList()
        if (ys.isEmpty()) return finish(world, listOf(startX to startY), Outcome.CLEAR, null, 0, "도로 끝")
        val d = MAX_SLOPE * STEP
        // 1) 단면별 목표 x
        val xs = ArrayList<Double>()
        val frees = ArrayList<List<Interval>>()
        var blockedAt = -1
        var prev = startX
        for ((i, y) in ys.withIndex()) {
            val free = feasible(world, frontal, startY, y)
            if (free.isEmpty()) { blockedAt = i; break }
            val pick = free.firstOrNull { it.contains(prev) } ?: free.minByOrNull { abs(it.clamp(prev) - prev) }!!
            // 좁은 구간은 가운데, 넓으면 현재 위치를 유지하며 가운데로 조금씩 (여유가 있으면 장애물에서 0.3m 더 떨어짐)
            val shrink = min(COMFORT, max(0.0, pick.width / 2 - 0.01))
            val comfy = Interval(pick.lo + shrink, pick.hi - shrink)
            val target = if (pick.width < 1.0) pick.mid else comfy.clamp(prev + (pick.mid - prev) * 0.15)
            xs.add(target); frees.add(free)
            prev = target
        }
        // 2) 조향 한계 반영: 뒤에서 앞으로 필요한 횡이동을 미리 시작(역방향), 그다음 출발점에서 이어지게(순방향)
        val n = xs.size
        if (n > 0) {
            for (i in n - 2 downTo 0) xs[i] = xs[i].coerceIn(xs[i + 1] - d, xs[i + 1] + d)
            xs[0] = startX
            for (i in 1 until n) xs[i] = xs[i].coerceIn(xs[i - 1] - d, xs[i - 1] + d)
        }
        // 2b) 헤딩까지 반영한 실제 차체 간격이 COMFORT 보다 작으면
        //     ① 반대쪽 여유가 있으면 밀어내고 ② 아니면 그 주변 횡이동을 더 완만하게 바꿔 차체가 덜 틀어지게 한다.
        //     완만하게 만드는 방식은 진입(미리 시작)·이탈(늦게 시작) 두 가지를 모두 시도해, 들어갈 수 있으면서
        //     최소 간격이 가장 큰 경로를 고른다. 어느 것도 낫지 않으면 보정 전 경로를 그대로 쓴다.
        if (n > 2) {
            val trial = AlleyWorld(world.parkedCars, world.safetyBollards, world.leftWallX, world.rightWallX, world.endLineY)
            fun headingAt(v: List<Double>, i: Int) = atan2(v[min(i + 1, n - 1)] - v[max(i - 1, 0)], ys[min(i + 1, n - 1)] - ys[max(i - 1, 0)])
            fun minGap(v: List<Double>): Double {
                var m = Double.MAX_VALUE
                for (i in 1 until n) { trial.restore(v[i], ys[i], headingAt(v, i), false); m = min(m, trial.clearance().meters) }
                return m
            }
            fun fits(v: List<Double>) = (0 until n).all { i -> frees[i].any { it.contains(v[i]) || abs(it.clamp(v[i]) - v[i]) < 0.02 } }
            fun repaired(backward: Boolean): List<Double> {
                val v = ArrayList(xs)
                val dLim = DoubleArray(n) { d }
                for (round in 0 until 8) {
                    var changed = false
                    for (i in 1 until n) {
                        val h = headingAt(v, i)
                        trial.restore(v[i], ys[i], h, false)
                        val c = trial.clearance()
                        if (c.meters >= COMFORT) continue
                        val push = if (c.leftMeters < c.rightMeters) 1.0 else -1.0
                        val near = min(c.leftMeters, c.rightMeters); val far = max(c.leftMeters, c.rightMeters)
                        val delta = min(COMFORT - near, (far - near) / 2)
                        val nx = v[i] + push * delta
                        if (delta > 0.01 && frees[i].any { it.contains(nx) }) { v[i] = nx; changed = true }
                        else if (abs(h) > 0.02) {
                            for (k in max(1, i - 8)..min(n - 1, i + 8))
                                if (dLim[k] > 0.25 * d) { dLim[k] *= 0.7; changed = true }
                        }
                    }
                    if (!changed) break
                    if (backward) for (i in n - 2 downTo 1) v[i] = v[i].coerceIn(v[i + 1] - dLim[i + 1], v[i + 1] + dLim[i + 1])
                    for (i in 1 until n) {
                        v[i] = v[i].coerceIn(v[i - 1] - dLim[i], v[i - 1] + dLim[i])
                        // 완만하게 만든 결과가 막힌 구간을 침범하면 구간 안으로 되돌린다 (이탈은 늦게 시작)
                        if (frees[i].none { it.contains(v[i]) }) {
                            val iv = frees[i].minByOrNull { abs(it.clamp(v[i]) - v[i]) }!!
                            v[i] = iv.clamp(v[i]).coerceIn(v[i - 1] - d, v[i - 1] + d)
                        }
                    }
                }
                return v
            }
            var best: List<Double> = ArrayList(xs)
            var bestGap = minGap(best)
            for (backward in booleanArrayOf(true, false)) {
                val v = repaired(backward)
                if (!fits(v) || v.zipWithNext().any { (p, q) -> abs(q - p) > d + 1e-6 }) continue
                val g = minGap(v)
                if (g > bestGap + 0.01) { best = v; bestGap = g }
            }
            // 두 방식을 이어 붙인 경우(진입은 미리, 이탈은 늦게)도 시도
            val both = repaired(true).let { first ->
                val saved = ArrayList(xs); xs.clear(); xs.addAll(first)
                val second = if (fits(first)) repaired(false) else first
                xs.clear(); xs.addAll(saved); second
            }
            if (fits(both) && both.zipWithNext().none { (p, q) -> abs(q - p) > d + 1e-6 } && minGap(both) > bestGap + 0.01) best = both
            for (i in 0 until n) xs[i] = best[i]
        }
        // 3) 보정 후에도 들어갈 수 있는지 확인
        var limit = n
        for (i in 0 until n) {
            if (feasible(world, frontal, startY, ys[i]).none { it.contains(xs[i]) || abs(it.clamp(xs[i]) - xs[i]) < 0.02 }) {
                limit = i; break
            }
        }
        if (limit == n && blockedAt < 0) {
            return finish(world, (0 until n).map { xs[it] to ys[it] }, Outcome.CLEAR, null, 0,
                if (frontal.isNotEmpty()) "대향차 옆으로 비켜 통과 가능" else "끝까지 통과 가능")
        }
        val stopIndex = (if (blockedAt >= 0) min(blockedAt, limit) else limit)
        // 4) 대향차가 있으면 양보 정차 위치 탐색 (오른쪽 우선, 한국 우측통행)
        if (frontal.isNotEmpty()) {
            for (side in intArrayOf(1, -1)) {
                val pull = findPullIn(world, frontal, ys, startX, startY, stopIndex, side) ?: continue
                return finish(world, pull, Outcome.YIELD, pull.last().second, side,
                    if (side > 0) "오른쪽에 붙어 정차 후 대향차 먼저 통과" else "왼쪽에 붙어 정차 후 대향차 먼저 통과")
            }
        }
        // 5) 막힘: 막힌 지점 1.5m 앞에서 정차
        val stopY = (ys.getOrNull(stopIndex) ?: ys.last()) - 1.5
        val pts = (0 until n).map { xs[it] to ys[it] }.filter { it.second <= max(startY, stopY) }
            .ifEmpty { listOf(startX to startY) }
        return finish(world, pts, Outcome.BLOCKED, pts.last().second, 0,
            if (frontal.isNotEmpty()) "양보할 공간 없음 · 정차 후 후진 필요" else "차 폭보다 좁은 구간 · 정차 필요")
    }

    /** 대향차가 지나갈 공간을 남기고 side 쪽으로 붙어 설 수 있는 가장 앞쪽 위치까지의 경로 */
    private fun findPullIn(world: AlleyWorld, frontal: List<AlleyWorld.ParkedCar>, ys: List<Double>,
                           startX: Double, startY: Double, stopIndex: Int, side: Int): List<Pair<Double, Double>>? {
        val d = MAX_SLOPE * STEP
        val lastFrontalY = frontal.minOf { it.y - it.length / 2 }
        // 대향차 앞 2m 까지가 정차 후보 (그보다 가까이 가지 않음)
        val maxJ = ys.indexOfLast { it + EGO_HALF_L <= lastFrontalY - 2.0 }
        for (j in maxJ downTo 1) {
            val y = ys[j]
            val free = feasibleStatic(world, frontal, y)
            if (free.isEmpty()) continue
            val iv = if (side > 0) free.maxByOrNull { it.hi }!! else free.minByOrNull { it.lo }!!
            val xp = if (side > 0) iv.hi else iv.lo
            // 대향차가 지나갈 공간이 남는지
            val roomOk = frontal.all { f ->
                if (side > 0) xp - EGO_HALF_W >= f.x + f.width / 2 + PASS_MARGIN - 1e-9 ||
                    (xp - EGO_HALF_W) - world.leftWallX >= f.width + 2 * PASS_MARGIN
                else xp + EGO_HALF_W <= f.x - f.width / 2 - PASS_MARGIN + 1e-9 ||
                    world.rightWallX - (xp + EGO_HALF_W) >= f.width + 2 * PASS_MARGIN
            }
            if (!roomOk) continue
            if (abs(xp - startX) > j * d + 1e-9) continue        // 그 거리 안에 옆으로 이동 불가
            // 출발점→정차점까지 등속 횡이동 경로가 정적 장애물과 겹치지 않는지 확인
            val path = (0..j).map { i ->
                val k = i.toDouble() / j
                // 앞부분은 직진하다가 후반에 붙는 부드러운 경로
                val s = (k * k * (3 - 2 * k))
                (startX + (xp - startX) * s) to ys[i]
            }
            val ok = path.withIndex().all { (i, p) ->
                i == 0 || feasibleStatic(world, frontal, p.second).any { it.contains(p.first) || abs(it.clamp(p.first) - p.first) < 0.02 }
            } && path.zipWithNext().all { (a, b) -> abs(a.first - b.first) <= d + 1e-6 }
            if (ok) return path
        }
        return null
    }

    private fun finish(world: AlleyWorld, pts: List<Pair<Double, Double>>, outcome: Outcome,
                       stopY: Double?, yieldSide: Int, note: String): Plan {
        val trial = AlleyWorld(world.parkedCars, world.safetyBollards, world.leftWallX, world.rightWallX, world.endLineY)
        val out = pts.mapIndexed { i, (x, y) ->
            val a = pts.getOrNull(i - 1) ?: pts.getOrNull(i)!!
            val b = pts.getOrNull(i + 1) ?: pts[i]
            val heading = if (b.second - a.second == 0.0) 0.0 else atan2(b.first - a.first, b.second - a.second)
            trial.restore(x, y, heading, false)
            val c = trial.clearance()
            Point(x, y, heading, c.leftMeters, c.rightMeters, c.meters, c.side)
        }
        val narrowest = out.minByOrNull { it.min }
        return Plan(out, outcome, stopY, yieldSide,
            out.minOfOrNull { it.left } ?: Double.NaN, out.minOfOrNull { it.right } ?: Double.NaN, narrowest, note)
    }
}
