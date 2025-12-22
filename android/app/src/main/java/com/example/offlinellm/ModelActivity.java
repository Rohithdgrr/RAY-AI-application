package com.example.offlinellm;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.example.offlinellm.databinding.ActivityModelBinding;
import java.util.ArrayList;
import java.util.List;

public class ModelActivity extends AppCompatActivity implements ModelAdapter.OnModelActionListener, ModelManager.DownloadProgressListener {
    private ActivityModelBinding binding;
    private ModelManager manager;
    private ModelAdapter adapter;
    private List<ModelManager.ModelInfo> allModels;
    private List<ModelManager.ModelInfo> filteredModels;
    private String currentSearchQuery = "";
    private ModelManager.Tier selectedTier = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityModelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.modelToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        binding.modelToolbar.setNavigationOnClickListener(v -> finish());

        manager = ModelManager.getInstance(this);
        manager.addProgressListener(this);
        allModels = new ArrayList<>(manager.getModels());
        filteredModels = new ArrayList<>(allModels);

        // Setup RecyclerView
        adapter = new ModelAdapter(filteredModels, manager, this);
        binding.modelRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.modelRecyclerView.setAdapter(adapter);

        // Setup search
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().toLowerCase();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Setup filter chips
        binding.filterChips.setOnCheckedStateChangeListener((group, checkedIds) -> {
            selectedTier = null;
            
            if (checkedIds.contains(binding.chipLight.getId())) {
                selectedTier = ModelManager.Tier.LIGHT;
            } else if (checkedIds.contains(binding.chipBalanced.getId())) {
                selectedTier = ModelManager.Tier.BALANCED;
            } else if (checkedIds.contains(binding.chipHighQuality.getId())) {
                selectedTier = ModelManager.Tier.HIGH_QUALITY;
            }
            
            applyFilters();
        });

        binding.btnHFImport.setOnClickListener(v -> showHFImportDialog());
    }

    private void showHFImportDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("https://huggingface.co/.../resolve/main/model.gguf");
        input.setPadding(40, 40, 40, 40);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Import from Hugging Face")
                .setMessage("Enter the direct GGUF download URL:")
                .setView(input)
                .setPositiveButton("Download", (dialog, which) -> {
                    String url = input.getText().toString().trim();
                    if (!url.isEmpty()) {
                        importCustomModel(url);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importCustomModel(String url) {
        // Extract filename from URL
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        if (!fileName.endsWith(".gguf")) {
            fileName += ".gguf";
        }
        
        ModelManager.ModelInfo customModel = new ModelManager.ModelInfo(
                "HF: " + fileName,
                url,
                fileName + ".enc",
                "PLACEHOLDER",
                ModelManager.Tier.BALANCED,
                4000L * 1024L * 1024L
        );
        
        manager.getModels().add(customModel);
        allModels.add(customModel);
        manager.downloadModel(customModel, url);
        applyFilters();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (manager != null) {
            manager.removeProgressListener(this);
        }
    }

    @Override
    public void onDownloadProgress(ModelManager.ModelInfo model, int progress, long downloadedBytes, long totalBytes) {
        runOnUiThread(() -> {
            int index = filteredModels.indexOf(model);
            if (index != -1) {
                adapter.notifyItemChanged(index);
            }
        });
    }

    @Override
    public void onDownloadStarted(ModelManager.ModelInfo model) {
        runOnUiThread(() -> {
            int index = filteredModels.indexOf(model);
            if (index != -1) {
                adapter.notifyItemChanged(index);
            }
        });
    }

    @Override
    public void onDownloadCompleted(ModelManager.ModelInfo model) {
        runOnUiThread(() -> {
            int index = filteredModels.indexOf(model);
            if (index != -1) {
                adapter.notifyItemChanged(index);
            }
            android.widget.Toast.makeText(this, "Download complete: " + model.name, android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDownloadFailed(ModelManager.ModelInfo model, String error) {
        runOnUiThread(() -> {
            int index = filteredModels.indexOf(model);
            if (index != -1) {
                adapter.notifyItemChanged(index);
            }
            android.widget.Toast.makeText(this, "Download failed: " + error, android.widget.Toast.LENGTH_LONG).show();
        });
    }

    private void applyFilters() {
        filteredModels.clear();

        for (ModelManager.ModelInfo model : allModels) {
            // Filter by search query
            boolean matchesSearch = currentSearchQuery.isEmpty() ||
                    model.name.toLowerCase().contains(currentSearchQuery) ||
                    model.tier.label.toLowerCase().contains(currentSearchQuery);

            // Filter by tier
            boolean matchesTier = selectedTier == null || model.tier == selectedTier;

            if (matchesSearch && matchesTier) {
                filteredModels.add(model);
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredModels.isEmpty()) {
            binding.modelRecyclerView.setVisibility(View.GONE);
            binding.emptyState.setVisibility(View.VISIBLE);
        } else {
            binding.modelRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    public void onModelSelected(ModelManager.ModelInfo model) {
        finish();
    }
}
