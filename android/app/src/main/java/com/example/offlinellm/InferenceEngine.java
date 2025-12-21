package com.example.offlinellm;

import android.content.Context;
import java.io.File;

public interface InferenceEngine {
    interface Callback {
        void onToken(String token);
        void onComplete();
        void onError(String message);
    }

    void loadModel(File modelFile) throws Exception;
    void generate(String prompt, Callback callback);
    void unload();
    boolean isLoaded();

    static InferenceEngine getForFile(Context context, File file) {
        String name = file.getName().toLowerCase();
        if (name.contains(".onnx")) {
            return new OnnxInference(context);
        } else {
            return new LlamaInference(context);
        }
    }
}
