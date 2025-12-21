package com.example.offlinellm;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.offlinellm.databinding.ActivityModelBinding;
import java.util.List;

public class ModelActivity extends AppCompatActivity {
    private ActivityModelBinding binding;
    private ModelManager manager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityModelBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        manager = ModelManager.getInstance(this);
        List<ModelManager.ModelInfo> models = manager.getModels();

        ArrayAdapter<ModelManager.ModelInfo> adapter = new ArrayAdapter<ModelManager.ModelInfo>(this, android.R.layout.simple_list_item_2, android.R.id.text1, models) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = convertView;
                if (v == null) v = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                
                ModelManager.ModelInfo info = getItem(position);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                
                t1.setText(info.name);
                t2.setText(info.isDownloaded ? "Downloaded (Encrypted)" : "Available to Download");
                
                return v;
            }
        };

        binding.modelListView.setAdapter(adapter);
        binding.modelListView.setOnItemClickListener((parent, view, position, id) -> {
            ModelManager.ModelInfo info = models.get(position);
            if (info.isDownloaded) {
                // Return to MainActivity and load
                Toast.makeText(this, "Loading " + info.name, Toast.LENGTH_SHORT).show();
                // In a real app, use a listener or shared view model to trigger load in MainActivity
                // For this example, we'll just show the Toast
                finish();
            } else {
                manager.downloadModel(info);
                Toast.makeText(this, "Started download...", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
