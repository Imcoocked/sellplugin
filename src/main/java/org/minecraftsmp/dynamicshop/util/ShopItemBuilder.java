package org.minecraftsmp.dynamicshop.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.minecraftsmp.dynamicshop.managers.MessageManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility to build standardized ItemStack displays for the
 * ProtocolLib virtual GUIs.
 *
 * - Category icons
 * - Shop items (with buy/sell price)
 * - Navigation buttons
 * - Placeholder filler panes
 */
public class ShopItemBuilder {

    // ---------------------------------------------------------
    // BUILD AN ITEM FOR THE SHOP ITEM LIST (with sell price)
    // ---------------------------------------------------------
    public static ItemStack buildShopDisplayItem(Material mat, String formattedBuyPrice, String formattedSellPrice) {
        ItemStack item = new ItemStack(mat);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {

            meta.displayName(translatableItemName(mat));

            List<Component> lore = new ArrayList<>();
            lore.add(component("§7────────────────────"));
            lore.add(component("§a§lBUY: §f" + formattedBuyPrice));

            // Only show sell price if item can be sold
            if (formattedSellPrice != null && !formattedSellPrice.equals("N/A")) {
                lore.add(component("§c§lSELL: §f" + formattedSellPrice));
            }

            lore.add(component("§7────────────────────"));
            lore.add(component("§7Left-click to §aBUY"));

            if (formattedSellPrice != null && !formattedSellPrice.equals("N/A")) {
                lore.add(component("§7Right-click to §cSELL"));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // Backward compatibility - defaults to no sell price
    public static ItemStack buildShopDisplayItem(Material mat, String formattedPrice) {
        return buildShopDisplayItem(mat, formattedPrice, null);
    }

    // ---------------------------------------------------------
    // CATEGORY / NAV ITEMS
    // ---------------------------------------------------------
    public static ItemStack navItem(String name, Material icon, String... loreLines) {
        ItemStack item = new ItemStack(icon);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {

            // Handle both & and § in name if present
            meta.displayName(MessageManager.parseComponent(name));

            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MessageManager.parseComponent(line));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Create a nav item using a Nexo custom item if available, falling back to vanilla Material.
     * @param name Display name
     * @param nexoId Nexo item ID (e.g. "shop_back_button")
     * @param fallbackIcon Vanilla material to use if Nexo isn't available
     * @param loreLines Lore text lines
     */
    public static ItemStack navItemNexo(String name, String nexoId, Material fallbackIcon, String... loreLines) {
        ItemStack item = null;

        // Try Nexo custom item first
        if (nexoId != null && org.minecraftsmp.dynamicshop.DynamicShop.getInstance()
                .getServer().getPluginManager().getPlugin("Nexo") != null) {
            item = org.minecraftsmp.dynamicshop.managers.NexoWrapper.getItem(nexoId);
            if (item != null) {
                item = item.clone();
            }
        }

        // Fall back to vanilla material
        if (item == null) {
            item = new ItemStack(fallbackIcon);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageManager.parseComponent(name));

            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MessageManager.parseComponent(line));
            }

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    // ---------------------------------------------------------
    // FILLER PANE (DECORATION)
    // ---------------------------------------------------------
    public static ItemStack filler() {
        return org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();
    }

    // ---------------------------------------------------------
    // TRANSLATABLE ITEM NAME
    // Uses Minecraft's built-in translation keys so item names
    // render in the player's client language (e.g. Ukrainian).
    // ---------------------------------------------------------
    public static Component translatableItemName(Material mat) {
        try {
            return Component.translatable(mat.translationKey())
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD);
        } catch (NoSuchMethodError e) {
            // Fallback for older servers without Material.translationKey()
            return component("§e§l" + prettify(mat.name()));
        }
    }

    /**
     * Get the translation key for a material, or prettified name as fallback.
     */
    public static String getMaterialTranslationKey(Material mat) {
        try {
            return mat.translationKey();
        } catch (NoSuchMethodError e) {
            return prettify(mat.name());
        }
    }

    // ---------------------------------------------------------
    // UTILITY – PRETTIFY MATERIAL NAMES (fallback)
    // STONE_BRICKS => Stone Bricks
    // ---------------------------------------------------------
    static String prettify(String input) {
        String[] parts = input.split("_");
        StringBuilder out = new StringBuilder();

        for (String s : parts) {
            if (s.isEmpty())
                continue;

            out.append(s.substring(0, 1).toUpperCase());
            out.append(s.substring(1).toLowerCase());
            out.append(" ");
        }

        return out.toString().trim();
    }

    private static Component component(String text) {
        return MessageManager.parseComponent(text);
    }
}
