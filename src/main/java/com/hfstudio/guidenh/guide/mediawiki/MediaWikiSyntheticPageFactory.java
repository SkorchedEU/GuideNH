package com.hfstudio.guidenh.guide.mediawiki;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import com.hfstudio.guidenh.guide.Guide;
import com.hfstudio.guidenh.guide.compiler.PageCompiler;
import com.hfstudio.guidenh.guide.compiler.ParsedGuidePage;
import com.hfstudio.guidenh.guide.indices.CategoryIndex;
import com.hfstudio.guidenh.guide.internal.MutableGuide;

public class MediaWikiSyntheticPageFactory {

    private MediaWikiSyntheticPageFactory() {}

    public static Map<ResourceLocation, ParsedGuidePage> buildPages(Guide guide, Collection<ParsedGuidePage> pages,
        CategoryIndex categoryIndex) {
        String language = resolveLanguage(guide, pages);
        String namespace = guide.getDefaultNamespace();
        String sourcePack = guide.getId()
            .toString();

        var syntheticPages = new LinkedHashMap<ResourceLocation, ParsedGuidePage>();
        syntheticPages.put(
            MediaWikiPageIds.specialPageId(namespace, MediaWikiPageIds.SPECIAL_ALL_PAGES),
            parseSyntheticPage(
                sourcePack,
                language,
                MediaWikiPageIds.specialPageId(namespace, MediaWikiPageIds.SPECIAL_ALL_PAGES),
                buildSpecialSource(MediaWikiPageIds.SPECIAL_ALL_PAGES)));
        syntheticPages.put(
            MediaWikiPageIds.specialPageId(namespace, MediaWikiPageIds.SPECIAL_CATEGORIES),
            parseSyntheticPage(
                sourcePack,
                language,
                MediaWikiPageIds.specialPageId(namespace, MediaWikiPageIds.SPECIAL_CATEGORIES),
                buildSpecialSource(MediaWikiPageIds.SPECIAL_CATEGORIES)));

        for (String categoryName : categoryIndex.getCategoryNames()) {
            ResourceLocation pageId = MediaWikiPageIds.categoryPageId(namespace, categoryName);
            syntheticPages
                .put(pageId, parseSyntheticPage(sourcePack, language, pageId, buildCategorySource(categoryName)));
        }
        return syntheticPages;
    }

    private static ParsedGuidePage parseSyntheticPage(String sourcePack, String language, ResourceLocation pageId,
        String source) {
        return PageCompiler.parse(sourcePack, language, pageId, source);
    }

    private static String resolveLanguage(Guide guide, Collection<ParsedGuidePage> pages) {
        for (ParsedGuidePage page : pages) {
            if (page != null && page.getLanguage() != null
                && !page.getLanguage()
                    .isEmpty()) {
                return page.getLanguage();
            }
        }
        if (guide instanceof MutableGuide mutableGuide) {
            return mutableGuide.getDefaultLanguage();
        }
        return "en_us";
    }

    private static String buildCategorySource(String categoryName) {
        StringBuilder source = new StringBuilder();
        source.append("# ")
            .append(MediaWikiPageIds.toCategoryTitle(categoryName))
            .append("\n\n")
            .append("<Category name=\"")
            .append(escapeAttribute(categoryName))
            .append("\" rows=\"")
            .append(MediaWikiListPlanner.DEFAULT_ROWS)
            .append("\" />\n");
        return source.toString();
    }

    private static String buildSpecialSource(String specialName) {
        StringBuilder source = new StringBuilder();
        source.append("# ")
            .append(MediaWikiPageIds.toSpecialTitle(specialName))
            .append("\n\n")
            .append("<Special name=\"")
            .append(specialName)
            .append("\" rows=\"")
            .append(MediaWikiListPlanner.DEFAULT_ROWS)
            .append("\" />\n");
        return source.toString();
    }

    private static String escapeAttribute(String value) {
        return value.replace("&", "&amp;")
            .replace("\"", "&quot;");
    }
}
