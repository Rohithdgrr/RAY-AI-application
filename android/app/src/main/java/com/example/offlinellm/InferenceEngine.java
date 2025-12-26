package com.example.offlinellm;

import android.content.Context;
import java.io.File;

public interface InferenceEngine {
    interface Callback {
        void onToken(String token);
        default void onThought(String thought) {}
        default void onStatus(String status) {}
        void onComplete();
        void onError(String message);
    }

    void loadModel(File modelFile) throws Exception;
    void generate(String prompt, Callback callback);
    void stop();
    void unload();
    boolean isLoaded();
    default void clearHistory() {}

    static InferenceEngine getForFile(Context context, File file) {
        String name = file.getName().toLowerCase();
        if (name.contains(".onnx")) {
            return new OnnxInference(context);
        } else {
            return new LlamaInference(context);
        }
    }
}
