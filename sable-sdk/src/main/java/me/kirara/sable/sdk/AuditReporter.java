package me.kirara.sable.sdk;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 异步审计上报器 — 业务线程仅入队，后台线程批量上报，避免阻塞主流程。
 */
public class AuditReporter implements AutoCloseable {

    private final SableAuditClient client;
    private final BlockingQueue<AuditEvent> queue;
    private volatile boolean running;
    private Thread worker;

    public AuditReporter(SableAuditClient client) {
        this(client, 4096);
    }

    public AuditReporter(SableAuditClient client, int capacity) {
        this.client = client;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /** 启动后台上报线程。 */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::loop, "sable-audit-reporter");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 采集一条审计事件（非阻塞）。
     *
     * @return 入队是否成功（队列满时返回 false，便于调用方降级）
     */
    public boolean report(AuditEvent event) {
        return queue.offer(event);
    }

    private void loop() {
        while (running) {
            try {
                AuditEvent event = queue.poll(500, TimeUnit.MILLISECONDS);
                if (event != null) {
                    client.report(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }
}
