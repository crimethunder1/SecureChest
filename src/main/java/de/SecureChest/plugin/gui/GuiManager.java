package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class GuiManager {

    private final MiscScreen  misc;
    private final LockScreen  locks;
    private final AdminScreen admin;

    public GuiManager(ChestSecurePlugin plugin) {
        GuiHelper helper = new GuiHelper(plugin);
        this.misc  = new MiscScreen(plugin, helper, this);
        this.locks = new LockScreen(plugin, helper, this);
        this.admin = new AdminScreen(plugin, helper, this);
    }

    public void openMain(Player player)                                 { misc.openMain(player); }
    public void openHelp(Player player)                                 { misc.openHelp(player); }
    public void openSettings(Player player)                             { misc.openSettings(player); }
    public void openStats(Player player)                                { misc.openStats(player); }
    public void openHoppersList(Player player, int page)                { misc.openHoppersList(player, page); }
    public void openRecipePreview(Player player, boolean showKeyRecipe) { misc.openRecipePreview(player, showKeyRecipe); }
    public void openConfirm(Player player, String title, List<String> lines) { misc.openConfirm(player, title, lines); }

    public void openLocksList(Player player, int page)                  { locks.openLocksList(player, page); }
    public void openLockManage(Player player, String lockId)            { locks.openLockManage(player, lockId); }
    public void openTrustNearby(Player player, String lockId)           { locks.openTrustNearby(player, lockId); }
    public void openTrustRole(Player player, String lockId, UUID target){ locks.openTrustRole(player, lockId, target); }

    public void openAdmin(Player player)                                { admin.openAdmin(player); }
    public void openLogs(Player player, int page)                       { admin.openLogs(player, page); }
}
