package com.example.irislens.common;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import org.opencv.android.CameraActivity;

/**
 * Actividad base con:
 * - Gestos swipe
 * - Doble toque
 * - Gestor de voz
 * - Gestión de permisos de cámara
 */
public abstract class BaseSwipeActivity extends CameraActivity {

    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    protected GestureDetector gestureDetector;
    protected int currentFunctionalityIndex = 0;

    // Gestor de voz compartido
    protected AppVoiceManager voiceManager;

    // Gestor de permisos
    protected PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Obtener gestor de voz global
        voiceManager = AppVoiceManager.getInstance(this);

        // Inicializar gestor de permisos
        permissionManager = new PermissionManager();

        // Pedir permisos UNA sola vez al crear
        permissionManager.getPermissions(this);

        // Configurar detector de gestos
        gestureDetector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onFling(
                            MotionEvent e1,
                            MotionEvent e2,
                            float velocityX,
                            float velocityY
                    ) {

                        float diffX = e2.getX() - e1.getX();
                        float diffY = e2.getY() - e1.getY();

                        if (Math.abs(diffX) > Math.abs(diffY)) {

                            if (Math.abs(diffX) > SWIPE_THRESHOLD
                                    && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                                if (diffX > 0) {
                                    onSwipeRight();
                                } else {
                                    onSwipeLeft();
                                }

                                return true;
                            }
                        }

                        return false;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        onDoubleTapDetected();
                        return true;
                    }
                }
        );
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {

        boolean handled = gestureDetector.onTouchEvent(ev);

        if (handled) {
            return true;
        }

        return super.dispatchTouchEvent(ev);
    }

    /**
     * Resultado de permisos Android
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (permissionManager != null) {

            permissionManager.handlePermissionsResult(
                    requestCode,
                    permissions,
                    grantResults,
                    this
            );
        }
    }

    /**
     * Swipe izquierda
     */
    protected void onSwipeLeft() {
        goToNext();
    }

    /**
     * Swipe derecha
     */
    protected void onSwipeRight() {
        goToPrevious();
    }

    /**
     * Ir a siguiente funcionalidad
     */
    private void goToNext() {

        int nextIndex =
                Functionalities.getNextIndex(currentFunctionalityIndex);

        Functionalities.launch(this, nextIndex);

        finish();
    }

    /**
     * Ir a funcionalidad anterior
     */
    private void goToPrevious() {

        int prevIndex =
                Functionalities.getPreviousIndex(currentFunctionalityIndex);

        Functionalities.launch(this, prevIndex);

        finish();
    }

    /**
     * Doble toque
     * Las actividades hijas pueden sobrescribir esto
     */
    protected void onDoubleTapDetected() {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}