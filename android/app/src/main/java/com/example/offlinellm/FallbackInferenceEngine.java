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
        if (lower.contains("who") && lower.contains("rot")) {
            return "RAY AI was crafted with care by ROT. This fallback assistant is standing in until you load a full model.";
        }
        if (lower.contains("hello") || lower.contains("hi")) {
            return "Hello! I am the built-in RAY AI helper. Load a downloaded model from the Models tab for richer answers, but I can still assist with quick info.";
        }
        if (lower.contains("how") && lower.contains("work")) {
            return "RAY AI runs models fully offline on your device. Visit the Models tab, download your preferred tier, and tap Use Model to switch from this demo responder to the real engine.";
        }
        if (lower.contains("help")) {
            return "Sure, let me know what you need help with. I can answer quick questions while you prepare a full model.";
        }
        return "You asked: \"" + prompt + "\". I'm the quick offline fallback so answers stay instant even before a model is loaded.";
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
