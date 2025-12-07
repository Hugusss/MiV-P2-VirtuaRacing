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
import java.util.ArrayList;
import java.util.List;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private Context context;
    private int width, height;
    float Z = 1;

    // --- OBJETOS 3D ---
    private Object3D road, sky, carChassis, carWheel, tree, stand;

    // --- TEXTURAS ---
    private int textureIdAtlas = -1;
    private int textureIdSky = -1;

    // --- LOGICA DE RUTA Y COCHE ---
    // Lista de puntos por donde pasará el coche
    private List<Vector4> routePoints = new ArrayList<>();

    // coche status
    private float carProgress = 0.0f; // Índice actual en la ruta
    private Vector4 carPosition = new Vector4(0,0,0,1);
    private float carRotationY = 0;   // Ángulo hacia donde mira el coche
    private float wheelRotation = 0;  // Giro de las ruedas al avanzar
    private float steeringAngle = 0f; // Ángulo de giro de las ruedas delanteras

    // Cámara
    private Camera camera;

    // Luces
    private Light sunLight;

    public MyGLRenderer(Context context) {
        this.context = context;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Configuración básica OpenGL
        gl.glClearColor(0.5f, 0.7f, 1.0f, 1.0f); // Fondo azul cielo por defecto
        gl.glClearDepthf(1.0f);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glDepthFunc(GL10.GL_LEQUAL);
        gl.glEnable(GL10.GL_CULL_FACE); //no dibujar caras traseras
        gl.glShadeModel(GL10.GL_SMOOTH);

        //Cargar Texturas
        textureIdAtlas = loadTexture(gl, context, R.raw.texture_atlas_sq);
        textureIdSky = loadTexture(gl, context, R.raw.sky_rural);   //para cielo

        //Cargar Modelos OBJ
        try {
            road = new Object3D(context, R.raw.road);
            sky = new Object3D(context, R.raw.sky);
            carChassis = new Object3D(context, R.raw.chassis);
            carWheel = new Object3D(context, R.raw.wheel);
            tree = new Object3D(context, R.raw.tree);
            stand = new Object3D(context, R.raw.stand);

            // Cargar la ruta lógica
            loadRoutePoints(context, R.raw.route);

        } catch (Exception e) {
            Log.e("MyGLRenderer", "Error cargando objetos: " + e.getMessage());
        }

        //Configurar Cámara Inicial
        camera = new Camera(gl, new Vector4(0, 10, -20, 1), new Vector4(0, 0, 0, 1), new Vector4(0, 1, 0, 0));

        // Configurar Luz
        gl.glEnable(GL10.GL_LIGHTING);
        gl.glEnable(GL10.GL_LIGHT0);
        sunLight = new Light(gl, GL10.GL_LIGHT0);
        sunLight.setPosition(new float[]{100.0f, 200.0f, 100.0f, 0.0f}); // Luz direccional
        sunLight.setAmbientColor(new float[]{0.3f, 0.3f, 0.3f}); // Ambiente suave
        sunLight.setDiffuseColor(new float[]{1.0f, 1.0f, 1.0f}); // Luz blanca
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
        // FOV de 60 grados, rango de visión de 1 a 1000 metros
        GLU.gluPerspective(gl, 60, aspect, 1f, 1000f);
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // --- A. ACTUALIZAR LÓGICA (mover coche) ---
        updateCarPhysics();

        // --- B. CÁMARA ---
        // Ponemos la cámara detrás del coche y un poco arriba
        // Calculamos posición cámara relativa al coche
        float camDist = 16.0f;
        float camHeight = 9.5f;

        //operaciones para situar la cámara detrás según la rotación del coche
        double rads = Math.toRadians(carRotationY);
        float camX = carPosition.get(0) - (float)Math.sin(rads) * camDist;
        float camZ = carPosition.get(2) - (float)Math.cos(rads) * camDist;

        // Actualizar vectores de cámara
        camera.eye = new Vector4(camX, carPosition.get(1) + camHeight, camZ, 1);
        //camera.center = carPosition; // Mirar al coche
        camera.center = new Vector4(carPosition.get(0), carPosition.get(1) + 6.5f, carPosition.get(2), 1);
        camera.look();

        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f); //reset de colores antes de dibujar

        // --- C. DIBUJAR ESCENA ---

        // 1. DIBUJAR CIELO
        gl.glDisable(GL10.GL_LIGHTING);
        gl.glPushMatrix();
        gl.glTranslatef(carPosition.get(0), 0, carPosition.get(2));
        bindTexture(gl, textureIdSky);
        if(sky != null) sky.draw(gl);
        gl.glPopMatrix();
        gl.glEnable(GL10.GL_LIGHTING); // Reactivar luces

        // 2. DIBUJAR CARRETERA
        bindTexture(gl, textureIdAtlas);
        if(road != null) road.draw(gl);

        // 3. DIBUJAR ÁRBOLES Y GRADAS
        drawDecorations(gl);

        // 4. DIBUJAR COCHE
        gl.glPushMatrix();
        gl.glTranslatef(carPosition.get(0), carPosition.get(1), carPosition.get(2));
        gl.glRotatef(carRotationY, 0, 1, 0); // Rotar chasis según dirección

        // Chasis
        if(carChassis != null) carChassis.draw(gl);

        // Ruedas (animación de giro y posición relativa)
        drawWheels(gl);

        gl.glPopMatrix();
    }

    // --- FUNCIONES AUXILIARES ---
    private void updateCarPhysics() {
        if (routePoints.size() < 2) return;

        // --- 1. AVANCE ---
        float speed = 0.4f;
        carProgress += speed;
        if (carProgress >= routePoints.size() - 1) {
            carProgress = 0;
        }

        // --- 2. POSICIÓN E INTERPOLACIÓN ---
        int idx = (int) carProgress;
        int nextIdx = (idx + 1) % routePoints.size();
        float t = carProgress - idx;

        Vector4 p1 = routePoints.get(idx);
        Vector4 p2 = routePoints.get(nextIdx);

        // Posición del coche
        float x = p1.get(0) * (1-t) + p2.get(0) * t;
        float y = p1.get(1) * (1-t) + p2.get(1) * t;
        float z = p1.get(2) * (1-t) + p2.get(2) * t;
        carPosition = new Vector4(x, y, z, 1);

        // --- 3. ROTACIÓN DEL CHASIS ---
        double dx = p2.get(0) - p1.get(0);
        double dz = p2.get(2) - p1.get(2);
        float currentHeading = (float) Math.toDegrees(Math.atan2(dx, dz));
        carRotationY = currentHeading;

        // --- 4. CÁLCULO DE GIRO DE RUEDAS (Look Ahead) ---
        // Miramos hacia el SIGUIENTE segmento para anticipar la curva
        int nextNextIdx = (nextIdx + 1) % routePoints.size();
        Vector4 p3 = routePoints.get(nextNextIdx);

        double dxNext = p3.get(0) - p2.get(0);
        double dzNext = p3.get(2) - p2.get(2);
        float futureHeading = (float) Math.toDegrees(Math.atan2(dxNext, dzNext));

        // Calculamos la diferencia (cuánto cambia la curva)
        float angleDiff = futureHeading - currentHeading;

        // Corregir el salto de 360 a 0 grados
        while (angleDiff < -180) angleDiff += 360;
        while (angleDiff > 180) angleDiff -= 360;

        // SUAVIZADO Y AMPLIFICACIÓN
        // Multiplicar por 3.0f para que se note más el giro visualmente
        //limitar a 40 grados para que no se rompan las ruedas
        float targetSteer = angleDiff * 3.0f;

        // Clamp (Límite máximo de giro)
        if (targetSteer > 40) targetSteer = 40;
        if (targetSteer < -40) targetSteer = -40;

        // Interpolación simple para que el volante no tiemble
        steeringAngle = steeringAngle * 0.8f + targetSteer * 0.5f;

        // Girar ruedas sobre su eje X (velocidad)
        wheelRotation += speed * 30;
    }

    private void drawWheels(GL10 gl) {
        if(carWheel == null) return;

        // Ajustes de posición
        float wX = 2f;
        float wY = 1.0f;
        float wZ = 2.5f;

        //efecto ÁNGULO Toe-in "/ \"
        float toeAngle = 6.0f;

        // --- Rueda Delantera Izquierda ---
        gl.glPushMatrix();
        gl.glTranslatef(wX*0.8f, wY, wZ*1.35f);
        gl.glRotatef(-toeAngle + steeringAngle, 0, 1, 0); // Rotar hacia adentro (eje Y)
        gl.glRotatef(wheelRotation, 1, 0, 0); // Girar por velocidad (eje X)
        carWheel.draw(gl);
        gl.glPopMatrix();

        // --- Rueda Delantera Derecha ---
        gl.glPushMatrix();
        gl.glTranslatef(-wX*0.8f, wY, wZ*1.35f);
        gl.glRotatef(180 + toeAngle + steeringAngle, 0, 1, 0); // Espejo + inclinación
        gl.glRotatef(wheelRotation, 1, 0, 0);  // Girar velocidad
        carWheel.draw(gl);
        gl.glPopMatrix();


        // Trasera Izquierda
        gl.glPushMatrix();
        gl.glTranslatef(-wX, wY, -wZ);
        gl.glRotatef(wheelRotation, 1, 0, 0);
        carWheel.draw(gl);
        gl.glPopMatrix();

        // Trasera Derecha
        gl.glPushMatrix();
        gl.glTranslatef(wX, wY, -wZ);
        gl.glRotatef(180, 0, 1, 0);
        gl.glRotatef(wheelRotation, 1, 0, 0);
        carWheel.draw(gl);
        gl.glPopMatrix();
    }

    private void drawDecorations(GL10 gl) {
        bindTexture(gl, textureIdAtlas);
        if (tree != null) {
            gl.glPushMatrix();
            gl.glTranslatef(20, 0, 20); // Un árbol en (20,0,20)
            gl.glScalef(3, 3, 3);
            tree.draw(gl);
            gl.glPopMatrix();
        }
    }

    // --- CARGA DE TEXTURAS SIMPLE ---
    private int loadTexture(GL10 gl, Context context, int resourceId) {
        int[] textures = new int[1];
        gl.glGenTextures(1, textures, 0);
        int textureId = textures[0];

        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);

        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_NEAREST);
        // REPEAT para que el cielo no se estire raro si es pequeño
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);

        InputStream is = context.getResources().openRawResource(resourceId);
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeStream(is);
        } finally {
            try { is.close(); } catch (IOException e) {}
        }

        GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
        bitmap.recycle();

        return textureId;
    }

    // Función auxiliar para activar texturas antes de draw
    private void bindTexture(GL10 gl, int textureId) {
        gl.glEnable(GL10.GL_TEXTURE_2D);
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);
    }

    public float getHeight() {
        return this.height;
    }

    public float getWidth() {
        return this.width;
    }

    public float getZ() {
        return Z;
    }

    public void setZ(float z) {
        this.Z = z;
    }

    // --- PARSER ESPECIAL PARA LA RUTA ---
    private void loadRoutePoints(Context context, int resourceId) {
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("v ")) { // solo vértices
                    String[] parts = line.split("\\s+");
                    float x = Float.parseFloat(parts[1]);
                    float z = Float.parseFloat(parts[2]); //blender Z es Y, y Y es Z...
                    float y = Float.parseFloat(parts[3]);

                    // Ajuste de ejes Blender -> Android (Y-up vs Z-up swap)
                    // En exportación se marca -Z Forward, Y Up
                    routePoints.add(new Vector4(x, z, y, 1));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}