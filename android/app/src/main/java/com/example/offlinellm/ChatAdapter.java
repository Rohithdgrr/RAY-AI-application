package com.example.offlinellm;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.sender.setText(message.getSender());
        holder.text.setText(message.getText());

        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.bubbleLayout.getLayoutParams();
        
        if (message.getSender().equalsIgnoreCase("You")) {
            params.horizontalBias = 1.0f;
            holder.bubbleLayout.setBackgroundResource(R.drawable.bubble_user);
            holder.sender.setVisibility(View.GONE);
        } else {
            params.horizontalBias = 0.0f;
            holder.bubbleLayout.setBackgroundResource(R.drawable.bubble_ai);
            holder.sender.setVisibility(View.VISIBLE);
        }
        holder.bubbleLayout.setLayoutParams(params);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView sender, text;
        LinearLayout bubbleLayout;
        ViewHolder(View view) {
            super(view);
            sender = view.findViewById(R.id.messageSender);
            text = view.findViewById(R.id.messageText);
            bubbleLayout = view.findViewById(R.id.bubbleLayout);
        }
    }
}
