package com.itemlimiter.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class ItemLimiterCommand implements CommandExecutor, TabCompleter {

    private final ItemLimiterPlugin plugin;

    public ItemLimiterCommand(ItemLimiterPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            sender.sendMessage(Component.text(
                    "[ItemLimiter] Config reloaded - " + plugin.getLimits().size() + " item limit(s) active."
            ).color(NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /" + label + " reload").color(NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("reload");
        }
        return Collections.emptyList();
    }
}
