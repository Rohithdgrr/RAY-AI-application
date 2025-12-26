# Installation Guide

This guide provides detailed instructions for building and running RAY AI from source.

## 📋 Prerequisites

Ensure you have the following installed on your development machine:

- **Android Studio Koala (2024.1.1)** or newer.
- **Android SDK**: API Level 34 (Android 14) recommended.
- **Android NDK**: Version 26.1.10909125 or newer.
- **CMake**: 3.22.1 or newer.
- **Git**: For cloning the repository and managing submodules.
- **Physical Device**: A device with `arm64-v8a` architecture is highly recommended for performance. Emulators are significantly slower for AI inference.

## 🛠 Building the Project

### 1. Clone the Repository
Clone the repository and its submodules (important for `llama.cpp`):
```bash
git clone --recursive https://github.com/Rohithdgrr/working-RAY-AI.git
cd working-RAY-AI
```

### 2. Open in Android Studio
- Launch Android Studio and select **Open**.
- Navigate to the `working-RAY-AI/android` folder and click **OK**.
- Wait for Gradle to sync. This may take a few minutes as it downloads dependencies.

### 3. Configure Native Build
The project uses CMake to build the `llama.cpp` native library.
- Go to **Tools > SDK Manager**.
- Under **SDK Tools**, ensure **NDK (Side by side)** and **CMake** are checked and installed.

### 4. Build the APK
- Connect your Android device via USB.
- Ensure **Developer Options** and **USB Debugging** are enabled on your device.
- Select your device in the toolbar.
- Click the **Run** button (green play icon) or go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

## ⚠️ Common Issues & Troubleshooting

### Library Loading Error
If you see "Model: Error" in the toolbar or a crash on startup:
- Ensure the native library (`libllama-jni.so`) was built correctly.
- Check the **Build** tab in Android Studio for any C++ compilation errors.
- Ensure you are running on a supported ABI (`arm64-v8a` or `x86_64`).

### Gradle Sync Failures
- Verify your internet connection.
- Ensure you are using the correct Gradle version (defined in `gradle-wrapper.properties`).
- Try **File > Invalidate Caches / Restart**.

### Slow Inference
- AI inference is CPU/GPU intensive. Ensure your device is not in power-saving mode.
- Use **GGUF** models with 4-bit quantization (`Q4_K_M`) for the best balance of speed and quality.
