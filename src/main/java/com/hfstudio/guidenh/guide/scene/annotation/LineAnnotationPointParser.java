package com.hfstudio.guidenh.guide.scene.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Vector3f;

import com.hfstudio.guidenh.guide.compiler.tags.MdxAttrs;

public class LineAnnotationPointParser {

    private LineAnnotationPointParser() {}

    public static List<Vector3f> parsePoints(String raw) {
        if (raw == null || raw.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("points expects at least two semicolon-separated points.");
        }
        String[] tokens = raw.split(";");
        List<Vector3f> points = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            String point = token.trim();
            if (point.isEmpty()) {
                continue;
            }
            float[] values = MdxAttrs.parseVector3Parts(point);
            if (values == null) {
                throw new IllegalArgumentException(
                    "points expects semicolon-separated 'x y z' triples, got: '" + raw + "'");
            }
            points.add(new Vector3f(values[0], values[1], values[2]));
        }
        if (points.size() < 2) {
            throw new IllegalArgumentException("points expects at least two semicolon-separated points.");
        }
        return Collections.unmodifiableList(points);
    }
}
