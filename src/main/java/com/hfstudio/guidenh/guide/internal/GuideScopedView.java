package com.hfstudio.guidenh.guide.internal;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.hfstudio.guidenh.guide.Guide;
import com.hfstudio.guidenh.guide.GuidePage;
import com.hfstudio.guidenh.guide.compiler.PageCompiler;
import com.hfstudio.guidenh.guide.compiler.ParsedGuidePage;
import com.hfstudio.guidenh.guide.extensions.ExtensionCollection;
import com.hfstudio.guidenh.guide.indices.CategoryIndex;
import com.hfstudio.guidenh.guide.indices.PageIndex;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiListContext;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiListContextProvider;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiSpecialDataIndex;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiSpecialDataIndexer;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiSpecialPageRefreshController;
import com.hfstudio.guidenh.guide.navigation.NavigationTree;

public class GuideScopedView implements Guide, MediaWikiListContextProvider {

    private final Guide delegate;
    private final Map<ResourceLocation, ParsedGuidePage> parsedPagesById;
    private final NavigationTree navigationTree;
    private final Map<Class<?>, PageIndex> indexOverrides;
    @Nullable
    private volatile MediaWikiListContext mediaWikiListContext;
    private volatile long mediaWikiListContextRevision = Long.MIN_VALUE;
    private final MediaWikiSpecialPageRefreshController mediaWikiRefreshController = new MediaWikiSpecialPageRefreshController();
    private final Map<ParsedGuidePage, GuidePage> compiledPages = Collections
        .synchronizedMap(new IdentityHashMap<ParsedGuidePage, GuidePage>());

    public GuideScopedView(Guide delegate, Map<ResourceLocation, ParsedGuidePage> parsedPagesById,
        NavigationTree navigationTree, Map<Class<?>, PageIndex> indexOverrides,
        @Nullable MediaWikiListContext mediaWikiListContext) {
        this.delegate = delegate;
        this.parsedPagesById = Collections.unmodifiableMap(new LinkedHashMap<>(parsedPagesById));
        this.navigationTree = navigationTree != null ? navigationTree : new NavigationTree();
        this.indexOverrides = indexOverrides != null ? new LinkedHashMap<>(indexOverrides) : Collections.emptyMap();
        this.mediaWikiListContext = mediaWikiListContext;
        requestMediaWikiContextWarmup(mediaWikiRefreshController.currentRevision());
    }

    public static GuideScopedView create(Guide delegate, Map<ResourceLocation, ParsedGuidePage> parsedPagesById,
        Map<Class<?>, PageIndex> indexOverrides) {
        Map<Class<?>, PageIndex> safeIndexOverrides = indexOverrides != null ? new LinkedHashMap<>(indexOverrides)
            : Collections.emptyMap();
        NavigationTree navigationTree = NavigationTree.build(delegate, parsedPagesById.values());
        return new GuideScopedView(delegate, parsedPagesById, navigationTree, safeIndexOverrides, null);
    }

    @Override
    public ResourceLocation getId() {
        return delegate.getId();
    }

    @Override
    public String getDefaultNamespace() {
        return delegate.getDefaultNamespace();
    }

    @Override
    public String getContentRootFolder() {
        return delegate.getContentRootFolder();
    }

    @Override
    public ExtensionCollection getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public <T extends PageIndex> T getIndex(Class<T> indexClass) {
        PageIndex override = indexOverrides.get(indexClass);
        if (override != null) {
            return indexClass.cast(override);
        }
        return delegate.getIndex(indexClass);
    }

    @Override
    public Collection<ParsedGuidePage> getPages() {
        return parsedPagesById.values();
    }

    @Override
    public @Nullable ParsedGuidePage getParsedPage(ResourceLocation id) {
        return parsedPagesById.get(id);
    }

    @Override
    public @Nullable GuidePage getPage(ResourceLocation id) {
        ParsedGuidePage parsedPage = parsedPagesById.get(id);
        if (parsedPage == null) {
            return null;
        }
        synchronized (compiledPages) {
            GuidePage compiledPage = compiledPages.get(parsedPage);
            if (compiledPage == null) {
                compiledPage = PageCompiler.compile(this, getExtensions(), parsedPage);
                compiledPages.put(parsedPage, compiledPage);
            }
            return compiledPage;
        }
    }

    @Override
    public byte @Nullable [] loadAsset(ResourceLocation id) {
        return delegate.loadAsset(id);
    }

    @Override
    public NavigationTree getNavigationTree() {
        return navigationTree;
    }

    @Override
    public boolean pageExists(ResourceLocation pageId) {
        return parsedPagesById.containsKey(pageId);
    }

    @Override
    public boolean isPageFailed(ResourceLocation pageId) {
        return delegate.isPageFailed(pageId);
    }

    @Override
    public @Nullable MediaWikiListContext getMediaWikiListContext() {
        long currentRevision = mediaWikiRefreshController.currentRevision();
        MediaWikiListContext cached = mediaWikiListContext;
        if (cached != null && mediaWikiListContextRevision == currentRevision) {
            return cached;
        }
        synchronized (this) {
            if (mediaWikiListContext != null && mediaWikiListContextRevision == currentRevision) {
                return mediaWikiListContext;
            }
            PageIndex categoryIndexOverride = getIndex(CategoryIndex.class);
            if (!(categoryIndexOverride instanceof CategoryIndex categoryIndex)) {
                return null;
            }
            MediaWikiSpecialDataIndex specialDataIndex = new MediaWikiSpecialDataIndexer()
                .build(this, parsedPagesById.values(), categoryIndex);
            mediaWikiListContext = MediaWikiListContext
                .create(this, parsedPagesById.values(), navigationTree, categoryIndex, specialDataIndex);
            mediaWikiListContextRevision = currentRevision;
            return mediaWikiListContext;
        }
    }

    private void requestMediaWikiContextWarmup(long revision) {
        PageIndex categoryIndexOverride = getIndex(CategoryIndex.class);
        if (!(categoryIndexOverride instanceof CategoryIndex categoryIndex)) {
            return;
        }
        Map<ResourceLocation, ParsedGuidePage> pagesSnapshot = new LinkedHashMap<>(parsedPagesById);
        mediaWikiRefreshController.requestRefresh(revision, () -> {
            MediaWikiSpecialDataIndex specialDataIndex = new MediaWikiSpecialDataIndexer()
                .build(this, pagesSnapshot.values(), categoryIndex);
            MediaWikiListContext context = MediaWikiListContext
                .create(this, pagesSnapshot.values(), navigationTree, categoryIndex, specialDataIndex);
            synchronized (this) {
                if (!mediaWikiRefreshController.isCurrent(revision)) {
                    return;
                }
                mediaWikiListContext = context;
                mediaWikiListContextRevision = revision;
            }
        });
    }
}
