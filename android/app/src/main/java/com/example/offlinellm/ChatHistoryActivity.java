package com.example.offlinellm;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import java.util.ArrayList;
import java.util.List;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.card.MaterialCardView;

public class ChatHistoryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatHistoryAdapter adapter;
    private ChatHistoryManager historyManager;
    private FloatingActionButton fabNewChat;
    private TextView emptyStateText;
    private ImageView emptyStateImage;
    private boolean showArchived = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Chat History");
        }

        historyManager = ChatHistoryManager.getInstance(this);
        
        initViews();
        setupRecyclerView();
        loadChatHistory();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerView);
        fabNewChat = findViewById(R.id.fabNewChat);
        emptyStateText = findViewById(R.id.emptyStateText);
        emptyStateImage = findViewById(R.id.emptyStateImage);

        fabNewChat.setOnClickListener(v -> {
            ChatSession newSession = historyManager.createNewSession();
            openChatSession(newSession.getId());
        });
    }

    private void setupRecyclerView() {
        // Use StaggeredGridLayoutManager for a modern card layout
        StaggeredGridLayoutManager layoutManager = new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        
        adapter = new ChatHistoryAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);
        
        // Add simple item decoration for spacing
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                int spacing = 8;
                outRect.left = spacing;
                outRect.right = spacing;
                outRect.top = spacing;
                outRect.bottom = spacing;
            }
        });
    }

    private void loadChatHistory() {
        List<ChatSession> sessions = showArchived ? 
            historyManager.getArchivedSessions() : 
            historyManager.getActiveSessions();
        
        adapter.updateSessions(sessions);
        
        // Show/hide empty state
        if (sessions.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.VISIBLE);
            emptyStateImage.setVisibility(View.VISIBLE);
            emptyStateText.setText(showArchived ? "No archived chats" : "No chat history yet");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyStateText.setVisibility(View.GONE);
            emptyStateImage.setVisibility(View.GONE);
        }
    }

    private void openChatSession(String sessionId) {
        historyManager.setActiveSession(sessionId);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("sessionId", sessionId);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_history_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_toggle_archived) {
            showArchived = !showArchived;
            item.setTitle(showArchived ? R.string.show_active : R.string.show_archived);
            loadChatHistory();
            return true;
        } else if (id == R.id.action_clear_all) {
            showClearAllDialog();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void showClearAllDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Clear All Chats")
                .setMessage("Are you sure you want to delete all chat history? This action cannot be undone.")
                .setPositiveButton("Delete All", (dialog, which) -> {
                    historyManager.clearAllSessions();
                    loadChatHistory();
                    Toast.makeText(this, "All chats deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void onChatClick(ChatSession session) {
        openChatSession(session.getId());
    }

    public void onChatLongClick(ChatSession session) {
        showChatOptionsDialog(session);
    }

    private void showChatOptionsDialog(ChatSession session) {
        String[] options;
        if (session.isArchived()) {
            options = new String[]{"Unarchive", "Delete", "Share"};
        } else {
            options = new String[]{"Archive", "Rename", "Delete", "Share"};
        }

        new AlertDialog.Builder(this)
                .setTitle(session.getTitle())
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Archive/Unarchive
                            if (session.isArchived()) {
                                historyManager.unarchiveSession(session.getId());
                                Toast.makeText(this, "Chat unarchived", Toast.LENGTH_SHORT).show();
                            } else {
                                historyManager.archiveSession(session.getId());
                                Toast.makeText(this, "Chat archived", Toast.LENGTH_SHORT).show();
                            }
                            loadChatHistory();
                            break;
                        case 1: // Rename/Delete
                            if (session.isArchived()) {
                                showDeleteDialog(session);
                            } else {
                                showRenameDialog(session);
                            }
                            break;
                        case 2: // Delete/Share
                            if (session.isArchived()) {
                                showDeleteDialog(session);
                            } else {
                                shareChat(session);
                            }
                            break;
                        case 3: // Share (for active chats)
                            shareChat(session);
                            break;
                    }
                })
                .show();
    }

    private void showRenameDialog(ChatSession session) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Rename Chat");
        
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(session.getTitle());
        input.setSelection(input.getText().length());
        builder.setView(input);
        
        builder.setPositiveButton("Rename", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                session.setTitle(newName);
                historyManager.updateSession(session);
                loadChatHistory();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showDeleteDialog(ChatSession session) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Chat")
                .setMessage("Are you sure you want to delete '" + session.getTitle() + "'? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    historyManager.deleteSession(session.getId());
                    loadChatHistory();
                    Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void shareChat(ChatSession session) {
        StringBuilder chatText = new StringBuilder();
        chatText.append("Chat: ").append(session.getTitle()).append("\n");
        chatText.append("Model: ").append(session.getModelName() != null ? session.getModelName() : "Unknown").append("\n");
        chatText.append("Date: ").append(session.getFormattedDate()).append("\n\n");
        
        for (ChatMessage message : session.getMessages()) {
            chatText.append(message.getSender()).append(": ").append(message.getText()).append("\n\n");
        }
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, chatText.toString());
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Chat: " + session.getTitle());
        startActivity(Intent.createChooser(shareIntent, "Share Chat"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadChatHistory();
    }
}
