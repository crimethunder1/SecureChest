package de.SecureChest.plugin.settings;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

public final class OwnerNameDisplaySetting {

    public record ChangeResult(boolean enabled, String saveError) {

        public boolean isSaved() {
            return saveError == null;
        }
    }

    private final SettingsStore store;
    private final BooleanSupplier configValue;
    private final Logger logger;
    private volatile Boolean override;

    public OwnerNameDisplaySetting(SettingsStore store, BooleanSupplier configValue, Logger logger) {
        this.store = store;
        this.configValue = configValue;
        this.logger = logger;
        this.override = store.load().ownerNameDisplay();
    }

    public static boolean effectiveValue(Boolean override, boolean configValue) {
        return override != null ? override : configValue;
    }

    public boolean isEnabled() {
        return effectiveValue(override, configValue.getAsBoolean());
    }

    public Boolean override() {
        return override;
    }

    public ChangeResult set(boolean enabled) {
        override = enabled;
        logger.info("Owner name display turned " + (enabled ? "on" : "off") + " for all locks.");

        String saveError = null;
        try {
            store.save(new SettingsStore.Contents(enabled));
        } catch (IOException ex) {
            saveError = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            logger.warning("Saving " + SettingsStore.FILE_NAME + " failed: " + saveError
                    + " The change stays active until the next restart.");
        }
        return new ChangeResult(enabled, saveError);
    }
}
