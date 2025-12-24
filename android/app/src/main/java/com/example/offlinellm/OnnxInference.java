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
        // ONNX Runtime LLM execution usually requires specific input processing
        // This is a simplified placeholder for the AAR implementation logic
        new Thread(() -> {
            try {
                // Simplified execution logic
                callback.onToken("ONNX response for: " + prompt);
                callback.onComplete();
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
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
