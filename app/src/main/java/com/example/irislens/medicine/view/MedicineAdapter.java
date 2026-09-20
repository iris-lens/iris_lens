package com.example.irislens.medicine.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.irislens.R;
import com.example.irislens.medicine.model.Medicamento;

import java.util.List;

public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.ViewHolder> {

    public interface Listener {
        void onEditar(Medicamento medicamento);
        void onEliminar(Medicamento medicamento);
        /** Se toca el item (fuera de los botones): hay que leer título + descripción juntos. */
        void onTocarItem(Medicamento medicamento);
    }

    private List<Medicamento> items;
    private final Listener listener;

    public MedicineAdapter(List<Medicamento> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void actualizar(List<Medicamento> nuevos) {
        this.items = nuevos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medicine, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Medicamento m = items.get(position);
        String descripcion = m.getDescripcion() == null ? "" : m.getDescripcion();

        holder.tvNombre.setText(m.getNombre());
        holder.tvDescripcion.setText(descripcion);

        // Título + descripción como un solo texto: es lo que arma que, al
        // tocar o al recorrer con TalkBack, se lea todo el item junto (y no
        // el título solo o la descripción sola por separado, como pasaba
        // antes).
        String textoCompleto = descripcion.isEmpty()
                ? m.getNombre()
                : m.getNombre() + ". " + descripcion;
        holder.itemContent.setContentDescription(textoCompleto);
        holder.itemContent.setOnClickListener(v -> listener.onTocarItem(m));

        holder.btnEditar.setContentDescription(
                holder.btnEditar.getContext().getString(R.string.editar) + " " + m.getNombre());
        holder.btnEliminar.setContentDescription(
                holder.btnEliminar.getContext().getString(R.string.eliminar) + " " + m.getNombre());

        holder.btnEditar.setOnClickListener(v -> listener.onEditar(m));
        holder.btnEliminar.setOnClickListener(v -> listener.onEliminar(m));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View itemContent;
        TextView tvNombre, tvDescripcion;
        ImageButton btnEditar, btnEliminar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemContent = itemView.findViewById(R.id.itemContent);
            tvNombre = itemView.findViewById(R.id.tvNombre);
            tvDescripcion = itemView.findViewById(R.id.tvDescripcion);
            btnEditar = itemView.findViewById(R.id.btnEditar);
            btnEliminar = itemView.findViewById(R.id.btnEliminar);
        }
    }
}