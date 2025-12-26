package com.example.offlinellm;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private final List<ChatMessage> messages;
    private OnChatActionListener actionListener;
    private Markwon markwon;

    public interface OnChatActionListener {
        void onCopy(ChatMessage message);
        void onEdit(ChatMessage message);
        void onDownload(ChatMessage message);
        void onRegenerate(ChatMessage message);
        void onMakeLonger(ChatMessage message);
        void onMakeShorter(ChatMessage message);
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
        if (markwon == null) {
            markwon = Markwon.builder(parent.getContext())
                    .usePlugin(TablePlugin.create(parent.getContext()))
                    .usePlugin(StrikethroughPlugin.create())
                    .build();
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean isUserMessage = message.getSender().equalsIgnoreCase("You");

        if (isUserMessage) {
            holder.userMessageContainer.setVisibility(View.VISIBLE);
            holder.aiMessageContainer.setVisibility(View.GONE);
            holder.messageText.setText(message.getText());
            holder.userTimestamp.setText(message.getFormattedTime());
            setupUserActions(holder, message);
        } else {
            holder.userMessageContainer.setVisibility(View.GONE);
            holder.aiMessageContainer.setVisibility(View.VISIBLE);

            String text = message.getText();
            String thought = message.getThought();
            holder.codeBlocksContainer.removeAllViews();

            boolean hasContent = (text != null && !text.trim().isEmpty()) || (thought != null && !thought.trim().isEmpty());

            if (message.isGenerating() && !hasContent) {
                holder.thinkingLayout.setVisibility(View.VISIBLE);
                holder.thoughtContainer.setVisibility(View.GONE);
                holder.aiMessageText.setVisibility(View.GONE);
                holder.codeBlocksContainer.setVisibility(View.GONE);
                holder.aiActionsLayout.setVisibility(View.GONE);
                holder.metadataLayout.setVisibility(View.GONE);
            } else {
                holder.thinkingLayout.setVisibility(View.GONE);
                holder.aiActionsLayout.setVisibility(View.VISIBLE);
                holder.metadataLayout.setVisibility(View.VISIBLE);

                // Thought / Reasoning display
                if (thought != null && !thought.trim().isEmpty()) {
                    holder.thoughtContainer.setVisibility(View.VISIBLE);
                    holder.thoughtText.setText(thought.trim());
                } else {
                    holder.thoughtContainer.setVisibility(View.GONE);
                }

                if (text != null && text.contains("```")) {
                    holder.codeBlocksContainer.setVisibility(View.VISIBLE);
                    holder.aiMessageText.setVisibility(View.GONE);
                    renderMixedContent(holder, text);
                } else {
                    holder.codeBlocksContainer.setVisibility(View.GONE);
                    holder.aiMessageText.setVisibility(View.VISIBLE);
                    if (text != null) {
                        markwon.setMarkdown(holder.aiMessageText, text);
                    } else {
                        holder.aiMessageText.setText("");
                    }
                }
            }

            holder.aiTimestamp.setText(message.getFormattedTime());
            if (message.getResponseTimeMs() > 0) {
                holder.responseTimeText.setVisibility(View.VISIBLE);
                holder.responseTimeText.setText("• " + message.getFormattedResponseTime());
            } else {
                holder.responseTimeText.setVisibility(View.GONE);
            }
            setupAIActions(holder, message);
        }
    }

    private void setupUserActions(ViewHolder holder, ChatMessage message) {
        Context context = holder.itemView.getContext();
        holder.btnUserCopy.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onCopy(message);
            else copyToClipboard(context, message.getText());
        });
        holder.btnUserEdit.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onEdit(message);
        });
    }

    private void setupAIActions(ViewHolder holder, ChatMessage message) {
        Context context = holder.itemView.getContext();
        holder.btnAiCopy.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onCopy(message);
            else copyToClipboard(context, message.getText());
        });
        holder.btnAiDownload.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onDownload(message);
            else {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, message.getText());
                context.startActivity(Intent.createChooser(shareIntent, "Share response"));
            }
        });
        holder.btnAiRetry.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onRegenerate(message);
        });
        holder.btnAiLonger.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onMakeLonger(message);
        });
        holder.btnAiShorter.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onMakeShorter(message);
        });

        boolean generating = message.isGenerating();
        holder.btnAiRetry.setEnabled(!generating);
        holder.btnAiLonger.setEnabled(!generating);
        holder.btnAiShorter.setEnabled(!generating);
        float alpha = generating ? 0.4f : 1.0f;
        holder.btnAiRetry.setAlpha(alpha);
        holder.btnAiLonger.setAlpha(alpha);
        holder.btnAiShorter.setAlpha(alpha);
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Chat Message", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void renderMixedContent(ViewHolder holder, String text) {
        String[] parts = text.split("```");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.trim().isEmpty()) continue;
            if (i % 2 == 1) addCodeBlockView(holder, part);
            else addTextView(holder, part);
        }
    }

    private void addTextView(ViewHolder holder, String text) {
        TextView tv = new TextView(holder.itemView.getContext());
        markwon.setMarkdown(tv, text.trim());
        tv.setTextSize(15);
        tv.setTextColor(holder.itemView.getContext().getColor(R.color.bubble_ai_text));
        tv.setPadding(0, 4, 0, 4);
        holder.codeBlocksContainer.addView(tv);
    }

    private void addCodeBlockView(ViewHolder holder, String codeContent) {
        View codeView = LayoutInflater.from(holder.itemView.getContext()).inflate(R.layout.layout_code_block, null, false);
        TextView langTv = codeView.findViewById(R.id.codeLanguage);
        TextView codeTv = codeView.findViewById(R.id.codeText);
        View copyBtn = codeView.findViewById(R.id.btnCopyCode);

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
        copyBtn.setOnClickListener(v -> copyToClipboard(v.getContext(), finalCode));
        holder.codeBlocksContainer.addView(codeView);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout userMessageContainer, aiMessageContainer, codeBlocksContainer, aiActionsLayout, metadataLayout, thinkingLayout;
        MaterialCardView bubbleLayout, aiBubbleLayout, thoughtContainer;
        TextView messageText, userTimestamp, aiMessageText, aiTimestamp, responseTimeText, thoughtText;
        ImageButton btnUserCopy, btnUserEdit, btnAiCopy, btnAiDownload, btnAiRetry, btnAiLonger, btnAiShorter;

        ViewHolder(View view) {
            super(view);
            userMessageContainer = view.findViewById(R.id.userMessageContainer);
            bubbleLayout = view.findViewById(R.id.bubbleLayout);
            messageText = view.findViewById(R.id.messageText);
            userTimestamp = view.findViewById(R.id.userTimestamp);
            btnUserCopy = view.findViewById(R.id.btnUserCopy);
            btnUserEdit = view.findViewById(R.id.btnUserEdit);
            aiMessageContainer = view.findViewById(R.id.aiMessageContainer);
            aiBubbleLayout = view.findViewById(R.id.aiBubbleLayout);
            thoughtContainer = view.findViewById(R.id.thoughtContainer);
            thoughtText = view.findViewById(R.id.thoughtText);
            aiMessageText = view.findViewById(R.id.aiMessageText);
            codeBlocksContainer = view.findViewById(R.id.codeBlocksContainer);
            thinkingLayout = view.findViewById(R.id.thinkingLayout);
            aiActionsLayout = view.findViewById(R.id.aiActionsLayout);
            metadataLayout = view.findViewById(R.id.metadataLayout);
            aiTimestamp = view.findViewById(R.id.aiTimestamp);
            responseTimeText = view.findViewById(R.id.responseTimeText);
            btnAiCopy = view.findViewById(R.id.btnAiCopy);
            btnAiDownload = view.findViewById(R.id.btnAiDownload);
            btnAiRetry = view.findViewById(R.id.btnAiRetry);
            btnAiLonger = view.findViewById(R.id.btnAiLonger);
            btnAiShorter = view.findViewById(R.id.btnAiShorter);
        }
    }
}
