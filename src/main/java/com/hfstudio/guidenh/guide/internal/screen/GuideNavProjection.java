package com.hfstudio.guidenh.guide.internal.screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.hfstudio.guidenh.guide.GuidePageIcon;
import com.hfstudio.guidenh.guide.internal.GuideBookmarkState;
import com.hfstudio.guidenh.guide.internal.GuidebookText;
import com.hfstudio.guidenh.guide.navigation.NavigationNode;
import com.hfstudio.guidenh.guide.navigation.NavigationTree;

public class GuideNavProjection {

    public enum RowKind {
        BOOKMARK_GROUP,
        BOOKMARK_PAGE,
        TREE_PAGE
    }

    public static class DisplayRow {

        private final RowKind kind;
        private final int depth;
        private final String title;
        @Nullable
        private final GuidePageIcon icon;
        @Nullable
        private final ResourceLocation guideId;
        @Nullable
        private final ResourceLocation pageId;
        private final boolean hasChildren;
        private final boolean hasPage;

        public DisplayRow(RowKind kind, int depth, String title, @Nullable GuidePageIcon icon,
            @Nullable ResourceLocation guideId, @Nullable ResourceLocation pageId, boolean hasChildren,
            boolean hasPage) {
            this.kind = kind;
            this.depth = depth;
            this.title = title;
            this.icon = icon;
            this.guideId = guideId;
            this.pageId = pageId;
            this.hasChildren = hasChildren;
            this.hasPage = hasPage;
        }

        public RowKind kind() {
            return kind;
        }

        public int depth() {
            return depth;
        }

        public String title() {
            return title;
        }

        @Nullable
        public GuidePageIcon icon() {
            return icon;
        }

        @Nullable
        public ResourceLocation guideId() {
            return guideId;
        }

        @Nullable
        public ResourceLocation pageId() {
            return pageId;
        }

        public boolean hasChildren() {
            return hasChildren;
        }

        public boolean hasPage() {
            return hasPage;
        }
    }

    private static final Comparator<DisplayRow> BOOKMARK_ROW_COMPARATOR = Comparator
        .comparing(DisplayRow::title, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(
            row -> row.pageId() != null ? row.pageId()
                .toString() : "");

    public List<DisplayRow> project(@Nullable NavigationTree tree, GuideBookmarkState bookmarkState,
        Set<ResourceLocation> expandedTreePageIds, boolean bookmarkGroupExpanded) {
        if (tree == null) {
            return Collections.emptyList();
        }

        var rows = new ArrayList<DisplayRow>();
        if (!bookmarkState.isEmpty()) {
            var validPageIds = collectValidPageIds(tree);
            bookmarkState.pruneInvalid(validPageIds);
        }

        var bookmarkRows = buildBookmarkRows(tree, bookmarkState);
        if (!bookmarkRows.isEmpty()) {
            rows.add(
                new DisplayRow(
                    RowKind.BOOKMARK_GROUP,
                    0,
                    GuidebookText.Bookmarks.text(),
                    null,
                    null,
                    null,
                    true,
                    false));
            if (bookmarkGroupExpanded) {
                rows.addAll(bookmarkRows);
            }
        }

        for (var root : tree.getRootNodes()) {
            addTreeRows(rows, root, 0, expandedTreePageIds);
        }
        return rows;
    }

    private Set<ResourceLocation> collectValidPageIds(NavigationTree tree) {
        var validPageIds = new HashSet<ResourceLocation>();
        for (var root : tree.getRootNodes()) {
            collectPageIds(root, validPageIds);
        }
        return validPageIds;
    }

    private void collectPageIds(NavigationNode node, Set<ResourceLocation> validPageIds) {
        if (node.pageId() != null) {
            validPageIds.add(node.pageId());
        }
        for (var child : node.children()) {
            collectPageIds(child, validPageIds);
        }
    }

    private List<DisplayRow> buildBookmarkRows(NavigationTree tree, GuideBookmarkState bookmarkState) {
        if (bookmarkState.isEmpty()) {
            return Collections.emptyList();
        }
        var rows = new ArrayList<DisplayRow>();
        for (ResourceLocation bookmarkId : bookmarkState.getBookmarksView()) {
            NavigationNode node = tree.getNodeById(bookmarkId);
            if (node == null || node.pageId() == null || !node.hasPage()) {
                continue;
            }
            rows.add(
                new DisplayRow(
                    RowKind.BOOKMARK_PAGE,
                    1,
                    node.title(),
                    node.icon(),
                    node.guideId(),
                    node.pageId(),
                    false,
                    true));
        }
        rows.sort(BOOKMARK_ROW_COMPARATOR);
        return rows;
    }

    private void addTreeRows(List<DisplayRow> rows, NavigationNode node, int depth,
        Set<ResourceLocation> expandedTreePageIds) {
        rows.add(
            new DisplayRow(
                RowKind.TREE_PAGE,
                depth,
                node.title(),
                node.icon(),
                node.guideId(),
                node.pageId(),
                !node.children()
                    .isEmpty(),
                node.hasPage()));
        if (node.pageId() != null && expandedTreePageIds.contains(node.pageId())) {
            for (var child : node.children()) {
                addTreeRows(rows, child, depth + 1, expandedTreePageIds);
            }
        }
    }
}
