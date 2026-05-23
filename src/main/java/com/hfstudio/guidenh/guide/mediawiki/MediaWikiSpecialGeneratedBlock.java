package com.hfstudio.guidenh.guide.mediawiki;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;
import com.hfstudio.guidenh.guide.GuidePageIcon;
import com.hfstudio.guidenh.guide.PageAnchor;
import com.hfstudio.guidenh.guide.color.ConstantColor;
import com.hfstudio.guidenh.guide.color.SymbolicColor;
import com.hfstudio.guidenh.guide.document.DefaultStyles;
import com.hfstudio.guidenh.guide.document.LytRect;
import com.hfstudio.guidenh.guide.document.block.BorderRenderer;
import com.hfstudio.guidenh.guide.document.block.LytBlock;
import com.hfstudio.guidenh.guide.document.flow.LytFlowContent;
import com.hfstudio.guidenh.guide.document.interaction.GuideTooltip;
import com.hfstudio.guidenh.guide.document.interaction.InteractiveElement;
import com.hfstudio.guidenh.guide.internal.GuidebookText;
import com.hfstudio.guidenh.guide.layout.LayoutContext;
import com.hfstudio.guidenh.guide.render.RenderContext;
import com.hfstudio.guidenh.guide.style.ResolvedTextStyle;
import com.hfstudio.guidenh.guide.style.TextStyle;
import com.hfstudio.guidenh.guide.ui.GuideUiHost;

public class MediaWikiSpecialGeneratedBlock extends LytBlock implements InteractiveElement {

    private static final int TOP_PADDING = 6;
    private static final int BOTTOM_PADDING = 6;
    private static final int SIDE_PADDING = 2;
    private static final int COLUMN_GAP = 10;
    private static final int GROUP_MARGIN = 6;
    private static final int HEADER_HEIGHT = 20;
    private static final int ENTRY_HEIGHT = 20;
    private static final int ENTRY_HEIGHT_WITH_SUBTITLE = 30;
    private static final int ENTRY_GAP = 2;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int LIST_MARKER_SIZE = 3;
    private static final int LIST_MARKER_GAP = 6;
    private static final int LOAD_MORE_HEIGHT = 18;
    private static final int LOAD_MORE_MARGIN_TOP = 2;
    private static final ConstantColor LIST_MARKER_COLOR = ConstantColor.WHITE;
    private static final ResolvedTextStyle LINK_STYLE = TextStyle.builder()
        .apply(DefaultStyles.BODY_TEXT)
        .color(SymbolicColor.LINK)
        .build()
        .mergeWith(DefaultStyles.BASE_STYLE);
    private static final ResolvedTextStyle HOVER_LINK_STYLE = TextStyle.builder()
        .apply(DefaultStyles.BODY_TEXT)
        .color(SymbolicColor.LINK)
        .underlined(true)
        .build()
        .mergeWith(DefaultStyles.BASE_STYLE);
    private static final ResolvedTextStyle HEADER_STYLE = TextStyle.builder()
        .apply(DefaultStyles.HEADING1)
        .build()
        .mergeWith(DefaultStyles.BASE_STYLE);
    private static final ResolvedTextStyle SUBTITLE_STYLE = TextStyle.builder()
        .apply(DefaultStyles.BODY_TEXT)
        .color(SymbolicColor.GRAY)
        .build()
        .mergeWith(DefaultStyles.BASE_STYLE);
    private static final ResolvedTextStyle EMPTY_STYLE = DefaultStyles.BODY_TEXT.mergeWith(DefaultStyles.BASE_STYLE);
    private static final BorderRenderer BORDER_RENDERER = new BorderRenderer();

    private final List<RowLayout> rowLayouts = new ArrayList<>();
    private MediaWikiSpecialPageResult result = MediaWikiSpecialPageModels.info(
        new MediaWikiSpecialDefinition(
            "Special",
            "guidenh.mediawiki.special.unsupported",
            "other",
            MediaWikiSpecialPageKind.INFO,
            false,
            false,
            MediaWikiSpecialPageQuery.PAGE_SIZE,
            null),
        "");
    private String searchQuery = "";
    private int rows = MediaWikiListPlanner.DEFAULT_ROWS;
    private String emptyText = GuidebookText.MediaWikiNoPages.text();
    private int currentVisibleCount = MediaWikiSpecialPageQuery.PAGE_SIZE;
    @Nullable
    private VisibilityCache visibilityCache;
    @Nullable
    private RowLayout hoveredRow;

    public void setResult(MediaWikiSpecialPageResult result) {
        if (result != null) {
            this.result = result;
            visibilityCache = null;
            currentVisibleCount = resolveDefaultVisibleCount(result);
        }
    }

    public void setRows(int rows) {
        this.rows = Math.max(1, rows);
    }

    public void setEmptyText(String emptyText) {
        this.emptyText = emptyText != null && !emptyText.isEmpty() ? emptyText : GuidebookText.MediaWikiNoPages.text();
    }

    public void setSearchQuery(String searchQuery) {
        String nextSearchQuery = searchQuery != null ? searchQuery : "";
        if (this.searchQuery.equals(nextSearchQuery)) {
            return;
        }
        this.searchQuery = nextSearchQuery;
        if (MediaWikiSpecialSearchSupport.normalize(nextSearchQuery)
            .isEmpty()) {
            currentVisibleCount = resolveDefaultVisibleCount(result);
        }
        visibilityCache = null;
        var document = getDocument();
        if (document != null) {
            document.invalidateLayout();
        }
    }

    @Override
    protected LytRect computeLayout(LayoutContext context, int x, int y, int availableWidth) {
        rowLayouts.clear();
        hoveredRow = null;

        MediaWikiSpecialPageResult visibleResult = applyVisibility(result, searchQuery);
        int columnCount = resolveColumnCount(visibleResult);
        int innerWidth = Math.max(0, availableWidth - SIDE_PADDING * 2);
        int columnWidth = Math.max(1, (innerWidth - COLUMN_GAP * (columnCount - 1)) / columnCount);

        if (isEmpty(visibleResult)) {
            rowLayouts
                .add(new RowLayout(new LytRect(x + SIDE_PADDING, y + TOP_PADDING, innerWidth, ENTRY_HEIGHT), null));
            return new LytRect(x, y, availableWidth, TOP_PADDING + ENTRY_HEIGHT + BOTTOM_PADDING);
        }

        List<List<GroupLayout>> columns = layoutColumns(visibleResult);
        int maxColumnHeight = 0;
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            int columnX = x + SIDE_PADDING + columnIndex * (columnWidth + COLUMN_GAP);
            int columnY = y + TOP_PADDING;
            for (GroupLayout group : columns.get(columnIndex)) {
                if (group.title() != null) {
                    rowLayouts.add(
                        new RowLayout(
                            new LytRect(columnX, columnY, columnWidth, HEADER_HEIGHT),
                            new RenderRow(group.title(), "", null, null, null, null, true, false)));
                    columnY += HEADER_HEIGHT;
                }
                for (MediaWikiSpecialListEntry entry : group.entries()) {
                    int entryHeight = entry.subtitle() != null && !entry.subtitle()
                        .isEmpty() ? ENTRY_HEIGHT_WITH_SUBTITLE : ENTRY_HEIGHT;
                    rowLayouts.add(
                        new RowLayout(
                            new LytRect(columnX, columnY, columnWidth, entryHeight),
                            new RenderRow(
                                entry.title(),
                                entry.subtitle(),
                                entry.pageId(),
                                entry.lineNumber(),
                                entry.icon(),
                                entry.externalUrl(),
                                false,
                                false)));
                    columnY += entryHeight + ENTRY_GAP;
                }
                columnY += GROUP_MARGIN;
            }
            maxColumnHeight = Math.max(maxColumnHeight, columnY - y - TOP_PADDING);
        }
        if (visibleResult.hasMore()) {
            rowLayouts.add(
                new RowLayout(
                    new LytRect(
                        x + SIDE_PADDING,
                        y + TOP_PADDING + maxColumnHeight + LOAD_MORE_MARGIN_TOP,
                        innerWidth,
                        LOAD_MORE_HEIGHT),
                    RenderRow.loadMore()));
            maxColumnHeight += LOAD_MORE_MARGIN_TOP + LOAD_MORE_HEIGHT;
        }
        return new LytRect(x, y, availableWidth, TOP_PADDING + maxColumnHeight + BOTTOM_PADDING);
    }

    @Override
    protected void onLayoutMoved(int deltaX, int deltaY) {
        for (int index = 0; index < rowLayouts.size(); index++) {
            rowLayouts.set(
                index,
                rowLayouts.get(index)
                    .move(deltaX, deltaY));
        }
    }

    @Override
    public void render(RenderContext context) {
        renderBorders(context);
        for (RowLayout rowLayout : rowLayouts) {
            if (!context.intersectsViewport(rowLayout.bounds())) {
                continue;
            }

            RenderRow row = rowLayout.row();
            if (row == null) {
                rowLayout.setClickableBounds(LytRect.empty());
                int emptyTextY = rowLayout.bounds()
                    .y()
                    + verticalCenterOffset(
                        context,
                        EMPTY_STYLE,
                        rowLayout.bounds()
                            .height());
                context.drawText(
                    emptyText,
                    rowLayout.bounds()
                        .x(),
                    emptyTextY,
                    EMPTY_STYLE);
                continue;
            }

            if (row.header()) {
                rowLayout.setClickableBounds(LytRect.empty());
                int headerTextY = rowLayout.bounds()
                    .y()
                    + verticalCenterOffset(
                        context,
                        HEADER_STYLE,
                        rowLayout.bounds()
                            .height());
                context.drawText(
                    row.title(),
                    rowLayout.bounds()
                        .x(),
                    headerTextY,
                    HEADER_STYLE);
                continue;
            }

            if (row.loadMoreRow()) {
                ResolvedTextStyle rowStyle = rowLayout == hoveredRow ? HOVER_LINK_STYLE : LINK_STYLE;
                int loadMoreY = rowLayout.bounds()
                    .y()
                    + verticalCenterOffset(
                        context,
                        rowStyle,
                        rowLayout.bounds()
                            .height());
                context.drawText(
                    GuidebookText.SpecialPageShowMore.text(),
                    rowLayout.bounds()
                        .x(),
                    loadMoreY,
                    rowStyle);
                rowLayout.setClickableBounds(rowLayout.bounds());
                continue;
            }

            ResolvedTextStyle rowStyle = rowLayout == hoveredRow ? HOVER_LINK_STYLE : LINK_STYLE;
            int markerX = rowLayout.bounds()
                .x();
            int markerY = rowLayout.bounds()
                .y()
                + (rowLayout.bounds()
                    .height() - LIST_MARKER_SIZE) / 2;
            context.fillRect(markerX, markerY, LIST_MARKER_SIZE, LIST_MARKER_SIZE, LIST_MARKER_COLOR);

            int textX = markerX + LIST_MARKER_SIZE + LIST_MARKER_GAP;
            if (row.icon() != null) {
                renderIcon(
                    context,
                    row.icon(),
                    textX,
                    rowLayout.bounds()
                        .y()
                        + (rowLayout.bounds()
                            .height() - ICON_SIZE) / 2);
                textX += ICON_SIZE + ICON_GAP;
            }
            String renderedTitle = clipToWidth(
                context,
                row.title(),
                Math.max(
                    1,
                    rowLayout.bounds()
                        .right() - textX),
                rowStyle);
            int titleY = rowLayout.bounds()
                .y()
                + verticalCenterOffset(
                    context,
                    rowStyle,
                    row.subtitle() != null && !row.subtitle()
                        .isEmpty() ? ENTRY_HEIGHT
                            : rowLayout.bounds()
                                .height());
            context.drawText(renderedTitle, textX, titleY, rowStyle);

            int clickableWidth = textX - rowLayout.bounds()
                .x();
            if (renderedTitle != null && !renderedTitle.isEmpty()) {
                clickableWidth += context.getStringWidth(renderedTitle, rowStyle);
            }

            if (row.subtitle() != null && !row.subtitle()
                .isEmpty()) {
                String subtitle = clipToWidth(
                    context,
                    row.subtitle(),
                    Math.max(
                        1,
                        rowLayout.bounds()
                            .right() - textX),
                    SUBTITLE_STYLE);
                context.drawText(
                    subtitle,
                    textX,
                    rowLayout.bounds()
                        .y() + context.getLineHeight(rowStyle)
                        + 1,
                    SUBTITLE_STYLE);
            }

            rowLayout.setClickableBounds(
                row.pageId() != null || row.externalUrl() != null ? new LytRect(
                    rowLayout.bounds()
                        .x(),
                    rowLayout.bounds()
                        .y(),
                    Math.max(0, clickableWidth),
                    rowLayout.bounds()
                        .height())
                    : LytRect.empty());
        }
    }

    @Override
    public boolean mouseClicked(GuideUiHost screen, int x, int y, int button, boolean doubleClick) {
        if (button != 0) {
            return false;
        }
        for (RowLayout rowLayout : rowLayouts) {
            if (!rowLayout.clickableBounds()
                .contains(x, y)) {
                continue;
            }
            RenderRow row = rowLayout.row();
            if (row == null) {
                return false;
            }
            if (row.pageId() != null) {
                String rowAnchor = row.lineNumber() != null ? "line-" + row.lineNumber() : null;
                screen.navigateTo(new PageAnchor(row.pageId(), rowAnchor));
                return true;
            }
            if (row.loadMoreRow()) {
                loadMore();
                return true;
            }
            URI externalUri = MediaWikiExternalLinkSupport.resolveExternalUri(row.externalUrl());
            if (externalUri != null) {
                screen.openExternalUrl(externalUri);
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public void onMouseEnter(@Nullable LytFlowContent hoveredContent) {
        hoveredRow = null;
    }

    @Override
    public void onMouseLeave() {
        hoveredRow = null;
    }

    @Override
    public Optional<GuideTooltip> getTooltip(float x, float y) {
        hoveredRow = findClickableRow((int) x, (int) y);
        return Optional.empty();
    }

    private int resolveColumnCount(MediaWikiSpecialPageResult visibleResult) {
        if (visibleResult.kind() == MediaWikiSpecialPageKind.GROUPED) {
            return 1;
        }
        if (visibleResult.definition() != null && (MediaWikiSpecialPageIds.ALL_TRANSLATIONS.equals(
            visibleResult.definition()
                .name())
            || MediaWikiSpecialPageIds.PAGE_TRANSLATION.equals(
                visibleResult.definition()
                    .name())
            || MediaWikiSpecialPageIds.SEARCH_TRANSLATIONS.equals(
                visibleResult.definition()
                    .name())
            || MediaWikiSpecialPageIds.GLOBAL_FILE_USAGE.equals(
                visibleResult.definition()
                    .name())
            || MediaWikiSpecialPageIds.DOUBLE_REDIRECTS.equals(
                visibleResult.definition()
                    .name())
            || MediaWikiSpecialPageIds.LINT_ERRORS.equals(
                visibleResult.definition()
                    .name())
            || MediaWikiSpecialPageIds.OVERRIDDEN_PAGES.equals(
                visibleResult.definition()
                    .name()))) {
            return 1;
        }
        return Math.max(1, rows);
    }

    private boolean isEmpty(MediaWikiSpecialPageResult visibleResult) {
        if (visibleResult == null) {
            return true;
        }
        return visibleResult.kind() == MediaWikiSpecialPageKind.GROUPED
            || visibleResult.kind() == MediaWikiSpecialPageKind.GROUP_INDEX
                ? visibleResult.groupedEntries()
                    .isEmpty()
                : visibleResult.flatEntries()
                    .isEmpty();
    }

    private List<List<GroupLayout>> layoutColumns(MediaWikiSpecialPageResult visibleResult) {
        int columnCount = resolveColumnCount(visibleResult);
        ArrayList<List<GroupLayout>> columns = new ArrayList<>(columnCount);
        ArrayList<Integer> heights = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            columns.add(new ArrayList<>());
            heights.add(0);
        }

        for (GroupLayout group : buildGroups(visibleResult)) {
            int targetColumn = 0;
            for (int index = 1; index < columnCount; index++) {
                if (heights.get(index) < heights.get(targetColumn)) {
                    targetColumn = index;
                }
            }
            columns.get(targetColumn)
                .add(group);
            heights.set(targetColumn, heights.get(targetColumn) + estimateHeight(group));
        }

        return columns;
    }

    private List<GroupLayout> buildGroups(MediaWikiSpecialPageResult visibleResult) {
        ArrayList<GroupLayout> groups = new ArrayList<>();
        if (visibleResult.kind() == MediaWikiSpecialPageKind.GROUPED
            || visibleResult.kind() == MediaWikiSpecialPageKind.GROUP_INDEX) {
            for (MediaWikiSpecialGroupedEntry group : visibleResult.groupedEntries()) {
                groups.add(
                    new GroupLayout(
                        group.title(),
                        group.children() != null ? group.children() : Collections.emptyList()));
            }
            return groups;
        }
        groups.add(
            new GroupLayout(
                null,
                visibleResult.flatEntries() != null ? visibleResult.flatEntries() : Collections.emptyList()));
        return groups;
    }

    private MediaWikiSpecialPageResult applyVisibility(MediaWikiSpecialPageResult source, String rawQuery) {
        if (source == null) {
            return result;
        }
        String normalizedQuery = MediaWikiSpecialSearchSupport.normalize(rawQuery);
        VisibilityCache cached = visibilityCache;
        if (cached != null && cached.matches(source, normalizedQuery, currentVisibleCount)) {
            return cached.result();
        }
        MediaWikiSpecialPageResult computed = computeVisibility(source, normalizedQuery);
        visibilityCache = new VisibilityCache(source, normalizedQuery, currentVisibleCount, computed);
        return computed;
    }

    private MediaWikiSpecialPageResult computeVisibility(MediaWikiSpecialPageResult source, String normalizedQuery) {
        MediaWikiSpecialPageResult filtered = source;
        if (!normalizedQuery.isEmpty()) {
            if (source.kind() == MediaWikiSpecialPageKind.GROUPED
                || source.kind() == MediaWikiSpecialPageKind.GROUP_INDEX) {
                filtered = new MediaWikiSpecialPageResult(
                    source.definition(),
                    source.kind(),
                    source.flatEntries(),
                    MediaWikiSpecialSearchSupport.filterGroupedEntries(source.groupedEntries(), normalizedQuery),
                    false,
                    source.searchEnabled());
            } else {
                filtered = new MediaWikiSpecialPageResult(
                    source.definition(),
                    source.kind(),
                    MediaWikiSpecialSearchSupport.filterFlatEntries(source.flatEntries(), normalizedQuery),
                    source.groupedEntries(),
                    false,
                    source.searchEnabled());
            }
            return filtered;
        }

        if (currentVisibleCount == Integer.MAX_VALUE) {
            return source;
        }

        if (source.kind() == MediaWikiSpecialPageKind.GROUPED
            || source.kind() == MediaWikiSpecialPageKind.GROUP_INDEX) {
            return new MediaWikiSpecialPageResult(
                source.definition(),
                source.kind(),
                source.flatEntries(),
                MediaWikiSpecialSearchSupport.limit(source.groupedEntries(), currentVisibleCount),
                MediaWikiSpecialSearchSupport.hasMore(source.groupedEntries(), currentVisibleCount),
                source.searchEnabled());
        }
        return new MediaWikiSpecialPageResult(
            source.definition(),
            source.kind(),
            MediaWikiSpecialSearchSupport.limit(source.flatEntries(), currentVisibleCount),
            source.groupedEntries(),
            MediaWikiSpecialSearchSupport.hasMore(source.flatEntries(), currentVisibleCount),
            source.searchEnabled());
    }

    private int resolveDefaultVisibleCount(MediaWikiSpecialPageResult source) {
        if (source == null || source.definition() == null) {
            return Integer.MAX_VALUE;
        }
        return source.definition()
            .showsAllByDefault() ? Integer.MAX_VALUE
                : Math.max(
                    1,
                    source.definition()
                        .defaultVisibleCount());
    }

    private void loadMore() {
        if (currentVisibleCount == Integer.MAX_VALUE) {
            return;
        }
        currentVisibleCount = Math.min(
            Integer.MAX_VALUE - MediaWikiSpecialPageQuery.PAGE_SIZE,
            currentVisibleCount + MediaWikiSpecialPageQuery.PAGE_SIZE);
        visibilityCache = null;
        var document = getDocument();
        if (document != null) {
            document.invalidateLayout();
        }
    }

    private int estimateHeight(GroupLayout group) {
        int height = group.title() != null ? HEADER_HEIGHT : 0;
        for (MediaWikiSpecialListEntry entry : group.entries()) {
            height += entry.subtitle() != null && !entry.subtitle()
                .isEmpty() ? ENTRY_HEIGHT_WITH_SUBTITLE : ENTRY_HEIGHT;
            height += ENTRY_GAP;
        }
        return height + GROUP_MARGIN;
    }

    @Nullable
    private RowLayout findClickableRow(int x, int y) {
        for (RowLayout rowLayout : rowLayouts) {
            if (rowLayout.row() != null && rowLayout.clickableBounds()
                .contains(x, y)) {
                return rowLayout;
            }
        }
        return null;
    }

    private int verticalCenterOffset(RenderContext context, ResolvedTextStyle style, int boxHeight) {
        return Math.max(0, (boxHeight - context.getLineHeight(style)) / 2);
    }

    private void renderIcon(RenderContext context, GuidePageIcon icon, int x, int y) {
        if (icon.isItemIcon() && icon.resolveCurrentItemStack() != null) {
            context.renderItemIcon(icon.resolveCurrentItemStack(), x, y);
            return;
        }
        if (icon.resolveCurrentTexture() != null) {
            context.fillTexturedRect(new LytRect(x, y, ICON_SIZE, ICON_SIZE), icon.resolveCurrentTexture());
        }
    }

    private void renderBorders(RenderContext context) {
        if (getBorderTop().width() <= 0 && getBorderLeft().width() <= 0
            && getBorderRight().width() <= 0
            && getBorderBottom().width() <= 0) {
            return;
        }
        BORDER_RENDERER.render(context, bounds, getBorderTop(), getBorderLeft(), getBorderRight(), getBorderBottom());
    }

    private String clipToWidth(RenderContext context, String text, int maxWidth, ResolvedTextStyle style) {
        if (text == null || text.isEmpty() || context.getStringWidth(text, style) <= maxWidth) {
            return text == null ? "" : text;
        }

        int ellipsisWidth = context.getStringWidth("...", style);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }

        int end = text.length();
        while (end > 0 && context.getStringWidth(text.substring(0, end), style) + ellipsisWidth > maxWidth) {
            end--;
        }
        return end <= 0 ? "..." : text.substring(0, end) + "...";
    }

    @Desugar
    private record GroupLayout(@Nullable String title, List<MediaWikiSpecialListEntry> entries) {}

    @Desugar
    private record RenderRow(String title, String subtitle, @Nullable ResourceLocation pageId,
        @Nullable Integer lineNumber, @Nullable GuidePageIcon icon, @Nullable String externalUrl, boolean header,
        boolean loadMoreRow) {

        public static RenderRow loadMore() {
            return new RenderRow("", "", null, null, null, null, false, true);
        }
    }

    @Desugar
    private record VisibilityCache(MediaWikiSpecialPageResult source, String normalizedQuery, int visibleCount,
        MediaWikiSpecialPageResult result) {

        private boolean matches(MediaWikiSpecialPageResult candidateSource, String candidateQuery,
            int candidateVisibleCount) {
            return source == candidateSource && normalizedQuery.equals(candidateQuery)
                && visibleCount == candidateVisibleCount;
        }
    }

    private static class RowLayout {

        private final LytRect bounds;
        @Nullable
        private final RenderRow row;
        private LytRect clickableBounds;

        private RowLayout(LytRect bounds, @Nullable RenderRow row) {
            this.bounds = bounds;
            this.row = row;
            this.clickableBounds = LytRect.empty();
        }

        private LytRect bounds() {
            return bounds;
        }

        @Nullable
        private RenderRow row() {
            return row;
        }

        private LytRect clickableBounds() {
            return clickableBounds;
        }

        private void setClickableBounds(LytRect clickableBounds) {
            this.clickableBounds = clickableBounds != null ? clickableBounds : LytRect.empty();
        }

        private RowLayout move(int deltaX, int deltaY) {
            RowLayout moved = new RowLayout(bounds.move(deltaX, deltaY), row);
            moved.setClickableBounds(clickableBounds.move(deltaX, deltaY));
            return moved;
        }
    }
}
