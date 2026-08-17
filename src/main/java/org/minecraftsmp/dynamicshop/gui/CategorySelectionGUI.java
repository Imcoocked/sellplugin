package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.category.ItemCategory;
import org.minecraftsmp.dynamicshop.managers.CategoryConfigManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI for selecting a category to browse in the shop.
 * 
 * Uses CategoryConfigManager for dynamic slot positions, icons, and names.
 * Layout: LIGHT_BLUE_STAINED_GLASS_PANE border frame with items in center.
 *
 * Border layout (6 rows x 9 columns = 54 slots):
 * [G][G][G][G][G][G][G][G][G]   (row 0)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 1)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 2)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 3)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 4)
 * [G][G][G][G][G][G][G][G][G]   (row 5)
 *
 * Center area (5 cols x 4 rows = 20 slots): rows 1-4, columns 2-6
 */
public class CategorySelectionGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inv;

    private static final int SIZE = 54;
    private static final int COLS = 9;

    // Border material — light blue stained glass pane as shown in the design
    private static final Material BORDER_MATERIAL = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

    public CategorySelectionGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        
        String title = plugin.getMessageManager().getMessage("gui-category-title");
        if (title == null) title = "§8§lShop Categories";
        
        this.inv = Bukkit.createInventory(null, SIZE,
                MessageManager.parseComponent(title, player));
    }

    public void open() {
        setupItems();
        player.openInventory(inv);
    }

    private void setupItems() {
        // Clear inventory
        inv.clear();

        // Draw the border frame with light blue stained glass panes
        ItemStack border = createBorderPane();
        drawBorder(border);

        // Place categories at their configured slots
        for (ItemCategory category : ItemCategory.values()) {
            int slot = CategoryConfigManager.getSlot(category);
            if (slot >= 0 && slot < SIZE) {
                inv.setItem(slot, createCategoryItem(category));
            }
        }
    }

    /**
     * Draw the border frame:
     * - Top row (slots 0-8): all glass
     * - Bottom row (slots 45-53): all glass
     * - Middle rows (1-4): glass at columns 0,1 and 7,8
     */
    private void drawBorder(ItemStack border) {
        // Top row
        for (int col = 0; col < COLS; col++) {
            inv.setItem(col, border);
        }
        // Middle rows — left and right edges
        for (int row = 1; row <= 4; row++) {
            int base = row * COLS;
            inv.setItem(base, border);       // column 0
            inv.setItem(base + 1, border);   // column 1
            inv.setItem(base + 7, border);   // column 7
            inv.setItem(base + 8, border);   // column 8
        }
        // Bottom row
        for (int col = 0; col < COLS; col++) {
            inv.setItem(5 * COLS + col, border);
        }
    }

    /**
     * Create the border glass pane with an invisible name.
     */
    private ItemStack createBorderPane() {
        ItemStack pane = new ItemStack(BORDER_MATERIAL);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageManager.parseComponent(" "));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createCategoryItem(ItemCategory category) {
        // Use configured icon and name from CategoryConfigManager
        ItemStack item = CategoryConfigManager.getIconItem(category);
        String displayName = CategoryConfigManager.getDisplayName(category);

        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Apply colors if present
            String formattedName = displayName.contains("&")
                    ? displayName
                    : "§e§l" + displayName;
            meta.displayName(MessageManager.parseComponent(formattedName));

            List<String> lore = new ArrayList<>();
            lore.add("§7───────────────────");

            // Get item count for this category
            int itemCount = getItemCount(category);

            if (itemCount > 0) {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().categoryLoreItems(itemCount));
                lore.add("§7");
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().categoryLoreClickToBrowse());
            } else {
                MessageManager.addLoreIfNotEmpty(lore, plugin.getMessageManager().categoryLoreNoItems());
            }

            lore.add("§7───────────────────");

            meta.lore(lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }

        return item;
    }

    private int getItemCount(ItemCategory category) {
        if (category == ItemCategory.PERMISSIONS) {
            return plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == ItemCategory.PERMISSIONS)
                    .toArray().length;
        } else if (category == ItemCategory.SERVER_SHOP) {
            return plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == ItemCategory.SERVER_SHOP)
                    .toArray().length;
        } else if (category == ItemCategory.PLAYER_SHOPS) {
            // Return number of active player shops
            return plugin.getPlayerShopManager().getActiveShopOwners().size();
        } else {
            int normalItems = ShopDataManager.getItemsInCategory(category).size();
            long specialItems = plugin.getSpecialShopManager().getAllSpecialItems().values().stream()
                    .filter(item -> item.getCategory() == category)
                    .count();
            return normalItems + (int) specialItems;
        }
    }

    public void handleClick(Player p, int slot) {
        // Find category at this slot using CategoryConfigManager
        ItemCategory category = CategoryConfigManager.getCategoryAtSlot(slot);

        if (category == null) {
            return; // Clicked a filler or empty slot
        }

        // Check if category has items
        if (getItemCount(category) == 0) {
            p.sendMessage(plugin.getMessageManager().categoryEmpty());
            return;
        }

        // Close current inventory
        p.closeInventory();

        // Handle Player Shops category specially
        if (category == ItemCategory.PLAYER_SHOPS) {
            org.minecraftsmp.dynamicshop.gui.PlayerShopBrowserGUI browserGUI = new org.minecraftsmp.dynamicshop.gui.PlayerShopBrowserGUI(
                    plugin, p);
            plugin.getPlayerShopListener().registerBrowserGUI(p, browserGUI);
            browserGUI.open();
            return;
        }

        // Open the shop GUI for this category
        ShopGUI shopGUI = new ShopGUI(plugin, p, category);
        plugin.getShopListener().registerShop(p, shopGUI);
        shopGUI.open();
    }

    public Inventory getInventory() {
        return inv;
    }
}
