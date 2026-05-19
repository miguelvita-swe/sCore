package br.com.skyy.core.menu;

import br.com.skyy.core.SCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Global inventory listener registered by SCorePlugin.
 * Handles all SCoreMenu instances automatically.
 */
public class SCoreMenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SCoreMenu)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;

        // Only handle clicks inside the top inventory (menu), not the player's own inventory
        int topSize = event.getView().getTopInventory().getSize();
        if (event.getRawSlot() < 0 || event.getRawSlot() >= topSize) return;

        final Player player = (Player) event.getWhoClicked();
        final SCoreMenu menu = (SCoreMenu) event.getView().getTopInventory().getHolder();
        final ItemStack item = event.getCurrentItem();

        if (item == null || item.getType() == Material.AIR) return;

        final int rawSlot = event.getRawSlot();
        final org.bukkit.event.inventory.ClickType clickType = event.getClick();

        Bukkit.getScheduler().runTask(SCorePlugin.getInstance(), new Runnable() {
            @Override
            public void run() {
                menu.onClick(player, rawSlot, item, clickType);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SCoreMenu)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        SCoreMenu menu = (SCoreMenu) event.getView().getTopInventory().getHolder();
        menu.onClose((Player) event.getPlayer());
    }
}
