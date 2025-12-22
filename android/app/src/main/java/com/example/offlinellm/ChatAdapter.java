package com.example.offlinellm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private final List<ChatMessage> messages;
    private OnChatActionListener actionListener;

    public interface OnChatActionListener {
        void onCopy(ChatMessage message);
        void onRegenerate(ChatMessage message);
        void onMakeShort(ChatMessage message);
        void onMakeLong(ChatMessage message);
    }

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public void setOnChatActionListener(OnChatActionListener listener) {
        this.actionListener = listener;
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
        holder.timestamp.setText(message.getFormattedTime());

        // Handle code blocks
        String text = message.getText();
        holder.codeBlocksContainer.removeAllViews();
        
        if (text.contains("```")) {
            holder.codeBlocksContainer.setVisibility(View.VISIBLE);
            renderMixedContent(holder, text);
        } else {
            holder.codeBlocksContainer.setVisibility(View.GONE);
            holder.text.setVisibility(View.VISIBLE);
            holder.text.setText(text);
        }

        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) holder.bubbleLayout.getLayoutParams();
        
        if (message.getSender().equalsIgnoreCase("You")) {
            params.horizontalBias = 1.0f;
            holder.bubbleLayout.setBackgroundResource(R.drawable.bubble_user);
            holder.sender.setVisibility(View.GONE);
            holder.text.setTextColor(holder.itemView.getContext().getColor(android.R.color.white));
            holder.timestamp.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
            holder.timestamp.setGravity(Gravity.END);
            
            // Hide metadata and actions for user messages
            holder.metadataLayout.setVisibility(View.GONE);
            holder.actionButtonsLayout.setVisibility(View.GONE);
        } else {
            params.horizontalBias = 0.0f;
            holder.bubbleLayout.setBackgroundResource(R.drawable.bubble_ai);
            holder.sender.setVisibility(View.GONE); // Hide AI name too for modern look
            holder.text.setTextColor(holder.itemView.getContext().getColor(R.color.text_primary));
            holder.timestamp.setTextColor(holder.itemView.getContext().getColor(R.color.text_secondary));
            holder.timestamp.setGravity(Gravity.START);
            
            // Show metadata and actions for AI responses
            updateMetadata(holder, message);
            setupActionButtons(holder, message);
        }
        holder.bubbleLayout.setLayoutParams(params);
    }

    private void updateMetadata(ViewHolder holder, ChatMessage message) {
        if (message.isAIResponse()) {
            holder.metadataLayout.setVisibility(View.VISIBLE);
            holder.actionButtonsLayout.setVisibility(View.VISIBLE);
            
            // Set model name
            if (message.getModelName() != null) {
                holder.modelNameText.setText("Model: " + message.getModelName());
            } else {
                holder.modelNameText.setText("Model: Unknown");
            }
            
            // Set response time
            if (message.getResponseTimeMs() > 0) {
                holder.responseTimeText.setText(message.getFormattedResponseTime());
            } else if (message.isGenerating()) {
                holder.responseTimeText.setText("Generating...");
            } else {
                holder.responseTimeText.setVisibility(View.GONE);
            }
        } else {
            holder.metadataLayout.setVisibility(View.GONE);
            holder.actionButtonsLayout.setVisibility(View.GONE);
        }
    }

    private void setupActionButtons(ViewHolder holder, ChatMessage message) {
        // Copy button
        holder.btnCopy.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onCopy(message);
            } else {
                // Default copy behavior
                Context context = v.getContext();
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Chat Message", message.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });

        // Regenerate button
        holder.btnRegenerate.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onRegenerate(message);
            }
        });

        // Make short button
        holder.btnMakeShort.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onMakeShort(message);
            }
        });

        // Make long button
        holder.btnMakeLong.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onMakeLong(message);
            }
        });

        // Disable regenerate button if message is still generating
        if (message.isGenerating()) {
            holder.btnRegenerate.setEnabled(false);
            holder.btnRegenerate.setAlpha(0.5f);
        } else {
            holder.btnRegenerate.setEnabled(true);
            holder.btnRegenerate.setAlpha(1.0f);
        }
    }

    private void renderMixedContent(ViewHolder holder, String text) {
        holder.text.setVisibility(View.GONE);
        String[] parts = text.split("```");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.trim().isEmpty()) continue;

            if (i % 2 == 1) { // This is a code block
                addCodeBlockView(holder, part);
            } else { // This is regular text
                addTextView(holder, part);
            }
        }
    }

    private void addTextView(ViewHolder holder, String text) {
        TextView tv = new TextView(holder.itemView.getContext());
        tv.setText(text.trim());
        tv.setTextSize(16);
        tv.setPadding(0, 4, 0, 4);
        tv.setTextColor(holder.text.getCurrentTextColor());
        holder.codeBlocksContainer.addView(tv);
    }

    private void addCodeBlockView(ViewHolder holder, String codeContent) {
        View codeView = LayoutInflater.from(holder.itemView.getContext())
                .inflate(R.layout.layout_code_block, holder.codeBlocksContainer, false);
        
        TextView langTv = codeView.findViewById(R.id.codeLanguage);
        TextView codeTv = codeView.findViewById(R.id.codeText);
        View copyBtn = codeView.findViewById(R.id.btnCopyCode);

        // Extract language if present
        String lang = "Code";
        String code = codeContent;
        int firstNewLine = codeContent.indexOf("\n");
        if (firstNewLine > 0 && firstNewLine < 20) {
            lang = codeContent.substring(0, firstNewLine).trim();
            code = codeContent.substring(firstNewLine).trim();
        }
        
        if (lang.isEmpty()) lang = "Code";
        langTv.setText(lang);
        codeTv.setText(code);

        final String finalCode = code;
        copyBtn.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Code", finalCode);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(v.getContext(), "Code copied", Toast.LENGTH_SHORT).show();
        });

        holder.codeBlocksContainer.addView(codeView);
    }

    @Override
    public int getItemCount() { return messages.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView sender, text, timestamp, modelNameText, responseTimeText;
        LinearLayout bubbleLayout, metadataLayout, actionButtonsLayout, codeBlocksContainer;
        ImageButton btnCopy, btnRegenerate, btnMakeShort, btnMakeLong;
        
        ViewHolder(View view) {
            super(view);
            sender = view.findViewById(R.id.messageSender);
            text = view.findViewById(R.id.messageText);
            timestamp = view.findViewById(R.id.timestampText);
            bubbleLayout = view.findViewById(R.id.bubbleLayout);
            metadataLayout = view.findViewById(R.id.metadataLayout);
            actionButtonsLayout = view.findViewById(R.id.actionButtonsLayout);
            codeBlocksContainer = view.findViewById(R.id.codeBlocksContainer);
            modelNameText = view.findViewById(R.id.modelNameText);
            responseTimeText = view.findViewById(R.id.responseTimeText);
            btnCopy = view.findViewById(R.id.btnCopy);
            btnRegenerate = view.findViewById(R.id.btnRegenerate);
            btnMakeShort = view.findViewById(R.id.btnMakeShort);
            btnMakeLong = view.findViewById(R.id.btnMakeLong);
        }
    }
}
