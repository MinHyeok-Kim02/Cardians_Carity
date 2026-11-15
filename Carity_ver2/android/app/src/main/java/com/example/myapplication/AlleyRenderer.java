package com.example.myapplication;

import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Draws supplied snapshots only; vehicle physics and clearance stay in AlleySimulation. */
final class AlleyRenderer implements GLSurfaceView.Renderer {
    volatile AlleyScene frame;
    private final Runnable requestNextFrame;

    AlleyRenderer(Runnable requestNextFrame) { this.requestNextFrame = requestNextFrame; }
    private final float[] projection=new float[16], view=new float[16], vp=new float[16];
    private final float[] model=new float[16], mvp=new float[16], car=new float[16], identity=new float[16];
    private FloatBuffer mesh;
    private int program, position, normal, uMvp, uModel, uColor, uUnlit;
    private final long[] loggedEvent={-1,-1};
    private final int[] loggedPhase={-1,-1};

    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
        createShaderProgram();
        locateShaderInputs();
        mesh=cube();
        Matrix.setIdentityM(identity,0);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClearColor(.68f,.82f,.91f,1);
        Log.i("Carity","GL_READY renderer="+GLES20.glGetString(GLES20.GL_RENDERER));
    }
    private void createShaderProgram() {
        String vertex="uniform mat4 uMvp; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal;"
                +"varying float light; varying float depth; uniform float uUnlit; void main(){"
                +"vec3 n=normalize(mat3(uModel)*aNormal);"
                +"light=mix(0.48+0.52*max(dot(n,normalize(vec3(-0.4,1.0,0.6))),0.0),1.0,uUnlit);"
                +"gl_Position=uMvp*vec4(aPosition,1.0); depth=gl_Position.w;}";
        String fragment="precision mediump float; uniform vec3 uColor; varying float light; varying float depth;"
                +"void main(){gl_FragColor=vec4(mix(uColor*light,vec3(0.68,0.82,0.91),smoothstep(24.0,65.0,depth)),1.0);}";
        program=GLES20.glCreateProgram();
        GLES20.glAttachShader(program,shader(GLES20.GL_VERTEX_SHADER,vertex));
        GLES20.glAttachShader(program,shader(GLES20.GL_FRAGMENT_SHADER,fragment));
        GLES20.glLinkProgram(program);
        int[] linked=new int[1]; GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
        if(linked[0]==0) throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
    }
    private void locateShaderInputs() {
        position=GLES20.glGetAttribLocation(program,"aPosition");
        normal=GLES20.glGetAttribLocation(program,"aNormal");
        uMvp=GLES20.glGetUniformLocation(program,"uMvp");
        uModel=GLES20.glGetUniformLocation(program,"uModel");
        uColor=GLES20.glGetUniformLocation(program,"uColor");
        uUnlit=GLES20.glGetUniformLocation(program,"uUnlit");
    }
    @Override public void onSurfaceChanged(GL10 gl,int width,int height) {
        GLES20.glViewport(0,0,width,height);
        Matrix.perspectiveM(projection,0,48f,(float)width/Math.max(1,height),.1f,100f);
    }
    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        AlleyScene f = frame;
        if (f == null) return;
        updateCamera(f);
        bindMesh();
        drawGround(f);
        drawWalls(f);
        drawRoadJoints(f);
        drawNeighborhood(f);
        updateCarTransform(f);
        drawCarBody(f);
        drawLamps(f);
        drawWheels(f);
        drawDirectionMarker(f);
        if (drawGuidelines(f)) requestNextFrame.run();
    }
    private void updateCamera(AlleyScene f) {
        double a=Math.toRadians(f.heading);
        // Elevated chase camera keeps the street and both clearances visible.
        Matrix.setLookAtM(view,0,f.x-(float)Math.sin(a)*8.8f,6.6f,
                f.z+(float)Math.cos(a)*8.8f,f.x,1f,f.z-5.5f,0,1,0);
        Matrix.multiplyMM(vp,0,projection,0,view,0);
    }
    private void bindMesh() {
        GLES20.glUseProgram(program);
        GLES20.glEnableVertexAttribArray(position); GLES20.glEnableVertexAttribArray(normal);
        mesh.position(0); GLES20.glVertexAttribPointer(position,3,GLES20.GL_FLOAT,false,24,mesh);
        mesh.position(3); GLES20.glVertexAttribPointer(normal,3,GLES20.GL_FLOAT,false,24,mesh);
    }
    private void drawGround(AlleyScene f) {
        float middle=(f.leftWall+f.rightWall)/2;
        box(identity,middle,-.18f,f.z-12,90,.30f,120,.49f,.62f,.45f,false);
        box(identity,middle,-.025f,f.z-12,f.rightWall-f.leftWall,.05f,100,.23f,.27f,.30f,false);
        for(int side=0;side<2;side++) {
            float edge=side==0?f.leftWall:f.rightWall, sign=side==0?-1:1;
            box(identity,edge+sign*1.05f,.05f,f.z-12,2.1f,.10f,100,.70f,.72f,.68f,false);
            box(identity,edge-sign*.14f,.007f,f.z-12,.055f,.012f,100,.94f,.84f,.48f,true);
        }
    }
    private void drawWalls(AlleyScene f) {
        // Inner faces coincide exactly with the simulation wall coordinates.
        for(int side=0;side<2;side++) {
            float edge=side==0?f.leftWall-.13f:f.rightWall+.13f;
            box(identity,edge,.30f,f.z-12,.26f,.60f,100,.69f,.68f,.61f,false);
            box(identity,edge,.62f,f.z-12,.30f,.06f,100,.88f,.86f,.78f,false);
        }
    }
    private void drawRoadJoints(AlleyScene f) {
        int start=(int)Math.floor(f.z/4)*4-48;
        for(int z=start;z<start+72;z+=4) {
            box(identity,f.leftWall+.38f,.006f,z,.18f,.008f,.75f,.27f,.31f,.33f,false);
            for(int side=0;side<2;side++) {
                float sign=side==0?-1:1, edge=side==0?f.leftWall:f.rightWall;
                box(identity,edge+sign*1.1f,.106f,z,1.8f,.008f,.025f,.54f,.57f,.54f,false);
                box(identity,edge+sign*.13f,.32f,z,.275f,.56f,.025f,.57f,.56f,.50f,false);
            }
        }
    }
    private void drawNeighborhood(AlleyScene f) {
        // World-aligned blocks do not slide along with the chase camera.
        int start=(int)Math.floor(f.z/10)*10-50;
        for(int z=start;z<start+70;z+=10) {
            int block=Math.floorMod(z/10,5);
            for(int side=0;side<2;side++) {
                float sign=side==0?-1:1, edge=side==0?f.leftWall:f.rightWall;
                float height=3.6f+((block+side)%3)*1.2f, center=edge+sign*4.2f;
                float r=side==0?.80f:.72f, g=side==0?.73f:.79f, b=side==0?.61f:.81f;
                box(identity,center,height/2,z,4.2f,height,6.6f,r,g,b,false);
                box(identity,center,height+.10f,z,4.45f,.20f,6.85f,.32f,.39f,.43f,false);
                box(identity,center,.25f,z,4.3f,.5f,6.7f,.55f,.59f,.58f,false);
                float face=edge+sign*2.09f;
                for(float floor=1.45f;floor<height-.3f;floor+=1.35f) {
                    for(int window=-1;window<=1;window++) {
                        box(identity,face,floor,z+window*1.8f,.035f,.82f,1.05f,.20f,.36f,.43f,false);
                        box(identity,face-sign*.025f,floor-.44f,z+window*1.8f,.10f,.07f,1.18f,.91f,.88f,.78f,false);
                        box(identity,face-sign*.025f,floor,z+window*1.8f,.06f,.83f,.045f,.76f,.80f,.79f,false);
                    }
                }
                float treeX=edge+sign*1.25f, treeZ=z+4;
                box(identity,treeX,.28f,treeZ,.80f,.45f,.85f,.48f,.51f,.45f,false);
                box(identity,treeX,1.12f,treeZ,.16f,1.5f,.16f,.40f,.30f,.21f,false);
                box(identity,treeX,2.15f,treeZ,1.25f,1.35f,1.25f,.27f,.48f,.30f,false);
                box(identity,treeX,2.88f,treeZ,.85f,.65f,.85f,.37f,.59f,.34f,false);
                if(block%2==0) {
                    float pole=edge+sign*1.8f;
                    box(identity,pole,1.9f,z-3.8f,.075f,3.7f,.075f,.25f,.32f,.35f,false);
                    box(identity,pole-sign*.35f,3.72f,z-3.8f,.75f,.08f,.18f,.32f,.39f,.42f,false);
                    box(identity,pole-sign*.62f,3.67f,z-3.8f,.25f,.035f,.15f,1,.94f,.72f,true);
                }
            }
        }
    }
    private void updateCarTransform(AlleyScene f) {
        Matrix.setIdentityM(car,0); Matrix.translateM(car,0,f.x,0,f.z);
        Matrix.rotateM(car,0,-f.heading,0,1,0);
    }
    private void drawCarBody(AlleyScene f) {
        // Body footprint is exactly width x length from the model, with front toward local -Z.
        box(car,0,.018f,.05f,f.width+.22f,.018f,f.length+.25f,.15f,.19f,.20f,true);
        box(car,0,.29f,0,f.width,.22f,f.length,.12f,.19f,.24f,false);
        box(car,0,.55f,0,f.width,.48f,f.length,.89f,.93f,.94f,false);
        box(car,0,.94f,.12f,f.width*.81f,.44f,f.length*.49f,.13f,.26f,.33f,false);
        box(car,0,1.18f,.18f,f.width*.76f,.08f,f.length*.31f,.88f,.93f,.94f,false);
        for(int sign=-1;sign<=1;sign+=2) {
            box(car,sign*f.width*.408f,.94f,.15f,.045f,.45f,.09f,.85f,.90f,.92f,false);
            box(car,sign*f.width*.49f,.78f,-.65f,.12f,.12f,.25f,.76f,.84f,.87f,false);
            box(car,sign*f.width*.506f,.57f,.35f,.025f,.045f,.22f,.31f,.42f,.48f,false);
        }
        box(car,0,.35f,f.length/2+.015f,f.width*.8f,.12f,.035f,.19f,.25f,.28f,false);
        box(car,0,.46f,f.length/2+.04f,.36f,.13f,.018f,.94f,.96f,.91f,true);
    }
    private void drawLamps(AlleyScene f) {
        // White front lamps and red rear lamps distinguish orientation.
        for(int sign:new int[]{-1,1}) {
            box(car,sign*f.width*.33f,.50f,-f.length/2-.012f,.32f,.13f,.035f,1,.96f,.64f,true);
            box(car,sign*f.width*.33f,.50f,f.length/2+.012f,.32f,.13f,.035f,.95f,.12f,.14f,true);
        }
    }
    private void drawWheels(AlleyScene f) {
        for(int sign:new int[]{-1,1}) {
            for(int axle:new int[]{-1,1}) {
                box(car,sign*(f.width/2-.07f),.23f,axle*f.length*.32f,.17f,.40f,.64f,.045f,.055f,.07f,false);
                box(car,sign*(f.width/2+.02f),.23f,axle*f.length*.32f,.025f,.24f,.36f,.58f,.65f,.68f,false);
            }
        }
    }
    private void drawDirectionMarker(AlleyScene f) {
        // Hood direction arrow, built from solid 3D bars.
        box(car,0,.755f,-f.length*.32f,.06f,.025f,.52f,.98f,.98f,1,true);
        box(car,-.10f,.755f,-f.length*.39f,.26f,.025f,.055f,.98f,.98f,1,true);
    }
    /** Returns whether another animation frame is needed. */
    private boolean drawGuidelines(AlleyScene f) {
        boolean animating=false;
        for(int i=0;i<2;i++) {
            long elapsed=SystemClock.uptimeMillis()-f.entered[i];
            boolean blink=f.levels[i]==AlleySimulation.Level.NEAR && f.entered[i]>=0 && elapsed<1500;
            int phase=blink?(int)(elapsed/250):6;
            if(f.entered[i]>=0 && (loggedEvent[i]!=f.entered[i] || loggedPhase[i]!=phase)) {
                Log.d("Carity","GL_LINE side="+(i==0?"LEFT":"RIGHT")+" phase="+phase+" elapsedMs="+elapsed);
                loggedEvent[i]=f.entered[i]; loggedPhase[i]=phase;
            }
            int rgb=AlleyView.color(f.levels[i]);
            float dim=blink && phase%2==1?.12f:1;
            float side=(i==0?-1:1)*(f.width/2+.065f);
            // Solid emissive strips rotate/translate with the car, outside its footprint.
            box(car,side,.13f,0,.10f,.08f,f.length+.14f,
                    Color.red(rgb)/255f*dim,Color.green(rgb)/255f*dim,Color.blue(rgb)/255f*dim,true);
            animating|=blink;
        }
        return animating;
    }
    private int shader(int type,String source) {
        int shader=GLES20.glCreateShader(type); GLES20.glShaderSource(shader,source); GLES20.glCompileShader(shader);
        int[] ok=new int[1]; GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0) throw new IllegalStateException(GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
    private void box(float[] parent,float x,float y,float z,float w,float h,float d,
                     float r,float g,float b,boolean unlit) {
        System.arraycopy(parent,0,model,0,16);
        Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,w,h,d);
        Matrix.multiplyMM(mvp,0,vp,0,model,0);
        GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0);
        GLES20.glUniformMatrix4fv(uModel,1,false,model,0);
        GLES20.glUniform3f(uColor,r,g,b); GLES20.glUniform1f(uUnlit,unlit?1:0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
    }
    private FloatBuffer cube() {
        float[][] faces={
            {-.5f,.5f,-.5f, -.5f,.5f,.5f, .5f,.5f,.5f, .5f,.5f,-.5f, 0,1,0},
            {-.5f,-.5f,.5f, -.5f,-.5f,-.5f, .5f,-.5f,-.5f, .5f,-.5f,.5f, 0,-1,0},
            {-.5f,-.5f,-.5f, -.5f,.5f,-.5f, .5f,.5f,-.5f, .5f,-.5f,-.5f, 0,0,-1},
            {.5f,-.5f,.5f, .5f,.5f,.5f, -.5f,.5f,.5f, -.5f,-.5f,.5f, 0,0,1},
            {-.5f,-.5f,.5f, -.5f,.5f,.5f, -.5f,.5f,-.5f, -.5f,-.5f,-.5f, -1,0,0},
            {.5f,-.5f,-.5f, .5f,.5f,-.5f, .5f,.5f,.5f, .5f,-.5f,.5f, 1,0,0}};
        FloatBuffer buffer=ByteBuffer.allocateDirect(36*6*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for(float[] face:faces) for(int index:new int[]{0,1,2,0,2,3}) {
            buffer.put(face,index*3,3); buffer.put(face,12,3);
        }
        buffer.position(0); return buffer;
    }
}
