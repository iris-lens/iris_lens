package com.example.irislens.display.view;

import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.display.presenter.DisplayRecognitionPresenter;
import com.example.irislens.common.BaseSwipeActivity;
import com.example.irislens.common.Functionalities;
import com.example.irislens.common.PermissionManager;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class DisplayRecognitionActivity extends BaseSwipeActivity {
    private static final String TAG = "DisplayActivity";

    private PermissionManager permissionManager;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRgba;
    private TextView tvResult;
    private DisplayRecognitionPresenter presenter;
    private Handler handler;
    private Runnable delayedSpeakTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_recognition);

        currentFunctionalityIndex = Functionalities.DISPLAY;

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);
        handler = new Handler();

        String message = "Reconocimiento de displays. Apunte la cámara hacia el display que desea reconocer.";

        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        try {
            presenter = new DisplayRecognitionPresenter(this, tvResult);
        } catch (IOException e) {
            Log.e(TAG, "Error cargando modelo TFLite", e);
            tvResult.setText("Error cargando el modelo de displays: " + e.getMessage());

            if (e.getMessage() != null) {
                if (e.getMessage().contains("assets")) {
                    Log.e(TAG, "Error relacionado con assets - verificar que los archivos detectorDisplay_small.tflite o detectorDisplay_nano.tflite, y labelsDisplay.txt estén en assets/");
                } else if (e.getMessage().contains("model")) {
                    Log.e(TAG, "Error del modelo - verificar formato TensorFlow Lite");
                }
            }
        }

        // Usar speakAndShow con delay para que TTS este listo
//        delayedSpeakTask = new Runnable() {
//            @Override
//            public void run() {
//                if (!isFinishing() && !isDestroyed()) {
//                    voiceManager.speakAndShow(tvResult, message);
//                }
//            }
//        };
//        handler.postDelayed(delayedSpeakTask, 500);

        // Espera breve para asegurar que TTS este listo
        new Handler().postDelayed(() -> {
            presenter.announceMessage(message);
        }, 1000);


        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCVInit", "Error al inicializar OpenCV");
        } else {
            cameraBridgeViewBase.enableView();
        }

        cameraBridgeViewBase.setCvCameraViewListener(new CameraBridgeViewBase.CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                mRgba = new Mat(height, width, CvType.CV_8UC4);
            }

            @Override
            public void onCameraViewStopped() {
                mRgba.release();
            }

            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                Mat originalImage = inputFrame.rgba();

                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                if (rotation == Surface.ROTATION_0) {
                    originalImage = com.example.irislens.common.ImageProcessor.rotateImage(originalImage);
                }

                if (presenter != null && !isFinishing()) {
                    presenter.processCameraFrame(originalImage);
                }

                return originalImage;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Detener cuando salimos
        if (handler != null && delayedSpeakTask != null) {
            handler.removeCallbacks(delayedSpeakTask);
        }

        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.disableView();
        }

        if (voiceManager != null) {
            voiceManager.stop();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.enableView();
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "🔴 onDestroy() llamado");

        // Limpiar en orden
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.disableView();
        }

        if (presenter != null) {
            presenter.onDestroy();
        }

        // No hacer shutdown del voiceManager aca. Es global y lo usan otras actividades
        if (voiceManager != null) {
            voiceManager.stop(); // Solo detener, no shutdown
        }

        super.onDestroy();
        Log.d(TAG, "✅ onDestroy() completado");
    }

    /**
     * Heredada, cuando detecta doble tap, pausa el audio.
     *
     * El orden importa.
     * - presenter.onDoubleTap() primero: setea doubleTapActive=true y cancela
     *   sus postDelayed antes de que voiceManager limpie el estado compartido.
     * - voiceManager.stopAndClear() después: detiene TTS y limpia el TextView.
     *   Si fuera al revés, el presenter podría encontrar un estado inconsistente
     *   (currentTextView=null en VoiceManager) cuando sus callbacks tardíos disparen.
     */
    @Override
    protected void onDoubleTapDetected() {
        // 1. Primero: el presenter cancela sus callbacks y marca el flag
        if (presenter != null) {
            presenter.onDoubleTap();
        }
        // 2. Después: el voiceManager detiene el TTS y limpia el texto
        if (voiceManager != null) {
            voiceManager.stopAndClear(tvResult);
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(cameraBridgeViewBase);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionsResult(requestCode, permissions, grantResults, this);
    }
}