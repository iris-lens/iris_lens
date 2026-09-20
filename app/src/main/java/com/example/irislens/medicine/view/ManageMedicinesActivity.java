package com.example.irislens.medicine.view;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.irislens.R;
import com.example.irislens.common.BaseManagementActivity;
import com.example.irislens.common.Functionalities;
import com.example.irislens.medicine.model.Medicamento;
import com.example.irislens.medicine.model.MedicineRepository;
import com.example.irislens.medicine.sync.MedicineSyncManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import androidx.core.content.ContextCompat;

public class ManageMedicinesActivity extends BaseManagementActivity implements MedicineAdapter.Listener {

    private MedicineRepository repository;
    private MedicineSyncManager syncManager;
    private MedicineAdapter adapter;
    private EditText etBuscar;

    private List<Medicamento> listaCompleta = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentFunctionalityIndex = Functionalities.MANAGE_MEDICINES;

        setContentView(R.layout.activity_manage_medicines);
        setTitle(R.string.gestionar_medicamentos);

        repository = new MedicineRepository(this);
        syncManager = new MedicineSyncManager(this);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MedicineAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fabAgregar = findViewById(R.id.fabAgregar);
        fabAgregar.setContentDescription(getString(R.string.agregar_medicamento));
        fabAgregar.setOnClickListener(v -> mostrarDialogoEdicion(null));

        etBuscar = findViewById(R.id.etBuscar);
        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                aplicarFiltro(s.toString());
            }
        });

        recargarLista();
        // OJO: acá NO se llama a sincronizarYRecargar(). onResume() se ejecuta
        // automáticamente después de onCreate() en cada apertura de pantalla,
        // así que alcanza con dejarlo ahí — si también se llamaba acá, corría
        // dos veces seguidas y por eso el Toast salía duplicado.

        new Handler().postDelayed(() -> {
            android.widget.TextView tvTitle = findViewById(R.id.tvTitle);
            announce(tvTitle, getString(R.string.gestionar_medicamentos));
        }, 700);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sincronizarYRecargar();
    }

    private void sincronizarYRecargar() {
        syncManager.sincronizarMedicamentos((nuevos, actualizados, vinculados) -> {
            Toast.makeText(this,
                    "Medicamentos — nuevos: " + nuevos +
                            ", actualizados: " + actualizados +
                            ", vinculados: " + vinculados,
                    Toast.LENGTH_SHORT).show();
            if (nuevos > 0 || actualizados > 0) {
                recargarLista();
            }
        });
    }

    private void recargarLista() {
        listaCompleta = repository.getAllMedicamentos();
        aplicarFiltro(etBuscar == null ? "" : etBuscar.getText().toString());
    }

    private void aplicarFiltro(String query) {
        String filtro = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (filtro.isEmpty()) {
            adapter.actualizar(listaCompleta);
            return;
        }
        List<Medicamento> filtrados = new ArrayList<>();
        for (Medicamento m : listaCompleta) {
            boolean coincideNombre = m.getNombre() != null && m.getNombre().toLowerCase(Locale.getDefault()).contains(filtro);
            boolean coincideDescripcion = m.getDescripcion() != null && m.getDescripcion().toLowerCase(Locale.getDefault()).contains(filtro);
            if (coincideNombre || coincideDescripcion) {
                filtrados.add(m);
            }
        }
        adapter.actualizar(filtrados);
    }

    @Override
    public void onTocarItem(Medicamento medicamento) {
        String descripcion = medicamento.getDescripcion() == null ? "" : medicamento.getDescripcion();
        String texto = descripcion.isEmpty() ? medicamento.getNombre() : medicamento.getNombre() + ". " + descripcion;
        announce(findViewById(R.id.recyclerView), texto);
    }

    private void anunciarYMostrarToast(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
        announce(findViewById(R.id.recyclerView), mensaje);
    }

    @Override
    public void onEditar(Medicamento medicamento) {
        mostrarDialogoEdicion(medicamento);
    }

    @Override
    public void onEliminar(Medicamento medicamento) {
        String titulo = getString(R.string.eliminar_x, medicamento.getNombre());

        int mensajeResId = medicamento.isLocalPropio()
                ? R.string.confirmar_eliminar
                : R.string.confirmar_eliminar_definitivo;

        String mensaje = getString(mensajeResId, medicamento.getNombre());
        announce(findViewById(R.id.recyclerView), mensaje);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton(R.string.eliminar, (d, which) -> {
                    repository.eliminarMedicamento(medicamento.getId());
                    recargarLista();
                    anunciarYMostrarToast(
                            getString(
                                    R.string.medicamento_eliminado_x,
                                    medicamento.getNombre()
                            )
                    );
                })
                .setNegativeButton(R.string.cancelar, (d, which) ->
                        anunciarYMostrarToast(
                                getString(
                                        R.string.eliminacion_cancelada_x,
                                        medicamento.getNombre()
                                )
                        ))
                .create();

        dialog.show();

        // Texto blanco
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.white));

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.white));

        // Fondo azul
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setBackgroundColor(ContextCompat.getColor(this, R.color.iris_blue));

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setBackgroundColor(ContextCompat.getColor(this, R.color.iris_blue));

        // Separación entre los botones
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) dialog.getButton(AlertDialog.BUTTON_POSITIVE).getLayoutParams();

        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setLayoutParams(params);
    }

    private void mostrarDialogoEdicion(Medicamento medicamentoExistente) {
        String titulo = medicamentoExistente == null
                ? getString(R.string.agregar_medicamento)
                : getString(R.string.editar_x, medicamentoExistente.getNombre());
        announce(findViewById(R.id.recyclerView), titulo);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        EditText etNombre = new EditText(this);
        etNombre.setHint(R.string.nombre);
        etNombre.setContentDescription(getString(R.string.nombre_del_medicamento));

        EditText etDescripcion = new EditText(this);
        etDescripcion.setHint(R.string.descripcion);
        etDescripcion.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etDescripcion.setContentDescription(getString(R.string.descripcion_del_medicamento));

        if (medicamentoExistente != null) {
            etNombre.setText(medicamentoExistente.getNombre());
            etDescripcion.setText(medicamentoExistente.getDescripcion());
        }

        layout.addView(etNombre);
        layout.addView(etDescripcion);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(layout)
                .setPositiveButton(R.string.guardar, null)
                .setNegativeButton(R.string.cancelar, (d, which) -> {
                    String mensajeCancelacion = medicamentoExistente == null
                            ? getString(R.string.creacion_cancelada)
                            : getString(R.string.edicion_cancelada_x, medicamentoExistente.getNombre());
                    anunciarYMostrarToast(mensajeCancelacion);
                })
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String nombre = etNombre.getText().toString().trim();
                    String descripcion = etDescripcion.getText().toString().trim();

                    if (nombre.isEmpty()) {
                        anunciarYMostrarToast(getString(R.string.el_nombre_es_obligatorio));
                        return;
                    }
                    if (descripcion.isEmpty()) {
                        anunciarYMostrarToast(getString(R.string.la_descripcion_es_obligatoria));
                        return;
                    }

                    if (medicamentoExistente == null) {
                        repository.crearMedicamentoLocal(nombre, descripcion);
                        anunciarYMostrarToast(getString(R.string.medicamento_creado_x, nombre));
                    } else {
                        repository.editarMedicamento(medicamentoExistente.getId(), nombre, descripcion);
                        anunciarYMostrarToast(getString(R.string.medicamento_actualizado_x, nombre));
                    }
                    recargarLista();
                    dialog.dismiss();
                }));

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.white));

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(ContextCompat.getColor(this, R.color.white));

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setBackgroundColor(ContextCompat.getColor(this, R.color.iris_blue));

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setBackgroundColor(ContextCompat.getColor(this, R.color.iris_blue));

        // Separación entre los botones
        ViewGroup.MarginLayoutParams params =
                (ViewGroup.MarginLayoutParams) dialog.getButton(AlertDialog.BUTTON_POSITIVE).getLayoutParams();

        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        params.leftMargin = margin;

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setLayoutParams(params);
    }
}