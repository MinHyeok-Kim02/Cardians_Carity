package com.example.pleostest

/** One warning source for the real display, cabin illustration and ambient requests. No geometry here. */
class SideClearanceWarning {
    enum class Level(val argb: Int, val label: String) {
        GREEN(0xFF4DDB83.toInt(), "여유"),
        ORANGE(0xFFFFA000.toInt(), "주의"),
        RED(0xFFFF7272.toInt(), "근접"),
        UNKNOWN(0xFF8796A5.toInt(), "미확인")
    }
    data class Side(val level: Level, val visible: Boolean)
    data class Frame(val left: Side, val right: Side)
    private class Track {
        var level = Level.UNKNOWN
        var enteredAt = 0L
        fun update(next: Level, now: Long): Side {
            if (next != level) { level = next; enteredAt = now }
            val elapsed = (now - enteredAt).coerceAtLeast(0)
            // Full period 500ms: 250ms on + 250ms off, exactly three cycles, then solid.
            val visible = level != Level.RED || elapsed >= 1500 || elapsed % 500 < 250
            return Side(level, visible)
        }
    }
    private var left = Track()
    private var right = Track()
    fun update(leftMeters: Double, rightMeters: Double, now: Long) =
        Frame(left.update(level(leftMeters), now), right.update(level(rightMeters), now))
    fun reset() { left = Track(); right = Track() }
    companion object {
        fun level(meters: Double) = when {
            !meters.isFinite() || meters < 0 -> Level.UNKNOWN
            meters <= .3 -> Level.RED
            meters <= AlleyPassDistances.CAUTION -> Level.ORANGE
            else -> Level.GREEN
        }
        fun route(areaNames: List<String>, left: Level, right: Level): Level {
            val onlyLeft = areaNames.isNotEmpty() && areaNames.all { it.endsWith("_LEFT") }
            val onlyRight = areaNames.isNotEmpty() && areaNames.all { it.endsWith("_RIGHT") }
            return when { onlyLeft -> left; onlyRight -> right
                left == Level.UNKNOWN || right == Level.UNKNOWN -> Level.UNKNOWN
                else -> if (left.ordinal >= right.ordinal) left else right }
        }
    }
}
