package com.hfstudio.guidenh.guide.compiler;

import java.util.List;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;

/**
 * Inserts a page into the navigation tree. Null parent means top-level category.
 * <p>
 * When {@code requiredMods} is non-null and non-empty, the page is only included in the navigation
 * tree and page indices when every listed mod ID is currently loaded.
 */
@Desugar
public record FrontmatterNavigation(String title, @Nullable ResourceLocation parent, int position,
    @Nullable String iconItemId, int iconItemMeta, @Nullable Map<?, ?> iconComponents,
    @Nullable ResourceLocation iconTextureId, @Nullable List<NavigationIconEntry> iconEntries,
    @Nullable List<ResourceLocation> iconTextureEntries, @Nullable List<String> requiredMods, int loadPriority) {}
