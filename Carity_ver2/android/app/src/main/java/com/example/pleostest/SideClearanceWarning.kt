package com.example.pleostest

/** One warning source for the real display, cabin illustration and ambient requests. No geometry here. */
class SideClearanceWarning {
    enum class Level(val argb: Int, val label: String) {
        GREEN(0xFF4DDB83.toInt(), "여유"),
        ORANGE(0xFFFFA000.toInt(), "주의"),
        RED(0xFFFF7272.toInt(), "근접"),
        UNKNOWN(0xFF8796A5.toInt(), "미확인")
    }
    data class Side(val level: Level, val visible: Boolean, val ambientArgb: Int = level.argb,
                    val ambientStrength: Float = 1f)
    data class Frame(val left: Side, val right: Side)
    private class Track {
        var level = Level.UNKNOWN
        var enteredAt = 0L
        fun update(next: Level, now: Long, meters: Double): Side {
            if (next != level) { level = next; enteredAt = now }
            val elapsed = (now - enteredAt).coerceAtLeast(0)
            // Full period 500ms: 250ms on + 250ms off, exactly three cycles, then solid.
            val visible = level != Level.RED || elapsed >= 1500 || elapsed % 500 < 250
            val strength=if(!meters.isFinite() || meters<0) 0f else
                ((1.6-meters)/1.3).coerceIn(0.0,1.0).toFloat()
            return Side(level, visible, ambientColor(meters), strength)
        }
    }
    private var left = Track()
    private var right = Track()
    fun update(leftMeters: Double, rightMeters: Double, now: Long) =
        Frame(left.update(level(leftMeters), now, leftMeters), right.update(level(rightMeters), now, rightMeters))
    fun reset() { left = Track(); right = Track() }
    companion object {
        /** Continuous illustration color driven by distance, independent of severity/blink thresholds. */
        fun ambientColor(meters: Double): Int {
            if(!meters.isFinite() || meters<0) return Level.UNKNOWN.argb
            val caution=AlleyPassDistances.CAUTION
            val stops=doubleArrayOf(.3,caution,caution*1.3,caution*2)
            val colors=intArrayOf(Level.RED.argb,Level.ORANGE.argb,0xFFFFD54F.toInt(),Level.GREEN.argb)
            if(meters<=stops.first()) return colors.first()
            if(meters>=stops.last()) return colors.last()
            val i=(0 until stops.lastIndex).first { meters<=stops[it+1] }
            val fraction=(meters-stops[i])/(stops[i+1]-stops[i])
            fun channel(shift: Int): Int {
                val a=(colors[i] ushr shift) and 255
                val b=(colors[i+1] ushr shift) and 255
                return kotlin.math.round(a+(b-a)*fraction).toInt().coerceIn(0,255)
            }
            return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
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
