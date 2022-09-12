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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.Test;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorWaitNotifyAll extends JfrTest {
    private static final int MILLIS = 50;
    static Helper helper = new Helper();
    static String waiterName1;
    static String waiterName2;
    static String notifierName;
    private boolean notifierFound = false;
    private int waitersFound = 0;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.JavaMonitorWait"};
    }

    @Override
    public void analyzeEvents() {
        List<RecordedEvent> events;
        try {
            events = getEvents("TestJavaMonitorWaitNotifyAll");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            if (!event.getEventType().getName().equals("jdk.JavaMonitorWait")) {
                continue;
            }
            String eventThread = struct.<RecordedThread> getValue("eventThread").getJavaName();
            String notifThread = struct.<RecordedThread> getValue("notifier") != null ? struct.<RecordedThread> getValue("notifier").getJavaName() : null;
            if (!eventThread.equals(waiterName1) &&
                            !eventThread.equals(waiterName2) &&
                            !eventThread.equals(notifierName)) {
                continue;
            }
            if (!struct.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }

            assertTrue("Event is wrong duration.", isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration()));

            if (eventThread.equals(notifierName)) {
                assertTrue("Should have timed out.", struct.<Boolean> getValue("timedOut").booleanValue());
                notifierFound = true;
            } else {
                assertFalse("Should not have timed out.", struct.<Boolean> getValue("timedOut").booleanValue());
                assertTrue("Notifier thread name is incorrect", notifThread.equals(notifierName));
                waitersFound++;
            }
        }
        assertTrue("Couldn't find expected wait events. NotifierFound: " + notifierFound + " waitersFound: " + waitersFound,
                        notifierFound && waitersFound == 2);
    }

    @Test
    public void test() throws Exception {
        Runnable consumer = () -> {
            try {
                helper.consume();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable producer = () -> {
            try {
                helper.produce();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        Thread tc1 = new Thread(consumer);
        Thread tp1 = new Thread(producer);
        Thread tp2 = new Thread(producer);
        waiterName1 = tp1.getName();
        waiterName2 = tp2.getName();
        notifierName = tc1.getName();

        tp1.start();
        tp2.start();
        tc1.start();

        tp1.join();
        tp2.join();
        tc1.join();

        // sleep so we know the event is recorded
        Thread.sleep(500);
    }

    static class Helper {
        public synchronized void produce() throws InterruptedException {
            wait();
        }

        public synchronized void consume() throws InterruptedException {
            // give the producers a headstart so they can start waiting
            wait(MILLIS);
            notifyAll(); // should wake up both producers
        }
    }
}
