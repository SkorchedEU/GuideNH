package com.hfstudio.guidenh.guide.internal.screen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import com.hfstudio.guidenh.guide.GuidePageIcon;
import com.hfstudio.guidenh.guide.PageCollection;
import com.hfstudio.guidenh.guide.internal.GuideBookmarkState;
import com.hfstudio.guidenh.guide.internal.util.DisplayScale;
import com.hfstudio.guidenh.guide.navigation.NavigationTree;
import com.hfstudio.guidenh.guide.render.GuidePageTexture;

public class GuideNavBar {

    public static class NavigationTarget {

        @Nullable
        private final ResourceLocation guideId;
        @Nullable
        private final ResourceLocation pageId;

        public NavigationTarget(@Nullable ResourceLocation guideId, @Nullable ResourceLocation pageId) {
            this.guideId = guideId;
            this.pageId = pageId;
        }

        @Nullable
        public ResourceLocation guideId() {
            return guideId;
        }

        @Nullable
        public ResourceLocation pageId() {
            return pageId;
        }
    }

    public static class ClickResult {

        @Nullable
        private final NavigationTarget navigationTarget;
        @Nullable
        private final ResourceLocation bookmarkTogglePageId;

        private ClickResult(@Nullable NavigationTarget navigationTarget,
            @Nullable ResourceLocation bookmarkTogglePageId) {
            this.navigationTarget = navigationTarget;
            this.bookmarkTogglePageId = bookmarkTogglePageId;
        }

        public static ClickResult navigate(@Nullable ResourceLocation guideId, @Nullable ResourceLocation pageId) {
            return pageId == null ? none() : new ClickResult(new NavigationTarget(guideId, pageId), null);
        }

        public static ClickResult toggleBookmark(ResourceLocation pageId) {
            return new ClickResult(null, pageId);
        }

        public static ClickResult none() {
            return new ClickResult(null, null);
        }

        @Nullable
        public NavigationTarget navigationTarget() {
            return navigationTarget;
        }

        @Nullable
        public ResourceLocation bookmarkTogglePageId() {
            return bookmarkTogglePageId;
        }
    }

    public static final int WIDTH_CLOSED = 10;
    public static final int WIDTH_OPEN = 150;
    public static final int CONTENT_PADDING = 2;
    public static final int ROW_H = 12;
    public static final int CHILD_INDENT = 12;
    public static final int EXPAND_INDENT = 8;
    public static final int ICON_SIZE = 9;
    public static final int ACTION_SLOT_W = 12;
    public static final int ACTION_ICON_SIZE = 9;
    public static final int ACTION_PADDING_RIGHT = 2;

    private final List<Row> rows = new ArrayList<Row>();
    private final Set<ResourceLocation> expandedPageIds = new HashSet<ResourceLocation>();
    private final GuideNavProjection projection = new GuideNavProjection();
    @Nullable
    private NavigationTree lastTree;
    private int lastBookmarkStateVersion;
    private int lastExpandedStateHash;
    private boolean bookmarkGroupExpanded = true;

    private int x;
    private int y;
    private int height;
    private boolean open;
    private boolean pinned;
    private int scrollY;

    public void setBounds(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    public int currentWidth() {
        return (open || pinned) ? WIDTH_OPEN : WIDTH_CLOSED;
    }

    public boolean isOpen() {
        return open || pinned;
    }

    public void update(int mouseX, int mouseY, @Nullable NavigationTree tree, GuideBookmarkState bookmarkState) {
        if (shouldRebuildRows(tree, bookmarkState)) {
            rebuildRows(tree, bookmarkState);
        }
        if (pinned) {
            open = true;
            return;
        }
        int w = currentWidth();
        open = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + height;
    }

    private boolean shouldRebuildRows(@Nullable NavigationTree tree, GuideBookmarkState bookmarkState) {
        return tree != lastTree || lastBookmarkStateVersion != bookmarkState.version()
            || lastExpandedStateHash != expandedPageIds.hashCode();
    }

    private void rebuildRows(@Nullable NavigationTree tree, GuideBookmarkState bookmarkState) {
        rows.clear();
        lastTree = tree;
        if (tree == null) {
            lastBookmarkStateVersion = bookmarkState.version();
            lastExpandedStateHash = expandedPageIds.hashCode();
            return;
        }
        for (GuideNavProjection.DisplayRow displayRow : projection
            .project(tree, bookmarkState, expandedPageIds, bookmarkGroupExpanded)) {
            rows.add(new Row(displayRow));
        }
        lastBookmarkStateVersion = bookmarkState.version();
        lastExpandedStateHash = expandedPageIds.hashCode();
    }

    public void render(Minecraft mc, @Nullable ResourceLocation currentGuideId,
        @Nullable ResourceLocation currentPageId, int mouseX, int mouseY, @Nullable PageCollection pageCollection,
        GuideBookmarkState bookmarkState) {
        if (lastTree == null || rows.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            int w = currentWidth();
            int rowRight = x + w - 1;
            int textRightBase = x + w - 2;
            int bookmarkActionLeft = x + w - ACTION_SLOT_W;
            int bookmarkIconX = bookmarkActionLeft + ACTION_PADDING_RIGHT;
            int bgTop = 0xE0151515;
            int bgBot = 0xE0101010;
            drawVGradient(x, y, w, height, bgTop, bgBot);
            Gui.drawRect(rowRight, y, x + w, y + height, 0xFF2A2A2A);

            if (!isOpen()) {
                drawArrow(x + w / 2 - 2, y + height / 2 - 3, true, 0xFF888888);
                return;
            }

            int sf = DisplayScale.scaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(x * sf, mc.displayHeight - (y + height) * sf, w * sf, height * sf);

            FontRenderer fr = mc.fontRenderer;
            int firstVisibleRow = getFirstVisibleRowIndex();
            for (int rowIndex = firstVisibleRow; rowIndex < rows.size(); rowIndex++) {
                Row row = rows.get(rowIndex);
                int rowY = getRowY(rowIndex);
                if (rowY >= y + height) {
                    break;
                }

                int indent = row.displayRow.depth() * CHILD_INDENT;
                int rowX = x + 2 + indent;
                boolean hovered = mouseX >= x && mouseX < rowRight && mouseY >= rowY && mouseY < rowY + ROW_H;
                boolean current = isCurrentRow(row.displayRow, currentGuideId, currentPageId);
                if (current) {
                    Gui.drawRect(x, rowY, rowRight, rowY + ROW_H, 0x40FFFFFF);
                } else if (hovered) {
                    Gui.drawRect(x, rowY, rowRight, rowY + ROW_H, 0x20FFFFFF);
                }

                if (row.displayRow.hasChildren()) {
                    boolean collapsed = isCollapsed(row.displayRow);
                    drawArrow(rowX, rowY + 2, collapsed, 0xFFCCCCCC);
                }

                int textX = rowX + EXPAND_INDENT;
                GuidePageIcon icon = row.displayRow.icon();
                if (icon != null) {
                    int iy = rowY + (ROW_H - ICON_SIZE) / 2;
                    drawMiniIcon(mc, icon, textX, iy);
                    textX += ICON_SIZE + 2;
                }

                int textRight = textRightBase;
                if (canToggleBookmark(row.displayRow)) {
                    textRight -= ACTION_SLOT_W;
                }
                int maxTw = textRight - textX;
                if (maxTw > 0) {
                    String title = row.getTitle(fr, maxTw);
                    boolean failed = row.displayRow.pageId() != null && pageCollection != null
                        && pageCollection.isPageFailed(row.displayRow.pageId());
                    int color = getRowTextColor(current, hovered, failed);
                    fr.drawString(title, textX, rowY + 2, color, false);
                }

                renderBookmarkIcon(mc, row.displayRow, rowY, hovered, bookmarkState, bookmarkIconX);
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    @Nullable
    public ClickResult mouseClicked(int mouseX, int mouseY, @Nullable ResourceLocation currentGuideId,
        @Nullable ResourceLocation currentPageId, GuideBookmarkState bookmarkState) {
        if (!isOpen()) {
            return null;
        }
        int w = currentWidth();
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + height) {
            return null;
        }
        int rowIndex = getRowIndexAt(mouseY);
        if (rowIndex < 0) {
            return null;
        }

        Row row = rows.get(rowIndex);
        int rowY = getRowY(rowIndex);
        if (canToggleBookmark(row.displayRow) && isInsideBookmarkAction(mouseX, row, rowY)
            && row.displayRow.pageId() != null) {
            return ClickResult.toggleBookmark(row.displayRow.pageId());
        }

        if (row.displayRow.hasChildren() && isInsideExpandArrow(mouseX, row)) {
            toggleExpand(row.displayRow, bookmarkState);
            return ClickResult.none();
        }

        if (row.displayRow.kind() == GuideNavProjection.RowKind.BOOKMARK_GROUP) {
            bookmarkGroupExpanded = !bookmarkGroupExpanded;
            rebuildRows(lastTree, bookmarkState);
            return ClickResult.none();
        }

        if (row.displayRow.hasChildren() && row.displayRow.kind() == GuideNavProjection.RowKind.TREE_PAGE) {
            boolean alreadyExpanded = isExpanded(row.displayRow);
            if (!alreadyExpanded) {
                toggleExpand(row.displayRow, bookmarkState);
            } else if (isCurrentRow(row.displayRow, currentGuideId, currentPageId)) {
                toggleExpand(row.displayRow, bookmarkState);
            }
        }

        if (row.displayRow.pageId() != null && row.displayRow.hasPage()) {
            return ClickResult.navigate(row.displayRow.guideId(), row.displayRow.pageId());
        }
        return ClickResult.none();
    }

    private void renderBookmarkIcon(Minecraft mc, GuideNavProjection.DisplayRow row, int rowY, boolean hovered,
        GuideBookmarkState bookmarkState, int bookmarkIconX) {
        if (!canToggleBookmark(row)) {
            return;
        }
        boolean bookmarked = row.pageId() != null && bookmarkState.isBookmarked(row.pageId());
        if (!bookmarked && !hovered) {
            return;
        }
        int iconY = rowY + (ROW_H - ACTION_ICON_SIZE) / 2;
        GuideIconButton.Role role = bookmarked ? GuideIconButton.Role.BOOKMARKED : GuideIconButton.Role.BOOKMARK;
        int color = GuideIconButton.resolveIconColor(true, hovered, bookmarked);
        GuideIconButton.drawIcon(mc, role, bookmarkIconX, iconY, ACTION_ICON_SIZE, ACTION_ICON_SIZE, color);
    }

    private boolean isInsideBookmarkAction(int mouseX, Row row, int rowY) {
        if (!canToggleBookmark(row.displayRow)) {
            return false;
        }
        int iconX = x + currentWidth() - ACTION_SLOT_W;
        return mouseX >= iconX && mouseX < x + currentWidth() - 1;
    }

    private boolean canToggleBookmark(GuideNavProjection.DisplayRow row) {
        return row.kind() == GuideNavProjection.RowKind.BOOKMARK_PAGE
            || row.kind() == GuideNavProjection.RowKind.TREE_PAGE && row.hasPage();
    }

    private boolean isInsideExpandArrow(int mouseX, Row row) {
        int arrowX = x + 2 + row.displayRow.depth() * CHILD_INDENT;
        return mouseX >= arrowX && mouseX < arrowX + EXPAND_INDENT;
    }

    private boolean isCurrentRow(GuideNavProjection.DisplayRow row, @Nullable ResourceLocation currentGuideId,
        @Nullable ResourceLocation currentPageId) {
        if (currentPageId == null || !currentPageId.equals(row.pageId())) {
            return false;
        }
        return row.guideId() == null || currentGuideId == null || currentGuideId.equals(row.guideId());
    }

    private boolean isCollapsed(GuideNavProjection.DisplayRow row) {
        if (row.kind() == GuideNavProjection.RowKind.BOOKMARK_GROUP) {
            return !bookmarkGroupExpanded;
        }
        return !isExpanded(row);
    }

    private boolean isExpanded(GuideNavProjection.DisplayRow row) {
        return row.pageId() != null && expandedPageIds.contains(row.pageId());
    }

    private void toggleExpand(GuideNavProjection.DisplayRow row, GuideBookmarkState bookmarkState) {
        if (row.kind() == GuideNavProjection.RowKind.BOOKMARK_GROUP) {
            bookmarkGroupExpanded = !bookmarkGroupExpanded;
        } else if (row.pageId() != null) {
            if (expandedPageIds.contains(row.pageId())) {
                expandedPageIds.remove(row.pageId());
            } else {
                expandedPageIds.add(row.pageId());
            }
        }
        rebuildRows(lastTree, bookmarkState);
    }

    public boolean contains(int mouseX, int mouseY) {
        int w = currentWidth();
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + height;
    }

    public void scroll(int dwheel) {
        int contentH = rows.size() * ROW_H + CONTENT_PADDING * 2;
        int max = Math.max(0, contentH - height);
        scrollY -= Integer.signum(dwheel) * ROW_H * 2;
        if (scrollY < 0) {
            scrollY = 0;
        }
        if (scrollY > max) {
            scrollY = max;
        }
    }

    private int getFirstVisibleRowIndex() {
        int firstVisibleRow = Math.floorDiv(scrollY - CONTENT_PADDING - 1, ROW_H);
        return Math.max(0, Math.min(firstVisibleRow, rows.size()));
    }

    private int getRowIndexAt(int mouseY) {
        int relativeY = mouseY - y + scrollY - CONTENT_PADDING;
        if (relativeY < 0) {
            return -1;
        }
        int rowIndex = relativeY / ROW_H;
        return rowIndex >= 0 && rowIndex < rows.size() ? rowIndex : -1;
    }

    private int getRowY(int rowIndex) {
        return y + CONTENT_PADDING - scrollY + rowIndex * ROW_H;
    }

    public static int getRowTextColor(boolean current, boolean hovered, boolean failed) {
        if (failed) {
            return current ? 0xFFFF9999 : hovered ? 0xFFFF7777 : 0xFFFF5555;
        }
        return current ? 0xFFFFFFFF : hovered ? 0xFF88BBFF : 0xFFBBBBBB;
    }

    public static void drawVGradient(int x, int y, int w, int h, int topColor, int botColor) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        var tes = Tessellator.instance;
        tes.startDrawingQuads();
        float ta = ((topColor >>> 24) & 0xFF) / 255f;
        float tr = ((topColor >>> 16) & 0xFF) / 255f;
        float tg = ((topColor >>> 8) & 0xFF) / 255f;
        float tb = (topColor & 0xFF) / 255f;
        float ba = ((botColor >>> 24) & 0xFF) / 255f;
        float br = ((botColor >>> 16) & 0xFF) / 255f;
        float bg = ((botColor >>> 8) & 0xFF) / 255f;
        float bb = (botColor & 0xFF) / 255f;
        tes.setColorRGBA_F(br, bg, bb, ba);
        tes.addVertex(x, y + h, 0);
        tes.addVertex(x + w, y + h, 0);
        tes.setColorRGBA_F(tr, tg, tb, ta);
        tes.addVertex(x + w, y, 0);
        tes.addVertex(x, y, 0);
        tes.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void drawArrow(int x, int y, boolean pointRight, int color) {
        if (pointRight) {
            Gui.drawRect(x, y, x + 1, y + 7, color);
            Gui.drawRect(x + 1, y + 1, x + 2, y + 6, color);
            Gui.drawRect(x + 2, y + 2, x + 3, y + 5, color);
            Gui.drawRect(x + 3, y + 3, x + 4, y + 4, color);
        } else {
            Gui.drawRect(x, y, x + 7, y + 1, color);
            Gui.drawRect(x + 1, y + 1, x + 6, y + 2, color);
            Gui.drawRect(x + 2, y + 2, x + 5, y + 3, color);
            Gui.drawRect(x + 3, y + 3, x + 4, y + 4, color);
        }
    }

    public static void drawMiniIcon(Minecraft mc, GuidePageIcon icon, int x, int y) {
        if (icon.isTextureIcon()) {
            drawMiniTextureIcon(icon.resolveCurrentTexture(), x, y);
            return;
        }
        drawMiniItemIcon(mc, icon.resolveCurrentItemStack(), x, y);
    }

    public static void drawMiniItemIcon(Minecraft mc, ItemStack stack, int x, int y) {
        if (stack == null) {
            return;
        }
        try {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0);
            float s = (float) ICON_SIZE / 16f;
            GL11.glScalef(s, s, 1f);
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL11.GL_NORMALIZE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            RenderItem ri = RenderItem.getInstance();
            ri.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
            RenderHelper.disableStandardItemLighting();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glPopMatrix();
        } catch (Throwable ignored) {
            GL11.glPopMatrix();
        } finally {
            GL11.glPopAttrib();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    public static void drawMiniTextureIcon(@Nullable GuidePageTexture texture, int x, int y) {
        if (texture == null || texture.isMissing()) {
            return;
        }

        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(texture.getTexture());
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        var tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x, y + ICON_SIZE, 0, 0f, 1f);
        tess.addVertexWithUV(x + ICON_SIZE, y + ICON_SIZE, 0, 1f, 1f);
        tess.addVertexWithUV(x + ICON_SIZE, y, 0, 1f, 0f);
        tess.addVertexWithUV(x, y, 0, 0f, 0f);
        tess.draw();
    }

    public static class Row {

        private final GuideNavProjection.DisplayRow displayRow;
        @Nullable
        private String cachedTitle;
        private int cachedMaxTw = -1;

        public Row(GuideNavProjection.DisplayRow displayRow) {
            this.displayRow = displayRow;
        }

        public String getTitle(FontRenderer fr, int maxTw) {
            if (maxTw == cachedMaxTw && cachedTitle != null) {
                return cachedTitle;
            }
            String title = displayRow.title();
            cachedTitle = fr.getStringWidth(title) > maxTw ? fr.trimStringToWidth(title, maxTw - 4) + "\u2026" : title;
            cachedMaxTw = maxTw;
            return cachedTitle;
        }
    }
}
