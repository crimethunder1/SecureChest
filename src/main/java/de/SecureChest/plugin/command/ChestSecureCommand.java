package de.SecureChest.plugin.command;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.Permissions;
import java.util.Arrays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ChestSecureCommand implements CommandExecutor {

    private final ChestSecurePlugin plugin;

    public ChestSecureCommand(ChestSecurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(Permissions.ADMIN)) {
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

        if (args.length >= 1 && args[0].equalsIgnoreCase("givekey")) {
            giveKey(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(plugin.msg().get("nur-spieler"), NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(Permissions.USE)) {
            player.sendMessage(Component.text(plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            return true;
        }
        plugin.gui().openMain(player);
        return true;
    }

    private void giveKey(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission(Permissions.ADMIN)) {
            sender.sendMessage(Component.text(plugin.msg().get("keine-berechtigung"), NamedTextColor.RED));
            return;
        }
        ItemStack keys = plugin.items().chestKey();
        int upperLimit = GiveKeyArgumentParser.upperLimit(keys.getMaxStackSize());
        switch (GiveKeyArgumentParser.parse(arguments, upperLimit)) {
            case GiveKeyArgumentParser.Rejected rejected -> sendRejection(sender, rejected, upperLimit);
            case GiveKeyArgumentParser.Request request -> deliverKeys(sender, request, keys);
        }
    }

    private void sendRejection(CommandSender sender, GiveKeyArgumentParser.Rejected rejected, int upperLimit) {
        String message = switch (rejected.reason()) {
            case TOO_MANY_ARGUMENTS -> plugin.msg().get("givekey-benutzung");
            case INVALID_AMOUNT -> plugin.msg().get("givekey-anzahl-ungueltig", rejected.detail(), upperLimit);
        };
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private void deliverKeys(CommandSender sender, GiveKeyArgumentParser.Request request, ItemStack keys) {
        Player target;
        if (request.hasPlayerName()) {
            target = Bukkit.getPlayerExact(request.playerName());
            if (target == null) {
                sender.sendMessage(Component.text(
                        plugin.msg().get("givekey-spieler-nicht-gefunden", request.playerName()), NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text(plugin.msg().get("givekey-konsole-ohne-spieler"), NamedTextColor.RED));
            return;
        }

        keys.setAmount(request.amount());
        int droppedAmount = 0;
        for (ItemStack leftover : target.getInventory().addItem(keys).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
            droppedAmount += leftover.getAmount();
        }

        boolean senderIsTarget = target.equals(sender);
        sender.sendMessage(Component.text(
                plugin.msg().get("givekey-gegeben", request.amount(), target.getName()), NamedTextColor.GREEN));
        if (!senderIsTarget) {
            target.sendMessage(Component.text(
                    plugin.msg().get("givekey-erhalten", request.amount()), NamedTextColor.GREEN));
        }
        if (droppedAmount > 0) {
            if (!senderIsTarget) {
                sender.sendMessage(Component.text(
                        plugin.msg().get("givekey-fallen-gelassen-absender", droppedAmount, target.getName()),
                        NamedTextColor.YELLOW));
            }
            target.sendMessage(Component.text(
                    plugin.msg().get("givekey-fallen-gelassen-empfaenger", droppedAmount), NamedTextColor.YELLOW));
        }
    }
}
