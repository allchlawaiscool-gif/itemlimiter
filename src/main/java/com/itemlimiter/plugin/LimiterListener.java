package com.itemlimiter.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Wires up every vector that can put items into a player's inventory.
 *
 * Ground pickup is the only thing cancelled outright (see onPickup). Every
 * other handler just schedules a one-tick-later re-check via
 * ItemLimiterPlugin#enforceLimits, so we're always reading the inventory
 * after it has fully settled rather than mid-event, which is where a lot
 * of "close but not quite" bugs come from in these events.
 */
public class LimiterListener implements Listener {

    private final ItemLimiterPlugin plugin;

    public LimiterListener(ItemLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("itemlimiter.bypass")) {
            return;
        }

        Material type = event.getItem().getItemStack().getType();
        Integer max = plugin.getLimit(type);
        if (max == null) {
            return;
        }

        int have = plugin.countMaterial(player, type);
        int incoming = event.getItem().getItemStack().getAmount();
        if (have + incoming > max) {
            // Block the whole pickup - the stack stays on the ground, untouched.
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreative(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleCheck(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleCheck(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleCheck(event.getPlayer());
    }

    private void scheduleCheck(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.enforceLimits(player));
    }
}
