package com.example.offlinellm;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages;
    private EditText chatInput;
    private TextView statusIndicator;
    private InferenceEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        statusIndicator = findViewById(R.id.statusIndicator);
        recyclerView = findViewById(R.id.chatRecyclerView);
        chatInput = findViewById(R.id.chatInput);
        FloatingActionButton btnSend = findViewById(R.id.btnSend);

        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        statusIndicator.setText("Model: Loading...");
        initEngine(null); // Load default or best model

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void initEngine(ModelManager.ModelInfo specificModel) {
        if (engine != null) {
            engine.unload();
            engine = null;
        }

        new Thread(() -> {
            try {
                ModelManager manager = ModelManager.getInstance(this);
                ModelManager.ModelInfo modelToLoad = specificModel;
                
                if (modelToLoad == null) {
                    modelToLoad = manager.getBestDownloadedModel(ModelManager.Tier.LIGHT);
                }
                
                if (modelToLoad == null) {
                    runOnUiThread(() -> statusIndicator.setText("Model: None downloaded"));
                    return;
                }

                File encryptedFile = new File(getFilesDir(), modelToLoad.fileName);
                engine = InferenceEngine.getForFile(this, encryptedFile);
                engine.loadModel(encryptedFile);

                ModelManager.ModelInfo finalModel = modelToLoad;
                runOnUiThread(() -> statusIndicator.setText("Model: " + finalModel.name));

            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusIndicator.setText("Model: Error");
                    Toast.makeText(this, "Load failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void sendMessage() {
        String msgText = chatInput.getText().toString().trim();
        if (msgText.isEmpty()) return;
        
        if (engine == null || !engine.isLoaded()) {
            Toast.makeText(this, "Model not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        chatInput.setText("");
        ChatMessage userMessage = new ChatMessage("You", msgText);
        messages.add(userMessage);
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);

        ChatMessage responseMessage = new ChatMessage("RAY", "");
        messages.add(responseMessage);
        int responseIndex = messages.size() - 1;
        adapter.notifyItemInserted(responseIndex);

        try {
            engine.generate(msgText, new InferenceEngine.Callback() {
                @Override
                public void onToken(String token) {
                    runOnUiThread(() -> {
                        if (responseMessage != null && messages.contains(responseMessage)) {
                            responseMessage.setText(responseMessage.getText() + token);
                            adapter.notifyItemChanged(responseIndex);
                            recyclerView.scrollToPosition(responseIndex);
                        }
                    });
                }

                @Override
                public void onComplete() {
                    runOnUiThread(() -> {
                        // Optional: Add completion indicator
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Generation error: " + message, Toast.LENGTH_SHORT).show();
                        if (messages.contains(responseMessage)) {
                            responseMessage.setText("Error: " + message);
                            adapter.notifyItemChanged(responseIndex);
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Unexpected error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showModelSheet() {
        ModelBottomSheet sheet = new ModelBottomSheet();
        sheet.setListener(this::initEngine);
        sheet.show(getSupportFragmentManager(), "ModelSheet");
    }

    private void clearChat() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Clear Chat")
                .setMessage("Are you sure you want to clear all messages?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    messages.clear();
                    adapter.notifyDataSetChanged();
                    Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_models) {
            showModelSheet();
            return true;
        } else if (item.getItemId() == R.id.action_clear) {
            clearChat();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
    }
}
