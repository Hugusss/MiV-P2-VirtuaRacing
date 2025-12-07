package com.example.virtuaracing;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.opengl.GLES10;
import android.opengl.GLES20;

public class Object3D {

    private FloatBuffer vertexBuffer;
    private FloatBuffer normalBuffer;
    private FloatBuffer textureBuffer; // ¡El buffer que faltaba!
    private int numVertices = 0;

    public Object3D(Context context, int resourceId) {
        loadModel(context, resourceId);
    }

    private void loadModel(Context context, int resourceId) {
        ArrayList<Float> tempVertices = new ArrayList<>();
        ArrayList<Float> tempNormals = new ArrayList<>();
        ArrayList<Float> tempTextures = new ArrayList<>();

        // Listas finales para construir los buffers (sin índices compartidos para evitar errores de texturas)
        ArrayList<Float> buildVertices = new ArrayList<>();
        ArrayList<Float> buildNormals = new ArrayList<>();
        ArrayList<Float> buildTextures = new ArrayList<>();

        InputStream inputStream = context.getResources().openRawResource(resourceId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        try {
            while ((line = reader.readLine()) != null) {
                // Limpieza de espacios múltiples
                String[] parts = line.trim().split("\\s+");

                if (parts.length == 0) continue;

                if (parts[0].equals("v")) {
                    // Vértices (x, y, z)
                    tempVertices.add(Float.parseFloat(parts[1]));
                    tempVertices.add(Float.parseFloat(parts[2]));
                    tempVertices.add(Float.parseFloat(parts[3]));
                }
                else if (parts[0].equals("vt")) {
                    // Texturas (u, v)
                    tempTextures.add(Float.parseFloat(parts[1]));
                    // Blender usa origen abajo-izq, OpenGL arriba-izq a veces.
                    // Normalmente invertimos V, pero probemos directo primero.
                    tempTextures.add(1.0f - Float.parseFloat(parts[2]));
                }
                else if (parts[0].equals("vn")) {
                    // Normales (nx, ny, nz)
                    tempNormals.add(Float.parseFloat(parts[1]));
                    tempNormals.add(Float.parseFloat(parts[2]));
                    tempNormals.add(Float.parseFloat(parts[3]));
                }
                else if (parts[0].equals("f")) {
                    // Caras (v/vt/vn v/vt/vn v/vt/vn)
                    // Importante: Triangulamos manualmente si vienen quads, aunque pedimos exportar triángulos.
                    int numPoints = parts.length - 1;

                    // Lógica para procesar triángulos (fan triangulation si es un polígono)
                    for (int i = 0; i < numPoints - 2; i++) {
                        processVertex(parts[1], tempVertices, tempTextures, tempNormals, buildVertices, buildTextures, buildNormals);
                        processVertex(parts[2 + i], tempVertices, tempTextures, tempNormals, buildVertices, buildTextures, buildNormals);
                        processVertex(parts[3 + i], tempVertices, tempTextures, tempNormals, buildVertices, buildTextures, buildNormals);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Convertir ArrayLists a Buffers
        numVertices = buildVertices.size() / 3;

        // Vertex Buffer
        ByteBuffer vbb = ByteBuffer.allocateDirect(buildVertices.size() * 4);
        vbb.order(ByteOrder.nativeOrder());
        vertexBuffer = vbb.asFloatBuffer();
        for (float f : buildVertices) vertexBuffer.put(f);
        vertexBuffer.position(0);

        // Texture Buffer
        ByteBuffer tbb = ByteBuffer.allocateDirect(buildTextures.size() * 4);
        tbb.order(ByteOrder.nativeOrder());
        textureBuffer = tbb.asFloatBuffer();
        for (float f : buildTextures) textureBuffer.put(f);
        textureBuffer.position(0);

        // Normal Buffer
        if (!buildNormals.isEmpty()) {
            ByteBuffer nbb = ByteBuffer.allocateDirect(buildNormals.size() * 4);
            nbb.order(ByteOrder.nativeOrder());
            normalBuffer = nbb.asFloatBuffer();
            for (float f : buildNormals) normalBuffer.put(f);
            normalBuffer.position(0);
        }
    }

    // Procesa un bloque "v/vt/vn"
    private void processVertex(String part,
                               ArrayList<Float> tempV, ArrayList<Float> tempT, ArrayList<Float> tempN,
                               ArrayList<Float> outV, ArrayList<Float> outT, ArrayList<Float> outN) {

        String[] indices = part.split("/");

        // 1. Vértice (siempre existe)
        int vIdx = (Integer.parseInt(indices[0]) - 1) * 3;
        outV.add(tempV.get(vIdx));
        outV.add(tempV.get(vIdx + 1));
        outV.add(tempV.get(vIdx + 2));

        // 2. Textura (puede estar vacía si es v//vn)
        if (indices.length > 1 && !indices[1].isEmpty()) {
            int tIdx = (Integer.parseInt(indices[1]) - 1) * 2;
            outT.add(tempT.get(tIdx));
            outT.add(tempT.get(tIdx + 1));
        } else {
            // Si no hay textura, poner 0,0 por defecto
            outT.add(0f); outT.add(0f);
        }

        // 3. Normal (puede faltar)
        if (indices.length > 2 && !indices[2].isEmpty()) {
            int nIdx = (Integer.parseInt(indices[2]) - 1) * 3;
            outN.add(tempN.get(nIdx));
            outN.add(tempN.get(nIdx + 1));
            outN.add(tempN.get(nIdx + 2));
        }
    }

    public void draw(GL10 gl) {
        // Habilitar arrays
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, vertexBuffer);

        if (normalBuffer != null) {
            gl.glEnableClientState(GL10.GL_NORMAL_ARRAY);
            gl.glNormalPointer(GL10.GL_FLOAT, 0, normalBuffer);
        }

        // ¡ESTO FALTABA! Habilitar texturas
        if (textureBuffer != null) {
            gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
            gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, textureBuffer);
        }

        // Dibujar
        gl.glDrawArrays(GL10.GL_TRIANGLES, 0, numVertices);

        // Limpiar estados
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_NORMAL_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
    }
}
