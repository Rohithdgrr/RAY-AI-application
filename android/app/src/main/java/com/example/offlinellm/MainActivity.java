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

    private ModelManager.Tier currentPreference = ModelManager.Tier.BALANCED;
    private boolean isRemoteFallbackEnabled = false;
    private RemoteInference remoteEngine;

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

        remoteEngine = new RemoteInference("YOUR_GROQ_API_KEY"); // User should set this

        binding.tierGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.tierHigh) currentPreference = ModelManager.Tier.HIGH_QUALITY;
            else if (checkedId == R.id.tierBalanced) currentPreference = ModelManager.Tier.BALANCED;
            else if (checkedId == R.id.tierMedium) currentPreference = ModelManager.Tier.MEDIUM;
            else if (checkedId == R.id.tierLight) currentPreference = ModelManager.Tier.LIGHT;
            else if (checkedId == R.id.tierUltra) currentPreference = ModelManager.Tier.ULTRA_LIGHT;
            
            loadBestModel(currentPreference);
        });

        binding.remoteFallbackSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isRemoteFallbackEnabled = isChecked;
        });
        
        // Initial auto-load
        loadBestModel(currentPreference);
    }

    private void loadBestModel(ModelManager.Tier preference) {
        ModelManager mm = ModelManager.getInstance(this);
        ModelManager.ModelInfo info = mm.getBestDownloadedModel(preference);
        if (info != null) {
            loadModel(info);
        } else {
            mainHandler.post(() -> binding.statusText.setText("No local models found. Use Manage Models to download."));
        }
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

        if (engine == null || !engine.isLoaded()) {
            if (isRemoteFallbackEnabled) {
                mainHandler.post(() -> assistantMsg.setText("Local model not loaded. Using Remote Fallback..."));
                callRemoteFallback(prompt, assistantMsg, assistPos);
            } else {
                assistantMsg.setText("Error: Local engine not loaded and Remote Fallback disabled.");
                adapter.notifyItemChanged(assistPos);
            }
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
                mainHandler.post(() -> {
                    if (isRemoteFallbackEnabled) {
                        Toast.makeText(MainActivity.this, "Local error, falling back to remote...", Toast.LENGTH_SHORT).show();
                        callRemoteFallback(prompt, assistantMsg, assistPos);
                    } else {
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void callRemoteFallback(String prompt, ChatMessage msg, int pos) {
        remoteEngine.generate(prompt, new RemoteInference.RemoteCallback() {
            @Override
            public void onToken(String token) {
                mainHandler.post(() -> {
                    msg.setText(token);
                    adapter.notifyItemChanged(pos);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    msg.setText("Remote Fallback failed: " + error);
                    adapter.notifyItemChanged(pos);
                });
            }
        });
    }

    public void loadModel(ModelManager.ModelInfo info) {
        new Thread(() -> {
            try {
                mainHandler.post(() -> binding.statusText.setText("Loading: " + info.name + "..."));

                File encryptedFile = new File(getFilesDir(), info.fileName);
                File tempDecrypted = new File(getCacheDir(), "m.tmp");

                // Memory Preflight
                if (!ModelManager.getInstance(this).canLoadModel(info)) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "RAM too low for " + info.tier.label + ". Trying fallback...", Toast.LENGTH_SHORT).show();
                        tryFallback(info.tier);
                    });
                    return;
                }

                SecurityHelper.decryptFile(this, encryptedFile, tempDecrypted);
                
                // Detection
                if (info.fileName.endsWith(".onnx.enc")) {
                    engine = new OnnxInference();
                } else {
                    engine = new LlamaInference();
                }

                engine.loadModel(tempDecrypted);
                tempDecrypted.delete();

                mainHandler.post(() -> binding.statusText.setText("Status: Loaded (" + info.tier.label + ")"));
            } catch (Exception e) {
                Log.e("MainActivity", "Load failed", e);
                mainHandler.post(() -> tryFallback(info.tier));
            }
        }).start();
    }

    private void tryFallback(ModelManager.Tier failedTier) {
        ModelManager mm = ModelManager.getInstance(this);
        // Find next lower tier
        int nextIdx = failedTier.ordinal() + 1;
        if (nextIdx < ModelManager.Tier.values().length) {
            ModelManager.Tier nextTier = ModelManager.Tier.values()[nextIdx];
            ModelManager.ModelInfo nextInfo = mm.getBestDownloadedModel(nextTier);
            if (nextInfo != null) {
                loadModel(nextInfo);
                return;
            }
        }
        
        mainHandler.post(() -> {
            binding.statusText.setText("Status: Not loaded (All local fallbacks failed)");
            Toast.makeText(this, "Local load failed. Enable Remote Fallback for cloud support.", Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
    }
}
