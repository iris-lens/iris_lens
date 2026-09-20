package com.example.irislens.money.model;

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
 * MoneyDetector: Detector de billetes usando TensorFlow Lite.
 * Modelo basado en YOLOv8n.
 */
public class MoneyDetector {
    private static final String TAG = "MoneyDetector";
    private static final String MODEL_PATH_SMALL = "detectorMoney_small.tflite";
    private static final String MODEL_PATH_NANO  = "detectorMoney_nano.tflite";
    private static final String LABELS_PATH = "labelsMoney.txt";

    private static final float CONF_THRESHOLD = 0.85f; // umbral de confianza minimo
    private static final float NMS_IOU_THRESHOLD = 0.5f;
    private static final int INPUT_SIZE = 640;

    private final Interpreter interpreter;
    private final List<String> labels;
    private final int numClasses;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface DetectionCallback {
        void onDetectionComplete(@NonNull List<DetectionResult> results);
        void onDetectionError(@NonNull Exception error);
    }

    /** Constructor - Inicializa el detector cargando el modelo y las etiquetas.
     * @param context Contexto de la aplicacion
     * @throws IOException Si hay error cargando el modelo o las etiquetas
     */
    public MoneyDetector(@NonNull Context context) throws IOException {
        Log.d(TAG, "Inicializando MoneyDetector...");
        // Cargar el modelo TFLite desde assets
        String selectedModel = selectModelPath();
        MappedByteBuffer model = FileUtil.loadMappedFile(context, selectedModel);
        // Configurar opciones del interprete (numero de hilos, NNAPI)
        Interpreter.Options options = new Interpreter.Options();
        int threads = Math.min(2, Runtime.getRuntime().availableProcessors());
        options.setNumThreads(threads);
        options.setUseNNAPI(false);

        // Inicializar el interprete de TensorFlow Lite
        interpreter = new Interpreter(model, options);
        Log.d(TAG, "✅ Modelo cargado: " + selectedModel + " | threads=" + threads);

        // Cargar las etiquetas desde assets
        labels = FileUtil.loadLabels(context, LABELS_PATH);
        numClasses = labels.size();
        Log.d(TAG, "✅ Labels cargadas (" + numClasses + "): " + labels);
    }

    /**
     * Detecta billetes en un Bitmap usando el modelo TFLite.
     * Ejecuta la deteccion en un hilo separado y retorna los resultados por callback.
     * @param bitmap Imagen de entrada
     * @param callback Callback para recibir resultados o errores
     */
    public void detect(@NonNull Bitmap bitmap, @NonNull DetectionCallback callback) {
        executor.execute(() -> {
            try {
                // Procesar la imagen y obtiene las detecciones
                List<DetectionResult> out = detectInternal(bitmap);
                // Retornar los resultados por el callback
                callback.onDetectionComplete(out);
            } catch (Exception e) {
                Log.e(TAG, "Error en detección", e);
                // Retornar el error por el callback
                callback.onDetectionError(e);
            }
        });
    }

    /** Deteccion interna (sincronica).
     * @param bitmap Imagen de entrada
     * @return Lista de detecciones
     */
    private List<DetectionResult> detectInternal(@NonNull Bitmap bitmap) {
        long t0 = System.currentTimeMillis();
        // Preprocesar la imagen para el modelo (resize y normalizacion)
        ByteBuffer input = preprocess(bitmap);

        // Obtiener la forma y tipo de la salida del modelo
        int[] outShape = interpreter.getOutputTensor(0).shape();
        DataType outType = interpreter.getOutputTensor(0).dataType();

        // Verificar que la salida tenga la forma esperada
        if (outShape.length != 3)
            throw new IllegalStateException("Output TFLite inesperado: " + Arrays.toString(outShape));

        // Preparar el array para la salida cruda del modelo
        float[][][] raw = new float[outShape[0]][outShape[1]][outShape[2]];
        interpreter.run(input, raw);

        // Determina el layout de la salida (canal primero o punto primero)
        int channels = 4 + numClasses;
        boolean layoutCFirst;
        if (outShape[1] == channels) layoutCFirst = true;
        else if (outShape[2] == channels) layoutCFirst = false;
        else layoutCFirst = (outShape[1] > outShape[2] && outShape[1] >= channels);

        // Parsea las predicciones segun el layout
        List<DetectionResult> preNms = layoutCFirst
                ? parsePreds_CFirst(raw[0], bitmap.getWidth(), bitmap.getHeight())
                : parsePreds_PFirst(raw[0], bitmap.getWidth(), bitmap.getHeight());

        // Aplica NMS para filtrar detecciones redundantes
        List<DetectionResult> finalDetections = nmsClassAgnostic(preNms, NMS_IOU_THRESHOLD);

        long dt = System.currentTimeMillis() - t0;
        Log.d(TAG, "⏱️ Inferencia+post: " + dt + " ms | detecciones finales=" + finalDetections.size());

        return finalDetections;
    }

    /** Preprocesa la imagen: redimensiona a INPUT_SIZE x INPUT_SIZE y normaliza a [0,1].
     * @param src Bitmap de entrada
     * @return ByteBuffer listo para pasar al modelo
     */
    private ByteBuffer preprocess(@NonNull Bitmap src) {
        // Redimensionar la imagen al tamaño de entrada del modelo
        Bitmap resized = Bitmap.createScaledBitmap(src, INPUT_SIZE, INPUT_SIZE, true);
        // Creae el buffer para los datos normalizados (float32)
        ByteBuffer buf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        buf.order(ByteOrder.nativeOrder());
        buf.rewind();

        // Extraer los pixeles de la imagen redimensionada
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // Normalizar cada canal RGB a [0,1] y agregar al buffer
        for (int p : pixels) {
            buf.putFloat(((p >> 16) & 0xFF) / 255.0f);
            buf.putFloat(((p >> 8) & 0xFF) / 255.0f);
            buf.putFloat((p & 0xFF) / 255.0f);
        }
        buf.rewind();
        return buf;
    }

    /**
     * Parsea las predicciones cuando el layout es canal primero.
     * @param preds Array de predicciones [canal][punto]
     * @param origW Ancho original de la imagen
     * @param origH Alto original de la imagen
     * @return Lista de DetectionResult
     */
    private List<DetectionResult> parsePreds_CFirst(float[][] preds, int origW, int origH) {
        List<DetectionResult> out = new ArrayList<>();
        int N = preds[0].length;    // cantidad de puntos
        for (int i = 0; i < N; i++) {
            float cx = preds[0][i], cy = preds[1][i], w = preds[2][i], h = preds[3][i];
            int best = -1; float bestScore = -1f;
            // Buscar la clase con mayor score
            for (int c = 0; c < numClasses; c++) {
                float s = preds[4 + c][i];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            // Filtrar por umbral de confianza
            if (bestScore < CONF_THRESHOLD || best < 0) continue;
            Log.d(TAG, "parseCFirst: class=" + best + " (" + labels.get(best) + ") score=" + bestScore +
                    " cx=" + cx + " cy=" + cy + " w=" + w + " h=" + h);
            // Agregar la deteccion
            addBox(out, cx, cy, w, h, best, bestScore, origW, origH);
        }
        return out;
    }

    /**
     * Parsea las predicciones cuando el layout es punto primero.
     * @param preds Array de predicciones [punto][canal]
     * @param origW Ancho original de la imagen
     * @param origH Alto original de la imagen
     * @return Lista de DetectionResult
     */
    private List<DetectionResult> parsePreds_PFirst(float[][] preds, int origW, int origH) {
        List<DetectionResult> out = new ArrayList<>();
        int N = preds.length;   // cantidad de puntos
        for (int i = 0; i < N; i++) {
            float cx = preds[i][0], cy = preds[i][1], w = preds[i][2], h = preds[i][3];
            int best = -1; float bestScore = -1f;
            // Buscar la clase con mayor score
            for (int c = 0; c < numClasses; c++) {
                float s = preds[i][4 + c];
                if (s > bestScore) { bestScore = s; best = c; }
            }
            // Filtrar por umbral de confianza
            if (bestScore < CONF_THRESHOLD || best < 0) continue;
            Log.d(TAG, "parsePFirst: class=" + best + " (" + labels.get(best) + ") score=" + bestScore +
                    " cx=" + cx + " cy=" + cy + " w=" + w + " h=" + h);
            // Agregar la deteccion
            addBox(out, cx, cy, w, h, best, bestScore, origW, origH);
        }
        return out;
    }

    /**
     * Agrega una deteccion a la lista, escalando la caja a la imagen original.
     * Si se desea mostrar en pantalla, se debe dibujar el boundingBox de DetectionResult.
     * @param list Lista de detecciones
     * @param cx Centro X de la caja (modelo)
     * @param cy Centro Y de la caja (modelo)
     * @param w Ancho de la caja (modelo)
     * @param h Alto de la caja (modelo)
     * @param classId ID de la clase detectada
     * @param score Confianza de la detección
     * @param origW Ancho original de la imagen
     * @param origH Alto original de la imagen
     */
    private void addBox(List<DetectionResult> list, float cx, float cy, float w, float h,
                        int classId, float score, int origW, int origH) {
        // Calcular bordes de la caja en coordenadas del modelo
        float left = cx - w/2f, top = cy - h/2f, right = cx + w/2f, bottom = cy + h/2f;
        // Escalar a dimensiones originales
        float scaleX = (float) origW / INPUT_SIZE, scaleY = (float) origH / INPUT_SIZE;
        RectF box = new RectF(
                clamp(left*scaleX, 0, origW),
                clamp(top*scaleY, 0, origH),
                clamp(right*scaleX, 0, origW),
                clamp(bottom*scaleY, 0, origH)
        );
        // Verificar que la caja tenga tamaño valido
        if (box.width() <= 0 || box.height() <= 0) return;
        // Obtener el label de la clase
        String label = (classId >= 0 && classId < labels.size()) ? labels.get(classId) : ("class_" + classId);
        // Agregar la deteccion a la lista
        list.add(new DetectionResult(label, score, box));
    }

    /**
     * Limita el valor v entre lo y hi.
     * @param v Valor a limitar
     * @param lo Mínimo permitido
     * @param hi Máximo permitido
     * @return Valor limitado
     */
    private float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /**
     * Aplica supresion de no-maximos (NMS) agnostico de clase para filtrar detecciones solapadas.
     * Es importante porque el modelo puede devolver multiples detecciones para el mismo objeto,
     * muchas de ellas duplicadas o solapadas, entonces nos debemos quedar solo con las mejores.
     * @param dets Lista de detecciones
     * @param iouTh Umbral de IoU para descartar
     * @return Lista filtrada de detecciones
     */
    private List<DetectionResult> nmsClassAgnostic(List<DetectionResult> dets, float iouTh) {
        if (dets.isEmpty()) return dets;
        // Ordenar por confianza descendente
        Collections.sort(dets, (a,b) -> Float.compare(b.confidence, a.confidence));
        List<DetectionResult> kept = new ArrayList<>();
        boolean[] removed = new boolean[dets.size()];
        for (int i = 0; i < dets.size(); i++) {
            if (removed[i]) continue;
            DetectionResult di = dets.get(i);
            kept.add(di);
            // Comparar con las siguientes detecciones
            for (int j = i+1; j < dets.size(); j++) {
                if (removed[j]) continue;
                if (iou(di.boundingBox, dets.get(j).boundingBox) > iouTh) removed[j]=true;
            }
        }
        return kept;
    }

    /**
     * Calcula el IoU (intersección sobre union) entre dos cajas.
     * @param a Primera caja
     * @param b Segunda caja
     * @return Valor de IoU [0,1]
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

    /** Libera los recursos del interprete y TFLite y apaga el executor (hilo). */
    public void close() {
        try { interpreter.close(); } catch (Throwable ignore) {}
        executor.shutdown();
    }

    /**
     * Clase que representa el resultado de una deteccion de billete y sirve para encapsular
     * los datos de cada deteccion. Incluye la etiqueta, confianza y caja delimitadora.
     */
    public static class DetectionResult {
        private final String label;
        private final float confidence;
        private final RectF boundingBox;
        public DetectionResult(String label, float confidence, RectF boundingBox) {
            this.label = label; this.confidence = confidence; this.boundingBox = boundingBox;
        }
        public String getLabel() { return label; }
        public float getConfidence() { return confidence; }
        public RectF getBoundingBox() { return boundingBox; }
        @NonNull
        @Override
        public String toString() {
            return "DetectionResult{" + "label='" + label + '\'' +
                    ", confidence=" + confidence + ", box=" + boundingBox + '}';
        }
    }

    private String selectModelPath() {
        int sdk = android.os.Build.VERSION.SDK_INT;
        int cores = Runtime.getRuntime().availableProcessors();
        long ram = Runtime.getRuntime().maxMemory() / (1024 * 1024); // MB aprox

        Log.d(TAG, "Device info → SDK=" + sdk + " cores=" + cores + " RAM=" + ram + "MB");

        // 🔴 Dispositivos con bajos recursos
        if (sdk <= 23 || cores <= 4 || ram < 256) {
            Log.d(TAG, "📱 Modo ligero → YOLO NANO");
            return MODEL_PATH_NANO;
        }

        // 🚀 Dispositivos con altos recursos
        Log.d(TAG, "🚀 Modo completo → YOLO SMALL");
        return MODEL_PATH_SMALL;
    }
}