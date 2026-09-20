package com.example.irislens.medicine.model;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Punto único de acceso a la base local de medicamentos y principios activos.
 * Usada tanto por las pantallas de gestión (CRUD manual del usuario) como
 * por {@link com.example.irislens.medicine.sync.MedicineSyncManager}.
 *
 * Reglas de borrado:
 * - Registro propio del usuario (firestore_id null): se borra físicamente.
 * - Registro que vino de remoto: se marca eliminado_local=1.
 *
 * Reglas de vinculación (es_semilla):
 * - Filas sembradas desde assets sin firestore_id nacen con es_semilla=1.
 *   Cuando el sync encuentra un documento remoto con el mismo nombre, las
 *   "adopta" (les asigna firestore_id, es_semilla=0) en vez de duplicar.
 */
public class MedicineRepository {

    private final MedicineDbHelper dbHelper;

    public MedicineRepository(Context context) {
        dbHelper = new MedicineDbHelper(context);
    }

    // ---------------------------------------------------------------
    // MEDICAMENTOS - CRUD para la pantalla de gestión
    // ---------------------------------------------------------------

    public List<Medicamento> getAllMedicamentos() {
        List<Medicamento> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(MedicineContract.MedicineEntry.TABLE_NAME,
                null, MedicineContract.MedicineEntry.COLUMN_ELIMINADO_LOCAL + "=0", null,
                null, null, MedicineContract.MedicineEntry.COLUMN_NAME + " COLLATE NOCASE ASC");
        while (c.moveToNext()) {
            result.add(medicamentoFromCursor(c));
        }
        c.close();
        return result;
    }

    public long crearMedicamentoLocal(String nombre, String descripcion) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.MedicineEntry.COLUMN_NAME, nombre);
        cv.put(MedicineContract.MedicineEntry.COLUMN_DESCRIPTION, descripcion);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insert(MedicineContract.MedicineEntry.TABLE_NAME, null, cv);
    }

    public void editarMedicamento(long id, String nombre, String descripcion) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.MedicineEntry.COLUMN_NAME, nombre);
        cv.put(MedicineContract.MedicineEntry.COLUMN_DESCRIPTION, descripcion);
        cv.put(MedicineContract.MedicineEntry.COLUMN_MODIFICADO_LOCAL, 1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(MedicineContract.MedicineEntry.TABLE_NAME, cv,
                MedicineContract.MedicineEntry._ID + "=?", new String[]{String.valueOf(id)});
    }

    public void eliminarMedicamento(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(MedicineContract.MedicineEntry.TABLE_NAME, null,
                MedicineContract.MedicineEntry._ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        boolean esLocalPropio = true;
        if (c.moveToFirst()) {
            esLocalPropio = c.getString(c.getColumnIndexOrThrow(MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID)) == null;
        }
        c.close();

        if (esLocalPropio) {
            db.delete(MedicineContract.MedicineEntry.TABLE_NAME,
                    MedicineContract.MedicineEntry._ID + "=?", new String[]{String.valueOf(id)});
        } else {
            ContentValues cv = new ContentValues();
            cv.put(MedicineContract.MedicineEntry.COLUMN_ELIMINADO_LOCAL, 1);
            db.update(MedicineContract.MedicineEntry.TABLE_NAME, cv,
                    MedicineContract.MedicineEntry._ID + "=?", new String[]{String.valueOf(id)});
        }
    }

    private Medicamento medicamentoFromCursor(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(MedicineContract.MedicineEntry._ID));
        String firestoreId = c.getString(c.getColumnIndexOrThrow(MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID));
        String nombre = c.getString(c.getColumnIndexOrThrow(MedicineContract.MedicineEntry.COLUMN_NAME));
        String descripcion = c.getString(c.getColumnIndexOrThrow(MedicineContract.MedicineEntry.COLUMN_DESCRIPTION));
        boolean modificadoLocal = c.getInt(c.getColumnIndexOrThrow(MedicineContract.MedicineEntry.COLUMN_MODIFICADO_LOCAL)) == 1;
        boolean eliminadoLocal = c.getInt(c.getColumnIndexOrThrow(MedicineContract.MedicineEntry.COLUMN_ELIMINADO_LOCAL)) == 1;
        return new Medicamento(id, firestoreId, nombre, descripcion, modificadoLocal, eliminadoLocal);
    }

    // ---------------------------------------------------------------
    // PRINCIPIOS ACTIVOS - CRUD para la pantalla de gestión
    // ---------------------------------------------------------------

    public List<PrincipioActivo> getAllPrincipiosActivos() {
        List<PrincipioActivo> result = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(MedicineContract.ActiveIngredient.TABLE_NAME,
                null, MedicineContract.ActiveIngredient.COLUMN_ELIMINADO_LOCAL + "=0", null,
                null, null, MedicineContract.ActiveIngredient.COLUMN_NAME + " COLLATE NOCASE ASC");
        while (c.moveToNext()) {
            result.add(principioActivoFromCursor(c));
        }
        c.close();
        return result;
    }

    public long crearPrincipioActivoLocal(String nombre) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.ActiveIngredient.COLUMN_NAME, nombre);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.insert(MedicineContract.ActiveIngredient.TABLE_NAME, null, cv);
    }

    public void editarPrincipioActivo(long id, String nombre) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.ActiveIngredient.COLUMN_NAME, nombre);
        cv.put(MedicineContract.ActiveIngredient.COLUMN_MODIFICADO_LOCAL, 1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(MedicineContract.ActiveIngredient.TABLE_NAME, cv,
                MedicineContract.ActiveIngredient._ID + "=?", new String[]{String.valueOf(id)});
    }

    public void eliminarPrincipioActivo(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor c = db.query(MedicineContract.ActiveIngredient.TABLE_NAME, null,
                MedicineContract.ActiveIngredient._ID + "=?", new String[]{String.valueOf(id)},
                null, null, null);
        boolean esLocalPropio = true;
        if (c.moveToFirst()) {
            esLocalPropio = c.getString(c.getColumnIndexOrThrow(MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID)) == null;
        }
        c.close();

        if (esLocalPropio) {
            db.delete(MedicineContract.ActiveIngredient.TABLE_NAME,
                    MedicineContract.ActiveIngredient._ID + "=?", new String[]{String.valueOf(id)});
        } else {
            ContentValues cv = new ContentValues();
            cv.put(MedicineContract.ActiveIngredient.COLUMN_ELIMINADO_LOCAL, 1);
            db.update(MedicineContract.ActiveIngredient.TABLE_NAME, cv,
                    MedicineContract.ActiveIngredient._ID + "=?", new String[]{String.valueOf(id)});
        }
    }

    private PrincipioActivo principioActivoFromCursor(Cursor c) {
        long id = c.getLong(c.getColumnIndexOrThrow(MedicineContract.ActiveIngredient._ID));
        String firestoreId = c.getString(c.getColumnIndexOrThrow(MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID));
        String nombre = c.getString(c.getColumnIndexOrThrow(MedicineContract.ActiveIngredient.COLUMN_NAME));
        boolean modificadoLocal = c.getInt(c.getColumnIndexOrThrow(MedicineContract.ActiveIngredient.COLUMN_MODIFICADO_LOCAL)) == 1;
        boolean eliminadoLocal = c.getInt(c.getColumnIndexOrThrow(MedicineContract.ActiveIngredient.COLUMN_ELIMINADO_LOCAL)) == 1;
        return new PrincipioActivo(id, firestoreId, nombre, modificadoLocal, eliminadoLocal);
    }

    // ---------------------------------------------------------------
    // Métodos usados por MedicineSyncManager para el merge remoto->local.
    // ---------------------------------------------------------------

    public Medicamento buscarMedicamentoPorFirestoreId(String firestoreId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(MedicineContract.MedicineEntry.TABLE_NAME, null,
                MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID + "=?", new String[]{firestoreId},
                null, null, null);
        Medicamento result = null;
        if (c.moveToFirst()) result = medicamentoFromCursor(c);
        c.close();
        return result;
    }

    public Medicamento buscarMedicamentoSemillaPorNombre(String nombre) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(MedicineContract.MedicineEntry.TABLE_NAME, null,
                MedicineContract.MedicineEntry.COLUMN_ES_SEMILLA + "=1 AND " +
                        MedicineContract.MedicineEntry.COLUMN_NAME + "=? COLLATE NOCASE",
                new String[]{nombre}, null, null, null);
        Medicamento result = null;
        if (c.moveToFirst()) result = medicamentoFromCursor(c);
        c.close();
        return result;
    }

    public void insertarMedicamentoDesdeRemoto(String firestoreId, String nombre, String descripcion) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID, firestoreId);
        cv.put(MedicineContract.MedicineEntry.COLUMN_NAME, nombre);
        cv.put(MedicineContract.MedicineEntry.COLUMN_DESCRIPTION, descripcion);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.insert(MedicineContract.MedicineEntry.TABLE_NAME, null, cv);
    }

    public void actualizarMedicamentoDesdeRemoto(long id, String nombre, String descripcion) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.MedicineEntry.COLUMN_NAME, nombre);
        cv.put(MedicineContract.MedicineEntry.COLUMN_DESCRIPTION, descripcion);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(MedicineContract.MedicineEntry.TABLE_NAME, cv,
                MedicineContract.MedicineEntry._ID + "=?", new String[]{String.valueOf(id)});
    }

    public void vincularMedicamentoConRemoto(long id, String firestoreId, String nombre,
                                             String descripcion, boolean modificadoLocal) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.MedicineEntry.COLUMN_FIRESTORE_ID, firestoreId);
        cv.put(MedicineContract.MedicineEntry.COLUMN_ES_SEMILLA, 0);
        if (!modificadoLocal) {
            cv.put(MedicineContract.MedicineEntry.COLUMN_NAME, nombre);
            cv.put(MedicineContract.MedicineEntry.COLUMN_DESCRIPTION, descripcion);
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(MedicineContract.MedicineEntry.TABLE_NAME, cv,
                MedicineContract.MedicineEntry._ID + "=?", new String[]{String.valueOf(id)});
    }

    public PrincipioActivo buscarPrincipioActivoPorFirestoreId(String firestoreId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(MedicineContract.ActiveIngredient.TABLE_NAME, null,
                MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID + "=?", new String[]{firestoreId},
                null, null, null);
        PrincipioActivo result = null;
        if (c.moveToFirst()) result = principioActivoFromCursor(c);
        c.close();
        return result;
    }

    public PrincipioActivo buscarPrincipioActivoSemillaPorNombre(String nombre) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(MedicineContract.ActiveIngredient.TABLE_NAME, null,
                MedicineContract.ActiveIngredient.COLUMN_ES_SEMILLA + "=1 AND " +
                        MedicineContract.ActiveIngredient.COLUMN_NAME + "=? COLLATE NOCASE",
                new String[]{nombre}, null, null, null);
        PrincipioActivo result = null;
        if (c.moveToFirst()) result = principioActivoFromCursor(c);
        c.close();
        return result;
    }

    public void insertarPrincipioActivoDesdeRemoto(String firestoreId, String nombre) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID, firestoreId);
        cv.put(MedicineContract.ActiveIngredient.COLUMN_NAME, nombre);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.insert(MedicineContract.ActiveIngredient.TABLE_NAME, null, cv);
    }

    public void actualizarPrincipioActivoDesdeRemoto(long id, String nombre) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.ActiveIngredient.COLUMN_NAME, nombre);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(MedicineContract.ActiveIngredient.TABLE_NAME, cv,
                MedicineContract.ActiveIngredient._ID + "=?", new String[]{String.valueOf(id)});
    }

    public void vincularPrincipioActivoConRemoto(long id, String firestoreId, String nombre, boolean modificadoLocal) {
        ContentValues cv = new ContentValues();
        cv.put(MedicineContract.ActiveIngredient.COLUMN_FIRESTORE_ID, firestoreId);
        cv.put(MedicineContract.ActiveIngredient.COLUMN_ES_SEMILLA, 0);
        if (!modificadoLocal) {
            cv.put(MedicineContract.ActiveIngredient.COLUMN_NAME, nombre);
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.update(MedicineContract.ActiveIngredient.TABLE_NAME, cv,
                MedicineContract.ActiveIngredient._ID + "=?", new String[]{String.valueOf(id)});
    }
}