package de.SecureChest.plugin.listener;

import de.SecureChest.plugin.ChestSecurePlugin;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.gui.ScreenActionRecorder;
import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Bestaetigung LOCK_BLOCK fuer Trichter")
class GuiListenerLockBlockConfirmationTest {

    private final UUID playerUuid = UUID.randomUUID();
    private final UUID worldUuid = UUID.randomUUID();
    private MockedStatic<Bukkit> bukkit;
    private World world;
    private LockService locks;
    private AreaService areas;
    private ConfirmManager confirms;
    private Player player;
    private Block hopperBlock;
    private Block container;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldUuid);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(worldUuid)).thenReturn(world);

        ChestSecurePlugin plugin = mock(ChestSecurePlugin.class);
        MessageService messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        areas = mock(AreaService.class);
        when(areas.isLockPlacementAllowed(any())).thenReturn(true);
        when(plugin.areas()).thenReturn(areas);
        when(plugin.gui()).thenReturn(mock(GuiManager.class));
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        when(plugin.chestKeyConsumption()).thenReturn(mock(ChestKeyConsumptionSetting.class));

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);

        container = block(0, 64, 0, Material.BARREL);
        hopperBlock = block(0, 65, 0, Material.HOPPER);
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(BlockFace.DOWN);
        when(hopperBlock.getBlockData()).thenReturn(hopperData);
        when(hopperBlock.getRelative(BlockFace.DOWN)).thenReturn(container);
        when(world.getBlockAt(0, 65, 0)).thenReturn(hopperBlock);
        when(locks.getLock(container)).thenReturn(Optional.of(
                new LockEntry(new LockKey(worldUuid, 0, 64, 0), Material.BARREL, playerUuid, 0L)));

        listener = new GuiListener(plugin);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private Block block(int x, int y, int z, Material material) {
        Block block = mock(Block.class);
        when(block.getType()).thenReturn(material);
        when(block.getWorld()).thenReturn(world);
        when(block.getLocation()).thenReturn(new Location(world, x, y, z));
        return block;
    }

    private void storeHopperConfirmation() {
        confirms.set(playerUuid, new ConfirmRequest(ConfirmType.LOCK_BLOCK,
                new LockKey(worldUuid, 0, 65, 0).asId(), "CLOSE", System.currentTimeMillis() + 60_000L));
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @Test
    @DisplayName("Ja: Trichter ueber eigenem gesperrtem Behaelter wird gebunden, nicht gesperrt")
    void ja_bindet() {
        when(locks.bindSecureHopper(player, hopperBlock, container)).thenReturn(true);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks).bindSecureHopper(player, hopperBlock, container);
        verify(locks, never()).lock(any(), any());
        assertEquals(List.of("hopper-gebunden", "gui-erledigt"), sentTexts());
    }

    @Test
    @DisplayName("Ja, aber der Trichter wurde inzwischen gesperrt: bisheriger Weg ueber lock, keine Bindung")
    void ja_inzwischenGesperrt() {
        when(locks.isLocked(hopperBlock)).thenReturn(true);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks, never()).bindSecureHopper(any(), any(), any());
        verify(locks).lock(player, hopperBlock);
    }

    @Test
    @DisplayName("Ja, aber der Trichter ist inzwischen Secure Hopper: keine zweite Bindung")
    void ja_inzwischenSecureHopper() {
        when(locks.isSecureHopper(hopperBlock)).thenReturn(true);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks, never()).bindSecureHopper(any(), any(), any());
    }

    @Test
    @DisplayName("Ja, aber der Behaelter ist inzwischen nicht mehr gesperrt: Trichter wird wie bisher gesperrt")
    void ja_behaelterInzwischenFrei() {
        when(locks.getLock(container)).thenReturn(Optional.empty());
        when(locks.lock(player, hopperBlock)).thenReturn(true);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks, never()).bindSecureHopper(any(), any(), any());
        verify(locks).lock(player, hopperBlock);
        assertEquals(List.of("gui-erledigt"), sentTexts());
    }

    @Test
    @DisplayName("Ja, Bindung scheitert (Limit, Datenbank): Meldung, keine Sperre")
    void ja_bindungScheitert() {
        when(locks.bindSecureHopper(player, hopperBlock, container)).thenReturn(false);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks, never()).lock(any(), any());
        assertEquals(List.of("hopper-bind-fehlgeschlagen", "gui-aktion-fehlgeschlagen"), sentTexts());
    }

    @Test
    @DisplayName("Ja, ausserhalb des Wirkungsbereichs: Meldung, weder Bindung noch Sperre")
    void ja_ausserhalb() {
        when(areas.isLockPlacementAllowed(container)).thenReturn(false);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks, never()).bindSecureHopper(any(), any(), any());
        verify(locks, never()).lock(any(), any());
        assertEquals(List.of("wirkungsbereich-hier-nicht-aktiv", "gui-aktion-fehlgeschlagen"), sentTexts());
    }

    @Test
    @DisplayName("Nein: nichts wird gebunden oder gesperrt")
    void nein_nichts() {
        storeHopperConfirmation();

        listener.handleConfirmAction(player, false);

        verify(locks, never()).bindSecureHopper(any(), any(), any());
        verify(locks, never()).lock(any(), any());
    }
}
