package com.itemlimiter.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Caps how many of a given item a single player may hold at once, counting
 * their main inventory, armor slots, and offhand together.
 *
 * Design notes (why this shouldn't dupe or eat items):
 *
 *  - Picking an item up off the ground is blocked outright once a player is
 *    at the cap for that item - see LimiterListener#onPickup. Nothing is
 *    split or partially granted, so there's no arithmetic there that could
 *    go wrong. The stack simply stays on the ground exactly as it was.
 *
 *  - Every other way an item can land in an inventory (crafting, chests,
 *    shift-clicking, dragging, creative mode, joining/respawning with
 *    excess, /give, other plugins) is handled by a single enforcement pass,
 *    enforceLimits(), which runs a tick after any relevant event. Rather
 *    than trying to intercept each of those cases individually (which is
 *    where a lot of plugins like this get dupe bugs from), it just measures
 *    what the player is actually holding, removes whatever is over the
 *    limit, and drops exactly that amount on the ground. Total item count
 *    never changes - it only moves from inventory to ground.
 *
 *  - A repeating task re-runs that same enforcement pass on every online
 *    player every 5 seconds, as a catch-all for anything that doesn't fire
 *    a Bukkit event at all (e.g. some other plugin calling addItem() on a
 *    player directly).
 */
public final class ItemLimiterPlugin extends JavaPlugin {

    private final Map<Material, Integer> limits = new HashMap<>();
    private String excessMessage = "";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();

        getServer().getPluginManager().registerEvents(new LimiterListener(this), this);

        ItemLimiterCommand commandHandler = new ItemLimiterCommand(this);
        PluginCommand command = getCommand("itemlimiter");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        }

        // Safety-net sweep, every 100 ticks (5 seconds).
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                enforceLimits(player);
            }
        }, 100L, 100L);

        getLogger().info("ItemLimiter enabled - " + limits.size() + " limited item(s) configured.");
    }

    /** Reloads config.yml and rebuilds the in-memory limit map. */
    public void reload() {
        reloadConfig();
        limits.clear();

        ConfigurationSection section = getConfig().getConfigurationSection("limits");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                if (material == null) {
                    getLogger().warning("config.yml: '" + key + "' is not a valid material name - skipping.");
                    continue;
                }
                int max = section.getInt(key, -1);
                if (max < 0) {
                    getLogger().warning("config.yml: limit for '" + key + "' must be 0 or higher - skipping.");
                    continue;
                }
                limits.put(material, max);
            }
        }

        excessMessage = getConfig().getString("excess-message", "");
    }

    public Integer getLimit(Material material) {
        return limits.get(material);
    }

    public Map<Material, Integer> getLimits() {
        return limits;
    }

    /** Total amount of `material` a player is holding: storage + armor + offhand. */
    public int countMaterial(Player player, Material material) {
        int count = 0;
        PlayerInventory inv = player.getInventory();

        for (ItemStack stack : inv.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        for (ItemStack stack : inv.getArmorContents()) {
            if (stack != null && stack.getType() == material) {
                count += stack.getAmount();
            }
        }
        ItemStack offHand = inv.getItemInOffHand();
        if (offHand.getType() == material) {
            count += offHand.getAmount();
        }
        return count;
    }

    /** If `player` is over the limit for any tracked item, drops the excess at their feet. */
    public void enforceLimits(Player player) {
        if (!player.isOnline() || player.hasPermission("itemlimiter.bypass")) {
            return;
        }
        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            Material material = entry.getKey();
            int max = entry.getValue();
            int have = countMaterial(player, material);
            if (have > max) {
                removeAndDrop(player, material, have - max);
                announceExcess(player, material, max);
            }
        }
    }

    private void announceExcess(Player player, Material material, int max) {
        if (excessMessage == null || excessMessage.isEmpty()) {
            return;
        }
        String formatted = excessMessage
                .replace("%item%", prettyName(material))
                .replace("%limit%", String.valueOf(max));
        player.sendMessage(Component.text(formatted).color(NamedTextColor.YELLOW));
    }

    /** Removes `amountToRemove` of `material` from the player's inventory and drops it on the ground. */
    private void removeAndDrop(Player player, Material material, int amountToRemove) {
        PlayerInventory inv = player.getInventory();

        ItemStack[] storage = inv.getStorageContents();
        int remaining = stripFrom(storage, material, amountToRemove);
        inv.setStorageContents(storage);

        if (remaining > 0) {
            ItemStack[] armor = inv.getArmorContents();
            remaining = stripFrom(armor, material, remaining);
            inv.setArmorContents(armor);
        }

        if (remaining > 0) {
            ItemStack offHand = inv.getItemInOffHand();
            if (offHand.getType() == material) {
                int take = Math.min(remaining, offHand.getAmount());
                offHand.setAmount(offHand.getAmount() - take);
                inv.setItemInOffHand(offHand);
                remaining -= take;
            }
        }

        int removed = amountToRemove - remaining;
        int maxStack = Math.max(1, material.getMaxStackSize());
        while (removed > 0) {
            int stackSize = Math.min(removed, maxStack);
            player.getWorld().dropItem(player.getLocation(), new ItemStack(material, stackSize));
            removed -= stackSize;
        }
    }

    /** Decrements matching stacks in `slots` by up to `remaining` total; returns what's left to remove. */
    private int stripFrom(ItemStack[] slots, Material material, int remaining) {
        for (int i = 0; i < slots.length && remaining > 0; i++) {
            ItemStack stack = slots[i];
            if (stack != null && stack.getType() == material) {
                int take = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - take);
                if (stack.getAmount() <= 0) {
                    slots[i] = null;
                }
                remaining -= take;
            }
        }
        return remaining;
    }

    private String prettyName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
