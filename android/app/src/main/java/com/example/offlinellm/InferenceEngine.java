package com.example.offlinellm;

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
}
