package com.example.offlinellm;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.List;
import com.example.offlinellm.ModelAdapter;

public class ModelBottomSheet extends BottomSheetDialogFragment implements ModelManager.DownloadProgressListener {

    public interface OnModelSelectedListener {
        void onModelSelected(ModelManager.ModelInfo model);
    }

    private OnModelSelectedListener listener;
    private ModelAdapter adapter;
    private ModelManager modelManager;
    private Handler mainHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getContext() != null) {
            modelManager = ModelManager.getInstance(getContext());
        } else {
            throw new IllegalStateException("Context not available in ModelBottomSheet onCreate");
        }
        mainHandler = new Handler(Looper.getMainLooper());
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
        adapter = new ModelAdapter(models, modelManager, model -> {
            if (listener != null) listener.onModelSelected(model);
            dismiss();
        });
        recyclerView.setAdapter(adapter);
        
        return view;
    }

    // Removed obsolete inner ModelAdapter; using shared com.example.offlinellm.ModelAdapter
}
