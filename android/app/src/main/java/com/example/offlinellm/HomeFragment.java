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
    
    private InferenceEngine engine;
    private ChatHistoryManager historyManager;
    private ChatSession currentSession;
    private boolean isGenerating = false;
    private InferenceEngine.Callback currentGenerationCallback;

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
        
        historyManager = ChatHistoryManager.getInstance(getContext());
        loadActiveSession();
        
        // Listeners for input
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage();
            return true;
        });
        
        btnSend.setOnClickListener(v -> sendMessage());
        
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

    private void sendMessage() {
        String text = chatInput.getText().toString().trim();
        if (text.isEmpty()) return;
        
        ChatMessage userMsg = new ChatMessage("You", text);
        messages.add(userMsg);
        adapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
        chatInput.setText("");
        
        // Simulation or engine call here
        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            activity.handleChatMessage(text, messages, adapter, recyclerView);
        }
    }
}