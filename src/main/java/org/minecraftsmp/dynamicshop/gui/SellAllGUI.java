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
 * and a confirmation button. 54-slot inventory for more space.
 *
 * Uses client-side translatable item names (renders in player's language)
 * and messages.yml keys for all text so admins can translate to Ukrainian.
 */
public class SellAllGUI {

    private final DynamicShop plugin;
    private final Player player;
    private final Inventory inventory;

    private static final int SIZE = 54; // 6 rows — more slots for items
    private static final int ITEM_SLOTS = 45; // First 5 rows for items
    private static final int NAV_ROW_START = 45; // Bottom row for nav

    // Nav slots
    private static final int SLOT_SELL_ALL = 49;  // Center — confirm button
    private static final int SLOT_INFO = 46;       // Summary info
    private static final int SLOT_CANCEL = 53;     // Close

    // Pre-computed sellable data
    private final Map<Material, Integer> sellableItems;
    private double totalPayout;
    private int totalItemCount;
    private int totalTypes;

    public SellAllGUI(DynamicShop plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        String title = plugin.getMessageManager().getMessage("sellall-gui-title");
        if (title == null) title = "&8&lПродати все";
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

        ItemStack filler = org.minecraftsmp.dynamicshop.managers.ConfigCacheManager.getFillerItem();

        // Fill all slots with filler
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        if (sellableItems.isEmpty()) {
            // No items to sell — show empty message
            ItemStack emptyItem = new ItemStack(Material.HOPPER);
            ItemMeta meta = emptyItem.getItemMeta();
            if (meta != null) {
                String emptyName = plugin.getMessageManager().getMessage("sellall-no-items");
                if (emptyName == null) emptyName = "&cНемає предметів для продажу";
                meta.displayName(MessageManager.parseComponent(emptyName));

                String emptyLore = plugin.getMessageManager().getMessage("sellall-no-items-lore");
                if (emptyLore == null) emptyLore = "&7У вашому інвентарі немає предметів,";
                String emptyLore2 = plugin.getMessageManager().getMessage("sellall-no-items-lore2");
                if (emptyLore2 == null) emptyLore2 = "&7які можна продати в магазині.";

                meta.lore(List.of(
                        MessageManager.parseComponent(emptyLore),
                        MessageManager.parseComponent(emptyLore2)
                ));
                emptyItem.setItemMeta(meta);
            }
            inventory.setItem(22, emptyItem);
            return;
        }

        // Render sellable items in first 5 rows
        int slot = 0;
        for (Map.Entry<Material, Integer> entry : sellableItems.entrySet()) {
            if (slot >= ITEM_SLOTS) break;

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
            inventory.setItem(slot, displayItem);
            slot++;
        }

        // ---- Navigation row ----

        // Info button (slot 46) — summary
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            String infoName = plugin.getMessageManager().getMessage("sellall-info-name");
            if (infoName == null) infoName = "&e&lІнформація";
            infoMeta.displayName(MessageManager.parseComponent(infoName));

            String itemsLabel = plugin.getMessageManager().getMessage("sellall-info-items");
            if (itemsLabel == null) itemsLabel = "&7Предметів: &f{count}";
            String typesLabel = plugin.getMessageManager().getMessage("sellall-info-types");
            if (typesLabel == null) typesLabel = "&7Типів: &f{types}";
            String totalLabel = plugin.getMessageManager().getMessage("sellall-info-total");
            if (totalLabel == null) totalLabel = "&7Загалом: &a{price}";

            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(totalItemCount));
            ph.put("types", String.valueOf(totalTypes));
            ph.put("price", plugin.getEconomyManager().format(totalPayout));

            infoMeta.lore(List.of(
                    MessageManager.parseComponent(itemsLabel.replace("{count}", ph.get("count"))
                            .replace("{types}", ph.get("types")).replace("{price}", ph.get("price"))),
                    MessageManager.parseComponent(typesLabel.replace("{count}", ph.get("count"))
                            .replace("{types}", ph.get("types")).replace("{price}", ph.get("price"))),
                    MessageManager.parseComponent(""),
                    MessageManager.parseComponent(totalLabel.replace("{count}", ph.get("count"))
                            .replace("{types}", ph.get("types")).replace("{price}", ph.get("price")))
            ));
            info.setItemMeta(infoMeta);
        }
        inventory.setItem(SLOT_INFO, info);

        // Sell All button (slot 49) — big green confirm
        ItemStack sellAllBtn = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta sellMeta = sellAllBtn.getItemMeta();
        if (sellMeta != null) {
            String sellName = plugin.getMessageManager().getMessage("sellall-confirm-name");
            if (sellName == null) sellName = "&a&l✓ Продати все";
            sellMeta.displayName(MessageManager.parseComponent(sellName));

            String sellLore1 = plugin.getMessageManager().getMessage("sellall-confirm-lore1");
            if (sellLore1 == null) sellLore1 = "&7Натисніть, щоб продати всі";
            String sellLore2 = plugin.getMessageManager().getMessage("sellall-confirm-lore2");
            if (sellLore2 == null) sellLore2 = "&7предмети зі свого інвентарю";

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

        // Cancel / close (slot 53)
        ItemStack cancelBtn = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancelBtn.getItemMeta();
        if (cancelMeta != null) {
            String cancelName = plugin.getMessageManager().getMessage("sellall-cancel-name");
            if (cancelName == null) cancelName = "&c&l✗ Скасувати";
            cancelMeta.displayName(MessageManager.parseComponent(cancelName));
            cancelBtn.setItemMeta(cancelMeta);
        }
        inventory.setItem(SLOT_CANCEL, cancelBtn);
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
            // Use client-side translation for the item name
            String customName = ShopDataManager.getCustomName(mat);
            if (customName != null) {
                meta.displayName(MessageManager.parseComponent("&e&l" + customName));
            } else if (!meta.hasDisplayName()) {
                meta.displayName(ShopItemBuilder.translatableItemName(mat));
            }

            List<String> lore = new ArrayList<>();

            // Amount line
            String amountLabel = plugin.getMessageManager().getMessage("sellall-lore-amount");
            if (amountLabel == null) amountLabel = "&7Кількість: &f{amount}";
            lore.add(amountLabel.replace("{amount}", String.valueOf(heldAmount)));

            // Sell price line
            String priceLabel = plugin.getMessageManager().getMessage("sellall-lore-price");
            if (priceLabel == null) priceLabel = "&7Ціна продажу: &e{price}";
            lore.add(priceLabel.replace("{price}", plugin.getEconomyManager().format(payout)));

            // Per-unit price
            double unitPrice = sellAmount > 0 ? payout / sellAmount : 0;
            String unitLabel = plugin.getMessageManager().getMessage("sellall-lore-unit");
            if (unitLabel == null) unitLabel = "&8({price} за шт.)";
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
            String emptyMsg = plugin.getMessageManager().getMessage("sellall-no-items");
            if (emptyMsg == null) emptyMsg = "&cНемає предметів для продажу!";
            player.sendMessage(MessageManager.parseComponent(emptyMsg));
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
            String failMsg = plugin.getMessageManager().getMessage("sellall-failed");
            if (failMsg == null) failMsg = "&cНе вдалося продати жодного предмету (сховище переповнене).";
            player.sendMessage(MessageManager.parseComponent(failMsg));
            return;
        }

        plugin.getEconomyManager().deposit(player, actualPayout);

        Map<String, String> ph = new HashMap<>();
        ph.put("count", String.valueOf(actualItems));
        ph.put("types", String.valueOf(actualTypes));
        ph.put("price", plugin.getEconomyManager().format(actualPayout));

        String successMsg = plugin.getMessageManager().getMessage("sellall-success", ph);
        if (successMsg == null) successMsg = "&a✓ &7Продано &f{count} предметів &7(&e{types} типів&7) за &a{price}";
        player.sendMessage(MessageManager.parseComponent(
                successMsg.replace("{count}", ph.get("count"))
                        .replace("{types}", ph.get("types"))
                        .replace("{price}", ph.get("price"))));
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
