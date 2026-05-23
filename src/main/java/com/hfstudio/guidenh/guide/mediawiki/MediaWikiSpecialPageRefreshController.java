package com.hfstudio.guidenh.guide.mediawiki;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.github.bsideup.jabel.Desugar;

public class MediaWikiSpecialPageRefreshController {

    private final AtomicLong revision = new AtomicLong(1L);
    private final AtomicLong queuedRevision = new AtomicLong(Long.MIN_VALUE);
    private final ConcurrentLinkedQueue<RefreshTask> pendingTasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean workerScheduled = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "guidenh-mediawiki-refresh");
        thread.setDaemon(true);
        return thread;
    });

    public long currentRevision() {
        return revision.get();
    }

    public long invalidate() {
        return revision.incrementAndGet();
    }

    public boolean isCurrent(long expectedRevision) {
        return revision.get() == expectedRevision;
    }

    public void requestRefresh(long expectedRevision, Runnable task) {
        if (task == null) {
            return;
        }
        pendingTasks.add(new RefreshTask(expectedRevision, task));
        queuedRevision.accumulateAndGet(expectedRevision, Math::max);
        if (!workerScheduled.compareAndSet(false, true)) {
            return;
        }
        executor.submit(() -> {
            try {
                drainPendingTasks();
            } finally {
                workerScheduled.set(false);
                if (!pendingTasks.isEmpty() && workerScheduled.compareAndSet(false, true)) {
                    executor.submit(this::drainPendingTasks);
                }
            }
        });
    }

    private void drainPendingTasks() {
        while (true) {
            RefreshTask refreshTask = pendingTasks.poll();
            if (refreshTask == null) {
                return;
            }
            long latestQueuedRevision = queuedRevision.get();
            if (refreshTask.revision() < latestQueuedRevision || !isCurrent(refreshTask.revision())) {
                continue;
            }
            refreshTask.task()
                .run();
        }
    }

    @Desugar
    private record RefreshTask(long revision, Runnable task) {}
}
