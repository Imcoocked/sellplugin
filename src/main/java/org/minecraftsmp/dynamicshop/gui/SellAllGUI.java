package org.minecraftsmp.dynamicshop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.managers.MessageManager;
import org.minecraftsmp.dynamicshop.managers.ShopDataManager;
import org.minecraftsmp.dynamicshop.util.ShopItemBuilder;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

/**
 * GUI for selling all sellable items at once.
 * Shows a preview of what will be sold with prices,
 * and a confirmation button. 54-slot inventory with border frame.
 *
 * Layout (6 rows x 9 columns = 54 slots):
 * [G][G][G][G][G][G][G][G][G]   (row 0 — top border)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 1 — left/right border + items)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 2)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 3)
 * [G][G][ ][ ][ ][ ][ ][G][G]   (row 4)
 * [G][G][G][G][G][G][G][G][G]   (row 5 — bottom border, with nav buttons)
 *
 * Center area: rows 1-4, columns 2-6 (5 cols x 4 rows = 20 item slots)
 * Nav buttons in bottom border row: slots 47 (info), 49 (sell all), 51 (cancel)
 *
 * All text defaults to Ukrainian; admins can override via messages.yml.
 */
public class SellAllGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inventory;

    private static final int SIZE = 54;
    private static final int COLS = 9;

    // Border material — light blue stained glass pane matching the design image
    private static final Material BORDER_MATERIAL = Material.LIGHT_BLUE_STAINED_GLASS_PANE;

    // Center area: rows 1-4, columns 2-6
    // That's 5 columns x 4 rows = 20 item slots
    private static final int CENTER_ROW_START = 1;
    private static final int CENTER_ROW_END = 4;
    private static final int CENTER_COL_START = 2;
    private static final int CENTER_COL_END = 6;

    // Nav slots (in bottom border row — row 5, slots 45-53)
    private static final int SLOT_INFO = 47;      // Info button
    private static final int SLOT_SELL_ALL = 49;  // Center — confirm button
    private static final int SLOT_CANCEL = 51;    // Cancel/close

    // Pre-computed sellable data
    private final Map<Material, Integer> sellableItems;
    private double totalPayout;
    private int totalItemCount;
    private int totalTypes;

    public SellAllGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        // Use Ukrainian default title; messages.yml can override
        String title = msgOrDefault("sellall-gui-title", "&8&lПродати все");
        this.inventory = Bukkit.createInventory(null, SIZE,
                MessageManager.parseComponent(title));

        this.sellableItems = new LinkedHashMap<>();
        this.totalPayout = 0;
        this.totalItemCount = 0;
        this.totalTypes = 0;
    }

    public void open() {
        scanInventory();
        render();
        player.openInventory(inventory);
    }

    /**
     * Get a message from messages.yml, or return the default value if the key
     * is missing. getMessage() returns "§cMessage not found: ..." for missing keys,
     * so we detect that pattern and use the default instead.
     */
    private String msgOrDefault(String key, String defaultValue) {
        String msg = plugin.getMessageManager().getMessage(key);
        if (msg == null || msg.contains("Message not found:")) {
            return defaultValue;
        }
        return msg;
    }

    /**
     * Scan player inventory for sellable items.
     */
    private void scanInventory() {
        sellableItems.clear();
        totalPayout = 0;
        totalItemCount = 0;
        totalTypes = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (isDamaged(item)) continue;

            Material mat = item.getType();
            if (ShopDataManager.getBasePrice(mat) < 0) continue;
            if (ShopDataManager.isSellDisabled(mat)) continue;

            sellableItems.merge(mat, item.getAmount(), Integer::sum);
        }

        // Calculate totals
        for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
            Material mat = entry.getKey();
            int amount = entry.getValue();

            // Check stock limits
            if (!ShopDataManager.canSell(mat, amount)) {
                int limit = ShopDataManager.getSellLimit(mat);
                if (limit <= 0) continue;
                amount = Math.min(amount, limit);
            }
            if (amount <= 0) continue;

            double payout = ShopDataManager.getTotalSellValue(mat, amount);
            totalPayout += payout;
            totalItemCount += amount;
            totalTypes++;
        }
    }

    private void render() {
        inventory.clear();

        // Draw the border frame
        ItemStack border = createBorderPane();
        drawBorder(border);

        if (sellableItems.isEmpty()) {
            // No items to sell — show empty message in center
            ItemStack emptyItem = new ItemStack(Material.HOPPER);
            ItemMeta meta = emptyItem.getItemMeta();
            if (meta != null) {
                meta.displayName(MessageManager.parseComponent(
                        msgOrDefault("sellall-no-items", "&cНемає предметів для продажу")));

                meta.lore(List.of(
                        MessageManager.parseComponent(
                                msgOrDefault("sellall-no-items-lore", "&7У вашому інвентарі немає предметів,")),
                        MessageManager.parseComponent(
                                msgOrDefault("sellall-no-items-lore2", "&7які можна продати в магазині."))
                ));
                emptyItem.setItemMeta(meta);
            }
            // Place in center of the inner area (row 2, col 4 = slot 22)
            inventory.setItem(22, emptyItem);
            return;
        }

        // Render sellable items in the center area (rows 1-4, cols 2-6)
        int itemIndex = 0;
        for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
            if (itemIndex >= getCenterSlotCount()) break;

            Material mat = entry.getKey();
            int amount = entry.getValue();

            // Check stock limits
            int sellAmount = amount;
            if (!ShopDataManager.canSell(mat, amount)) {
                int limit = ShopDataManager.getSellLimit(mat);
                if (limit <= 0) continue;
                sellAmount = Math.min(amount, limit);
            }
            if (sellAmount <= 0) continue;

            double payout = ShopDataManager.getTotalSellValue(mat, sellAmount);

            ItemStack displayItem = buildSellableItem(mat, amount, sellAmount, payout);
            int slot = getCenterSlot(itemIndex);
            inventory.setItem(slot, displayItem);
            itemIndex++;
        }

        // ---- Navigation row (bottom border, row 5) ----

        // Info button (slot 47) — summary
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(MessageManager.parseComponent(
                    msgOrDefault("sellall-info-name", "&e&lІнформація")));

            String itemsLabel = msgOrDefault("sellall-info-items", "&7Предметів: &f{count}");
            String typesLabel = msgOrDefault("sellall-info-types", "&7Типів: &f{types}");
            String totalLabel = msgOrDefault("sellall-info-total", "&7Загалом: &a{price}");

            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(totalItemCount));
            ph.put("types", String.valueOf(totalTypes));
            ph.put("price", plugin.getEconomyManager().format(totalPayout));

            infoMeta.lore(List.of(
                    MessageManager.parseComponent(replacePlaceholders(itemsLabel, ph)),
                    MessageManager.parseComponent(replacePlaceholders(typesLabel, ph)),
                    MessageManager.parseComponent(""),
                    MessageManager.parseComponent(replacePlaceholders(totalLabel, ph))
            ));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(SLOT_INFO, info);

        // Sell All button (slot 49) — big green confirm
        ItemStack sellAllBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta sellMeta = sellAllBtn.getItemMeta();
        if (sellMeta != null) {
            sellMeta.displayName(MessageManager.parseComponent(
                    msgOrDefault("sellall-confirm-name", "&a&l✓ Продати все")));

            String sellLore1 = msgOrDefault("sellall-confirm-lore1", "&7Натисніть, щоб продати всі");
            String sellLore2 = msgOrDefault("sellall-confirm-lore2", "&7предмети зі свого інвентарю");

            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(totalItemCount));
            ph.put("price", plugin.getEconomyManager().format(totalPayout));

            sellMeta.lore(List.of(
                    MessageManager.parseComponent(sellLore1),
                    MessageManager.parseComponent(sellLore2),
                    MessageManager.parseComponent(""),
                    MessageManager.parseComponent("&a" + ph.get("price"))
            ));
            sellAllBtn.setItemMeta(sellMeta);
        }
        inventory.setItem(SLOT_SELL_ALL, sellAllBtn);

        // Cancel / close (slot 51)
        ItemStack cancelBtn = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancelBtn.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.displayName(MessageManager.parseComponent(
                    msgOrDefault("sellall-cancel-name", "&c&l✗ Скасувати")));
            cancelBtn.setItemMeta(cancelMeta);
        }
        inventory.setItem(SLOT_CANCEL, cancelBtn);
    }

    /**
     * Draw the border frame with light blue stained glass panes:
     * - Top row (slots 0-8): all glass
     * - Bottom row (slots 45-53): all glass
     * - Middle rows (1-4): glass at columns 0,1 and 7,8
     */
    private void drawBorder(ItemStack border) {
        // Top row
        for (int col = 0; col < COLS; col++) {
            inv().setItem(col, border);
        }
        // Middle rows — left and right edges (2 columns on each side)
        for (int row = 1; row <= 4; row++) {
            int base = row * COLS;
            inv().setItem(base, border);       // column 0
            inv().setItem(base + 1, border);   // column 1
            inv().setItem(base + 7, border);   // column 7
            inv().setItem(base + 8, border);   // column 8
        }
        // Bottom row
        for (int col = 0; col < COLS; col++) {
            inv().setItem(5 * COLS + col, border);
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

    /**
     * Get the inventory slot for a center-area item by its index.
     * Center area: rows 1-4, columns 2-6 (5 cols x 4 rows = 20 slots)
     * Items fill left-to-right, top-to-bottom within the center area.
     */
    private int getCenterSlot(int index) {
        int centerCols = CENTER_COL_END - CENTER_COL_START + 1; // 5
        int row = CENTER_ROW_START + (index / centerCols);
        int col = CENTER_COL_START + (index % centerCols);
        return row * COLS + col;
    }

    /**
     * Total number of center-area slots.
     */
    private int getCenterSlotCount() {
        int centerCols = CENTER_COL_END - CENTER_COL_START + 1; // 5
        int centerRows = CENTER_ROW_END - CENTER_ROW_START + 1; // 4
        return centerCols * centerRows; // 20
    }

    private Inventory inv() {
        return inventory;
    }

    /**
     * Build a display item for one sellable material.
     */
    private ItemStack buildSellableItem(Material mat, int heldAmount, int sellAmount, double payout) {
        ItemStack item;
        try {
            ItemStack template = ShopDataManager.getTemplate(mat);
            if (template != null) {
                item = template.clone();
                item.setAmount(1);
            } else {
                item = new ItemStack(mat, 1);
            }
        } catch (IllegalArgumentException e) {
            item = new ItemStack(mat, 1);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Display name: custom_name > translatable material name
            String customName = ShopDataManager.getCustomName(mat);
            if (customName != null) {
                meta.displayName(MessageManager.parseComponent("&e&l" + customName));
            } else if (!meta.hasDisplayName()) {
                ShopItemBuilder.applyItemName(meta, mat);
            }

            List<String> lore = new ArrayList<>();

            // Amount line
            String amountLabel = msgOrDefault("sellall-lore-amount", "&7Кількість: &f{amount}");
            lore.add(amountLabel.replace("{amount}", String.valueOf(heldAmount)));

            // Sell price line
            String priceLabel = msgOrDefault("sellall-lore-price", "&7Ціна продажу: &e{price}");
            lore.add(priceLabel.replace("{price}", plugin.getEconomyManager().format(payout)));

            // Per-unit price
            double unitPrice = sellAmount > 0 ? payout / sellAmount : 0;
            String unitLabel = msgOrDefault("sellall-lore-unit", "&8({price} за шт.)");
            lore.add(unitLabel.replace("{price}", plugin.getEconomyManager().format(unitPrice)));

            meta.lore(lore.stream().map(s -> MessageManager.parseComponent(s)).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Execute the sell-all operation.
     */
    public void executeSellAll() {
        if (sellableItems.isEmpty()) {
            player.sendMessage(MessageManager.parseComponent(
                    msgOrDefault("sellall-no-items", "&cНемає предметів для продажу!")));
            return;
        }

        double actualPayout = 0;
        int actualItems = 0;
        int actualTypes = 0;

        for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
            Material mat = entry.getKey();
            int amount = entry.getValue();

            // Check stock limits
            if (!ShopDataManager.canSell(mat, amount)) {
                int limit = ShopDataManager.getSellLimit(mat);
                if (limit <= 0) continue;
                amount = Math.min(amount, limit);
            }
            if (amount <= 0) continue;

            double payout = ShopDataManager.getTotalSellValue(mat, amount);

            // Remove items from inventory
            int toRemove = amount;
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.getType() == mat && !isDamaged(item) && toRemove > 0) {
                    int take = Math.min(item.getAmount(), toRemove);
                    int newAmt = item.getAmount() - take;
                    if (newAmt <= 0) {
                        player.getInventory().setItem(i, null);
                    } else {
                        item.setAmount(newAmt);
                    }
                    toRemove -= take;
                }
            }

            int actuallySold = amount - toRemove;
            ShopDataManager.updateStock(mat, actuallySold);
            actualPayout += payout;
            actualItems += actuallySold;
            actualTypes++;

            plugin.getTransactionLogger().log(org.minecraftsmp.dynamicshop.transactions.Transaction.now(
                    player.getName(),
                    org.minecraftsmp.dynamicshop.transactions.Transaction.TransactionType.SELL,
                    mat.name(),
                    actuallySold,
                    payout,
                    ShopDataManager.detectCategory(mat).name(),
                    ""));
        }

        if (actualItems == 0) {
            player.sendMessage(MessageManager.parseComponent(
                    msgOrDefault("sellall-failed", "&cНе вдалося продати жодного предмету (сховище переповнене).")));
            return;
        }

        plugin.getEconomyManager().deposit(player, actualPayout);

        Map<String, String> ph = new HashMap<>();
        ph.put("count", String.valueOf(actualItems));
        ph.put("types", String.valueOf(actualTypes));
        ph.put("price", plugin.getEconomyManager().format(actualPayout));

        String successTemplate = msgOrDefault("sellall-success",
                "&a✓ &7Продано &f{count} предметів &7(&e{types} типів&7) за &a{price}");
        player.sendMessage(MessageManager.parseComponent(replacePlaceholders(successTemplate, ph)));
    }

    private String replacePlaceholders(String text, Map<String, String> placeholders) {
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }

    private boolean isDamaged(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
            return damageable.hasDamage();
        }
        return false;
    }

    // ---- Click handling ----

    public void handleClick(Player p, int slot) {
        if (slot == SLOT_SELL_ALL) {
            // Confirm sell
            p.closeInventory();
            executeSellAll();
        } else if (slot == SLOT_CANCEL) {
            p.closeInventory();
        }
        // Other slots are just display — no action
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Map<Material, Integer> getSellableItems() {
        return sellableItems;
    }
}
