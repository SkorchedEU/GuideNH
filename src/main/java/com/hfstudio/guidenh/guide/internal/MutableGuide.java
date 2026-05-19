package com.hfstudio.guidenh.guide.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.hfstudio.guidenh.guide.Guide;
import com.hfstudio.guidenh.guide.GuideItemSettings;
import com.hfstudio.guidenh.guide.GuidePage;
import com.hfstudio.guidenh.guide.GuidePageChange;
import com.hfstudio.guidenh.guide.PageAnchor;
import com.hfstudio.guidenh.guide.compiler.PageCompiler;
import com.hfstudio.guidenh.guide.compiler.ParsedGuidePage;
import com.hfstudio.guidenh.guide.extensions.ExtensionCollection;
import com.hfstudio.guidenh.guide.indices.PageIndex;
import com.hfstudio.guidenh.guide.internal.home.GuideScreenHomeHistory;
import com.hfstudio.guidenh.guide.internal.resource.GuideResourceAccess;
import com.hfstudio.guidenh.guide.internal.util.LangUtil;
import com.hfstudio.guidenh.guide.navigation.NavigationNode;
import com.hfstudio.guidenh.guide.navigation.NavigationTree;

import cpw.mods.fml.common.FMLLog;

/**
 * Encapsulates a Guide, which consists of a collection of Markdown pages and associated content, loaded from a
 * guide-specific subdirectory of resource packs.
 */
public class MutableGuide implements Guide, GuideDevWatcherPump.TickableGuide {

    public static final String ACTIVE_CLIENT_WORLD_REQUIRED_MESSAGE = "active client world";

    private final ResourceLocation id;
    private final String defaultNamespace;
    private final String folder;
    private final String defaultLanguage;
    private final Map<ResourceLocation, ParsedGuidePage> developmentPages = new HashMap<>();
    private final Map<ResourceLocation, GuidePageFailure> pageFailures = new HashMap<>();
    private final Map<Class<?>, PageIndex> indices;
    private NavigationTree navigationTree = new NavigationTree();
    /**
     * These are only loaded for the current language and optionally supplemented by language-neutral pages.
     */
    private Map<ResourceLocation, ParsedGuidePage> pages;
    private final Map<ParsedGuidePage, GuidePage> compiledPages = Collections.synchronizedMap(new WeakHashMap<>());
    private final ExtensionCollection extensions;
    private final boolean availableToOpenHotkey;
    private final GuideItemSettings itemSettings;
    private final GuideDevelopmentSourceLayout developmentSourceLayout;

    @Nullable
    private final Path developmentSourceFolder;
    @Nullable
    private final String developmentSourceNamespace;

    @Nullable
    private GuideSourceWatcher watcher;

    public MutableGuide(ResourceLocation id, String defaultNamespace, String folder, String defaultLanguage,
        @Nullable Path developmentSourceFolder, @Nullable String developmentSourceNamespace,
        Map<Class<?>, PageIndex> indices, ExtensionCollection extensions, boolean availableToOpenHotkey,
        GuideItemSettings itemSettings) {
        this.id = id;
        this.defaultNamespace = defaultNamespace;
        this.folder = folder;
        this.defaultLanguage = defaultLanguage;
        this.developmentSourceFolder = developmentSourceFolder;
        this.developmentSourceNamespace = developmentSourceNamespace;
        this.developmentSourceLayout = detectDevelopmentSourceLayout(developmentSourceFolder);
        this.indices = indices;
        this.extensions = extensions;
        this.availableToOpenHotkey = availableToOpenHotkey;
        this.itemSettings = itemSettings;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    /**
     * The resource pack subfolder that is the content root for this guide.
     */
    @Override
    public String getContentRootFolder() {
        return folder;
    }

    public static ResourceLocation resolveTranslatedAssetId(ResourceLocation assetId, String language) {
        if (assetId.getResourcePath()
            .startsWith("assets/")) {
            return assetId;
        }
        return LangUtil.getTranslatedAsset(assetId, language);
    }

    public static ResourceLocation resolveGuideAssetId(ResourceLocation assetId, String folder) {
        String path = assetId.getResourcePath();
        if (path.startsWith("assets/") || path.startsWith(folder + "/")) {
            return assetId;
        }
        return new ResourceLocation(assetId.getResourceDomain(), folder + "/" + path);
    }

    @Override
    public <T extends PageIndex> T getIndex(Class<T> indexClass) {
        var index = indices.get(indexClass);
        if (index == null) {
            throw new IllegalArgumentException("No index of type " + indexClass + " is registered with this guide.");
        }
        return indexClass.cast(index);
    }

    @Override
    @Nullable
    public ParsedGuidePage getParsedPage(ResourceLocation id) {
        if (pages == null) {
            FMLLog.getLogger()
                .warn("[GuideNH] [MutableGuide] Can't get page {}. Pages not loaded yet.", id);
            return null;
        }

        return developmentPages.getOrDefault(id, pages.get(id));
    }

    @Override
    @Nullable
    public GuidePage getPage(ResourceLocation id) {
        var parsedPage = getParsedPage(id);
        if (parsedPage == null) {
            return null;
        }

        GuidePage compiledPage;
        try {
            synchronized (compiledPages) {
                compiledPage = compiledPages.get(parsedPage);
                if (compiledPage == null) {
                    compiledPage = PageCompiler.compile(this, extensions, parsedPage);
                    compiledPages.put(parsedPage, compiledPage);
                }
            }
            clearCompileFailure(id);
        } catch (Throwable t) {
            recordCompileFailure(id, buildCompileFailureText(id, t));
            FMLLog.getLogger()
                .error("[GuideNH] [MutableGuide] Failed to compile guide page {}", id, t);
            compiledPage = buildFailurePage(parsedPage, pageFailures.get(id));
        }
        compiledPage.prepareForDisplay();
        return compiledPage;
    }

    @Override
    public Collection<ParsedGuidePage> getPages() {
        if (pages == null) {
            throw new IllegalStateException("Pages are not loaded yet.");
        }

        if (developmentPages.isEmpty()) {
            return this.pages.values();
        }

        var pages = new LinkedHashMap<>(this.pages);
        pages.putAll(developmentPages);
        return pages.values();
    }

    @Override
    public byte[] loadAsset(ResourceLocation id) {
        var language = LangUtil.getCurrentLanguage();
        var result = loadAssetInternal(LangUtil.getTranslatedAsset(id, language));
        if (result != null) return result;

        if (!Objects.equals(language, defaultLanguage)) {
            result = loadAssetInternal(LangUtil.getTranslatedAsset(id, defaultLanguage));
            if (result != null) return result;
        }

        return loadAssetInternal(id);
    }

    private byte @Nullable [] loadAssetInternal(ResourceLocation id) {
        // Also load assets from the development sources folder.
        if (canLoadDevelopmentSource(id)) {
            var path = resolveDevelopmentSourcePath(id);
            try {
                return Files.readAllBytes(path);
            } catch (NoSuchFileException ignored) {} catch (IOException e) {
                FMLLog.getLogger()
                    .error("[GuideNH] [MutableGuide] Failed to open guidebook asset {}", path);
                return null;
            }
        }

        // Transform id such that the path is prefixed with the source folder for the guidebook assets
        id = new ResourceLocation(id.getResourceDomain(), folder + "/" + id.getResourcePath());

        var bytes = GuideResourceAccess.readBytes(
            Minecraft.getMinecraft()
                .getResourceManager(),
            id);
        if (bytes != null) {
            return bytes;
        }

        FMLLog.getLogger()
            .error("[GuideNH] [MutableGuide] Failed to open guidebook asset {}", id);
        return null;
    }

    @Override
    public NavigationTree getNavigationTree() {
        return navigationTree;
    }

    @Override
    public boolean pageExists(ResourceLocation pageId) {
        return developmentPages.containsKey(pageId) || pages != null && pages.containsKey(pageId);
    }

    @Override
    public boolean isPageFailed(ResourceLocation pageId) {
        return pageFailures.containsKey(pageId);
    }

    /**
     * Returns the on-disk path for a given guidebook resource (i.e. page, asset) if development mode is enabled and the
     * resource exists in the development source folder.
     *
     * @return null if development mode is not enabled or the resource doesn't exist in the development sources.
     */
    @Nullable
    public Path getDevelopmentSourcePath(ResourceLocation id) {
        if (canLoadDevelopmentSource(id)) {
            var path = resolveDevelopmentSourcePath(id);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    @Nullable
    public Path getDevelopmentSourceFolder() {
        return developmentSourceFolder;
    }

    @Override
    public ExtensionCollection getExtensions() {
        return extensions;
    }

    /**
     * @return True if this guide should be considered for use in the global open guide hotkey.
     */
    public boolean isAvailableToOpenHotkey() {
        return availableToOpenHotkey;
    }

    public void watchDevelopmentSources() {
        if (watcher != null) {
            return;
        }

        watcher = new GuideSourceWatcher(developmentSourceNamespace, folder, defaultLanguage, developmentSourceFolder);
        Runtime.getRuntime()
            .addShutdownHook(new Thread(watcher::close));
    }

    @Override
    public boolean hasDevelopmentSources() {
        return watcher != null;
    }

    private final Deque<ResourceLocation> warmupPageQueue = new ArrayDeque<>();
    private final HashSet<ResourceLocation> queuedWarmupPages = new HashSet<>();

    /**
     * Called each client tick to pre-compile one representative page in the background, eliminating the 5-6 second
     * freeze on first guide open. Compilation is deferred until an active server connection is available (required for
     * guidebook preview world creation).
     */
    public void tickWarmup() {
        if (pages == null) {
            return;
        }
        // GuidebookFakeWorld (needed for <GameScene> blocks) requires an active NetHandler.
        // Only attempt warmup once we're actually in-game.
        if (Minecraft.getMinecraft()
            .getNetHandler() == null) {
            return;
        }
        ResourceLocation pageId = pollWarmupPage();
        if (pageId == null) {
            return;
        }
        try {
            warmPage(pageId);
        } catch (Throwable t) {
            if (!isDeferrableWarmPageFailure(t)) {
                FMLLog.getLogger()
                    .error("[GuideNH] [MutableGuide] Failed to pre-warm guide page {}", pageId, t);
            }
            // Deferrable failures are expected when world is partially loaded; the page will compile on first open.
        }
    }

    public void resetWarmup() {
        rebuildWarmupQueue();
    }

    public void tick() {
        if (pages == null || watcher == null) {
            return; // Do nothing while pages haven't been loaded yet
        }

        var changes = watcher.takeChanges();
        if (!changes.isEmpty()) {
            applyChanges(changes);
        }
    }

    @Override
    public void tickDevelopmentSources() {
        tick();
    }

    private void applyChanges(List<GuidePageChange> changes) {
        var initialPages = new HashMap<>(developmentPages);
        var deduplicatedChanges = new ArrayList<GuidePageChange>(changes.size());
        var seenPageIds = new LinkedHashSet<ResourceLocation>();
        for (int i = changes.size() - 1; i >= 0; i--) {
            var change = changes.get(i);
            if (change == null || !seenPageIds.add(change.pageId())) {
                continue;
            }
            deduplicatedChanges.add(0, change);
        }

        // Enrich each change with the previous page data while we process them
        for (int i = 0; i < deduplicatedChanges.size(); i++) {
            var change = deduplicatedChanges.get(i);
            var pageId = change.pageId();

            var newPage = change.newPage();
            if (newPage != null) {
                developmentPages.put(pageId, newPage);
            } else {
                developmentPages.remove(pageId);
            }
            removeCompiledPage(pageId);
            prioritizeWarmupPage(pageId);

            deduplicatedChanges
                .set(i, new GuidePageChange(change.language(), pageId, initialPages.get(pageId), newPage));
        }

        // Allow indices to rebuild
        var allPages = new ArrayList<ParsedGuidePage>(pages.size() + developmentPages.size());
        allPages.addAll(pages.values());
        allPages.addAll(developmentPages.values());
        allPages.removeIf(
            p -> !NavigationTree.areModRequirementsMet(
                p.getFrontmatter()
                    .navigationEntry()));
        for (var index : indices.values()) {
            if (index.supportsUpdate()) {
                index.update(allPages, deduplicatedChanges);
            } else {
                index.rebuild(allPages);
            }
        }

        // Rebuild navigation
        this.navigationTree = buildNavigation();
        GuideRegistry.invalidateMergedNavigationTree();
        refreshPageFailures();

        // Reload the current page if it has been changed
        var guideScreen = GuideScreen.current();
        if (guideScreen != null) {
            var currentId = guideScreen.getCurrentPageId();
            if (currentId != null) {
                for (var change : deduplicatedChanges) {
                    if (currentId.equals(change.pageId())) {
                        guideScreen.reloadPage();
                        break;
                    }
                }
            }
        }
    }

    private NavigationTree buildNavigation() {
        if (developmentPages.isEmpty()) {
            return NavigationTree.build(this, pages.values());
        } else {
            var allPages = new HashMap<>(pages);
            allPages.putAll(developmentPages);
            return NavigationTree.build(this, allPages.values());
        }
    }

    public void validateAll() {
        // Iterate and compile all pages to warn about errors on startup
        for (var entry : developmentPages.entrySet()) {
            FMLLog.getLogger()
                .info("[GuideNH] [MutableGuide] Compiling {}", entry.getKey());
            getPage(entry.getKey());
        }
    }

    public void rebuildIndices() {
        var allPages = new ArrayList<>(getPages());
        allPages.removeIf(
            p -> !NavigationTree.areModRequirementsMet(
                p.getFrontmatter()
                    .navigationEntry()));
        for (var index : indices.values()) {
            index.rebuild(allPages);
        }
    }

    public void setPages(Map<ResourceLocation, ParsedGuidePage> pages) {
        this.pages = Collections.unmodifiableMap(new HashMap<>(pages));
        compiledPages.clear();

        if (watcher != null) {
            watcher.clearChanges(); // Since we'll load them all now, ignore all changes up to now

            for (var page : watcher.loadAll()) {
                developmentPages.put(page.getId(), page);
            }
        }

        rebuildIndices();
        navigationTree = buildNavigation();
        GuideRegistry.invalidateMergedNavigationTree();
        refreshPageFailures();
        rebuildWarmupQueue();
        // Do not eagerly compile pages here. Some packs register or rewrite recipes
        // during FMLLoadComplete, after the initial resource reload has already parsed the guide.
        // Deferring compilation until first display avoids caching stale "missing recipe" error blocks.

        var guideScreen = GuideScreen.current();
        if (guideScreen != null && guideScreen.isShowingGuide(getId())) {
            guideScreen.reloadPage();
        }
    }

    public void applyEditorPage(ParsedGuidePage parsedPage) {
        if (parsedPage == null) {
            return;
        }
        ResourceLocation pageId = parsedPage.getId();
        developmentPages.put(pageId, parsedPage);
        removeCompiledPage(pageId);
        prioritizeWarmupPage(pageId);
        if (parsedPage.hasParseFailure()) {
            recordParseFailure(parsedPage);
        } else {
            clearCompileFailure(pageId);
            clearParseFailure(pageId);
        }
    }

    private void removeCompiledPage(ResourceLocation pageId) {
        synchronized (compiledPages) {
            compiledPages.keySet()
                .removeIf(page -> page != null && pageId.equals(page.getId()));
        }
    }

    private void rebuildWarmupQueue() {
        warmupPageQueue.clear();
        queuedWarmupPages.clear();
        queueWarmupPage(resolveRememberedWarmupPageId());
        queueWarmupBookmarkedPages();
        queueWarmupHistoryPages();
        ResourceLocation firstNavigationPage = resolveWarmupPageId();
        if (firstNavigationPage != null) {
            queueWarmupPage(firstNavigationPage);
        }
        if (pages != null) {
            for (ParsedGuidePage parsedPage : pages.values()) {
                queueWarmupPage(parsedPage.getId());
            }
        }
        for (ParsedGuidePage parsedPage : developmentPages.values()) {
            queueWarmupPage(parsedPage.getId());
        }
    }

    private void prioritizeWarmupPage(ResourceLocation pageId) {
        if (pageId == null || compiledPageExists(pageId)) {
            return;
        }
        if (queuedWarmupPages.remove(pageId)) {
            warmupPageQueue.remove(pageId);
        }
        warmupPageQueue.offerFirst(pageId);
        queuedWarmupPages.add(pageId);
    }

    @Nullable
    private ResourceLocation pollWarmupPage() {
        while (!warmupPageQueue.isEmpty()) {
            ResourceLocation pageId = warmupPageQueue.poll();
            if (pageId == null) {
                return null;
            }
            queuedWarmupPages.remove(pageId);
            if (!compiledPageExists(pageId) && getParsedPage(pageId) != null) {
                return pageId;
            }
        }
        return null;
    }

    private void queueWarmupPage(@Nullable ResourceLocation pageId) {
        if (pageId == null || compiledPageExists(pageId) || !queuedWarmupPages.add(pageId)) {
            return;
        }
        warmupPageQueue.offerLast(pageId);
    }

    @Nullable
    private ResourceLocation resolveRememberedWarmupPageId() {
        GuideScreenViewState state = GuideScreenMemory.recallLastContentState();
        if (state == null || state.route() == null
            || state.route()
                .guideId() == null
            || !id.equals(
                state.route()
                    .guideId())) {
            return null;
        }
        PageAnchor anchor = state.route()
            .anchor();
        return anchor != null ? anchor.pageId() : null;
    }

    private void queueWarmupBookmarkedPages() {
        for (ResourceLocation pageId : GuideBookmarkState.getSharedInstance()
            .getBookmarksView()) {
            if (pageExists(pageId)) {
                queueWarmupPage(pageId);
            }
        }
    }

    private void queueWarmupHistoryPages() {
        for (GuideScreenHomeHistory.Entry entry : GuideScreenHomeHistory.shared()
            .snapshot()) {
            if (id.equals(entry.guideId()) && pageExists(entry.pageId())) {
                queueWarmupPage(entry.pageId());
            }
        }
    }

    private boolean compiledPageExists(ResourceLocation pageId) {
        ParsedGuidePage parsedPage = getParsedPage(pageId);
        if (parsedPage == null) {
            return false;
        }
        synchronized (compiledPages) {
            return compiledPages.containsKey(parsedPage);
        }
    }

    public void rebuildEditorNavigationState() {
        rebuildIndices();
        navigationTree = buildNavigation();
        GuideRegistry.invalidateMergedNavigationTree();
        refreshPageFailures();
    }

    public void rebuildEditorNavigationStateWithoutValidation() {
        rebuildIndices();
        navigationTree = buildNavigation();
        GuideRegistry.invalidateMergedNavigationTree();
    }

    public GuideItemSettings getItemSettings() {
        return itemSettings;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    @Nullable
    private ResourceLocation resolveWarmupPageId() {
        if (navigationTree != null) {
            ResourceLocation pageId = findFirstNavigationPageId(navigationTree.getRootNodes());
            if (pageId != null) {
                return pageId;
            }
        }
        return pages != null && !pages.isEmpty() ? pages.keySet()
            .iterator()
            .next() : null;
    }

    @Nullable
    private ResourceLocation findFirstNavigationPageId(List<NavigationNode> nodes) {
        for (var node : nodes) {
            if (node.hasPage() && node.pageId() != null) {
                return node.pageId();
            }
            ResourceLocation childPageId = findFirstNavigationPageId(node.children());
            if (childPageId != null) {
                return childPageId;
            }
        }
        return null;
    }

    private boolean canLoadDevelopmentSource(ResourceLocation id) {
        if (developmentSourceFolder == null) {
            return false;
        }
        return developmentSourceLayout != GuideDevelopmentSourceLayout.CONTENT_ROOT || id.getResourceDomain()
            .equals(developmentSourceNamespace);
    }

    private Path resolveDevelopmentSourcePath(ResourceLocation id) {
        return switch (developmentSourceLayout) {
            case CONTENT_ROOT -> developmentSourceFolder.resolve(id.getResourcePath());
            case RESOURCE_PACK_ROOT -> developmentSourceFolder.resolve("assets")
                .resolve(id.getResourceDomain())
                .resolve(folder)
                .resolve(id.getResourcePath());
            case ASSETS_ROOT -> developmentSourceFolder.resolve(id.getResourceDomain())
                .resolve(folder)
                .resolve(id.getResourcePath());
        };
    }

    private GuideDevelopmentSourceLayout detectDevelopmentSourceLayout(@Nullable Path sourceFolder) {
        if (sourceFolder == null) {
            return GuideDevelopmentSourceLayout.CONTENT_ROOT;
        }
        return GuideDevelopmentSourceLayout.detect(sourceFolder, folder);
    }

    private void warmPage(ResourceLocation pageId) {
        var parsedPage = getParsedPage(pageId);
        if (parsedPage == null) {
            return;
        }
        synchronized (compiledPages) {
            if (!compiledPages.containsKey(parsedPage)) {
                try {
                    compiledPages.put(parsedPage, PageCompiler.compile(this, extensions, parsedPage));
                } catch (RuntimeException e) {
                    if (isDeferrableWarmPageFailure(e)) {
                        FMLLog.getLogger()
                            .debug(
                                "[GuideNH] [MutableGuide] Deferring warm compilation for page {} until an active client world is available",
                                pageId);
                        return;
                    }
                    throw e;
                }
            }
        }
    }

    static boolean isDeferrableWarmPageFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if ((current instanceof IllegalStateException || current instanceof IllegalArgumentException)
                && current.getMessage() != null
                && current.getMessage()
                    .contains(ACTIVE_CLIENT_WORLD_REQUIRED_MESSAGE)) {
                return true;
            }
        }
        return false;
    }

    private void refreshPageFailures() {
        pageFailures.clear();
        for (var parsedPage : getAllParsedPages().values()) {
            if (parsedPage.hasParseFailure()) {
                recordParseFailure(parsedPage);
                continue;
            }

            try {
                PageCompiler.compile(this, extensions, parsedPage);
            } catch (Throwable t) {
                if (isDeferrableWarmPageFailure(t)) {
                    FMLLog.getLogger()
                        .debug(
                            "[GuideNH] [MutableGuide] Deferring validation for page {} until an active client world is available",
                            parsedPage.getId());
                    continue;
                }
                recordCompileFailure(parsedPage.getId(), buildCompileFailureText(parsedPage.getId(), t));
                FMLLog.getLogger()
                    .error(
                        "[GuideNH] [MutableGuide] Failed to compile guide page {} during guide refresh",
                        parsedPage.getId(),
                        t);
            }
        }
    }

    private Map<ResourceLocation, ParsedGuidePage> getAllParsedPages() {
        var allPages = new LinkedHashMap<ResourceLocation, ParsedGuidePage>();
        if (pages != null) {
            allPages.putAll(pages);
        }
        allPages.putAll(developmentPages);
        return allPages;
    }

    private void recordParseFailure(ParsedGuidePage parsedPage) {
        var parseFailureMessage = parsedPage.getParseFailureMessage();
        if (parseFailureMessage == null || parseFailureMessage.isEmpty()) {
            return;
        }

        pageFailures.put(parsedPage.getId(), GuidePageFailure.parse(parseFailureMessage));
    }

    private void recordCompileFailure(ResourceLocation pageId, String errorText) {
        pageFailures.put(pageId, GuidePageFailure.compile(errorText));
    }

    private void clearCompileFailure(ResourceLocation pageId) {
        var failure = pageFailures.get(pageId);
        if (failure != null && !failure.parseFailure) {
            pageFailures.remove(pageId);
        }
    }

    private void clearParseFailure(ResourceLocation pageId) {
        var failure = pageFailures.get(pageId);
        if (failure != null && failure.parseFailure) {
            pageFailures.remove(pageId);
        }
    }

    private String buildCompileFailureText(ResourceLocation pageId, Throwable throwable) {
        var writer = new StringWriter();
        var printer = new PrintWriter(writer);
        printer.printf("Failed to compile guide page %s%n%n", pageId);
        throwable.printStackTrace(printer);
        printer.flush();
        return writer.toString();
    }

    private GuidePage buildFailurePage(ParsedGuidePage parsedPage, @Nullable GuidePageFailure failure) {
        var effectiveFailure = failure != null ? failure : GuidePageFailure.compile("Unknown guide page failure");
        return PageCompiler.buildErrorGuidePage(
            this,
            extensions,
            parsedPage.getSourcePack(),
            parsedPage.getId(),
            parsedPage.getSource(),
            effectiveFailure.headingText,
            effectiveFailure.errorText);
    }

    private static class GuidePageFailure {

        private final String headingText;
        private final String errorText;
        private final boolean parseFailure;

        private GuidePageFailure(String headingText, String errorText, boolean parseFailure) {
            this.headingText = headingText;
            this.errorText = errorText;
            this.parseFailure = parseFailure;
        }

        private static GuidePageFailure parse(String errorText) {
            return new GuidePageFailure("PARSING ERROR", errorText, true);
        }

        private static GuidePageFailure compile(String errorText) {
            return new GuidePageFailure("COMPILATION ERROR", errorText, false);
        }
    }
}
