package com.example.offlinellm;

import android.util.Log;
import java.io.File;

public class LlamaInference implements InferenceEngine {
    private static final String TAG = "LlamaInference";

    static {
        System.loadLibrary("llama-jni");
    }

    private long contextPointer = 0;

    @Override
    public void loadModel(File modelFile) throws Exception {
        contextPointer = nativeInit(modelFile.getAbsolutePath());
        if (contextPointer == 0) throw new Exception("Failed to initialize llama model");
    }

    @Override
    public void generate(String prompt, Callback callback) {
        new Thread(() -> {
            nativeGenerate(contextPointer, prompt, new NativeCallback() {
                @Override
                public void onToken(String token) {
                    callback.onToken(token);
                }

                @Override
                public void onComplete() {
                    callback.onComplete();
                }
            });
        }).start();
    }

    @Override
    public void unload() {
        if (contextPointer != 0) {
            nativeFree(contextPointer);
            contextPointer = 0;
        }
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
