package com.example.offlinellm;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ChatSession implements Serializable {
    private String id;
    private String title;
    private long createdAt;
    private long updatedAt;
    private List<ChatMessage> messages;
    private String modelName;
    private boolean isArchived;

    public ChatSession() {
        this.id = UUID.randomUUID().toString();
        this.title = "New Chat";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.messages = new ArrayList<>();
        this.isArchived = false;
    }

    public ChatSession(String title, String modelName) {
        this();
        this.title = title;
        this.modelName = modelName;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { 
        this.title = title; 
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { 
        this.messages = messages; 
        this.updatedAt = System.currentTimeMillis();
    }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { 
        this.modelName = modelName; 
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isArchived() { return isArchived; }
    public void setArchived(boolean archived) { 
        isArchived = archived; 
        this.updatedAt = System.currentTimeMillis();
    }

    // Utility methods
    public void addMessage(ChatMessage message) {
        messages.add(message);
        updatedAt = System.currentTimeMillis();
        
        // Auto-generate title from first user message if title is default
        if (title.equals("New Chat") && messages.size() == 1 && "You".equals(message.getSender())) {
            String messageText = message.getText();
            if (messageText.length() > 30) {
                title = messageText.substring(0, 27) + "...";
            } else {
                title = messageText;
            }
        }
    }

    public int getMessageCount() {
        return messages.size();
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(updatedAt));
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return sdf.format(new Date(updatedAt));
    }

    public String getLastMessagePreview() {
        if (messages.isEmpty()) {
            return "No messages yet";
        }
        ChatMessage lastMessage = messages.get(messages.size() - 1);
        String text = lastMessage.getText();
        return text.length() > 50 ? text.substring(0, 47) + "..." : text;
    }

    public void clearMessages() {
        messages.clear();
        updatedAt = System.currentTimeMillis();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ChatSession that = (ChatSession) obj;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
