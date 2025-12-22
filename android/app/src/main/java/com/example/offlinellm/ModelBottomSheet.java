package com.example.offlinellm;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.text.DecimalFormat;
import java.util.List;

public class ModelBottomSheet extends BottomSheetDialogFragment implements ModelManager.DownloadProgressListener {

    public interface OnModelSelectedListener {
        void onModelSelected(ModelManager.ModelInfo model);
    }

    private OnModelSelectedListener listener;
    private ModelAdapter adapter;
    private ModelManager modelManager;
    private Handler mainHandler;
    private DecimalFormat sizeFormat;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        modelManager = ModelManager.getInstance(getContext());
        mainHandler = new Handler(Looper.getMainLooper());
        sizeFormat = new DecimalFormat("#.#");
    }

    @Override
    public void onResume() {
        super.onResume();
        modelManager.addProgressListener(this);
        modelManager.scanForExistingModels();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        modelManager.removeProgressListener(this);
    }

    public void setListener(OnModelSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onDownloadProgress(ModelManager.ModelInfo model, int progress, long downloadedBytes, long totalBytes) {
        mainHandler.post(() -> {
            if (adapter != null) {
                adapter.updateProgress(model, progress, downloadedBytes, totalBytes);
            }
        });
    }

    @Override
    public void onDownloadStarted(ModelManager.ModelInfo model) {
        mainHandler.post(() -> {
            if (adapter != null) {
                adapter.updateDownloadStarted(model);
            }
        });
    }

    @Override
    public void onDownloadCompleted(ModelManager.ModelInfo model) {
        mainHandler.post(() -> {
            if (adapter != null) {
                adapter.updateDownloadCompleted(model);
            }
        });
    }

    @Override
    public void onDownloadFailed(ModelManager.ModelInfo model, String error) {
        mainHandler.post(() -> {
            if (adapter != null) {
                adapter.updateDownloadFailed(model, error);
            }
        });
    }
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sheet_models, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.modelRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        List<ModelManager.ModelInfo> models = modelManager.getModels();
        adapter = new ModelAdapter(models);
        adapter.setRecyclerView(recyclerView);
        recyclerView.setAdapter(adapter);
        
        return view;
    }

    private class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
        private final List<ModelManager.ModelInfo> models;
        private RecyclerView recyclerView;

        public ModelAdapter(List<ModelManager.ModelInfo> models) {
            this.models = models;
        }
        
        public void setRecyclerView(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_model, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ModelManager.ModelInfo info = models.get(position);
            holder.name.setText(info.name);
            holder.tier.setText("Est. RAM: " + formatFileSize(info.estimatedRamBytes));
            holder.size.setText("~" + formatFileSize(getModelFileSize(info)));
            
            // Set tier badge
            holder.tierBadge.setText(info.tier.label);
            switch (info.tier) {
                case HIGH_QUALITY:
                    holder.tierBadge.setChipBackgroundColorResource(android.R.color.holo_red_dark);
                    break;
                case BALANCED:
                    holder.tierBadge.setChipBackgroundColorResource(android.R.color.holo_orange_dark);
                    break;
                case MEDIUM:
                    holder.tierBadge.setChipBackgroundColorResource(android.R.color.holo_blue_dark);
                    break;
                case LIGHT:
                    holder.tierBadge.setChipBackgroundColorResource(android.R.color.holo_green_dark);
                    break;
                case ULTRA_LIGHT:
                    holder.tierBadge.setChipBackgroundColorResource(android.R.color.holo_purple);
                    break;
            }
            
            updateViewHolder(holder, info);
            
            holder.btnDownload.setOnClickListener(v -> {
                if (info.isDownloading) {
                    modelManager.cancelDownload(info);
                } else {
                    modelManager.downloadModel(info);
                }
                notifyItemChanged(position);
            });
            
            holder.btnUse.setOnClickListener(v -> {
                if (listener != null) listener.onModelSelected(info);
                dismiss();
            });

            if (holder.btnDelete != null) {
                holder.btnDelete.setVisibility(modelManager.verifyModel(info) ? View.VISIBLE : View.GONE);
                holder.btnDelete.setOnClickListener(v -> {
                    modelManager.deleteModel(info);
                    notifyItemChanged(position);
                });
            }
        }

        private long getModelFileSize(ModelManager.ModelInfo info) {
            // Estimated file sizes based on model names
            if (info.name.contains("3B")) return 2000L * 1024L * 1024L; // ~2GB
            if (info.name.contains("Phi-3")) return 2300L * 1024L * 1024L; // ~2.3GB
            if (info.name.contains("Gemma-2B")) return 1600L * 1024L * 1024L; // ~1.6GB
            if (info.name.contains("TinyLlama")) return 700L * 1024L * 1024L; // ~700MB
            if (info.name.contains("Qwen2-0.5B")) return 500L * 1024L * 1024L; // ~500MB
            
            // New models
            if (info.name.contains("Mistral-7B")) return 4100L * 1024L * 1024L; // ~4.1GB
            if (info.name.contains("DeepSeek-Coder-6.7B")) return 3800L * 1024L * 1024L; // ~3.8GB
            if (info.name.contains("Qwen2.5-7B")) return 4500L * 1024L * 1024L; // ~4.5GB
            if (info.name.contains("Nemotron-4-340B")) return 4200L * 1024L * 1024L; // ~4.2GB
            if (info.name.contains("Gemma-7B")) return 4800L * 1024L * 1024L; // ~4.8GB
            if (info.name.contains("Kimi-2B")) return 1400L * 1024L * 1024L; // ~1.4GB
            if (info.name.contains("GPT-2-Small")) return 600L * 1024L * 1024L; // ~600MB
            if (info.name.contains("Qwen2-1.5B")) return 900L * 1024L * 1024L; // ~900MB
            if (info.name.contains("Mistral-Nemo")) return 1600L * 1024L * 1024L; // ~1.6GB
            
            return 1000L * 1024L * 1024L; // Default 1GB
        }

        private void updateViewHolder(ViewHolder holder, ModelManager.ModelInfo info) {
            boolean downloaded = modelManager.verifyModel(info);
            
            if (downloaded) {
                holder.btnDownload.setVisibility(View.GONE);
                holder.btnUse.setVisibility(View.VISIBLE);
                holder.progress.setVisibility(View.GONE);
                holder.downloadStatus.setVisibility(View.VISIBLE);
                holder.downloadStatus.setText("Verified & Ready");
                holder.downloadStatus.setTextColor(getContext().getResources().getColor(android.R.color.holo_green_dark));
                if (holder.statusIcon != null) {
                    holder.statusIcon.setVisibility(View.VISIBLE);
                    holder.statusIcon.setImageResource(android.R.drawable.ic_menu_save);
                    holder.statusIcon.setColorFilter(getContext().getResources().getColor(android.R.color.holo_green_dark));
                }
            } else if (info.isDownloading) {
                holder.btnDownload.setVisibility(View.VISIBLE);
                holder.btnDownload.setText("Cancel");
                holder.btnUse.setVisibility(View.GONE);
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setProgress(info.downloadProgress);
                holder.downloadStatus.setVisibility(View.VISIBLE);
                holder.downloadStatus.setText("Downloading: " + info.downloadProgress + "%");
                holder.downloadStatus.setTextColor(getContext().getResources().getColor(android.R.color.darker_gray));
                if (holder.statusIcon != null) {
                    holder.statusIcon.setVisibility(View.VISIBLE);
                    holder.statusIcon.setImageResource(android.R.drawable.stat_sys_download);
                    holder.statusIcon.clearColorFilter();
                }
            } else {
                holder.btnDownload.setVisibility(View.VISIBLE);
                holder.btnDownload.setText("Download");
                holder.btnUse.setVisibility(View.GONE);
                holder.progress.setVisibility(View.GONE);
                holder.downloadStatus.setVisibility(View.GONE);
                if (holder.statusIcon != null) {
                    holder.statusIcon.setVisibility(View.GONE);
                }
            }
        }


        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return sizeFormat.format(bytes / 1024.0) + " KB";
            if (bytes < 1024 * 1024 * 1024) return sizeFormat.format(bytes / (1024.0 * 1024.0)) + " MB";
            return sizeFormat.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }

        public void updateProgress(ModelManager.ModelInfo model, int progress, long downloadedBytes, long totalBytes) {
            int position = getModelPosition(model);
            if (position != -1) {
                ViewHolder holder = getViewHolder(position);
                if (holder != null) {
                    holder.progress.setProgress(progress);
                    holder.downloadStatus.setText("Downloading: " + progress + "% (" + 
                            formatFileSize(downloadedBytes) + " / " + formatFileSize(totalBytes) + ")");
                }
            }
        }

        public void updateDownloadStarted(ModelManager.ModelInfo model) {
            int position = getModelPosition(model);
            if (position != -1) {
                ViewHolder holder = getViewHolder(position);
                if (holder != null) {
                    updateViewHolder(holder, model);
                }
            }
        }

        public void updateDownloadCompleted(ModelManager.ModelInfo model) {
            int position = getModelPosition(model);
            if (position != -1) {
                ViewHolder holder = getViewHolder(position);
                if (holder != null) {
                    updateViewHolder(holder, model);
                }
            }
        }

        public void updateDownloadFailed(ModelManager.ModelInfo model, String error) {
            int position = getModelPosition(model);
            if (position != -1) {
                ViewHolder holder = getViewHolder(position);
                if (holder != null) {
                    holder.downloadStatus.setText("Failed: " + error);
                    holder.downloadStatus.setVisibility(View.VISIBLE);
                    updateViewHolder(holder, model);
                }
            }
        }

        private int getModelPosition(ModelManager.ModelInfo model) {
            for (int i = 0; i < models.size(); i++) {
                if (models.get(i).name.equals(model.name)) {
                    return i;
                }
            }
            return -1;
        }

        private ViewHolder getViewHolder(int position) {
            return recyclerView != null ? (ViewHolder) recyclerView.findViewHolderForAdapterPosition(position) : null;
        }

        @Override
        public int getItemCount() {
            return models.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, tier, size, downloadStatus;
            Button btnDownload, btnUse, btnDelete;
            ProgressBar progress;
            android.widget.ImageView statusIcon;
            com.google.android.material.chip.Chip tierBadge;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.modelName);
                tier = itemView.findViewById(R.id.modelTier);
                size = itemView.findViewById(R.id.modelSize);
                btnDownload = itemView.findViewById(R.id.btnDownload);
                btnUse = itemView.findViewById(R.id.btnUse);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                progress = itemView.findViewById(R.id.modelProgress);
                downloadStatus = itemView.findViewById(R.id.downloadStatus);
                statusIcon = itemView.findViewById(R.id.statusIcon);
                tierBadge = itemView.findViewById(R.id.tierBadge);
            }
        }
    }
}
