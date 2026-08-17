package org.minecraftsmp.dynamicshop.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.minecraftsmp.dynamicshop.DynamicShop;
import org.minecraftsmp.dynamicshop.gui.SellAllGUI;

/**
 * /sellall command
 *
 * Opens the SellAllGUI — a visual preview of all sellable items
 * in the player's inventory with a confirmation button.
 */
public class SellAllCommand implements CommandExecutor {

    private final DynamicShop plugin;

    public SellAllCommand(DynamicShop plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            String consoleMsg = plugin.getMessageManager().getMessage("sellall-console-only");
            if (consoleMsg == null) consoleMsg = "&cТільки гравці можуть використовувати цю команду.";
            sender.sendMessage(consoleMsg);
            return true;
        }

        if (!p.hasPermission("dynamicshop.use.sellall")) {
            p.sendMessage(plugin.getMessageManager().noPermission());
            return true;
        }

        // Open SellAllGUI
        SellAllGUI gui = new SellAllGUI(plugin, p);
        plugin.getShopListener().registerSellAllGUI(p, gui);
        gui.open();

        return true;
    }
}
