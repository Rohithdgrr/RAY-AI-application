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

public class ModelActivity extends AppCompatActivity implements ModelAdapter.OnModelActionListener {
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
