package com.example.myapplication;

import java.util.ArrayList;
import java.util.List;

/** All world coordinates are metres. Walls extend along the y axis. */
public final class AlleySimulation {
    public static final class Scenario {
        public final double leftWall = 0, rightWall = 4.0;
        public final double carWidth = 1.8, carLength = 4.0;
        public final double initialX = 2.0, initialY = 0, initialHeading = 0;
        public final double stepMetres = 0.25, steeringDegrees = 5;
    }
    public enum Level {
        CLEAR, CAUTION, NEAR;
        public static Level from(double metres) {
            return metres <= .30 + 1e-9 ? NEAR : metres <= .60 + 1e-9 ? CAUTION : CLEAR;
        }
    }
    public enum Side { LEFT, RIGHT }
    public final Scenario scenario;
    public double x, y, heading;
    private boolean leftNear, rightNear;

    public AlleySimulation() { this(new Scenario()); }
    public AlleySimulation(Scenario scenario) { this.scenario = scenario; reset(); }
    public void reset() {
        x = scenario.initialX; y = scenario.initialY; heading = scenario.initialHeading;
        leftNear = rightNear = false;
    }
    public void steer(double degrees) {
        heading = Math.max(-35, Math.min(35, heading + degrees));
    }
    public void forward() {
        double angle = Math.toRadians(heading);
        x += Math.sin(angle) * scenario.stepMetres;
        y += Math.cos(angle) * scenario.stepMetres;
        clampPositionToWalls(angle);
    }
    /** Bicycle approximation for a virtual vehicle; not calibrated to a real car.
     * Stops before the virtual body crosses either wall. */
    public boolean drive(double metresPerSecond, double steeringWheelDegrees, double seconds) {
        if (!Double.isFinite(metresPerSecond) || !Double.isFinite(steeringWheelDegrees)
                || !Double.isFinite(seconds) || seconds <= 0 || seconds > .25) return false;
        double wheelAngle = Math.toRadians(Math.max(-35, Math.min(35, steeringWheelDegrees / 16.0)));
        int steps = Math.max(1, (int) Math.ceil(seconds / .02));
        double dt = seconds / steps;
        boolean moved = false;
        for (int i = 0; i < steps; i++) {
            double turn = metresPerSecond / 2.7 * Math.tan(wheelAngle) * dt;
            double middleHeading = Math.toRadians(heading) + turn / 2;
            double nextHeading = heading + Math.toDegrees(turn);
            double angle = Math.toRadians(nextHeading);
            double nextX = x + Math.sin(middleHeading) * metresPerSecond * dt;
            double extent = scenario.carWidth / 2 * Math.abs(Math.cos(angle))
                    + scenario.carLength / 2 * Math.abs(Math.sin(angle));
            if (nextX - extent < scenario.leftWall || nextX + extent > scenario.rightWall) break;
            x = nextX;
            y += Math.cos(middleHeading) * metresPerSecond * dt;
            heading = ((nextHeading + 180) % 360 + 360) % 360 - 180;
            moved |= Math.abs(metresPerSecond) > 1e-6;
        }
        return moved;
    }

    /** Stop lateral travel at the wall so repeated forward taps keep the car visible. */
    private void clampPositionToWalls(double angle) {
        double extent = scenario.carWidth / 2 * Math.abs(Math.cos(angle))
                + scenario.carLength / 2 * Math.abs(Math.sin(angle));
        x = Math.max(scenario.leftWall + extent, Math.min(scenario.rightWall - extent, x));
    }
    /** Rotated rectangle vertices, ordered around the perimeter; heading 0 points up. */
    public double[][] corners() {
        double a = Math.toRadians(heading), sin = Math.sin(a), cos = Math.cos(a);
        double w = scenario.carWidth / 2, l = scenario.carLength / 2;
        double[][] local = {{-w,-l},{w,-l},{w,l},{-w,l}};
        for (double[] p : local) rotateCornerIntoWorld(p, sin, cos);
        return local;
    }
    private void rotateCornerIntoWorld(double[] point, double sin, double cos) {
        double localX = point[0], localY = point[1];
        point[0] = x + localX * cos + localY * sin;
        point[1] = y - localX * sin + localY * cos;
    }
    /** Projection of every rotated corner gives the exact gap to infinite straight walls.
     * Crossing/overlap with either wall is clamped to zero. */
    public double gap(Side side) {
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (double[] p : corners()) { min = Math.min(min, p[0]); max = Math.max(max, p[0]); }
        return Math.max(0, side == Side.LEFT ? min - scenario.leftWall : scenario.rightWall - max);
    }
    /** Edge-triggered events: one per side until that side leaves NEAR. */
    public List<Side> pollEntries() {
        boolean left = Level.from(gap(Side.LEFT)) == Level.NEAR;
        boolean right = Level.from(gap(Side.RIGHT)) == Level.NEAR;
        List<Side> entries = new ArrayList<>();
        if (left && !leftNear) entries.add(Side.LEFT);
        if (right && !rightNear) entries.add(Side.RIGHT);
        leftNear = left; rightNear = right;
        return entries;
    }
}
