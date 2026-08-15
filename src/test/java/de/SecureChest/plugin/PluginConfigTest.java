package de.SecureChest.plugin;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginConfig – Defaults und Werte-Laden")
class PluginConfigTest {

    private static PluginConfig from(String yaml) {
        return new PluginConfig(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static PluginConfig defaults() {
        return from("");
    }

    @Test
    @DisplayName("sounds.enabled ist standardmäßig true")
    void default_sounds() {
        assertTrue(defaults().soundsEnabled());
    }

    @Test
    @DisplayName("particles.enabled ist standardmäßig true")
    void default_particles() {
        assertTrue(defaults().particlesEnabled());
    }

    @Test
    @DisplayName("signs.enabled ist standardmäßig true")
    void default_signs() {
        assertTrue(defaults().signsEnabled());
    }

    @Test
    @DisplayName("notifications.enabled ist standardmäßig true")
    void default_notifications() {
        assertTrue(defaults().notificationsEnabled());
    }

    @Test
    @DisplayName("notifications.max_per_player ist standardmäßig 20")
    void default_notificationsMax() {
        assertEquals(20, defaults().notificationsMaxPerPlayer());
    }

    @Test
    @DisplayName("ui.confirm_timeout_seconds ist standardmäßig 15")
    void default_confirmTimeout() {
        assertEquals(15, defaults().confirmTimeoutSeconds());
    }

    @Test
    @DisplayName("ui.undo_seconds ist standardmäßig 20")
    void default_undoSeconds() {
        assertEquals(20, defaults().undoSeconds());
    }

    @Test
    @DisplayName("ui.max_undo_history ist standardmäßig 5")
    void default_maxUndoHistory() {
        assertEquals(5, defaults().maxUndoHistory());
    }

    @Test
    @DisplayName("ui.trust_nearby_radius ist standardmäßig 32.0")
    void default_trustNearbyRadius() {
        assertEquals(32.0, defaults().trustNearbyRadius(), 0.001);
    }

    @Test
    @DisplayName("auto_lock.enabled ist standardmäßig false")
    void default_autoLock() {
        assertFalse(defaults().autoLockEnabled());
    }

    @Test
    @DisplayName("auto_lock.require_key ist standardmäßig true")
    void default_autoLockRequireKey() {
        assertTrue(defaults().autoLockRequireKey());
    }

    @Test
    @DisplayName("auto_lock.sneak_only ist standardmäßig false")
    void default_autoLockSneakOnly() {
        assertFalse(defaults().autoLockSneakOnly());
    }

    @Test
    @DisplayName("admin.logs.book_entries ist standardmäßig 200")
    void default_adminLogsBookEntries() {
        assertEquals(200, defaults().adminLogsBookEntries());
    }

    @Test
    @DisplayName("admin.logs.teleport_enabled ist standardmäßig false")
    void default_teleportEnabled() {
        assertFalse(defaults().adminLogsTeleportEnabled());
    }

    @Test
    @DisplayName("limits.max_locks_per_player ist standardmäßig 1000")
    void default_maxLocks() {
        assertEquals(1000, defaults().maxLocksPerPlayer());
    }

    @Test
    @DisplayName("limits.max_hoppers_per_player ist standardmäßig 50")
    void default_maxHoppers() {
        assertEquals(50, defaults().maxHoppersPerPlayer());
    }

    @Test
    @DisplayName("worlds.enabled ist standardmäßig eine leere Liste")
    void default_enabledWorlds() {
        assertNotNull(defaults().enabledWorlds());
        assertTrue(defaults().enabledWorlds().isEmpty());
    }

    @Test
    @DisplayName("sounds.enabled kann auf false gesetzt werden")
    void custom_soundsDisabled() {
        PluginConfig cfg = from("sounds:\n  enabled: false");
        assertFalse(cfg.soundsEnabled());
    }

    @Test
    @DisplayName("ui.confirm_timeout_seconds wird korrekt geladen")
    void custom_confirmTimeout() {
        PluginConfig cfg = from("ui:\n  confirm_timeout_seconds: 30");
        assertEquals(30, cfg.confirmTimeoutSeconds());
    }

    @Test
    @DisplayName("limits.max_locks_per_player wird korrekt geladen")
    void custom_maxLocks() {
        PluginConfig cfg = from("limits:\n  max_locks_per_player: 50");
        assertEquals(50, cfg.maxLocksPerPlayer());
    }

    @Test
    @DisplayName("worlds.enabled mit mehreren Welten wird als Liste geladen")
    void custom_worlds() {
        PluginConfig cfg = from(
                "worlds:\n  enabled:\n    - world\n    - world_nether\n    - world_the_end");
        assertEquals(3, cfg.enabledWorlds().size());
        assertTrue(cfg.enabledWorlds().contains("world"));
        assertTrue(cfg.enabledWorlds().contains("world_nether"));
    }

    @Test
    @DisplayName("ui.trust_nearby_radius als Dezimalzahl wird korrekt geladen")
    void custom_trustRadius() {
        PluginConfig cfg = from("ui:\n  trust_nearby_radius: 64.5");
        assertEquals(64.5, cfg.trustNearbyRadius(), 0.001);
    }

    @Test
    @DisplayName("Vollständige config.yml-Struktur lädt alle Werte korrekt")
    void custom_vollstaendig() {
        String yaml = String.join("\n",
                "sounds:",
                "  enabled: false",
                "particles:",
                "  enabled: false",
                "signs:",
                "  enabled: false",
                "notifications:",
                "  enabled: false",
                "  max_per_player: 5",
                "ui:",
                "  confirm_timeout_seconds: 10",
                "  undo_seconds: 30",
                "  max_undo_history: 10",
                "  trust_nearby_radius: 16.0",
                "auto_lock:",
                "  enabled: true",
                "  require_key: false",
                "  sneak_only: true",
                "admin:",
                "  logs:",
                "    book_entries: 100",
                "    teleport_enabled: true",
                "limits:",
                "  max_locks_per_player: 25",
                "  max_hoppers_per_player: 10"
        );
        PluginConfig cfg = from(yaml);

        assertFalse(cfg.soundsEnabled());
        assertFalse(cfg.particlesEnabled());
        assertFalse(cfg.signsEnabled());
        assertFalse(cfg.notificationsEnabled());
        assertEquals(5,    cfg.notificationsMaxPerPlayer());
        assertEquals(10,   cfg.confirmTimeoutSeconds());
        assertEquals(30,   cfg.undoSeconds());
        assertEquals(10,   cfg.maxUndoHistory());
        assertEquals(16.0, cfg.trustNearbyRadius(), 0.001);
        assertTrue(cfg.autoLockEnabled());
        assertFalse(cfg.autoLockRequireKey());
        assertTrue(cfg.autoLockSneakOnly());
        assertEquals(100,  cfg.adminLogsBookEntries());
        assertTrue(cfg.adminLogsTeleportEnabled());
        assertEquals(25,   cfg.maxLocksPerPlayer());
        assertEquals(10,   cfg.maxHoppersPerPlayer());
    }

    @Test
    @DisplayName("cooldowns.lock_action_ms ist standardmaessig 1000ms")
    void default_lockActionCooldown() {
        assertEquals(1000L, defaults().lockActionCooldownMs());
    }

    @Test
    @DisplayName("cooldowns.break_notification_ms ist standardmaessig 30000ms")
    void default_breakNotificationCooldown() {
        assertEquals(30000L, defaults().breakNotificationCooldownMs());
    }

    @Test
    @DisplayName("Cooldowns werden aus Config gelesen")
    void cooldowns_werden_gelesen() {
        PluginConfig cfg = from(
                "cooldowns:\n"
                        + "  lock_action_ms: 2500\n"
                        + "  break_notification_ms: 60000\n");
        assertEquals(2500L,  cfg.lockActionCooldownMs());
        assertEquals(60000L, cfg.breakNotificationCooldownMs());
    }
}
