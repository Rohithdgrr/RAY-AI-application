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
        if (lower.contains("model") || lower.contains("download") || lower.contains("offline") || lower.contains("work")) {
            return "Hello! I am RAY AI, currently running in MOCK mode because the native AI engine is still compiling or not yet loaded.\n\n" +
                   "This is a simulation of the offline inference process. Once the build finishes and you download a model from the 'Models' tab, I will be able to provide real on-device responses!";
        }
        
        // Greeting patterns
        if (lower.contains("hello") || lower.contains("hi") || lower.contains("hey")) {
            return "Hello! I am RAY AI, running in MOCK mode while the native engine finishes setup.\n\n" +
                   "I work completely offline once fully initialized. Please ensure you've downloaded a model from the Models tab!";
        }
        
        // Default response with status info
        return "I'm RAY AI (Offline Mode). I'm currently using my fallback engine because the high-performance native engine is still being prepared.\n\n" +
               "How can I assist you in the meantime? (Note: Download real models in the Models tab for better answers)";
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
