package com.example.offlinellm;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.util.Log;
import android.widget.TextView;
import com.google.android.material.snackbar.Snackbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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
    private CoordinatorLayout rootContainer;
    private TextView modelNameText;
    private BottomNavigationView bottomNav;
    private DrawerLayout drawerLayout;
    private RecyclerView drawerHistoryRecycler;
    private DrawerHistoryAdapter drawerHistoryAdapter;
    private String currentModelName = "Not loaded";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        drawerLayout = findViewById(R.id.drawerLayout);
        rootContainer = findViewById(R.id.rootContainer);

        toolbarTitle = findViewById(R.id.toolbarTitle);
        modelNameText = findViewById(R.id.modelNameText);
        bottomNav = findViewById(R.id.bottom_navigation);

        modelManager = ModelManager.getInstance(this);
        modelManager.addProgressListener(this);
        historyManager = ChatHistoryManager.getInstance(this);

        // Setup drawer
        setupDrawer();

        bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                showFragment(new HomeFragment(), "Home");
                return true;
            } else if (id == R.id.nav_models) {
                showFragment(new ModelsFragment(), "Models");
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
        findViewById(R.id.btnQuickActions).setOnClickListener(v -> toggleDrawer());
    }

    private void setupDrawer() {
        // New Chat button in drawer
        MaterialButton drawerNewChat = findViewById(R.id.drawerNewChat);
        if (drawerNewChat != null) {
            drawerNewChat.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                createNewChat();
            });
        }

        // View All History button
        MaterialButton drawerViewAllHistory = findViewById(R.id.drawerViewAllHistory);
        if (drawerViewAllHistory != null) {
            drawerViewAllHistory.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                startActivity(new Intent(this, ChatHistoryActivity.class));
            });
        }

        // Setup recent chats RecyclerView
        drawerHistoryRecycler = findViewById(R.id.drawerHistoryRecycler);
        if (drawerHistoryRecycler != null) {
            drawerHistoryRecycler.setLayoutManager(new LinearLayoutManager(this));
            drawerHistoryAdapter = new DrawerHistoryAdapter(new ArrayList<>(), session -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                historyManager.setActiveSession(session.getId());
                showHome();
            });
            drawerHistoryRecycler.setAdapter(drawerHistoryAdapter);
            loadRecentChats();
        }
    }

    private void loadRecentChats() {
        if (drawerHistoryAdapter != null) {
            List<ChatSession> sessions = historyManager.getActiveSessions();
            // Limit to 10 recent chats for the drawer
            List<ChatSession> recentSessions = sessions.size() > 10 ? 
                sessions.subList(0, 10) : sessions;
            drawerHistoryAdapter.updateSessions(recentSessions);
        }
    }

    private void toggleDrawer() {
        if (drawerLayout == null) {
            Log.e("MainActivity", "drawerLayout is null!");
            return;
        }
        
        try {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                loadRecentChats(); // Refresh recent chats before opening
                drawerLayout.openDrawer(GravityCompat.START);
                Log.d("MainActivity", "Drawer opened");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error toggling drawer", e);
        }
    }

    private void showFragment(Fragment fragment, String title) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        if (toolbarTitle != null) {
            toolbarTitle.setText(title.equals("Home") ? "RAY AI" : title);
        }
    }

    public void showHome() {
        bottomNav.setSelectedItemId(R.id.nav_home);
    }

    private void createNewChat() {
        historyManager.createNewSession();
        showHome(); // Refresh home with new context
        showSnack("New chat started");
        loadRecentChats(); // Update drawer
    }

    public boolean handleChatMessage(String prompt, List<ChatMessage> messages, ChatAdapter adapter, RecyclerView recyclerView) {
        if (engine == null || !engine.isLoaded()) {
            showSnack("Model not ready. Please go to Models to download/load one.");
            return false;
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
                    
                    // Notify HomeFragment if it's currently active
                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
                    if (fragment instanceof HomeFragment) {
                        ((HomeFragment) fragment).onGenerationComplete();
                    }
                    
                    loadRecentChats(); // Update drawer with new chat content
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    showSnack("Error: " + message);
                    responseMessage.setText("Error: " + message);
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

        engine.generate(prompt, currentGenerationCallback);
        return true;
    }

    public void loadModel(ModelManager.ModelInfo model) {
        if (engine != null) engine.unload();
        new Thread(() -> {
            try {
                File modelFile = new File(getFilesDir(), model.fileName);
                engine = InferenceEngine.getForFile(this, modelFile);
                engine.loadModel(modelFile);
                currentModelName = model.name;
                runOnUiThread(() -> {
                    if (modelNameText != null) {
                        modelNameText.setText("Model: " + model.name);
                    }
                    showSnack(model.name + " ready");
                });
            } catch (Exception e) {
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
            Snackbar.make(rootContainer, message, Snackbar.LENGTH_LONG).show();
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
        loadRecentChats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
        modelManager.removeProgressListener(this);
    }
}