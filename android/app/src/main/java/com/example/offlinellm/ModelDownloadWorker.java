package com.example.offlinellm;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ListenableWorker.Result;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class ModelDownloadWorker extends Worker {
    private static final String TAG = "ModelDownloadWorker";
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;

    public ModelDownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String name = getInputData().getString("name");
        String urlString = getInputData().getString("url");
        String fileName = getInputData().getString("fileName");
        String expectedSha = getInputData().getString("expectedSha");

        Log.d(TAG, "Starting download for: " + name + " from " + urlString);

        if (urlString == null || fileName == null) {
            return Result.failure(new Data.Builder().putString("error", "Missing url or filename").build());
        }

        Context ctx = getApplicationContext();
        File tempFile = new File(ctx.getCacheDir(), fileName + ".tmp");
        File targetFile = new File(ctx.getFilesDir(), fileName);

        ModelManager manager = ModelManager.getInstance(ctx);
        long existingLength = tempFile.exists() ? tempFile.length() : 0;

        try {
            // Follow redirects manually for better control
            HttpURLConnection conn = createConnectionWithRedirects(urlString, existingLength);
            
            if (conn == null) {
                manager.onWorkerFailed(fileName, "Failed to connect after redirects");
                return Result.failure(new Data.Builder().putString("error", "Connection failed").build());
            }

            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Response code: " + responseCode);
            
            long total;
            boolean append = false;

            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                // Resuming
                String contentRange = conn.getHeaderField("Content-Range");
                if (contentRange != null) {
                    // format: bytes start-end/total
                    total = Long.parseLong(contentRange.substring(contentRange.lastIndexOf("/") + 1));
                } else {
                    total = conn.getContentLengthLong() + existingLength;
                }
                append = true;
                Log.d(TAG, "Resuming download from " + existingLength + " bytes. Total: " + total);
            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                // Starting fresh or server doesn't support Range
                total = conn.getContentLengthLong();
                existingLength = 0;
                append = false;
                Log.d(TAG, "Starting fresh download. Total: " + total);
            } else {
                String errorMsg = "HTTP Error: " + responseCode;
                Log.e(TAG, errorMsg);
                manager.onWorkerFailed(fileName, errorMsg);
                return Result.failure(new Data.Builder().putString("error", errorMsg).build());
            }

            try (InputStream in = new BufferedInputStream(conn.getInputStream(), 16384);
                 FileOutputStream out = new FileOutputStream(tempFile, append)) {
                byte[] buffer = new byte[16384];
                long downloaded = existingLength;
                int read;
                long lastProgressTime = System.currentTimeMillis();
                
                while ((read = in.read(buffer)) != -1) {
                    if (isStopped()) {
                        Log.d(TAG, "Download stopped by system (Paused/Cancelled)");
                        // We DON'T delete tempFile here to allow resuming
                        return Result.failure();
                    }
                    
                    out.write(buffer, 0, read);
                    downloaded += read;
                    
                    // Update progress every 500ms to avoid too many updates
                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime >= 500 || downloaded == total) {
                        int progress = (total > 0) ? (int) ((downloaded * 100) / total) : 0;
                        Data progressData = new Data.Builder()
                                .putInt("progress", progress)
                                .putLong("downloaded", downloaded)
                                .putLong("total", total)
                                .putString("fileName", fileName)
                                .build();
                        setProgressAsync(progressData);
                        manager.onWorkerProgress(fileName, progress, downloaded, total);
                        lastProgressTime = now;
                    }
                }
            }

            conn.disconnect();
            Log.d(TAG, "Download completed. File size: " + tempFile.length());

            // SHA check
            if (expectedSha != null && !"PLACEHOLDER".equals(expectedSha)) {
                String actual = calculateSha256(tempFile);
                if (!expectedSha.equalsIgnoreCase(actual)) {
                    tempFile.delete();
                    manager.onWorkerFailed(fileName, "SHA-256 mismatch");
                    return Result.failure(new Data.Builder().putString("error", "SHA mismatch").build());
                }
            }

            // Ensure we have enough space for encryption (size of file * 1.1)
            long requiredSpace = (long) (tempFile.length() * 1.1);
            if (targetFile.getParentFile().getUsableSpace() < requiredSpace) {
                String errorMsg = "Insufficient storage for encryption";
                Log.e(TAG, errorMsg);
                manager.onWorkerFailed(fileName, errorMsg);
                tempFile.delete();
                return Result.failure(new Data.Builder().putString("error", errorMsg).build());
            }

            // Encrypt/move
            try {
                SecurityHelper.encryptFile(ctx, tempFile, targetFile);
            } catch (Exception e) {
                Log.e(TAG, "Encryption failed", e);
                manager.onWorkerFailed(fileName, "Encryption failed: " + e.getMessage());
                tempFile.delete();
                if (targetFile.exists()) targetFile.delete();
                return Result.failure(new Data.Builder().putString("error", "Encryption failed").build());
            }
            
            tempFile.delete();
            
            Log.d(TAG, "Model saved to: " + targetFile.getAbsolutePath());
            manager.onWorkerCompleted(fileName, targetFile);
            return Result.success();
            
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            // We DON'T delete tempFile here to allow resuming after network change/error
            manager.onWorkerFailed(fileName, e.getMessage());
            return Result.failure(new Data.Builder().putString("error", e.getMessage()).build());
        }
    }

    private HttpURLConnection createConnectionWithRedirects(String urlString, long offset) throws Exception {
        int redirectCount = 0;
        String currentUrl = urlString;
        
        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "RAY-AI-Android/1.0");
            conn.setRequestProperty("Accept", "*/*");

            if (offset > 0) {
                conn.setRequestProperty("Range", "bytes=" + offset + "-");
            }
            
            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Checking URL: " + currentUrl + " -> " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                return conn;
            } else if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                       responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                       responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                       responseCode == 307 || responseCode == 308) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                
                if (newUrl == null || newUrl.isEmpty()) {
                    Log.e(TAG, "Redirect with no Location header");
                    return null;
                }
                
                // Handle relative redirects
                if (!newUrl.startsWith("http")) {
                    URL baseUrl = new URL(currentUrl);
                    newUrl = new URL(baseUrl, newUrl).toString();
                }
                
                Log.d(TAG, "Redirecting to: " + newUrl);
                currentUrl = newUrl;
                redirectCount++;
            } else {
                return conn; // Let caller handle error
            }
        }
        
        Log.e(TAG, "Too many redirects");
        return null;
    }

    private String calculateSha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
