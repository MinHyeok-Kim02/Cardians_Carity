package com.example.myapplication;

/** Immutable UI-to-GL snapshot. Coordinates map (x,y) to (x,height,-y).
 * Constructed on the UI thread; never reads the mutable model from the GL thread. */
final class AlleyScene {
    final float x, z, heading, width, length, leftWall, rightWall;
    final AlleySimulation.Level[] levels = new AlleySimulation.Level[2];
    final long[] entered;

    AlleyScene(AlleySimulation model, long[] entered) {
        x = (float) model.x;
        z = (float) -model.y;
        heading = (float) model.heading;
        width = (float) model.scenario.carWidth;
        length = (float) model.scenario.carLength;
        leftWall = (float) model.scenario.leftWall;
        rightWall = (float) model.scenario.rightWall;
        for (AlleySimulation.Side side : AlleySimulation.Side.values()) {
            levels[side.ordinal()] = AlleySimulation.Level.from(model.gap(side));
        }
        this.entered = entered.clone();
    }
}
