package com.hfstudio.guidenh.guide.mediawiki;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.guidenh.guide.Guide;
import com.hfstudio.guidenh.guide.compiler.IdUtils;
import com.hfstudio.guidenh.guide.compiler.ParsedGuidePage;
import com.hfstudio.guidenh.guide.indices.CategoryIndex;
import com.hfstudio.guidenh.guide.internal.datadriven.DataDrivenGuideLoader;
import com.hfstudio.guidenh.guide.internal.util.LangUtil;
import com.hfstudio.guidenh.integration.betterquesting.QuestIndex;
import com.hfstudio.guidenh.libs.unist.UnistPoint;

public class MediaWikiSpecialDataIndexer {

    private static final Pattern EXTERNAL_LINK_PATTERN = Pattern.compile("https?://[^\\s)>\\]]+");
    private static final Pattern RESOURCE_REFERENCE_PATTERN = Pattern
        .compile("(?:src|texture|image|csv|sound|file)\\s*=\\s*[\"']([^\"'#?][^\"']*)[\"']");
    private static final String[] ASSET_EXTENSIONS = { ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".csv",
        ".json", ".mcmeta", ".txt", ".lang", ".ogg", ".mp3", ".wav", ".mermaid" };

    public MediaWikiSpecialDataIndex build(Guide guide, Collection<ParsedGuidePage> pages,
        CategoryIndex categoryIndex) {
        LinkedHashMap<ResourceLocation, ParsedGuidePage> normalPages = new LinkedHashMap<>();
        if (pages != null) {
            for (ParsedGuidePage page : pages) {
                if (page == null || MediaWikiPageIds.isSyntheticPage(page.getId())) {
                    continue;
                }
                normalPages.put(page.getId(), page);
            }
        }
        Map<String, List<ResourceLocation>> fileUsageByPath = buildFileUsage(normalPages.values());
        Map<ResourceLocation, Long> assetSizesById = buildAssetSizes(guide);

        return new MediaWikiSpecialDataIndex(
            Collections.unmodifiableMap(normalPages),
            Collections.unmodifiableMap(buildTranslations(normalPages.values())),
            Collections.unmodifiableMap(buildPageProperties(normalPages.values())),
            Collections.unmodifiableMap(buildExternalLinks(normalPages.values())),
            Collections.unmodifiableMap(buildPageSizes(normalPages.values())),
            Collections.unmodifiableMap(assetSizesById),
            Collections.unmodifiableMap(fileUsageByPath),
            Collections.unmodifiableMap(buildLintIssues(normalPages.values())),
            Collections.unmodifiableMap(buildAmbiguousItemBindings(normalPages.values())),
            Collections.unmodifiableMap(buildOverrides(normalPages.values())),
            Collections.unmodifiableSet(buildUnusedFiles(assetSizesById.keySet(), fileUsageByPath.keySet())));
    }

    private Map<ResourceLocation, List<ResourceLocation>> buildTranslations(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<ResourceLocation, List<ResourceLocation>> bySource = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page == null) {
                continue;
            }
            ResourceLocation sourcePageId = LangUtil.stripLangFromPageId(page.getId());
            bySource.computeIfAbsent(sourcePageId, ignored -> new ArrayList<>())
                .add(page.getId());
        }
        return bySource;
    }

    private Map<ResourceLocation, Set<String>> buildPageProperties(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<ResourceLocation, Set<String>> byPage = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page == null || page.getFrontmatter() == null) {
                continue;
            }
            byPage.put(
                page.getId(),
                new LinkedHashSet<>(
                    page.getFrontmatter()
                        .additionalProperties()
                        .keySet()));
        }
        return byPage;
    }

    private Map<ResourceLocation, List<String>> buildExternalLinks(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<ResourceLocation, List<String>> linksByPage = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page == null || page.getSource() == null
                || page.getSource()
                    .isEmpty()) {
                continue;
            }
            Matcher matcher = EXTERNAL_LINK_PATTERN.matcher(page.getSource());
            LinkedHashSet<String> links = new LinkedHashSet<>();
            while (matcher.find()) {
                links.add(matcher.group());
            }
            if (!links.isEmpty()) {
                linksByPage.put(page.getId(), new ArrayList<>(links));
            }
        }
        return linksByPage;
    }

    private Map<ResourceLocation, Long> buildPageSizes(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<ResourceLocation, Long> sizes = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page != null) {
                sizes.put(
                    page.getId(),
                    page.getSource() != null ? (long) page.getSource()
                        .length() : 0L);
            }
        }
        return sizes;
    }

    private Map<ResourceLocation, Long> buildAssetSizes(Guide guide) {
        LinkedHashMap<ResourceLocation, Long> sizes = new LinkedHashMap<>();
        for (IResourcePack resourcePack : DataDrivenGuideLoader.getActiveResourcePacks()) {
            File resourcePackFile = DataDrivenGuideLoader.getResourcePackFile(resourcePack);
            if (resourcePackFile == null || !resourcePackFile.exists()) {
                continue;
            }
            if (resourcePackFile.isDirectory()) {
                collectAssetSizesFromDirectory(guide, resourcePackFile, sizes);
            } else {
                collectAssetSizesFromZip(guide, resourcePackFile, sizes);
            }
        }
        return sizes;
    }

    private void collectAssetSizesFromDirectory(Guide guide, File resourcePackRoot, Map<ResourceLocation, Long> sizes) {
        File guideRoot = new File(
            new File(new File(resourcePackRoot, "assets"), guide.getDefaultNamespace()),
            guide.getContentRootFolder());
        if (!guideRoot.isDirectory()) {
            return;
        }
        collectAssetSizesRecursively(guide, guideRoot, guideRoot, sizes);
    }

    private void collectAssetSizesRecursively(Guide guide, File guideRoot, File current,
        Map<ResourceLocation, Long> sizes) {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectAssetSizesRecursively(guide, guideRoot, child, sizes);
                continue;
            }
            String relativePath = guideRoot.toPath()
                .relativize(child.toPath())
                .toString()
                .replace(File.separatorChar, '/');
            if (shouldIndexAsset(relativePath)) {
                sizes.putIfAbsent(new ResourceLocation(guide.getDefaultNamespace(), relativePath), child.length());
            }
        }
    }

    private void collectAssetSizesFromZip(Guide guide, File resourcePackFile, Map<ResourceLocation, Long> sizes) {
        String prefix = "assets/" + guide.getDefaultNamespace() + "/" + guide.getContentRootFolder() + "/";
        try (ZipFile zip = new ZipFile(resourcePackFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();
                if (!path.startsWith(prefix)) {
                    continue;
                }
                String relativePath = path.substring(prefix.length());
                if (shouldIndexAsset(relativePath)) {
                    sizes.putIfAbsent(new ResourceLocation(guide.getDefaultNamespace(), relativePath), entry.getSize());
                }
            }
        } catch (IOException ignored) {}
    }

    private Map<ResourceLocation, List<MediaWikiSpecialLintIssue>> buildLintIssues(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<ResourceLocation, List<MediaWikiSpecialLintIssue>> issues = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page != null && page.hasParseFailure()) {
                issues.put(
                    page.getId(),
                    Collections.singletonList(
                        new MediaWikiSpecialLintIssue(
                            page.getParseFailureMessage(),
                            resolveLineNumber(page.getParseFailureFrom()))));
            }
        }
        return issues;
    }

    private Integer resolveLineNumber(UnistPoint point) {
        if (point == null || point.line() <= 0) {
            return null;
        }
        return point.line();
    }

    private Map<String, List<ResourceLocation>> buildAmbiguousItemBindings(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<String, List<ResourceLocation>> bindings = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page == null) {
                continue;
            }
            appendBindings(bindings, page, "item:", readStringList(page, "item_ids"));
            appendBindings(bindings, page, "ore:", readStringList(page, "ore_ids"));
            appendQuestBindings(bindings, page);
        }

        bindings.entrySet()
            .removeIf(
                entry -> entry.getValue() == null || entry.getValue()
                    .size() < 2);
        return bindings;
    }

    private Map<String, List<ResourceLocation>> buildFileUsage(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<String, List<ResourceLocation>> usage = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page == null || page.getSource() == null
                || page.getSource()
                    .isEmpty()) {
                continue;
            }
            LinkedHashSet<String> pageAssets = new LinkedHashSet<>();
            Matcher matcher = RESOURCE_REFERENCE_PATTERN.matcher(page.getSource());
            while (matcher.find()) {
                String rawPath = matcher.group(1);
                ResourceLocation assetId = resolveAssetId(page, rawPath);
                if (assetId != null) {
                    pageAssets.add(assetId.toString());
                }
            }
            for (String assetKey : pageAssets) {
                usage.computeIfAbsent(assetKey, ignored -> new ArrayList<>())
                    .add(page.getId());
            }
        }
        return usage;
    }

    private Map<ResourceLocation, List<String>> buildOverrides(Collection<ParsedGuidePage> pages) {
        LinkedHashMap<ResourceLocation, List<String>> overridesByPage = new LinkedHashMap<>();
        LinkedHashMap<String, List<ParsedGuidePage>> pagesBySourcePath = new LinkedHashMap<>();
        for (ParsedGuidePage page : pages) {
            if (page == null) {
                continue;
            }
            pagesBySourcePath.computeIfAbsent(
                page.getId()
                    .toString(),
                ignored -> new ArrayList<>())
                .add(page);
        }

        for (List<ParsedGuidePage> variants : pagesBySourcePath.values()) {
            if (variants.size() < 2) {
                continue;
            }
            variants.sort((left, right) -> Integer.compare(resolveLoadPriority(right), resolveLoadPriority(left)));
            for (ParsedGuidePage page : variants) {
                ArrayList<String> descriptions = new ArrayList<>(variants.size());
                for (ParsedGuidePage variant : variants) {
                    descriptions.add(
                        resolveLoadPriority(variant) + " | " + variant.getSourcePack() + " | " + variant.getLanguage());
                }
                overridesByPage.put(page.getId(), descriptions);
            }
        }
        return overridesByPage;
    }

    private Set<String> buildUnusedFiles(Set<ResourceLocation> assets, Set<String> usedAssetKeys) {
        LinkedHashSet<String> unused = new LinkedHashSet<>();
        for (ResourceLocation asset : assets) {
            if (asset == null) {
                continue;
            }
            String key = asset.toString();
            if (!usedAssetKeys.contains(key)) {
                unused.add(key);
            }
        }
        return unused;
    }

    private void appendBindings(Map<String, List<ResourceLocation>> bindings, ParsedGuidePage page, String prefix,
        List<String> ids) {
        for (String id : ids) {
            if (id == null || id.trim()
                .isEmpty()) {
                continue;
            }
            bindings.computeIfAbsent(prefix + id.trim(), ignored -> new ArrayList<>())
                .add(page.getId());
        }
    }

    private void appendQuestBindings(Map<String, List<ResourceLocation>> bindings, ParsedGuidePage page) {
        for (var questAnchor : QuestIndex.getQuestAnchors(page)) {
            bindings.computeIfAbsent("quest:" + questAnchor.getLeft(), ignored -> new ArrayList<>())
                .add(
                    questAnchor.getRight()
                        .pageId());
        }
    }

    private List<String> readStringList(ParsedGuidePage page, String key) {
        Object value = page.getFrontmatter()
            .additionalProperties()
            .get(key);
        if (!(value instanceof List<?>values)) {
            return Collections.emptyList();
        }
        ArrayList<String> result = new ArrayList<>(values.size());
        for (Object entry : values) {
            if (entry instanceof String text && !text.trim()
                .isEmpty()) {
                result.add(text.trim());
            }
        }
        return result;
    }

    private ResourceLocation resolveAssetId(ParsedGuidePage page, String rawPath) {
        if (rawPath == null) {
            return null;
        }
        String trimmed = rawPath.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("http://")
            || trimmed.startsWith("https://")
            || trimmed.startsWith("#")) {
            return null;
        }
        if (!shouldIndexAsset(trimmed)) {
            return null;
        }
        try {
            return IdUtils.resolveLink(trimmed, page.getId());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean shouldIndexAsset(String path) {
        String lowered = path.toLowerCase();
        for (String extension : ASSET_EXTENSIONS) {
            if (lowered.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private int resolveLoadPriority(ParsedGuidePage page) {
        if (page == null || page.getFrontmatter() == null
            || page.getFrontmatter()
                .navigationEntry() == null) {
            return 0;
        }
        return page.getFrontmatter()
            .navigationEntry()
            .loadPriority();
    }
}
