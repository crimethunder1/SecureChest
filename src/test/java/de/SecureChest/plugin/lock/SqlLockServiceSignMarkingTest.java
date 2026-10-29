package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.sign.SignManager;
import de.SecureChest.plugin.ui.UndoManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SqlLockService: jede Aenderung an Sperren und Secure Hoppern markiert die Namensgruppe")
class SqlLockServiceSignMarkingTest {

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private SignManager signs;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        signs = fixture.plugin.signs();
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.config.undoSeconds()).thenReturn(60);
        alice = player("Alice");
        bob = player("Bob");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Player player(String name) {
        Player player = fixture.player(name, false);
        Block standingOn = world.blockAt(0, 100, 0);
        Location location = mock(Location.class);
        when(location.getBlock()).thenReturn(standingOn);
        when(player.getLocation()).thenReturn(location);
        return player;
    }

    private Block lockedBarrel(Player owner, int x, int z) {
        Block block = world.setBlock(x, 64, z, Material.BARREL);
        assertTrue(service.lock(owner, block));
        return block;
    }

    private Block secureHopper(Player owner, Block container) {
        Block hopper = world.setBlock(container.getX(), 65, container.getZ(), Material.HOPPER);
        assertTrue(service.bindSecureHopper(owner, hopper, container));
        return hopper;
    }

    private LockKey keyOf(Block block) {
        return world.keyAt(block.getX(), block.getY(), block.getZ());
    }

    private void runPendingAsyncTasks() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.scheduler, atLeastOnce()).runTaskAsynchronously(eq(fixture.plugin), taskCaptor.capture());
        clearInvocations(fixture.scheduler);
        for (Runnable task : taskCaptor.getAllValues()) task.run();
    }

    private void runSchedulerImmediately() {
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskAsynchronously(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        });
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        });
        fixture.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
    }

    @Test
    @DisplayName("Anlegen und Entsperren markieren die Sperre wie bisher ueber spawnSign und removeSign")
    void lockAndUnlock() {
        Block block = lockedBarrel(alice, 3, 3);
        verify(signs).spawnSign(eq(keyOf(block)), any());

        assertTrue(service.unlockByIdWithUndo(alice, keyOf(block).asId()));
        verify(signs).removeSign(keyOf(block).asId());
    }

    @Test
    @DisplayName("Secure Hopper binden markiert den Trichter, ein abgelehntes zweites Binden nicht")
    void bindSecureHopperMarks() {
        Block container = lockedBarrel(alice, 3, 3);
        Block hopper = secureHopper(alice, container);

        verify(signs).markChanged(keyOf(hopper));
        assertFalse(service.bindSecureHopper(alice, hopper, container));
        verify(signs, times(1)).markChanged(keyOf(hopper));
    }

    @Test
    @DisplayName("Fehlgeschlagenes Speichern beim Binden nimmt den Secure Hopper zurueck und markiert erneut")
    void bindSecureHopperRollbackMarks() {
        Block container = lockedBarrel(alice, 3, 3);
        clearInvocations(fixture.scheduler);
        Block hopper = secureHopper(alice, container);

        runPendingAsyncTasks();

        assertTrue(service.getCachedHoppersInChunk(world.world().getUID(), 0, 0).isEmpty());
        verify(signs, times(2)).markChanged(keyOf(hopper));
    }

    @Test
    @DisplayName("Secure Hopper entfernen markiert den Trichter")
    void removeSecureHopperMarks() {
        Block hopper = secureHopper(alice, lockedBarrel(alice, 3, 3));

        assertTrue(service.removeSecureHopper(hopper));

        verify(signs, times(2)).markChanged(keyOf(hopper));
    }

    @Test
    @DisplayName("forceRemoveHopperAt markiert den Trichter, ein unbekannter Schluessel nichts")
    void forceRemoveHopperMarks() {
        Block hopper = secureHopper(alice, lockedBarrel(alice, 3, 3));

        service.forceRemoveHopperAt(keyOf(hopper));
        service.forceRemoveHopperAt(world.keyAt(9, 65, 9));

        verify(signs, times(2)).markChanged(keyOf(hopper));
        verify(signs, never()).markChanged(world.keyAt(9, 65, 9));
    }

    @Test
    @DisplayName("Uebertragen markiert die Sperre und jeden entfernten fremden Secure Hopper")
    void transferMarksLockAndForeignHoppers() {
        Block container = lockedBarrel(alice, 3, 3);
        Block hopper = secureHopper(alice, container);

        assertTrue(service.transferLock(alice, keyOf(container).asId(), bob.getUniqueId()));

        verify(signs).removeSign(keyOf(container).asId());
        verify(signs, times(2)).spawnSign(eq(keyOf(container)), any());
        verify(signs, times(2)).markChanged(keyOf(hopper));
    }

    @Test
    @DisplayName("Entfernen ausserhalb des Wirkungsbereichs markiert Sperren und Secure Hopper, das Wiederherstellen erneut")
    void areaRemovalAndRestoreMark() {
        Block container = lockedBarrel(alice, 3, 3);
        Block hopper = secureHopper(alice, container);
        List<LockEntry> outsideLocks = List.copyOf(service.getAllCachedLocks());
        List<SecureHopperEntry> outsideHoppers = List.copyOf(service.getAllCachedHoppers());
        runSchedulerImmediately();

        service.removeOutsideArea(outsideLocks, outsideHoppers);

        verify(signs).removeSign(keyOf(container).asId());
        verify(signs, times(2)).spawnSign(eq(keyOf(container)), any());
        verify(signs, times(3)).markChanged(keyOf(hopper));
        assertEquals(1, service.getAllCachedHoppers().size());
    }

    @Test
    @DisplayName("Globales Entsperren markiert jede Sperre, Rueckgaengig markiert Sperren und Secure Hopper")
    void unlockAllAndRestoreMark() {
        Block first = lockedBarrel(alice, 3, 3);
        Block second = lockedBarrel(alice, 4, 3);
        Block hopper = secureHopper(alice, first);

        assertTrue(service.unlockAllWithUndo(alice, alice.getUniqueId()));

        verify(signs).removeSign(keyOf(first).asId());
        verify(signs).removeSign(keyOf(second).asId());

        assertTrue(service.restoreUndo(alice));

        verify(signs, times(2)).spawnSign(eq(keyOf(first)), any());
        verify(signs, times(2)).spawnSign(eq(keyOf(second)), any());
        verify(signs, times(2)).markChanged(keyOf(hopper));
    }

    @Test
    @DisplayName("Fehlgeschlagenes Speichern beim Rueckgaengig markiert die zurueckgenommenen Secure Hopper")
    void restoreUndoRollbackMarks() {
        Block first = lockedBarrel(alice, 3, 3);
        Block hopper = secureHopper(alice, first);
        assertTrue(service.unlockAllWithUndo(alice, alice.getUniqueId()));
        runSchedulerImmediately();

        assertTrue(service.restoreUndo(alice));

        assertTrue(service.getAllCachedHoppers().isEmpty());
        verify(signs, times(3)).markChanged(keyOf(hopper));
        verify(signs, times(2)).removeSign(keyOf(first).asId());
    }
}
