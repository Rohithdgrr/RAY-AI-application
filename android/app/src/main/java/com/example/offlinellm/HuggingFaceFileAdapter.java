package com.example.offlinellm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.JsonObject;
import java.text.DecimalFormat;
import java.util.List;

public class HuggingFaceFileAdapter extends RecyclerView.Adapter<HuggingFaceFileAdapter.ViewHolder> {

    private final List<JsonObject> files;
    private final OnFileSelectedListener listener;
    private final DecimalFormat df = new DecimalFormat("0.00");

    public interface OnFileSelectedListener {
        void onFileSelected(JsonObject file);
    }

    public HuggingFaceFileAdapter(List<JsonObject> files, OnFileSelectedListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_hf_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JsonObject file = files.get(position);
        String rfile = file.get("rfile").getAsString();
        long size = file.has("size") ? file.get("size").getAsLong() : 0;

        holder.fileName.setText(rfile);
        if (size > 0) {
            double gb = size / (1024.0 * 1024.0 * 1024.0);
            holder.fileSize.setText(df.format(gb) + " GB");
            
            // Heuristic for RAM: File size + some buffer (approx 1.2x file size for KV cache etc)
            double ramGb = gb * 1.2;
            holder.ramRequired.setText(" • ~" + df.format(ramGb) + " GB RAM required");
        } else {
            holder.fileSize.setText("Unknown size");
            holder.ramRequired.setText("");
        }

        holder.itemView.setOnClickListener(v -> listener.onFileSelected(file));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView fileName, fileSize, ramRequired;

        ViewHolder(View view) {
            super(view);
            fileName = view.findViewById(R.id.fileName);
            fileSize = view.findViewById(R.id.fileSize);
            ramRequired = view.findViewById(R.id.ramRequired);
        }
    }
}