package com.example.pleostest

import kotlin.math.*

/** All geometry is synthetic. Only ego speed and steering come from the emulator. */
class AlleyWorld(
    val parkedCars: List<ParkedCar> = DEFAULT_PARKED
) {
    data class Point(val x: Double, val y: Double)
    data class ParkedCar(val x: Double, val y: Double, val width: Double = 1.8, val length: Double = 4.4)
    data class Clearance(
        val meters: Double, val side: String, val approaching: Boolean, val contact: Boolean,
        val leftMeters: Double, val rightMeters: Double, val frontMeters: Double, val rearMeters: Double
    )
    enum class Advice { STOP, LEFT, RIGHT, STRAIGHT, WAIT }
    data class Guide(val advice: Advice, val message: String)

    val roadHalfWidth = 3.5
    var x = 0.0; private set
    var y = 4.0; private set
    var heading = 0.0; private set
    var contact = false; private set
    var simulationSeconds = 0.0; private set
    private var lastDistance: Double? = null
    fun obstacles(): List<ParkedCar> = parkedCars
    fun reset() { x = 0.0; y = 4.0; heading = 0.0; contact = false; simulationSeconds = 0.0; lastDistance = null }
    fun restore(px: Double, py: Double, angle: Double, collided: Boolean, seconds: Double = 0.0) {
        if (px.isFinite() && py.isFinite() && angle.isFinite() && seconds.isFinite() && seconds >= 0) {
            x = px; y = py; heading = angle; contact = collided; simulationSeconds = seconds; lastDistance = null
        }
    }
    fun corners() = listOf(Point(-.9, -2.1), Point(.9, -2.1), Point(.9, 2.1), Point(-.9, 2.1))
        .map { Point(x + it.x * cos(heading) + it.y * sin(heading),
            y - it.x * sin(heading) + it.y * cos(heading)) }

    fun advance(seconds: Double, speedMps: Double, steering: Double) {
        if (contact || !seconds.isFinite() || !speedMps.isFinite() || !steering.isFinite() || seconds <= 0) return
        if (measure().meters <= 0) { contact = true; return }
        val dt = seconds.coerceAtMost(.1)
        val relativeSpeed = abs(speedMps)
        val steps = max(1, ceil(relativeSpeed * dt / .08).toInt())
        repeat(steps) {
            if (contact) return
            val step = dt / steps
            val frontAngle = steering.coerceIn(-1.0, 1.0) * Math.toRadians(28.0)
            heading += speedMps / 2.7 * tan(frontAngle) * step
            x += sin(heading) * speedMps * step
            y += cos(heading) * speedMps * step
            simulationSeconds += step
            if (measure().meters <= 0) contact = true
        }
    }

    fun clearance(): Clearance {
        val current = measure()
        val approaching = lastDistance?.let { current.meters < it - .002 } ?: false
        lastDistance = current.meters
        return current.copy(approaching = approaching, contact = contact || current.meters <= 0)
    }

    /** Conservative demo advice: compare whole-body clearance of three short simulated motions.
     * No control command is sent. Paths are never drawn as Ghost View.
     */
    fun guidance(speedMps: Double): Guide {
        val current = measure()
        if (!speedMps.isFinite()) return Guide(Advice.WAIT, "차량 입력을 기다려 주세요")
        if (current.contact || current.meters <= .3) return Guide(Advice.STOP, "정차 후 주변 간격을 확인하세요")
        if (abs(speedMps) * 3.6 > 10) return Guide(Advice.STOP, "속도를 낮추고 주변을 확인하세요")
        if (abs(speedMps) < .1) return Guide(Advice.WAIT, "정차 중 · 주변을 확인하세요")
        if (speedMps < 0) return Guide(Advice.STOP, "후진 중 · 정차 후 후방을 확인하세요")
        fun score(turn: Double): Double {
            val trial = AlleyWorld(parkedCars)
            trial.restore(x, y, heading, false, simulationSeconds)
            var minimum = current.meters
            repeat(25) {
                trial.advance(.1, speedMps, turn)
                minimum = min(minimum, trial.measure().meters)
            }
            return if (trial.contact) -1.0 else minimum
        }
        val straight = score(0.0)
        val left = score(-.35)
        val right = score(.35)
        val best = max(straight, max(left, right))
        if (best < .45) return Guide(Advice.STOP, "정차하고 교행 공간을 확인하세요")
        if (straight >= .6 || straight >= max(left, right) - .12)
            return Guide(Advice.STRAIGHT, "조향을 중앙에 가깝게 유지하며 천천히 이동하세요")
        return if (left > right)
            Guide(Advice.LEFT, "가상 경로 비교: 왼쪽 여유를 확인하며 천천히 조향하세요")
        else Guide(Advice.RIGHT, "가상 경로 비교: 오른쪽 여유를 확인하며 천천히 조향하세요")
    }

    private fun measure(): Clearance {
        val body = corners()
        val leftWall = (body.minOf { it.x } + roadHalfWidth).coerceAtLeast(0.0)
        val rightWall = (roadHalfWidth - body.maxOf { it.x }).coerceAtLeast(0.0)
        var distance = min(leftWall, rightWall)
        var label = if (leftWall < rightWall) "왼쪽 골목 경계" else "오른쪽 골목 경계"
        // Each entire edge is measured, including its midpoint, not only its endpoints.
        val edges = listOf(3 to 0, 1 to 2, 2 to 3, 0 to 1)
        val gaps = edges.map { (a,b) ->
            min(min(body[a].x, body[b].x) + roadHalfWidth,
                roadHalfWidth - max(body[a].x, body[b].x)).coerceAtLeast(0.0)
        }.toMutableList()
        for (car in obstacles()) {
            if (abs(car.y - y) > 18) continue
            val obstacle = rectangle(car)
            val gap = polygonDistance(body, obstacle)
            for ((index, edge) in edges.withIndex())
                gaps[index] = min(gaps[index], edgeDistance(body[edge.first], body[edge.second], obstacle))
            if (gap < distance) {
                distance = gap
                val lateral = (car.x - x) * cos(heading) - (car.y - y) * sin(heading)
                val forward = (car.x - x) * sin(heading) + (car.y - y) * cos(heading)
                val direction = if (abs(lateral) < .5) {
                    if (forward >= 0) "전방" else "후방"
                } else if (lateral < 0) "왼쪽" else "오른쪽"
                label = direction + " 주차 차량"
            }
        }
        return Clearance(distance, label, false, contact || distance <= 0,
            gaps[0], gaps[1], gaps[2], gaps[3])
    }

    companion object {
        val DEFAULT_PARKED = listOf(
            ParkedCar(-2.5, 14.0), ParkedCar(2.5, 22.0),
            ParkedCar(-2.4, 34.0), ParkedCar(2.4, 38.0),
            ParkedCar(-2.5, 52.0), ParkedCar(2.5, 64.0))
        fun rectangle(car: ParkedCar) = listOf(
            Point(car.x-car.width/2, car.y-car.length/2), Point(car.x+car.width/2, car.y-car.length/2),
            Point(car.x+car.width/2, car.y+car.length/2), Point(car.x-car.width/2, car.y+car.length/2))

        internal fun polygonDistance(a: List<Point>, b: List<Point>): Double {
            val separated = (a.indices.map { a[it] to a[(it+1)%a.size] } +
                b.indices.map { b[it] to b[(it+1)%b.size] }).any { (p,q) ->
                val nx = -(q.y-p.y); val ny = q.x-p.x
                val pa = a.map { it.x*nx + it.y*ny }; val pb = b.map { it.x*nx + it.y*ny }
                pa.max() < pb.min() || pb.max() < pa.min()
            }
            if (!separated) return 0.0
            return a.indices.minOf { edgeDistance(a[it], a[(it+1)%a.size], b) }
        }
        private fun edgeDistance(a: Point, b: Point, polygon: List<Point>): Double =
            polygon.indices.minOf { i ->
                val c = polygon[i]; val d = polygon[(i+1)%polygon.size]
                segmentPairDistance(a,b,c,d)
            }
        internal fun segmentPairDistance(a: Point, b: Point, c: Point, d: Point): Double {
            fun cross(p: Point, q: Point, r: Point) =
                (q.x-p.x)*(r.y-p.y) - (q.y-p.y)*(r.x-p.x)
            val abC = cross(a,b,c); val abD = cross(a,b,d)
            val cdA = cross(c,d,a); val cdB = cross(c,d,b)
            val overlapX = max(min(a.x,b.x), min(c.x,d.x)) <= min(max(a.x,b.x), max(c.x,d.x))
            val overlapY = max(min(a.y,b.y), min(c.y,d.y)) <= min(max(a.y,b.y), max(c.y,d.y))
            if (abC*abD <= 0 && cdA*cdB <= 0 && overlapX && overlapY) return 0.0
            return min(min(segmentDistance(a,c,d), segmentDistance(b,c,d)),
                min(segmentDistance(c,a,b), segmentDistance(d,a,b)))
        }
        private fun segmentDistance(p: Point, a: Point, b: Point): Double {
            val dx=b.x-a.x; val dy=b.y-a.y
            val length2=dx*dx+dy*dy
            val t=if(length2 == 0.0) 0.0 else (((p.x-a.x)*dx+(p.y-a.y)*dy)/length2).coerceIn(0.0,1.0)
            return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
        }
    }
}
