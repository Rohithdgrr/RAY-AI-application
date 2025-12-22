package com.example.offlinellm;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private SettingsAdapter adapter;
    private List<SettingItem> settingsList;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        initViews();
        setupSettings();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.settingsRecyclerView);
        settingsList = new ArrayList<>();
        adapter = new SettingsAdapter(settingsList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupSettings() {
        settingsList.clear();

        // Model Settings
        SettingItem modelSection = new SettingItem(SettingItem.TYPE_HEADER, "Model Settings");
        settingsList.add(modelSection);

        SettingItem defaultModel = new SettingItem(SettingItem.TYPE_SELECTION, "Default Model", 
                "Select preferred model", prefs.getString("default_model", "Auto"));
        settingsList.add(defaultModel);

        SettingItem maxTokens = new SettingItem(SettingItem.TYPE_SLIDER, "Max Tokens", 
                "Maximum response length", prefs.getInt("max_tokens", 2048));
        settingsList.add(maxTokens);

        SettingItem temperature = new SettingItem(SettingItem.TYPE_SLIDER, "Temperature", 
                "Response creativity (0.0-1.0)", prefs.getFloat("temperature", 0.7f));
        settingsList.add(temperature);

        // Chat Settings
        SettingItem chatSection = new SettingItem(SettingItem.TYPE_HEADER, "Chat Settings");
        settingsList.add(chatSection);

        SettingItem autoSave = new SettingItem(SettingItem.TYPE_SWITCH, "Auto-save Chat", 
                "Automatically save conversations", prefs.getBoolean("auto_save", true));
        settingsList.add(autoSave);

        SettingItem timestamps = new SettingItem(SettingItem.TYPE_SWITCH, "Show Timestamps", 
                "Display message timestamps", prefs.getBoolean("show_timestamps", true));
        settingsList.add(timestamps);

        SettingItem typingIndicator = new SettingItem(SettingItem.TYPE_SWITCH, "Typing Indicator", 
                "Show when AI is typing", prefs.getBoolean("typing_indicator", true));
        settingsList.add(typingIndicator);

        // UI Settings
        SettingItem uiSection = new SettingItem(SettingItem.TYPE_HEADER, "UI Settings");
        settingsList.add(uiSection);

        SettingItem darkMode = new SettingItem(SettingItem.TYPE_SWITCH, "Dark Mode", 
                "Enable dark theme", prefs.getBoolean("dark_mode", false));
        settingsList.add(darkMode);

        SettingItem compactMode = new SettingItem(SettingItem.TYPE_SWITCH, "Compact Mode", 
                "Show more messages on screen", prefs.getBoolean("compact_mode", false));
        settingsList.add(compactMode);

        SettingItem fontSize = new SettingItem(SettingItem.TYPE_SELECTION, "Font Size", 
                "Adjust text size", prefs.getString("font_size", "Medium"));
        settingsList.add(fontSize);

        // Storage Settings
        SettingItem storageSection = new SettingItem(SettingItem.TYPE_HEADER, "Storage Settings");
        settingsList.add(storageSection);

        SettingItem clearCache = new SettingItem(SettingItem.TYPE_ACTION, "Clear Cache", 
                "Free up storage space", "Clear");
        settingsList.add(clearCache);

        SettingItem exportData = new SettingItem(SettingItem.TYPE_ACTION, "Export Chat History", 
                "Download your conversations", "Export");
        settingsList.add(exportData);

        // About
        SettingItem aboutSection = new SettingItem(SettingItem.TYPE_HEADER, "About");
        settingsList.add(aboutSection);

        SettingItem version = new SettingItem(SettingItem.TYPE_INFO, "Version", 
                "RAY AI v1.0.0", "");
        settingsList.add(version);

        SettingItem about = new SettingItem(SettingItem.TYPE_ACTION, "About", 
                "App information and credits", "Learn More");
        settingsList.add(about);

        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onSettingClick(SettingItem item) {
        switch (item.getType()) {
            case SettingItem.TYPE_SWITCH:
                boolean newValue = !item.isChecked();
                item.setChecked(newValue);
                saveSetting(item.getKey(), newValue);
                adapter.notifyDataSetChanged();
                break;
                
            case SettingItem.TYPE_SELECTION:
                showSelectionDialog(item);
                break;
                
            case SettingItem.TYPE_SLIDER:
                showSliderDialog(item);
                break;
                
            case SettingItem.TYPE_ACTION:
                handleAction(item);
                break;
        }
    }

    private void saveSetting(String key, Object value) {
        SharedPreferences.Editor editor = prefs.edit();
        if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        }
        editor.apply();
    }

    private void showSelectionDialog(SettingItem item) {
        String[] options = getOptionsForSetting(item.getKey());
        if (options != null) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(item.getTitle())
                    .setItems(options, (dialog, which) -> {
                        item.setValue(options[which]);
                        saveSetting(item.getKey(), options[which]);
                        adapter.notifyDataSetChanged();
                    })
                    .show();
        }
    }

    private void showSliderDialog(SettingItem item) {
        // Create a custom dialog with slider
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_slider, null);
        
        android.widget.SeekBar seekBar = dialogView.findViewById(R.id.seekBar);
        TextView valueText = dialogView.findViewById(R.id.valueText);
        TextView titleText = dialogView.findViewById(R.id.titleText);
        
        titleText.setText(item.getTitle());
        
        if ("max_tokens".equals(item.getKey())) {
            seekBar.setMax(4096);
            seekBar.setProgress(item.getIntValue() - 512);
            valueText.setText(item.getIntValue() + " tokens");
            
            seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    int value = progress + 512;
                    valueText.setText(value + " tokens");
                    if (fromUser) {
                        item.setIntValue(value);
                        saveSetting(item.getKey(), value);
                    }
                }
                
                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                    adapter.notifyDataSetChanged();
                }
            });
        } else if ("temperature".equals(item.getKey())) {
            seekBar.setMax(100);
            seekBar.setProgress((int)(item.getFloatValue() * 100));
            valueText.setText(String.format("%.2f", item.getFloatValue()));
            
            seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                    float value = progress / 100f;
                    valueText.setText(String.format("%.2f", value));
                    if (fromUser) {
                        item.setFloatValue(value);
                        saveSetting(item.getKey(), value);
                    }
                }
                
                @Override
                public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
                
                @Override
                public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                    adapter.notifyDataSetChanged();
                }
            });
        }
        
        builder.setView(dialogView)
                .setPositiveButton("OK", null)
                .show();
    }

    private void handleAction(SettingItem item) {
        if ("clear_cache".equals(item.getKey())) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Clear Cache")
                    .setMessage("This will clear all cached data. Are you sure?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        // Clear cache logic here
                        Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } else if ("export_data".equals(item.getKey())) {
            Toast.makeText(this, "Exporting chat history...", Toast.LENGTH_SHORT).show();
            // Export logic here
        } else if ("about".equals(item.getKey())) {
            showAboutDialog();
        }
    }

    private void showAboutDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("About RAY AI")
                .setMessage("RAY AI - The Future of Offline Intelligence\n\n" +
                        "Version: 1.0.0\n\n" +
                        "A powerful offline AI assistant that runs directly on your device.\n\n" +
                        "Features:\n" +
                        "• Multiple AI models\n" +
                        "• Real-time chat\n" +
                        "• Privacy-focused\n" +
                        "• No internet required\n\n" +
                        "© 2024 RAY AI")
                .setPositiveButton("OK", null)
                .show();
    }

    private String[] getOptionsForSetting(String key) {
        switch (key) {
            case "default_model":
                return new String[]{"Auto", "Qwen2-0.5B", "Qwen2-1.5B", "Phi-3", "Gemma-2B", "Llama-3.2-3B"};
            case "font_size":
                return new String[]{"Small", "Medium", "Large", "Extra Large"};
            default:
                return null;
        }
    }
}
