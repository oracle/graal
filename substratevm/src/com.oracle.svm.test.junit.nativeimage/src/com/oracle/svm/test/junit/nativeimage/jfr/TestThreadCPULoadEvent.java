/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, BELLSOFT. All rights reserved.
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

package com.oracle.svm.test.jfr;

import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.graalvm.collections.EconomicSet;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.shared.util.TimeUtils;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestThreadCPULoadEvent extends JfrRecordingTest {
    private static final int TIMEOUT = 30000;
    private static final String EXITED_THREAD_NAME = "Exited Thread";
    private static final String RUNNING_THREAD_NAME = "Running Thread";

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.ThreadCPULoad.getName()};
        Recording recording = startRecording(events);

        /* Start a thread and wait until it exits. The event is emitted when the thread exits. */
        WeakReference<Thread> exitedThread = new WeakReference<>(createAndStartBusyWaitThread(EXITED_THREAD_NAME, 20, 0));
        waitUntilCollected(exitedThread);

        /* Start a thread and keep it running. The event is emitted upon chunk end */
        CountDownLatch busyWaitFinished = new CountDownLatch(1);
        Thread runningThread = createAndStartBusyWaitThread(RUNNING_THREAD_NAME, 20, TIMEOUT, busyWaitFinished);
        try {
            busyWaitFinished.await();

            stopRecording(recording, TestThreadCPULoadEvent::validateEvents);
            Assert.assertTrue(runningThread.isAlive());
        } finally {
            runningThread.interrupt();
            runningThread.join();
        }
    }

    private static void validateEvents(List<RecordedEvent> events) {
        EconomicSet<String> threads = EconomicSet.create(List.of(EXITED_THREAD_NAME, RUNNING_THREAD_NAME));

        for (RecordedEvent e : events) {
            String threadName = e.getThread().getJavaName();
            threads.remove(threadName);

            float userLoad = e.<Float> getValue("user");
            float systemLoad = e.<Float> getValue("system");
            assertTrue("User load is outside 0..1 range", 0.0 <= userLoad && userLoad <= 1.0);
            assertTrue("System load is outside 0..1 range", 0.0 <= systemLoad && systemLoad <= 1.0);
        }

        assertTrue("Events for the following threads are missing: " + threads, threads.isEmpty());
    }

    private static Thread createAndStartBusyWaitThread(String name, int busyMs, int idleMs) {
        return createAndStartBusyWaitThread(name, busyMs, idleMs, null);
    }

    private static Thread createAndStartBusyWaitThread(String name, int busyMs, int idleMs, CountDownLatch busyWaitFinished) {
        Thread thread = new Thread(() -> {
            try {
                busyWait(busyMs);
            } finally {
                if (busyWaitFinished != null) {
                    busyWaitFinished.countDown();
                }
            }
            sleep(idleMs);
        });
        thread.setName(name);
        thread.start();
        return thread;
    }

    private static void busyWait(long waitMs) {
        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        long timeout = System.currentTimeMillis() + TIMEOUT;
        while (timeout > System.currentTimeMillis() &&
                        (mxBean.getCurrentThreadUserTime() < TimeUtils.millisToNanos(waitMs) || mxBean.getCurrentThreadCpuTime() < TimeUtils.millisToNanos(waitMs))) {
            /* Busy wait. */
        }
    }

    private static void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Waits until the thread object was garbage collected. Thread.join() is not sufficient because
     * it may return before the ThreadCPULoad events are emitted in
     * JfrThreadLocal.afterThreadExit().
     */
    private static void waitUntilCollected(WeakReference<Thread> thread) throws InterruptedException {
        join(thread);

        while (!thread.refersTo(null)) {
            Thread.sleep(100);
            System.gc();
        }
    }

    private static void join(WeakReference<Thread> thread) throws InterruptedException {
        Thread t = thread.get();
        if (t != null) {
            t.join();
        }
    }
}
