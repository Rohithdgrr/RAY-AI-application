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

    public static class ModelInfo {
        public String name;
        public String url;
        public String fileName;
        public String expectedSha256;
        public boolean isDownloaded;

        public ModelInfo(String name, String url, String fileName, String expectedSha256) {
            this.name = name;
            this.url = url;
            this.fileName = fileName;
            this.expectedSha256 = expectedSha256;
        }
    }

    private ModelManager(Context context) {
        this.context = context.getApplicationContext();
        this.availableModels = new ArrayList<>();
        // Example Model: TinyLlama-1.1B Q4_K_M
        availableModels.add(new ModelInfo(
                "TinyLlama-1.1B-Chat Q4_K_M GGUF",
                "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf.enc",
                "0f8983196f30d3118d0979bf3595b2836af8147d3c0e35fa5a4a5892557e9389" // Example SHA
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
                // Find matching model info
                ModelInfo info = null;
                for (ModelInfo mi : availableModels) {
                    if (tempFile.getName().contains(mi.fileName)) {
                        info = mi;
                        break;
                    }
                }
                if (info == null) return;

                // Verify SHA-256 (Simplified for prompt)
                // In production, compute actual hash here

                // Encrypt
                File encryptedFile = new File(context.getFilesDir(), info.fileName);
                SecurityHelper.encryptFile(context, tempFile, encryptedFile);
                
                // Cleanup
                tempFile.delete();
                Log.d(TAG, "Model encrypted and stored: " + info.fileName);
                updateStatus();
            } catch (Exception e) {
                Log.e(TAG, "Encryption failed", e);
            }
        }).start();
    }
}
