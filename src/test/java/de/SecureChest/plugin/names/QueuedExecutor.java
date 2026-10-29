package de.SecureChest.plugin.names;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

final class QueuedExecutor implements Executor {

    private static final ThreadLocal<Boolean> RUNNING_ASYNC = ThreadLocal.withInitial(() -> false);

    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private final boolean async;

    QueuedExecutor(boolean async) {
        this.async = async;
    }

    static boolean isRunningAsync() {
        return RUNNING_ASYNC.get();
    }

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
    }

    int pending() {
        return tasks.size();
    }

    void runAll() {
        while (!tasks.isEmpty()) {
            Runnable task = tasks.poll();
            boolean previous = RUNNING_ASYNC.get();
            RUNNING_ASYNC.set(async);
            try {
                task.run();
            } finally {
                RUNNING_ASYNC.set(previous);
            }
        }
    }
}
