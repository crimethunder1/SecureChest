package de.SecureChest.plugin.ui;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FEATURE: Expanded undo history — stores a configurable stack of snapshots per player
 * instead of just one. Each unlock operation pushes a new entry; restoreUndo pops the
 * most recent unexpired one.
 */
public final class UndoManager {

    private final int maxHistory;
    /** Player UUID → deque of snapshots, newest at the front. */
    private final Map<UUID, Deque<UndoSnapshot>> stacks = new ConcurrentHashMap<>();

    public UndoManager(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
    }

    /** Push a new snapshot onto the player's undo stack. */
    public void set(UUID player, UndoSnapshot snapshot) {
        Deque<UndoSnapshot> stack = stacks.computeIfAbsent(player, u -> new ArrayDeque<>());
        stack.addFirst(snapshot);
        // Trim to max history size
        while (stack.size() > maxHistory) {
            stack.removeLast();
        }
    }

    /** Peek at the most recent valid (non-expired) snapshot without removing it. */
    public Optional<UndoSnapshot> get(UUID player) {
        return peekValid(player);
    }

    /** Pop and return the most recent valid snapshot. Expired entries are purged. */
    public Optional<UndoSnapshot> consume(UUID player) {
        Deque<UndoSnapshot> stack = stacks.get(player);
        if (stack == null) return Optional.empty();
        long now = System.currentTimeMillis();
        // Drain expired entries from the front
        while (!stack.isEmpty() && now > stack.peekFirst().expiresAt()) {
            stack.pollFirst();
        }
        if (stack.isEmpty()) {
            stacks.remove(player);
            return Optional.empty();
        }
        UndoSnapshot snap = stack.pollFirst();
        if (stack.isEmpty()) stacks.remove(player);
        return Optional.of(snap);
    }

    /** How many valid (non-expired) snapshots are queued for this player. */
    public int count(UUID player) {
        Deque<UndoSnapshot> stack = stacks.get(player);
        if (stack == null) return 0;
        long now = System.currentTimeMillis();
        int c = 0;
        for (UndoSnapshot s : stack) {
            if (now <= s.expiresAt()) c++;
        }
        return c;
    }

    public boolean hasUndo(UUID player) {
        return peekValid(player).isPresent();
    }

    public void clear(UUID player) {
        stacks.remove(player);
    }

    // -------------------------------------------------------------------------

    private Optional<UndoSnapshot> peekValid(UUID player) {
        Deque<UndoSnapshot> stack = stacks.get(player);
        if (stack == null) return Optional.empty();
        long now = System.currentTimeMillis();
        // Drain expired entries from the front
        while (!stack.isEmpty() && now > stack.peekFirst().expiresAt()) {
            stack.pollFirst();
        }
        if (stack.isEmpty()) {
            stacks.remove(player);
            return Optional.empty();
        }
        return Optional.of(stack.peekFirst());
    }
}
