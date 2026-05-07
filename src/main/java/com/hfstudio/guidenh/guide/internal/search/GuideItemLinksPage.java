package com.hfstudio.guidenh.guide.internal.search;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.hfstudio.guidenh.guide.PageAnchor;
import com.hfstudio.guidenh.guide.indices.ItemIndex;
import com.hfstudio.guidenh.guide.internal.GuideME;

/**
 * Helpers for the virtual "item links" page that lists all guide pages bound to a specific item.
 * The anchor field stores the item identity as {@code "modid:name"} or {@code "modid:name:meta"},
 * matching the format produced by {@link ItemIndex#formatKey(ItemId)}.
 */
public class GuideItemLinksPage {

    public static final ResourceLocation PAGE_ID = GuideME.makeId("item_links");

    private GuideItemLinksPage() {}

    public static boolean isItemLinksAnchor(@Nullable PageAnchor anchor) {
        return anchor != null && PAGE_ID.equals(anchor.pageId());
    }

    /**
     * Builds an anchor that encodes the given item stack (preserving exact meta).
     */
    public static PageAnchor anchorForStack(ItemStack stack) {
        Item item = stack.getItem();
        ItemId id = ItemId.createNoCopy(item, stack.getItemDamage(), null);
        return new PageAnchor(PAGE_ID, ItemIndex.formatKey(id));
    }

    /**
     * Decodes the item stack from an item-links anchor. Returns {@code null} if the anchor is
     * invalid or the item is not registered.
     */
    @Nullable
    public static ItemStack stackFromAnchor(@Nullable PageAnchor anchor) {
        if (!isItemLinksAnchor(anchor) || anchor.anchor() == null) return null;
        String[] parts = anchor.anchor()
            .split(":", 3);
        if (parts.length < 2) return null;
        String itemName = parts[0] + ":" + parts[1];
        int meta = OreDictionary.WILDCARD_VALUE;
        if (parts.length == 3) {
            try {
                meta = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                meta = OreDictionary.WILDCARD_VALUE;
            }
        }
        Item item = (Item) Item.itemRegistry.getObject(itemName);
        if (item == null) return null;
        return new ItemStack(item, 1, meta);
    }
}
