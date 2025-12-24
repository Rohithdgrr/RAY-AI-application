package com.example.offlinellm;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private List<ChatMessage> messages;
    private EditText chatInput;
    private View inputCard;
    private ImageButton btnSend;
    
    private ChatHistoryManager historyManager;
    private ChatSession currentSession;
    private boolean isGenerating = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        
        recyclerView = view.findViewById(R.id.chatRecyclerView);
        chatInput = view.findViewById(R.id.chatInput);
        inputCard = view.findViewById(R.id.inputCard);
        btnSend = view.findViewById(R.id.btnSend);
        
        // Setup recyclerview
        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        // Setup action listener for chat actions
        adapter.setOnChatActionListener(new ChatAdapter.OnChatActionListener() {
            @Override
            public void onCopy(ChatMessage message) {
                // Handled by adapter default
            }

            @Override
            public void onEdit(ChatMessage message) {
                // Put the user message back in the input
                if (message.getSender().equalsIgnoreCase("You")) {
                    chatInput.setText(message.getText());
                    chatInput.setSelection(chatInput.getText().length());
                    chatInput.requestFocus();
                }
            }

            @Override
            public void onDownload(ChatMessage message) {
                // Handled by adapter default (share)
            }

            @Override
            public void onRegenerate(ChatMessage message) {
                if (getActivity() instanceof MainActivity && !isGenerating) {
                    // Find the user message before this AI message and regenerate
                    int msgIndex = messages.indexOf(message);
                    if (msgIndex > 0) {
                        ChatMessage previousUserMsg = messages.get(msgIndex - 1);
                        if (previousUserMsg.getSender().equalsIgnoreCase("You")) {
                            // Remove the AI response and regenerate
                            messages.remove(msgIndex);
                            adapter.notifyItemRemoved(msgIndex);
                            
                            MainActivity activity = (MainActivity) getActivity();
                            activity.handleChatMessage(previousUserMsg.getText(), messages, adapter, recyclerView);
                        }
                    }
                }
            }

            @Override
            public void onMakeLonger(ChatMessage message) {
                if (getActivity() instanceof MainActivity && !isGenerating) {
                    // Create prompt to make longer
                    String prompt = "Please expand on your previous response and provide more detail: " + message.getText();
                    MainActivity activity = (MainActivity) getActivity();
                    
                    ChatMessage userMsg = new ChatMessage("You", "Make this longer");
                    messages.add(userMsg);
                    adapter.notifyItemInserted(messages.size() - 1);
                    
                    activity.handleChatMessage(prompt, messages, adapter, recyclerView);
                }
            }

            @Override
            public void onMakeShorter(ChatMessage message) {
                if (getActivity() instanceof MainActivity && !isGenerating) {
                    // Create prompt to make shorter
                    String prompt = "Please summarize this in a shorter way: " + message.getText();
                    MainActivity activity = (MainActivity) getActivity();
                    
                    ChatMessage userMsg = new ChatMessage("You", "Make this shorter");
                    messages.add(userMsg);
                    adapter.notifyItemInserted(messages.size() - 1);
                    
                    activity.handleChatMessage(prompt, messages, adapter, recyclerView);
                }
            }
        });
        
        historyManager = ChatHistoryManager.getInstance(getContext());
        loadActiveSession();
        
        // Listeners for input
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            handleSendButtonClick();
            return true;
        });
        
        btnSend.setOnClickListener(v -> handleSendButtonClick());
        
        return view;
    }

    private void loadActiveSession() {
        currentSession = historyManager.getActiveSession();
        if (currentSession != null) {
            messages.clear();
            messages.addAll(currentSession.getMessages());
            adapter.notifyDataSetChanged();
            if (!messages.isEmpty()) {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        }
    }

    private void handleSendButtonClick() {
        if (isGenerating) {
            // Stop generation
            stopGeneration();
        } else {
            // Send message
            sendMessage();
        }
    }

    private void sendMessage() {
        String text = chatInput.getText().toString().trim();
        if (text.isEmpty()) return;
        
        ChatMessage userMsg = new ChatMessage("You", text);
        messages.add(userMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
        chatInput.setText("");
        
        // Update UI for generating state
        setGenerating(true);
        
        // Simulation or engine call here
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            boolean started = activity.handleChatMessage(text, messages, adapter, recyclerView);
            if (!started) {
                setGenerating(false);
            }
        } else {
            setGenerating(false);
        }
    }

    public void setGenerating(boolean generating) {
        this.isGenerating = generating;
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (generating) {
                    btnSend.setImageResource(R.drawable.ic_stop);
                    btnSend.setColorFilter(null); // Keep it white
                } else {
                    btnSend.setImageResource(R.drawable.ic_send);
                    btnSend.setColorFilter(null);
                }
            });
        }
    }

    private void stopGeneration() {
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.stopGeneration();
            setGenerating(false);
            
            Toast.makeText(getContext(), "Generation stopped", Toast.LENGTH_SHORT).show();
        }
    }

    public void onGenerationComplete() {
        setGenerating(false);
    }
}