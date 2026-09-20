package com.example.irislens.medicine.view;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.irislens.R;
import com.example.irislens.common.BaseManagementActivity;
import com.example.irislens.common.Functionalities;
import com.example.irislens.medicine.model.MedicineRepository;
import com.example.irislens.medicine.model.PrincipioActivo;
import com.example.irislens.medicine.sync.MedicineSyncManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ManageActiveIngredientsActivity extends BaseManagementActivity implements ActiveIngredientAdapter.Listener {

    private MedicineRepository repository;
    private MedicineSyncManager syncManager;
    private ActiveIngredientAdapter adapter;
    private EditText etBuscar;

    private List<PrincipioActivo> listaCompleta = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentFunctionalityIndex = Functionalities.MANAGE_ACTIVE_INGREDIENTS;

        setContentView(R.layout.activity_manage_active_ingredients);
        setTitle(R.string.gestionar_principios_activos);

        repository = new MedicineRepository(this);
        syncManager = new MedicineSyncManager(this);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ActiveIngredientAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        FloatingActionButton fabAgregar = findViewById(R.id.fabAgregar);
        fabAgregar.setContentDescription(getString(R.string.agregar_principio_activo));
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
        // Igual que en ManageMedicinesActivity: sin llamada acá, solo en onResume().

        new Handler().postDelayed(() -> {
            android.widget.TextView tvTitle = findViewById(R.id.tvTitle);
            announce(tvTitle, getString(R.string.gestionar_principios_activos));
        }, 700);
    }

    @Override
    protected void onResume() {
        super.onResume();
        sincronizarYRecargar();
    }

    private void sincronizarYRecargar() {
        syncManager.sincronizarPrincipiosActivos((nuevos, actualizados, vinculados) -> {
            Toast.makeText(this,
                    "Principios activos — nuevos: " + nuevos +
                            ", actualizados: " + actualizados +
                            ", vinculados: " + vinculados,
                    Toast.LENGTH_SHORT).show();
            if (nuevos > 0 || actualizados > 0) {
                recargarLista();
            }
        });
    }

    private void recargarLista() {
        listaCompleta = repository.getAllPrincipiosActivos();
        aplicarFiltro(etBuscar == null ? "" : etBuscar.getText().toString());
    }

    private void aplicarFiltro(String query) {
        String filtro = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        if (filtro.isEmpty()) {
            adapter.actualizar(listaCompleta);
            return;
        }
        List<PrincipioActivo> filtrados = new ArrayList<>();
        for (PrincipioActivo p : listaCompleta) {
            if (p.getNombre() != null && p.getNombre().toLowerCase(Locale.getDefault()).contains(filtro)) {
                filtrados.add(p);
            }
        }
        adapter.actualizar(filtrados);
    }

    @Override
    public void onTocarItem(PrincipioActivo principioActivo) {
        announce(findViewById(R.id.recyclerView), principioActivo.getNombre());
    }

    private void anunciarYMostrarToast(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
        announce(findViewById(R.id.recyclerView), mensaje);
    }

    @Override
    public void onEditar(PrincipioActivo principioActivo) {
        mostrarDialogoEdicion(principioActivo);
    }

    @Override
    public void onEliminar(PrincipioActivo principioActivo) {
        String titulo = getString(R.string.eliminar_x, principioActivo.getNombre());

        int mensajeResId = principioActivo.isLocalPropio()
                ? R.string.confirmar_eliminar
                : R.string.confirmar_eliminar_definitivo;

        String mensaje = getString(mensajeResId, principioActivo.getNombre());
        announce(findViewById(R.id.recyclerView), mensaje);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensaje)
                .setPositiveButton(R.string.eliminar, (d, which) -> {
                    repository.eliminarPrincipioActivo(principioActivo.getId());
                    recargarLista();
                    anunciarYMostrarToast(
                            getString(
                                    R.string.principio_activo_eliminado_x,
                                    principioActivo.getNombre()
                            )
                    );
                })
                .setNegativeButton(R.string.cancelar, (d, which) ->
                        anunciarYMostrarToast(
                                getString(
                                        R.string.eliminacion_cancelada_x,
                                        principioActivo.getNombre()
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

    private void mostrarDialogoEdicion(PrincipioActivo existente) {
        String titulo = existente == null
                ? getString(R.string.agregar_principio_activo)
                : getString(R.string.editar_x, existente.getNombre());
        announce(findViewById(R.id.recyclerView), titulo);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        EditText etNombre = new EditText(this);
        etNombre.setHint(R.string.nombre);
        etNombre.setTextSize(18);
        etNombre.setContentDescription(getString(R.string.nombre_del_principio_activo));
        if (existente != null) {
            etNombre.setText(existente.getNombre());
        }
        layout.addView(etNombre);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setView(layout)
                .setPositiveButton(R.string.guardar, null)
                .setNegativeButton(R.string.cancelar, (d, which) -> {
                    String mensajeCancelacion = existente == null
                            ? getString(R.string.creacion_cancelada)
                            : getString(R.string.edicion_cancelada_x, existente.getNombre());
                    anunciarYMostrarToast(mensajeCancelacion);
                })
                .create();

        dialog.setOnShowListener(dialogInterface ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String nombre = etNombre.getText().toString().trim();
                    if (nombre.isEmpty()) {
                        anunciarYMostrarToast(getString(R.string.el_nombre_es_obligatorio));
                        return;
                    }
                    if (existente == null) {
                        repository.crearPrincipioActivoLocal(nombre);
                        anunciarYMostrarToast(getString(R.string.principio_activo_creado_x, nombre));
                    } else {
                        repository.editarPrincipioActivo(existente.getId(), nombre);
                        anunciarYMostrarToast(getString(R.string.principio_activo_actualizado_x, nombre));
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