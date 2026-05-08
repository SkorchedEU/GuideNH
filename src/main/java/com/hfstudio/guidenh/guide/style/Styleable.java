package com.hfstudio.guidenh.guide.style;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.hfstudio.guidenh.guide.document.DefaultStyles;

/**
 * Interface implemented on layout elements that can be styled and inherit styling from their parents.
 */
public interface Styleable {

    TextStyle getStyle();

    void setStyle(TextStyle style);

    TextStyle getHoverStyle();

    void setHoverStyle(TextStyle style);

    @Nullable
    Styleable getStylingParent();

    default void modifyStyle(Consumer<TextStyle.Builder> customizer) {
        var builder = getStyle().toBuilder();
        customizer.accept(builder);
        setStyle(builder.build());
    }

    default void modifyHoverStyle(Consumer<TextStyle.Builder> customizer) {
        var builder = getHoverStyle().toBuilder();
        customizer.accept(builder);
        var hoverStyle = builder.build();
        if (hoverStyle.whiteSpace() != null) {
            throw new IllegalStateException("Hover-Style may not override layout properties");
        }
        setHoverStyle(hoverStyle);
    }

    default ResolvedTextStyle resolveStyle() {
        if (getStyle() == TextStyle.EMPTY) {
            var stylingParent = getStylingParent();
            return stylingParent != null ? stylingParent.resolveStyle() : DefaultStyles.BASE_STYLE;
        }
        var stylingParent = getStylingParent();
        return getStyle().mergeWith(stylingParent != null ? stylingParent.resolveStyle() : DefaultStyles.BASE_STYLE);
    }

    default ResolvedTextStyle resolveHoverStyle(ResolvedTextStyle baseStyle) {
        if (getHoverStyle() == TextStyle.EMPTY) {
            var stylingParent = getStylingParent();
            return stylingParent != null ? stylingParent.resolveHoverStyle(baseStyle) : baseStyle;
        }
        var stylingParent = getStylingParent();
        return getHoverStyle()
            .mergeWith(stylingParent != null ? stylingParent.resolveHoverStyle(baseStyle) : baseStyle);
    }
}
