package de.SecureChest.plugin.settings;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

public final class ChestKeyConsumptionSetting {

    public record ChangeResult(boolean enabled, String saveError) {

        public boolean isSaved() {
            return saveError == null;
        }
    }

    private final SettingsStore store;
    private final BooleanSupplier configValue;
    private final Logger logger;
    private volatile Boolean override;

    public ChestKeyConsumptionSetting(SettingsStore store, BooleanSupplier configValue, Logger logger) {
        this.store = store;
        this.configValue = configValue;
        this.logger = logger;
        this.override = store.current().chestKeyConsumption();
    }

    public boolean isEnabled() {
        return OwnerNameDisplaySetting.effectiveValue(override, configValue.getAsBoolean());
    }

    public Boolean override() {
        return override;
    }

    public ChangeResult set(boolean enabled) {
        override = enabled;
        logger.info("Chest Key consumption turned " + (enabled ? "on" : "off") + " for new locks.");
        String saveError = null;
        try {
            SettingsStore.Contents current = store.current();
            store.save(new SettingsStore.Contents(current.ownerNameDisplay(), current.ownerNameHideWhenCovered(),
                    enabled));
        } catch (IOException ex) {
            saveError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            logger.warning("Saving " + SettingsStore.FILE_NAME + " failed: " + saveError
                    + " The change stays active until the next restart.");
        }
        return new ChangeResult(enabled, saveError);
    }
}
