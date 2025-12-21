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
import java.util.List;

public class ModelBottomSheet extends BottomSheetDialogFragment {

    public interface OnModelSelectedListener {
        void onModelSelected(ModelManager.ModelInfo model);
    }

    private OnModelSelectedListener listener;
    private ModelAdapter adapter;

    public void setListener(OnModelSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sheet_models, container, false);
        RecyclerView recyclerView = view.findViewById(R.id.modelRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        List<ModelManager.ModelInfo> models = ModelManager.getInstance(getContext()).getModels();
        adapter = new ModelAdapter(models);
        recyclerView.setAdapter(adapter);
        
        return view;
    }

    private class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
        private final List<ModelManager.ModelInfo> models;

        public ModelAdapter(List<ModelManager.ModelInfo> models) {
            this.models = models;
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
            holder.tier.setText("Tier: " + info.tier.label);
            
            ModelManager manager = ModelManager.getInstance(getContext());
            boolean downloaded = manager.verifyModel(info);
            
            holder.btnDownload.setVisibility(downloaded ? View.GONE : View.VISIBLE);
            holder.btnUse.setVisibility(downloaded ? View.VISIBLE : View.GONE);
            
            holder.btnDownload.setOnClickListener(v -> {
                manager.downloadModel(info);
                notifyItemChanged(position);
            });
            
            holder.btnUse.setOnClickListener(v -> {
                if (listener != null) listener.onModelSelected(info);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return models.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView name, tier;
            Button btnDownload, btnUse;
            ProgressBar progress;
            ImageButton btnPauseResume;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.modelName);
                tier = itemView.findViewById(R.id.modelTier);
                btnDownload = itemView.findViewById(R.id.btnDownload);
                btnUse = itemView.findViewById(R.id.btnUse);
                progress = itemView.findViewById(R.id.modelProgress);
                btnPauseResume = itemView.findViewById(R.id.btnPauseResume);
            }
        }
    }
}
