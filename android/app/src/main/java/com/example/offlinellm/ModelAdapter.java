package com.example.offlinellm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import java.util.List;
import java.text.DecimalFormat;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
    private final List<ModelManager.ModelInfo> models;
    private final ModelManager manager;
    private final OnModelActionListener listener;
    private final DecimalFormat sizeFormat = new DecimalFormat("0.0");

    public interface OnModelActionListener {
        void onModelSelected(ModelManager.ModelInfo model);
    }

    public ModelAdapter(List<ModelManager.ModelInfo> models, ModelManager manager, OnModelActionListener listener) {
        this.models = models;
        this.manager = manager;
        this.listener = listener;
    }

    public void updateDownloadStarted(ModelManager.ModelInfo target) {
        int pos = findPosition(target);
        if (pos == -1) return;
        ModelManager.ModelInfo model = models.get(pos);
        model.isDownloading = true;
        model.downloadProgress = 0;
        notifyItemChanged(pos);
    }

    public void updateProgress(ModelManager.ModelInfo target, int progress, long downloadedBytes, long totalBytes) {
        int pos = findPosition(target);
        if (pos == -1) return;
        ModelManager.ModelInfo model = models.get(pos);
        model.isDownloading = true;
        model.downloadProgress = progress;
        model.downloadedBytes = downloadedBytes;
        model.totalBytes = totalBytes;
        notifyItemChanged(pos);
    }

    public void updateDownloadCompleted(ModelManager.ModelInfo target) {
        int pos = findPosition(target);
        if (pos == -1) return;
        ModelManager.ModelInfo model = models.get(pos);
        model.isDownloading = false;
        model.isDownloaded = true;
        model.downloadProgress = 100;
        notifyItemChanged(pos);
    }

    public void updateDownloadFailed(ModelManager.ModelInfo target, String error) {
        int pos = findPosition(target);
        if (pos == -1) return;
        ModelManager.ModelInfo model = models.get(pos);
        model.isDownloading = false;
        model.downloadProgress = 0;
        notifyItemChanged(pos);
    }

    private int findPosition(ModelManager.ModelInfo target) {
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).fileName.equals(target.fileName)) return i;
        }
        return -1;
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
        holder.modelTier.setText("~" + (model.estimatedRamBytes / (1024L * 1024L)) + "MB RAM");
        holder.modelSize.setText("~" + String.format("%.1f", model.estimatedRamBytes / (1024.0 * 1024.0 * 1024.0)) + " GB");
        holder.tierBadge.setText(model.tier.label);

        // Set tier badge color based on tier
        int badgeColor;
        switch (model.tier) {
            case HIGH_QUALITY:
                badgeColor = holder.itemView.getContext().getColor(R.color.tier_high_quality);
                break;
            case BALANCED:
                badgeColor = holder.itemView.getContext().getColor(R.color.tier_balanced);
                break;
            case MEDIUM:
                badgeColor = holder.itemView.getContext().getColor(R.color.tier_medium);
                break;
            case LIGHT:
                badgeColor = holder.itemView.getContext().getColor(R.color.tier_light);
                break;
            case ULTRA_LIGHT:
                badgeColor = holder.itemView.getContext().getColor(R.color.tier_ultra_light);
                break;
            default:
                badgeColor = holder.itemView.getContext().getColor(R.color.primary);
        }
        holder.tierBadge.setChipBackgroundColorResource(0);
        holder.tierBadge.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(badgeColor));

        // Verification check
        if (!model.isDownloaded) {
            model.isDownloaded = manager.isModelAvailableOnDevice(model.fileName);
        }

        if (model.isDownloaded) {
            // Downloaded state
            holder.btnDownload.setVisibility(View.GONE);
            holder.btnUse.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.statusIcon.setVisibility(View.VISIBLE);
            holder.progressContainer.setVisibility(View.GONE);
            
            holder.btnUse.setOnClickListener(v -> {
                if (listener != null) listener.onModelSelected(model);
            });
            
            holder.btnDelete.setOnClickListener(v -> {
                manager.deleteModel(model);
                model.isDownloaded = false;
                notifyItemChanged(position);
            });
        } else if (model.isDownloading) {
            // Downloading state
            holder.btnDownload.setVisibility(View.GONE);
            holder.btnUse.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.statusIcon.setVisibility(View.GONE);
            holder.progressContainer.setVisibility(View.VISIBLE);
            
            holder.modelProgress.setProgress(model.downloadProgress);
            String percent = model.downloadProgress + "%";
            String bytes = "";
            if (model.totalBytes > 0) {
                double downloadedGb = model.downloadedBytes / (1024.0 * 1024.0 * 1024.0);
                double totalGb = model.totalBytes / (1024.0 * 1024.0 * 1024.0);
                bytes = " (" + sizeFormat.format(downloadedGb) + " / " + sizeFormat.format(totalGb) + " GB)";
            }
            holder.downloadStatus.setText("Downloading: " + percent + bytes);
        } else {
            // Not downloaded state
            holder.btnDownload.setVisibility(View.VISIBLE);
            holder.btnDownload.setText("Download");
            holder.btnDownload.setEnabled(true);
            holder.btnDownload.setAlpha(1.0f);
            holder.btnUse.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.statusIcon.setVisibility(View.GONE);
            holder.progressContainer.setVisibility(View.GONE);
            
            holder.btnDownload.setOnClickListener(v -> {
                manager.downloadModel(model);
                notifyItemChanged(position);
            });
        }
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView modelName, modelTier, modelSize, downloadStatus;
        MaterialButton btnDownload, btnUse, btnDelete;
        ProgressBar modelProgress;
        LinearLayout progressContainer;
        Chip tierBadge;
        ImageView statusIcon;

        ViewHolder(View view) {
            super(view);
            modelName = view.findViewById(R.id.modelName);
            modelTier = view.findViewById(R.id.modelTier);
            modelSize = view.findViewById(R.id.modelSize);
            btnDownload = view.findViewById(R.id.btnDownload);
            btnUse = view.findViewById(R.id.btnUse);
            btnDelete = view.findViewById(R.id.btnDelete);
            modelProgress = view.findViewById(R.id.modelProgress);
            progressContainer = view.findViewById(R.id.progressContainer);
            tierBadge = view.findViewById(R.id.tierBadge);
            statusIcon = view.findViewById(R.id.statusIcon);
            downloadStatus = view.findViewById(R.id.downloadStatus);
        }
    }
}