package com.example.irislens.medicine.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.irislens.medicine.model.Medicamento;
import com.example.irislens.medicine.model.MedicineRepository;
import com.example.irislens.medicine.model.PrincipioActivo;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Único punto de sincronización remoto->local de todo el proyecto.
 * Cualquier pantalla que sincronice debe pasar por acá — nunca escribir SQL
 * de sync directo, para que no haya dos lógicas de merge pisándose.
 *
 * - Reconocimiento de medicamentos → sincronizar(listener) (ambas colecciones).
 * - Gestión de medicamentos → sincronizarMedicamentos(listener).
 * - Gestión de principios activos → sincronizarPrincipiosActivos(listener).
 *
 * Reglas de merge (medicamentos y principios activos por igual):
 * 1) Remoto nuevo, sin fila de fábrica con ese nombre → se descarga a local.
 * 1b) Remoto nuevo, con fila de fábrica (es_semilla=1) de mismo nombre → se
 *     vincula esa fila en vez de duplicar.
 * 2) Remoto editado, sin cambios locales → se actualiza en local.
 * 3) Remoto editado, con cambios locales (modificado_local=1) → gana lo local.
 * 4) Remoto borrado → NO se borra nada en local automáticamente. El único
 *    borrado válido es el que pide el usuario desde el gestor (eliminado_local=1).
 * 5) Local creado por el usuario → nunca se sube ni se toca.
 */
public class MedicineSyncManager {

    private static final String TAG = "MedicineSyncManager";

    public interface SyncListener {
        void onSincronizado(int nuevos, int actualizados, int vinculados);
    }

    private final MedicineRepository repository;
    private final FirebaseFirestore firestore;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public MedicineSyncManager(Context context) {
        this.repository = new MedicineRepository(context);
        this.firestore = FirebaseFirestore.getInstance();
    }

    /** Sincroniza medicamentos Y principios activos (Reconocimiento). */
    public void sincronizar(SyncListener listener) {
        AtomicInteger nuevos = new AtomicInteger(0);
        AtomicInteger actualizados = new AtomicInteger(0);
        AtomicInteger vinculados = new AtomicInteger(0);
        AtomicInteger pendientes = new AtomicInteger(2);

        SyncListener acumulador = (n, a, v) -> {
            nuevos.addAndGet(n);
            actualizados.addAndGet(a);
            vinculados.addAndGet(v);
            if (pendientes.decrementAndGet() == 0 && listener != null) {
                listener.onSincronizado(nuevos.get(), actualizados.get(), vinculados.get());
            }
        };

        sincronizarMedicamentos(acumulador);
        sincronizarPrincipiosActivos(acumulador);
    }

    public void sincronizar() {
        sincronizar(null);
    }

    /** Sincroniza solo medicamentos (Gestión de medicamentos). */
    public void sincronizarMedicamentos(SyncListener listener) {
        firestore.collection("medicamentos").get()
                .addOnSuccessListener(snapshot -> {
                    int nuevos = 0, actualizados = 0, vinculados = 0;

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String firestoreId = doc.getId();
                        String nombre = doc.getString("nombre");
                        String descripcion = doc.getString("descripcion");
                        if (nombre == null) continue;

                        Medicamento existente = repository.buscarMedicamentoPorFirestoreId(firestoreId);
                        if (existente == null) {
                            Medicamento semilla = repository.buscarMedicamentoSemillaPorNombre(nombre);
                            if (semilla != null) {
                                repository.vincularMedicamentoConRemoto(semilla.getId(), firestoreId,
                                        nombre, descripcion, semilla.isModificadoLocal());
                                vinculados++;
                            } else {
                                repository.insertarMedicamentoDesdeRemoto(firestoreId, nombre, descripcion);
                                nuevos++;
                            }
                        } else if (existente.isEliminadoLocal()) {
                            // No se reinserta.
                        } else if (!existente.isModificadoLocal()) {
                            boolean cambio = !nombre.equals(existente.getNombre())
                                    || !safeEquals(descripcion, existente.getDescripcion());
                            if (cambio) {
                                repository.actualizarMedicamentoDesdeRemoto(existente.getId(), nombre, descripcion);
                                actualizados++;
                            }
                        }
                        // Si modificadoLocal == true: gana lo local.
                    }

                    if (listener != null) {
                        int fN = nuevos, fA = actualizados, fV = vinculados;
                        mainHandler.post(() -> listener.onSincronizado(fN, fA, fV));
                    }
                })
                .addOnFailureListener(e ->
                        Log.d(TAG, "Sin conexión, se omite sincronización de medicamentos: " + e.getMessage()));
    }

    /** Sincroniza solo principios activos (Gestión de principios activos). */
    public void sincronizarPrincipiosActivos(SyncListener listener) {
        firestore.collection("principios_activos").get()
                .addOnSuccessListener(snapshot -> {
                    int nuevos = 0, actualizados = 0, vinculados = 0;

                    for (QueryDocumentSnapshot doc : snapshot) {
                        String firestoreId = doc.getId();
                        String nombre = doc.getString("nombre");
                        if (nombre == null) continue;

                        PrincipioActivo existente = repository.buscarPrincipioActivoPorFirestoreId(firestoreId);
                        if (existente == null) {
                            PrincipioActivo semilla = repository.buscarPrincipioActivoSemillaPorNombre(nombre);
                            if (semilla != null) {
                                repository.vincularPrincipioActivoConRemoto(semilla.getId(), firestoreId,
                                        nombre, semilla.isModificadoLocal());
                                vinculados++;
                            } else {
                                repository.insertarPrincipioActivoDesdeRemoto(firestoreId, nombre);
                                nuevos++;
                            }
                        } else if (existente.isEliminadoLocal()) {
                            // No se reinserta.
                        } else if (!existente.isModificadoLocal()) {
                            if (!nombre.equals(existente.getNombre())) {
                                repository.actualizarPrincipioActivoDesdeRemoto(existente.getId(), nombre);
                                actualizados++;
                            }
                        }
                    }

                    if (listener != null) {
                        int fN = nuevos, fA = actualizados, fV = vinculados;
                        mainHandler.post(() -> listener.onSincronizado(fN, fA, fV));
                    }
                })
                .addOnFailureListener(e ->
                        Log.d(TAG, "Sin conexión, se omite sincronización de principios activos: " + e.getMessage()));
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}