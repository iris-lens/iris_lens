package com.example.irislens.medicine.presenter;

import android.app.Activity;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.example.irislens.R;
import com.example.irislens.medicine.model.DatabaseManager;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.medicine.model.ReadImageText;
import com.example.irislens.medicine.sync.MedicineSyncManager;
import com.example.irislens.common.AppVoiceManager;
import com.example.irislens.common.AccessibilityHelper;
import com.example.irislens.medicine.model.Tools;

import org.opencv.core.Mat;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

import androidx.core.util.Pair;

public class MedicineRecognitionPresenter {
    private static final String TAG = "MedicinePresenter";

    private final Activity activity;
    private final TextView tvResult;
    private final DatabaseManager dbManager;
    private final AppVoiceManager voiceManager;
    private final AccessibilityHelper accessibilityHelper;
    private final ReadImageText readImageText;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private int noDetectionCount = 0;
    private boolean rotate = false;
    private int rotationState = 0;
    private int frameCount = 0;
    private volatile boolean isProcessing = false;
    private volatile boolean isAnnouncing = false;

    public MedicineRecognitionPresenter(Activity activity, TextView tvResult) {
        this.activity = activity;
        this.tvResult = tvResult;
        this.dbManager = new DatabaseManager(activity.getApplicationContext());
        this.voiceManager = AppVoiceManager.getInstance(activity);
        this.accessibilityHelper = new AccessibilityHelper(activity);
        this.readImageText = new ReadImageText(activity.getApplicationContext());
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Asegura que la base local exista y sincroniza con Firestore usando el
     * motor único de sync (respeta modificado_local, eliminado_local y
     * es_semilla). Antes acá había SQL de sync propio con CONFLICT_REPLACE
     * que borraba y reemplazaba filas sin respetar ediciones locales — no
     * volver a hacer eso.
     */
    public void initDatabase() {
        dbManager.getReadableDatabase();
        Log.d("DB", "Base de datos lista. Sincronizando con Firestore...");

        MedicineSyncManager syncManager = new MedicineSyncManager(activity.getApplicationContext());

        syncManager.sincronizarMedicamentos((nuevos, actualizados, vinculados) -> {
            Toast.makeText(activity,
                    "Medicamentos — nuevos: " + nuevos +
                            ", actualizados: " + actualizados +
                            ", vinculados: " + vinculados,
                    Toast.LENGTH_SHORT).show();
        });

        syncManager.sincronizarPrincipiosActivos((nuevos, actualizados, vinculados) -> {
            Toast.makeText(activity,
                    "Principios activos — nuevos: " + nuevos +
                            ", actualizados: " + actualizados +
                            ", vinculados: " + vinculados,
                    Toast.LENGTH_SHORT).show();
        });
    }

    public Mat rotateImage(Mat image) {
        return ImageProcessor.rotateImage(image);
    }

    /**
     * Procesa un frame de la camara para detectar medicamentos.
     */
    public void processCameraFrame(Mat image) {
        if (isProcessing || isAnnouncing) {
            return;
        }

        frameCount++;
        if (frameCount == 10) {
            frameCount = 0;

            MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.captura);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
            }

            Pair<Mat, Double> processedImageAndBrightness = ImageProcessor.preprocessImage(image);
            Mat mRgba = processedImageAndBrightness.first;
            double meanBrightness = processedImageAndBrightness.second;

            // Verificar brillo bajo
            if (meanBrightness < 30) {
                activity.runOnUiThread(() -> announceMessage("El objeto no se distingue correctamente, aleje un poco la cámara o el objeto."));
                return;
            }

            // Verificar brillo alto
            if (meanBrightness > 220) {
                activity.runOnUiThread(() -> announceMessage("El objeto no se distingue correctamente, cambie levemente la posición o inclinación de la cámara."));
                return;
            }

            if (noDetectionCount > 0 && rotate) {
                switch (rotationState) {
                    case 0:
                        processImageAndSearchMatches(mRgba);
                        rotationState = 1;
                        break;
                    case 1:
                        mRgba = ImageProcessor.rotateImage180(mRgba);
                        processImageAndSearchMatches(mRgba);
                        rotationState = 2;
                        break;
                    case 2:
                        mRgba = ImageProcessor.rotateImage90(mRgba);
                        processImageAndSearchMatches(mRgba);
                        rotationState = 3;
                        break;
                    case 3:
                        mRgba = ImageProcessor.rotateImage270(mRgba);
                        processImageAndSearchMatches(mRgba);
                        rotationState = 0;
                        break;
                }
            } else {
                processImageAndSearchMatches(mRgba);
            }
        }
    }

    /**
     * Procesa la imagen y busca coincidencias en la base de datos.
     */
    private void processImageAndSearchMatches(Mat image) {
        Bitmap bitmap = ImageProcessor.convertToBitmap(image);
        isProcessing = true;

        executor.execute(() -> {
            String result = readImageText.processImage(bitmap);
            mainHandler.post(() -> handleImageProcessingResult(result));
        });
    }

    /**
     * Maneja el resultado del procesamiento de la imagen.
     */
    private void handleImageProcessingResult(String result) {
        String finalResult = Tools.cleanupText(result);
        Map<String, Object> searchResult = Tools.searchSimilarity(finalResult, dbManager.getReadableDatabase());
        List<Pair<String, String>> matches = (List<Pair<String, String>>) searchResult.get("matches");
        boolean multiples = (boolean) searchResult.get("multiplesMedicamentos");

        activity.runOnUiThread(() -> {
            if (!matches.isEmpty()) {
                if (multiples) {
                    String msg = "Se detectaron varios medicamentos. Por favor, seleccione solo uno.";
                    announceMessage(msg);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (Pair<String, String> match : matches) {
                        sb.append(match.first);
                        if (!match.second.isEmpty()) {
                            sb.append(": ").append(match.second);
                        }
                        sb.append("\n");
                    }

                    String speechText = sb.toString();
                    announceMedicine(speechText);
                }
                noDetectionCount = 0;
                rotate = false;
                rotationState = 0;
            } else {
                noDetectionCount++;
                rotate = true;

                // Solo limpiar si no hay un mensaje activo en pantalla
                if (!isAnnouncing) {
                    tvResult.setText("");
                }

                // Limpiar descripcion de accesibilidad cuando no hay detección
                if (accessibilityHelper.isTalkBackEnabled()) {
                    accessibilityHelper.clearAccessibilityDescription(tvResult);
                }
            }

            if (noDetectionCount == 8) {
                String msg = "No se pudo detectar. Mejore la posición de la cámara o del objeto.";
                announceMessage(msg);
                noDetectionCount = 0;
                rotate = false;
            }

            isProcessing = false;
        });
    }

    /**
     * Anuncia mensajes generales (iluminacion, multiples medicamentos, etc.)
     */
    public void announceMessage(String message) {
        Log.d(TAG, "📢 Anunciando mensaje: " + message);

        tvResult.setText(message);
        isAnnouncing = true;

        if (accessibilityHelper.isTalkBackEnabled()) {
            // Con TalkBack: usar sistema de accesibilidad
            Log.d(TAG, "📱 TalkBack ACTIVO - usando AccessibilityHelper");
            accessibilityHelper.announceForAccessibility(tvResult, message);

            // Limpiar despues de que TalkBack termine
            int duration = calculateSpeechDuration(message);
            mainHandler.postDelayed(() -> {
                tvResult.setText("");
                accessibilityHelper.clearAccessibilityDescription(tvResult);
                isAnnouncing = false;
                tvResult.setFocusable(false);
                Log.d(TAG, "🧹 Mensaje limpiado (TalkBack)");
            }, duration);
        } else {
            // Sin TalkBack: usar TTS normal
            Log.d(TAG, "🔊 TalkBack INACTIVO - usando TTS");
            voiceManager.speak(message);

            int duration = calculateSpeechDuration(message);
            mainHandler.postDelayed(() -> {
                tvResult.setText("");
                isAnnouncing = false;
                Log.d(TAG, "🧹 Mensaje limpiado (TTS)");
            }, duration);
        }
    }

    /**
     * Anuncia medicamentos detectados (mas tiempo en pantalla)
     */
    private void announceMedicine(String medicineInfo) {
        Log.d(TAG, "💊 Anunciando medicamento: " + medicineInfo);

        tvResult.setText(medicineInfo);
        isAnnouncing = true;

        if (accessibilityHelper.isTalkBackEnabled()) {
            // Con TalkBack: usar sistema de accesibilidad
            Log.d(TAG, "📱 TalkBack ACTIVO - usando AccessibilityHelper para medicamento");
            accessibilityHelper.announceForAccessibility(tvResult, medicineInfo);

            int duration = calculateSpeechDuration(medicineInfo);
            mainHandler.postDelayed(() -> {
                tvResult.setText("");
                accessibilityHelper.clearAccessibilityDescription(tvResult);
                isAnnouncing = false;
                tvResult.setFocusable(false);
                Log.d(TAG, "🧹 Medicamento limpiado (TalkBack)");
            }, duration);
        } else {
            // Sin TalkBack: usar TTS normal
            Log.d(TAG, "🔊 TalkBack INACTIVO - usando TTS para medicamento");
            voiceManager.speak(medicineInfo);

            int duration = calculateSpeechDuration(medicineInfo);
            mainHandler.postDelayed(() -> {
                tvResult.setText("");
                isAnnouncing = false;
                Log.d(TAG, "🧹 Medicamento limpiado (TTS)");
            }, duration);
        }
    }

    /**
     * Calcula duracion estimada del habla
     */
    private int calculateSpeechDuration(String text) {
        int wordCount = text.split("\\s+").length;

        if (accessibilityHelper.isTalkBackEnabled()) {
            // TalkBack es mas rapido que TTS (~150 palabras/minuto)
            int baseDuration = (int) ((wordCount / 2.5) * 1000);
            return baseDuration + 1000;
        } else {
            // TTS es mas lento (~120 palabras/minuto)
            int baseDuration = (int) ((wordCount / 2.0) * 1000);
            return baseDuration + 1000;
        }
    }

    public void onDoubleTap() {
        Log.d(TAG, "👆 Doble tap detectado");
        voiceManager.stop();
        tvResult.setText("");

        // Limpiar la descripcion de accesibilidad
        if (accessibilityHelper.isTalkBackEnabled()) {
            accessibilityHelper.clearAccessibilityDescription(tvResult);
        }

        isAnnouncing = false;
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "✅ Voz detenida y pantalla limpia");
    }

    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (executor != null) executor.shutdown();
    }
}