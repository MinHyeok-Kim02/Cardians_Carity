package com.example.pleostest;

/** Immutable UI-to-GL snapshot. Coordinates (x,y) become (x,height,-y). */
final class Alley3dFrame {
    final float x, z, heading, width, length, leftWall, rightWall;
    final float[] gaps;
    final float[][] obstacles, bollards;
    Alley3dFrame(float x, float y, float headingRadians, float[] gaps, float[][] obstacles, float[][] bollards) {
        this.x=x; this.z=-y; this.heading=(float)Math.toDegrees(headingRadians);
        width=1.8f; length=4.2f; leftWall=-3.5f; rightWall=3.5f;
        this.gaps=gaps.clone();
        this.obstacles=new float[obstacles.length][];
        for(int i=0;i<obstacles.length;i++) this.obstacles[i]=obstacles[i].clone();
        this.bollards=new float[bollards.length][];
        for(int i=0;i<bollards.length;i++) this.bollards[i]=bollards[i].clone();
    }
    Alley3dFrame(float[] obstacle) {
        x=obstacle[0]; z=-obstacle[1]; width=obstacle[2]; length=obstacle[3];
        heading=obstacle[4]; leftWall=-3.5f; rightWall=3.5f;
        gaps=new float[]{10,10}; obstacles=new float[0][]; bollards=new float[0][];
    }
}
