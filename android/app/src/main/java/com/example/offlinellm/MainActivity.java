package com.example.offlinellm;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.widget.TextView;
import android.widget.ImageButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.offlinellm.HomeFragment;
import com.example.offlinellm.ModelsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ModelManager.DownloadProgressListener {
    private InferenceEngine engine;
    private ModelManager modelManager;
    private ChatHistoryManager historyManager;
    private boolean isGenerating = false;
    private InferenceEngine.Callback currentGenerationCallback;
    private TextView toolbarTitle;
    private androidx.appcompat.widget.Toolbar appToolbar;
    private CoordinatorLayout rootContainer;
    private TextView modelNameText;
    private BottomNavigationView bottomNav;
    private DrawerLayout drawerLayout;
    private RecyclerView drawerHistoryRecycler;
    private DrawerHistoryAdapter drawerHistoryAdapter;
    private String currentModelName = "Not loaded";
    private HomeFragment homeFragment;
    private ModelsFragment modelsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        rootContainer = findViewById(R.id.rootContainer);

        appToolbar = findViewById(R.id.toolbar);
        toolbarTitle = findViewById(R.id.toolbarTitle);
        modelNameText = findViewById(R.id.modelNameText);
        ImageButton btnNewChat = findViewById(R.id.btnNewChat);
        View btnInfo = findViewById(R.id.btnInfo);
        bottomNav = findViewById(R.id.bottom_navigation);

        modelManager = ModelManager.getInstance(this);
        modelManager.addProgressListener(this);
        historyManager = ChatHistoryManager.getInstance(this);

        // Initialize fallback engine so responses work even without a downloaded model
        engine = new FallbackInferenceEngine(this);
        try {
            engine.loadModel(null);
        } catch (Exception ignored) {}

        homeFragment = new HomeFragment();
        modelsFragment = new ModelsFragment();

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                switchFragment(homeFragment, "RAY AI");
                return true;
            } else if (id == R.id.nav_models) {
                switchFragment(modelsFragment, "Models");
                return true;
            }
            return false;
        });

        // Load default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, modelsFragment, "Models").hide(modelsFragment)
                    .add(R.id.fragment_container, homeFragment, "Home")
                    .commit();
            activeFragment = homeFragment;
            if (toolbarTitle != null) toolbarTitle.setText("RAY AI");
        }

        scanAndLoadBestModel();
        
        if (btnNewChat != null) btnNewChat.setOnClickListener(v -> createNewChat());
        if (btnInfo != null) btnInfo.setOnClickListener(v -> showInfoDialog());
    }

    private void showInfoDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("About RAY AI")
                .setMessage("RAY AI ORCHID v1.0\nCreated by ROT\n\nA professional offline AI assistant running locally on your device.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void switchFragment(Fragment fragment, String title) {
        if (activeFragment == fragment) return;
        
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(fragment)
                .commit();
        activeFragment = fragment;
        
        if (toolbarTitle != null) {
            toolbarTitle.setText(title);
        }
    }

    public void showHome() {
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    private void createNewChat() {
        historyManager.createNewSession();
        if (engine != null) {
            engine.clearHistory();
        }
        // Refresh home fragment data
        if (homeFragment != null) {
            homeFragment.loadActiveSession();
        }
        showHome();
        showSnack("New chat started");
    }

    public boolean handleChatMessage(String prompt, List<ChatMessage> messages, ChatAdapter adapter, RecyclerView recyclerView) {
        if (prompt == null || prompt.trim().isEmpty()) {
            showSnack("Please enter a message");
            return false;
        }

        ChatMessage responseMessage = new ChatMessage("RAY", "", "Thinking...");
        responseMessage.startGeneration();
        messages.add(responseMessage);
        int responseIndex = messages.size() - 1;
        adapter.notifyItemInserted(responseIndex);
        recyclerView.scrollToPosition(responseIndex);

        isGenerating = true;

        new Thread(() -> {
            // Check if engine needs loading
            if (engine == null || !engine.isLoaded()) {
                ModelManager.ModelInfo bestModel = modelManager.getBestDownloadedModel(ModelManager.Tier.ULTRA_LIGHT);
                if (bestModel != null) {
                    try {
                        File modelFile = new File(getFilesDir(), bestModel.fileName);
                        InferenceEngine newEngine = InferenceEngine.getForFile(this, modelFile);
                        newEngine.loadModel(modelFile);
                        engine = newEngine;
                        runOnUiThread(() -> {
                            modelNameText.setText("Model: " + bestModel.name);
                            currentModelName = bestModel.name;
                        });
                    } catch (Exception e) {
                        Log.e("MainActivity", "Auto-load failed", e);
                    }
                }
                
                if (engine == null || !engine.isLoaded()) {
                    try {
                        engine = new FallbackInferenceEngine(this);
                        engine.loadModel(null);
                    } catch (Exception ignored) {}
                }
            }

            if (engine == null || !engine.isLoaded()) {
                runOnUiThread(() -> {
                    showSnack("Engine failed to initialize");
                    messages.remove(responseIndex);
                    adapter.notifyItemRemoved(responseIndex);
                    isGenerating = false;
                });
                return;
            }

            currentGenerationCallback = new InferenceEngine.Callback() {
            private boolean isFirstToken = true;

            @Override
            public void onToken(String token) {
                if (token != null) {
                    runOnUiThread(() -> {
                        if (isFirstToken) {
                            responseMessage.setText(token);
                            isFirstToken = false;
                        } else {
                            responseMessage.setText(responseMessage.getText() + token);
                        }
                        adapter.notifyItemChanged(responseIndex);
                        recyclerView.scrollToPosition(responseIndex);
                    });
                }
            }

            @Override
            public void onThought(String thought) {
                if (thought != null) {
                    runOnUiThread(() -> {
                        responseMessage.setThought(responseMessage.getThought() + thought);
                        adapter.notifyItemChanged(responseIndex);
                    });
                }
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
                    
                    // Notify HomeFragment if it's currently active
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (fragment instanceof HomeFragment) {
                        ((HomeFragment) fragment).onGenerationComplete();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    String errorMsg = message != null ? message : "Unknown error";
                    showSnack("Error: " + errorMsg);
                    responseMessage.setText("Error: " + errorMsg);
                    responseMessage.finishGeneration();
                    adapter.notifyItemChanged(responseIndex);
                    isGenerating = false;
                    
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (fragment instanceof HomeFragment) {
                        ((HomeFragment) fragment).onGenerationComplete();
                    }
                });
            }
        };

        try {
            engine.generate(prompt, currentGenerationCallback);
        } catch (Exception e) {
            isGenerating = false;
            showSnack("Generation failed: " + e.getMessage());
            messages.remove(responseIndex);
            adapter.notifyItemRemoved(responseIndex);
            return;
        }
    }).start();
    return true;
}

    public void loadModel(ModelManager.ModelInfo model) {
        new Thread(() -> {
            try {
                File modelFile = new File(getFilesDir(), model.fileName);
                InferenceEngine newEngine = InferenceEngine.getForFile(this, modelFile);
                newEngine.loadModel(modelFile);
                
                // Only swap engine after successful load
                if (engine != null) engine.unload();
                engine = newEngine;
                
                currentModelName = model.name;
                runOnUiThread(() -> {
                    if (modelNameText != null) {
                        modelNameText.setText("Model: " + model.name);
                    }
                    showSnack(model.name + " ready");
                });
            } catch (Exception e) {
                // Keep existing engine if possible, otherwise fallback
                if (engine == null || !engine.isLoaded()) {
                    try {
                        engine = new FallbackInferenceEngine(this);
                        engine.loadModel(null);
                    } catch (Exception ignored) {}
                }

                currentModelName = "Error";
                runOnUiThread(() -> {
                    if (modelNameText != null) {
                        modelNameText.setText("Model: Error");
                    }
                    showSnack("Load failed: " + e.getMessage());
                });
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

    public void stopGeneration() {
        if (isGenerating && engine != null) {
            engine.stop();
            isGenerating = false;
            
            // Notify the HomeFragment
            Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (fragment instanceof HomeFragment) {
                ((HomeFragment) fragment).onGenerationComplete();
            }
        }
    }

    private void showSnack(String message) {
        if (rootContainer != null) {
            com.google.android.material.snackbar.Snackbar sb = com.google.android.material.snackbar.Snackbar
                    .make(rootContainer, message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                    .setAnimationMode(com.google.android.material.snackbar.Snackbar.ANIMATION_MODE_SLIDE);
            if (appToolbar != null) sb.setAnchorView(appToolbar);
            sb.setBackgroundTint(getColor(R.color.surface_elevated));
            sb.setTextColor(getColor(R.color.text_primary));
            sb.show();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
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
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
        modelManager.removeProgressListener(this);
    }
}