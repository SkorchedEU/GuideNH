package com.hfstudio.guidenh.guide.scene.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.hfstudio.guidenh.compat.Mods;
import com.hfstudio.guidenh.compat.ae2.Ae2Helpers;
import com.hfstudio.guidenh.compat.carpentersblocks.CarpentersBlocksHelpers;
import com.hfstudio.guidenh.compat.forgemultipart.ForgeMultipartHelpers;
import com.hfstudio.guidenh.guide.scene.level.GuidebookLevel;

public final class GuideBlockStatsStackResolver {

    private GuideBlockStatsStackResolver() {}

    public static List<ItemStack> resolveStacks(GuidebookLevel level, int x, int y, int z) {
        if (level == null) {
            return Collections.emptyList();
        }
        Block block = level.getBlock(x, y, z);
        if (block == null || block == Blocks.air) {
            return Collections.emptyList();
        }
        TileEntity tileEntity = level.getTileEntity(x, y, z);
        ArrayList<ItemStack> stacks = new ArrayList<>(4);
        appendMultipartStacks(level, block, tileEntity, x, y, z, stacks);
        if (stacks.isEmpty()) {
            appendFallbackStack(level, x, y, z, stacks);
        }
        normalizeStacks(stacks);
        return stacks;
    }

    private static void appendMultipartStacks(GuidebookLevel level, Block block, TileEntity tileEntity, int x, int y,
        int z, List<ItemStack> stacks) {
        if (Mods.AE2.isModLoaded()) {
            Ae2Helpers.appendCableBusStatStacks(tileEntity, stacks);
        }
        if (stacks.isEmpty() && Mods.ForgeMultipart.isModLoaded()
            && (ForgeMultipartHelpers.isForgeMultipartBlock(block)
                || ForgeMultipartHelpers.isMultipartTileEntity(tileEntity))) {
            ForgeMultipartHelpers.appendMultipartStatStacks(tileEntity, stacks);
        }
        if (stacks.isEmpty() && CarpentersBlocksHelpers.isCarpentersBlock(block)) {
            CarpentersBlocksHelpers.appendComponentStatStacks(tileEntity, stacks);
        }
    }

    private static void appendFallbackStack(GuidebookLevel level, int x, int y, int z, List<ItemStack> stacks) {
        try {
            ItemStack stack = GuideBlockDisplayResolver.resolveDisplayStack(level, x, y, z);
            if (stack != null) {
                stacks.add(stack);
            }
        } catch (Throwable ignored) {}
    }

    private static void normalizeStacks(List<ItemStack> stacks) {
        for (int i = stacks.size() - 1; i >= 0; i--) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.getItem() == null || stack.stackSize <= 0) {
                stacks.remove(i);
            }
        }
    }
}
