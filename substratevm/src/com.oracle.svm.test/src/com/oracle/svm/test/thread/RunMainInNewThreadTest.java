/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.test.thread;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.test.NativeImageBuildArgs;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

/**
 * Verifies Java main thread semantics when application main runs on a launcher-created thread.
 */
@NativeImageBuildArgs({
                "-H:+UnlockExperimentalVMOptions",
                "-H:+RunMainInNewThread",
                "-H:-UnlockExperimentalVMOptions"
})
public class RunMainInNewThreadTest {
    private static final String THREAD_START_EVENT = "jdk.ThreadStart";
    private static final String THREAD_END_EVENT = "jdk.ThreadEnd";
    private static final String JFR_WORKER_NAME = "RunMainInNewThreadTest-jfr-worker";

    /**
     * Checks the management counters before test methods create any worker threads.
     */
    @BeforeClass
    public static void checkMainHandoffThreadLifecycle() {
        if (ImageInfo.inImageRuntimeCode()) {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            long totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();
            int liveThreadCount = threadMXBean.getThreadCount();
            Assert.assertEquals("Unexpected completed thread lifecycle before test startup.", liveThreadCount, totalStartedThreadCount);
        }
    }

    /**
     * Checks that application code observes the normal non-daemon {@code main} thread.
     */
    @Test
    public void mainThreadKeepsJavaSemantics() throws InterruptedException {
        Thread current = Thread.currentThread();
        Assert.assertEquals("main", current.getName());
        Assert.assertFalse(current.isDaemon());

        AtomicBoolean childDaemon = new AtomicBoolean(true);
        Thread child = new Thread(() -> childDaemon.set(Thread.currentThread().isDaemon()));

        /* A child thread created by main should inherit non-daemon status. */
        Assert.assertFalse(child.isDaemon());
        child.start();
        child.join();
        Assert.assertFalse(childDaemon.get());
    }

    /**
     * Verifies that the unmanaged launcher thread does not become an extra Java thread when main is
     * started on a fresh native thread.
     */
    @Test
    public void testMainThreadIdentityAndVisibility() {
        Assume.assumeTrue("native image runtime only", ImageInfo.inImageRuntimeCode());

        Thread mainThread = Thread.currentThread();
        assertMainThread(mainThread);

        ThreadGroup mainGroup = mainThread.getThreadGroup();
        Assert.assertEquals("Unexpected visible non-daemon threads.",
                        List.of(mainThread), visibleNonDaemonThreads());
        Assert.assertEquals("Unexpected direct non-daemon threads in the main group.",
                        List.of(mainThread), directNonDaemonThreadsIn(mainGroup));
    }

    /**
     * Checks the Java-level properties used to recognize the preallocated main thread.
     */
    private static void assertMainThread(Thread thread) {
        Assert.assertEquals("Unexpected main thread name.", "main", thread.getName());
        Assert.assertEquals("Unexpected main thread state.", Thread.State.RUNNABLE, thread.getState());
        Assert.assertFalse("Unexpected main thread daemon status.", thread.isDaemon());
        Assert.assertEquals("Unexpected main thread group.", "main", thread.getThreadGroup().getName());
        Assert.assertEquals("Unexpected parent thread group.", "system", thread.getThreadGroup().getParent().getName());
    }

    /**
     * Lists non-daemon Java threads exposed by stack walking.
     */
    private static List<Thread> visibleNonDaemonThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                        .filter(thread -> !thread.isDaemon())
                        .toList();
    }

    /**
     * Enumerates direct non-daemon Java thread entries in {@code group} without recursing into
     * child groups.
     */
    private static List<Thread> directNonDaemonThreadsIn(ThreadGroup group) {
        Thread[] threads = new Thread[Math.max(4, group.activeCount() + 1)];
        int count = group.enumerate(threads, false);

        /*
         * The active count is only an estimate. Retry with a larger array if the first result may
         * have been truncated.
         */
        while (count == threads.length) {
            threads = new Thread[threads.length * 2];
            count = group.enumerate(threads, false);
        }

        return Arrays.stream(threads, 0, count)
                        .filter(thread -> !thread.isDaemon())
                        .toList();
    }

    /**
     * Checks that main can execute VM operations while the launcher thread waits for it to finish.
     */
    @Test
    public void mainThreadCanReachSafepoint() {
        System.gc();
    }

    /**
     * Checks that JFR thread listeners still see a complete worker lifecycle.
     */
    @Test
    public void jfrThreadHooksRecordThreadLifecycle() throws Throwable {
        Assume.assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || HasJfrSupport.get());

        Path recordingPath = Files.createTempFile(RunMainInNewThreadTest.class.getSimpleName(), ".jfr");
        try (Recording recording = new Recording()) {
            recording.setDestination(recordingPath);
            recording.enable(THREAD_START_EVENT).withThreshold(Duration.ZERO);
            recording.enable(THREAD_END_EVENT).withThreshold(Duration.ZERO);
            recording.start();

            Thread worker = new Thread(() -> {
                /* The lifecycle hooks are the tested behavior; no worker-side action is required. */
            }, JFR_WORKER_NAME);
            worker.start();
            worker.join();

            recording.stop();
        }
        try {
            validateJfrThreadLifecycle(RecordingFile.readAllEvents(recordingPath));
        } finally {
            Files.deleteIfExists(recordingPath);
        }
    }

    private static void validateJfrThreadLifecycle(List<RecordedEvent> events) {
        boolean foundStart = false;
        boolean foundEnd = false;
        for (RecordedEvent event : events) {
            String eventName = event.getEventType().getName();
            if (THREAD_START_EVENT.equals(eventName)) {
                foundStart |= eventReferencesThread(event, JFR_WORKER_NAME);
            } else if (THREAD_END_EVENT.equals(eventName)) {
                foundEnd |= eventReferencesThread(event, JFR_WORKER_NAME);
            }
        }
        Assert.assertTrue("Missing JFR ThreadStart event for " + JFR_WORKER_NAME, foundStart);
        Assert.assertTrue("Missing JFR ThreadEnd event for " + JFR_WORKER_NAME, foundEnd);
    }

    private static boolean eventReferencesThread(RecordedEvent event, String threadName) {
        RecordedThread eventThread = event.getThread();
        return hasName(eventThread, threadName) || threadFieldHasName(event, "eventThread", threadName) || threadFieldHasName(event, "thread", threadName);
    }

    private static boolean threadFieldHasName(RecordedEvent event, String fieldName, String threadName) {
        try {
            return hasName(event.getThread(fieldName), threadName);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean hasName(RecordedThread thread, String threadName) {
        return thread != null && threadName.equals(thread.getJavaName());
    }

    /**
     * Checks that management thread listeners still account for started and finished workers.
     */
    @Test
    public void managementThreadHooksTrackThreadLifecycle() throws InterruptedException {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long startedBefore = threadMXBean.getTotalStartedThreadCount();
        int liveBefore = threadMXBean.getThreadCount();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch finishWorker = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            workerStarted.countDown();
            await(finishWorker);
        }, "RunMainInNewThreadTest-management-worker");
        worker.start();
        workerStarted.await();

        waitUntilTrue(() -> threadMXBean.getTotalStartedThreadCount() >= startedBefore + 1);
        waitUntilTrue(() -> threadMXBean.getThreadCount() >= liveBefore + 1);
        int liveWithWorker = threadMXBean.getThreadCount();

        finishWorker.countDown();
        worker.join();

        waitUntilTrue(() -> threadMXBean.getThreadCount() <= liveWithWorker - 1);
    }

    private static void waitUntilTrue(BooleanSupplier supplier) throws InterruptedException {
        long timeoutNanos = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (!supplier.getAsBoolean()) {
            if (System.nanoTime() > timeoutNanos) {
                Assert.fail("Timed out waiting for condition.");
            }
            Thread.sleep(10);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
