/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import static java.lang.Math.abs;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import jdk.jfr.consumer.RecordedThread;
import org.junit.Test;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

public class TestJavaMonitorEnter extends JfrTest {
    private final int threads = 10;
    private static final int MILLIS = 60;
    static Object monitor = new Object();

    private static Queue<String> orderedWaiterNames = new LinkedList<>();

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.JavaMonitorEnter"};
    }

    @Override
    public void analyzeEvents() {
        List<RecordedEvent> events;
        try {
            events = getEvents("jdk.JavaMonitorEnter");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int count = 0;
        orderedWaiterNames.poll(); // first worker does not wait
        String waiterName = orderedWaiterNames.poll();
        Long prev = 0L;
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread> getValue("eventThread").getJavaName();
            if (event.getEventType().getName().equals("jdk.JavaMonitorEnter") && isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration()) && waiterName.equals(eventThread)) {
                Long duration = event.getDuration().toMillis();
                assertTrue("Durations not as expected ", abs(duration - prev - MILLIS) < msTolerance);
                count++;
                waiterName = orderedWaiterNames.poll();
                prev = duration;
            }
        }
        assertTrue("Wrong number of Java Monitor Enter Events " + count, count == threads - 1);

    }

    private static void doWork(Object obj) throws InterruptedException {
        synchronized (obj) {
            Thread.sleep(MILLIS);
            orderedWaiterNames.add(Thread.currentThread().getName());
        }
    }

    @Test
    public void test() throws Exception {
        int threadCount = threads;
        Runnable r = () -> {
            // create contention between threads for one lock
            try {
                doWork(monitor);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        };
        Thread.UncaughtExceptionHandler eh = (t, e) -> e.printStackTrace();

        try {
            Stressor.execute(threadCount, eh, r);
            // sleep so we know the event is recorded
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
