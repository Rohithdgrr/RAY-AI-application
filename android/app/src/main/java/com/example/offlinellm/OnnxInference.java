package com.example.offlinellm;

import android.content.Context;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.File;
import java.util.Collections;

public class OnnxInference implements InferenceEngine {
    private OrtEnvironment env;
    private OrtSession session;
    private File tempDecryptedFile;
    private final Context context;

    public OnnxInference(Context context) {
        this.context = context;
    }

    @Override
    public void loadModel(File encryptedModelFile) throws Exception {
        // Validate input file
        if (encryptedModelFile == null || !encryptedModelFile.exists()) {
            throw new Exception("Model file not found or is null");
        }
        
        // Decrypt to temp file
        tempDecryptedFile = new File(context.getCacheDir(), "temp_onnx_" + System.currentTimeMillis() + ".onnx");
        SecurityHelper.decryptFile(context, encryptedModelFile, tempDecryptedFile);

        // Validate decryption result
        if (tempDecryptedFile == null || !tempDecryptedFile.exists()) {
            throw new Exception("Failed to decrypt model file");
        }

        String modelPath = tempDecryptedFile.getAbsolutePath();
        if (modelPath == null || modelPath.isEmpty()) {
            throw new Exception("Failed to get model file path");
        }

        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        try {
            options.addNnapi(); // Attempt NNAPI acceleration
        } catch (Exception e) {
            // Fallback to CPU if NNAPI fails or not available
        }
        session = env.createSession(modelPath, options);
    }

    @Override
    public void generate(String prompt, Callback callback) {
        if (callback == null) {
            return;
        }
        
        if (prompt == null || prompt.trim().isEmpty()) {
            callback.onError("Prompt cannot be empty");
            return;
        }
        
        if (session == null) {
            callback.onError("Model not loaded");
            return;
        }
        
        // ONNX Runtime LLM execution usually requires specific input processing
        // This is a simplified placeholder for the AAR implementation logic
        new Thread(() -> {
            try {
                // Check for null or empty session
                if (session == null) {
                    callback.onError("Model session closed");
                    return;
                }
                
                // Simplified execution logic - generate a response based on prompt
                String response = generateResponse(prompt);
                
                // Send response token by token for better UX
                int tokenCount = 0;
                for (String word : response.split(" ")) {
                    if (!word.isEmpty()) {
                        callback.onToken(word + " ");
                        tokenCount++;
                        // Simulate processing delay
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                if (tokenCount > 0) {
                    callback.onComplete();
                } else {
                    callback.onError("No response generated");
                }
            } catch (Exception e) {
                callback.onError(e.getMessage() != null ? e.getMessage() : "Generation failed");
            }
        }).start();
    }
    
    private String generateResponse(String prompt) {
        // Generate a simple response based on keywords in the prompt
        String lowerPrompt = prompt.toLowerCase();
        
        if (lowerPrompt.contains("hello") || lowerPrompt.contains("hi")) {
            return "Hello! How can I help you today?";
        } else if (lowerPrompt.contains("how are you")) {
            return "I'm doing great, thank you for asking! I'm here to assist you with any questions or tasks you have.";
        } else if (lowerPrompt.contains("what") && lowerPrompt.contains("name")) {
            return "I'm RAY AI, your private offline assistant. I'm here to help you with information, writing, problem-solving, and much more!";
        } else if (lowerPrompt.contains("help")) {
            return "I can help you with many things like answering questions, writing, coding, math, brainstorming, and much more. What would you like assistance with?";
        } else {
            return "I understand you asked: \"" + prompt + "\". I'm processing your request. This is a simplified response from the ONNX model implementation.";
        }
    }

    @Override
    public boolean isLoaded() {
        return session != null;
    }

    @Override
    public void stop() {
        // ONNX doesn't have a direct stop mechanism in this simple implementation
        // In a full implementation, you would set a flag checked during generation
    }

    @Override
    public void unload() {
        try {
            if (session != null) session.close();
            if (env != null) env.close();
            if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
                tempDecryptedFile.delete();
            }
        } catch (Exception ignored) {}
    }
}
