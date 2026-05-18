package de.SecureChest.plugin.util;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.PluginConfig;
import org.bukkit.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Worlds – reload() und isEnabled (SEC-05)")
class WorldsReloadTest {

    private World mockWorld(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private ChestSecurePlugin mockPlugin(List<String> enabledWorlds) {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(cfg);
        when(cfg.enabledWorlds()).thenReturn(enabledWorlds);
        return plugin;
    }

    @Test
    @DisplayName("Konstruktor laedt die Welt-Liste aus der Config")
    void konstruktor_laedtKonfiguration() {
        ChestSecurePlugin plugin = mockPlugin(List.of("world", "world_nether"));
        Worlds worlds = new Worlds(plugin);

        assertTrue(worlds.isEnabled(mockWorld("world")));
        assertTrue(worlds.isEnabled(mockWorld("world_nether")));
        assertFalse(worlds.isEnabled(mockWorld("creative")));
    }

    @Test
    @DisplayName("Leere Konfig-Liste = alle Welten erlaubt (Default-Verhalten)")
    void leereListe_alleWeltenErlaubt() {
        ChestSecurePlugin plugin = mockPlugin(List.of());
        Worlds worlds = new Worlds(plugin);

        assertTrue(worlds.isEnabled(mockWorld("beliebig")));
        assertTrue(worlds.isEnabled(mockWorld("world")));
    }

    @Test
    @DisplayName("null als Config-Liste wird wie leere Liste behandelt")
    void nullKonfig_alleWeltenErlaubt() {
        ChestSecurePlugin plugin = mockPlugin(null);
        Worlds worlds = new Worlds(plugin);

        assertTrue(worlds.isEnabled(mockWorld("beliebig")));
    }

    @Test
    @DisplayName("isEnabled(null) gibt false zurueck")
    void nullWorld_istNichtAktiv() {
        ChestSecurePlugin plugin = mockPlugin(List.of("world"));
        Worlds worlds = new Worlds(plugin);

        assertFalse(worlds.isEnabled(null));
    }

    @Test
    @DisplayName("reload() uebernimmt aenderungen in der Konfig")
    void reload_uebernimmtNeueKonfig() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(cfg);

        when(cfg.enabledWorlds()).thenReturn(List.of("world"));
        Worlds worlds = new Worlds(plugin);
        assertFalse(worlds.isEnabled(mockWorld("nether")));

        when(cfg.enabledWorlds()).thenReturn(List.of("world", "nether"));
        worlds.reload();
        assertTrue(worlds.isEnabled(mockWorld("nether")));
        assertTrue(worlds.isEnabled(mockWorld("world")));
    }

    @Test
    @DisplayName("reload() kann Welten auch wieder entfernen")
    void reload_kannWeltenEntfernen() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(cfg);

        when(cfg.enabledWorlds()).thenReturn(List.of("world", "nether"));
        Worlds worlds = new Worlds(plugin);
        assertTrue(worlds.isEnabled(mockWorld("nether")));

        when(cfg.enabledWorlds()).thenReturn(List.of("world"));
        worlds.reload();
        assertFalse(worlds.isEnabled(mockWorld("nether")));
    }

    @Test
    @DisplayName("Leere und null-Eintraege in der Liste werden ignoriert")
    void reload_ignoriertLeereUndNullEintraege() {
        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        PluginConfig cfg = mock(PluginConfig.class);
        when(plugin.cfg()).thenReturn(cfg);

        java.util.List<String> mitLeerEintragen = new java.util.ArrayList<>();
        mitLeerEintragen.add("world");
        mitLeerEintragen.add("");
        mitLeerEintragen.add(null);
        mitLeerEintragen.add("  ");
        when(cfg.enabledWorlds()).thenReturn(mitLeerEintragen);

        Worlds worlds = new Worlds(plugin);
        assertTrue(worlds.isEnabled(mockWorld("world")));
        assertFalse(worlds.isEnabled(mockWorld("")));
    }

    @Test
    @DisplayName("Weltnamen werden bei der Aufnahme getrimmt")
    void reload_trimmtWeltnamen() {
        ChestSecurePlugin plugin = mockPlugin(List.of("  world  ", " nether"));
        Worlds worlds = new Worlds(plugin);

        assertTrue(worlds.isEnabled(mockWorld("world")));
        assertTrue(worlds.isEnabled(mockWorld("nether")));
    }
}
