package com.example.offlinellm;

import android.content.Context;
import android.util.Log;
import java.io.File;

public class LlamaInference implements InferenceEngine {
    private static final String TAG = "LlamaInference";

    static {
        System.loadLibrary("llama-jni");
    }

    private long contextPointer = 0;
    private File tempDecryptedFile = null;
    private Context context;

    public LlamaInference(Context context) {
        this.context = context;
    }

    @Override
    public void loadModel(File encryptedModelFile) throws Exception {
        // RAM Preflight check
        long fileSize = encryptedModelFile.length();
        long requiredRam = (long) (fileSize * 1.5);
        long availableRam = ModelManager.getInstance(context).getAvailableRamMb() * 1024L * 1024L;
        
        if (availableRam < requiredRam) {
            throw new Exception("Insufficient RAM: Need ~" + (requiredRam/1024/1024) + "MB, Available: " + (availableRam/1024/1024) + "MB");
        }

        // Decrypt to temp file
        tempDecryptedFile = new File(context.getCacheDir(), "temp_model_" + System.currentTimeMillis() + ".gguf");
        SecurityHelper.decryptFile(context, encryptedModelFile, tempDecryptedFile);

        contextPointer = nativeInit(tempDecryptedFile.getAbsolutePath());
        
        // After loading into memory via mmap, we can technically delete the temp file 
        // if mmap implementation in llama.cpp allows it (usually it does on Linux/Android after open)
        // but for safety we cleanup on unload or app close as per requirement.
        
        if (contextPointer == 0) {
            cleanupTempFile();
            throw new Exception("Failed to initialize llama model");
        }
    }

    private void cleanupTempFile() {
        if (tempDecryptedFile != null && tempDecryptedFile.exists()) {
            tempDecryptedFile.delete();
            tempDecryptedFile = null;
        }
    }

    private boolean isGenerating = false;

    @Override
    public void generate(String prompt, Callback callback) {
        if (contextPointer == 0) {
            callback.onError("Model not loaded");
            return;
        }
        if (isGenerating) {
            callback.onError("Already busy");
            return;
        }
        isGenerating = true;
        new Thread(() -> {
            try {
                nativeGenerate(contextPointer, prompt, new NativeCallback() {
                    @Override
                    public void onToken(String token) {
                        callback.onToken(token);
                    }

                    @Override
                    public void onComplete() {
                        isGenerating = false;
                        callback.onComplete();
                    }
                });
            } catch (Exception e) {
                isGenerating = false;
                callback.onError(e.getMessage());
            }
        }).start();
    }

    @Override
    public void unload() {
        if (contextPointer != 0) {
            nativeFree(contextPointer);
            contextPointer = 0;
        }
        cleanupTempFile();
    }

    @Override
    public boolean isLoaded() {
        return contextPointer != 0;
    }

    // JNI Methods
    private native long nativeInit(String modelPath);
    private native void nativeGenerate(long ptr, String prompt, NativeCallback cb);
    private native void nativeFree(long ptr);

    public interface NativeCallback {
        void onToken(String token);
        void onComplete();
    }
}
