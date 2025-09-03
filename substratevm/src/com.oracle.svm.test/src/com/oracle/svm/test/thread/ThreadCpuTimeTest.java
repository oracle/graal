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
package com.oracle.svm.test.thread;

import static org.junit.Assert.assertTrue;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class ThreadCpuTimeTest {
    private static final int TIMEOUT = 10000;
    private final AtomicReference<Throwable> exception = new AtomicReference<>();
    private final CountDownLatch testLatch = new CountDownLatch(1);
    private final CountDownLatch threadLatch = new CountDownLatch(1);

    @Test
    public void testThreadCpuTime() throws Throwable {
        Thread thread;
        try {
            thread = new Thread(new ThreadCpuTimeRunnable());
            thread.start();

            testLatch.await();
            checkThreadCpuTime(getThreadId(thread), false);
        } finally {
            threadLatch.countDown();
        }

        thread.join();
        if (exception.get() != null) {
            throw exception.get();
        }
    }

    private static void checkThreadCpuTime(long tid, boolean checkCurrentThread) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        assertTrue("Thread CPU time is not supported",
                        threadMXBean.isThreadCpuTimeSupported());

        if (checkCurrentThread) {
            assertTrue("Current thread CPU time is less or equal to zero",
                            threadMXBean.getCurrentThreadCpuTime() > 0);
            assertTrue("Current thread user time is less or equal to zero",
                            threadMXBean.getCurrentThreadUserTime() > 0);
        }

        assertTrue("Thread CPU time is less or equal to zero",
                        threadMXBean.getThreadCpuTime(tid) > 0);
        assertTrue("Thread user time is less or equal to zero",
                        threadMXBean.getThreadUserTime(tid) > 0);
    }

    /* Can be removed when we drop the JDK 17 support. */
    @SuppressWarnings("deprecation")
    private static long getThreadId(Thread thread) {
        return thread.getId();
    }

    private final class ThreadCpuTimeRunnable implements Runnable {
        @Override
        public void run() {
            try {
                work();
            } catch (Throwable e) {
                exception.set(e);
            }
        }
    }

    private void work() throws InterruptedException {
        try {
            ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
            long timeout = System.currentTimeMillis() + TIMEOUT;
            while (timeout > System.currentTimeMillis() &&
                            (mxBean.getCurrentThreadCpuTime() == 0 || mxBean.getCurrentThreadUserTime() == 0)) {
                /* Nothing to do. */
            }
            checkThreadCpuTime(getThreadId(Thread.currentThread()), true);
        } finally {
            testLatch.countDown();
        }

        threadLatch.await();
    }
}
