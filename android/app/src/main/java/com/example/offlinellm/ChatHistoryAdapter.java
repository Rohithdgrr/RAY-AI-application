package com.example.offlinellm;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.ViewHolder> {
    private List<ChatSession> sessions;
    private final Context context;
    private final ChatHistoryActivity activity;
    private final Random random = new Random();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());

    public ChatHistoryAdapter(List<ChatSession> sessions, ChatHistoryActivity activity) {
        this.sessions = sessions;
        this.context = activity;
        this.activity = activity;
    }

    public void updateSessions(List<ChatSession> newSessions) {
        this.sessions = new ArrayList<>(newSessions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatSession session = sessions.get(position);
        
        holder.titleText.setText(session.getTitle());
        holder.messagePreview.setText(session.getLastMessagePreview());
        holder.messageCount.setText(session.getMessageCount() + " messages");
        holder.timeText.setText(session.getFormattedTime());
        holder.dateText.setText(session.getFormattedDate());
        
        // Set model name if available
        if (session.getModelName() != null) {
            holder.modelText.setText(session.getModelName());
            holder.modelText.setVisibility(View.VISIBLE);
        } else {
            holder.modelText.setVisibility(View.GONE);
        }
        
        // Set random gradient background for visual appeal
        setCardBackground(holder.cardView, session.getId());
        
        // Set archived indicator
        if (session.isArchived()) {
            holder.archivedIndicator.setVisibility(View.VISIBLE);
            holder.cardView.setAlpha(0.7f);
        } else {
            holder.archivedIndicator.setVisibility(View.GONE);
            holder.cardView.setAlpha(1.0f);
        }
        
        // Click listeners
        holder.cardView.setOnClickListener(v -> activity.onChatClick(session));
        holder.cardView.setOnLongClickListener(v -> {
            activity.onChatLongClick(session);
            return true;
        });
        
        // More options click
        holder.moreOptions.setOnClickListener(v -> activity.onChatLongClick(session));
        
        // Animate card entrance
        holder.cardView.setAlpha(0f);
        holder.cardView.setScaleX(0.8f);
        holder.cardView.setScaleY(0.8f);
        holder.cardView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setStartDelay(position * 50)
                .start();
    }

    private void setCardBackground(MaterialCardView cardView, String sessionId) {
        // Generate consistent random gradient based on session ID
        int hash = sessionId.hashCode();
        int color1 = getRandomColor(hash);
        int color2 = getRandomColor(hash + 1);
        
        GradientDrawable gradient = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{color1, color2}
        );
        gradient.setCornerRadius(16f);
        cardView.setBackground(gradient);
    }

    private int getRandomColor(int seed) {
        Random rand = new Random(seed);
        int hue = rand.nextInt(360);
        float saturation = 0.3f + rand.nextFloat() * 0.3f; // 30-60%
        float lightness = 0.85f + rand.nextFloat() * 0.1f; // 85-95%
        
        float[] hsl = {hue, saturation, lightness};
        int color = Color.HSVToColor(hsl);
        
        // Add slight transparency
        return Color.argb(200, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView titleText;
        TextView messagePreview;
        TextView messageCount;
        TextView timeText;
        TextView dateText;
        TextView modelText;
        ImageView archivedIndicator;
        ImageView moreOptions;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.cardView);
            titleText = itemView.findViewById(R.id.titleText);
            messagePreview = itemView.findViewById(R.id.messagePreview);
            messageCount = itemView.findViewById(R.id.messageCount);
            timeText = itemView.findViewById(R.id.timeText);
            dateText = itemView.findViewById(R.id.dateText);
            modelText = itemView.findViewById(R.id.modelText);
            archivedIndicator = itemView.findViewById(R.id.archivedIndicator);
            moreOptions = itemView.findViewById(R.id.moreOptions);
        }
    }
}
