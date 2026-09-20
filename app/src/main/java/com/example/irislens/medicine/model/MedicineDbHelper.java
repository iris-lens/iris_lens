package com.example.irislens.medicine.model;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MedicineDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "medicamentos_db";
    // v2: firestore_id
    // v3: modificado_local + eliminado_local
    // v4: es_semilla (vincula filas de assets con Firestore sin duplicar)
    private static final int DATABASE_VERSION = 4;
    private final Context mContext;

    public MedicineDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String SQL_CREATE_MEDICINE =
                "CREATE TABLE " + MedicineContract.MedicineEntry.TABLE_NAME + " (" +
                        MedicineContract.MedicineEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID + " TEXT UNIQUE, " +
                        MedicineContract.MedicineEntry.COLUMN_NAME + " TEXT NOT NULL, " +
                        MedicineContract.MedicineEntry.COLUMN_DESCRIPTION + " TEXT, " +
                        MedicineContract.MedicineEntry.COLUMN_MODIFICADO_LOCAL + " INTEGER NOT NULL DEFAULT 0, " +
                        MedicineContract.MedicineEntry.COLUMN_ELIMINADO_LOCAL + " INTEGER NOT NULL DEFAULT 0, " +
                        MedicineContract.MedicineEntry.COLUMN_ES_SEMILLA + " INTEGER NOT NULL DEFAULT 0);";

        String SQL_CREATE_DRUG =
                "CREATE TABLE " + MedicineContract.ActiveIngredient.TABLE_NAME + " (" +
                        MedicineContract.ActiveIngredient._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID + " TEXT UNIQUE, " +
                        MedicineContract.ActiveIngredient.COLUMN_NAME + " TEXT NOT NULL, " +
                        MedicineContract.ActiveIngredient.COLUMN_MODIFICADO_LOCAL + " INTEGER NOT NULL DEFAULT 0, " +
                        MedicineContract.ActiveIngredient.COLUMN_ELIMINADO_LOCAL + " INTEGER NOT NULL DEFAULT 0, " +
                        MedicineContract.ActiveIngredient.COLUMN_ES_SEMILLA + " INTEGER NOT NULL DEFAULT 0);";

        db.execSQL(SQL_CREATE_MEDICINE);
        db.execSQL(SQL_CREATE_DRUG);

        seedFromAssets(db, "medicamentos.json");
        seedFromAssets(db, "principios_activos.json");
    }

    private void seedFromAssets(SQLiteDatabase db, String assetName) {
        boolean isMedicine = assetName.equals("medicamentos.json");
        try {
            AssetManager assetManager = mContext.getAssets();
            InputStream is = assetManager.open(assetName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) jsonBuilder.append(line);
            reader.close();
            is.close();

            JSONArray array = new JSONArray(jsonBuilder.toString());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String nombre = item.getString("nombre");
                // Si el JSON trae firestore_id, la fila nace ya vinculada
                // (es_semilla=0). Si no lo trae, nace como semilla suelta
                // (es_semilla=1) para que el sync la vincule por nombre.
                String firestoreId = item.has("firestore_id") ? item.optString("firestore_id", null) : null;
                boolean vinculada = firestoreId != null;

                if (isMedicine) {
                    String descripcion = item.optString("descripcion", "");
                    db.execSQL("INSERT INTO " + MedicineContract.MedicineEntry.TABLE_NAME +
                                    " (" + MedicineContract.MedicineEntry.COLUMN_NAME + ", " +
                                    MedicineContract.MedicineEntry.COLUMN_DESCRIPTION + ", " +
                                    MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID + ", " +
                                    MedicineContract.MedicineEntry.COLUMN_ES_SEMILLA + ") VALUES (?, ?, ?, ?)",
                            new Object[]{nombre, descripcion, firestoreId, vinculada ? 0 : 1});
                } else {
                    db.execSQL("INSERT INTO " + MedicineContract.ActiveIngredient.TABLE_NAME +
                                    " (" + MedicineContract.ActiveIngredient.COLUMN_NAME + ", " +
                                    MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID + ", " +
                                    MedicineContract.ActiveIngredient.COLUMN_ES_SEMILLA + ") VALUES (?, ?, ?)",
                            new Object[]{nombre, firestoreId, vinculada ? 0 : 1});
                }
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // IMPORTANTE: nunca DROP TABLE. Los medicamentos/principios activos
        // creados, editados o borrados por el usuario deben sobrevivir a las
        // actualizaciones de la app.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + MedicineContract.MedicineEntry.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID + " TEXT");
            db.execSQL("ALTER TABLE " + MedicineContract.ActiveIngredient.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + MedicineContract.MedicineEntry.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.MedicineEntry.COLUMN_MODIFICADO_LOCAL + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + MedicineContract.MedicineEntry.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.MedicineEntry.COLUMN_ELIMINADO_LOCAL + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + MedicineContract.ActiveIngredient.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.ActiveIngredient.COLUMN_MODIFICADO_LOCAL + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + MedicineContract.ActiveIngredient.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.ActiveIngredient.COLUMN_ELIMINADO_LOCAL + " INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + MedicineContract.MedicineEntry.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.MedicineEntry.COLUMN_ES_SEMILLA + " INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + MedicineContract.ActiveIngredient.TABLE_NAME +
                    " ADD COLUMN " + MedicineContract.ActiveIngredient.COLUMN_ES_SEMILLA + " INTEGER NOT NULL DEFAULT 0");

            // Migración única: limpia duplicados que pudo haber dejado el
            // viejo sync (CONFLICT_REPLACE + matching por nombre inexistente).
            limpiarDuplicadosDeSemilla(db, MedicineContract.MedicineEntry.TABLE_NAME,
                    MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID,
                    MedicineContract.MedicineEntry.COLUMN_NAME,
                    MedicineContract.MedicineEntry.COLUMN_MODIFICADO_LOCAL,
                    MedicineContract.MedicineEntry.COLUMN_ES_SEMILLA);
            limpiarDuplicadosDeSemilla(db, MedicineContract.ActiveIngredient.TABLE_NAME,
                    MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID,
                    MedicineContract.ActiveIngredient.COLUMN_NAME,
                    MedicineContract.ActiveIngredient.COLUMN_MODIFICADO_LOCAL,
                    MedicineContract.ActiveIngredient.COLUMN_ES_SEMILLA);
        }
    }

    private void limpiarDuplicadosDeSemilla(SQLiteDatabase db, String tabla, String colFirestoreId,
                                            String colNombre, String colModificadoLocal, String colEsSemilla) {
        // Fila huérfana sin cambios del usuario: es basura duplicada, se borra.
        db.execSQL("DELETE FROM " + tabla + " WHERE " +
                colFirestoreId + " IS NULL AND " + colModificadoLocal + "=0 AND " +
                colNombre + " COLLATE NOCASE IN (SELECT " + colNombre + " FROM " + tabla +
                " WHERE " + colFirestoreId + " IS NOT NULL)");

        // Fila huérfana pero editada por el usuario: no se borra, se marca
        // como semilla para que el próximo sync la vincule.
        db.execSQL("UPDATE " + tabla + " SET " + colEsSemilla + "=1 WHERE " +
                colFirestoreId + " IS NULL AND " +
                colNombre + " COLLATE NOCASE IN (SELECT " + colNombre + " FROM " + tabla +
                " WHERE " + colFirestoreId + " IS NOT NULL)");
    }
}