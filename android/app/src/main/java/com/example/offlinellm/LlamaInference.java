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
        // Validate input file
        if (encryptedModelFile == null || !encryptedModelFile.exists()) {
            throw new Exception("Model file not found or is null");
        }
        
        // Validate file size (must be > 20MB to be a real model, not a 404 page)
        long fileSize = encryptedModelFile.length();
        if (fileSize < 20 * 1024 * 1024) {
            throw new Exception("Model file too small (" + (fileSize/1024) + "KB). Please re-download the model.");
        }
        
        // RAM Preflight check
        long requiredRam = (long) (fileSize * 1.5);
        long availableRam = ModelManager.getInstance(context).getAvailableRamMb() * 1024L * 1024L;
        
        if (availableRam < requiredRam) {
            throw new Exception("Insufficient RAM: Need ~" + (requiredRam/1024/1024) + "MB, Available: " + (availableRam/1024/1024) + "MB");
        }

        // Decrypt to temp file
        tempDecryptedFile = new File(context.getCacheDir(), "temp_model_" + System.currentTimeMillis() + ".gguf");
        SecurityHelper.decryptFile(context, encryptedModelFile, tempDecryptedFile);

        // Validate decryption result
        if (tempDecryptedFile == null || !tempDecryptedFile.exists()) {
            throw new Exception("Failed to decrypt model file");
        }

        String modelPath = tempDecryptedFile.getAbsolutePath();
        if (modelPath == null || modelPath.isEmpty()) {
            throw new Exception("Failed to get model file path");
        }

        contextPointer = nativeInit(modelPath);
        
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

    private final Object lock = new Object();
    private boolean isGenerating = false;
    private volatile boolean stopRequested = false;

    @Override
    public void generate(String prompt, Callback callback) {
        synchronized (lock) {
            if (contextPointer == 0) {
                callback.onError("Model not loaded");
                return;
            }
            if (isGenerating) {
                callback.onError("Already busy");
                return;
            }
            isGenerating = true;
            stopRequested = false;
        }

        new Thread(() -> {
            try {
                long currentPtr;
                synchronized (lock) {
                    currentPtr = contextPointer;
                    if (currentPtr == 0) {
                        isGenerating = false;
                        callback.onError("Model not loaded");
                        return;
                    }
                }
                
                nativeGenerate(currentPtr, prompt, new NativeCallback() {
                    @Override
                    public void onToken(String token) {
                        if (!stopRequested) {
                            callback.onToken(token);
                        }
                    }

                    @Override
                    public void onComplete() {
                        synchronized (lock) {
                            isGenerating = false;
                        }
                        if (!stopRequested) {
                            callback.onComplete();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Generation failed", e);
                callback.onError(e.getMessage());
            } finally {
                synchronized (lock) {
                    isGenerating = false;
                    stopRequested = false;
                }
            }
        }).start();
    }

    @Override
    public void stop() {
        synchronized (lock) {
            stopRequested = true;
            isGenerating = false;
        }
    }

    @Override
    public void unload() {
        synchronized (lock) {
            if (contextPointer != 0) {
                nativeFree(contextPointer);
                contextPointer = 0;
            }
            isGenerating = false;
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
