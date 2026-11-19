package com.peregrine.admin.resource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmartPublishCoordinatorTest {

    private final SmartPublishCoordinator coordinator = new SmartPublishCoordinator();

    @Before
    public void setUp() {
        coordinator.activate();
    }

    @After
    public void tearDown() {
        coordinator.deactivate();
    }

    @Test
    public void requestsDuringARunAreCombinedIntoOneBatchAndNeverOverlap() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger running = new AtomicInteger();
        AtomicBoolean overlapped = new AtomicBoolean();
        List<List<Object>> batches = Collections.synchronizedList(new ArrayList<>());
        SmartPublishCoordinator.BatchProcessor processor = payloads -> {
            overlapped.compareAndSet(false, running.incrementAndGet() > 1);
            batches.add(new ArrayList<>(payloads));
            if (payloads.contains("a")) {
                firstStarted.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
            }
            running.decrementAndGet();
        };

        SmartPublishCoordinator.Ticket a = coordinator.submit("site", "u", "/a", "a", processor);
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        SmartPublishCoordinator.Ticket b = coordinator.submit("site", "u", "/b", "b", processor);
        SmartPublishCoordinator.Ticket c = coordinator.submit("other-site", "u", "/c", "c", processor);
        SmartPublishCoordinator.Ticket d = coordinator.submit("site", "u", "/d", "d", processor);
        assertEquals(SmartPublishCoordinator.State.QUEUED, b.getState());
        releaseFirst.countDown();

        awaitFinished(a, b, c, d);
        assertTrue(!overlapped.get());
        assertEquals(List.of(List.of("a"), List.of("b", "d"), List.of("c")), batches);
        assertEquals(2, d.getBatchSize());
        assertEquals(List.of("/b", "/d"), d.getBatchPaths());
        assertEquals(b.getBatchId(), d.getBatchId());
        assertEquals(SmartPublishCoordinator.State.SUCCEEDED, d.getState());
    }

    @Test
    public void failureIsReportedOnEveryTicketOfTheBatch() throws Exception {
        SmartPublishCoordinator.Ticket t = coordinator.submit("site", "u", "/a", "a", payloads -> {
            throw new Exception("HTTP 500 - webhook down");
        });
        awaitFinished(t);
        assertEquals(SmartPublishCoordinator.State.FAILED, t.getState());
        assertEquals("HTTP 500 - webhook down", t.getMessage());
    }

    @Test
    public void exclusiveLockIsUnavailableWhileABatchRuns() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SmartPublishCoordinator.Ticket t = coordinator.submit("site", "u", "/a", "a", payloads -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(!coordinator.exclusiveLock().tryLock(50, TimeUnit.MILLISECONDS));
        release.countDown();
        awaitFinished(t);
        assertTrue(coordinator.exclusiveLock().tryLock(1, TimeUnit.SECONDS));
        coordinator.exclusiveLock().unlock();
    }

    private static void awaitFinished(SmartPublishCoordinator.Ticket... tickets) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        for (SmartPublishCoordinator.Ticket t : tickets) {
            while (t.getState() == SmartPublishCoordinator.State.QUEUED || t.getState() == SmartPublishCoordinator.State.RUNNING) {
                assertTrue("still " + t.getState(), System.currentTimeMillis() < deadline);
                Thread.sleep(20);
            }
        }
    }
}
