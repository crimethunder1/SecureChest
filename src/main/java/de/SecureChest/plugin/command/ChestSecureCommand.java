package de.SecureChest.plugin.command;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ChestSecureCommand implements CommandExecutor {

    private final ChestSecurePlugin plugin;

    public ChestSecureCommand(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /cs reload – nur für OPs / chestsecure.admin
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chestsecure.admin")) {
                sender.sendMessage(Component.text(
                        plugin.msg().get("reload-keine-berechtigung"), NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            plugin.cfg().reload();
            plugin.msg().reload();
            plugin.worlds().reload();
            sender.sendMessage(Component.text(
                    plugin.msg().get("reload-erfolg"), NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(plugin.msg().get("nur-spieler"), NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("chestsecure.use")) {
            player.sendMessage(Component.text(plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            return true;
        }
        plugin.gui().openMain(player);
        return true;
    }
}
