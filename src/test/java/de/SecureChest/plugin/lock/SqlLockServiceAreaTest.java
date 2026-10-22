package de.SecureChest.plugin.lock;

import de.SecureChest.plugin.area.AreaChange;
import de.SecureChest.plugin.area.AreaChangeResult;
import de.SecureChest.plugin.area.AreaService;
import de.SecureChest.plugin.area.AreaStore;
import de.SecureChest.plugin.area.RecordingAreaEnvironment;
import de.SecureChest.plugin.ui.UndoManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SqlLockService: Wirkungsbereich beim Anlegen und Massenentfernung")
class SqlLockServiceAreaTest {

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private AreaService areas;
    private Player alice;
    private Player bob;

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        areas = fixture.areas;
        alice = fixture.player("Alice", false);
        bob = fixture.player("Bob", false);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private void apply(AreaChange change) {
        assertTrue(areas.apply(change).applied(), "Aenderung sollte gelten: " + change);
    }

    private void enableWithSpawnArea() {
        apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        apply(new AreaChange.Enable());
    }

    private Block barrel(int x, int z) {
        return world.setBlock(x, 64, z, Material.BARREL);
    }

    private Set<LockKey> cachedLockKeys() {
        return service.getAllCachedLocks().stream().map(LockEntry::key).collect(Collectors.toSet());
    }

    private Set<LockKey> cachedHopperKeys() {
        return service.getAllCachedHoppers().stream().map(SecureHopperEntry::hopperKey).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Schalter aus: lock() funktioniert ueberall wie bisher")
    void schalterAus_lockUeberall() {
        apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));

        assertTrue(service.lock(alice, barrel(500, 500)));
    }

    @Test
    @DisplayName("Schalter an: lock() innerhalb erlaubt, ausserhalb abgelehnt ohne Zaehler-Aenderung")
    void schalterAn_lockInnenUndAussen() {
        enableWithSpawnArea();

        assertTrue(service.lock(alice, barrel(10, 10)));
        assertFalse(service.lock(alice, barrel(11, 10)));
        assertFalse(service.lock(alice, barrel(-1, 0)));

        assertEquals(Set.of(world.keyAt(10, 64, 10)), cachedLockKeys());
        when(fixture.config.maxLocksPerPlayer()).thenReturn(2);
        assertTrue(service.lock(alice, barrel(0, 0)));
        assertFalse(service.lock(alice, barrel(1, 1)));
    }

    @Test
    @DisplayName("Doppelkiste an der Arealgrenze: Anlegen nur, wenn beide Haelften innerhalb liegen")
    void doppelkisteAnDerGrenze() {
        enableWithSpawnArea();
        Block insideHalf = world.setChest(10, 64, 0, Material.CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        Block outsideHalf = world.setChest(11, 64, 0, Material.CHEST, BlockFace.SOUTH, Chest.Type.LEFT);
        assertSame(outsideHalf, LockUtil.findDoubleChestPartner(insideHalf));

        assertFalse(areas.isLockPlacementAllowed(insideHalf));
        assertFalse(areas.isLockPlacementAllowed(outsideHalf));
        assertFalse(service.lock(alice, insideHalf));
        assertFalse(service.lock(alice, outsideHalf));
        assertTrue(cachedLockKeys().isEmpty());

        Block leftInside = world.setChest(5, 64, 5, Material.CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        world.setChest(6, 64, 5, Material.CHEST, BlockFace.SOUTH, Chest.Type.LEFT);
        assertTrue(areas.isLockPlacementAllowed(leftInside));
        assertTrue(service.lock(alice, leftInside));
    }

    @Test
    @DisplayName("Einzelkiste an der Grenze ist erlaubt, Schalter aus erlaubt auch die Doppelkiste")
    void einzelkisteAnDerGrenze_undSchalterAus() {
        apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        Block insideHalf = world.setChest(10, 64, 0, Material.CHEST, BlockFace.SOUTH, Chest.Type.RIGHT);
        world.setChest(11, 64, 0, Material.CHEST, BlockFace.SOUTH, Chest.Type.LEFT);
        assertTrue(areas.isLockPlacementAllowed(insideHalf));

        apply(new AreaChange.Enable());
        Block single = world.setChest(10, 64, 9, Material.CHEST, BlockFace.SOUTH, Chest.Type.SINGLE);
        assertTrue(areas.isLockPlacementAllowed(single));
    }

    @Test
    @DisplayName("bindSecureHopper() lehnt ab, wenn der Hopper ausserhalb liegt")
    void bindSecureHopper_hopperAusserhalb() {
        enableWithSpawnArea();
        Block container = barrel(10, 5);
        assertTrue(service.lock(alice, container));

        Block outsideHopper = world.setBlock(11, 64, 5, Material.HOPPER);
        Block insideHopper = world.setBlock(10, 65, 5, Material.HOPPER);

        assertFalse(service.bindSecureHopper(alice, outsideHopper, container));
        assertTrue(cachedHopperKeys().isEmpty());
        assertTrue(service.bindSecureHopper(alice, insideHopper, container));
        assertEquals(Set.of(world.keyAt(10, 65, 5)), cachedHopperKeys());
    }

    @Test
    @DisplayName("bindSecureHopper() lehnt ab, wenn der Behaelter ausserhalb liegt")
    void bindSecureHopper_behaelterAusserhalb() throws IOException {
        Block container = barrel(11, 10);
        assertTrue(service.lock(alice, container));
        Path file = fixture.areaFolder.resolve(AreaStore.FILE_NAME);
        Files.writeString(file, String.join("\n",
                "enabled: true",
                "areas:",
                "- name: spawn",
                "  world: world",
                "  min_x: 0",
                "  min_z: 0",
                "  max_x: 10",
                "  max_z: 10",
                ""));
        AreaService reloaded = new AreaService(new AreaStore(file, mock(Logger.class)), service,
                fixture.areaEnvironment, mock(Logger.class));
        when(fixture.plugin.areas()).thenReturn(reloaded);

        Block insideHopper = world.setBlock(10, 64, 10, Material.HOPPER);
        assertTrue(reloaded.isInside(insideHopper));
        assertTrue(service.isLocked(container));
        assertFalse(service.bindSecureHopper(alice, insideHopper, container));
        assertTrue(service.getAllCachedHoppers().isEmpty());
    }

    @Test
    @DisplayName("restoreUndo() lehnt ab, wenn eine Sperre im Snapshot ausserhalb liegt, und behaelt den Snapshot")
    void restoreUndo_ausserhalb() {
        when(fixture.plugin.undos()).thenReturn(new UndoManager(5));
        when(fixture.config.undoSeconds()).thenReturn(60);
        when(alice.getLocation()).thenReturn(new Location(world.world(), 0, 64, 0));
        apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        apply(new AreaChange.Enable());
        Block chest = barrel(8, 8);
        assertTrue(service.lock(alice, chest));
        assertTrue(service.unlockByIdWithUndo(alice, world.keyAt(8, 64, 8).asId()));
        apply(new AreaChange.Resize("spawn", 0, 0, 5, 5));

        assertFalse(service.restoreUndo(alice));
        assertTrue(service.hasUndo(alice));
        assertFalse(service.isLocked(chest));

        apply(new AreaChange.Resize("spawn", 0, 0, 10, 10));
        assertTrue(service.restoreUndo(alice));
        assertTrue(service.isLocked(chest));
    }

    @Test
    @DisplayName("apply entfernt genau die Sperren und Hopper ausserhalb, Cache und Zaehler stimmen danach")
    void apply_entferntGenauDieRichtigen() {
        apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        Block aliceInside = barrel(5, 5);
        Block aliceOutsideOne = barrel(50, 50);
        Block aliceOutsideTwo = barrel(-50, 50);
        Block bobOutside = barrel(60, 60);
        Block bobInside = barrel(6, 6);
        for (Block block : List.of(aliceInside, aliceOutsideOne, aliceOutsideTwo, bobOutside, bobInside)) {
            Player owner = block == bobOutside || block == bobInside ? bob : alice;
            assertTrue(service.lock(owner, block));
        }
        assertTrue(service.bindSecureHopper(alice, world.setBlock(50, 65, 50, Material.HOPPER), aliceOutsideOne));
        assertTrue(service.bindSecureHopper(alice, world.setBlock(5, 65, 5, Material.HOPPER), aliceInside));
        assertTrue(service.bindSecureHopper(bob, world.setBlock(11, 64, 6, Material.HOPPER), bobInside));

        AreaChangeResult result = areas.apply(new AreaChange.Enable());

        assertEquals(3, result.removedLocks());
        assertEquals(2, result.removedSecureHoppers());
        assertEquals(2, result.notifiedOwners());
        assertEquals(Set.of(world.keyAt(5, 64, 5), world.keyAt(6, 64, 6)), cachedLockKeys());
        assertEquals(Set.of(world.keyAt(5, 65, 5)), cachedHopperKeys());
        assertEquals(1, service.countLocks(alice.getUniqueId()));
        assertEquals(1, service.countLocks(bob.getUniqueId()));
        assertFalse(service.isLocked(aliceOutsideOne));

        verify(fixture.plugin.signs()).removeSign(world.keyAt(50, 64, 50).asId());
        verify(fixture.plugin.signs()).removeSign(world.keyAt(-50, 64, 50).asId());
        verify(fixture.plugin.signs()).removeSign(world.keyAt(60, 64, 60).asId());
        verify(fixture.plugin.signs(), never()).removeSign(world.keyAt(5, 64, 5).asId());

        when(fixture.config.maxLocksPerPlayer()).thenReturn(2);
        when(fixture.config.maxHoppersPerPlayer()).thenReturn(1);
        assertTrue(service.lock(alice, barrel(1, 1)));
        assertFalse(service.lock(alice, barrel(2, 2)));
        assertTrue(service.bindSecureHopper(bob, world.setBlock(6, 65, 6, Material.HOPPER), bobInside));
        assertFalse(service.bindSecureHopper(alice, world.setBlock(1, 65, 1, Material.HOPPER), barrel(1, 1)));
    }

    @Test
    @DisplayName("Benachrichtigung: pro betroffenem Besitzer genau eine Meldung mit den Anzahlen")
    void apply_benachrichtigungProBesitzerEinmal() {
        apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        Block aliceOutside = barrel(50, 50);
        assertTrue(service.lock(alice, aliceOutside));
        assertTrue(service.lock(alice, barrel(51, 50)));
        assertTrue(service.lock(bob, barrel(70, 70)));
        assertTrue(service.lock(bob, barrel(5, 5)));
        assertTrue(service.bindSecureHopper(alice, world.setBlock(50, 65, 50, Material.HOPPER), aliceOutside));

        areas.apply(new AreaChange.Enable());

        assertEquals(Set.of(
                        new RecordingAreaEnvironment.OwnerNotification(alice.getUniqueId(), 2, 1),
                        new RecordingAreaEnvironment.OwnerNotification(bob.getUniqueId(), 1, 0)),
                Set.copyOf(fixture.areaEnvironment.notifications()));
        assertEquals(2, fixture.areaEnvironment.notifications().size());
    }

    @Test
    @DisplayName("Loeschen eines Areals entfernt dessen Sperren, Ausschalten danach entfernt nichts")
    void apply_loeschenUndAusschalten() {
        apply(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        apply(new AreaChange.Create("markt", "world", 100, 100, 110, 110));
        apply(new AreaChange.Enable());
        assertTrue(service.lock(alice, barrel(105, 105)));
        assertTrue(service.lock(alice, barrel(5, 5)));

        AreaChangeResult deleted = areas.apply(new AreaChange.Delete("MARKT"));
        AreaChangeResult disabled = areas.apply(new AreaChange.Disable());

        assertEquals(1, deleted.removedLocks());
        assertEquals(0, disabled.removedLocks());
        assertEquals(Set.of(world.keyAt(5, 64, 5)), cachedLockKeys());
    }

    @Test
    @DisplayName("removeOutsideArea ueberspringt Eintraege, die nicht mehr im Cache sind")
    void removeOutsideArea_nichtMehrImCache() {
        assertTrue(service.lock(alice, barrel(5, 5)));
        LockEntry stale = new LockEntry(world.keyAt(9, 64, 9), Material.BARREL, alice.getUniqueId(), 1L);
        LockEntry cached = service.getAllCachedLocks().iterator().next();

        LockRemovalBatch batch = service.removeOutsideArea(List.of(stale, cached), List.of());

        assertEquals(List.of(cached), batch.locks());
        assertTrue(service.getAllCachedLocks().isEmpty());
        assertTrue(service.removeOutsideArea(List.of(cached), List.of()).isEmpty());
    }

    @Test
    @DisplayName("DB-Fehler beim Entfernen: Cache wird wiederhergestellt, ohne neuere Eintraege zu ueberschreiben")
    void removeOutsideArea_dbFehler_cacheWiederhergestellt() {
        assertTrue(service.lock(alice, barrel(50, 50)));
        assertTrue(service.lock(bob, barrel(60, 60)));
        List<LockEntry> toRemove = List.copyOf(service.getAllCachedLocks());

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

        LockRemovalBatch batch = service.removeOutsideArea(toRemove, List.of());

        assertEquals(2, batch.locks().size());
        assertEquals(Set.of(world.keyAt(50, 64, 50), world.keyAt(60, 64, 60)), cachedLockKeys());
        assertEquals(1, service.countLocks(alice.getUniqueId()));
        assertEquals(1, service.countLocks(bob.getUniqueId()));
    }
}
