package com.example.offlinellm;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
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

        binding.menuBtn.setOnClickListener(this::showSettingsMenu);

        remoteEngine = new RemoteInference("YOUR_GROQ_API_KEY"); 

        updateWelcomeVisibility();
        
        // Initial auto-load
        loadBestModel(currentPreference);
    }

    private void showSettingsMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(0, 1, 0, "Quality: High");
        popup.getMenu().add(0, 2, 0, "Quality: Balanced");
        popup.getMenu().add(0, 3, 0, "Quality: Medium");
        popup.getMenu().add(0, 4, 0, "Quality: Light");
        popup.getMenu().add(0, 5, 0, "Quality: Ultra Light");
        
        MenuItem remoteItem = popup.getMenu().add(0, 6, 0, "Remote Fallback: " + (isRemoteFallbackEnabled ? "ON" : "OFF"));

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: currentPreference = ModelManager.Tier.HIGH_QUALITY; break;
                case 2: currentPreference = ModelManager.Tier.BALANCED; break;
                case 3: currentPreference = ModelManager.Tier.MEDIUM; break;
                case 4: currentPreference = ModelManager.Tier.LIGHT; break;
                case 5: currentPreference = ModelManager.Tier.ULTRA_LIGHT; break;
                case 6: 
                    isRemoteFallbackEnabled = !isRemoteFallbackEnabled;
                    Toast.makeText(this, "Remote Fallback: " + (isRemoteFallbackEnabled ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
                    return true;
            }
            loadBestModel(currentPreference);
            return true;
        });
        popup.show();
    }

    private void updateWelcomeVisibility() {
        if (messages.isEmpty()) {
            binding.welcomeContainer.setVisibility(View.VISIBLE);
            binding.chatRecyclerView.setVisibility(View.GONE);
        } else {
            binding.welcomeContainer.setVisibility(View.GONE);
            binding.chatRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void loadBestModel(ModelManager.Tier preference) {
        ModelManager mm = ModelManager.getInstance(this);
        ModelManager.ModelInfo info = mm.getBestDownloadedModel(preference);
        if (info != null) {
            loadModel(info);
        } else {
            mainHandler.post(() -> binding.statusText.setText("RAY AI Status: Ready (No local model)"));
        }
    }

    private void sendMessage() {
        String prompt = binding.promptEdit.getText().toString().trim();
        if (prompt.isEmpty()) return;

        messages.add(new ChatMessage("You", prompt));
        adapter.notifyItemInserted(messages.size() - 1);
        binding.promptEdit.setText("");
        updateWelcomeVisibility();

        ChatMessage assistantMsg = new ChatMessage("AI", "");
        messages.add(assistantMsg);
        int assistPos = messages.size() - 1;
        adapter.notifyItemInserted(assistPos);
        binding.chatRecyclerView.scrollToPosition(assistPos);

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
                mainHandler.post(() -> binding.statusText.setText("Status: Loading " + info.name + "..."));

                File encryptedFile = new File(getFilesDir(), info.fileName);
                File tempDecrypted = new File(getCacheDir(), "m.tmp");

                if (!ModelManager.getInstance(this).canLoadModel(info)) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "Memory too low for " + info.tier.label, Toast.LENGTH_SHORT).show();
                        tryFallback(info.tier);
                    });
                    return;
                }

                SecurityHelper.decryptFile(this, encryptedFile, tempDecrypted);
                
                if (engine != null) engine.unload();
                
                if (info.fileName.endsWith(".onnx.enc")) {
                    engine = new OnnxInference();
                } else {
                    engine = new LlamaInference();
                }

                engine.loadModel(tempDecrypted);
                tempDecrypted.delete();

                mainHandler.post(() -> binding.statusText.setText("RAY AI Status: Active (" + info.tier.label + ")"));
            } catch (Exception e) {
                Log.e("MainActivity", "Load failed", e);
                mainHandler.post(() -> tryFallback(info.tier));
            }
        }).start();
    }

    private void tryFallback(ModelManager.Tier failedTier) {
        ModelManager mm = ModelManager.getInstance(this);
        int nextIdx = failedTier.ordinal() + 1;
        if (nextIdx < ModelManager.Tier.values().length) {
            ModelManager.Tier nextTier = ModelManager.Tier.values()[nextIdx];
            ModelManager.ModelInfo nextInfo = mm.getBestDownloadedModel(nextTier);
            if (nextInfo != null) {
                loadModel(nextInfo);
                return;
            }
        }
        mainHandler.post(() -> binding.statusText.setText("RAY AI Status: Fallback failed"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
    }
}
