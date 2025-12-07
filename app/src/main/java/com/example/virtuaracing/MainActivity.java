package com.example.virtuaracing;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.MotionEvent;

public class MainActivity extends Activity {

    private GLSurfaceView glView;   // Use GLSurfaceView
    private MyGLRenderer myGLRenderer;

    // Call back when the activity is started, to initialize the view
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        glView = new GLSurfaceView(this);           // Allocate a GLSurfaceView
        glView.setRenderer(myGLRenderer=new MyGLRenderer(this)); // Use a custom renderer
        this.setContentView(glView);                // This activity sets to GLSurfaceView
    }

    // Call back when the activity is going into the background
    @Override
    protected void onPause() {
        super.onPause();
        glView.onPause();
    }

    // Call back after onPause()
    @Override
    protected void onResume() {
        super.onResume();
        glView.onResume();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {

        float y = e.getY();

        if (y >  myGLRenderer.getHeight() / 2) {
            myGLRenderer.setZ(myGLRenderer.getZ()+0.1f );
        }else{

            myGLRenderer.setZ(myGLRenderer.getZ()-0.1f);
        }
        return true;
    }
}