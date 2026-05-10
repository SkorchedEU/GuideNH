package com.hfstudio.guidenh.guide.scene;

import net.minecraft.item.ItemStack;

public class SceneBlockStatsEntry {

    private final String key;
    private final ItemStack stack;
    private final String label;
    private int count;
    private int cachedTextWidth = -1;
    private int cachedEllipsizedTextMaxWidth = -1;
    private String cachedEllipsizedSource = "";
    private String cachedEllipsizedText = "";

    public SceneBlockStatsEntry(String key, ItemStack stack, String label, int count) {
        this.key = key != null ? key : "";
        this.stack = stack;
        this.label = label != null ? label : "";
        this.count = Math.max(0, count);
    }

    public String getKey() {
        return key;
    }

    public ItemStack getStack() {
        return stack;
    }

    public String getLabel() {
        return label;
    }

    public int getCount() {
        return count;
    }

    public void addCount(int delta) {
        this.count = Math.max(0, this.count + delta);
    }

    public int getCachedTextWidth() {
        return cachedTextWidth;
    }

    public void setCachedTextWidth(int cachedTextWidth) {
        this.cachedTextWidth = cachedTextWidth;
    }

    public String getCachedEllipsizedText(String source, int maxWidth) {
        if (source == null) {
            source = "";
        }
        if (maxWidth == cachedEllipsizedTextMaxWidth && source.equals(cachedEllipsizedSource)) {
            return cachedEllipsizedText;
        }
        return null;
    }

    public void setCachedEllipsizedText(String source, int maxWidth, String text) {
        cachedEllipsizedSource = source != null ? source : "";
        cachedEllipsizedTextMaxWidth = maxWidth;
        cachedEllipsizedText = text != null ? text : "";
    }
}
