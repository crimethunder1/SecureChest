package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.listener.ProtectionListener;
import de.SecureChest.plugin.ui.UndoManager;
import io.papermc.paper.event.entity.ItemTransportingEntityValidateTargetEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Kupfergolems an gesperrten Behaeltern")
class ProtectionListenerCopperGolemTest {

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private ProtectionListener listener;
    private Player alice;
    private CopperGolem takingGolem;
    private CopperGolem droppingGolem;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.config.undoSeconds()).thenReturn(60);
        listener = new ProtectionListener(fixture.plugin);
        alice = fixture.player("Alice", false);
        takingGolem = golem(CopperGolem.State.GETTING_ITEM);
        droppingGolem = golem(CopperGolem.State.DROPPING_ITEM);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private static CopperGolem golem(CopperGolem.State state) {
        CopperGolem golem = mock(CopperGolem.class);
        when(golem.getGolemState()).thenReturn(state);
        return golem;
    }

    private Block container(int x, int y, int z, Material type) {
        if (LockUtil.isChestFamily(type)) {
            return world.setChest(x, y, z, type, BlockFace.NORTH, Chest.Type.SINGLE);
        }
        return world.setBlock(x, y, z, type);
    }

    private Block lockedContainer(int x, int y, int z, Material type) {
        Block block = container(x, y, z, type);
        assertTrue(service.lock(alice, block), "Sperre fuer " + type);
        return block;
    }

    private Block lockedCopperDoubleChestLeftHalf(Material type) {
        Block original = world.setChest(0, 64, 0, type, BlockFace.NORTH, Chest.Type.SINGLE);
        assertTrue(service.lock(alice, original));
        world.setChest(0, 64, 0, type, BlockFace.NORTH, Chest.Type.RIGHT);
        return world.setChest(-1, 64, 0, type, BlockFace.NORTH, Chest.Type.LEFT);
    }

    private ItemTransportingEntityValidateTargetEvent validate(Entity entity, Block block, boolean allowed) {
        ItemTransportingEntityValidateTargetEvent event = mock(ItemTransportingEntityValidateTargetEvent.class);
        when(event.getEntity()).thenReturn(entity);
        when(event.getBlock()).thenReturn(block);
        when(event.isAllowed()).thenReturn(allowed);
        listener.onItemTransportingEntityValidateTarget(event);
        return event;
    }

    private void assertRejected(Block block) {
        ItemTransportingEntityValidateTargetEvent asSource = validate(takingGolem, block, true);
        ItemTransportingEntityValidateTargetEvent asTarget = validate(droppingGolem, block, true);
        verify(asSource).setAllowed(false);
        verify(asSource, never()).setAllowed(true);
        verify(asTarget).setAllowed(false);
        verify(asTarget, never()).setAllowed(true);
    }

    private void assertUntouched(Block block) {
        verify(validate(takingGolem, block, true), never()).setAllowed(anyBoolean());
        verify(validate(droppingGolem, block, true), never()).setAllowed(anyBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Gesperrte Kupferkiste: als Quelle und als Ziel abgelehnt")
    void lockedCopperChestIsRejected(Material type) {
        assertRejected(lockedContainer(0, 64, 0, type));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"CHEST", "TRAPPED_CHEST", "BARREL"})
    @DisplayName("Gesperrte Kiste, Redstone-Kiste und Fass: als Quelle und als Ziel abgelehnt")
    void lockedChestAndBarrelAreRejected(Material type) {
        assertRejected(lockedContainer(0, 64, 0, type));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST",
            "WAXED_COPPER_CHEST", "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST",
            "WAXED_OXIDIZED_COPPER_CHEST"})
    @DisplayName("Gesperrte Doppelkiste aus Kupferkisten, Sperre unter der rechten Haelfte: von beiden Haelften abgelehnt")
    void lockedCopperDoubleChestIsRejectedFromBothHalves(Material type) {
        Block leftHalf = lockedCopperDoubleChestLeftHalf(type);
        Block rightHalf = world.blockAt(0, 64, 0);

        assertRejected(leftHalf);
        assertRejected(rightHalf);
    }

    @Test
    @DisplayName("Doppelkiste aus Kupferkisten verschiedener Stufe, ueber die linke Haelfte gesperrt: von beiden Haelften abgelehnt")
    void lockedMixedCopperDoubleChestIsRejectedFromBothHalves() {
        Block leftHalf = world.setChest(-1, 64, 0, Material.EXPOSED_COPPER_CHEST, BlockFace.NORTH, Chest.Type.LEFT);
        Block rightHalf = world.setChest(0, 64, 0, Material.WAXED_OXIDIZED_COPPER_CHEST, BlockFace.NORTH, Chest.Type.RIGHT);
        assertTrue(service.lock(alice, leftHalf));

        assertRejected(leftHalf);
        assertRejected(rightHalf);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {
            "COPPER_CHEST", "WAXED_OXIDIZED_COPPER_CHEST", "CHEST", "TRAPPED_CHEST", "BARREL"})
    @DisplayName("Ungesperrte Kupferkiste, Kiste und Fass: nicht angefasst")
    void unlockedContainersAreUntouched(Material type) {
        lockedContainer(5, 64, 0, Material.COPPER_CHEST);

        assertUntouched(container(0, 64, 0, type));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"STONE", "AIR", "COPPER_BLOCK"})
    @DisplayName("Block ohne Sperrbarkeit: nicht angefasst")
    void nonLockableBlocksAreUntouched(Material type) {
        assertUntouched(world.setBlock(0, 64, 0, type));
    }

    @Test
    @DisplayName("Sperre im Cache, Block inzwischen nicht mehr sperrbar: nicht angefasst")
    void lockOnNonLockableBlockIsUntouched() {
        lockedContainer(0, 64, 0, Material.COPPER_CHEST);
        Block replaced = world.setBlock(0, 64, 0, Material.STONE);

        assertUntouched(replaced);
    }

    @Test
    @DisplayName("Welt nicht aktiviert: gesperrte Kupferkiste nicht angefasst")
    void disabledWorldIsUntouched() {
        Block block = lockedContainer(0, 64, 0, Material.COPPER_CHEST);
        when(fixture.plugin.worlds().isEnabled(any())).thenReturn(false);

        assertUntouched(block);
    }

    @Test
    @DisplayName("Bereits abgelehntes Ziel wird nie wieder erlaubt und nicht angefasst, gesperrt wie ungesperrt")
    void alreadyRejectedTargetStaysRejected() {
        Block locked = lockedContainer(0, 64, 0, Material.COPPER_CHEST);
        Block unlocked = container(5, 64, 0, Material.COPPER_CHEST);

        ItemTransportingEntityValidateTargetEvent lockedEvent = validate(takingGolem, locked, false);
        ItemTransportingEntityValidateTargetEvent unlockedEvent = validate(droppingGolem, unlocked, false);

        verify(lockedEvent, never()).setAllowed(anyBoolean());
        verify(unlockedEvent, never()).setAllowed(anyBoolean());
    }

    @Test
    @DisplayName("Nach dem Entsperren ist die Kupferkiste wieder frei")
    void unlockedAgainIsFree() {
        Block block = lockedContainer(0, 64, 0, Material.COPPER_CHEST);
        assertRejected(block);

        assertTrue(service.unlockByIdWithUndo(alice, service.getLock(block).orElseThrow().key().asId()));

        assertFalse(service.isLocked(block));
        assertUntouched(block);
    }

    @Test
    @DisplayName("Nach dem Entsperren ist die Doppelkiste aus Kupferkisten von beiden Haelften wieder frei")
    void unlockedDoubleChestIsFree() {
        Block leftHalf = lockedCopperDoubleChestLeftHalf(Material.WEATHERED_COPPER_CHEST);
        Block rightHalf = world.blockAt(0, 64, 0);

        assertTrue(service.unlockByIdWithUndo(alice, service.getLock(leftHalf).orElseThrow().key().asId()));

        assertUntouched(leftHalf);
        assertUntouched(rightHalf);
    }

    @Test
    @DisplayName("Viele Ereignisse hintereinander: jedes abgelehnt, ohne Datenbank und ohne geplante Tasks")
    void repeatedEventsUseOnlyTheCache() throws SQLException {
        Block block = lockedContainer(0, 64, 0, Material.COPPER_CHEST);
        clearInvocations(fixture.database, fixture.scheduler);

        for (int round = 0; round < 100; round++) {
            verify(validate(takingGolem, block, true), times(1)).setAllowed(false);
        }

        verify(fixture.database, never()).getConnection();
        verifyNoInteractions(fixture.scheduler);
    }

    @Test
    @DisplayName("Der Handler laeuft mit Prioritaet HIGHEST und ignoreCancelled")
    void handlerPriority() throws NoSuchMethodException {
        Method handler = ProtectionListener.class.getMethod(
                "onItemTransportingEntityValidateTarget", ItemTransportingEntityValidateTargetEvent.class);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);

        assertNotNull(annotation);
        assertEquals(EventPriority.HIGHEST, annotation.priority());
        assertTrue(annotation.ignoreCancelled());
    }
}
