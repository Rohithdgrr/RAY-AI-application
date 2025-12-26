# RAY AI - Comprehensive Project Documentation

## 1. Project Overview
RAY AI is a commercial-grade, high-performance offline AI assistant for Android. It is designed to provide a private, secure, and fast LLM inference experience directly on mobile hardware. By utilizing `llama.cpp` and `ONNX Runtime`, RAY AI eliminates the need for cloud-based APIs, ensuring that user data remains strictly on-device.

### Key Value Propositions
- **Privacy First**: 100% offline inference. No data collection, no tracking, no internet required after model download.
- **High Performance**: Native C++ implementation optimized for ARMv8-A processors.
- **Modern UX**: Copilot-inspired Material 3 interface with smooth animations and intuitive controls.
- **Versatility**: Support for multiple model formats (GGUF, ONNX) and a wide range of model sizes.

---

## 2. Technical Architecture

### 2.1 Hybrid Architecture
The application follows a modular architecture separating the high-level Android UI from the low-level inference logic.

- **UI Layer (Java/XML)**: Built with Android Material Components 3, following modern design patterns.
- **Inference Service (Java)**: Orchestrates model loading, session management, and communication with native layers.
- **Native Bridge (JNI)**: A high-performance C++ layer that bridges Java calls to the underlying C++ engines.
- **Native Engines**:
    - **llama.cpp**: Primary engine for GGUF models.
    - **ONNX Runtime**: Secondary engine for specialized ONNX models.

### 2.2 Native Integration (llama.cpp)
The core of the system is the `llama.cpp` integration, which is compiled as a static library and linked into the application's native shared library (`libllama-jni.so`).

- **Optimization Flags**: Enabled NEON, DOTPROD, and I8MM instructions for ARM64.
- **Memory Management**: Uses memory mapping (mmap) for model loading to minimize RAM footprint.
- **Threading**: Intelligent thread scheduling that maps inference tasks to high-performance cores.

### 2.3 Data Flow
1. **User Input**: Input is captured in `MainActivity` and sent to `ChatAdapter`.
2. **Preprocessing**: The input is sanitized and added to the conversation history.
3. **Inference Request**: The `LlamaInference` or `OnnxInference` service is invoked.
4. **Native Execution**: The JNI layer calls the native `predict` function.
5. **Streaming Output**: Tokens are streamed back to Java via callbacks, updating the UI in real-time.

---

## 3. Core Features

### 3.1 Advanced Chat Interface
- **Real-time Streaming**: Responses appear as they are generated, providing immediate feedback.
- **Thinking State Visualization**: A dedicated "Thinking..." bubble shows the AI's reasoning process and performance metrics.
- **Code Block Support**: Syntax-highlighted code blocks with one-tap copy functionality.
- **Response Actions**: Quick actions to Copy, Regenerate, Condense (Make Short), or Expand (Make Long) responses.

### 3.2 Intelligent Model Management
- **Tier-based Categorization**: Models are ranked (Ultra Light, Light, Balanced, High Quality) based on their performance on mobile.
- **Compatibility Filtering**: Automatically filters and displays only models suitable for the current device's hardware.
- **Secure Downloads**: Models are downloaded over HTTPS and stored in encrypted internal storage.

### 3.3 Enhanced Performance Optimizations
Recent updates have introduced several cutting-edge optimizations:
- **Chunked Prompt Processing**: Prevents UI hangs during large context initialization.
- **Thread Capping**: Limits inference to 4 high-performance cores to prevent thermal throttling.
- **Flash Attention**: Native support for Flash Attention, significantly speeding up processing for long conversations.
- **Optimized Batching**: Efficient token batching for smoother streaming.

---

## 4. UI/UX Design Philosophy

### 4.1 Copilot-Inspired Design
The UI is built to feel like a premium, commercial product.
- **Color Palette**: Deep blues and vibrant accents inspired by modern AI assistants.
- **Typography**: Uses Material 3 type scales for clear hierarchy and readability.
- **Geometry**: Asymmetric rounded corners on chat bubbles for a distinct, modern look.

### 4.2 Animations and Transitions
- **Fluid Motion**: All UI changes are accompanied by 300-400ms transitions.
- **Breathing Indicators**: The "Thinking" state uses a subtle breathing animation to signal activity without being distracting.
- **Layout Transitions**: Smooth slide and scale animations for message entry and removal.

---

## 5. Security and Privacy Implementation

### 5.1 On-Device Security
- **Sandbox Environment**: All model files and chat histories are stored within the app's private directory.
- **No Analytics**: The app contains zero tracking or analytics SDKs.
- **Hardware-Backed Encryption**: Sensitive metadata is stored using the Android Keystore system.

### 5.2 Safe Inference
- **Deterministic Output**: Options for controlling temperature and top-p sampling for consistent results.
- **Local Sanitization**: Basic input/output filtering to ensure a safe user experience.

---

## 6. Build and Development Guide

### 6.1 Requirements
- **IDE**: Android Studio Koala+
- **NDK**: Version 26+
- **CMake**: 3.22.1+
- **Build System**: Gradle 8.x

### 6.2 Native Build Configuration
The native layer is configured via `CMakeLists.txt` located in `app/src/main/cpp/`.
To build for release:
```bash
cd android
./gradlew assembleRelease
```
The resulting APK will be located in `app/build/outputs/apk/release/`.

### 6.3 Adding New Models
Models must be in GGUF or ONNX format. To add a new model to the catalog:
1. Update `ModelManager.java` with the model metadata.
2. Provide a valid download URL and SHA-256 hash for verification.
3. Categorize the model into the appropriate performance tier.

---

## 7. Future Roadmap
- **Multimodal Support**: Local image and document analysis using Vision-Language Models (VLMs).
- **Voice Integration**: High-quality local TTS (Text-to-Speech) and STT (Speech-to-Text).
- **Tool Use / Function Calling**: Allowing the AI to interact with local device APIs (calendar, contacts) securely.
- **Personalization**: Local fine-tuning or RAG (Retrieval-Augmented Generation) based on user documents.

---

## 8. License and Attribution
RAY AI is licensed under the MIT License.
Includes components from:
- **llama.cpp**: (C) 2023 Georgi Gerganov
- **ONNX Runtime**: (C) Microsoft Corporation
- **Material Components**: (C) Google LLC
