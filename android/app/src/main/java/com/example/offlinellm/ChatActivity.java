package com.example.offlinellm;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class ChatActivity extends AppCompatActivity {

    private TextView chatLog;
    private EditText chatInput;
    private ScrollView chatScroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatLog = findViewById(R.id.chatLog);
        chatInput = findViewById(R.id.chatInput);
        chatScroll = findViewById(R.id.chatScroll);
        Button btnSend = findViewById(R.id.btnSend);

        appendLine("RAY: Hello! Type a message to begin.");

        btnSend.setOnClickListener(v -> {
            String msg = chatInput.getText().toString();
            if (TextUtils.isEmpty(msg.trim())) return;

            chatInput.setText("");
            appendLine("You: " + msg.trim());
            appendLine("RAY: " + msg.trim());
        });
    }

    private void appendLine(String line) {
        String current = chatLog.getText().toString();
        if (current.isEmpty()) {
            chatLog.setText(line);
        } else {
            chatLog.setText(current + "\n\n" + line);
        }
        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
