<img width="1024" height="512" alt="image" src="https://github.com/user-attachments/assets/6658951a-f77c-4a1c-8cb6-298c17b7e78f" />



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
## Screenshots

Here’s how RAY AI looks in action:

<table>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/e8c2f098-a4f5-4c6c-99ac-7b17c989a6f3" width="100%" alt="Chat interface - Thinking phase"/></td>
    <td><img src="https://github.com/user-attachments/assets/b65004bf-e6cd-4c25-98e4-e447945d5be2" width="100%" alt="Model management screen"/></td>
  </tr>
  <tr>
    <td><img src="https://github.com/user-attachments/assets/93f8099c-99a6-45a0-8354-51e3c5e34ce0" width="100%" alt="Response starting"/></td>
    <td><img src="https://github.com/user-attachments/assets/3f4001cd-515c-4028-8265-16f2a650ae25" width="100%" alt="Full streaming response"/></td>
  </tr>
</table>

**From left to right, top to bottom:**  
Chat · Model management · Thinking → Response · Complete answer streaming



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
