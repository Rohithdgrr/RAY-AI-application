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
        // Decrypt to temp file
        tempDecryptedFile = new File(context.getCacheDir(), "temp_onnx_" + System.currentTimeMillis() + ".onnx");
        SecurityHelper.decryptFile(context, encryptedModelFile, tempDecryptedFile);

        env = OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        try {
            options.addNnapi(); // Attempt NNAPI acceleration
        } catch (Exception e) {
            // Fallback to CPU if NNAPI fails or not available
        }
        session = env.createSession(tempDecryptedFile.getAbsolutePath(), options);
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
