package com.example.irislens.display.presenter;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.irislens.R;
import com.example.irislens.common.AccessibilityHelper;
import com.example.irislens.common.AppVoiceManager;
import com.example.irislens.common.ImageProcessor;
import com.example.irislens.display.model.DisplayDetector;

import org.opencv.core.Mat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DisplayRecognitionPresenter {
    private static final String TAG = "DisplayPresenter";
    private final Activity activity;
    private final TextView tvResult;
    private final DisplayDetector detector;
    private final AppVoiceManager voiceManager;
    private final AccessibilityHelper accessibilityHelper;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private volatile boolean isProcessing = false;
    private volatile boolean isAnnouncing = false;
    private int frameCount = 0;
    private static final int NO_DETECTION_THRESHOLD = 8;
    private int noDetectionCount = 0;
    private String lastResultText = "";
    private static final float MEDIUM_CONFIDENCE_THRESHOLD = 0.55f;
    private static final float LOW_CONFIDENCE_THRESHOLD = 0.45f;

    public DisplayRecognitionPresenter(Activity activity, TextView tvResult) throws IOException {
        this.activity = activity;
        this.tvResult = tvResult;
        this.voiceManager = AppVoiceManager.getInstance(activity);
        this.accessibilityHelper = new AccessibilityHelper(activity);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.detector = new DisplayDetector(activity.getApplicationContext());
    }

    /**
     * Procesa un frame de la camara para detectar digitos en un display.
     */
    public void processCameraFrame(Mat image) {
        if (isProcessing || isAnnouncing) {
            return;
        }

        frameCount++;
        if (frameCount == 5) {
            frameCount = 0;

            MediaPlayer mediaPlayer = MediaPlayer.create(activity, R.raw.captura);
            if (mediaPlayer != null) {
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
            }

            // Calcular brillo medio
            double meanBrightness = ImageProcessor.calculateMeanBrightness(image);

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

            Bitmap bitmap = ImageProcessor.convertToBitmap(image);
            isProcessing = true;

            detector.detect(bitmap, new DisplayDetector.DetectionCallback() {
                @Override
                public void onDetectionComplete(@NonNull List<DisplayDetector.DetectionResult> results,
                                                @NonNull List<DisplayDetector.DetectionResult> rawResults) {
                    mainHandler.post(() -> handleDetection(results, rawResults));
                }

                @Override
                public void onDetectionError(@NonNull Exception error) {
                    Log.e(TAG, "Error en detección TensorFlow Lite", error);
                    mainHandler.post(() -> {
                        isProcessing = false;
                        tvResult.setText("Error: " + error.getMessage());
                    });
                }
            });
        }
    }

    /**
     * Maneja los resultados de la deteccion.
     */
    private void handleDetection(List<DisplayDetector.DetectionResult> results,
                                 List<DisplayDetector.DetectionResult> rawResults) {

        if (results.isEmpty()) {
            noDetectionCount++;

            if (noDetectionCount >= NO_DETECTION_THRESHOLD) {
                String msg = "No se pudo detectar. Mejore la posición de la cámara o del objeto.";

                // Mostrar texto y hablar
                tvResult.setText(msg);
                announceMessage(msg);
//                isAnnouncing = true;
//                voiceManager.speak(msg);

                // Calcular duracion y limpiar automaticamente
                int duration = calculateSpeechDuration(msg);
                mainHandler.postDelayed(() -> {
                    tvResult.setText("");
                    isAnnouncing = false;
                    Log.d(TAG, "🧹 Texto limpiado automáticamente");
                }, duration);

                Log.d(TAG, "⚠️ Sin detecciones tras " + NO_DETECTION_THRESHOLD + " frames");
                noDetectionCount = 0;
            }
        } else {
            noDetectionCount = 0;

            DisplayResult displayResult = buildNumberFromDetections(results);

            if (!displayResult.isEmpty()) {
                // Log detallado
                StringBuilder logBuilder = new StringBuilder("🔍 DETECCIONES:\n");
                for (DisplayDetector.DetectionResult det : results) {
                    logBuilder.append(String.format("  %s | %.3f | [%.0f, %.0f]\n",
                            det.getLabel(), det.getConfidence(),
                            det.getBoundingBox().left, det.getBoundingBox().top));
                }
                Log.d(TAG, logBuilder.toString());

                // Mostrar texto coloreado
                setColoredText(displayResult);
                lastResultText = displayResult.getPlainText();

                // Reproducir audio con resultado
                String speechText = displayResult.getSpeechText();
                if (!speechText.isEmpty()) {
                    announceMessage(speechText);
                }
//                isAnnouncing = true;
//                voiceManager.speak(speechText);

                // Calcular duracion y limpiar automaticamente
                int duration = calculateSpeechDuration(speechText);
                mainHandler.postDelayed(() -> {
                    tvResult.setText("");
                    isAnnouncing = false;
                    Log.d(TAG, "🧹 Texto limpiado automáticamente");
                }, duration);

                Log.d(TAG, "✅ Detectado: " + lastResultText + " | Dígitos: " + results.size());
            }
        }

        isProcessing = false;
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
                accessibilityHelper.clearAccessibilityDescription(tvResult);
                isAnnouncing = false;
                tvResult.setFocusable(false);
                tvResult.setText("");
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

    /**
     * Construye el numero detectado a partir de los resultados.
     */
    private DisplayResult buildNumberFromDetections(List<DisplayDetector.DetectionResult> results) {
        if (results.isEmpty()) return new DisplayResult();

        List<DigitWithPosition> digits = new ArrayList<>();

        for (DisplayDetector.DetectionResult detection : results) {
            String label = detection.getLabel().trim();
            float confidence = detection.getConfidence();
            RectF box = detection.getBoundingBox();
            float xPosition = box.left + (box.width() / 2);

            ConfidenceLevel level;
            if (confidence >= MEDIUM_CONFIDENCE_THRESHOLD) {
                level = ConfidenceLevel.HIGH;
            } else if (confidence >= LOW_CONFIDENCE_THRESHOLD) {
                level = ConfidenceLevel.MEDIUM;
            } else {
                level = ConfidenceLevel.LOW;
            }

            digits.add(new DigitWithPosition(label, xPosition, confidence, level));
        }

        Collections.sort(digits, new Comparator<DigitWithPosition>() {
            @Override
            public int compare(DigitWithPosition d1, DigitWithPosition d2) {
                return Float.compare(d1.xPosition, d2.xPosition);
            }
        });

        return new DisplayResult(digits);
    }

    /**
     * Establece el texto coloreado en el TextView basado en los niveles de confianza.
     */
    private void setColoredText(DisplayResult result) {
        String fullText = result.getDisplayText();
        SpannableString spannable = new SpannableString(fullText);

        int position = 0;
        for (DigitWithPosition digit : result.digits) {
            int color;
            switch (digit.confidenceLevel) {
                case HIGH:
                    color = Color.GREEN;
                    break;
                case MEDIUM:
                    color = Color.rgb(255, 165, 0);
                    break;
                case LOW:
                    color = Color.rgb(255, 100, 100);
                    break;
                default:
                    color = Color.RED;
            }

            spannable.setSpan(
                    new ForegroundColorSpan(color),
                    position,
                    position + digit.digit.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            position += digit.digit.length();
        }

        tvResult.setText(spannable);
    }

    private enum ConfidenceLevel {
        HIGH, MEDIUM, LOW
    }

    private static class DigitWithPosition {
        String digit;
        float xPosition;
        float confidence;
        ConfidenceLevel confidenceLevel;

        DigitWithPosition(String digit, float xPosition, float confidence, ConfidenceLevel level) {
            this.digit = digit;
            this.xPosition = xPosition;
            this.confidence = confidence;
            this.confidenceLevel = level;
        }
    }

    private static class DisplayResult {
        List<DigitWithPosition> digits;

        DisplayResult() {
            this.digits = new ArrayList<>();
        }

        DisplayResult(List<DigitWithPosition> digits) {
            this.digits = digits;
        }

        boolean isEmpty() {
            return digits.isEmpty();
        }

        String getDisplayText() {
            StringBuilder sb = new StringBuilder();
            for (DigitWithPosition digit : digits) {
                sb.append(digit.digit);
            }
            return sb.toString();
        }

        String getPlainText() {
            return getDisplayText();
        }

        String getSpeechText() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < digits.size(); i++) {
                sb.append(digits.get(i).digit);
                if (i < digits.size() - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }
    }

    public void onDestroy() {
        // Cancelar callbacks pendientes
        mainHandler.removeCallbacksAndMessages(null);
        if (executor != null && !executor.isShutdown()) executor.shutdown();
        if (detector != null) detector.close();
    }

    public void onDoubleTap() {
        Log.d(TAG, "👆 Doble tap detectado");

        // Detener voz
        voiceManager.stop();

        // Limpiar texto
        tvResult.setText("");

        // Resetear flag y cancelar limpieza pendiente
        isAnnouncing = false;
        mainHandler.removeCallbacksAndMessages(null);

        Log.d(TAG, "✅ Voz detenida y pantalla limpia");
    }

    public String getLastResultText() {
        return lastResultText;
    }

    public boolean isProcessing() {
        return isProcessing;
    }
}