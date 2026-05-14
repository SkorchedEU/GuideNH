package com.hfstudio.guidenh.guide.internal;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

public class GuideReloadListener implements IResourceManagerReloadListener {

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        GuideLightweightReloadService.reloadGuides(resourceManager);
    }
}
