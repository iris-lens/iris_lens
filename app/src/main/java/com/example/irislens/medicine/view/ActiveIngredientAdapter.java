package com.example.irislens.medicine.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.irislens.R;
import com.example.irislens.medicine.model.PrincipioActivo;

import java.util.List;

public class ActiveIngredientAdapter extends RecyclerView.Adapter<ActiveIngredientAdapter.ViewHolder> {

    public interface Listener {
        void onEditar(PrincipioActivo principioActivo);
        void onEliminar(PrincipioActivo principioActivo);
        void onTocarItem(PrincipioActivo principioActivo);
    }

    private List<PrincipioActivo> items;
    private final Listener listener;

    public ActiveIngredientAdapter(List<PrincipioActivo> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void actualizar(List<PrincipioActivo> nuevos) {
        this.items = nuevos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_active_ingredient, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PrincipioActivo p = items.get(position);
        holder.tvNombre.setText(p.getNombre());
        holder.tvNombre.setContentDescription(p.getNombre());
        holder.tvNombre.setOnClickListener(v -> listener.onTocarItem(p));

        holder.btnEditar.setContentDescription(
                holder.btnEditar.getContext().getString(R.string.editar) + " " + p.getNombre());
        holder.btnEliminar.setContentDescription(
                holder.btnEliminar.getContext().getString(R.string.eliminar) + " " + p.getNombre());

        holder.btnEditar.setOnClickListener(v -> listener.onEditar(p));
        holder.btnEliminar.setOnClickListener(v -> listener.onEliminar(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre;
        ImageButton btnEditar, btnEliminar;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvNombre);
            btnEditar = itemView.findViewById(R.id.btnEditar);
            btnEliminar = itemView.findViewById(R.id.btnEliminar);
        }
    }
}