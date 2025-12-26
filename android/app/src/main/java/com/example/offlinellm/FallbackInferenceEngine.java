package com.example.offlinellm;

import android.content.Context;
import android.text.TextUtils;

import java.io.File;

/**
 * Lightweight rule-based responder used when no on-device model
 * has been downloaded yet. Keeps the chat experience functional
 * until the user loads a real model.
 */
public class FallbackInferenceEngine implements InferenceEngine {

    private final Context context;
    private volatile boolean loaded;
    private volatile boolean stopRequested;

    public FallbackInferenceEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void loadModel(File modelFile) {
        loaded = true;
    }

    @Override
    public void generate(String prompt, Callback callback) {
        if (callback == null) return;

        if (!loaded) {
            callback.onError("Fallback responder not initialized");
            return;
        }

        if (TextUtils.isEmpty(prompt)) {
            callback.onError("Please enter a prompt");
            return;
        }

        stopRequested = false;

        new Thread(() -> {
            try {
                String response = buildResponse(prompt);
                String[] tokens = response.split(" ");
                for (String token : tokens) {
                    if (stopRequested) {
                        callback.onError("Generation stopped");
                        return;
                    }
                    callback.onToken(token + " ");
                    try {
                        Thread.sleep(45);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
                callback.onComplete();
            } catch (Exception e) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "Fallback failed");
            }
        }).start();
    }

    private String buildResponse(String prompt) {
        String lower = prompt.toLowerCase();
        
        // Check for model/inference related questions
        if (lower.contains("model") || lower.contains("download") || lower.contains("offline")) {
            return "RAY AI works completely offline! All models run locally on your device. Go to the Models tab to download lightweight models that don't require internet connection.";
        }
        
        // Check for creator questions
        if (lower.contains("who") && lower.contains("rot")) {
            return "RAY AI was crafted with care by ROT. This is a fully offline AI assistant that runs models locally on your device without any internet connection required.";
        }
        
        // Greeting patterns
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "Hello! I'm RAY AI, your offline assistant. I work completely without internet. Download models from the Models tab for more advanced conversations!";
        }
        
        // How it works questions
        if (lower.contains("how") && (lower.contains("work") || lower.contains("function"))) {
            return "RAY AI runs AI models entirely offline on your device. No internet connection needed! Just download a model from the Models tab and start chatting privately.";
        }
        
        // Help requests
        if (lower.contains("help") || lower.contains("assist")) {
            return "I'm here to help! I can answer questions and have conversations completely offline. For better responses, download a model from the Models tab. What would you like to know?";
        }
        
        // Internet/offline related questions
        if (lower.contains("internet") || lower.contains("online") || lower.contains("connection")) {
            return "RAY AI works completely offline! No internet connection required. All AI processing happens locally on your device for maximum privacy and speed.";
        }
        
        // Privacy questions
        if (lower.contains("privacy") || lower.contains("private") || lower.contains("secure")) {
            return "Your privacy is protected! RAY AI processes everything locally on your device. No data is sent to external servers - it's 100% offline and private.";
        }
        
        // Default response with offline emphasis
        return "I'm RAY AI, your offline assistant. I can help you without any internet connection. For more detailed conversations, download a model from the Models tab. How can I assist you today?";
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    @Override
    public void unload() {
        stopRequested = true;
        loaded = false;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }
}
