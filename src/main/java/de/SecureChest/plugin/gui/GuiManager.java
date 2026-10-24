package de.SecureChest.plugin.gui;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.area.Area;
import de.SecureChest.plugin.area.AreaChangePreview;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaCoordinateParser;
import de.SecureChest.plugin.area.AreaValidationError;
import de.SecureChest.plugin.recipe.RecipeChangeResult;
import de.SecureChest.plugin.recipe.RecipeKind;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class GuiManager {

    private final MiscScreen  misc;
    private final LockScreen  locks;
    private final AdminScreen admin;
    private final AreaScreen  areas;
    private final RecipeScreen recipes;

    public GuiManager(ChestSecurePlugin plugin) {
        GuiHelper helper = new GuiHelper(plugin);
        this.misc  = new MiscScreen(plugin, helper, this);
        this.locks = new LockScreen(plugin, helper, this);
        this.admin = new AdminScreen(plugin, helper, this);
        this.areas = new AreaScreen(plugin, helper, this);
        this.recipes = new RecipeScreen(plugin, helper, this, Material::isItem);
    }

    public void openMain(Player player)                                 { misc.openMain(player); }
    public void openHelp(Player player)                                 { misc.openHelp(player); }
    public void openSettings(Player player)                             { misc.openSettings(player); }
    public void openStats(Player player)                                { misc.openStats(player); }
    public void openHoppersList(Player player, int page)                { misc.openHoppersList(player, page); }
    public void openRecipePreview(Player player)                        { misc.openRecipePreview(player); }
    public void openConfirm(Player player, String title, List<String> lines) { misc.openConfirm(player, title, lines); }

    public void openLocksList(Player player, int page)                  { locks.openLocksList(player, page); }
    public void openLockManage(Player player, String lockId)            { locks.openLockManage(player, lockId); }
    public void openTrustNearby(Player player, String lockId)           { locks.openTrustNearby(player, lockId); }
    public void openTrustRole(Player player, String lockId, UUID target){ locks.openTrustRole(player, lockId, target); }

    public void openAdmin(Player player)                                { admin.openAdmin(player); }
    public void openLogs(Player player, int page)                       { admin.openLogs(player, page); }
    public void promptChestKeyAmount(Player player)                     { admin.promptChestKeyAmount(player); }

    public void openAreas(Player player, int page)                      { areas.openAreas(player, page); }
    public void openAreaChangeConfirm(Player player, AreaChangePreview preview) { areas.openChangeConfirm(player, preview); }
    public void promptAreaName(Player player)                           { areas.promptName(player); }
    public void promptAreaCreateCoordinates(Player player, String name) { areas.promptCreateCoordinates(player, name); }
    public void promptAreaResizeCoordinates(Player player, Area area)   { areas.promptResizeCoordinates(player, area); }
    public void sendAreaErrors(Player player, List<AreaValidationError> errors) { areas.sendValidationErrors(player, errors); }
    public void sendAreaCoordinateError(Player player, AreaCoordinateParser.Rejected rejected) { areas.sendCoordinateError(player, rejected); }
    public void sendAreaResult(Player player, AreaChangeResult result)  { areas.sendResult(player, result); }

    public void openRecipes(Player player)                              { recipes.openRecipes(player); }
    public void openRecipeEditor(Player player, RecipeKind kind)        { recipes.openEditor(player, kind); }
    public void refreshRecipeEditor(Player player)                      { recipes.refreshEditor(player); }
    public void openRecipeResetConfirm(Player player, RecipeKind kind)  { recipes.openResetConfirm(player, kind); }
    public void sendRecipeSaveResult(Player player, RecipeChangeResult result)  { recipes.sendSaveResult(player, result); }
    public void sendRecipeResetResult(Player player, RecipeChangeResult result) { recipes.sendResetResult(player, result); }
}
