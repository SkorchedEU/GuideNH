package com.hfstudio.guidenh.guide.sound;

import java.util.Locale;

public enum GuideSoundTrigger {

    CLICK,
    HOVER,
    ENTER;

    public static GuideSoundTrigger parse(String value, GuideSoundTrigger defaultValue) {
        if (value == null || value.trim()
            .isEmpty()) {
            return defaultValue;
        }
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "click":
                return CLICK;
            case "hover":
                return HOVER;
            case "enter":
                return ENTER;
            default:
                return defaultValue;
        }
    }
}
