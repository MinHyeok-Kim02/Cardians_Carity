package com.example.pleostest

/** Pleos emulator readback: positive = left. Scene coordinates: positive = right.
 * Keep the SDK raw value unchanged; only convert the scene input coordinate convention.
 * The normalized angle remains a demo mapping, not a calibrated physical wheel angle.
 */
object PleosSteeringMapping {
    fun toSimulation(raw: Float?, range: Float): Float? {
        if (raw == null || !raw.isFinite() || !range.isFinite() || range <= 0f) return null
        return (-raw / range).coerceIn(-1f, 1f)
    }
}
