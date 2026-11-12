package com.example.pleostest

import kotlin.math.*

/** Entire scene and all distances are synthetic. Input speed/steering come from the emulator. */
class AlleyWorld {
    data class Point(val x: Double, val y: Double)
    data class ParkedCar(val x: Double, val y: Double, val width: Double = 1.8, val length: Double = 4.4)
    data class Clearance(val meters: Double, val side: String, val approaching: Boolean, val contact: Boolean)
    val parkedCars = listOf(
        ParkedCar(-2.5, 14.0), ParkedCar(2.5, 22.0),
        ParkedCar(-2.4, 34.0), ParkedCar(2.4, 38.0),
        ParkedCar(-2.5, 52.0), ParkedCar(2.5, 64.0)
    )
    val roadHalfWidth = 3.5
    var x = 0.0; private set
    var y = 4.0; private set
    var heading = 0.0; private set
    var contact = false; private set
    private var lastDistance: Double? = null

    fun reset() { x = 0.0; y = 4.0; heading = 0.0; contact = false; lastDistance = null }
    fun restore(px: Double, py: Double, angle: Double, collided: Boolean) {
        if (px.isFinite() && py.isFinite() && angle.isFinite()) {
            x = px; y = py; heading = angle; contact = collided
        }
    }
    fun corners() = listOf(
        Point(-0.9, -2.1), Point(0.9, -2.1), Point(0.9, 2.1), Point(-0.9, 2.1)
    ).map { Point(x + it.x * cos(heading) + it.y * sin(heading), y - it.x * sin(heading) + it.y * cos(heading)) }

    fun advance(seconds: Double, speedMps: Double, steering: Double) {
        if (contact || !seconds.isFinite() || !speedMps.isFinite() || !steering.isFinite() || seconds <= 0) return
        val dt = seconds.coerceAtMost(0.1)
        val steps = max(1, ceil(abs(speedMps) * dt / 0.08).toInt())
        repeat(steps) {
            if (contact) return
            val step = dt / steps
            // Explicit demo mapping: normalized SDK angle -> +/-28 degree model front-wheel angle.
            val frontAngle = steering.coerceIn(-1.0, 1.0) * Math.toRadians(28.0)
            heading += speedMps / 2.7 * tan(frontAngle) * step
            x += sin(heading) * speedMps * step
            y += cos(heading) * speedMps * step
            if (measure().meters <= 0.0) contact = true
        }
    }

    fun clearance(): Clearance {
        val current = measure()
        val approaching = lastDistance?.let { current.meters < it - 0.002 } ?: false
        lastDistance = current.meters
        return current.copy(approaching = approaching, contact = contact || current.meters <= 0)
    }

    private fun measure(): Clearance {
        val body = corners()
        val left = body.minOf { it.x } + roadHalfWidth
        val right = roadHalfWidth - body.maxOf { it.x }
        var distance = min(left, right).coerceAtLeast(0.0)
        var side = if (left < right) "왼쪽 골목 경계" else "오른쪽 골목 경계"
        for (car in parkedCars) {
            if (abs(car.y - y) > 18) continue
            val obstacle = listOf(
                Point(car.x - car.width / 2, car.y - car.length / 2),
                Point(car.x + car.width / 2, car.y - car.length / 2),
                Point(car.x + car.width / 2, car.y + car.length / 2),
                Point(car.x - car.width / 2, car.y + car.length / 2)
            )
            val gap = polygonDistance(body, obstacle)
            if (gap < distance) {
                distance = gap
                val lateral = (car.x - x) * cos(heading) - (car.y - y) * sin(heading)
                side = if (abs(lateral) < 0.5) "전방 주차 차량" else if (lateral < 0) "왼쪽 주차 차량" else "오른쪽 주차 차량"
            }
        }
        return Clearance(distance, side, false, contact)
    }

    private fun polygonDistance(a: List<Point>, b: List<Point>): Double {
        val separated = (a.indices.map { a[it] to a[(it + 1) % 4] } +
            b.indices.map { b[it] to b[(it + 1) % 4] }).any { (p, q) ->
            val nx = -(q.y - p.y); val ny = q.x - p.x
            val pa = a.map { it.x * nx + it.y * ny }; val pb = b.map { it.x * nx + it.y * ny }
            pa.maxOrNull()!! < pb.minOrNull()!! || pb.maxOrNull()!! < pa.minOrNull()!!
        }
        if (!separated) return 0.0
        return min(
            a.minOf { p -> b.indices.minOf { segmentDistance(p, b[it], b[(it + 1) % 4]) } },
            b.minOf { p -> a.indices.minOf { segmentDistance(p, a[it], a[(it + 1) % 4]) } }
        )
    }
    private fun segmentDistance(p: Point, a: Point, b: Point): Double {
        val dx = b.x - a.x; val dy = b.y - a.y
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        return hypot(p.x - a.x - t * dx, p.y - a.y - t * dy)
    }
}
