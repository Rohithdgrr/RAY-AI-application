package com.example.offlinellm;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages;
    private EditText chatInput;
    private TextView statusIndicator;
    private Button btnSend;
    private android.widget.ImageButton btnStop;
    private volatile InferenceEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        statusIndicator = findViewById(R.id.statusIndicator);
        recyclerView = findViewById(R.id.chatRecyclerView);
        chatInput = findViewById(R.id.chatInput);
        btnSend = findViewById(R.id.btnSend);
        btnStop = findViewById(R.id.btnStop);

        findViewById(R.id.btnModels).setOnClickListener(v -> {
            ModelBottomSheet bottomSheet = new ModelBottomSheet();
            bottomSheet.setListener(model -> {
                statusIndicator.setText("Model: Switching...");
                initEngine(model);
            });
            bottomSheet.show(getSupportFragmentManager(), "ModelBottomSheet");
        });

        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        statusIndicator.setText("Model: Loading...");
        initEngine();

        btnSend.setOnClickListener(v -> sendMessage());
        btnStop.setOnClickListener(v -> {
            if (engine != null) {
                engine.stop();
                setGeneratingState(false);
            }
        });
    }

    private void setGeneratingState(boolean generating) {
        runOnUiThread(() -> {
            btnSend.setVisibility(generating ? android.view.View.GONE : android.view.View.VISIBLE);
            btnStop.setVisibility(generating ? android.view.View.VISIBLE : android.view.View.GONE);
            if (!generating) {
                chatInput.setEnabled(true);
            }
        });
    }

    private void initEngine() {
        ModelManager manager = ModelManager.getInstance(this);
        ModelManager.ModelInfo bestModel = manager.getBestDownloadedModel(ModelManager.Tier.LIGHT);
        initEngine(bestModel);
    }

    private void initEngine(ModelManager.ModelInfo model) {
        new Thread(() -> {
            try {
                if (model == null) {
                    runOnUiThread(() -> {
                        statusIndicator.setText("Model: Not found");
                        Toast.makeText(this, "Please download a model first", Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                if (engine != null) {
                    engine.unload();
                    engine = null;
                }

                File encryptedFile = new File(getFilesDir(), model.fileName);
                engine = InferenceEngine.getForFile(this, encryptedFile);
                engine.loadModel(encryptedFile);

                runOnUiThread(() -> statusIndicator.setText("Model: " + model.name));

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
        InferenceEngine currentEngine = engine;
        if (msgText.isEmpty() || currentEngine == null || !currentEngine.isLoaded()) return;

        chatInput.setText("");
        setGeneratingState(true);
        
        messages.add(new ChatMessage("You", msgText));
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);

        ChatMessage responseMessage = new ChatMessage("RAY", "");
        responseMessage.startGeneration();
        messages.add(responseMessage);
        int responseIndex = messages.size() - 1;
        adapter.notifyItemInserted(responseIndex);

        currentEngine.generate(msgText, new InferenceEngine.Callback() {
            @Override
            public void onToken(String token) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    responseMessage.setText(responseMessage.getText() + token);
                    adapter.notifyItemChanged(responseIndex);
                    recyclerView.scrollToPosition(responseIndex);
                });
            }

            @Override
            public void onThought(String thought) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    responseMessage.setThought(responseMessage.getThought() + thought);
                    adapter.notifyItemChanged(responseIndex);
                    recyclerView.scrollToPosition(responseIndex);
                });
            }

            @Override
            public void onComplete() {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    responseMessage.finishGeneration();
                    adapter.notifyItemChanged(responseIndex);
                    setGeneratingState(false);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    responseMessage.finishGeneration();
                    setGeneratingState(false);
                    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.unload();
    }
}
