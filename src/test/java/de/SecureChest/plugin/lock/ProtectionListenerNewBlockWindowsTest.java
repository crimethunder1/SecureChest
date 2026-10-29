package de.SecureChest.plugin.lock;

import com.destroystokyo.paper.event.player.PlayerRecipeBookClickEvent;
import de.SecureChest.plugin.MessageService;
import de.SecureChest.plugin.listener.ProtectionListener;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.CrafterInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.view.CrafterView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.description;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ProtectionListener: Fenster der neuen Bloecke fuer Besitzer, Viewer, Stocker und Fremde")
class ProtectionListenerNewBlockWindowsTest {

    record Window(Material type, int topSize, List<Integer> topSlots) {
        @Override
        public String toString() {
            return type.name();
        }
    }

    record Click(ClickType clickType, InventoryAction action, boolean inTopInventory) {}

    private static final List<Click> VIEWER_FORBIDDEN_TOP_CLICKS = List.of(
            new Click(ClickType.LEFT, InventoryAction.PICKUP_ALL, true),
            new Click(ClickType.RIGHT, InventoryAction.PICKUP_HALF, true),
            new Click(ClickType.LEFT, InventoryAction.PLACE_ALL, true),
            new Click(ClickType.RIGHT, InventoryAction.PLACE_ONE, true),
            new Click(ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR, true),
            new Click(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, true),
            new Click(ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY, true),
            new Click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, true),
            new Click(ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP, true),
            new Click(ClickType.DROP, InventoryAction.DROP_ONE_SLOT, true),
            new Click(ClickType.CONTROL_DROP, InventoryAction.DROP_ALL_SLOT, true),
            new Click(ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR, true),
            new Click(ClickType.MIDDLE, InventoryAction.CLONE_STACK, true),
            new Click(ClickType.LEFT, InventoryAction.NOTHING, true));

    private static final List<Click> VIEWER_FORBIDDEN_BOTTOM_CLICKS = List.of(
            new Click(ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, false),
            new Click(ClickType.SHIFT_RIGHT, InventoryAction.MOVE_TO_OTHER_INVENTORY, false),
            new Click(ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR, false),
            new Click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, false));

    private static final List<Click> VIEWER_ALLOWED_BOTTOM_CLICKS = List.of(
            new Click(ClickType.LEFT, InventoryAction.PICKUP_ALL, false),
            new Click(ClickType.RIGHT, InventoryAction.PLACE_ONE, false),
            new Click(ClickType.DROP, InventoryAction.DROP_ONE_SLOT, false));

    private LockServiceTestFixture fixture;
    private DoubleChestTestWorld world;
    private SqlLockService service;
    private MessageService messages;
    private ProtectionListener listener;
    private Player owner;
    private Player viewer;
    private Player stocker;
    private Player stranger;

    static List<Window> windows() {
        return List.of(
                new Window(Material.FURNACE, 3, List.of(0, 1, 2)),
                new Window(Material.BLAST_FURNACE, 3, List.of(0, 1, 2)),
                new Window(Material.SMOKER, 3, List.of(0, 1, 2)),
                new Window(Material.DROPPER, 9, List.of(0, 4, 8)),
                new Window(Material.DISPENSER, 9, List.of(0, 4, 8)),
                new Window(Material.BREWING_STAND, 5, List.of(0, 1, 2, 3, 4)),
                new Window(Material.CRAFTER, 9, List.of(0, 4, 8)));
    }

    @BeforeEach
    void setUp() throws SQLException {
        fixture = new LockServiceTestFixture();
        world = fixture.world;
        service = fixture.service;
        messages = fixture.plugin.msg();
        listener = new ProtectionListener(fixture.plugin);
        owner = fixture.player("Owner", false);
        viewer = fixture.player("Viewer", false);
        stocker = fixture.player("Stocker", false);
        stranger = fixture.player("Stranger", false);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private Block lockedWithTrust(Material type) {
        Block block = world.setBlock(0, 64, 0, type);
        assertTrue(service.lock(owner, block));
        String lockId = service.getLock(block).orElseThrow().key().asId();
        assertTrue(service.setTrustById(owner, lockId, viewer.getUniqueId(), AccessLevel.VIEW));
        assertTrue(service.setTrustById(owner, lockId, stocker.getUniqueId(), AccessLevel.STOCKER));
        return block;
    }

    private Inventory topInventoryOf(Block block, Material type, int size) {
        boolean furnaceType = type == Material.FURNACE || type == Material.BLAST_FURNACE || type == Material.SMOKER;
        Location location = new Location(world.world(), block.getX(), block.getY(), block.getZ());
        Inventory inventory = furnaceType ? mock(FurnaceInventory.class)
                : type == Material.CRAFTER ? mock(CrafterInventory.class) : mock(Inventory.class);
        when(inventory.getLocation()).thenReturn(location);
        when(inventory.getSize()).thenReturn(size);
        return inventory;
    }

    private InventoryView viewOf(Inventory topInventory, Inventory bottomInventory, Material type) {
        InventoryView view = type == Material.CRAFTER ? mock(CrafterView.class) : mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(topInventory);
        when(view.getBottomInventory()).thenReturn(bottomInventory);
        return view;
    }

    private InventoryView windowFor(Window window) {
        Block block = lockedWithTrust(window.type());
        Inventory top = topInventoryOf(block, window.type(), window.topSize());
        return viewOf(top, mock(Inventory.class), window.type());
    }

    private InventoryClickEvent click(Player player, InventoryView view, Click click, int rawSlot) {
        Inventory clickedInventory = click.inTopInventory() ? view.getTopInventory() : view.getBottomInventory();
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getClickedInventory()).thenReturn(clickedInventory);
        when(event.getClick()).thenReturn(click.clickType());
        when(event.isShiftClick()).thenReturn(click.clickType().isShiftClick());
        when(event.getAction()).thenReturn(click.action());
        when(event.getRawSlot()).thenReturn(rawSlot);
        return event;
    }

    private InventoryDragEvent drag(Player player, InventoryView view, Set<Integer> rawSlots) {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(rawSlots);
        return event;
    }

    private InventoryOpenEvent open(Player player, InventoryView view) {
        InventoryOpenEvent event = mock(InventoryOpenEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        return event;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Oeffnen: Fremder wird abgewiesen, Besitzer, Viewer und Stocker oeffnen")
    void opening(Window window) {
        InventoryView view = windowFor(window);

        InventoryOpenEvent strangerOpen = open(stranger, view);
        InventoryOpenEvent ownerOpen = open(owner, view);
        InventoryOpenEvent viewerOpen = open(viewer, view);
        InventoryOpenEvent stockerOpen = open(stocker, view);
        listener.onInventoryOpen(strangerOpen);
        listener.onInventoryOpen(ownerOpen);
        listener.onInventoryOpen(viewerOpen);
        listener.onInventoryOpen(stockerOpen);

        verify(strangerOpen).setCancelled(true);
        verify(messages).get(eq("block-gehoert"), any());
        verify(ownerOpen, never()).setCancelled(anyBoolean());
        verify(viewerOpen, never()).setCancelled(anyBoolean());
        verify(stockerOpen, never()).setCancelled(anyBoolean());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Viewer: jede Klickart auf Zutat, Brennstoff, Ergebnis und alle anderen Felder wird abgebrochen")
    void viewerTopClicksAreCancelled(Window window) {
        InventoryView view = windowFor(window);

        for (int slot : window.topSlots()) {
            for (Click forbidden : VIEWER_FORBIDDEN_TOP_CLICKS) {
                InventoryClickEvent event = click(viewer, view, forbidden, slot);
                listener.onInventoryClick(event);
                verify(event, description(forbidden + " slot " + slot)).setCancelled(true);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Viewer: Shift-Klick, Doppelklick und Zahlentasten im eigenen Inventar werden abgebrochen")
    void viewerBottomTransfersAreCancelled(Window window) {
        InventoryView view = windowFor(window);
        int bottomSlot = window.topSize() + 5;

        for (Click forbidden : VIEWER_FORBIDDEN_BOTTOM_CLICKS) {
            InventoryClickEvent event = click(viewer, view, forbidden, bottomSlot);
            listener.onInventoryClick(event);
            verify(event, description(forbidden.toString())).setCancelled(true);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Viewer: einfache Klicks im eigenen Inventar bleiben erlaubt")
    void viewerOwnInventoryClicksAreAllowed(Window window) {
        InventoryView view = windowFor(window);
        int bottomSlot = window.topSize() + 5;

        for (Click allowed : VIEWER_ALLOWED_BOTTOM_CLICKS) {
            InventoryClickEvent event = click(viewer, view, allowed, bottomSlot);
            listener.onInventoryClick(event);
            verify(event, never()).setCancelled(anyBoolean());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Stocker und Besitzer: Einlegen und Entnehmen inklusive Ergebnisfeld sind erlaubt")
    void stockerAndOwnerMayUseWindow(Window window) {
        InventoryView view = windowFor(window);

        for (Player allowedPlayer : List.of(stocker, owner)) {
            for (int slot : window.topSlots()) {
                for (Click anyClick : VIEWER_FORBIDDEN_TOP_CLICKS) {
                    InventoryClickEvent event = click(allowedPlayer, view, anyClick, slot);
                    listener.onInventoryClick(event);
                    verify(event, never()).setCancelled(anyBoolean());
                }
            }
            for (Click anyClick : VIEWER_FORBIDDEN_BOTTOM_CLICKS) {
                InventoryClickEvent event = click(allowedPlayer, view, anyClick, window.topSize() + 5);
                listener.onInventoryClick(event);
                verify(event, never()).setCancelled(anyBoolean());
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Fremder mit trotzdem offenem Fenster: jeder Klick wird abgebrochen, auch im eigenen Inventar")
    void strangerClicksAreCancelled(Window window) {
        InventoryView view = windowFor(window);

        InventoryClickEvent topClick = click(stranger, view, VIEWER_FORBIDDEN_TOP_CLICKS.get(0), window.topSlots().get(0));
        InventoryClickEvent bottomClick = click(stranger, view, VIEWER_ALLOWED_BOTTOM_CLICKS.get(0), window.topSize() + 5);
        listener.onInventoryClick(topClick);
        listener.onInventoryClick(bottomClick);

        verify(topClick).setCancelled(true);
        verify(bottomClick).setCancelled(true);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Ziehen: Viewer und Fremder duerfen nicht ins Fenster ziehen, Stocker und Besitzer schon")
    void dragging(Window window) {
        InventoryView view = windowFor(window);
        int topSlot = window.topSlots().get(window.topSlots().size() - 1);
        int bottomSlot = window.topSize() + 3;

        InventoryDragEvent viewerIntoTop = drag(viewer, view, Set.of(topSlot, bottomSlot));
        InventoryDragEvent viewerBottomOnly = drag(viewer, view, Set.of(bottomSlot, bottomSlot + 1));
        InventoryDragEvent strangerBottomOnly = drag(stranger, view, Set.of(bottomSlot));
        InventoryDragEvent stockerIntoTop = drag(stocker, view, Set.of(topSlot));
        InventoryDragEvent ownerIntoTop = drag(owner, view, Set.of(topSlot));
        for (InventoryDragEvent event : List.of(viewerIntoTop, viewerBottomOnly, strangerBottomOnly,
                stockerIntoTop, ownerIntoTop)) {
            listener.onInventoryDrag(event);
        }

        verify(viewerIntoTop).setCancelled(true);
        verify(viewerBottomOnly, never()).setCancelled(anyBoolean());
        verify(strangerBottomOnly).setCancelled(true);
        verify(stockerIntoTop, never()).setCancelled(anyBoolean());
        verify(ownerIntoTop, never()).setCancelled(anyBoolean());
    }

    private CrafterView crafterViewWithState(boolean[] disabledSlots) {
        Block block = lockedWithTrust(Material.CRAFTER);
        Inventory top = topInventoryOf(block, Material.CRAFTER, 9);
        CrafterView view = (CrafterView) viewOf(top, mock(Inventory.class), Material.CRAFTER);
        when(view.isSlotDisabled(anyInt())).thenAnswer(invocation -> disabledSlots[(int) invocation.getArgument(0)]);
        doAnswer(invocation -> {
            disabledSlots[(int) invocation.getArgument(0)] = invocation.getArgument(1);
            return null;
        }).when(view).setSlotDisabled(anyInt(), anyBoolean());
        return view;
    }

    @Test
    @DisplayName("Crafter: Viewer schaltet ein leeres Feld um, der Klick wird abgebrochen und der Zustand zurueckgesetzt")
    void crafterViewerToggleIsReverted() {
        boolean[] disabledSlots = new boolean[9];
        disabledSlots[2] = true;
        CrafterView view = crafterViewWithState(disabledSlots);
        listener.onInventoryOpen(open(viewer, view));

        disabledSlots[4] = true;
        InventoryClickEvent disableClick = click(viewer, view, new Click(ClickType.LEFT, InventoryAction.NOTHING, true), 4);
        listener.onInventoryClick(disableClick);

        disabledSlots[2] = false;
        InventoryClickEvent enableClick = click(viewer, view,
                new Click(ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, true), 2);
        listener.onInventoryClick(enableClick);

        verify(disableClick).setCancelled(true);
        verify(enableClick).setCancelled(true);
        assertFalse(disabledSlots[4], "Feld 4 muss wieder aktiv sein");
        assertTrue(disabledSlots[2], "Feld 2 muss wieder gesperrt sein");
        verify(view).setSlotDisabled(4, false);
        verify(view).setSlotDisabled(2, true);
    }

    @Test
    @DisplayName("Crafter: Stocker und Besitzer duerfen Felder umschalten, ein Klick ins eigene Inventar setzt nichts zurueck")
    void crafterStockerToggleStays() {
        boolean[] disabledSlots = new boolean[9];
        CrafterView view = crafterViewWithState(disabledSlots);
        listener.onInventoryOpen(open(stocker, view));
        listener.onInventoryOpen(open(viewer, view));

        disabledSlots[5] = true;
        InventoryClickEvent stockerClick = click(stocker, view, new Click(ClickType.LEFT, InventoryAction.NOTHING, true), 5);
        listener.onInventoryClick(stockerClick);
        InventoryClickEvent viewerBottomClick = click(viewer, view,
                new Click(ClickType.LEFT, InventoryAction.PICKUP_ALL, false), 20);
        listener.onInventoryClick(viewerBottomClick);

        verify(stockerClick, never()).setCancelled(anyBoolean());
        verify(viewerBottomClick, never()).setCancelled(anyBoolean());
        assertTrue(disabledSlots[5]);
        verify(view, never()).setSlotDisabled(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("Crafter: nach dem Schliessen haelt der Listener keinen Zustand des Viewers mehr")
    void crafterStateIsForgottenOnClose() {
        boolean[] disabledSlots = new boolean[9];
        CrafterView view = crafterViewWithState(disabledSlots);
        listener.onInventoryOpen(open(viewer, view));
        InventoryCloseEvent close = mock(InventoryCloseEvent.class);
        when(close.getPlayer()).thenReturn(viewer);
        listener.onInventoryClose(close);

        disabledSlots[1] = true;
        InventoryClickEvent click = click(viewer, view, new Click(ClickType.LEFT, InventoryAction.NOTHING, true), 1);
        listener.onInventoryClick(click);

        verify(click).setCancelled(true);
        verify(view, never()).setSlotDisabled(anyInt(), anyBoolean());
    }

    private PlayerRecipeBookClickEvent recipeBookClick(Player player, Inventory topInventory) {
        InventoryView openView = mock(InventoryView.class);
        when(openView.getTopInventory()).thenReturn(topInventory);
        when(player.getOpenInventory()).thenReturn(openView);
        return new PlayerRecipeBookClickEvent(player, NamespacedKey.minecraft("iron_ingot"), true);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Material.class, names = {"FURNACE", "BLAST_FURNACE", "SMOKER"})
    @DisplayName("Rezeptbuch im Ofen: Viewer und Fremder werden abgebrochen, Stocker und Besitzer nicht")
    void recipeBookInFurnace(Material type) {
        Block block = lockedWithTrust(type);
        Inventory top = topInventoryOf(block, type, 3);

        PlayerRecipeBookClickEvent viewerEvent = recipeBookClick(viewer, top);
        PlayerRecipeBookClickEvent strangerEvent = recipeBookClick(stranger, top);
        PlayerRecipeBookClickEvent stockerEvent = recipeBookClick(stocker, top);
        PlayerRecipeBookClickEvent ownerEvent = recipeBookClick(owner, top);
        for (PlayerRecipeBookClickEvent event : List.of(viewerEvent, strangerEvent, stockerEvent, ownerEvent)) {
            listener.onRecipeBookClick(event);
        }

        assertTrue(viewerEvent.isCancelled());
        assertTrue(strangerEvent.isCancelled());
        assertFalse(stockerEvent.isCancelled());
        assertFalse(ownerEvent.isCancelled());
        verify(viewer).updateInventory();
    }

    @Test
    @DisplayName("Rezeptbuch: ungesperrter Ofen und Fenster ohne Ofen bleiben unberuehrt")
    void recipeBookOutsideLockedFurnace() {
        Block unlockedFurnace = world.setBlock(10, 64, 0, Material.FURNACE);
        Inventory unlockedTop = topInventoryOf(unlockedFurnace, Material.FURNACE, 3);
        Block lockedCrafter = lockedWithTrust(Material.CRAFTER);
        Inventory crafterTop = topInventoryOf(lockedCrafter, Material.CRAFTER, 9);

        PlayerRecipeBookClickEvent unlockedEvent = recipeBookClick(stranger, unlockedTop);
        listener.onRecipeBookClick(unlockedEvent);
        PlayerRecipeBookClickEvent crafterEvent = recipeBookClick(viewer, crafterTop);
        listener.onRecipeBookClick(crafterEvent);

        assertFalse(unlockedEvent.isCancelled());
        assertFalse(crafterEvent.isCancelled());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windows")
    @DisplayName("Ungesperrter Block: kein Eingriff in Oeffnen, Klicken und Ziehen")
    void unlockedWindowIsUntouched(Window window) {
        Block block = world.setBlock(20, 64, 0, window.type());
        InventoryView view = viewOf(topInventoryOf(block, window.type(), window.topSize()), mock(Inventory.class),
                window.type());

        InventoryOpenEvent openEvent = open(stranger, view);
        InventoryClickEvent clickEvent = click(stranger, view, VIEWER_FORBIDDEN_TOP_CLICKS.get(0), 0);
        InventoryDragEvent dragEvent = drag(stranger, view, Set.of(0));
        listener.onInventoryOpen(openEvent);
        listener.onInventoryClick(clickEvent);
        listener.onInventoryDrag(dragEvent);

        verify(openEvent, never()).setCancelled(anyBoolean());
        verify(clickEvent, never()).setCancelled(anyBoolean());
        verify(dragEvent, never()).setCancelled(anyBoolean());
    }
}
