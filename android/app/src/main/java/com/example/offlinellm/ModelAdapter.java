package com.example.offlinellm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import java.util.List;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
    private final List<ModelManager.ModelInfo> models;
    private final ModelManager manager;
    private final OnModelActionListener listener;

    public interface OnModelActionListener {
        void onModelSelected(ModelManager.ModelInfo model);
    }

    public ModelAdapter(List<ModelManager.ModelInfo> models, ModelManager manager, OnModelActionListener listener) {
        this.models = models;
        this.manager = manager;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelManager.ModelInfo model = models.get(position);

        holder.modelName.setText(model.name);
        holder.modelTier.setText("Est. RAM: " + (model.estimatedRamBytes / (1024L * 1024L)) + "MB");
        holder.modelSize.setText("~" + String.format("%.1f", model.estimatedRamBytes / (1024.0 * 1024.0 * 1024.0)) + " GB");
        holder.tierBadge.setText(model.tier.label);

        if (model.isDownloaded) {
            holder.btnDownload.setVisibility(View.GONE);
            holder.btnUse.setVisibility(View.VISIBLE);
            holder.statusIcon.setVisibility(View.VISIBLE);
            holder.modelProgress.setVisibility(View.GONE);
            holder.downloadStatus.setVisibility(View.GONE);
            
            holder.btnUse.setOnClickListener(v -> {
                if (listener != null) listener.onModelSelected(model);
            });
        } else {
            holder.btnDownload.setVisibility(View.VISIBLE);
            holder.btnUse.setVisibility(View.GONE);
            holder.statusIcon.setVisibility(View.GONE);
            holder.modelProgress.setVisibility(View.GONE);
            holder.downloadStatus.setVisibility(View.GONE);
            
            holder.btnDownload.setOnClickListener(v -> {
                manager.downloadModel(model);
                Toast.makeText(holder.itemView.getContext(), "Download started: " + model.name, Toast.LENGTH_SHORT).show();
                holder.btnDownload.setEnabled(false);
                holder.btnDownload.setAlpha(0.5f);
            });
        }
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView modelName, modelTier, modelSize, downloadStatus;
        MaterialButton btnDownload, btnUse;
        ProgressBar modelProgress;
        Chip tierBadge;
        ImageView statusIcon;

        ViewHolder(View view) {
            super(view);
            modelName = view.findViewById(R.id.modelName);
            modelTier = view.findViewById(R.id.modelTier);
            modelSize = view.findViewById(R.id.modelSize);
            btnDownload = view.findViewById(R.id.btnDownload);
            btnUse = view.findViewById(R.id.btnUse);
            modelProgress = view.findViewById(R.id.modelProgress);
            tierBadge = view.findViewById(R.id.tierBadge);
            statusIcon = view.findViewById(R.id.statusIcon);
            downloadStatus = view.findViewById(R.id.downloadStatus);
        }
    }
}