package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleMotionControllerTest {
    private VehicleMotionController ready(AlleySimulation model, double speed, double steering) {
        VehicleMotionController motion = new VehicleMotionController(model);
        motion.setEnabled(true);
        motion.setForeground(true);
        motion.setRestrictions(true, false);
        motion.speed(speed, 1000);
        motion.steering(steering, 1000);
        motion.tick(1000);
        return motion;
    }

    @Test public void tenKmhTravelsCorrectDistanceOverOneSecond() {
        AlleySimulation model = new AlleySimulation();
        VehicleMotionController motion = ready(model, 10, 0);
        for (long time = 1100; time <= 2000; time += 100) assertTrue(motion.tick(time));
        assertEquals(10 / 3.6, model.y, 1e-6);
        assertEquals(2, model.x, 1e-6);
        assertEquals(0, model.heading, 1e-6);
    }

    @Test public void steeringChangesHeadingOnlyWhileMovingAndMirrorsLeftRight() {
        AlleySimulation left = new AlleySimulation(), right = new AlleySimulation();
        VehicleMotionController l = ready(left, 5, -160), r = ready(right, 5, 160);
        for (long time = 1100; time <= 2000; time += 100) { l.tick(time); r.tick(time); }
        assertTrue(left.x < 2);
        assertTrue(right.x > 2);
        assertEquals(4, left.x + right.x, 1e-6);
        assertEquals(-left.heading, right.heading, 1e-6);
        AlleySimulation stopped = new AlleySimulation();
        VehicleMotionController stationary = ready(stopped, 0, 540);
        stationary.tick(1100);
        assertEquals(0, stopped.heading, 0);
        assertEquals(0, stopped.y, 0);
    }

    @Test public void reverseSpeedMovesBackwards() {
        AlleySimulation model = new AlleySimulation();
        VehicleMotionController motion = ready(model, -3.6, 0);
        motion.tick(1100);
        assertEquals(-.1, model.y, 1e-6);
    }

    @Test public void disabledRestrictedOrUnknownUxCannotMove() {
        for (int gate = 0; gate < 3; gate++) {
            AlleySimulation model = new AlleySimulation();
            VehicleMotionController motion = ready(model, 15, 0);
            if (gate == 0) motion.setEnabled(false);
            if (gate == 1) motion.setRestrictions(true, true);
            if (gate == 2) motion.setRestrictions(false, false);
            for (long time = 1100; time <= 2000; time += 100) assertFalse(motion.tick(time));
            assertEquals(0, model.y, 0);
        }
    }

    @Test public void oldOrMissingSignalsDoNotMoveAndResumeDoesNotCatchUp() {
        AlleySimulation model = new AlleySimulation();
        VehicleMotionController motion = ready(model, 10, 0);
        motion.tick(1100);
        double position = model.y;
        assertFalse(motion.tick(7001));
        assertEquals(VehicleMotionController.Status.STALE, motion.status());
        assertEquals(position, model.y, 0);
        motion.setForeground(false);
        motion.setForeground(true);
        assertFalse(motion.tick(7100));
        motion.speed(10, 7100);
        assertFalse(motion.tick(7200)); // Steering must be fresh as well.
        motion.steering(0, 7200);
        assertTrue(motion.tick(7300));
        assertEquals(position + 10 / 3.6 * .1, model.y, 1e-6);
    }

    @Test public void aLongFrameOrToggleDoesNotJumpForward() {
        AlleySimulation model = new AlleySimulation();
        VehicleMotionController motion = ready(model, 15, 0);
        assertFalse(motion.tick(2000)); // One second pause is not integrated.
        assertEquals(0, model.y, 0);
        motion.setEnabled(false);
        motion.tick(2100);
        motion.setEnabled(true);
        assertFalse(motion.tick(2200));
        assertEquals(0, model.y, 0);
    }

    @Test public void virtualWallsContainTheBodyDuringTurns() {
        AlleySimulation model = new AlleySimulation();
        VehicleMotionController motion = ready(model, 15, 540);
        for (long time = 1100; time <= 5000; time += 100) motion.tick(time);
        for (double[] corner : model.corners()) {
            assertTrue(corner[0] >= model.scenario.leftWall);
            assertTrue(corner[0] <= model.scenario.rightWall);
        }
        assertEquals(VehicleMotionController.Status.WALL, motion.status());
    }
}
