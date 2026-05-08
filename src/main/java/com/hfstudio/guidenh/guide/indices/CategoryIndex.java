package com.hfstudio.guidenh.guide.indices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.google.gson.stream.JsonWriter;
import com.hfstudio.guidenh.guide.Guide;
import com.hfstudio.guidenh.guide.PageAnchor;
import com.hfstudio.guidenh.guide.compiler.ParsedGuidePage;

import cpw.mods.fml.common.FMLLog;

/**
 * Pages can declare to be part of multiple categories using the categories frontmatter.
 * <p/>
 * This index is installed by default on all {@linkplain Guide guides}.
 */
public class CategoryIndex extends MultiValuedIndex<String, PageAnchor> {

    public CategoryIndex() {
        super(
            "Categories",
            CategoryIndex::getCategories,
            JsonWriter::value,
            (writer, value) -> writer.value(value.toString()));
    }

    public static List<Pair<String, PageAnchor>> getCategories(ParsedGuidePage page) {
        var categoriesNode = page.getFrontmatter()
            .additionalProperties()
            .get("categories");
        if (categoriesNode == null) {
            return Collections.emptyList();
        }

        if (!(categoriesNode instanceof List<?>categoryList)) {
            FMLLog.getLogger()
                .warn("[GuideNH] [CategoryIndex] Page {} contains malformed categories frontmatter", page.getId());
            return Collections.emptyList();
        }

        // The anchor to the current page
        var anchor = new PageAnchor(page.getId(), null);

        var categories = new ArrayList<Pair<String, PageAnchor>>(categoryList.size());

        for (var listEntry : categoryList) {
            if (listEntry instanceof String categoryString) {
                categories.add(Pair.of(categoryString, anchor));
            } else {
                FMLLog.getLogger()
                    .warn(
                        "[GuideNH] [CategoryIndex] Page {} contains a malformed categories frontmatter entry: {}",
                        page.getId(),
                        listEntry);
            }
        }

        return categories;
    }
}
