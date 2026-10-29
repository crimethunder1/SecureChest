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
import de.SecureChest.plugin.util.Items;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GuiListener: Chest-Key-Verbrauch im Bestaetigungsdialog")
class GuiListenerChestKeyConsumptionTest {

    private final UUID playerUuid = UUID.randomUUID();
    private final UUID worldUuid = UUID.randomUUID();
    private MockedStatic<Bukkit> bukkit;
    private World world;
    private ChestSecurePlugin plugin;
    private MessageService messages;
    private GuiManager gui;
    private LockService locks;
    private AreaService areas;
    private ConfirmManager confirms;
    private ChestKeyConsumptionSetting consumption;
    private Player player;
    private FakePlayerInventory inventory;
    private Block barrel;
    private Block hopperBlock;
    private Block container;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldUuid);
        bukkit = org.mockito.Mockito.mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getWorld(worldUuid)).thenReturn(world);

        plugin = mock(ChestSecurePlugin.class);
        messages = ScreenActionRecorder.keyEchoingMessages();
        when(plugin.msg()).thenReturn(messages);
        gui = mock(GuiManager.class);
        when(plugin.gui()).thenReturn(gui);
        locks = mock(LockService.class);
        when(plugin.locks()).thenReturn(locks);
        areas = mock(AreaService.class);
        when(areas.isLockPlacementAllowed(any())).thenReturn(true);
        when(plugin.areas()).thenReturn(areas);
        confirms = new ConfirmManager();
        when(plugin.confirms()).thenReturn(confirms);
        consumption = mock(ChestKeyConsumptionSetting.class);
        when(consumption.isEnabled()).thenReturn(true);
        when(plugin.chestKeyConsumption()).thenReturn(consumption);
        Items items = mock(Items.class);
        when(plugin.items()).thenReturn(items);

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        inventory = new FakePlayerInventory(player);
        inventory.answerChestKeyChecks(items, player);

        barrel = block(5, 64, 5, Material.BARREL);
        when(world.getBlockAt(5, 64, 5)).thenReturn(barrel);
        container = block(0, 64, 0, Material.BARREL);
        hopperBlock = block(0, 65, 0, Material.HOPPER);
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(BlockFace.DOWN);
        when(hopperBlock.getBlockData()).thenReturn(hopperData);
        when(hopperBlock.getRelative(BlockFace.DOWN)).thenReturn(container);
        when(world.getBlockAt(0, 65, 0)).thenReturn(hopperBlock);
        when(locks.getLock(container)).thenReturn(Optional.of(
                new LockEntry(new LockKey(worldUuid, 0, 64, 0), Material.BARREL, playerUuid, 0L)));

        listener = new GuiListener(plugin, material -> true);
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

    private void storeConfirmation(ConfirmType type, String data) {
        confirms.set(playerUuid, new ConfirmRequest(type, data, "CLOSE", System.currentTimeMillis() + 60_000L));
    }

    private void storeLockConfirmation() {
        storeConfirmation(ConfirmType.LOCK_BLOCK, new LockKey(worldUuid, 5, 64, 5).asId());
    }

    private void storeHopperConfirmation() {
        storeConfirmation(ConfirmType.LOCK_BLOCK, new LockKey(worldUuid, 0, 65, 0).asId());
    }

    private List<String> sentTexts() {
        ArgumentCaptor<Component> captor = ArgumentCaptor.forClass(Component.class);
        verify(player, atLeastOnce()).sendMessage(captor.capture());
        List<String> texts = new ArrayList<>();
        for (Component component : captor.getAllValues()) texts.add(((TextComponent) component).content());
        return texts;
    }

    @Test
    @DisplayName("Dialog Sperre, Verbrauch an: nach Ja gesperrt und ein Key verbraucht")
    void lockDialog_on() {
        inventory.putChestKeys(0, 2);
        when(locks.lock(player, barrel)).thenReturn(true);
        storeLockConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks).lock(player, barrel);
        assertEquals(1, inventory.amount(0));
        assertEquals(List.of("gui-erledigt"), sentTexts());
    }

    @Test
    @DisplayName("Dialog Sperre, Verbrauch aus: gesperrt, das Inventar wird nicht angefasst")
    void lockDialog_off() {
        when(consumption.isEnabled()).thenReturn(false);
        when(locks.lock(player, barrel)).thenReturn(true);
        storeLockConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks).lock(player, barrel);
        verify(player, never()).getInventory();
    }

    @Test
    @DisplayName("Dialog Sperre, Key inzwischen weg: Meldung, keine Sperre")
    void lockDialog_missingKey() {
        inventory.putPlainTripwireHooks(0, 3);
        storeLockConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks, never()).lock(any(), any());
        assertEquals(List.of("sperren-key-erforderlich", "gui-aktion-fehlgeschlagen"), sentTexts());
        assertEquals(3, inventory.amount(0));
    }

    @Test
    @DisplayName("Dialog Sperre, Block inzwischen gesperrt (lock scheitert): der Key kommt zurueck")
    void lockDialog_failedRefund() {
        inventory.putChestKeys(0, 1);
        when(locks.lock(player, barrel)).thenReturn(false);
        storeLockConfirmation();

        listener.handleConfirmAction(player, true);

        assertEquals(1, inventory.amount(0));
        assertEquals(List.of("gui-aktion-fehlgeschlagen"), sentTexts());
    }

    @Test
    @DisplayName("Dialog Sperre im Kreativmodus: nichts verbraucht, auch ohne Key")
    void lockDialog_creative() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        when(locks.lock(player, barrel)).thenReturn(true);
        storeLockConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks).lock(player, barrel);
        verify(player, never()).getInventory();
    }

    @Test
    @DisplayName("Zweimal Ja: die Anfrage ist nach dem ersten Mal weg, nur ein Key verbraucht")
    void lockDialog_confirmTwice() {
        inventory.putChestKeys(0, 3);
        when(locks.lock(player, barrel)).thenReturn(true);
        storeLockConfirmation();

        listener.handleConfirmAction(player, true);
        listener.handleConfirmAction(player, true);

        verify(locks, times(1)).lock(player, barrel);
        assertEquals(2, inventory.amount(0));
        assertEquals(List.of("gui-erledigt", "gui-confirm-expired"), sentTexts());
    }

    @Test
    @DisplayName("Dialog Secure Hopper, Verbrauch an: gebunden und ein Key verbraucht")
    void hopperDialog_on() {
        inventory.putChestKeys(0, 2);
        when(locks.bindSecureHopper(player, hopperBlock, container)).thenReturn(true);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks).bindSecureHopper(player, hopperBlock, container);
        assertEquals(1, inventory.amount(0));
        assertEquals(List.of("hopper-gebunden", "gui-erledigt"), sentTexts());
    }

    @Test
    @DisplayName("Dialog Secure Hopper, Verbrauch aus: gebunden, das Inventar wird nicht angefasst")
    void hopperDialog_off() {
        when(consumption.isEnabled()).thenReturn(false);
        when(locks.bindSecureHopper(player, hopperBlock, container)).thenReturn(true);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks).bindSecureHopper(player, hopperBlock, container);
        verify(player, never()).getInventory();
    }

    @Test
    @DisplayName("Dialog Secure Hopper, Bindung scheitert: der Key kommt zurueck")
    void hopperDialog_failedRefund() {
        inventory.putChestKeys(0, 2);
        when(locks.bindSecureHopper(player, hopperBlock, container)).thenReturn(false);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        assertEquals(2, inventory.amount(0));
        assertEquals(List.of("hopper-bind-fehlgeschlagen", "gui-aktion-fehlgeschlagen"), sentTexts());
    }

    @Test
    @DisplayName("Dialog Secure Hopper ohne Key: Meldung, keine Bindung")
    void hopperDialog_missingKey() {
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        verify(locks, never()).bindSecureHopper(any(), any(), any());
        assertEquals(List.of("sperren-key-erforderlich", "gui-aktion-fehlgeschlagen"), sentTexts());
    }

    @Test
    @DisplayName("Dialog Secure Hopper ausserhalb des Wirkungsbereichs: kein Key verbraucht")
    void hopperDialog_outsideArea() {
        inventory.putChestKeys(0, 2);
        when(areas.isLockPlacementAllowed(container)).thenReturn(false);
        storeHopperConfirmation();

        listener.handleConfirmAction(player, true);

        assertEquals(2, inventory.amount(0));
        verify(locks, never()).bindSecureHopper(any(), any(), any());
    }

    @Test
    @DisplayName("Entsperren, Uebertragen und Vertrauen entfernen im Dialog kosten nie einen Key")
    void otherConfirmations_neverConsume() {
        String lockId = new LockKey(worldUuid, 5, 64, 5).asId();
        UUID target = UUID.randomUUID();

        storeConfirmation(ConfirmType.UNLOCK_ONE, lockId);
        listener.handleConfirmAction(player, true);
        storeConfirmation(ConfirmType.TRANSFER_LOCK, lockId + "|" + target);
        listener.handleConfirmAction(player, true);
        storeConfirmation(ConfirmType.REMOVE_TRUST, lockId + "|" + target);
        listener.handleConfirmAction(player, true);

        verify(locks).unlockByIdWithUndo(player, lockId);
        verify(locks).transferLock(player, lockId, target);
        verify(locks).removeTrustById(player, lockId, target);
        verify(player, never()).getInventory();
    }
}
