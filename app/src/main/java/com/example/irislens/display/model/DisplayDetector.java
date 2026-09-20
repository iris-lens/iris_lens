package com.example.irislens.display.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.NonNull;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DisplayDetector: Detector de dígitos en displays usando TensorFlow Lite.
 * Modelo basado en YOLOv8n para reconocimiento de números en pantallas.
 */
public class DisplayDetector {
    private static final String TAG = "DisplayDetector";
    private static final String MODEL_PATH_SMALL = "detectorDisplay_small.tflite";
    private static final String MODEL_PATH_NANO  = "detectorDisplay_nano.tflite";
    private static final String LABELS_PATH = "labelsDisplay.txt";

    private static final float CONF_THRESHOLD = 0.45f;
    private static final float NMS_IOU_THRESHOLD = 0.4f;
    private static final int INPUT_SIZE = 640;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int numClasses;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface DetectionCallback {
        void onDetectionComplete(@NonNull List<DetectionResult> results,
                                 @NonNull List<DetectionResult> rawResults);
        void onDetectionError(@NonNull Exception error);
    }

    /**
     * Constructor que carga el modelo TFLite y las etiquetas.
     * @param context Contexto de la aplicación.
     * @throws IOException Si hay un error al cargar el modelo o las etiquetas.
     */
    public DisplayDetector(@NonNull Context context) throws IOException {
        Log.d(TAG, "Inicializando DisplayDetector...");
        String selectedModel = selectModelPath();
        MappedByteBuffer model = FileUtil.loadMappedFile(context, selectedModel);
        Interpreter.Options options = new Interpreter.Options();
        int threads = Math.min(2, Runtime.getRuntime().availableProcessors());
        options.setNumThreads(threads);
        options.setUseNNAPI(false);

        interpreter = new Interpreter(model, options);
        Log.d(TAG, "✅ Modelo cargado: " + selectedModel + " | threads=" + threads);

        labels = FileUtil.loadLabels(context, LABELS_PATH);
        numClasses = labels.size();
        Log.d(TAG, "✅ Labels cargadas (" + numClasses + "): " + labels);
    }

    /**
     * Realiza la deteccion de digitos en la imagen proporcionada.
     * @param bitmap Imagen de entrada como Bitmap.
     * @param callback Callback para manejar los resultados o errores.
     */
    public void detect(@NonNull Bitmap bitmap, @NonNull DetectionCallback callback) {
        executor.execute(() -> {
            try {
                List<DetectionResult>[] results = detectInternal(bitmap);
                callback.onDetectionComplete(results[0], results[1]);
            } catch (Exception e) {
                Log.e(TAG, "Error en detección", e);
                callback.onDetectionError(e);
            }
        });
    }

    /**
     * Realiza la deteccion interna y devuelve los resultados.
     */
    private List<DetectionResult>[] detectInternal(@NonNull Bitmap bitmap) {
        long t0 = System.currentTimeMillis();
        ByteBuffer input = preprocess(bitmap);

        int[] outShape = interpreter.getOutputTensor(0).shape();
        DataType outType = interpreter.getOutputTensor(0).dataType();

        if (outShape.length != 3)
            throw new IllegalStateException("Output TFLite inesperado: " + Arrays.toString(outShape));

        float[][][] raw = new float[outShape[0]][outShape[1]][outShape[2]];
        interpreter.run(input, raw);

        int channels = 4 + numClasses;
        boolean layoutCFirst;
        if (outShape[1] == channels) layoutCFirst = true;
        else if (outShape[2] == channels) layoutCFirst = false;
        else layoutCFirst = (outShape[1] > outShape[2] && outShape[1] >= channels);

        // Parsear SIN umbral para resultados RAW
        List<DetectionResult> rawResults = layoutCFirst
                ? parsePreds_CFirst(raw[0], bitmap.getWidth(), bitmap.getHeight(), 0.01f)
                : parsePreds_PFirst(raw[0], bitmap.getWidth(), bitmap.getHeight(), 0.01f);

        // Parsear CON umbral normal
        List<DetectionResult> preNms = layoutCFirst
                ? parsePreds_CFirst(raw[0], bitmap.getWidth(), bitmap.getHeight(), CONF_THRESHOLD)
                : parsePreds_PFirst(raw[0], bitmap.getWidth(), bitmap.getHeight(), CONF_THRESHOLD);

        List<DetectionResult> finalDetections = nmsClassAgnostic(preNms, NMS_IOU_THRESHOLD);

        long dt = System.currentTimeMillis() - t0;
        Log.d(TAG, "⏱️ Inferencia: " + dt + "ms | Raw: " + rawResults.size() +
                " | Post-filtro: " + finalDetections.size());

        return new List[]{finalDetections, rawResults};
    }

    /**
     * Preprocesa la imagen de entrada para el modelo.
     * @param src Bitmap de entrada.
     * @return ByteBuffer con la imagen preprocesada.
     */
    private ByteBuffer preprocess(@NonNull Bitmap src) {
        Bitmap resized = Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        buf.order(ByteOrder.nativeOrder());
        buf.rewind();

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int p : pixels) {
            buf.putFloat(((p >> 16) & 0xFF) / 255.0f);
            buf.putFloat(((p >> 8) & 0xFF) / 255.0f);
            buf.putFloat((p & 0xFF) / 255.0f);
        }
        buf.rewind();
        return buf;
    }

    /**
     * Parsea las predicciones del modelo en formato C-first.
     */
    private List<DetectionResult> parsePreds_CFirst(float[][] preds, int origW, int origH, float threshold) {
        List<DetectionResult> out = new ArrayList<>();
        int N = preds[0].length;
        for (int i = 0; i < N; i++) {
            float cx = preds[0][i], cy = preds[1][i], w = preds[2][i], h = preds[3][i];
            int best = -1; float bestScore = -1f;
            for (int c = 0; c < numClasses; c++) {
                float s = preds[4 + c][i];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            if (bestScore < threshold || best < 0) continue;
            addBox(out, cx, cy, w, h, best, bestScore, origW, origH);
        }
        return out;
    }

    /**
     * Parsea las predicciones del modelo en formato P-first.
     */
    private List<DetectionResult> parsePreds_PFirst(float[][] preds, int origW, int origH, float threshold) {
        List<DetectionResult> out = new ArrayList<>();
        int N = preds.length;
        for (int i = 0; i < N; i++) {
            float cx = preds[i][0], cy = preds[i][1], w = preds[i][2], h = preds[i][3];
            int best = -1; float bestScore = -1f;
            for (int c = 0; c < numClasses; c++) {
                float s = preds[i][4 + c];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            if (bestScore < threshold || best < 0) continue;
            addBox(out, cx, cy, w, h, best, bestScore, origW, origH);
        }
        return out;
    }

    /**
     * Añade una caja delimitadora a la lista de resultados.
     */
    private void addBox(List<DetectionResult> list, float cx, float cy, float w, float h,
                        int classId, float score, int origW, int origH) {
        float left = cx - w/2f, top = cy - h/2f, right = cx + w/2f, bottom = cy + h/2f;
        float scaleX = (float) origW / INPUT_SIZE, scaleY = (float) origH / INPUT_SIZE;
        RectF box = new RectF(
                clamp(left*scaleX, 0, origW),
                clamp(top*scaleY, 0, origH),
                clamp(right*scaleX, 0, origW),
                clamp(bottom*scaleY, 0, origH)
        );
        if (box.width() <= 0 || box.height() <= 0) return;
        String label = (classId >= 0 && classId < labels.size()) ? labels.get(classId) : ("digit_" + classId);
        list.add(new DetectionResult(label, score, box));
    }

    /**
     * Clampea un valor entre un minimo y un maximo.
     */
    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * Aplica Non-Maximum Suppression (NMS) agnostico a la clase.
     */
    private List<DetectionResult> nmsClassAgnostic(List<DetectionResult> dets, float iouTh) {
        if (dets.isEmpty()) return dets;
        Collections.sort(dets, (a,b) -> Float.compare(b.confidence, a.confidence));
        List<DetectionResult> kept = new ArrayList<>();
        boolean[] removed = new boolean[dets.size()];
        for (int i = 0; i < dets.size(); i++) {
            if (removed[i]) continue;
            DetectionResult di = dets.get(i);
            kept.add(di);
            for (int j = i+1; j < dets.size(); j++) {
                if (removed[j]) continue;
                if (iou(di.boundingBox, dets.get(j).boundingBox) > iouTh) removed[j]=true;
            }
        }
        return kept;
    }

    /**
     * Calcula el Intersection over Union (IoU) entre dos rectangulos.
     */
    private float iou(RectF a, RectF b) {
        float ix1 = Math.max(a.left, b.left), iy1 = Math.max(a.top, b.top);
        float ix2 = Math.min(a.right, b.right), iy2 = Math.min(a.bottom, b.bottom);
        float iw = Math.max(0, ix2-ix1), ih = Math.max(0, iy2-iy1);
        float inter = iw*ih;
        float areaA = Math.max(0,a.width())*Math.max(0,a.height());
        float areaB = Math.max(0,b.width())*Math.max(0,b.height());
        float union = areaA+areaB-inter;
        return union>0? inter/union:0f;
    }

    /**
     * Cierra el interprete y el ejecutor.
     */
    public void close() {
        try { interpreter.close(); } catch (Throwable ignore) {}
        executor.shutdown();
    }

    /**
     * Resultado de una deteccion.
     */
    public static class DetectionResult {
        private final String label;
        private final float confidence;
        private final RectF boundingBox;

        public DetectionResult(String label, float confidence, RectF boundingBox) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        public String getLabel() { return label; }
        public float getConfidence() { return confidence; }
        public RectF getBoundingBox() { return boundingBox; }

        @NonNull
        @Override
        public String toString() {
            return String.format("Det{%s %.2f [%.0f,%.0f,%.0f,%.0f]}",
                    label, confidence, boundingBox.left, boundingBox.top,
                    boundingBox.right, boundingBox.bottom);
        }
    }

    private String selectModelPath() {
        int sdk = android.os.Build.VERSION.SDK_INT;
        int cores = Runtime.getRuntime().availableProcessors();
        long ram = Runtime.getRuntime().maxMemory() / (1024 * 1024); // MB aprox

        Log.d(TAG, "Device info → SDK=" + sdk + " cores=" + cores + " RAM=" + ram + "MB");

        // Dispositivos con bajo recursos
        if (sdk <= 23 || cores <= 4 || ram < 256) {
            Log.d(TAG, "📱 Modo ligero → YOLO NANO");
            return MODEL_PATH_NANO;
        }

        // Dispositivos con altos recursos
        Log.d(TAG, "🚀 Modo completo → YOLO SMALL");
        return MODEL_PATH_SMALL;
    }
}