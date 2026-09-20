package com.example.irislens.common;

import android.graphics.Bitmap;

import androidx.core.util.Pair;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class ImageProcessor {
    /**
     * Rota la imagen de la camara a un angulo especifico. Se utiliza para corregir la orientacion
     * de la camara de OpenCV que se muestra en la pantalla.
     *
     * @param src La imagen de origen (camara de OpenCV)
     * @return La imagen rotada (camera de OpenCV)
     */
    public static Mat rotateImage(Mat src) {
        Mat dst = new Mat();
        // Obtener el centro de la imagen
        Point center = new Point(src.cols() / 2, src.rows() / 2);
        // Definir el ángulo de rotación (90 grados)
        double angle = -90;
        // Obtener la matriz de rotación
        Mat rotationMatrix = Imgproc.getRotationMatrix2D(center, angle, 1.0);
        // Rotar la imagen
        Imgproc.warpAffine(src, dst, rotationMatrix, new Size(src.cols(), src.rows()));
        return dst;
    }

    /**
     * Convierte una imagen de OpenCV a un Bitmap
     *
     * @param image La imagen a convertir
     * @return El Bitmap resultante
     */
    public static Bitmap convertToBitmap(Mat image) {
        // Convertir la imagen a un Bitmap para procesarla con Tesseract
        Bitmap bitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, bitmap);
        return bitmap;
    }

    /**
     * Preprocesa una imagen para mejorar la visibilidad del texto
     *
     * @param image La imagen a procesar
     * @return La imagen preprocesada y el brillo medio de la imagen
     */
    public static Pair<Mat, Double> preprocessImage(Mat image) {
        // Convertir a escala de grises
        Imgproc.cvtColor(image, image, Imgproc.COLOR_RGB2GRAY);

        // Calcular el brillo medio del frame
        Scalar mean = Core.mean(image);
        double meanBrightness = mean.val[0];

        // Si el brillo medio del frame es muy bajo o muy alto, se aplican
        // correcciones gamma para mejorar la visibilidad del texto. Si no usa
        // directo la imagen original en escala de grises.
        if(meanBrightness < 50 || meanBrightness > 200) {
            // Convertir la matriz a CV_32F (por si la correccion gamma tiene valores no enteros)
            image.convertTo(image, CvType.CV_32F);

            // Normalizar los valores de los pixeles al rango 0-1 (por si la correccion gamma tiene valores no enteros)
            Core.divide(image, new Scalar(255), image);

            // Asigar el valor de gamma segun el brillo medio del frame
            double gamma = 1.0;
            if (meanBrightness < 50 && meanBrightness >= 15) {
                gamma = 2;  // Si la imagen es oscura, aumentar el brillo
            } else if (meanBrightness > 200) {
                gamma = 0.5;  // Si la imagen es clara, disminuir el brillo
            }

            // Aplicar la corrección gamma
            Core.pow(image, gamma, image);

            // Convertir los valores de los píxeles de nuevo al rango 0-255 (por si la correccion gamma tiene valores no enteros)
            Core.multiply(image, new Scalar(255), image);

            // Convertir la matriz de nuevo a CV_8U (por si la correccion gamma tiene valores no enteros)
            image.convertTo(image, CvType.CV_8U);
        }

        // Devolver la imagen y el brillo medio
        return new Pair<>(image, meanBrightness);
    }

    /**
     * Calcula el brillo medio de una imagen
     *
     * @param image La imagen a procesar
     * @return El brillo medio de la imagen
     */
    public static double calculateMeanBrightness(Mat image) {
        Mat gray = new Mat();

        // Convertir a escala de grises SIN modificar la original
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_RGB2GRAY);

        // Calcular brillo medio
        Scalar mean = Core.mean(gray);
        double meanBrightness = mean.val[0];

        gray.release();

        return meanBrightness;
    }

    /**
     * Rota una imagen 180 grados. Se utiliza para rotar un frame capturado antes de procesarlo.
     *
     * @param image La imagen a rotar
     * @return La imagen rotada
     */
    public static Mat rotateImage180(Mat image) {
        Mat rotatedImage = new Mat();
        // Volcar la imagen horizontalmente
        Core.flip(image, rotatedImage, 0);
        // Volcar la imagen resultante verticalmente
        Core.flip(rotatedImage, rotatedImage, 1);
        return rotatedImage;
    }

    /**
     * Rota una imagen 90 grados. Se utiliza para rotar un frame capturado antes de procesarlo.
     *
     * @param image La imagen a rotar
     * @return La imagen rotada
     */
    public static Mat rotateImage90(Mat image) {
        Mat rotatedImage = new Mat();
        // Transponer la imagen
        Core.transpose(image, rotatedImage);
        // Volcar la imagen transpuesta verticalmente
        Core.flip(rotatedImage, rotatedImage, 1);
        return rotatedImage;
    }

    /**
     * Rota una imagen 270 grados. Se utiliza para rotar un frame capturado antes de procesarlo.
     *
     * @param image La imagen a rotar
     * @return La imagen rotada
     */
    public static Mat rotateImage270(Mat image) {
        Mat rotatedImage = new Mat();
        // Transponer la imagen
        Core.transpose(image, rotatedImage);
        // Volcar la imagen transpuesta horizontalmente
        Core.flip(rotatedImage, rotatedImage, 0);
        return rotatedImage;
    }
}