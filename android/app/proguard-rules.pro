-keep class com.example.offlinellm.LlamaInference$NativeCallback { *; }
-keep class com.example.offlinellm.LlamaInference {
    native <methods>;
}
-keep class ai.onnxruntime.** { *; }
