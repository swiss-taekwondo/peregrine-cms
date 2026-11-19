package com.peregrine.admin.resource;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Runs Smart Publish batches one at a time on a single background thread. Requests for a tenant that arrive while a
 * batch is queued or running are combined into that tenant's next batch, so a burst of publishes costs at most two
 * site renders. Batches of different tenants run one after another, never in parallel, so publishing uses at most
 * one render's worth of CPU. Synchronous replication takes {@link #exclusiveLock()} so it never runs inside a batch.
 * State is in memory: queued requests are failed when the component stops.
 */
@Component(service = SmartPublishCoordinator.class)
public class SmartPublishCoordinator {

    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED }

    @FunctionalInterface
    public interface BatchProcessor {
        void process(List<Object> payloads) throws Exception;
    }

    public static final class Ticket {
        private final String id = UUID.randomUUID().toString();
        private final String userId;
        private final String sourcePath;
        private volatile State state = State.QUEUED;
        private volatile String message = "";
        private volatile int batchSize = 1;
        private volatile String batchId = "";
        private volatile List<String> batchPaths = List.of();
        private volatile long finishedAt;

        Ticket(String userId, String sourcePath) {
            this.userId = userId;
            this.sourcePath = sourcePath;
        }

        public String getId() { return id; }
        public String getUserId() { return userId; }
        public String getSourcePath() { return sourcePath; }
        public State getState() { return state; }
        public String getMessage() { return message; }
        /** Number of publish requests combined into this ticket's batch. */
        public int getBatchSize() { return batchSize; }
        /** Id of the batch this ticket ran in, shared by all tickets of that batch. */
        public String getBatchId() { return batchId; }
        /** Paths of all requests in this ticket's batch, set when the batch finishes. */
        public List<String> getBatchPaths() { return batchPaths; }
    }

    private static final class Batch {
        final String id = UUID.randomUUID().toString();
        final BatchProcessor processor;
        final List<Object> payloads = new ArrayList<>();
        final List<Ticket> tickets = new ArrayList<>();

        Batch(BatchProcessor processor) {
            this.processor = processor;
        }
    }

    private static final long RETENTION_MILLIS = TimeUnit.HOURS.toMillis(1);

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Lock runLock = new ReentrantLock(true);
    private final Map<String, Batch> pending = new HashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private ExecutorService worker;

    @Activate
    void activate() {
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "smart-publish");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Deactivate
    void deactivate() {
        worker.shutdownNow();
        synchronized (this) {
            for (Batch batch : pending.values()) {
                finish(batch, State.FAILED, "Publishing was cancelled because the publishing service stopped. Publish again.");
                batch.payloads.stream().filter(AutoCloseable.class::isInstance).forEach(p -> {
                    try {
                        ((AutoCloseable) p).close();
                    } catch (Exception e) {
                        logger.debug("Failed to close an abandoned Smart Publish request", e);
                    }
                });
            }
            pending.clear();
        }
    }

    /**
     * Adds a request to the tenant's next batch. The processor of the batch's first request runs the batch.
     * Throws {@link java.util.concurrent.RejectedExecutionException} when the coordinator has stopped.
     */
    public synchronized Ticket submit(String tenant, String userId, String sourcePath, Object payload, BatchProcessor processor) {
        long now = System.currentTimeMillis();
        tickets.values().removeIf(t -> t.finishedAt > 0 && t.finishedAt < now - RETENTION_MILLIS);
        Batch batch = pending.get(tenant);
        if (batch == null) {
            batch = new Batch(processor);
            worker.execute(() -> run(tenant));
            pending.put(tenant, batch);
        }
        Ticket ticket = new Ticket(userId, sourcePath);
        ticket.batchId = batch.id;
        batch.payloads.add(payload);
        batch.tickets.add(ticket);
        for (Ticket t : batch.tickets) {
            t.batchSize = batch.tickets.size();
        }
        tickets.put(ticket.id, ticket);
        return ticket;
    }

    public Optional<Ticket> getTicket(String id) {
        return Optional.ofNullable(id).map(tickets::get);
    }

    /** Held while a batch runs. Synchronous replication takes it so it never runs during a batch. */
    public Lock exclusiveLock() {
        return runLock;
    }

    private void run(String tenant) {
        Batch batch;
        synchronized (this) {
            batch = pending.remove(tenant);
        }
        if (batch == null) {
            return;
        }
        batch.tickets.forEach(t -> t.state = State.RUNNING);
        State outcome = State.FAILED;
        String message;
        runLock.lock();
        try {
            batch.processor.process(batch.payloads);
            outcome = State.SUCCEEDED;
            int others = batch.payloads.size() - 1;
            message = others > 0 ? "Published together with " + others + " other request(s)" : "Published";
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Smart Publish batch for tenant {} failed", tenant, e);
            message = Objects.toString(e.getMessage(), e.getClass().getSimpleName());
        } finally {
            runLock.unlock();
        }
        finish(batch, outcome, message);
    }

    private static void finish(Batch batch, State state, String message) {
        long now = System.currentTimeMillis();
        List<String> paths = batch.tickets.stream().map(t -> t.sourcePath).distinct().collect(Collectors.toList());
        for (Ticket t : batch.tickets) {
            t.batchPaths = paths;
            t.message = message;
            t.finishedAt = now;
            t.state = state;
        }
    }
}
