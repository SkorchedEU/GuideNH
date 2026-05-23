package com.hfstudio.guidenh.guide.compiler.tags.mediawiki;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.hfstudio.guidenh.guide.compiler.IndexingContext;
import com.hfstudio.guidenh.guide.compiler.IndexingSink;
import com.hfstudio.guidenh.guide.compiler.PageCompiler;
import com.hfstudio.guidenh.guide.compiler.tags.BlockTagCompiler;
import com.hfstudio.guidenh.guide.document.block.LytBlockContainer;
import com.hfstudio.guidenh.guide.indices.CategoryIndex;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiListEntry;
import com.hfstudio.guidenh.guide.mediawiki.MediaWikiSpecialPageResolver;
import com.hfstudio.guidenh.libs.mdast.mdx.model.MdxJsxElementFields;

public class SpecialCompiler extends BlockTagCompiler {

    @Override
    public Set<String> getTagNames() {
        return Collections.singleton("Special");
    }

    @Override
    protected void compile(PageCompiler compiler, LytBlockContainer parent, MdxJsxElementFields el) {
        String rawSpecialName = el.getAttributeString("name", null);
        if (rawSpecialName == null || rawSpecialName.trim()
            .isEmpty()) {
            parent.appendError(compiler, "Missing special page name", el);
            return;
        }
        String specialName = MediaWikiSpecialPageResolver.normalizeSupportedName(rawSpecialName);
        if (specialName == null) {
            parent.appendError(compiler, "Unsupported special page: " + rawSpecialName, el);
            return;
        }

        var guide = MediaWikiTagCompilerSupport.resolveGuide(compiler, parent, el);
        if (guide == null) {
            return;
        }

        var context = MediaWikiTagCompilerSupport.createListContext(guide, compiler.getIndex(CategoryIndex.class));
        List<MediaWikiListEntry> entries = MediaWikiSpecialPageResolver.resolveEntries(context, specialName);
        parent.append(
            MediaWikiTagCompilerSupport
                .createBlock(entries, MediaWikiTagCompilerSupport.readRows(el), "No pages available"));
    }

    @Override
    public void index(IndexingContext indexer, MdxJsxElementFields el, IndexingSink sink) {
        String specialName = MediaWikiSpecialPageResolver.normalizeSupportedName(el.getAttributeString("name", null));
        if (specialName == null) {
            return;
        }

        var guide = MediaWikiTagCompilerSupport.resolveGuide(indexer);
        if (guide == null) {
            return;
        }

        var context = MediaWikiTagCompilerSupport.createListContext(guide, indexer.getIndex(CategoryIndex.class));
        sink.appendText(el, specialName);
        sink.appendBreak();
        MediaWikiTagCompilerSupport
            .indexEntries(sink, el, MediaWikiSpecialPageResolver.resolveEntries(context, specialName));
    }
}
