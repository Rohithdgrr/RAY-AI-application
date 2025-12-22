package com.example.offlinellm;

public class SettingItem {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_SWITCH = 1;
    public static final int TYPE_SELECTION = 2;
    public static final int TYPE_SLIDER = 3;
    public static final int TYPE_ACTION = 4;
    public static final int TYPE_INFO = 5;

    private int type;
    private String title;
    private String description;
    private String value;
    private String key;
    private boolean checked;
    private int intValue;
    private float floatValue;

    public SettingItem(int type, String title) {
        this.type = type;
        this.title = title;
    }

    public SettingItem(int type, String title, String description, String value) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.value = value;
        this.key = title.toLowerCase().replace(" ", "_");
    }

    public SettingItem(int type, String title, String description, boolean checked) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.checked = checked;
        this.key = title.toLowerCase().replace(" ", "_");
    }

    public SettingItem(int type, String title, String description, int intValue) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.intValue = intValue;
        this.key = title.toLowerCase().replace(" ", "_");
    }

    public SettingItem(int type, String title, String description, float floatValue) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.floatValue = floatValue;
        this.key = title.toLowerCase().replace(" ", "_");
    }

    // Getters and Setters
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }

    public int getIntValue() { return intValue; }
    public void setIntValue(int intValue) { this.intValue = intValue; }

    public float getFloatValue() { return floatValue; }
    public void setFloatValue(float floatValue) { this.floatValue = floatValue; }
}
