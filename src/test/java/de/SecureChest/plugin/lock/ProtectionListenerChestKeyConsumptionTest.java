package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.FakePlayerInventory;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.settings.ChestKeyConsumptionSetting;
import de.SecureChest.plugin.settings.SettingsStore;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmType;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.ui.UndoManager;
import de.SecureChest.plugin.util.Items;
import de.SecureChest.plugin.util.Worlds;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Chest-Key-Verbrauch bei neuen Sperren und Secure Hoppern")
class ProtectionListenerChestKeyConsumptionTest {

    private static final NamespacedKey SECURE_HOPPER_MARK = new NamespacedKey("chestsecure", "secure_hopper_block");

    @TempDir
    Path settingsFolder;

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private MessageService messages;
    private Items items;
    private ConfirmManager confirms;
    private ChestKeyConsumptionSetting consumption;
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private ProtectionListener listener;
    private Player alice;
    private FakePlayerInventory aliceInventory;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        messages = fixture.plugin.msg();
        items = mock(Items.class);
        when(fixture.plugin.items()).thenReturn(items);
        when(fixture.plugin.ui()).thenReturn(new UiStateStore());
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.plugin.gui()).thenReturn(mock(GuiManager.class));
        confirms = new ConfirmManager();
        when(fixture.plugin.confirms()).thenReturn(confirms);
        when(fixture.plugin.keySecureHopperBlock()).thenReturn(SECURE_HOPPER_MARK);
        consumption = mock(ChestKeyConsumptionSetting.class);
        when(consumption.isEnabled()).thenReturn(true);
        when(fixture.plugin.chestKeyConsumption()).thenReturn(consumption);
        listener = new ProtectionListener(fixture.plugin, clock::get);

        alice = fixture.player("Alice", false);
        when(alice.getGameMode()).thenReturn(GameMode.SURVIVAL);
        aliceInventory = new FakePlayerInventory(alice);
        aliceInventory.answerChestKeyChecks(items, alice);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private PlayerInteractEvent rightClick(Player player, Block block) {
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private BlockPlaceEvent place(Player player, Block block) {
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        return event;
    }

    private void holdChestKeyInHand(Player player, int amount, boolean sneaking) {
        aliceInventory.putChestKeys(0, amount);
        when(items.isHoldingChestKey(player)).thenReturn(true);
        when(player.isSneaking()).thenReturn(sneaking);
    }

    private void enableQuickLock(Player player) {
        service.setQuickLock(player.getUniqueId(), true);
    }

    private Block barrel(int x, int y, int z) {
        return world.setBlock(x, y, z, Material.BARREL);
    }

    private Block hopper(int x, int y, int z, BlockFace facing) {
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(facing);
        return world.setBlock(x, y, z, Material.HOPPER, hopperData);
    }

    private void enableAutoLock(boolean requireKey) {
        when(fixture.config.autoLockEnabled()).thenReturn(true);
        when(fixture.config.autoLockRequireKey()).thenReturn(requireKey);
    }

    @Test
    @DisplayName("Schleichen, Verbrauch an: Sperre gesetzt, genau ein Key aus der Hand")
    void sneak_on() {
        holdChestKeyInHand(alice, 3, true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
        verify(messages).get("block-gesperrt");
    }

    @Test
    @DisplayName("Schleichen, Verbrauch aus: Sperre gesetzt, kein Key verbraucht")
    void sneak_off() {
        when(consumption.isEnabled()).thenReturn(false);
        holdChestKeyInHand(alice, 3, true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(3, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Quick Lock, Verbrauch an: leere Hand, der Key kommt aus dem Inventar")
    void quickLock_on() {
        enableQuickLock(alice);
        aliceInventory.putChestKeys(15, 1);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertNull(aliceInventory.item(15));
    }

    @Test
    @DisplayName("Quick Lock, Verbrauch aus: kein Key verbraucht")
    void quickLock_off() {
        when(consumption.isEnabled()).thenReturn(false);
        enableQuickLock(alice);
        aliceInventory.putChestKeys(15, 1);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(1, aliceInventory.amount(15));
    }

    @Test
    @DisplayName("Quick Lock ohne Key im Inventar greift wie bisher nicht, der Behaelter oeffnet sich normal")
    void quickLock_withoutKeyUnchanged() {
        enableQuickLock(alice);
        Block barrel = barrel(0, 64, 0);
        PlayerInteractEvent event = rightClick(alice, barrel);

        listener.onPlayerInteract(event);

        assertFalse(service.isLocked(barrel));
        verify(event, never()).setCancelled(true);
        verify(messages, never()).get("sperren-key-erforderlich");
    }

    @Test
    @DisplayName("Mehrere Stapel: zwei Quick Locks verbrauchen nacheinander aus dem ersten und dem zweiten Stapel")
    void quickLock_severalStacks() {
        enableQuickLock(alice);
        aliceInventory.putChestKeys(9, 1);
        aliceInventory.putChestKeys(27, 4);

        listener.onPlayerInteract(rightClick(alice, barrel(0, 64, 0)));
        listener.onPlayerInteract(rightClick(alice, barrel(5, 64, 0)));

        assertNull(aliceInventory.item(9));
        assertEquals(3, aliceInventory.amount(27));
    }

    @Test
    @DisplayName("Nur echte Chest Keys: normale Haken bleiben liegen")
    void onlyRealChestKeys() {
        enableQuickLock(alice);
        aliceInventory.putPlainTripwireHooks(1, 64);
        aliceInventory.putChestKeys(2, 1);

        listener.onPlayerInteract(rightClick(alice, barrel(0, 64, 0)));

        assertEquals(64, aliceInventory.amount(1));
        assertNull(aliceInventory.item(2));
    }

    @Test
    @DisplayName("Secure Hopper per Schleichen, Verbrauch an: gebunden, ein Key verbraucht")
    void hopperSneak_on() {
        Block container = barrel(0, 64, 0);
        assertTrue(service.lock(alice, container));
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        holdChestKeyInHand(alice, 2, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        assertTrue(service.isSecureHopper(hopperBlock));
        assertEquals(1, aliceInventory.amount(0));
        verify(messages).get("hopper-gebunden");
    }

    @Test
    @DisplayName("Secure Hopper per Schleichen, Verbrauch aus: gebunden, kein Key verbraucht")
    void hopperSneak_off() {
        when(consumption.isEnabled()).thenReturn(false);
        Block container = barrel(0, 64, 0);
        assertTrue(service.lock(alice, container));
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        holdChestKeyInHand(alice, 2, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        assertTrue(service.isSecureHopper(hopperBlock));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Secure Hopper per Quick Lock, Verbrauch an und aus")
    void hopperQuickLock_onAndOff() {
        enableQuickLock(alice);
        aliceInventory.putChestKeys(20, 2);
        Block firstContainer = barrel(0, 64, 0);
        assertTrue(service.lock(alice, firstContainer));
        Block firstHopper = hopper(0, 65, 0, BlockFace.DOWN);

        listener.onPlayerInteract(rightClick(alice, firstHopper));
        assertTrue(service.isSecureHopper(firstHopper));
        assertEquals(1, aliceInventory.amount(20));

        when(consumption.isEnabled()).thenReturn(false);
        Block secondContainer = barrel(5, 64, 0);
        assertTrue(service.lock(alice, secondContainer));
        Block secondHopper = hopper(5, 65, 0, BlockFace.DOWN);

        listener.onPlayerInteract(rightClick(alice, secondHopper));
        assertTrue(service.isSecureHopper(secondHopper));
        assertEquals(1, aliceInventory.amount(20));
    }

    @Test
    @DisplayName("Secure Hopper am Limit: Bindung scheitert, der Key kommt zurueck")
    void hopperLimit_refund() {
        when(fixture.config.maxHoppersPerPlayer()).thenReturn(1);
        Block firstContainer = barrel(0, 64, 0);
        assertTrue(service.lock(alice, firstContainer));
        assertTrue(service.bindSecureHopper(alice, hopper(0, 65, 0, BlockFace.DOWN), firstContainer));
        Block secondContainer = barrel(5, 64, 0);
        assertTrue(service.lock(alice, secondContainer));
        Block secondHopper = hopper(5, 65, 0, BlockFace.DOWN);
        holdChestKeyInHand(alice, 1, true);

        listener.onPlayerInteract(rightClick(alice, secondHopper));

        assertFalse(service.isSecureHopper(secondHopper));
        assertEquals(1, aliceInventory.amount(0));
        verify(messages).get("hopper-bind-fehlgeschlagen");
    }

    @Test
    @DisplayName("Auto-Lock mit require_key, Verbrauch an: Sperre gesetzt, ein Key verbraucht")
    void autoLockRequireKey_on() {
        enableAutoLock(true);
        aliceInventory.putChestKeys(5, 2);
        Block barrel = barrel(0, 64, 0);

        listener.onBlockPlaceAutoLock(place(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(1, aliceInventory.amount(5));
        verify(messages).get("automatisch-gesperrt");
    }

    @Test
    @DisplayName("Auto-Lock mit require_key, Verbrauch aus: kein Key verbraucht")
    void autoLockRequireKey_off() {
        when(consumption.isEnabled()).thenReturn(false);
        enableAutoLock(true);
        aliceInventory.putChestKeys(5, 2);
        Block barrel = barrel(0, 64, 0);

        listener.onBlockPlaceAutoLock(place(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(5));
    }

    @Test
    @DisplayName("Auto-Lock ohne require_key: verbraucht nie einen Key, auch bei Verbrauch an")
    void autoLockWithoutRequireKey_neverConsumes() {
        enableAutoLock(false);
        aliceInventory.putChestKeys(5, 2);
        Block barrel = barrel(0, 64, 0);

        listener.onBlockPlaceAutoLock(place(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(5));
    }

    @Test
    @DisplayName("Auto-Lock mit require_key ohne Key: wie bisher keine Sperre und keine Meldung")
    void autoLockRequireKey_withoutKeyUnchanged() {
        enableAutoLock(true);
        Block barrel = barrel(0, 64, 0);

        listener.onBlockPlaceAutoLock(place(alice, barrel));

        assertFalse(service.isLocked(barrel));
        verify(alice, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    @Test
    @DisplayName("Auto-Lock am Limit: keine Sperre, der Key kommt zurueck")
    void autoLockLimit_refund() {
        when(fixture.config.maxLocksPerPlayer()).thenReturn(1);
        assertTrue(service.lock(alice, barrel(10, 64, 0)));
        enableAutoLock(true);
        aliceInventory.putChestKeys(5, 2);
        Block barrel = barrel(0, 64, 0);

        listener.onBlockPlaceAutoLock(place(alice, barrel));

        assertFalse(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(5));
    }

    @Test
    @DisplayName("Kreativmodus: Schleichen, Quick Lock und Auto-Lock verbrauchen nichts")
    void creative_noConsumption() {
        when(alice.getGameMode()).thenReturn(GameMode.CREATIVE);
        holdChestKeyInHand(alice, 3, true);
        Block sneakBarrel = barrel(0, 64, 0);
        listener.onPlayerInteract(rightClick(alice, sneakBarrel));

        when(items.isHoldingChestKey(alice)).thenReturn(false);
        enableQuickLock(alice);
        Block quickLockBarrel = barrel(5, 64, 0);
        listener.onPlayerInteract(rightClick(alice, quickLockBarrel));

        enableAutoLock(true);
        Block autoLockBarrel = barrel(10, 64, 0);
        listener.onBlockPlaceAutoLock(place(alice, autoLockBarrel));

        assertTrue(service.isLocked(sneakBarrel));
        assertTrue(service.isLocked(quickLockBarrel));
        assertTrue(service.isLocked(autoLockBarrel));
        assertEquals(3, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Admin ohne Kreativmodus verbraucht wie ein normaler Spieler")
    void admin_likePlayer() {
        Player admin = fixture.player("Admin", true);
        when(admin.getGameMode()).thenReturn(GameMode.SURVIVAL);
        FakePlayerInventory adminInventory = new FakePlayerInventory(admin);
        adminInventory.putChestKeys(0, 2);
        when(items.is(nullable(ItemStack.class), eq(Items.Type.CHEST_KEY))).thenAnswer(invocation ->
                adminInventory.isChestKey(invocation.getArgument(0)));
        when(items.isHoldingChestKey(admin)).thenReturn(true);
        when(admin.isSneaking()).thenReturn(true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(admin, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(1, adminInventory.amount(0));
    }

    @Test
    @DisplayName("Limit max_locks_per_player: Sperre scheitert, kein Key verbraucht")
    void limit_noConsumption() {
        when(fixture.config.maxLocksPerPlayer()).thenReturn(1);
        assertTrue(service.lock(alice, barrel(10, 64, 0)));
        holdChestKeyInHand(alice, 2, true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertFalse(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
        verify(messages).get("limit-locks-erreicht", 1);
    }

    @Test
    @DisplayName("Wirkungsbereich: Meldung, keine Sperre, kein Key verbraucht")
    void outsideArea_noConsumption() {
        assertTrue(fixture.areas.apply(new AreaChange.Create("spawn", "world", 100, 100, 110, 110)).applied());
        assertTrue(fixture.areas.apply(new AreaChange.Enable()).applied());
        holdChestKeyInHand(alice, 2, true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertFalse(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
        verify(messages).get("wirkungsbereich-hier-nicht-aktiv");
    }

    @Test
    @DisplayName("Welt nicht aktiv: keine Sperre, kein Key verbraucht")
    void inactiveWorld_noConsumption() {
        Worlds worlds = mock(Worlds.class);
        when(worlds.isEnabled(any())).thenReturn(false);
        when(fixture.plugin.worlds()).thenReturn(worlds);
        holdChestKeyInHand(alice, 2, true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertFalse(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Entsperren mit Schleichen kostet nichts und gibt nichts zurueck")
    void unlock_noConsumptionNoRefund() {
        Block barrel = barrel(0, 64, 0);
        assertTrue(service.lock(alice, barrel));
        holdChestKeyInHand(alice, 2, true);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertFalse(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Verwalten (Menue ohne Schleichen) kostet nichts")
    void manage_noConsumption() {
        Block barrel = barrel(0, 64, 0);
        assertTrue(service.lock(alice, barrel));
        holdChestKeyInHand(alice, 2, false);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Secure Hopper entbinden kostet nichts")
    void unbind_noConsumption() {
        Block container = barrel(0, 64, 0);
        assertTrue(service.lock(alice, container));
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        assertTrue(service.bindSecureHopper(alice, hopperBlock, container));
        holdChestKeyInHand(alice, 2, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        assertFalse(service.isSecureHopper(hopperBlock));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Altes Secure-Hopper-Item platzieren ist keine Chest-Key-Aktion und kostet nichts")
    void legacySecureHopperItem_noConsumption() {
        Block container = barrel(0, 64, 0);
        assertTrue(service.lock(alice, container));
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        aliceInventory.putChestKeys(3, 2);
        BlockPlaceEvent event = place(alice, hopperBlock);
        ItemStack legacyItem = mock(ItemStack.class);
        when(event.getItemInHand()).thenReturn(legacyItem);
        when(items.is(legacyItem, Items.Type.SECURE_HOPPER)).thenReturn(true);

        listener.onBlockPlace(event);

        assertTrue(service.isSecureHopper(hopperBlock));
        assertEquals(2, aliceInventory.amount(3));
    }

    @Test
    @DisplayName("Dialog: der Klick ohne Schleichen oeffnet den Dialog und verbraucht noch nichts")
    void dialog_noConsumptionBeforeConfirmation() {
        when(fixture.config.confirmTimeoutSeconds()).thenReturn(60);
        holdChestKeyInHand(alice, 2, false);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertEquals(ConfirmType.LOCK_BLOCK, confirms.get(alice.getUniqueId()).orElseThrow().type());
        assertFalse(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Zwei schnelle Klicks: der zweite faellt in den Cooldown, nur ein Key verbraucht")
    void twoClicks_cooldownNoDoubleConsumption() {
        when(fixture.config.lockActionCooldownMs()).thenReturn(1000L);
        holdChestKeyInHand(alice, 3, true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));
        clock.addAndGet(100L);
        listener.onPlayerInteract(rightClick(alice, barrel));

        assertTrue(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Zwei Klicks ohne Cooldown: Sperren und Entsperren, insgesamt ein Key")
    void twoClicks_withoutCooldownOneKey() {
        holdChestKeyInHand(alice, 3, true);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));
        listener.onPlayerInteract(rightClick(alice, barrel));

        assertFalse(service.isLocked(barrel));
        assertEquals(2, aliceInventory.amount(0));
    }

    @Test
    @DisplayName("Fehlender Key beim Verbrauch (Key nicht mehr im Inventar): Meldung, keine Sperre")
    void missingKeyAtConsumption() {
        when(items.isHoldingChestKey(alice)).thenReturn(true);
        when(alice.isSneaking()).thenReturn(true);
        aliceInventory.putPlainTripwireHooks(0, 1);
        Block barrel = barrel(0, 64, 0);

        listener.onPlayerInteract(rightClick(alice, barrel));

        assertFalse(service.isLocked(barrel));
        verify(messages).get("sperren-key-erforderlich");
        verify(messages, never()).get("block-gesperrt");
    }

    @Test
    @DisplayName("Umschalten wirkt sofort: echte Einstellung, an verbraucht, danach aus verbraucht nicht")
    void toggleAppliesImmediately() {
        ChestKeyConsumptionSetting setting = new ChestKeyConsumptionSetting(
                new SettingsStore(settingsFolder.resolve(SettingsStore.FILE_NAME), mock(Logger.class)),
                () -> false, mock(Logger.class));
        when(fixture.plugin.chestKeyConsumption()).thenReturn(setting);
        holdChestKeyInHand(alice, 5, true);

        listener.onPlayerInteract(rightClick(alice, barrel(0, 64, 0)));
        assertEquals(5, aliceInventory.amount(0));

        setting.set(true);
        listener.onPlayerInteract(rightClick(alice, barrel(5, 64, 0)));
        assertEquals(4, aliceInventory.amount(0));

        setting.set(false);
        listener.onPlayerInteract(rightClick(alice, barrel(10, 64, 0)));
        assertEquals(4, aliceInventory.amount(0));
    }
}
