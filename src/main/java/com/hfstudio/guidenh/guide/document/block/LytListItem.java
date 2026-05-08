package com.hfstudio.guidenh.guide.document.block;

import com.hfstudio.guidenh.guide.color.SymbolicColor;
import com.hfstudio.guidenh.guide.document.DefaultStyles;
import com.hfstudio.guidenh.guide.document.LytRect;
import com.hfstudio.guidenh.guide.layout.LayoutContext;
import com.hfstudio.guidenh.guide.render.RenderContext;
import com.hfstudio.guidenh.guide.style.ResolvedTextStyle;

public class LytListItem extends LytVBox {

    public static final int LEVEL_MARGIN = 10;

    private final ResolvedTextStyle style = DefaultStyles.BODY_TEXT.mergeWith(DefaultStyles.BASE_STYLE);

    /**
     * Cached ordered item number from the last layout pass.
     * -1 means the item is in an unordered list or has no list parent.
     * Updated in {@link #computeBoxLayout} so that {@link #render} avoids an O(N) sibling scan every frame.
     */
    private int cachedOrderedNumber = -1;

    @Override
    protected LytRect computeBoxLayout(LayoutContext context, int x, int y, int availableWidth) {
        // Compute and cache the ordered item number once per layout pass.
        if (parent instanceof LytList list && list.isOrdered()) {
            int number = list.getStart();
            for (var child : list.getChildren()) {
                if (child == this) break;
                if (child instanceof LytListItem) number++;
            }
            cachedOrderedNumber = number;
        } else {
            cachedOrderedNumber = -1;
        }
        var margin = LEVEL_MARGIN;
        var bounds = super.computeBoxLayout(context, x + margin, y, availableWidth - margin);
        return bounds.expand(LEVEL_MARGIN, 0, 0, 0);
    }

    @Override
    public void render(RenderContext context) {
        if (cachedOrderedNumber >= 0) {
            String label = cachedOrderedNumber + ".";
            var width = context.getWidth(label, style);
            var bounds = getBounds();
            var x = bounds.x() + LEVEL_MARGIN - width - 2;
            context.drawText(label, x, bounds.y(), style);
        } else {
            var bounds = getBounds();
            context.fillRect(bounds.x() + 5, bounds.y() + 4, 2, 2, SymbolicColor.BODY_TEXT);
        }
        super.render(context);
    }
}
