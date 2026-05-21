package com.hfstudio.guidenh.guide.internal.util;

import java.util.Locale;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public class LangUtil {

    public static final String ENGLISH_LANGUAGE = "en_us";
    public static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_\\-]*");

    private LangUtil() {}

    public static String normalizeLanguage(String language) {
        return language.toLowerCase(Locale.ROOT);
    }

    public static boolean isLanguageCode(@Nullable String language) {
        return language != null && LANGUAGE_CODE_PATTERN.matcher(normalizeLanguage(language))
            .matches();
    }

    public static String getCurrentLanguage() {
        var client = Minecraft.getMinecraft();
        if (client != null && client.gameSettings != null) {
            return normalizeLanguage(client.gameSettings.language);
        }
        return ENGLISH_LANGUAGE;
    }

    public static String toLanguageRegistryCode(String language) {
        String normalized = normalizeLanguage(language);
        int separator = normalized.indexOf('_');
        if (separator < 0 || separator + 1 >= normalized.length()) {
            return normalized;
        }

        StringBuilder builder = new StringBuilder(normalized.length());
        builder.append(normalized, 0, separator);
        int segmentStart = separator + 1;
        while (segmentStart < normalized.length()) {
            int nextSeparator = normalized.indexOf('_', segmentStart);
            int segmentEnd = nextSeparator >= 0 ? nextSeparator : normalized.length();
            builder.append('_')
                .append(
                    normalized.substring(segmentStart, segmentEnd)
                        .toUpperCase(Locale.ROOT));
            if (nextSeparator < 0) {
                break;
            }
            segmentStart = nextSeparator + 1;
        }
        return builder.toString();
    }

    public static ResourceLocation getTranslatedAsset(ResourceLocation assetId, String language) {
        return new ResourceLocation(
            assetId.getResourceDomain(),
            "_" + normalizeLanguage(language) + "/" + assetId.getResourcePath());
    }

    public static ResourceLocation stripLangFromPageId(ResourceLocation pageId) {
        String path = pageId.getResourcePath();
        var language = extractLangPrefix(path);
        if (language == null) {
            return pageId;
        }

        return new ResourceLocation(pageId.getResourceDomain(), path.substring(language.length() + 2));
    }

    @Nullable
    public static String getLangFromPageId(ResourceLocation pageId) {
        return extractLangPrefix(pageId.getResourcePath());
    }

    @Nullable
    public static String extractLangPrefix(String path) {
        int firstSep = path.indexOf("/");
        if (firstSep <= 1 || path.charAt(0) != '_' || firstSep + 1 >= path.length()) {
            return null;
        }

        var potentialLanguage = normalizeLanguage(path.substring(1, firstSep));
        return isLanguageCode(potentialLanguage) ? potentialLanguage : null;
    }
}
