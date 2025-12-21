# Building llama.cpp for Android

To build the full native part of this application, follow these steps:

1. Clone llama.cpp into `android/app/src/main/cpp/llama.cpp`.
2. Ensure you have the Android NDK installed.
3. Update `android/app/src/main/cpp/CMakeLists.txt` to include `add_subdirectory(llama.cpp)`.
4. Run the build in Android Studio.

## Optimization Notes
- The current JNI wrapper is a high-level skeleton. In production, use `llama_tokenize`, `llama_eval`, and `llama_sample` within `nativeGenerate`.
- Enable mmap and specify the correct number of threads based on `cpu_cores`.
- For NNAPI support in ONNX, ensure the device is API level 27+.
