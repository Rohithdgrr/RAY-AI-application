package com.example.offlinellm;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatMessage {
    private String sender;
    private String text;
    private long timestamp;
    private String modelName;
    private String thought;
    private long startTimeMs;
    private long responseTimeMs;
    private boolean isGenerating;

    public ChatMessage(String sender, String text) {
        this.sender = sender;
        this.text = text;
        this.thought = "";
        this.timestamp = System.currentTimeMillis();
        this.startTimeMs = 0;
        this.responseTimeMs = 0;
        this.isGenerating = false;
    }

    public ChatMessage(String sender, String text, String modelName) {
        this(sender, text);
        this.modelName = modelName;
    }

    // Getters and Setters
    public String getSender() { return sender; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getThought() { return thought; }
    public void setThought(String thought) { this.thought = thought; }
    public long getTimestamp() { return timestamp; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }
    public boolean isGenerating() { return isGenerating; }
    public void setGenerating(boolean generating) { isGenerating = generating; }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public String getFormattedResponseTime() {
        long displayTime = responseTimeMs;
        if (isGenerating && startTimeMs > 0) {
            displayTime = System.currentTimeMillis() - startTimeMs;
        }
        
        if (displayTime <= 0) return "";
        
        if (displayTime < 1000) {
            return displayTime + "ms";
        } else if (displayTime < 60000) {
            return String.format(Locale.getDefault(), "%.1fs", displayTime / 1000.0);
        } else {
            long minutes = displayTime / 60000;
            long seconds = (displayTime % 60000) / 1000;
            if (minutes > 1440) return ""; // Filter out ridiculous values
            return String.format(Locale.getDefault(), "%dm %ds", minutes, seconds);
        }
    }

    public boolean isAIResponse() {
        return "RAY".equals(sender) || sender != null && sender.toLowerCase().contains("ai");
    }

    public void startGeneration() {
        this.isGenerating = true;
        this.startTimeMs = System.currentTimeMillis();
        this.responseTimeMs = 0;
    }

    public void finishGeneration() {
        if (isGenerating && startTimeMs > 0) {
            this.responseTimeMs = System.currentTimeMillis() - startTimeMs;
            this.isGenerating = false;
        }
    }
}
