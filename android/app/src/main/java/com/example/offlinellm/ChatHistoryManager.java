package com.example.offlinellm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ChatHistoryManager {
    private static final String TAG = "ChatHistoryManager";
    private static final String PREFS_NAME = "chat_history_prefs";
    private static final String ACTIVE_SESSION_ID_KEY = "active_session_id";
    private static final String CHAT_SESSIONS_FILE = "chat_sessions.json";
    
    private static ChatHistoryManager instance;
    private final Context context;
    private final Gson gson;
    private final SharedPreferences prefs;
    private List<ChatSession> sessions;
    private String activeSessionId;

    private ChatHistoryManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .setPrettyPrinting()
                .create();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.sessions = new ArrayList<>();
        loadSessions();
        this.activeSessionId = prefs.getString(ACTIVE_SESSION_ID_KEY, null);
    }

    public static synchronized ChatHistoryManager getInstance(Context context) {
        if (instance == null) {
            instance = new ChatHistoryManager(context);
        }
        return instance;
    }

    private void loadSessions() {
        try {
            File file = new File(context.getFilesDir(), CHAT_SESSIONS_FILE);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[(int) file.length()];
                fis.read(buffer);
                fis.close();
                
                String json = new String(buffer, "UTF-8");
                Type listType = new TypeToken<List<ChatSession>>(){}.getType();
                List<ChatSession> loadedSessions = gson.fromJson(json, listType);
                
                if (loadedSessions != null) {
                    sessions = loadedSessions;
                    Log.d(TAG, "Loaded " + sessions.size() + " chat sessions");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading chat sessions", e);
        }
    }

    private void saveSessions() {
        try {
            File file = new File(context.getFilesDir(), CHAT_SESSIONS_FILE);
            FileOutputStream fos = new FileOutputStream(file);
            
            String json = gson.toJson(sessions);
            fos.write(json.getBytes("UTF-8"));
            fos.close();
            
            Log.d(TAG, "Saved " + sessions.size() + " chat sessions");
        } catch (IOException e) {
            Log.e(TAG, "Error saving chat sessions", e);
        }
    }

    public ChatSession createNewSession(String title, String modelName) {
        ChatSession session = new ChatSession(title, modelName);
        sessions.add(session);
        saveSessions();
        setActiveSession(session.getId());
        return session;
    }

    public ChatSession createNewSession() {
        return createNewSession("New Chat", null);
    }

    public ChatSession getSession(String sessionId) {
        for (ChatSession session : sessions) {
            if (session.getId().equals(sessionId)) {
                return session;
            }
        }
        return null;
    }

    public List<ChatSession> getAllSessions() {
        return new ArrayList<>(sessions);
    }

    public List<ChatSession> getActiveSessions() {
        List<ChatSession> activeSessions = new ArrayList<>();
        for (ChatSession session : sessions) {
            if (!session.isArchived()) {
                activeSessions.add(session);
            }
        }
        // Sort by last updated (most recent first)
        Collections.sort(activeSessions, new Comparator<ChatSession>() {
            @Override
            public int compare(ChatSession s1, ChatSession s2) {
                return Long.compare(s2.getUpdatedAt(), s1.getUpdatedAt());
            }
        });
        return activeSessions;
    }

    public List<ChatSession> getArchivedSessions() {
        List<ChatSession> archivedSessions = new ArrayList<>();
        for (ChatSession session : sessions) {
            if (session.isArchived()) {
                archivedSessions.add(session);
            }
        }
        // Sort by last updated (most recent first)
        Collections.sort(archivedSessions, new Comparator<ChatSession>() {
            @Override
            public int compare(ChatSession s1, ChatSession s2) {
                return Long.compare(s2.getUpdatedAt(), s1.getUpdatedAt());
            }
        });
        return archivedSessions;
    }

    public void updateSession(ChatSession session) {
        int index = -1;
        for (int i = 0; i < sessions.size(); i++) {
            if (sessions.get(i).getId().equals(session.getId())) {
                index = i;
                break;
            }
        }
        
        if (index != -1) {
            sessions.set(index, session);
            saveSessions();
        }
    }

    public void deleteSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            sessions.remove(session);
            saveSessions();
            
            // If deleted session was active, clear active session
            if (sessionId.equals(activeSessionId)) {
                activeSessionId = null;
                prefs.edit().remove(ACTIVE_SESSION_ID_KEY).apply();
            }
        }
    }

    public void archiveSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            session.setArchived(true);
            updateSession(session);
        }
    }

    public void unarchiveSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            session.setArchived(false);
            updateSession(session);
        }
    }

    public ChatSession getActiveSession() {
        if (activeSessionId != null) {
            return getSession(activeSessionId);
        }
        return null;
    }

    public void setActiveSession(String sessionId) {
        this.activeSessionId = sessionId;
        prefs.edit().putString(ACTIVE_SESSION_ID_KEY, sessionId).apply();
    }

    public void clearAllSessions() {
        sessions.clear();
        activeSessionId = null;
        prefs.edit().remove(ACTIVE_SESSION_ID_KEY).apply();
        saveSessions();
    }

    public int getSessionCount() {
        return sessions.size();
    }

    public int getActiveSessionCount() {
        int count = 0;
        for (ChatSession session : sessions) {
            if (!session.isArchived()) {
                count++;
            }
        }
        return count;
    }
}
