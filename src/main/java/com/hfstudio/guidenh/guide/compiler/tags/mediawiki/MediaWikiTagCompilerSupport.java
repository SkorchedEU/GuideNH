package com.hfstudio.guidenh.guide.compiler.tags.mediawiki;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.hfstudio.guidenh.guide.Guide;
import com.hfstudio.guidenh.guide.color.SymbolicColor;
import com.hfstudio.guidenh.guide.compiler.IndexingContext;
import com.hfstudio.guidenh.guide.compiler.IndexingSink;
import com.hfstudio.guidenh.guide.compiler.PageCompiler;
import com.hfstudio.guidenh.guide.document.block.LytBlockContainer;
import com.hfstudio.guidenh.guide.indices.CategoryIndex;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiGeneratedListBlock;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiListContext;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiListContextProvider;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiListEntry;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiListPlanner;
import com.hfstudio.guidenh.guide.style.BorderStyle;
import com.hfstudio.guidenh.libs.mdast.mdx.model.MdxJsxElementFields;
import com.hfstudio.guidenh.libs.unist.UnistNode;

public class MediaWikiTagCompilerSupport {

    private MediaWikiTagCompilerSupport() {}

    public static @Nullable Guide resolveGuide(PageCompiler compiler, LytBlockContainer parent,
        MdxJsxElementFields el) {
        if (compiler.getPageCollection() instanceof Guide guide) {
            return guide;
        }
        parent.appendError(compiler, "MediaWiki tags require a guide-backed page collection", el);
        return null;
    }

    public static @Nullable Guide resolveGuide(IndexingContext indexer) {
        return indexer.getPageCollection() instanceof Guide guide ? guide : null;
    }

    public static MediaWikiListContext createListContext(Guide guide, CategoryIndex categoryIndex) {
        if (guide instanceof MediaWikiListContextProvider provider) {
            MediaWikiListContext providedContext = provider.getMediaWikiListContext();
            if (providedContext != null) {
                return providedContext;
            }
        }
        return MediaWikiListContext.create(guide, guide.getPages(), guide.getNavigationTree(), categoryIndex);
    }

    public static MediaWikiGeneratedListBlock createBlock(List<MediaWikiListEntry> entries, int rows,
        String emptyText) {
        var block = new MediaWikiGeneratedListBlock();
        block.setFullWidth(true);
        block.setBorderTop(new BorderStyle(SymbolicColor.TABLE_BORDER, 1));
        block.setBorderBottom(new BorderStyle(SymbolicColor.TABLE_BORDER, 1));
        block.setEntries(entries);
        block.setRows(MediaWikiListPlanner.sanitizeRows(rows));
        block.setEmptyText(emptyText);
        return block;
    }

    public static int readRows(MdxJsxElementFields el) {
        String rawRows = el.getAttributeString("rows", null);
        if (rawRows == null || rawRows.trim()
            .isEmpty()) {
            return MediaWikiListPlanner.DEFAULT_ROWS;
        }
        try {
            return MediaWikiListPlanner.sanitizeRows(Integer.parseInt(rawRows.trim()));
        } catch (NumberFormatException ignored) {
            return MediaWikiListPlanner.DEFAULT_ROWS;
        }
    }

    public static void indexEntries(IndexingSink sink, UnistNode parent, List<MediaWikiListEntry> entries) {
        for (MediaWikiListEntry entry : entries) {
            sink.appendText(parent, entry.title());
            sink.appendBreak();
        }
    }
}
