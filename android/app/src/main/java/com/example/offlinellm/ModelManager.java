package com.example.offlinellm;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ModelManager {
    private static final String TAG = "ModelManager";
    private static ModelManager instance;
    private final Context context;
    private final List<ModelInfo> availableModels;

    public enum Tier {
        HIGH_QUALITY("High Quality", 6000), // 6GB RAM
        BALANCED("Balanced", 4500),         // 4.5GB RAM
        MEDIUM("Medium", 3500),           // 3.5GB RAM
        LIGHT("Light", 2500),             // 2.5GB RAM
        ULTRA_LIGHT("Ultra-Light", 1500); // 1.5GB RAM

        public final String label;
        public final long minRamMb;

        Tier(String label, long minRamMb) {
            this.label = label;
            this.minRamMb = minRamMb;
        }
    }

    public static class ModelInfo {
        public String name;
        public String url;
        public String fileName;
        public String expectedSha256;
        public boolean isDownloaded;
        public Tier tier;
        public long estimatedRamBytes;

        public ModelInfo(String name, String url, String fileName, String expectedSha256, Tier tier, long estimatedRamBytes) {
            this.name = name;
            this.url = url;
            this.fileName = fileName;
            this.expectedSha256 = expectedSha256;
            this.tier = tier;
            this.estimatedRamBytes = estimatedRamBytes;
        }
    }

    private ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.availableModels = new ArrayList<>();
        
        // Tier 1: High Quality
        availableModels.add(new ModelInfo(
                "Llama-3.2-3B-Instruct Q4_K_M",
                "https://huggingface.co/TheBloke/Llama-3.2-3B-Instruct-GGUF/resolve/main/llama-3.2-3b-instruct.Q4_K_M.gguf",
                "llama3_2_3b_q4km.gguf.enc",
                "SHA_LLAMA_3_2", // Placeholder SHA
                Tier.HIGH_QUALITY,
                5200L * 1024L * 1024L
        ));

        // Tier 2: Balanced
        availableModels.add(new ModelInfo(
                "Phi-3 Mini 4K Instruct Q4_K_M",
                "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
                "phi3_mini_q4.gguf.enc",
                "SHA_PHI3",
                Tier.BALANCED,
                4200L * 1024L * 1024L
        ));

        // Tier 3: Medium
        availableModels.add(new ModelInfo(
                "Gemma-2B-It Q4_K_M",
                "https://huggingface.co/google/gemma-2b-it-GGUF/resolve/main/gemma-2b-it.Q4_K_M.gguf",
                "gemma_2b_q4km.gguf.enc",
                "SHA_GEMMA",
                Tier.MEDIUM,
                3200L * 1024L * 1024L
        ));

        // Tier 4: Light
        availableModels.add(new ModelInfo(
                "TinyLlama-1.1B-Chat Q4_K_M",
                "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                "tinyllama_1_1b_q4km.gguf.enc",
                "0f8983196f30d3118d0979bf3595b2836af8147d3c0e35fa5a4a5892557e9389",
                Tier.LIGHT,
                2200L * 1024L * 1024L
        ));

        // Tier 5: Ultra-Light
        availableModels.add(new ModelInfo(
                "Qwen2-0.5B-Instruct Q8_0",
                "https://huggingface.co/Qwen/Qwen2-0.5B-Instruct-GGUF/resolve/main/qwen2-0_5b-instruct-q8_0.gguf",
                "qwen2_0_5b_q80.gguf.enc",
                "SHA_QWEN2",
                Tier.ULTRA_LIGHT,
                1200L * 1024L * 1024L
        ));

        updateStatus();
    }

    public static synchronized ModelManager getInstance(Context context) {
        if (instance == null) instance = new ModelManager(context);
        return instance;
    }

    public List<ModelInfo> getModels() { return availableModels; }

    public void updateStatus() {
        for (ModelInfo info : availableModels) {
            File file = new File(context.getFilesDir(), info.fileName);
            info.isDownloaded = file.exists();
        }
    }

    public void downloadModel(ModelInfo info) {
        File targetFile = new File(context.getFilesDir(), info.fileName);
        if (targetFile.exists()) return;

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.url))
                .setTitle("Downloading " + info.name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(new File(context.getExternalCacheDir(), info.fileName + ".tmp")));

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        dm.enqueue(request);
    }

    public void onDownloadComplete(long id) {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = dm.query(query)) {
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri uri = Uri.parse(cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
                    processDownloadedFile(new File(uri.getPath()));
                }
            }
        }
    }

    private void processDownloadedFile(File tempFile) {
        new Thread(() -> {
            try {
                ModelInfo info = null;
                for (ModelInfo mi : availableModels) {
                    if (tempFile.getName().contains(mi.fileName)) {
                        info = mi;
                        break;
                    }
                }
                if (info == null) return;

                File encryptedFile = new File(context.getFilesDir(), info.fileName);
                SecurityHelper.encryptFile(context, tempFile, encryptedFile);
                tempFile.delete();
                Log.d(TAG, "Model encrypted: " + info.fileName);
                updateStatus();
            } catch (Exception e) {
                Log.e(TAG, "Encryption failed", e);
            }
        }).start();
    }

    public ModelInfo getBestDownloadedModel(Tier preference) {
        updateStatus();
        // Try tiers starting from preference down to ULTRA_LIGHT
        Tier[] tiers = Tier.values();
        for (int i = preference.ordinal(); i < tiers.length; i++) {
            Tier currentTier = tiers[i];
            for (ModelInfo info : availableModels) {
                if (info.tier == currentTier && info.isDownloaded) {
                    return info;
                }
            }
        }
        return null;
    }

    public long getAvailableRamMb() {
        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
        android.app.ActivityManager activityManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        return mi.availMem / (1024L * 1024L);
    }

    public boolean canLoadModel(ModelInfo info) {
        long availableMb = getAvailableRamMb();
        return availableMb > (info.estimatedRamBytes / (1024L * 1024L));
    }
}
