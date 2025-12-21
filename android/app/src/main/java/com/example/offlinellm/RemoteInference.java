package com.example.offlinellm;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RemoteInference {
    private static final String TAG = "RemoteInference";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private String apiKey;

    public interface RemoteCallback {
        void onToken(String token);
        void onError(String error);
    }

    public RemoteInference(String apiKey) {
        this.apiKey = apiKey;
    }

    public void generate(String prompt, RemoteCallback callback) {
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("model", "llama3-8b-8192");
                JSONArray messages = new JSONArray();
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                msg.put("content", prompt);
                messages.put(msg);
                payload.put("messages", messages);
                payload.put("stream", false); // Simple non-stream for now, or use true if desired

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }
                    
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    String content = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                    
                    callback.onToken(content);
                } else {
                    callback.onError("HTTP " + code);
                }
            } catch (Exception e) {
                Log.e(TAG, "Remote inference failed", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
