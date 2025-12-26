![RAY AI LOGO](https://github.com/user-attachments/assets/b3a1eec3-6933-4b69-8203-219da94ec844) 



# RAY AI - Offline AI Inference for Android

RAY AI is a high-performance, private, and secure offline AI assistant for Android. It leverages `llama.cpp` and `ONNX Runtime` to provide state-of-the-art LLM inference directly on your device without requiring an internet connection.

## 🚀 Features

- **100% Offline Inference**: No data leaves your device. Private by design.
- **llama.cpp Integration**: High-performance C++ backend optimized for mobile ARM processors.
- **Dual Engine Support**: Supports both GGUF (via llama.cpp) and ONNX models.
- **Modern Copilot UI**: Commercial-grade Material 3 design with smooth animations.
- **Model Management**: Download and manage lightweight models optimized for mobile (TinyLlama, Qwen, etc.).
- **Real-time Streaming**: Watch responses generate token-by-token.
- **Secure Storage**: Models and sensitive data are protected with hardware-backed encryption.

## 🛠 Tech Stack

- **Language**: Java / C++ (JNI)
- **UI Framework**: Android Material Components 3
- **Inference Engines**: 
  - [llama.cpp](https://github.com/ggerganov/llama.cpp) (Native C++)
  - [ONNX Runtime](https://onnxruntime.ai/)
- **Build System**: CMake & Gradle
- **Min SDK**: Android 24 (Nougat)

## 📥 Getting Started

### Prerequisites

- Android Studio Koala or newer
- Android NDK 26.1.10909125+
- CMake 3.22.1+

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/Rohithdgrr/working-RAY-AI.git
   ```
2. Open the project in Android Studio.
3. Sync project with Gradle files.
4. Build and run on a physical device (arm64-v8a recommended).

## 📄 Documentation

- [Architecture](./docs/ARCHITECTURE.md) - Deep dive into JNI and engine integration.
- [Installation Guide](./docs/INSTALLATION.md) - Detailed build instructions and troubleshooting.
- [User Guide](./docs/USER_GUIDE.md) - How to use the app and manage models.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
