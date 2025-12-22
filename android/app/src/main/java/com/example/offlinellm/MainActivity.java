package com.example.offlinellm;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.example.offlinellm.HomeFragment;
import com.example.offlinellm.ModelsFragment;
import com.example.offlinellm.HistoryFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.File;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ModelManager.DownloadProgressListener {
    private InferenceEngine engine;
    private ModelManager modelManager;
    private ChatHistoryManager historyManager;
    private boolean isGenerating = false;
    private InferenceEngine.Callback currentGenerationCallback;
    private TextView toolbarTitle;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        toolbarTitle = findViewById(R.id.toolbarTitle);
        bottomNav = findViewById(R.id.bottom_navigation);

        modelManager = ModelManager.getInstance(this);
        modelManager.addProgressListener(this);
        historyManager = ChatHistoryManager.getInstance(this);

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showFragment(new HomeFragment(), "Home");
                return true;
            } else if (id == R.id.nav_models) {
                showFragment(new ModelsFragment(), "Models");
                return true;
            } else if (id == R.id.nav_history) {
                showFragment(new HistoryFragment(), "History");
                return true;
            }
            return false;
        });

        // Load default fragment
        if (savedInstanceState == null) {
            showFragment(new HomeFragment(), "Home");
        }

        scanAndLoadBestModel();
        
        findViewById(R.id.btnNewChat).setOnClickListener(v -> createNewChat());
        findViewById(R.id.btnMenu).setOnClickListener(v -> {
            // Optional: Show drawer or menu
        });
    }

    private void showFragment(Fragment fragment, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        if (toolbarTitle != null) {
            toolbarTitle.setText(title.equals("Home") ? "New conversation" : title);
        }
    }

    public void showHome() {
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    private void createNewChat() {
        historyManager.createNewSession();
        showHome(); // Refresh home with new context
        Toast.makeText(this, "New chat started", Toast.LENGTH_SHORT).show();
    }

    public void handleChatMessage(String prompt, List<ChatMessage> messages, ChatAdapter adapter, RecyclerView recyclerView) {
        if (engine == null || !engine.isLoaded()) {
            Toast.makeText(this, "Model not ready. Please go to Models to download/load one.", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage responseMessage = new ChatMessage("RAY", "", "Thinking...");
        responseMessage.startGeneration();
        messages.add(responseMessage);
        int responseIndex = messages.size() - 1;
        adapter.notifyItemInserted(responseIndex);
        recyclerView.scrollToPosition(responseIndex);

        isGenerating = true;

        currentGenerationCallback = new InferenceEngine.Callback() {
            @Override
            public void onToken(String token) {
                runOnUiThread(() -> {
                    responseMessage.setText(responseMessage.getText() + token);
                    adapter.notifyItemChanged(responseIndex);
                    recyclerView.scrollToPosition(responseIndex);
                });
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    responseMessage.finishGeneration();
                    adapter.notifyItemChanged(responseIndex);
                    ChatSession session = historyManager.getActiveSession();
                    if (session != null) {
                        session.addMessage(new ChatMessage("You", prompt));
                        session.addMessage(responseMessage);
                        historyManager.updateSession(session);
                    }
                    isGenerating = false;
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + message, Toast.LENGTH_SHORT).show();
                    responseMessage.setText("Error: " + message);
                    adapter.notifyItemChanged(responseIndex);
                    isGenerating = false;
                });
            }
        };

        engine.generate(prompt, currentGenerationCallback);
    }

    public void loadModel(ModelManager.ModelInfo model) {
        if (engine != null) engine.unload();
        new Thread(() -> {
            try {
                File modelFile = new File(getFilesDir(), model.fileName);
                engine = InferenceEngine.getForFile(this, modelFile);
                engine.loadModel(modelFile);
                runOnUiThread(() -> Toast.makeText(this, model.name + " ready", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void scanAndLoadBestModel() {
        new Thread(() -> {
            modelManager.scanForExistingModels();
            ModelManager.ModelInfo best = modelManager.getBestDownloadedModel(ModelManager.Tier.LIGHT);
            if (best != null) loadModel(best);
        }).start();
    }

    @Override
    public void onDownloadProgress(ModelManager.ModelInfo model, int progress, long downloadedBytes, long totalBytes) {}
    @Override
    public void onDownloadStarted(ModelManager.ModelInfo model) {}
    @Override
    public void onDownloadCompleted(ModelManager.ModelInfo model) {}
    @Override
    public void onDownloadFailed(ModelManager.ModelInfo model, String error) {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
        modelManager.removeProgressListener(this);
    }
}