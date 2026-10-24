package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.gui.GuiManager;
import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.ui.ConfirmManager;
import de.SecureChest.plugin.ui.ConfirmRequest;
import de.SecureChest.plugin.ui.ConfirmType;
import de.SecureChest.plugin.ui.UiStateStore;
import de.SecureChest.plugin.util.Items;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Secure Hopper per Chest Key")
class ProtectionListenerChestKeyHopperTest {

    private static final NamespacedKey SECURE_HOPPER_MARK = new NamespacedKey("chestsecure", "secure_hopper_block");
    private static final List<String> NEW_LANGUAGE_KEYS = List.of(
            "hopper-entbunden",
            "confirm-bind-hopper-title",
            "confirm-bind-hopper-question",
            "confirm-bind-hopper-target"
    );

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private ProtectionListener listener;
    private MessageService messages;
    private Items items;
    private GuiManager gui;
    private ConfirmManager confirms;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        messages = fixture.plugin.msg();
        items = mock(Items.class);
        when(fixture.plugin.items()).thenReturn(items);
        when(fixture.plugin.ui()).thenReturn(new UiStateStore());
        gui = mock(GuiManager.class);
        when(fixture.plugin.gui()).thenReturn(gui);
        confirms = new ConfirmManager();
        when(fixture.plugin.confirms()).thenReturn(confirms);
        when(fixture.plugin.keySecureHopperBlock()).thenReturn(SECURE_HOPPER_MARK);
        listener = new ProtectionListener(fixture.plugin);
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
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

    private Block hopper(int x, int y, int z, BlockFace facing) {
        Hopper hopperData = mock(Hopper.class);
        when(hopperData.getFacing()).thenReturn(facing);
        return world.setBlock(x, y, z, Material.HOPPER, hopperData);
    }

    private Block barrel(int x, int y, int z, Player lockOwner) {
        Block barrel = world.setBlock(x, y, z, Material.BARREL);
        if (lockOwner != null) assertTrue(service.lock(lockOwner, barrel));
        return barrel;
    }

    private PersistentDataContainer tileStateOf(Block block) {
        org.bukkit.block.Hopper hopperState = mock(org.bukkit.block.Hopper.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(hopperState.getPersistentDataContainer()).thenReturn(container);
        when(block.getState()).thenReturn(hopperState);
        return container;
    }

    private void holdChestKey(Player player, boolean sneaking) {
        when(items.isHoldingChestKey(player)).thenReturn(true);
        when(player.isSneaking()).thenReturn(sneaking);
    }

    private void enableQuickLock(Player player) {
        service.setQuickLock(player.getUniqueId(), true);
        when(items.hasChestKeyInInventory(player)).thenReturn(true);
    }

    private void assertBoundTo(Block hopperBlock, Block container, Player owner) {
        SecureHopperEntry entry = service.getSecureHopper(hopperBlock).orElseThrow();
        assertEquals(owner.getUniqueId(), entry.owner());
        assertEquals(service.getLock(container).orElseThrow().key().asId(), entry.boundLock().asId());
        assertFalse(service.isLocked(hopperBlock), "Trichter darf nicht zusaetzlich als Container gesperrt sein");
    }

    private void assertNormalHopper(Block hopperBlock) {
        assertFalse(service.isSecureHopper(hopperBlock));
        assertTrue(service.getAllCachedHoppers().isEmpty());
    }

    private void bindViaService(Player owner, Block hopperBlock, Block container) {
        assertTrue(service.bindSecureHopper(owner, hopperBlock, container));
    }

    private Runnable capturedMainThreadTask() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler).runTask(eq(fixture.plugin), taskCaptor.capture());
        return taskCaptor.getValue();
    }

    @Test
    @DisplayName("Entscheidung: Trichter zeigt auf eigenen gesperrten Behaelter, Ziel wird geliefert")
    void entscheidung_eigenerBehaelter() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);

        assertSame(container, LockUtil.chestKeyHopperBindTarget(service, hopperBlock, alice.getUniqueId()));
    }

    @Test
    @DisplayName("Entscheidung: Trichter zeigt seitlich auf die zweite Haelfte einer eigenen Doppelkiste")
    void entscheidung_doppelkiste() {
        Block canonicalHalf = world.setChest(0, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block otherHalf = world.setChest(1, 64, 0, Material.CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertTrue(service.lock(alice, canonicalHalf));
        Block hopperBlock = hopper(2, 64, 0, BlockFace.WEST);

        assertSame(otherHalf, LockUtil.chestKeyHopperBindTarget(service, hopperBlock, alice.getUniqueId()));
    }

    @Test
    @DisplayName("Entscheidung: kein Ziel bei ungesperrtem, fremdem oder fehlendem Behaelter")
    void entscheidung_zielUngeeignet() {
        barrel(0, 64, 0, null);
        barrel(5, 64, 0, bob);
        world.setBlock(10, 64, 0, Material.STONE);

        assertNull(LockUtil.chestKeyHopperBindTarget(service, hopper(0, 65, 0, BlockFace.DOWN), alice.getUniqueId()));
        assertNull(LockUtil.chestKeyHopperBindTarget(service, hopper(5, 65, 0, BlockFace.DOWN), alice.getUniqueId()));
        assertNull(LockUtil.chestKeyHopperBindTarget(service, hopper(10, 65, 0, BlockFace.DOWN), alice.getUniqueId()));
    }

    @Test
    @DisplayName("Entscheidung: kein Ziel, wenn der Trichter schon gesperrt oder schon Secure Hopper ist")
    void entscheidung_trichterSchonGeschuetzt() {
        barrel(0, 64, 0, alice);
        Block lockedHopper = hopper(0, 65, 0, BlockFace.DOWN);
        assertTrue(service.lock(alice, lockedHopper));
        Block secondContainer = barrel(5, 64, 0, alice);
        Block secureHopper = hopper(5, 65, 0, BlockFace.DOWN);
        bindViaService(alice, secureHopper, secondContainer);

        assertNull(LockUtil.chestKeyHopperBindTarget(service, lockedHopper, alice.getUniqueId()));
        assertNull(LockUtil.chestKeyHopperBindTarget(service, secureHopper, alice.getUniqueId()));
    }

    @Test
    @DisplayName("Entscheidung: kein Ziel fuer andere Bloecke oder Trichter ohne Trichterdaten")
    void entscheidung_keinTrichter() {
        barrel(0, 64, 0, alice);
        Block otherBlock = barrel(0, 65, 0, null);
        barrel(5, 64, 0, alice);
        Block hopperWithoutData = world.setBlock(5, 65, 0, Material.HOPPER);

        assertNull(LockUtil.chestKeyHopperBindTarget(service, otherBlock, alice.getUniqueId()));
        assertNull(LockUtil.chestKeyHopperBindTarget(service, hopperWithoutData, alice.getUniqueId()));
        assertNull(LockUtil.chestKeyHopperBindTarget(service, null, alice.getUniqueId()));
    }

    @Test
    @DisplayName("Schleichen: Trichter wird Secure Hopper, Eintrag und Markierung gesetzt, keine Containersperre")
    void schleichen_wandeltUm() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        PersistentDataContainer mark = tileStateOf(hopperBlock);
        holdChestKey(alice, true);
        PlayerInteractEvent event = rightClick(alice, hopperBlock);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("hopper-gebunden");
        verify(messages, never()).get("block-gesperrt");
        verify(mark).set(SECURE_HOPPER_MARK, PersistentDataType.BYTE, (byte) 1);
        assertBoundTo(hopperBlock, container, alice);
    }

    @Test
    @DisplayName("Quick Lock mit leerer Hand: Trichter wird Secure Hopper, keine Containersperre")
    void quickLock_wandeltUm() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        PersistentDataContainer mark = tileStateOf(hopperBlock);
        enableQuickLock(alice);
        PlayerInteractEvent event = rightClick(alice, hopperBlock);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("hopper-gebunden");
        verify(mark).set(SECURE_HOPPER_MARK, PersistentDataType.BYTE, (byte) 1);
        assertBoundTo(hopperBlock, container, alice);
    }

    @Test
    @DisplayName("Chest Key bei aktivem Quick Lock ohne Schleichen: Trichter wird Secure Hopper ohne Dialog")
    void chestKeyMitQuickLock_wandeltUm() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        enableQuickLock(alice);
        holdChestKey(alice, false);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages).get("hopper-gebunden");
        assertTrue(confirms.get(alice.getUniqueId()).isEmpty());
        assertBoundTo(hopperBlock, container, alice);
    }

    @Test
    @DisplayName("Dialog: Bindetexte mit den Koordinaten des Behaelters, Anfrage LOCK_BLOCK fuer den Trichter")
    void dialog_bindetexte() {
        when(fixture.config.confirmTimeoutSeconds()).thenReturn(60);
        barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        holdChestKey(alice, false);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));
        capturedMainThreadTask().run();

        ConfirmRequest request = confirms.get(alice.getUniqueId()).orElseThrow();
        assertEquals(ConfirmType.LOCK_BLOCK, request.type());
        assertEquals(LockKey.fromLocation(hopperBlock.getLocation()).asId(), request.data());
        verify(messages).get("confirm-bind-hopper-title");
        verify(messages).get("confirm-bind-hopper-question");
        verify(messages).get("confirm-bind-hopper-target", 0, 64, 0);
        verify(messages, never()).get("confirm-lock-block-title");
        verify(gui).openConfirm(eq(alice), any(), anyList());
        assertNormalHopper(hopperBlock);
        assertFalse(service.isLocked(hopperBlock));
    }

    @Test
    @DisplayName("Dialog: Trichter ueber ungesperrtem Behaelter bekommt die bisherigen Sperrtexte")
    void dialog_sperrtexte() {
        barrel(0, 64, 0, null);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        holdChestKey(alice, false);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));
        capturedMainThreadTask().run();

        verify(messages).get("confirm-lock-block-title");
        verify(messages).get("confirm-lock-block-question", "HOPPER");
        verify(messages, never()).get("confirm-bind-hopper-title");
    }

    @Test
    @DisplayName("Ziel ungesperrt: Trichter wird wie bisher als Container gesperrt")
    void zielUngesperrt_sperrtTrichter() {
        barrel(0, 64, 0, null);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        holdChestKey(alice, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages).get("block-gesperrt");
        assertTrue(service.isLocked(hopperBlock));
        assertNormalHopper(hopperBlock);
    }

    @Test
    @DisplayName("Ziel fremd gesperrt: Trichter wird wie bisher als Container gesperrt")
    void zielFremd_sperrtTrichter() {
        barrel(0, 64, 0, bob);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        holdChestKey(alice, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages).get("block-gesperrt");
        assertEquals(alice.getUniqueId(), service.getLock(hopperBlock).orElseThrow().owner());
        assertNormalHopper(hopperBlock);
    }

    @Test
    @DisplayName("Ziel kein Behaelter: Trichter wird wie bisher als Container gesperrt")
    void zielKeinBehaelter_sperrtTrichter() {
        world.setBlock(0, 64, 0, Material.STONE);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        enableQuickLock(alice);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages).get("block-gesperrt");
        assertTrue(service.isLocked(hopperBlock));
        assertNormalHopper(hopperBlock);
    }

    @Test
    @DisplayName("Trichter schon gesperrt: keine Umwandlung, Sperre bleibt, Verwaltung wie bisher")
    void trichterGesperrt_keineUmwandlung() {
        barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        assertTrue(service.lock(alice, hopperBlock));
        holdChestKey(alice, false);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages, never()).get("hopper-gebunden");
        assertTrue(service.isLocked(hopperBlock));
        assertNormalHopper(hopperBlock);
        assertNotNull(capturedMainThreadTask());
    }

    @Test
    @DisplayName("Trichter schon Secure Hopper, ohne Schleichen: Meldung 'bereits geschuetzt' wie bisher")
    void trichterSchonSecureHopper_ohneSchleichen() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        bindViaService(alice, hopperBlock, container);
        holdChestKey(alice, false);
        PlayerInteractEvent event = rightClick(alice, hopperBlock);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("hopper-bereits-geschuetzt");
        verify(messages, never()).get("hopper-entbunden");
        assertBoundTo(hopperBlock, container, alice);
    }

    @Test
    @DisplayName("Trichter schon Secure Hopper, Quick Lock mit leerer Hand: nichts aendert sich")
    void trichterSchonSecureHopper_quickLock() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        bindViaService(alice, hopperBlock, container);
        enableQuickLock(alice);
        PlayerInteractEvent event = rightClick(alice, hopperBlock);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertBoundTo(hopperBlock, container, alice);
        assertEquals(1, service.getAllCachedHoppers().size());
    }

    @Test
    @DisplayName("Limit max_hoppers_per_player: keine Umwandlung, Limit-Meldung, keine Containersperre")
    void limit_verhindertUmwandlung() {
        when(fixture.config.maxHoppersPerPlayer()).thenReturn(1);
        Block firstContainer = barrel(0, 64, 0, alice);
        bindViaService(alice, hopper(0, 65, 0, BlockFace.DOWN), firstContainer);
        barrel(5, 64, 0, alice);
        Block secondHopper = hopper(5, 65, 0, BlockFace.DOWN);
        holdChestKey(alice, true);

        listener.onPlayerInteract(rightClick(alice, secondHopper));

        verify(messages).get("limit-hopper-erreicht", 1);
        verify(messages).get("hopper-bind-fehlgeschlagen");
        verify(messages, never()).get("hopper-gebunden");
        assertFalse(service.isSecureHopper(secondHopper));
        assertFalse(service.isLocked(secondHopper));
        assertEquals(1, service.getAllCachedHoppers().size());
    }

    @Test
    @DisplayName("Wirkungsbereich: Trichter ausserhalb, Schleichen bringt die Meldung, nichts wird gebunden oder gesperrt")
    void wirkungsbereich_trichterAusserhalb() {
        assertTrue(fixture.areas.apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10)).applied());
        assertTrue(fixture.areas.apply(new AreaChange.Enable()).applied());
        barrel(10, 64, 5, alice);
        Block hopperBlock = hopper(11, 64, 5, BlockFace.WEST);
        holdChestKey(alice, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages).get("wirkungsbereich-hier-nicht-aktiv");
        verify(messages, never()).get("hopper-gebunden");
        assertNormalHopper(hopperBlock);
        assertFalse(service.isLocked(hopperBlock));
    }

    @Test
    @DisplayName("Wirkungsbereich: Trichter ausserhalb, Quick Lock greift nicht")
    void wirkungsbereich_quickLockAusserhalb() {
        assertTrue(fixture.areas.apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10)).applied());
        assertTrue(fixture.areas.apply(new AreaChange.Enable()).applied());
        barrel(10, 64, 5, alice);
        Block hopperBlock = hopper(11, 64, 5, BlockFace.WEST);
        enableQuickLock(alice);
        PlayerInteractEvent event = rightClick(alice, hopperBlock);

        listener.onPlayerInteract(event);

        verify(event, never()).setCancelled(anyBoolean());
        assertNormalHopper(hopperBlock);
        assertFalse(service.isLocked(hopperBlock));
    }

    @Test
    @DisplayName("Wirkungsbereich: Ziel ausserhalb, Meldung statt 'Binden fehlgeschlagen', nichts gesperrt")
    void wirkungsbereich_zielAusserhalb() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        AreaService areas = spy(fixture.areas);
        doReturn(false).when(areas).isLockPlacementAllowed(container);
        when(fixture.plugin.areas()).thenReturn(areas);
        holdChestKey(alice, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages).get("wirkungsbereich-hier-nicht-aktiv");
        verify(messages, never()).get("hopper-bind-fehlgeschlagen");
        assertNormalHopper(hopperBlock);
        assertFalse(service.isLocked(hopperBlock));
    }

    @Test
    @DisplayName("Status entfernen: Besitzer mit Schleichen, Eintrag und Markierung weg, Log, kein Containerschutz")
    void entbinden_besitzer() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        PersistentDataContainer mark = tileStateOf(hopperBlock);
        bindViaService(alice, hopperBlock, container);
        SqlLockService spiedService = spy(service);
        when(fixture.plugin.locks()).thenReturn(spiedService);
        holdChestKey(alice, true);
        PlayerInteractEvent event = rightClick(alice, hopperBlock);

        listener.onPlayerInteract(event);

        verify(event).setCancelled(true);
        verify(messages).get("hopper-entbunden");
        verify(messages, never()).get("hopper-bereits-geschuetzt");
        verify(spiedService).logSecureHopperRemove(alice, hopperBlock);
        verify(spiedService).removeSecureHopper(hopperBlock);
        verify(mark).remove(SECURE_HOPPER_MARK);
        assertNormalHopper(hopperBlock);
        assertFalse(service.isLocked(hopperBlock));
    }

    @Test
    @DisplayName("Status entfernen: anderer Spieler mit Schleichen kann es nicht, Meldung wie bisher")
    void entbinden_andererSpieler() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        bindViaService(alice, hopperBlock, container);
        holdChestKey(bob, true);

        listener.onPlayerInteract(rightClick(bob, hopperBlock));

        verify(messages).get("hopper-bereits-geschuetzt");
        verify(messages, never()).get("hopper-entbunden");
        assertBoundTo(hopperBlock, container, alice);
    }

    @Test
    @DisplayName("Status entfernen: Admin, der nicht Besitzer ist, nutzt diesen Weg nicht")
    void entbinden_adminNichtBesitzer() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        bindViaService(alice, hopperBlock, container);
        Player admin = fixture.player("Admin", true);
        holdChestKey(admin, true);

        listener.onPlayerInteract(rightClick(admin, hopperBlock));

        verify(messages).get("hopper-bereits-geschuetzt");
        verify(messages, never()).get("hopper-entbunden");
        assertBoundTo(hopperBlock, container, alice);
    }

    @Test
    @DisplayName("Schleichen wechselt: erster Klick wandelt um, zweiter Klick macht wieder einen normalen Trichter")
    void schleichen_wechselt() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        holdChestKey(alice, true);

        listener.onPlayerInteract(rightClick(alice, hopperBlock));
        assertBoundTo(hopperBlock, container, alice);
        listener.onPlayerInteract(rightClick(alice, hopperBlock));

        verify(messages).get("hopper-gebunden");
        verify(messages).get("hopper-entbunden");
        assertNormalHopper(hopperBlock);
        assertFalse(service.isLocked(hopperBlock));
    }

    @Test
    @DisplayName("Abbauen: es faellt ein normaler Trichter statt des Secure-Hopper-Items, Eintrag entfernt")
    void abbauen_normalerTrichter() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        bindViaService(alice, hopperBlock, container);
        BlockBreakEvent event = mock(BlockBreakEvent.class);
        when(event.getBlock()).thenReturn(hopperBlock);
        when(event.getPlayer()).thenReturn(alice);
        ItemStack delegate = mock(ItemStack.class);
        when(delegate.getType()).thenReturn(Material.HOPPER);

        try (MockedStatic<ItemStack> itemStacks = mockStatic(ItemStack.class)) {
            itemStacks.when(() -> ItemStack.of(Material.HOPPER, 1)).thenReturn(delegate);
            listener.onBlockBreak(event);
        }

        ArgumentCaptor<ItemStack> dropCaptor = ArgumentCaptor.forClass(ItemStack.class);
        verify(world.world()).dropItemNaturally(any(Location.class), dropCaptor.capture());
        assertEquals(Material.HOPPER, dropCaptor.getValue().getType());
        verify(event).setDropItems(false);
        verify(event, never()).setCancelled(true);
        verify(messages).get("hopper-entfernt");
        assertNormalHopper(hopperBlock);
    }

    @Test
    @DisplayName("Altes Secure-Hopper-Item: Platzieren bindet weiterhin")
    void altesItem_bindetBeimPlatzieren() {
        Block container = barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(hopperBlock);
        when(event.getPlayer()).thenReturn(alice);
        when(event.getItemInHand()).thenReturn(mock(ItemStack.class));
        when(items.is(any(), eq(Items.Type.SECURE_HOPPER))).thenReturn(true);

        listener.onBlockPlace(event);

        verify(event, never()).setCancelled(true);
        verify(messages).get("hopper-gebunden");
        assertBoundTo(hopperBlock, container, alice);
    }

    @Test
    @DisplayName("Auto-Lock bleibt unveraendert: normaler Trichter am eigenen Behaelter wird als Container gesperrt")
    void autoLock_unveraendert() {
        when(fixture.config.autoLockEnabled()).thenReturn(true);
        barrel(0, 64, 0, alice);
        Block hopperBlock = hopper(0, 65, 0, BlockFace.DOWN);
        BlockPlaceEvent event = mock(BlockPlaceEvent.class);
        when(event.getBlockPlaced()).thenReturn(hopperBlock);
        when(event.getPlayer()).thenReturn(alice);

        listener.onBlockPlaceAutoLock(event);

        verify(messages).get("automatisch-gesperrt");
        assertTrue(service.isLocked(hopperBlock));
        assertNormalHopper(hopperBlock);
    }

    @Test
    @DisplayName("Alle neuen Sprachschluessel existieren in de.yml und en.yml, nicht leer, gleiche Platzhalter")
    void sprachschluessel_inBeidenDateien() {
        Path languageFolder = Path.of("src", "main", "resources", "lang");
        YamlConfiguration german = YamlConfiguration.loadConfiguration(languageFolder.resolve("de.yml").toFile());
        YamlConfiguration english = YamlConfiguration.loadConfiguration(languageFolder.resolve("en.yml").toFile());

        for (String key : NEW_LANGUAGE_KEYS) {
            String germanValue = german.getString(key);
            String englishValue = english.getString(key);
            assertNotNull(germanValue, "de.yml: " + key);
            assertNotNull(englishValue, "en.yml: " + key);
            assertFalse(germanValue.isBlank(), "de.yml leer: " + key);
            assertFalse(englishValue.isBlank(), "en.yml leer: " + key);
        }
        for (String placeholder : List.of("{0}", "{1}", "{2}")) {
            assertTrue(german.getString("confirm-bind-hopper-target").contains(placeholder));
            assertTrue(english.getString("confirm-bind-hopper-target").contains(placeholder));
        }
        assertFalse(german.getString("confirm-bind-hopper-target").contains("{3}"));
        assertFalse(english.getString("confirm-bind-hopper-target").contains("{3}"));
    }
}
