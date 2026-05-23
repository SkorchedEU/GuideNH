package com.hfstudio.guidenh.guide.mediawiki;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.util.ResourceLocation;

import com.github.bsideup.jabel.Desugar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.hfstudio.guidenh.guide.Guide;

public class MediaWikiSpecialContributors {

    private static final Gson GSON = new Gson();
    private static final ResourceLocation CONTRIBUTORS_ID = new ResourceLocation(
        "guidenh",
        "mediawiki/contributors.json");

    public List<ContributorEntry> load(Guide guide) {
        if (guide == null) {
            return Collections.emptyList();
        }

        byte[] data = guide.loadAsset(CONTRIBUTORS_ID);
        if (data == null || data.length == 0) {
            return Collections.emptyList();
        }

        List<ContributorEntry> contributors = GSON
            .fromJson(new String(data, StandardCharsets.UTF_8), new TypeToken<List<ContributorEntry>>() {}.getType());
        if (contributors == null || contributors.isEmpty()) {
            return Collections.emptyList();
        }

        ArrayList<ContributorEntry> sanitized = new ArrayList<>(contributors.size());
        for (ContributorEntry contributor : contributors) {
            if (contributor == null || contributor.name() == null
                || contributor.name()
                    .trim()
                    .isEmpty()) {
                continue;
            }
            sanitized.add(contributor);
        }
        return sanitized;
    }

    @Desugar
    public record ContributorEntry(String name, String role, String link) {}
}
