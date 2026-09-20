package com.example.irislens.common;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Base para las "funcionalidades" que NO usan cámara (por ejemplo, las
 * pantallas de gestión de medicamentos y principios activos), pero que
 * igual deben poder navegarse con swipe izquierda/derecha como el resto
 * de las secciones de la app (ver {@link BaseSwipeActivity} para el
 * equivalente con cámara).
 *
 * Se mantiene como una clase separada de BaseSwipeActivity porque esa
 * extiende CameraActivity de OpenCV, que inicializa cámara y permisos que
 * estas pantallas no necesitan.
 */
public abstract class BaseManagementActivity extends AppCompatActivity {

    private static final int SWIPE_THRESHOLD = 100;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    protected GestureDetector gestureDetector;
    protected int currentFunctionalityIndex = 0;

    // Gestor de voz compartido, igual que en BaseSwipeActivity
    protected AppVoiceManager voiceManager;

    // Para saber si hay que anunciar via TalkBack o via TTS propio
    protected AccessibilityHelper accessibilityHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        voiceManager = AppVoiceManager.getInstance(this);
        accessibilityHelper = new AccessibilityHelper(this);

        gestureDetector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
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
        if (gestureDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    protected void onSwipeLeft() {
        goToNext();
    }

    protected void onSwipeRight() {
        goToPrevious();
    }

    private void goToNext() {
        int nextIndex = Functionalities.getNextIndex(currentFunctionalityIndex);
        Functionalities.launch(this, nextIndex);
        finish();
    }

    private void goToPrevious() {
        int prevIndex = Functionalities.getPreviousIndex(currentFunctionalityIndex);
        Functionalities.launch(this, prevIndex);
        finish();
    }

    /** Las actividades hijas pueden sobrescribir esto. */
    protected void onDoubleTapDetected() {
    }

    /**
     * Anuncia un mensaje tanto si TalkBack está activo como si no, igual que
     * {@code DisplayRecognitionPresenter.announceMessage}: con TalkBack usa
     * el sistema de accesibilidad, sin TalkBack usa el TTS propio de la app.
     *
     * @param anchorView Vista de referencia para el anuncio de accesibilidad
     *                    (no hace falta que muestre el texto anunciado).
     * @param message     Texto a anunciar.
     */
    protected void announce(View anchorView, String message) {
        if (accessibilityHelper.isTalkBackEnabled()) {
            accessibilityHelper.announceForAccessibility(anchorView, message);
        } else if (voiceManager != null) {
            voiceManager.speak(message);
        }
    }
}