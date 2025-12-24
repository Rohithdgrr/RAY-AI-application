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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.Toast;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import com.example.offlinellm.ModelAdapter;

public class ModelsFragment extends Fragment implements ModelManager.DownloadProgressListener {

    private ModelManager modelManager;
    private RecyclerView recyclerView;
    private ModelAdapter adapter;
    private EditText hfSearchInput;
    private Button btnHFSearch;
    private Handler mainHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        modelManager = ModelManager.getInstance(getContext());
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_models, container, false);
        
        try {
            recyclerView = view.findViewById(R.id.modelRecyclerView);
            hfSearchInput = view.findViewById(R.id.hfSearchInput);
            btnHFSearch = view.findViewById(R.id.btnHFSearch);
            
            if (recyclerView != null) {
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                // Display only mobile-compatible models (LIGHT and ULTRA_LIGHT tiers)
                List<ModelManager.ModelInfo> models = modelManager.getMobileCompatibleModels();
                if (models == null) {
                    models = new ArrayList<>();
                }
                adapter = new ModelAdapter(models, modelManager, model -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).loadModel(model);
                        ((MainActivity) getActivity()).showHome();
                    }
                });
                recyclerView.setAdapter(adapter);
            }
            
            if (btnHFSearch != null) {
                btnHFSearch.setOnClickListener(v -> searchHuggingFace());
            }
        } catch (Exception e) {
            android.util.Log.e("ModelsFragment", "Error initializing fragment", e);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Error loading models: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        
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
                    if (!isAdded()) return;
                    if (results.size() == 0) {
                        Toast.makeText(getContext(), "No models found", Toast.LENGTH_SHORT).show();
                    } else {
                        for (int i = 0; i < results.size(); i++) {
                            com.google.gson.JsonObject obj = results.get(i).getAsJsonObject();
                            String modelId = obj.get("id").getAsString();
                            
                            // Find GGUF files in siblings
                            String fileName = "";
                            if (obj.has("siblings")) {
                                com.google.gson.JsonArray siblings = obj.getAsJsonArray("siblings");
                                for (int j = 0; j < siblings.size(); j++) {
                                    String rfile = siblings.get(j).getAsJsonObject().get("rfile").getAsString();
                                    if (rfile.toLowerCase().endsWith(".gguf")) {
                                        fileName = rfile;
                                        break;
                                    }
                                }
                            }
                            
                            if (fileName.isEmpty()) {
                                // Fallback: search for first file in repo if siblings missing
                                fileName = modelId.contains("/") ? modelId.split("/")[1] + ".gguf" : modelId + ".gguf";
                            }
                            
                            String displayName = modelId.contains("/") ? modelId.split("/")[1] : modelId;
                            String downloadUrl = "https://huggingface.co/" + modelId + "/resolve/main/" + fileName;
                            
                            ModelManager.ModelInfo info = new ModelManager.ModelInfo(
                                displayName + " (" + fileName + ")",
                                downloadUrl, 
                                modelId.replace("/", "_") + "_" + fileName.replace("/", "_") + ".enc",
                                "PLACEHOLDER",
                                ModelManager.Tier.BALANCED,
                                4000L * 1024L * 1024L
                            );
                            
                            // Check if model already in list to avoid duplicates
                            boolean exists = false;
                            for (ModelManager.ModelInfo m : modelManager.getModels()) {
                                if (m.fileName.equals(info.fileName)) {
                                    exists = true;
                                    break;
                                }
                            }
                            if (!exists) {
                                modelManager.getModels().add(0, info);
                            }
                        }
                        adapter.notifyDataSetChanged();
                        recyclerView.scrollToPosition(0);
                        Toast.makeText(getContext(), "Found models. Check if filenames are correct in the URL.", Toast.LENGTH_LONG).show();
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

    // Removed inline adapter; using shared ModelAdapter
}