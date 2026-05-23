package com.hfstudio.guidenh.guide.mediawiki;

import java.util.Collections;
import java.util.List;

public class MediaWikiSpecialPageResolver {

    private MediaWikiSpecialPageResolver() {}

    public static boolean isSupported(String specialName) {
        return normalizeSupportedName(specialName) != null;
    }

    public static String normalizeSupportedName(String specialName) {
        if (specialName == null) {
            return null;
        }
        String trimmed = specialName.trim();
        if (MediaWikiPageIds.SPECIAL_ALL_PAGES.equalsIgnoreCase(trimmed)) {
            return MediaWikiPageIds.SPECIAL_ALL_PAGES;
        }
        if (MediaWikiPageIds.SPECIAL_CATEGORIES.equalsIgnoreCase(trimmed)) {
            return MediaWikiPageIds.SPECIAL_CATEGORIES;
        }
        return null;
    }

    public static List<MediaWikiListEntry> resolveEntries(MediaWikiListContext context, String specialName) {
        String normalizedSpecialName = normalizeSupportedName(specialName);
        if (MediaWikiPageIds.SPECIAL_ALL_PAGES.equals(normalizedSpecialName)) {
            return MediaWikiPageListBuilder.buildAllPages(context);
        }
        if (MediaWikiPageIds.SPECIAL_CATEGORIES.equals(normalizedSpecialName)) {
            return MediaWikiPageListBuilder.buildCategories(context);
        }
        return Collections.emptyList();
    }
}
