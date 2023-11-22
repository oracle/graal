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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.util.TimeUtils;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestThreadCPULoadEvent extends JfrRecordingTest {
    private static final int TIMEOUT = 10000;
    private static final String THREAD_NAME_1 = "Thread-1";
    private static final String THREAD_NAME_2 = "Thread-2";
    private static final String THREAD_NAME_3 = "Thread-3";

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.ThreadCPULoad.getName()};
        Recording recording = startRecording(events);

        WeakReference<Thread> thread1 = createAndStartBusyWaitThread(THREAD_NAME_1, 10, 250);
        WeakReference<Thread> thread2 = createAndStartBusyWaitThread(THREAD_NAME_2, 250, 10);
        Thread thread3 = createAndStartBusyWaitThread(THREAD_NAME_3, 20, TIMEOUT).get();

        /* For threads 1 and 2, the event is emitted when the thread exits. */
        waitUntilCollected(thread1);
        waitUntilCollected(thread2);

        /* For thread 3, the event is emitted upon chunk end. */
        stopRecording(recording, TestThreadCPULoadEvent::validateEvents);

        Assert.assertTrue(thread3.isAlive());
        thread3.interrupt();
    }

    private static void validateEvents(List<RecordedEvent> events) {
        Map<String, Float> userTimes = new HashMap<>();
        Map<String, Float> cpuTimes = new HashMap<>();

        for (RecordedEvent e : events) {
            String threadName = e.getThread().getJavaName();
            float userTime = e.<Float> getValue("user");
            float systemTime = e.<Float> getValue("system");
            assertTrue("User time is outside 0..1 range", 0.0 <= userTime && userTime <= 1.0);
            assertTrue("System time is outside 0..1 range", 0.0 <= systemTime && systemTime <= 1.0);
            userTimes.put(threadName, userTime);
            cpuTimes.put(threadName, userTime + systemTime);
        }
        assertTrue(cpuTimes.containsKey(THREAD_NAME_3));
        assertTrue(userTimes.get(THREAD_NAME_1) < userTimes.get(THREAD_NAME_2));
        assertTrue(cpuTimes.get(THREAD_NAME_1) < cpuTimes.get(THREAD_NAME_2));
    }

    private static WeakReference<Thread> createAndStartBusyWaitThread(String name, int busyMs, int idleMs) {
        Thread thread = new Thread(() -> {
            busyWait(busyMs);
            sleep(idleMs);
        });
        thread.setName(name);
        thread.start();
        return new WeakReference<>(thread);
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
