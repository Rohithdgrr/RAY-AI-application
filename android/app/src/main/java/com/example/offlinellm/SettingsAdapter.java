package com.example.offlinellm;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<SettingItem> settings;
    private final SettingsActivity activity;

    public SettingsAdapter(List<SettingItem> settings, SettingsActivity activity) {
        this.settings = settings;
        this.activity = activity;
    }

    @Override
    public int getItemViewType(int position) {
        return settings.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case SettingItem.TYPE_HEADER:
                return new HeaderViewHolder(inflater.inflate(R.layout.item_setting_header, parent, false));
            case SettingItem.TYPE_SWITCH:
                return new SwitchViewHolder(inflater.inflate(R.layout.item_setting_switch, parent, false));
            case SettingItem.TYPE_SELECTION:
                return new SelectionViewHolder(inflater.inflate(R.layout.item_setting_selection, parent, false));
            case SettingItem.TYPE_SLIDER:
                return new SliderViewHolder(inflater.inflate(R.layout.item_setting_slider, parent, false));
            case SettingItem.TYPE_ACTION:
                return new ActionViewHolder(inflater.inflate(R.layout.item_setting_action, parent, false));
            case SettingItem.TYPE_INFO:
                return new InfoViewHolder(inflater.inflate(R.layout.item_setting_info, parent, false));
            default:
                throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SettingItem item = settings.get(position);
        
        switch (holder.getItemViewType()) {
            case SettingItem.TYPE_HEADER:
                ((HeaderViewHolder) holder).bind(item);
                break;
            case SettingItem.TYPE_SWITCH:
                ((SwitchViewHolder) holder).bind(item);
                break;
            case SettingItem.TYPE_SELECTION:
                ((SelectionViewHolder) holder).bind(item);
                break;
            case SettingItem.TYPE_SLIDER:
                ((SliderViewHolder) holder).bind(item);
                break;
            case SettingItem.TYPE_ACTION:
                ((ActionViewHolder) holder).bind(item);
                break;
            case SettingItem.TYPE_INFO:
                ((InfoViewHolder) holder).bind(item);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return settings.size();
    }

    // Header ViewHolder
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;

        HeaderViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);
        }

        void bind(SettingItem item) {
            titleText.setText(item.getTitle());
        }
    }

    // Switch ViewHolder
    class SwitchViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, descriptionText;
        Switch switchWidget;

        SwitchViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);
            descriptionText = view.findViewById(R.id.descriptionText);
            switchWidget = view.findViewById(R.id.switchWidget);
        }

        void bind(SettingItem item) {
            titleText.setText(item.getTitle());
            descriptionText.setText(item.getDescription());
            switchWidget.setChecked(item.isChecked());
            
            itemView.setOnClickListener(v -> {
                boolean newState = !switchWidget.isChecked();
                switchWidget.setChecked(newState);
                item.setChecked(newState);
                activity.onSettingClick(item);
            });
            
            switchWidget.setOnCheckedChangeListener((buttonView, isChecked) -> {
                item.setChecked(isChecked);
                activity.onSettingClick(item);
            });
        }
    }

    // Selection ViewHolder
    class SelectionViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, descriptionText, valueText;
        ImageView arrowIcon;

        SelectionViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);
            descriptionText = view.findViewById(R.id.descriptionText);
            valueText = view.findViewById(R.id.valueText);
            arrowIcon = view.findViewById(R.id.arrowIcon);
        }

        void bind(SettingItem item) {
            titleText.setText(item.getTitle());
            descriptionText.setText(item.getDescription());
            valueText.setText(item.getValue());
            arrowIcon.setVisibility(View.VISIBLE);
            
            itemView.setOnClickListener(v -> activity.onSettingClick(item));
        }
    }

    // Slider ViewHolder
    class SliderViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, descriptionText, valueText;
        SeekBar seekBar;

        SliderViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);
            descriptionText = view.findViewById(R.id.descriptionText);
            valueText = view.findViewById(R.id.valueText);
            seekBar = view.findViewById(R.id.seekBar);
        }

        void bind(SettingItem item) {
            titleText.setText(item.getTitle());
            descriptionText.setText(item.getDescription());
            
            if ("max_tokens".equals(item.getKey())) {
                seekBar.setMax(4096);
                seekBar.setProgress(item.getIntValue() - 512);
                valueText.setText(item.getIntValue() + " tokens");
            } else if ("temperature".equals(item.getKey())) {
                seekBar.setMax(100);
                seekBar.setProgress((int)(item.getFloatValue() * 100));
                valueText.setText(String.format("%.2f", item.getFloatValue()));
            }
            
            itemView.setOnClickListener(v -> activity.onSettingClick(item));
        }
    }

    // Action ViewHolder
    class ActionViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, descriptionText, actionText;
        ImageView actionIcon;

        ActionViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);
            descriptionText = view.findViewById(R.id.descriptionText);
            actionText = view.findViewById(R.id.actionText);
            actionIcon = view.findViewById(R.id.actionIcon);
        }

        void bind(SettingItem item) {
            titleText.setText(item.getTitle());
            descriptionText.setText(item.getDescription());
            actionText.setText(item.getValue());
            
            // Set appropriate icon
            if ("clear_cache".equals(item.getKey())) {
                actionIcon.setImageResource(android.R.drawable.ic_menu_delete);
            } else if ("export_data".equals(item.getKey())) {
                actionIcon.setImageResource(android.R.drawable.ic_menu_save);
            } else if ("about".equals(item.getKey())) {
                actionIcon.setImageResource(android.R.drawable.ic_menu_info_details);
            }
            
            itemView.setOnClickListener(v -> activity.onSettingClick(item));
        }
    }

    // Info ViewHolder
    class InfoViewHolder extends RecyclerView.ViewHolder {
        TextView titleText, valueText;

        InfoViewHolder(View view) {
            super(view);
            titleText = view.findViewById(R.id.titleText);
            valueText = view.findViewById(R.id.valueText);
        }

        void bind(SettingItem item) {
            titleText.setText(item.getTitle());
            valueText.setText(item.getValue());
        }
    }
}
