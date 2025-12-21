package com.example.offlinellm;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatMessage {
    private String sender;
    private String text;
    private long timestamp;

    public ChatMessage(String sender, String text) {
        this.sender = sender;
        this.text = text;
        this.timestamp = System.currentTimeMillis();
    }

    public String getSender() { return sender; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public long getTimestamp() { return timestamp; }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}
