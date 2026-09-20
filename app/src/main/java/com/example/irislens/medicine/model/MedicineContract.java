package com.example.irislens.medicine.model;

import android.provider.BaseColumns;

/**
 * Clase que define la estructura de la base de datos de medicamentos.
 */
public final class MedicineContract {
    private MedicineContract() {}

    public static class MedicineEntry implements BaseColumns {
        public static final String TABLE_NAME = "medicamento";
        public static final String COLUMN_NAME = "nombre";
        public static final String COLUMN_DESCRIPTION = "descripcion";
        public static final String COLUMN_FIRESTORE_ID = "firestore_id";
        // 1 si el registro fue editado localmente por el usuario y no debe
        // ser sobreescrito por el contenido remoto en el próximo sync.
        public static final String COLUMN_MODIFICADO_LOCAL = "modificado_local";
        // 1 si el usuario borró este registro (que venía de remoto) de forma
        // definitiva. La fila NO se borra físicamente: se guarda como
        // "cerrado" para que el sync sepa que nunca más debe volver a bajarlo.
        public static final String COLUMN_ELIMINADO_LOCAL = "eliminado_local";
        // 1 si la fila proviene del archivo medicamentos.json.
        // Se utiliza durante la sincronización para vincularla con su
        // correspondiente registro remoto y evitar duplicados.
        public static final String COLUMN_ES_SEMILLA = "es_semilla";
    }

    public static class ActiveIngredient implements BaseColumns {
        public static final String TABLE_NAME = "principio_activo";
        public static final String COLUMN_NAME = "nombre";
        public static final String COLUMN_FIRESTORE_ID = "firestore_id";
        public static final String COLUMN_MODIFICADO_LOCAL = "modificado_local";
        public static final String COLUMN_ELIMINADO_LOCAL = "eliminado_local";
        public static final String COLUMN_ES_SEMILLA = "es_semilla";
    }
}