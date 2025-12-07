package com.example.virtuaracing;

import static java.lang.Math.cos;
import static java.lang.Math.sin;

import android.opengl.GLU;

import javax.microedition.khronos.opengles.GL10;

public class Camera {
    GL10 gl;
    Vector4 eye, up, center;

    public Camera(GL10 gl, Vector4 eye, Vector4 center, Vector4 up) {
        this.gl = gl;
        this.eye=eye;
        this.center=center;
        this.up = up;
    }

    public void addMovement(Vector4 movement){
        this.eye=this.eye.add(movement);
        this.center=this.center.add(movement);
    }

    public void look()
    {
        GLU.gluLookAt(gl, eye.get(0), eye.get(1), eye.get(2),
                center.get(0), center.get(1), center.get(2),
                up.get(0), up.get(1), up.get(2));
    }
}
