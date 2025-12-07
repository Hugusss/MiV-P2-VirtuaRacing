package com.example.virtuaracing;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

/**
 * Permite situar una luz, y determinar el tipo de luz y su color. No se comenta con mucho detalle ya que lo
 * poco que se tenía que implementar se hizo durante un laboratorio, y es a demás bastante trivial, con
 * funciones de opengl.
 */
public class Light {
	ByteBuffer vtbb;
	FloatBuffer posicio;
	FloatBuffer ambient;
	FloatBuffer difuse;
	FloatBuffer specular;
	GL10 gl;
	int lightid;

	FloatBuffer fb_pos;

	public Light(GL10 gl, int lightid) {
		this.gl = gl;
		this.lightid = lightid;
		gl.glEnable(lightid);
		//gl.glEnable(GL10.GL_COLOR_MATERIAL);
    }

	//To enable and disable the light
	public void enable() {gl.glEnable(lightid);}
	public void disable() {gl.glDisable(lightid);}

	//To position the light
	public void setPosition(float[] pos) {
		fb_pos = FloatBuffer.wrap(pos);
		gl.glLightfv(lightid, GL10.GL_POSITION, fb_pos);
	}

	public void setPosition() {		// Després d'una transformació es torna a cridar aquest metode
		if(fb_pos!=null){
			gl.glLightfv(lightid, GL10.GL_POSITION, fb_pos);
		}
	}


	//To set the light colors
	public void setAmbientColor(float[] color) {
		FloatBuffer fb = FloatBuffer.wrap(color);
		gl.glLightfv(lightid, GL10.GL_AMBIENT, fb);
	}

	public void setDiffuseColor(float[] color) {
		FloatBuffer fb = FloatBuffer.wrap(color);
		gl.glLightfv(lightid, GL10.GL_DIFFUSE, fb);
	}

	public void setSpecularColor(float[] color) {
		FloatBuffer fb = FloatBuffer.wrap(color);
		gl.glLightfv(lightid, GL10.GL_SPECULAR, fb);
	}

}
