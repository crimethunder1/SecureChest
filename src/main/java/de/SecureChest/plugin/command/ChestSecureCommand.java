package de.SecureChest.plugin.command;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ChestSecureCommand implements CommandExecutor {

    private final ChestSecurePlugin plugin;

    public ChestSecureCommand(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("chestsecure.use")) {
            player.sendMessage("No permission.");
            return true;
        }
        plugin.gui().openMain(player);
        return true;
    }
}
