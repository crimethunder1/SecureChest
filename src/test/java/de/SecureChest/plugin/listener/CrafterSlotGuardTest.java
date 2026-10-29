package de.SecureChest.plugin.listener;

import org.bukkit.inventory.view.CrafterView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CrafterSlotGuard: Zustand der Crafter-Felder fuer Viewer merken und zuruecksetzen")
class CrafterSlotGuardTest {

    private final UUID viewer = UUID.randomUUID();
    private final UUID otherViewer = UUID.randomUUID();

    private static CrafterView viewBackedBy(boolean[] disabledSlots) {
        CrafterView view = mock(CrafterView.class);
        when(view.isSlotDisabled(anyInt())).thenAnswer(invocation -> disabledSlots[(int) invocation.getArgument(0)]);
        doAnswer(invocation -> {
            disabledSlots[(int) invocation.getArgument(0)] = invocation.getArgument(1);
            return null;
        }).when(view).setSlotDisabled(anyInt(), anyBoolean());
        return view;
    }

    @Test
    @DisplayName("Nur das angeklickte Feld wird auf den gemerkten Zustand gesetzt")
    void restoresOnlyClickedSlot() {
        boolean[] disabledSlots = new boolean[CrafterSlotGuard.CRAFTER_SLOT_COUNT];
        disabledSlots[0] = true;
        CrafterView view = viewBackedBy(disabledSlots);
        CrafterSlotGuard guard = new CrafterSlotGuard();
        guard.remember(viewer, view);

        disabledSlots[0] = false;
        disabledSlots[3] = true;
        guard.restore(viewer, view, 3);

        assertFalse(disabledSlots[3]);
        assertFalse(disabledSlots[0], "anderes Feld bleibt unberuehrt");
        verify(view, never()).setSlotDisabled(0, true);
    }

    @Test
    @DisplayName("Unveraendertes Feld wird nicht geschrieben")
    void unchangedSlotIsNotWritten() {
        boolean[] disabledSlots = new boolean[CrafterSlotGuard.CRAFTER_SLOT_COUNT];
        CrafterView view = viewBackedBy(disabledSlots);
        CrafterSlotGuard guard = new CrafterSlotGuard();
        guard.remember(viewer, view);

        guard.restore(viewer, view, 4);

        verify(view, never()).setSlotDisabled(anyInt(), anyBoolean());
    }

    @Test
    @DisplayName("Felder ausserhalb des Rasters, unbekannte Viewer und vergessene Viewer werden ignoriert")
    void ignoresOutsideAndUnknown() {
        boolean[] disabledSlots = new boolean[CrafterSlotGuard.CRAFTER_SLOT_COUNT];
        CrafterView view = viewBackedBy(disabledSlots);
        CrafterSlotGuard guard = new CrafterSlotGuard();
        guard.remember(viewer, view);
        disabledSlots[1] = true;

        guard.restore(viewer, view, -999);
        guard.restore(viewer, view, CrafterSlotGuard.CRAFTER_SLOT_COUNT);
        guard.restore(viewer, view, 30);
        guard.restore(otherViewer, view, 1);
        guard.forget(viewer);
        guard.restore(viewer, view, 1);

        assertTrue(disabledSlots[1]);
        verify(view, never()).setSlotDisabled(anyInt(), anyBoolean());
    }
}
