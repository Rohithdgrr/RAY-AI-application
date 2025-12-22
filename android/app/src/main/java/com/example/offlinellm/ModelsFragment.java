package com.example.offlinellm;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.net.Uri;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import java.text.DecimalFormat;
import java.util.List;

public class ModelsFragment extends Fragment implements ModelManager.DownloadProgressListener {

    private ModelManager modelManager;
    private RecyclerView recyclerView;
    private ModelAdapter adapter;
    private EditText hfSearchInput;
    private Button btnHFSearch;
    private Handler mainHandler;
    private DecimalFormat sizeFormat;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        modelManager = ModelManager.getInstance(getContext());
        mainHandler = new Handler(Looper.getMainLooper());
        sizeFormat = new DecimalFormat("#.#");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_models, container, false);
        
        recyclerView = view.findViewById(R.id.modelRecyclerView);
        hfSearchInput = view.findViewById(R.id.hfSearchInput);
        btnHFSearch = view.findViewById(R.id.btnHFSearch);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        List<ModelManager.ModelInfo> models = modelManager.getModels();
        adapter = new ModelAdapter(models);
        adapter.setRecyclerView(recyclerView);
        recyclerView.setAdapter(adapter);
        
        btnHFSearch.setOnClickListener(v -> searchHuggingFace());
        
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        modelManager.addProgressListener(this);
        modelManager.scanForExistingModels();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    @Override
    public void onPause() {
        super.onPause();
        modelManager.removeProgressListener(this);
    }

    private void searchHuggingFace() {
        String query = hfSearchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(getContext(), "Enter a search term", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (query.startsWith("http")) {
            addCustomModelFromUrl(query);
            return;
        }

        btnHFSearch.setEnabled(false);
        btnHFSearch.setText("Searching...");

        new Thread(() -> {
            try {
                // HF API search for models with GGUF filter
                String urlString = "https://huggingface.co/api/models?search=" + Uri.encode(query) + "&filter=gguf&sort=downloads&direction=-1&limit=8";
                java.net.URL url = new java.net.URL(urlString);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                java.io.InputStream is = conn.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                
                com.google.gson.JsonArray results = com.google.gson.JsonParser.parseString(sb.toString()).getAsJsonArray();
                
                mainHandler.post(() -> {
                    if (results.size() == 0) {
                        Toast.makeText(getContext(), "No models found", Toast.LENGTH_SHORT).show();
                    } else {
                        for (int i = 0; i < results.size(); i++) {
                            com.google.gson.JsonObject obj = results.get(i).getAsJsonObject();
                            String modelId = obj.get("id").getAsString();
                            
                            // Create a model info for this result
                            // Note: We need a GGUF file URL. Usually it's resolve/main/FILENAME.gguf
                            // For simplicity, we'll try to guess or just add the repo
                            // A better way would be to list files in the repo and pick the first .gguf
                            
                            String downloadUrl = "https://huggingface.co/" + modelId + "/resolve/main/";
                            // We don't know the exact filename yet, so we'll let the user enter it or try a default
                            
                            ModelManager.ModelInfo info = new ModelManager.ModelInfo(
                                modelId.split("/")[1],
                                downloadUrl, // Partial URL, will need the filename
                                modelId.replace("/", "_") + ".gguf.enc",
                                "PLACEHOLDER",
                                ModelManager.Tier.BALANCED,
                                4000L * 1024L * 1024L
                            );
                            
                            modelManager.getModels().add(0, info);
                        }
                        adapter.notifyDataSetChanged();
                        recyclerView.scrollToPosition(0);
                        Toast.makeText(getContext(), "Found " + results.size() + " models. Click Download and paste specific GGUF filename if needed.", Toast.LENGTH_LONG).show();
                    }
                    btnHFSearch.setEnabled(true);
                    btnHFSearch.setText("Search");
                });
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(getContext(), "Search failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnHFSearch.setEnabled(true);
                    btnHFSearch.setText("Search");
                });
            }
        }).start();
    }

    private void addCustomModelFromUrl(String query) {
        Toast.makeText(getContext(), "Adding custom model...", Toast.LENGTH_SHORT).show();
        String fileName = query.substring(query.lastIndexOf('/') + 1);
        if (fileName.contains("?")) fileName = fileName.substring(0, fileName.indexOf("?"));
        if (!fileName.endsWith(".gguf")) fileName += ".gguf";
        
        ModelManager.ModelInfo customModel = new ModelManager.ModelInfo(
            "Custom: " + fileName,
            query,
            fileName.replace(".gguf", ".gguf.enc"),
            "PLACEHOLDER",
            ModelManager.Tier.MEDIUM,
            3000L * 1024L * 1024L
        );
        
        modelManager.getModels().add(0, customModel);
        adapter.notifyItemInserted(0);
        recyclerView.scrollToPosition(0);
        hfSearchInput.setText("");
    }

    @Override
    public void onDownloadProgress(ModelManager.ModelInfo model, int progress, long downloadedBytes, long totalBytes) {
        mainHandler.post(() -> {
            if (adapter != null) adapter.updateProgress(model, progress, downloadedBytes, totalBytes);
        });
    }

    @Override
    public void onDownloadStarted(ModelManager.ModelInfo model) {
        mainHandler.post(() -> {
            if (adapter != null) adapter.updateDownloadStarted(model);
        });
    }

    @Override
    public void onDownloadCompleted(ModelManager.ModelInfo model) {
        mainHandler.post(() -> {
            if (adapter != null) adapter.updateDownloadCompleted(model);
        });
    }

    @Override
    public void onDownloadFailed(ModelManager.ModelInfo model, String error) {
        mainHandler.post(() -> {
            if (adapter != null) adapter.updateDownloadFailed(model, error);
        });
    }

    // Adapt ModelAdapter logic from ModelBottomSheet here or refactor it to a common class
    // For speed, I'll duplicate and adjust slightly here.
    private class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
        private final List<ModelManager.ModelInfo> models;
        private RecyclerView recyclerView;

        public ModelAdapter(List<ModelManager.ModelInfo> models) { this.models = models; }
        public void setRecyclerView(RecyclerView recyclerView) { this.recyclerView = recyclerView; }

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
            holder.size.setText("~" + formatFileSize(1500L * 1024L * 1024L)); // Mock size
            
            holder.tierBadge.setText(info.tier.label);
            
            updateViewHolder(holder, info);
            
            holder.btnDownload.setOnClickListener(v -> {
                if (info.isDownloading) modelManager.cancelDownload(info);
                else modelManager.downloadModel(info);
                notifyItemChanged(position);
            });
            
            holder.btnUse.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).loadModel(info);
                    // Switch to home fragment
                    ((MainActivity) getActivity()).showHome();
                }
            });

            if (holder.btnDelete != null) {
                holder.btnDelete.setVisibility(modelManager.verifyModel(info) ? View.VISIBLE : View.GONE);
                holder.btnDelete.setOnClickListener(v -> {
                    modelManager.deleteModel(info);
                    notifyItemChanged(position);
                });
            }
        }

        private void updateViewHolder(ViewHolder holder, ModelManager.ModelInfo info) {
            boolean downloaded = modelManager.verifyModel(info);
            if (downloaded) {
                holder.btnDownload.setVisibility(View.GONE);
                holder.btnUse.setVisibility(View.VISIBLE);
                holder.progress.setVisibility(View.GONE);
                holder.downloadStatus.setVisibility(View.VISIBLE);
                holder.downloadStatus.setText("Ready");
                holder.downloadStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else if (info.isDownloading) {
                holder.btnDownload.setVisibility(View.VISIBLE);
                holder.btnDownload.setText("Cancel");
                holder.btnUse.setVisibility(View.GONE);
                holder.progress.setVisibility(View.VISIBLE);
                holder.progress.setProgress(info.downloadProgress);
                holder.downloadStatus.setVisibility(View.VISIBLE);
                holder.downloadStatus.setText("Downloading: " + info.downloadProgress + "%");
                holder.downloadStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
            } else {
                holder.btnDownload.setVisibility(View.VISIBLE);
                holder.btnDownload.setText("Download");
                holder.btnUse.setVisibility(View.GONE);
                holder.progress.setVisibility(View.GONE);
                holder.downloadStatus.setVisibility(View.GONE);
            }
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024 * 1024 * 1024) return sizeFormat.format(bytes / (1024.0 * 1024.0)) + " MB";
            return sizeFormat.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }

        public void updateProgress(ModelManager.ModelInfo model, int progress, long downloadedBytes, long totalBytes) {
            int pos = getModelPosition(model);
            if (pos != -1) {
                ViewHolder holder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(pos);
                if (holder != null) {
                    holder.progress.setProgress(progress);
                    holder.downloadStatus.setText("Downloading: " + progress + "%");
                }
            }
        }

        public void updateDownloadStarted(ModelManager.ModelInfo model) { notifyItemChanged(getModelPosition(model)); }
        public void updateDownloadCompleted(ModelManager.ModelInfo model) { notifyItemChanged(getModelPosition(model)); }
        public void updateDownloadFailed(ModelManager.ModelInfo model, String error) {
            int pos = getModelPosition(model);
            if (pos != -1) {
                notifyItemChanged(pos);
                Toast.makeText(getContext(), "Download failed: " + error, Toast.LENGTH_LONG).show();
            }
        }

        private int getModelPosition(ModelManager.ModelInfo model) {
            for (int i = 0; i < models.size(); i++) {
                if (models.get(i).name.equals(model.name)) return i;
            }
            return -1;
        }

        @Override public int getItemCount() { return models.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, tier, size, downloadStatus;
            Button btnDownload, btnUse, btnDelete;
            ProgressBar progress;
            Chip tierBadge;

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
                tierBadge = itemView.findViewById(R.id.tierBadge);
            }
        }
    }
}