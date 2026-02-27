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
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("chestsecure.use")) {
            player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }
        plugin.gui().openMain(player);
        return true;
    }
}
