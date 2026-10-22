package de.SecureChest.plugin.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ChestSecureHolder implements InventoryHolder {

    public enum GuiId {
        MAIN,
        HELP,
        SETTINGS,
        LOCKS_LIST,
        LOCK_MANAGE,
        TRUST_NEARBY,
        TRUST_ROLE,
        HOPPERS_LIST,
        RECIPE_KEY,
        RECIPE_HOPPER,
        ADMIN,
        LOGS_LIST,
        CONFIRM,
        STATS,
        AREAS,
        RECIPES,
        RECIPE_EDIT
    }

    private final GuiId id;
    private final String context;
    private Inventory inventory;

    public ChestSecureHolder(GuiId id, String context) {
        this.id = id;
        this.context = context;
    }

    public GuiId id() {
        return id;
    }

    public String context() {
        return context;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
