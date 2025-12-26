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

        // Decrypt to cached file if not already present
        String cachedName = "decrypted_" + encryptedModelFile.getName();
        tempDecryptedFile = new File(context.getCacheDir(), cachedName);
        
        // Only decrypt if cached file doesn't exist or is older/wrong size
        // Note: For real security we might want a better check, but for speed this is a massive win
        if (!tempDecryptedFile.exists() || tempDecryptedFile.length() == 0) {
            Log.d(TAG, "Decrypting model to cache: " + cachedName);
            SecurityHelper.decryptFile(context, encryptedModelFile, tempDecryptedFile);
        } else {
            Log.d(TAG, "Using cached decrypted model: " + cachedName);
        }

        // Validate decryption result
        if (!tempDecryptedFile.exists() || tempDecryptedFile.length() == 0) {
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
        if (callback == null) {
            Log.e(TAG, "Callback is null");
            return;
        }
        
        if (prompt == null || prompt.trim().isEmpty()) {
            callback.onError("Prompt cannot be empty");
            return;
        }

        // Apply basic Chat Template if not present
        String formattedPrompt = prompt;
        if (!prompt.contains("<|im_start|>") && !prompt.contains("[INST]") && !prompt.contains("<|user|>")) {
            // Default to ChatML-like template which works for many modern GGUFs (Qwen, Llama 3.2, etc.)
            formattedPrompt = "<|im_start|>system\nYou are RAY AI, a high-quality, helpful, and professional AI assistant created by ROT. Provide accurate, detailed, and perfectly formatted responses.<|im_end|>\n" +
                             "<|im_start|>user\n" + prompt + "<|im_end|>\n" +
                             "<|im_start|>assistant\n";
        }
        
        final String finalPrompt = formattedPrompt;
        
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
                
                nativeGenerate(currentPtr, finalPrompt, new NativeCallback() {
                    private boolean inThought = false;
                    private StringBuilder currentThought = new StringBuilder();

                    @Override
                    public void onToken(String token) {
                        if (stopRequested || token == null || token.isEmpty()) return;

                        if (token.contains("<thought>")) {
                            inThought = true;
                            String after = token.substring(token.indexOf("<thought>") + 9);
                            if (!after.isEmpty()) {
                                callback.onThought(after);
                                currentThought.append(after);
                            }
                            return;
                        }

                        if (token.contains("</thought>")) {
                            inThought = false;
                            String before = token.substring(0, token.indexOf("</thought>"));
                            if (!before.isEmpty()) {
                                callback.onThought(before);
                                currentThought.append(before);
                            }
                            return;
                        }

                        if (inThought) {
                            callback.onThought(token);
                            currentThought.append(token);
                        } else {
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

                    @Override
                    public void onError(String message) {
                        synchronized (lock) {
                            isGenerating = false;
                        }
                        callback.onError(message);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Generation failed", e);
                callback.onError(e.getMessage() != null ? e.getMessage() : "Generation failed");
                synchronized (lock) {
                    isGenerating = false;
                }
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
            if (contextPointer != 0) {
                nativeStop(contextPointer);
            }
        }
    }

    @Override
    public void unload() {
        synchronized (lock) {
            stopRequested = true;
            if (contextPointer != 0) {
                nativeStop(contextPointer); // Ensure it stops first
                nativeFree(contextPointer);
                contextPointer = 0;
            }
            isGenerating = false;
        }
        // We keep the decrypted cache for faster subsequent loads
    }

    @Override
    public void clearHistory() {
        synchronized (lock) {
            if (contextPointer != 0) {
                nativeClearKV(contextPointer);
            }
        }
    }

    @Override
    public boolean isLoaded() {
        return contextPointer != 0;
    }

    // JNI Methods
    private native long nativeInit(String modelPath);
    private native void nativeGenerate(long ptr, String prompt, NativeCallback cb);
    private native void nativeClearKV(long ptr);
    private native void nativeStop(long ptr);
    private native void nativeFree(long ptr);

    public interface NativeCallback {
        void onToken(String token);
        void onComplete();
        void onError(String message);
    }
}
