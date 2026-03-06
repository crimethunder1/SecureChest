package de.SecureChest.plugin.ui;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class UndoManager {

    private final int maxHistory;

    private final Map<UUID, Deque<UndoSnapshot>> stacks = new ConcurrentHashMap<>();

    public UndoManager(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
    }

    public void set(UUID player, UndoSnapshot snapshot) {
        Deque<UndoSnapshot> stack = stacks.computeIfAbsent(player, u -> new ArrayDeque<>());
        stack.addFirst(snapshot);

        while (stack.size() > maxHistory) {
            stack.removeLast();
        }
    }

    public Optional<UndoSnapshot> get(UUID player) {
        return peekValid(player);
    }

    public Optional<UndoSnapshot> consume(UUID player) {
        Deque<UndoSnapshot> stack = stacks.get(player);
        if (stack == null) return Optional.empty();
        long now = System.currentTimeMillis();

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

    private Optional<UndoSnapshot> peekValid(UUID player) {
        Deque<UndoSnapshot> stack = stacks.get(player);
        if (stack == null) return Optional.empty();
        long now = System.currentTimeMillis();

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
