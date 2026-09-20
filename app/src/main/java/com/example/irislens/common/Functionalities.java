package com.example.irislens.common;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import com.example.irislens.medicine.view.MedicineRecognitionActivity;
import com.example.irislens.medicine.view.ManageMedicinesActivity;
import com.example.irislens.medicine.view.ManageActiveIngredientsActivity;
import com.example.irislens.money.view.MoneyRecognitionActivity;
import com.example.irislens.display.view.DisplayRecognitionActivity;

/**
 * Clase que gestiona las funcionalidades disponibles de la aplicacion.
 * Permite lanzar actividades, obtener nombres descriptivos y navegar entre funcionalidades
 */
public class Functionalities {

    // Índices de las funcionalidades. Primero van todos los reconocimientos
    // (medicamentos, billetes, displays) y recién después las dos secciones
    // de gestión, para que la persona aprenda un orden simple al navegar
    // con swipe: "reconocer" y después "gestionar mis listas".
    public static final int WELCOME = 0;
    public static final int MEDICINE = 1;
    public static final int MONEY = 2;
    public static final int DISPLAY = 3;
    public static final int MANAGE_MEDICINES = 4;
    public static final int MANAGE_ACTIVE_INGREDIENTS = 5;
    public static final int MANUAL = 6;
    public static final int ABOUT = 7;

    // Tag para log
    private static final String TAG = "Functionalities";

    // Lista de nombres correspondientes a las funcionalidades
    private static final String[] NAMES = {
            "Bienvenida",
            "Reconocimiento de medicamentos",
            "Reconocimiento de billetes",
            "Reconocimiento de displays",
            "Gestionar medicamentos",
            "Gestionar principios activos",
            "Manual de usuario",
            "Acerca de Iris Lens"
    };

    // Lista de clases correspondientes a las funcionalidades
    public static final List<Class<?>> FUNCTIONALITIES = Arrays.asList(
            WelcomeActivity.class,
            MedicineRecognitionActivity.class,
            MoneyRecognitionActivity.class,
            DisplayRecognitionActivity.class,
            ManageMedicinesActivity.class,
            ManageActiveIngredientsActivity.class,
            ManualActivity.class,
            AboutActivity.class
    );

    /**
     * Devuelve el indice de la siguiente funcionalidad, de forma circular
     *
     * @param current Indice actual
     * @return Indice siguiente
     */
    public static int getNextIndex(int current) {
        return (current + 1) % FUNCTIONALITIES.size();
    }

    /**
     * Devuelve el indice de la funcionalidad anterior, de forma circular
     *
     * @param current Indice actual
     * @return Indice anterior
     */
    public static int getPreviousIndex(int current) {
        return (current - 1 + FUNCTIONALITIES.size()) % FUNCTIONALITIES.size();
    }

    /**
     * Lanza la actividad correspondiente al indice indicado
     *
     * @param context Contexto actual desde el cual se lanza
     * @param index Indice de la funcionalidad a lanzar
     */
    public static void launch(Context context, int index) {
        if (index < 0 || index >= FUNCTIONALITIES.size()) {
            Log.e(TAG, "Índice fuera de rango: " + index);
            return;
        }

        Class<?> activityClass = FUNCTIONALITIES.get(index);
        Log.d(TAG, "Lanzando actividad: " + activityClass.getSimpleName());

        try {
            Intent intent = new Intent(context, activityClass);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error al lanzar la actividad: " + activityClass.getSimpleName(), e);
        }
    }

    /**
     * Devuelve el nombre descriptivo de una funcionalidad segun su indice
     *
     * @param index Indice de la funcionalidad
     * @return Nombre descriptivo
     */
    public static String getName(int index) {
        if (index >= 0 && index < NAMES.length) {
            return NAMES[index];
        }
        return "Funcionalidad";
    }
}
