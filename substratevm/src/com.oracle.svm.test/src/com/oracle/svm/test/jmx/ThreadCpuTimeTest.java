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
package com.oracle.svm.test.jmx;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ThreadCpuTimeTest {

    private static final int MILLIS = 20;
    private static final AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    private static final CountDownLatch testLatch = new CountDownLatch(1);
    private static final CountDownLatch threadLatch = new CountDownLatch(1);

    @Before
    public void init() {
        Thread.setDefaultUncaughtExceptionHandler((Thread t, Throwable e) -> {
            if (exceptionReference.get() == null) {
                exceptionReference.set(e);
            } else {
                e.printStackTrace();
            }
        });
    }

    @After
    public void checkExceptions() throws Throwable {
        if (exceptionReference.get() != null) {
            throw exceptionReference.get();
        }
    }

    @Test
    public void testThreadCpuTime() throws Exception {
        try {
            Thread thread = new Thread(new ThreadCpuTimeRunnable());
            thread.start();
            testLatch.await();
            checkThreadCpuTime(thread.threadId(), false);
        } finally {
            threadLatch.countDown();
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

    private static class ThreadCpuTimeRunnable implements Runnable {

        public void run() {
            try {
                long time = System.currentTimeMillis() + MILLIS;
                long count = 0;
                do {
                    count++;
                } while (time > System.currentTimeMillis());

                assertTrue(count > 0);

                time = System.currentTimeMillis() + MILLIS;
                do {
                    writeToFile();
                } while (time > System.currentTimeMillis());

                checkThreadCpuTime(Thread.currentThread().threadId(), true);
            } finally {
                testLatch.countDown();
            }

            try {
                threadLatch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


        private static void writeToFile() {
            int fileSize = 1000;
            try {
                File temp = File.createTempFile("ThreadCpuTimeTestData_", ".tmp");
                temp.deleteOnExit();
                try (RandomAccessFile file = new RandomAccessFile(temp, "rw")) {
                    for (int i = 0; i < fileSize; i++) {
                        file.writeByte(i);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
