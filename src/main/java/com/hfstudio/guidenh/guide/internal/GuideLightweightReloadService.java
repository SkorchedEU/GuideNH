package com.hfstudio.guidenh.guide.internal;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;
import com.hfstudio.guidenh.guide.compiler.PageCompiler;
import com.hfstudio.guidenh.guide.compiler.ParsedGuidePage;
import com.hfstudio.guidenh.guide.internal.datadriven.DataDrivenGuideLoader;
import com.hfstudio.guidenh.guide.internal.recipe.NeiAnimationTicker;
import com.hfstudio.guidenh.guide.internal.recipe.RecipeCache;
import com.hfstudio.guidenh.guide.internal.resource.GuideResourceAccess;
import com.hfstudio.guidenh.guide.internal.util.LangUtil;
import com.hfstudio.guidenh.guide.render.GuidePageTexture;

import cpw.mods.fml.common.FMLLog;

public final class GuideLightweightReloadService {

    private GuideLightweightReloadService() {}

    public static void reloadDevelopmentGuides() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return;
        }
        reloadGuides(minecraft.getResourceManager());
    }

    public static void reloadGuides(IResourceManager resourceManager) {
        FMLLog.getLogger()
            .info("[GuideNH] [GuideLightweightReloadService] Reloading guide data...");
        RecipeCache.clear();
        NeiAnimationTicker.clear();
        GuidePageTexture.clear();
        GuideRegistry.setDataDriven(DataDrivenGuideLoader.load());
        var guidePages = new HashMap<ResourceLocation, Map<ResourceLocation, ParsedGuidePage>>();
        var pagePathCache = new LinkedHashMap<String, LinkedHashMap<String, LinkedHashSet<String>>>();

        String language = LangUtil.getCurrentLanguage();

        for (var guide : GuideRegistry.getAll()) {
            var pages = loadPages(
                resourceManager,
                guide.getId(),
                guide.getContentRootFolder(),
                guide.getDefaultLanguage(),
                language,
                pagePathCache);
            guidePages.put(guide.getId(), pages);
        }

        for (var entry : guidePages.entrySet()) {
            GuideRegistry.updatePages(entry.getKey(), entry.getValue());
        }

        try {
            GuideME.getSearch()
                .indexAll();
        } catch (Throwable t) {
            FMLLog.getLogger()
                .warn("[GuideNH] [GuideLightweightReloadService] Failed to reindex search after reload", t);
        }

        FMLLog.getLogger()
            .info(
                "[GuideNH] [GuideLightweightReloadService] Guide reload complete, loaded {} guides",
                guidePages.size());
    }

    /**
     * Scans the guide folder tree and loads all markdown files under {@code assets/<namespace>/<folder>/_<lang>/...}.
     */
    static Map<ResourceLocation, ParsedGuidePage> loadPages(IResourceManager resourceManager, ResourceLocation guideId,
        String folder, String defaultLanguage, @Nullable String currentLanguage) {
        return loadPages(resourceManager, guideId, folder, defaultLanguage, currentLanguage, new LinkedHashMap<>());
    }

    static Map<ResourceLocation, ParsedGuidePage> loadPages(IResourceManager resourceManager, ResourceLocation guideId,
        String folder, String defaultLanguage, @Nullable String currentLanguage,
        Map<String, LinkedHashMap<String, LinkedHashSet<String>>> pagePathCache) {
        var pages = new HashMap<ResourceLocation, ParsedGuidePage>();
        var pagePaths = pagePathsForGuide(guideId, folder, pagePathCache, DataDrivenGuideLoader::discoverPagePaths);
        String lang = currentLanguage != null ? currentLanguage : defaultLanguage;
        String sourceNamespace = guideId.getResourceDomain();
        String sourcePack = "resources:" + sourceNamespace;

        for (var pagePath : pagePaths) {
            ResourceLocation pageId = new ResourceLocation(sourceNamespace, pagePath);

            ParsedGuidePage parsed = tryLoadPage(
                resourceManager,
                sourcePack,
                lang,
                sourceNamespace,
                folder,
                pagePath,
                pageId);
            if (parsed == null && !lang.equals(defaultLanguage)) {
                parsed = tryLoadPage(
                    resourceManager,
                    sourcePack,
                    defaultLanguage,
                    sourceNamespace,
                    folder,
                    pagePath,
                    pageId);
            }
            if (parsed == null) {
                parsed = tryParsePage(
                    resourceManager,
                    sourcePack,
                    defaultLanguage,
                    pageId,
                    new ResourceLocation(sourceNamespace, folder + "/" + pagePath));
            }
            if (parsed != null) {
                pages.put(pageId, parsed);
            } else {
                FMLLog.getLogger()
                    .warn("[GuideNH] [GuideLightweightReloadService] Failed to load guide page {}", pageId);
            }
        }

        FMLLog.getLogger()
            .info("[GuideNH] [GuideLightweightReloadService] Loaded {} pages for folder {}", pages.size(), folder);
        return pages;
    }

    static LinkedHashSet<String> pagePathsForGuide(ResourceLocation guideId, String folder,
        Map<String, LinkedHashMap<String, LinkedHashSet<String>>> pagePathCache,
        Function<String, LinkedHashMap<String, LinkedHashSet<String>>> discoverPagePaths) {
        var pathsByNamespace = pagePathCache.computeIfAbsent(folder, discoverPagePaths);
        var pagePaths = pathsByNamespace.get(guideId.getResourceDomain());
        return pagePaths != null ? pagePaths : new LinkedHashSet<>();
    }

    @Nullable
    private static ParsedGuidePage tryLoadPage(IResourceManager resourceManager, String sourcePack, String language,
        String namespace, String folder, String pagePath, ResourceLocation pageId) {
        return tryParsePageCandidate(
            sourcePack,
            language,
            pageId,
            new ResourceLocation(namespace, folder + "/_" + language + "/" + pagePath));
    }

    @Nullable
    private static ParsedGuidePage tryParsePage(IResourceManager resourceManager, String sourcePack, String language,
        ResourceLocation pageId, ResourceLocation sourceId) {
        byte[] bytes = selectPageCandidate(sourceId);
        if (bytes == null) {
            bytes = GuideResourceAccess.readBytes(resourceManager, sourceId);
        }
        if (bytes == null) {
            return null;
        }
        return parsePageBytes(sourcePack, language, pageId, sourceId, bytes);
    }

    @Nullable
    private static ParsedGuidePage tryParsePageCandidate(String sourcePack, String language, ResourceLocation pageId,
        ResourceLocation sourceId) {
        byte[] bytes = selectPageCandidate(sourceId);
        if (bytes == null) {
            return null;
        }
        return parsePageBytes(sourcePack, language, pageId, sourceId, bytes);
    }

    @Nullable
    private static ParsedGuidePage parsePageBytes(String sourcePack, String language, ResourceLocation pageId,
        ResourceLocation sourceId, byte[] bytes) {
        try {
            return PageCompiler
                .parse(sourcePack, language, pageId, new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) {
            FMLLog.getLogger()
                .error("[GuideNH] [GuideLightweightReloadService] Error parsing page {} from {}", pageId, sourceId, ex);
            return null;
        }
    }

    static @Nullable byte[] selectPageCandidate(ResourceLocation sourceId) {
        return selectPageCandidate(sourceId, DataDrivenGuideLoader.getActiveResourcePacks());
    }

    static @Nullable byte[] selectPageCandidate(ResourceLocation sourceId,
        Iterable<? extends IResourcePack> resourcePacks) {
        PageCandidate winner = null;
        int order = 0;
        for (IResourcePack resourcePack : resourcePacks) {
            byte[] bytes = DataDrivenGuideLoader.readBytes(resourcePack, sourceId);
            if (bytes == null) {
                continue;
            }
            PageCandidate candidate = new PageCandidate(bytes, readLoadPriority(sourceId, bytes), order++);
            if (winner == null || candidate.shouldReplace(winner)) {
                winner = candidate;
            }
        }
        return winner != null ? winner.bytes() : null;
    }

    static int readLoadPriority(ResourceLocation sourceId, byte[] bytes) {
        String source = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        var frontmatter = PageCompiler.parseFrontmatterFromSource(sourceId, PageCompiler.normalizeLineEndings(source));
        var navigation = frontmatter.navigationEntry();
        return navigation != null ? navigation.loadPriority() : 0;
    }

    @Desugar
    private record PageCandidate(byte[] bytes, int priority, int order) {

        boolean shouldReplace(PageCandidate previous) {
            return priority > previous.priority || priority == previous.priority && order > previous.order;
        }
    }
}
