package com.example.myapplication;

/** UI-thread input gate. Stale signals and elapsed time during pauses never move the car. */
final class VehicleMotionController {
    enum Status { OFF, PAUSED, RESTRICTED, WAITING, STALE, STOPPED, MOVING, WALL }
    private final AlleySimulation simulation;
    private boolean enabled, foreground, uxKnown, requiresDo;
    private Double speedKmh, steeringDegrees;
    private long speedAt, steeringAt, lastTick = -1;
    private Status status = Status.OFF;

    VehicleMotionController(AlleySimulation simulation) { this.simulation = simulation; }
    void setEnabled(boolean value) { enabled = value; lastTick = -1; }
    void setForeground(boolean value) {
        foreground = value;
        lastTick = -1;
        if (!value) clearSignals();
    }
    void setRestrictions(boolean known, boolean requiresDo) {
        uxKnown = known;
        this.requiresDo = requiresDo;
        lastTick = -1;
    }
    void speed(double value, long now) {
        speedKmh = Double.isFinite(value) ? value : null;
        speedAt = now;
    }
    void steering(double value, long now) {
        steeringDegrees = Double.isFinite(value) ? value : null;
        steeringAt = now;
    }
    void clearSpeed() { speedKmh = null; }
    void clearSteering() { steeringDegrees = null; }
    void clearSignals() { speedKmh = null; steeringDegrees = null; lastTick = -1; }
    Status status() { return status; }
    boolean tick(long now) {
        long previous = lastTick;
        lastTick = now;
        if (!enabled) { status = Status.OFF; return false; }
        if (!foreground) { status = Status.PAUSED; return false; }
        if (!uxKnown || requiresDo) { status = Status.RESTRICTED; return false; }
        if (speedKmh == null || steeringDegrees == null) { status = Status.WAITING; return false; }
        if (now < speedAt || now < steeringAt || now - speedAt > 5000 || now - steeringAt > 5000) {
            status = Status.STALE;
            return false;
        }
        if (Math.abs(speedKmh) < .01) { status = Status.STOPPED; return false; }
        if (previous < 0 || now <= previous || now - previous > 250) {
            status = Status.WAITING;
            return false;
        }
        boolean moved = simulation.drive(speedKmh / 3.6, steeringDegrees, (now - previous) / 1000.0);
        status = moved ? Status.MOVING : Status.WALL;
        return moved;
    }
}
