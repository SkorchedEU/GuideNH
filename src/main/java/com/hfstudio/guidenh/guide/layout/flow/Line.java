package com.hfstudio.guidenh.guide.layout.flow;

import com.hfstudio.guidenh.guide.document.LytRect;

/**
 * A single typeset line produced by {@link LineBuilder}.
 * Kept mutable so {@link FlowBuilder#move} can shift bounds in-place
 * without allocating a new object per line.
 */
public class Line {

    public LytRect bounds;
    public final LineElement firstElement;

    public Line(LytRect bounds, LineElement firstElement) {
        this.bounds = bounds;
        this.firstElement = firstElement;
    }
}
