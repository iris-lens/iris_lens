package com.example.irislens.money.view;

import android.os.Bundle;
import android.os.Handler;
import android.view.Surface;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.money.presenter.MoneyRecognitionPresenter;
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

public class MoneyRecognitionActivity extends BaseSwipeActivity {
    private static final String TAG = "MoneyActivity";

    private PermissionManager permissionManager;
    private CameraBridgeViewBase cameraBridgeViewBase;
    private Mat mRgba;
    private TextView tvResult;
    private MoneyRecognitionPresenter presenter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_money_recognition);

        currentFunctionalityIndex = Functionalities.MONEY;

        cameraBridgeViewBase = findViewById(R.id.camera_view);
        tvResult = findViewById(R.id.tvResult);
        String message = "Reconocimiento de billetes. Apunte la cámara hacia el billete que desea reconocer.";

        // Inicializar PermissionManager y solicitar permiso de camara
        permissionManager = new PermissionManager();
        permissionManager.getPermissions(this);

        try {
            presenter = new MoneyRecognitionPresenter(this, tvResult);
        } catch (IOException e) {
            Log.e(TAG, "Error cargando el modelo de billetes TensorFlow Lite", e);
            tvResult.setText("Error cargando el modelo de billetes: " + e.getMessage());

            // Mostrar información más detallada del error
            if (e.getMessage() != null) {
                if (e.getMessage().contains("assets")) {
                    Log.e(TAG, "Error relacionado con assets - verificar que los archivos detectorMoney_small.tflite o detectorMoney_nano.tflite, y labelsMoney.txt estén en assets/");
                } else if (e.getMessage().contains("model")) {
                    Log.e(TAG, "Error del modelo - verificar formato TensorFlow Lite");
                }
            }
        }

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
                // Inicializar la matriz RGBA
                mRgba = new Mat(height, width, CvType.CV_8UC4);
            }

            @Override
            public void onCameraViewStopped() {
                mRgba.release();
            }

            /** Procesa cada frame capturado por la camara
             * @param inputFrame Frame de la camara
             * @return Mat a mostrar en pantalla (el mismo inputFrame)
             */
            @Override
            public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
                // Obtener la imagen en formato RGBA
                Mat originalImage = inputFrame.rgba();

                // Rotar la imagen si la orientacion del dispositivo es vertical
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                if (rotation == Surface.ROTATION_0) {
                    originalImage = com.example.irislens.common.ImageProcessor.rotateImage(originalImage);
                }
                // Procesar el frame en el presentador
                if (presenter != null) {
                    presenter.processCameraFrame(originalImage);
                }
                // Devolver la imagen original para mostrarla en pantalla
                return originalImage;
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraBridgeViewBase != null) cameraBridgeViewBase.disableView();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraBridgeViewBase != null) cameraBridgeViewBase.enableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (cameraBridgeViewBase != null) {
            cameraBridgeViewBase.disableView();
        }

        if (presenter != null) {
            presenter.onDestroy();
        }

        if (voiceManager != null) {
            voiceManager.stop();
        }
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

    /** Manejo del resultado de los permisos */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.handlePermissionsResult(requestCode, permissions, grantResults, this);
    }
}