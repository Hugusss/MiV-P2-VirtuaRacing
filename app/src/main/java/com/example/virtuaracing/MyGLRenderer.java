package com.example.virtuaracing;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.GLU;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private Context context;
    private int width, height;

    // --- OBJETOS 3D ---
    private Object3D road, sky, carChassis, carWheel, tree, stand;

    // --- TEXTURAS ---
    private int textureIdAtlas = -1;
    private int textureIdSky = -1;

    // --- LÓGICA DE RUTA ---
    private List<Vector4> routePoints = new ArrayList<>();

    // --- LÓGICA DEL JUGADOR ---
    private float playerProgress = 0.0f;
    private Vector4 playerPos = new Vector4(0,0,0,1);
    private float playerRotY = 0;

    // --- ESCENARIO Y CULLING ---
    //clase auxiliar para guardar posición y tipo de objeto (0=arbol, 1=grada)
    private static class SceneryItem {
        Vector4 position;
        int type;
        public SceneryItem(Vector4 p, int t) { position = p; type = t; }
    }
    private List<SceneryItem> sceneryItems = new ArrayList<>();
    private float RENDER_DISTANCE = 45.0f; //distancia de pop-up

    // --- CÁMARA ---
    private Camera camera;
    private boolean isOverheadView = false; // Para cambiar de cámara luego

    // --- LUCES ---
    private Light sunLight;

    // Variables globales de animación
    private float globalWheelRotation = 0;

    public MyGLRenderer(Context context) {
        this.context = context;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        gl.glClearColor(0.5f, 0.7f, 1.0f, 1.0f);
        gl.glClearDepthf(1.0f);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glDepthFunc(GL10.GL_LEQUAL);
        gl.glShadeModel(GL10.GL_SMOOTH);

        // Cargar Texturas
        textureIdAtlas = loadTexture(gl, context, R.raw.texture_atlas_sq);
        textureIdSky = loadTexture(gl, context, R.raw.sky_rural);

        // Cargar Modelos
        try {
            road = new Object3D(context, R.raw.road);
            sky = new Object3D(context, R.raw.sky);
            carChassis = new Object3D(context, R.raw.chassis);
            carWheel = new Object3D(context, R.raw.wheel);
            tree = new Object3D(context, R.raw.tree);
            stand = new Object3D(context, R.raw.stand);

            loadRoutePoints(context, R.raw.route);

            // GENERAR ESCENARIO AUTOMÁTICO ALREDEDOR DE LA RUTA
            generateScenery();

        } catch (Exception e) {
            Log.e("MyGLRenderer", "Error: " + e.getMessage());
        }

        camera = new Camera(gl,new Vector4(0, 10, -20, 1),
                            new Vector4(0, 0, 0, 1),
                            new Vector4(0, 1, 0, 0));

        // Luz
        gl.glEnable(GL10.GL_LIGHTING);
        gl.glEnable(GL10.GL_LIGHT0);
        sunLight = new Light(gl, GL10.GL_LIGHT0);
        sunLight.setPosition(new float[]{50.0f, 200.0f, 50.0f, 0.0f});
        sunLight.setAmbientColor(new float[]{0.4f, 0.4f, 0.4f});
        sunLight.setDiffuseColor(new float[]{1.0f, 1.0f, 1.0f});
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (height == 0) height = 1;
        this.width = width;
        this.height = height;
        float aspect = (float) width / height;
        gl.glViewport(0, 0, width, height);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        GLU.gluPerspective(gl, 60, aspect, 1f, 200f); //far clipping 200m
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // 1. UPDATE FÍSICAS (solo avanza contador global)
        float speed = 1.25f;
        playerProgress += speed;
        if (playerProgress >= routePoints.size()) playerProgress = 0;

        globalWheelRotation += speed * 30;

        // 2. CALCULAR POSICIÓN JUGADOR (para la cámara)
        //se precalcula antes de dibujar para saber dónde poner la cam
        CarState playerState = calculateCarState(playerProgress);
        playerPos = playerState.position;
        playerRotY = playerState.rotationY;

        // 3. CÁMARA (Lógica de seguimiento)
        updateCamera();

        // 4. DIBUJAR
        // Reset color base a blanco para texturas
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // A. CIELO (Siempre sigue al jugador para no llegar al borde)
        gl.glDisable(GL10.GL_LIGHTING);
        gl.glPushMatrix();
        gl.glTranslatef(playerPos.get(0), 0, playerPos.get(2));
        bindTexture(gl, textureIdSky);
        if(sky != null) sky.draw(gl);
        gl.glPopMatrix();
        gl.glEnable(GL10.GL_LIGHTING);

        // B. CARRETERA
        bindTexture(gl, textureIdAtlas);
        if(road != null) road.draw(gl);

        // C. ESCENARIO CON POP-UP (CULLING)
        drawScenery(gl);

        // D. COCHES (JUGADOR Y RIVALES)
        // Jugador
        drawCar(gl, playerProgress, 0, 0.8f); // 0 offset lateral

        // Rival 1 (Adelantado, a la derecha)
        float rival1Prog = playerProgress + 25;
        if(rival1Prog >= routePoints.size()) rival1Prog -= routePoints.size();
        drawCar(gl, rival1Prog, -3.5f, 0.4f); // -3.5 offset lateral

        // Rival 2 (Atrasado, a la izquierda)
        float rival2Prog = playerProgress - 15;
        if(rival2Prog < 0) rival2Prog += routePoints.size();
        drawCar(gl, rival2Prog, 3.5f, 0.5f);
    }

    // --- MÉTODOS DE DIBUJO ---
    // Dibuja un coche completo en un punto concreto de la ruta
    private void drawCar(GL10 gl, float progress, float lateralOffset, float sizeScale) {
        // 1. Calcular dónde está este coche
        CarState state = calculateCarState(progress);

        gl.glPushMatrix();
        // Traslación base
        gl.glTranslatef(state.position.get(0), state.position.get(1), state.position.get(2));
        // Rotación base (rumbo)
        gl.glRotatef(state.rotationY, 0, 1, 0);
        gl.glTranslatef(lateralOffset, 0, 0);

        // Escala (opcional, para variar tamaños)
        //gl.glScalef(sizeScale, sizeScale, sizeScale); // Descomentar si quieres coches de distinto tamaño

        // Dibujar Chasis
        if(carChassis != null) carChassis.draw(gl);

        // Dibujar Ruedas
        drawWheels(gl, state.steeringAngle);

        gl.glPopMatrix();
    }

    private void drawWheels(GL10 gl, float steeringAngle) {
        if(carWheel == null) return;
        float wX = 2.0f; float wY = 1.0f; float wZ = 2.5f;
        float toeAngle = 7.0f; //efecto ÁNGULO Toe-in "/ \"

        // Delantera Izq
        gl.glPushMatrix();
        gl.glTranslatef(wX*0.8f, wY, wZ*1.35f);
        gl.glRotatef(-toeAngle + steeringAngle, 0, 1, 0);//rotar hacia adentro (eje Y)
        gl.glRotatef(globalWheelRotation, 1, 0, 0);//girar por velocidad (eje X)
        carWheel.draw(gl);
        gl.glPopMatrix();

        // Delantera Der
        gl.glPushMatrix();
        gl.glTranslatef(-wX*0.8f, wY, wZ*1.35f);
        gl.glRotatef(180 + toeAngle + steeringAngle, 0, 1, 0);//espejo + inclinación
        gl.glRotatef(globalWheelRotation, 1, 0, 0);//girar velocidad
        carWheel.draw(gl);
        gl.glPopMatrix();

        // Trasera Izq
        gl.glPushMatrix();
        gl.glTranslatef(-wX, wY, -wZ);
        gl.glRotatef(globalWheelRotation, 1, 0, 0);
        carWheel.draw(gl);
        gl.glPopMatrix();

        // Trasera Der
        gl.glPushMatrix();
        gl.glTranslatef(wX, wY, -wZ);
        gl.glRotatef(180, 0, 1, 0);
        gl.glRotatef(globalWheelRotation, 1, 0, 0);
        carWheel.draw(gl);
        gl.glPopMatrix();
    }

    // Recorre la lista de árboles y solo dibuja los cercanos
    private void drawScenery(GL10 gl) {
        bindTexture(gl, textureIdAtlas);

        for (SceneryItem item : sceneryItems) {
            // CÁLCULO DE DISTANCIA
            float dx = item.position.get(0) - playerPos.get(0);
            float dz = item.position.get(2) - playerPos.get(2);
            float distSq = dx*dx + dz*dz; //distancia al cuadrado es más rápido que raíz cuadrada

            // Si está dentro del rango
            if (distSq < (RENDER_DISTANCE * RENDER_DISTANCE)) {
                gl.glPushMatrix();
                gl.glTranslatef(item.position.get(0), item.position.get(1), item.position.get(2));

                // Rotar para que miren al centro (aprox) o aleatorio
                if (item.type == 0 && tree != null) tree.draw(gl);
                else if (item.type == 1 && stand != null) {
                    gl.glRotatef(180, 0, 1, 0);
                    stand.draw(gl);
                }
                gl.glPopMatrix();
            }
        }
    }

    // --- LÓGICA MATEMÁTICA ---
    // Struct para devolver posición y rotación a la vez
    private class CarState {
        Vector4 position;
        float rotationY;
        float steeringAngle;
    }

    private CarState calculateCarState(float progress) {
        CarState state = new CarState();
        if (routePoints.isEmpty()) return state;

        int idx = (int) progress;
        int nextIdx = (idx + 1) % routePoints.size();
        float t = progress - idx;

        Vector4 p1 = routePoints.get(idx);
        Vector4 p2 = routePoints.get(nextIdx);

        // Interpolación Posición
        float x = p1.get(0) * (1-t) + p2.get(0) * t;
        float y = p1.get(1) * (1-t) + p2.get(1) * t;
        float z = p1.get(2) * (1-t) + p2.get(2) * t;
        state.position = new Vector4(x, y, z, 1);

        // Rotación Chasis
        double dx = p2.get(0) - p1.get(0);
        double dz = p2.get(2) - p1.get(2);
        float currentHeading = (float) Math.toDegrees(Math.atan2(dx, dz));
        state.rotationY = currentHeading;

        // Rotación Volante (Look ahead)
        int nextNextIdx = (nextIdx + 1) % routePoints.size();
        Vector4 p3 = routePoints.get(nextNextIdx);

        double dxNext = p3.get(0) - p2.get(0);
        double dzNext = p3.get(2) - p2.get(2);
        float futureHeading = (float) Math.toDegrees(Math.atan2(dxNext, dzNext));

        float angleDiff = futureHeading - currentHeading;//calcular la diferencia (cuánto cambia la curva)
        //corregir el salto de 360 a 0 grados
        while (angleDiff < -180) angleDiff += 360;
        while (angleDiff > 180) angleDiff -= 360;

        float steer = angleDiff * 7.5f; //multiplicador de la ganancia de giro para hacerlo +-pronunciado
        //Clamp, lím máx de giro
        if(steer > 50) steer = 50;
        if(steer < -50) steer = -50;
        state.steeringAngle = steer;

        return state;
    }

    private void updateCamera() {
        if (!isOverheadView) {
            // CÁMARA TRASERA (Juego normal)
            float camDist = 16.0f;
            float camHeight = 9.5f;
            //actualizar vectores de cam
            double rads = Math.toRadians(playerRotY);
            float camX = playerPos.get(0) - (float)Math.sin(rads) * camDist;
            float camZ = playerPos.get(2) - (float)Math.cos(rads) * camDist;

            camera.eye = new Vector4(camX, playerPos.get(1) + camHeight, camZ, 1);
            camera.center = new Vector4(playerPos.get(0), playerPos.get(1) + 6.5f, playerPos.get(2), 1);
        } else {
            // CÁMARA ZENITAL (Mapa aéreo)
            // Subimos mucho en Y y miramos abajo
            camera.eye = new Vector4(playerPos.get(0), 85, playerPos.get(2), 1);
            camera.center = playerPos;
            camera.up = new Vector4(0, 0, -1, 0);
        }
        camera.look();
        //restaurar UP vector si cambiar modo
        if(!isOverheadView) camera.up = new Vector4(0, 1, 0, 0);
    }

    public void toggleCameraMode() { //metodo para alternar cámara
        isOverheadView = !isOverheadView;
    }

    // --- GENERACIÓN PROCEDURAL DE ESCENARIO ---
    private void generateScenery() {
        if(routePoints.isEmpty()) return;
        Random rand = new Random();

        // Recorremos la ruta y cada 'i' pasos ponemos algo
        for (int i = 0; i < routePoints.size(); i+= 7) { // Cada 8 puntos
            Vector4 p = routePoints.get(i);
            Vector4 nextP = routePoints.get((i+1)%routePoints.size());//evitamos error de índice al buscar el siguiente punto

            // Vector dirección
            float dx = nextP.get(0) - p.get(0);
            float dz = nextP.get(2) - p.get(2);
            // Normalizar
            float len = (float)Math.sqrt(dx*dx + dz*dz);
            if (len == 0) continue;
            dx /= len; dz /= len;

            // Vector perpendicular (Izquierda/Derecha)
            float perpX = -dz;
            float perpZ = dx;

            // LADO DERECHO (Árboles)
            if (rand.nextFloat() > 0.3f) { // 70% probabilidad
                float dist = 7 + rand.nextFloat() * 9.0f; // Entre 15 y 35 metros del centro
                float objX = p.get(0) + perpX * dist;
                float objZ = p.get(2) + perpZ * dist;
                sceneryItems.add(new SceneryItem(new Vector4(objX, p.get(1), objZ, 1), 0)); // Tipo 0 = Árbol
            }

            // LADO IZQUIERDO (Gradas a veces, árboles otras)
            if (rand.nextFloat() > 0.6f) {
                float dist = -8 - rand.nextFloat() * 10.0f; // Lado contrario (negativo)
                float objX = p.get(0) + perpX * dist;
                float objZ = p.get(2) + perpZ * dist;

                // Tipo 1 (Grada) si toca, o 0 (Árbol)
                int type = (rand.nextFloat() > 0.5f) ? 1 : 0;
                sceneryItems.add(new SceneryItem(new Vector4(objX, p.get(1), objZ, 1), type));
            }
        }
    }

    // --- CARGADORES DE TEXTURAS ---
    private int loadTexture(GL10 gl, Context context, int resourceId) {
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        int textureId = textures[0];
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);
        InputStream is = context.getResources().openRawResource(resourceId);
        Bitmap bitmap;
        try { bitmap = BitmapFactory.decodeStream(is); }
        finally { try { is.close(); } catch (IOException e) {} }
        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();
        return textureId;
    }

    private void bindTexture(GL10 gl, int textureId) {
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);
    }
    //función auxiliar para cargar el path a seguir
    private void loadRoutePoints(Context context, int resourceId) {
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("v ")) {
                    String[] parts = line.split("\\s+");
                    float x = Float.parseFloat(parts[1]);
                    float z = Float.parseFloat(parts[2]);
                    float y = Float.parseFloat(parts[3]);
                    routePoints.add(new Vector4(x, z, y, 1));
                }
            }
        } catch (IOException e) {}
    }
}