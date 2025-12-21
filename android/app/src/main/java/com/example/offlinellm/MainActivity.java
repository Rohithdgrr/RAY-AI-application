package com.example.offlinellm;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.example.offlinellm.databinding.ActivityMainBinding;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private InferenceEngine engine;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ChatAdapter(messages);
        binding.chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.chatRecyclerView.setAdapter(adapter);

        binding.sendBtn.setOnClickListener(v -> sendMessage());
        binding.manageModelsBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, ModelActivity.class));
        });

        // Initialize engine based on last used or default
        // For example, using LlamaInference by default
        engine = new LlamaInference();
    }

    private void sendMessage() {
        String prompt = binding.promptEdit.getText().toString().trim();
        if (prompt.isEmpty()) return;

        messages.add(new ChatMessage("You", prompt));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.promptEdit.setText("");

        ChatMessage assistantMsg = new ChatMessage("AI", "");
        messages.add(assistantMsg);
        int assistPos = messages.size() - 1;
        adapter.notifyItemInserted(assistPos);

        if (engine == null) {
            assistantMsg.setText("Error: Engine not loaded.");
            adapter.notifyItemChanged(assistPos);
            return;
        }

        engine.generate(prompt, new InferenceEngine.Callback() {
            @Override
            public void onToken(String token) {
                mainHandler.post(() -> {
                    assistantMsg.setText(assistantMsg.getText() + token);
                    adapter.notifyItemChanged(assistPos);
                    binding.chatRecyclerView.scrollToPosition(assistPos);
                });
            }

            @Override
            public void onComplete() {
                Log.d("MainActivity", "Generation complete");
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    public void loadModel(ModelManager.ModelInfo info) {
        new Thread(() -> {
            try {
                mainHandler.post(() -> binding.statusText.setText(R.string.status_loading));

                File encryptedFile = new File(getFilesDir(), info.fileName);
                File tempDecrypted = new File(getCacheDir(), "m.tmp");

                // Memory Preflight
                if (!checkMemory(encryptedFile.length())) {
                    mainHandler.post(() -> {
                        binding.statusText.setText(R.string.status_not_loaded);
                        Toast.makeText(this, R.string.error_oom, Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                SecurityHelper.decryptFile(this, encryptedFile, tempDecrypted);
                
                if (info.fileName.endsWith(".onnx.enc")) {
                    engine = new OnnxInference();
                } else {
                    engine = new LlamaInference();
                }

                engine.loadModel(tempDecrypted);
                
                // Securely delete temporary file
                tempDecrypted.delete();

                mainHandler.post(() -> binding.statusText.setText(R.string.status_loaded));
            } catch (Exception e) {
                Log.e("MainActivity", "Load failed", e);
                mainHandler.post(() -> {
                    binding.statusText.setText(R.string.status_not_loaded);
                    Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private boolean checkMemory(long fileSize) {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long available = mi.availMem;
        long needed = (long) (fileSize * 1.5); // 1.5x overhead estimate
        return available > needed;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
    }
}
