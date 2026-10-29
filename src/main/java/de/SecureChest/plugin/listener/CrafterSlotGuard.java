package de.SecureChest.plugin.listener;

import org.bukkit.inventory.view.CrafterView;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class CrafterSlotGuard {

    static final int CRAFTER_SLOT_COUNT = 9;

    private final Map<UUID, boolean[]> disabledSlotsByViewer = new ConcurrentHashMap<>();

    void remember(UUID viewerUuid, CrafterView view) {
        boolean[] disabledSlots = new boolean[CRAFTER_SLOT_COUNT];
        for (int slot = 0; slot < CRAFTER_SLOT_COUNT; slot++) {
            disabledSlots[slot] = view.isSlotDisabled(slot);
        }
        disabledSlotsByViewer.put(viewerUuid, disabledSlots);
    }

    void restore(UUID viewerUuid, CrafterView view, int rawSlot) {
        if (rawSlot < 0 || rawSlot >= CRAFTER_SLOT_COUNT) return;
        boolean[] disabledSlots = disabledSlotsByViewer.get(viewerUuid);
        if (disabledSlots == null) return;
        if (view.isSlotDisabled(rawSlot) != disabledSlots[rawSlot]) {
            view.setSlotDisabled(rawSlot, disabledSlots[rawSlot]);
        }
    }

    void forget(UUID viewerUuid) {
        disabledSlotsByViewer.remove(viewerUuid);
    }
}
