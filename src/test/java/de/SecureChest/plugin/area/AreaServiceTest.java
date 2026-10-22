package de.SecureChest.plugin.area;

import de.SecureChest.plugin.lock.LockEntry;
import de.SecureChest.plugin.lock.LockKey;
import de.SecureChest.plugin.lock.LockRemovalBatch;
import de.SecureChest.plugin.lock.LockService;
import de.SecureChest.plugin.lock.SecureHopperEntry;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AreaService: Schalter, Geometrie, Vorschau und Anwenden")
class AreaServiceTest {

    private static final UUID OVERWORLD_UUID = UUID.randomUUID();
    private static final UUID NETHER_UUID = UUID.randomUUID();
    private static final UUID UNLOADED_WORLD_UUID = UUID.randomUUID();

    @TempDir
    Path folder;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    private final List<LockEntry> cachedLocks = new ArrayList<>();
    private final List<SecureHopperEntry> cachedHoppers = new ArrayList<>();
    private LockService locks;
    private RecordingAreaEnvironment environment;
    private AreaService service;

    @BeforeEach
    void setUp() {
        locks = mock(LockService.class);
        when(locks.getAllCachedLocks()).thenReturn(cachedLocks);
        when(locks.getAllCachedHoppers()).thenReturn(cachedHoppers);
        when(locks.removeOutsideArea(any(), any())).thenAnswer(invocation -> {
            Collection<LockEntry> requestedLocks = invocation.getArgument(0);
            Collection<SecureHopperEntry> requestedHoppers = invocation.getArgument(1);
            return new LockRemovalBatch(List.copyOf(requestedLocks), List.copyOf(requestedHoppers));
        });
        environment = new RecordingAreaEnvironment()
                .addWorld(OVERWORLD_UUID, "world")
                .addWorld(NETHER_UUID, "world_nether");
        service = newService();
    }

    private AreaService newService() {
        return new AreaService(new AreaStore(folder.resolve(AreaStore.FILE_NAME), mock(Logger.class)),
                locks, environment, mock(Logger.class));
    }

    private void applyValid(AreaChange change) {
        assertTrue(service.apply(change).applied(), "Aenderung sollte gelten: " + change);
    }

    private LockEntry lockAt(UUID worldUuid, int x, int z, UUID owner) {
        LockEntry entry = new LockEntry(new LockKey(worldUuid, x, 64, z), Material.CHEST, owner, 1L);
        cachedLocks.add(entry);
        return entry;
    }

    private SecureHopperEntry hopperAt(UUID worldUuid, int x, int z, UUID owner, LockEntry boundLock) {
        SecureHopperEntry entry = new SecureHopperEntry(new LockKey(worldUuid, x, 65, z), owner, boundLock.key());
        cachedHoppers.add(entry);
        return entry;
    }

    private Block block(String worldName, int x, int z) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(worldName);
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(Material.BARREL);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(64);
        when(block.getZ()).thenReturn(z);
        return block;
    }

    private Set<AreaValidationError.Code> codes(AreaChangePreview preview) {
        return preview.errors().stream().map(AreaValidationError::code).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Startzustand ohne Datei: Schalter aus, keine Areale, alles erlaubt")
    void startzustand_ohneDatei() {
        assertFalse(service.isEnabled());
        assertTrue(service.areas().isEmpty());
        assertTrue(service.isLockPlacementAllowed(block("world", 12345, -9876)));
    }

    @Test
    @DisplayName("Schalter aus: Areale sind ohne Wirkung, auch ausserhalb und in Welten ohne Areal")
    void schalterAus_keineWirkung() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));

        assertFalse(service.isEnabled());
        assertTrue(service.isLockPlacementAllowed(block("world", 500, 500)));
        assertTrue(service.isLockPlacementAllowed(block("world_nether", 5, 5)));
        assertTrue(service.isLockPlacementAllowed(block("world", 5, 5)));
    }

    @Test
    @DisplayName("Schalter an und Welt ohne Areal: nirgends erlaubt")
    void schalterAn_weltOhneAreal_nichtsErlaubt() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Enable());

        assertFalse(service.isLockPlacementAllowed(block("world_nether", 5, 5)));
        assertFalse(service.isLockPlacementAllowed(block("world_nether", 0, 0)));
    }

    @Test
    @DisplayName("Schalter an ohne jedes Areal: nirgends erlaubt")
    void schalterAn_ohneAreal_nichtsErlaubt() {
        applyValid(new AreaChange.Enable());

        assertFalse(service.isLockPlacementAllowed(block("world", 0, 0)));
    }

    @Test
    @DisplayName("Schalter an: innerhalb erlaubt, Grenzen inklusive, ausserhalb abgelehnt")
    void schalterAn_innenErlaubt_aussenAbgelehnt() {
        applyValid(new AreaChange.Create("spawn", "world", 10, 10, -10, -10));
        applyValid(new AreaChange.Enable());

        assertTrue(service.isLockPlacementAllowed(block("world", 0, 0)));
        assertTrue(service.isLockPlacementAllowed(block("world", -10, 10)));
        assertFalse(service.isLockPlacementAllowed(block("world", 11, 0)));
        assertFalse(service.isLockPlacementAllowed(block("world", 0, -11)));
    }

    @Test
    @DisplayName("Mehrere und ueberlappende Areale: wirksam ist die Vereinigung")
    void ueberlappendeAreale_vereinigung() {
        applyValid(new AreaChange.Create("a", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Create("b", "world", 5, 5, 20, 20));
        applyValid(new AreaChange.Create("c", "world", -100, -100, -90, -90));
        applyValid(new AreaChange.Enable());

        assertTrue(service.isInside("world", 2, 2));
        assertTrue(service.isInside("world", 7, 7));
        assertTrue(service.isInside("world", 18, 18));
        assertTrue(service.isInside("world", -95, -95));
        assertFalse(service.isInside("world", 2, 18));
        assertFalse(service.isInside("world", 18, 2));
        assertFalse(service.isInside("world", -50, -50));
    }

    @Test
    @DisplayName("Mehrere Welten: ein Areal wirkt nur in seiner Welt")
    void mehrereWelten() {
        applyValid(new AreaChange.Create("oben", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Create("unten", "world_nether", 100, 100, 110, 110));

        assertTrue(service.isInside("world", 5, 5));
        assertFalse(service.isInside("world", 105, 105));
        assertTrue(service.isInside("world_nether", 105, 105));
        assertFalse(service.isInside("world_nether", 5, 5));
    }

    @Test
    @DisplayName("isInside(Block) gilt unabhaengig vom Schalter")
    void isInsideBlock_unabhaengigVomSchalter() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));

        assertTrue(service.isInside(block("world", 10, 10)));
        assertFalse(service.isInside(block("world", 11, 10)));
        assertFalse(service.isInside((Block) null));
    }

    @Test
    @DisplayName("find ignoriert Gross- und Kleinschreibung, areas() ist unveraenderlich")
    void find_undAreasKopie() {
        applyValid(new AreaChange.Create("Spawn", "world", 0, 0, 10, 10));

        assertEquals("Spawn", service.find("sPAWN").orElseThrow().name());
        assertTrue(service.find("anders").isEmpty());
        assertTrue(service.find(null).isEmpty());
        List<Area> areas = service.areas();
        assertThrows(UnsupportedOperationException.class, () -> areas.add(Area.normalized("x", "world", 0, 0, 0, 0)));
    }

    @Test
    @DisplayName("previewCreate: Name ungueltig, Welt fehlt")
    void previewCreate_validierung() {
        AreaChangePreview invalidName = service.previewCreate("mit leerzeichen", "world", 0, 0, 1, 1);
        AreaChangePreview missingWorld = service.previewCreate("spawn", " ", 0, 0, 1, 1);
        AreaChangePreview unknownWorld = service.previewCreate("spawn", "world_the_end", 0, 0, 1, 1);
        AreaChangePreview tooLong = service.previewCreate("a".repeat(33), "world", 0, 0, 1, 1);

        assertEquals(Set.of(AreaValidationError.Code.NAME_INVALID), codes(invalidName));
        assertEquals(Set.of(AreaValidationError.Code.WORLD_NAME_MISSING), codes(missingWorld));
        assertEquals(Set.of(AreaValidationError.Code.WORLD_NOT_FOUND), codes(unknownWorld));
        assertEquals("world_the_end", unknownWorld.errors().get(0).detail());
        assertEquals(Set.of(AreaValidationError.Code.NAME_INVALID), codes(tooLong));
        assertFalse(invalidName.isValid());
    }

    @Test
    @DisplayName("previewCreate: Name ist ohne Gross- und Kleinschreibung eindeutig")
    void previewCreate_nameSchonVergeben() {
        applyValid(new AreaChange.Create("Spawn", "world", 0, 0, 10, 10));

        AreaChangePreview duplicate = service.previewCreate("SPAWN", "world_nether", 0, 0, 1, 1);

        assertEquals(Set.of(AreaValidationError.Code.NAME_ALREADY_USED), codes(duplicate));
    }

    @Test
    @DisplayName("previewCreate hat nie Folgen, auch bei aktivem Schalter und Sperren ausserhalb")
    void previewCreate_nieFolgen() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Enable());
        lockAt(OVERWORLD_UUID, 500, 500, alice);

        AreaChangePreview preview = service.previewCreate("markt", "world", 100, 100, 200, 200);

        assertTrue(preview.isValid());
        assertEquals(0, preview.affectedLocks());
        assertEquals(0, preview.affectedSecureHoppers());
        assertEquals(0, preview.affectedOwners());
    }

    @Test
    @DisplayName("previewEnable zaehlt Sperren, Hopper und Besitzer ausserhalb und aendert nichts")
    void previewEnable_zaehltUndAendertNichts() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        LockEntry inside = lockAt(OVERWORLD_UUID, 5, 5, alice);
        LockEntry outside = lockAt(OVERWORLD_UUID, 50, 50, alice);
        lockAt(NETHER_UUID, 5, 5, bob);
        hopperAt(OVERWORLD_UUID, 50, 51, alice, outside);
        hopperAt(OVERWORLD_UUID, 5, 6, alice, inside);
        hopperAt(OVERWORLD_UUID, 20, 20, carol, inside);

        AreaChangePreview preview = service.previewEnable();

        assertTrue(preview.isValid());
        assertEquals(2, preview.affectedLocks());
        assertEquals(2, preview.affectedSecureHoppers());
        assertEquals(3, preview.affectedOwners());
        assertFalse(service.isEnabled());
        verify(locks, never()).removeOutsideArea(any(), any());
        assertTrue(environment.notifications().isEmpty());
    }

    @Test
    @DisplayName("previewDisable hat nie Folgen")
    void previewDisable_ohneFolgen() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Enable());
        lockAt(OVERWORLD_UUID, 500, 500, alice);

        AreaChangePreview preview = service.previewDisable();

        assertTrue(preview.isValid());
        assertEquals(0, preview.affectedLocks());
        assertTrue(service.isEnabled());
    }

    @Test
    @DisplayName("previewResize zaehlt nur Sperren, die durch die Verkleinerung herausfallen")
    void previewResize_zaehltNurHerausfallende() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 100, 100));
        applyValid(new AreaChange.Create("markt", "world", 200, 200, 300, 300));
        applyValid(new AreaChange.Enable());
        lockAt(OVERWORLD_UUID, 10, 10, alice);
        lockAt(OVERWORLD_UUID, 90, 90, alice);
        lockAt(OVERWORLD_UUID, 90, 10, bob);
        lockAt(OVERWORLD_UUID, 250, 250, carol);

        AreaChangePreview preview = service.previewResize("SPAWN", 0, 0, 50, 50);

        assertEquals(2, preview.affectedLocks());
        assertEquals(2, preview.affectedOwners());
        assertEquals(new Area("spawn", "world", 0, 0, 100, 100), service.find("spawn").orElseThrow());
    }

    @Test
    @DisplayName("previewResize: Ueberlappung mit anderem Areal schuetzt die Sperre")
    void previewResize_ueberlappungSchuetzt() {
        applyValid(new AreaChange.Create("gross", "world", 0, 0, 100, 100));
        applyValid(new AreaChange.Create("klein", "world", 80, 80, 120, 120));
        applyValid(new AreaChange.Enable());
        lockAt(OVERWORLD_UUID, 90, 90, alice);

        assertEquals(0, service.previewResize("gross", 0, 0, 10, 10).affectedLocks());
        assertEquals(0, service.previewDelete("gross").affectedLocks());
    }

    @Test
    @DisplayName("previewDelete zaehlt Sperren und Hopper im geloeschten Areal")
    void previewDelete_zaehlt() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Enable());
        LockEntry lock = lockAt(OVERWORLD_UUID, 5, 5, alice);
        hopperAt(OVERWORLD_UUID, 5, 6, alice, lock);

        AreaChangePreview preview = service.previewDelete("spawn");

        assertEquals(1, preview.affectedLocks());
        assertEquals(1, preview.affectedSecureHoppers());
        assertEquals(1, preview.affectedOwners());
        assertTrue(service.find("spawn").isPresent());
    }

    @Test
    @DisplayName("Resize und Delete bei ausgeschaltetem Schalter haben keine Folgen")
    void resizeUndDelete_schalterAus_ohneFolgen() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        lockAt(OVERWORLD_UUID, 5, 5, alice);

        assertEquals(0, service.previewResize("spawn", 100, 100, 110, 110).affectedLocks());
        assertEquals(0, service.previewDelete("spawn").affectedLocks());
    }

    @Test
    @DisplayName("previewResize und previewDelete fuer unbekanntes Areal liefern AREA_NOT_FOUND")
    void previewResizeDelete_unbekannt() {
        assertEquals(Set.of(AreaValidationError.Code.AREA_NOT_FOUND), codes(service.previewResize("fehlt", 0, 0, 1, 1)));
        assertEquals(Set.of(AreaValidationError.Code.AREA_NOT_FOUND), codes(service.previewDelete("fehlt")));
    }

    @Test
    @DisplayName("Sperren, die schon vorher ausserhalb lagen, zaehlen bei einer Verkleinerung nicht")
    void bereitsAusserhalb_zaehltNicht() throws IOException {
        Files.writeString(folder.resolve(AreaStore.FILE_NAME), String.join("\n",
                "enabled: true",
                "areas:",
                "- name: spawn",
                "  world: world",
                "  min_x: 0",
                "  min_z: 0",
                "  max_x: 100",
                "  max_z: 100",
                ""));
        service = newService();
        lockAt(OVERWORLD_UUID, 500, 500, alice);
        lockAt(OVERWORLD_UUID, 80, 80, bob);

        AreaChangePreview preview = service.previewResize("spawn", 0, 0, 50, 50);

        assertEquals(1, preview.affectedLocks());
        assertEquals(1, preview.affectedOwners());
    }

    @Test
    @DisplayName("Sperren in nicht geladenen Welten werden nicht geprueft und nicht entfernt")
    void nichtGeladeneWelt_bleibt() {
        lockAt(UNLOADED_WORLD_UUID, 5, 5, alice);

        assertEquals(0, service.previewEnable().affectedLocks());
        AreaChangeResult result = service.apply(new AreaChange.Enable());

        assertEquals(0, result.removedLocks());
        verify(locks, never()).removeOutsideArea(any(), any());
    }

    @Test
    @DisplayName("Spaeter fehlende Welt: Zugriffe auf ihr Areal stuerzen nicht ab")
    void spaeterFehlendeWelt_keinAbsturz() {
        applyValid(new AreaChange.Create("unten", "world_nether", 0, 0, 10, 10));
        environment.removeWorld("world_nether");

        assertTrue(service.isInside("world_nether", 5, 5));
        assertTrue(service.previewResize("unten", 0, 0, 5, 5).isValid());
        assertTrue(service.apply(new AreaChange.Delete("unten")).applied());
    }

    @Test
    @DisplayName("apply mit Fehlern aendert nichts und speichert nichts")
    void apply_ungueltig_aendertNichts() {
        AreaChangeResult result = service.apply(new AreaChange.Create("x y", "world", 0, 0, 1, 1));

        assertFalse(result.applied());
        assertEquals(Set.of(AreaValidationError.Code.NAME_INVALID),
                result.errors().stream().map(AreaValidationError::code).collect(Collectors.toSet()));
        assertTrue(service.areas().isEmpty());
        assertFalse(Files.exists(folder.resolve(AreaStore.FILE_NAME)));
    }

    @Test
    @DisplayName("apply(Enable) entfernt genau die Sperren und Hopper ausserhalb und benachrichtigt jeden Besitzer einmal")
    void applyEnable_entferntUndBenachrichtigt() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        LockEntry inside = lockAt(OVERWORLD_UUID, 5, 5, alice);
        LockEntry aliceOutsideOne = lockAt(OVERWORLD_UUID, 50, 50, alice);
        LockEntry aliceOutsideTwo = lockAt(OVERWORLD_UUID, -50, 50, alice);
        LockEntry bobOutside = lockAt(NETHER_UUID, 5, 5, bob);
        SecureHopperEntry boundToRemoved = hopperAt(OVERWORLD_UUID, 50, 51, alice, aliceOutsideOne);
        hopperAt(OVERWORLD_UUID, 5, 6, alice, inside);
        SecureHopperEntry outsideBoundToInside = hopperAt(OVERWORLD_UUID, 11, 5, carol, inside);

        AreaChangeResult result = service.apply(new AreaChange.Enable());

        assertTrue(result.applied());
        assertTrue(result.isSaved());
        assertEquals(3, result.removedLocks());
        assertEquals(2, result.removedSecureHoppers());
        assertEquals(3, result.notifiedOwners());
        verify(locks).removeOutsideArea(
                List.of(aliceOutsideOne, aliceOutsideTwo, bobOutside),
                List.of(boundToRemoved, outsideBoundToInside));
        assertEquals(Set.of(
                        new RecordingAreaEnvironment.OwnerNotification(alice, 2, 1),
                        new RecordingAreaEnvironment.OwnerNotification(bob, 1, 0),
                        new RecordingAreaEnvironment.OwnerNotification(carol, 0, 1)),
                Set.copyOf(environment.notifications()));
        assertEquals(3, environment.notifications().size());
        assertTrue(service.isEnabled());
        assertTrue(newService().isEnabled());
    }

    @Test
    @DisplayName("apply rechnet mit dem Stand beim Anwenden, nicht mit dem der Vorschau")
    void apply_standBeimAnwenden() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        lockAt(OVERWORLD_UUID, 50, 50, alice);
        AreaChangePreview preview = service.previewEnable();
        lockAt(OVERWORLD_UUID, 60, 60, bob);

        AreaChangeResult result = service.apply(preview.change());

        assertEquals(1, preview.affectedLocks());
        assertEquals(2, result.removedLocks());
        assertEquals(2, result.notifiedOwners());
    }

    @Test
    @DisplayName("Ergebnis meldet die tatsaechlich entfernten Eintraege des LockService")
    void apply_tatsaechlicheZahlen() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        LockEntry removed = lockAt(OVERWORLD_UUID, 50, 50, alice);
        lockAt(OVERWORLD_UUID, 60, 60, bob);
        doReturn(new LockRemovalBatch(List.of(removed), List.of())).when(locks).removeOutsideArea(any(), any());

        AreaChangeResult result = service.apply(new AreaChange.Enable());

        assertEquals(1, result.removedLocks());
        assertEquals(1, result.notifiedOwners());
        assertEquals(List.of(new RecordingAreaEnvironment.OwnerNotification(alice, 1, 0)), environment.notifications());
    }

    @Test
    @DisplayName("apply(Disable) entfernt nichts")
    void applyDisable_entferntNichts() {
        applyValid(new AreaChange.Create("spawn", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Enable());
        lockAt(OVERWORLD_UUID, 50, 50, alice);

        AreaChangeResult result = service.apply(new AreaChange.Disable());

        assertEquals(0, result.removedLocks());
        assertFalse(service.isEnabled());
        verify(locks, never()).removeOutsideArea(any(), any());
    }

    @Test
    @DisplayName("apply(Resize) uebernimmt normalisierte Ecken und behaelt Namen und Reihenfolge")
    void applyResize_normalisiert() {
        applyValid(new AreaChange.Create("Spawn", "world", 0, 0, 10, 10));
        applyValid(new AreaChange.Create("markt", "world", 20, 20, 30, 30));

        applyValid(new AreaChange.Resize("spawn", 5, 5, -5, -5));

        assertEquals(List.of(new Area("Spawn", "world", -5, -5, 5, 5), new Area("markt", "world", 20, 20, 30, 30)),
                service.areas());
        assertEquals(service.areas(), newService().areas());
    }

    @Test
    @DisplayName("Speicherfehler: Aenderung bleibt im Speicher wirksam, Ergebnis meldet den Fehler")
    void apply_speicherfehler() throws IOException {
        Files.writeString(folder.resolve(AreaStore.FILE_NAME), "areas: [ kaputt");
        service = newService();
        lockAt(OVERWORLD_UUID, 50, 50, alice);

        AreaChangeResult result = service.apply(new AreaChange.Enable());

        assertTrue(result.applied());
        assertFalse(result.isSaved());
        assertNotNull(result.saveError());
        assertTrue(service.isEnabled());
        assertEquals(1, result.removedLocks());
        assertEquals("areas: [ kaputt", Files.readString(folder.resolve(AreaStore.FILE_NAME)));
    }
}
