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

public class TestJavaMonitorWaitInterrupt extends JfrTest {
    private static final int MILLIS = 50;
    static Helper helper = new Helper();
    static String interruptedName;
    static String interrupterName;
    static String simpleWaitName;
    static String simpleNotifyName;

    private boolean interruptedFound = false;
    private boolean simpleWaitFound = false;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.JavaMonitorWait"};
    }

    @Override
    public void analyzeEvents() {
        List<RecordedEvent> events;
        try {
            events = getEvents(recording, "TestJavaMonitorWaitInterrupt");
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
            if (!eventThread.equals(interrupterName) &&
                            !eventThread.equals(interruptedName) &&
                            !eventThread.equals(simpleNotifyName) &&
                            !eventThread.equals(simpleWaitName)) {
                continue;
            }
            if (!struct.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }
            assertTrue("Event is wrong duration.", isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration()));
            assertFalse("Should not have timed out.", struct.<Boolean> getValue("timedOut").booleanValue());

            if (eventThread.equals(interruptedName)) {
                assertTrue("Notifier of interrupted thread should be null", notifThread == null);
                interruptedFound = true;
            } else if (eventThread.equals(simpleWaitName)) {
                assertTrue("Notifier of simple wait is incorrect: " + notifThread + " " + simpleNotifyName, notifThread.equals(simpleNotifyName));
                simpleWaitFound = true;
            }
        }
        assertTrue("Couldn't find expected wait events. SimpleWaiter: " + simpleWaitFound + " interrupted: " + interruptedFound,
                        simpleWaitFound && interruptedFound);
    }

    @Test
    public void test() throws Exception {
        Runnable interrupter = () -> {
            try {
                helper.interrupt();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable interrupted = () -> {
            try {
                helper.interrupted();
                throw new RuntimeException("Was not interrupted!!");
            } catch (InterruptedException e) {
                // should get interrupted
            }
        };

        Runnable simpleWaiter = () -> {
            try {
                helper.simpleWait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable simpleNotifier = () -> {
            try {
                helper.simpleNotify();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        Thread interrupterThread = new Thread(interrupter);
        Thread interruptedThread = new Thread(interrupted);
        helper.interrupted = interruptedThread;
        interrupterName = interrupterThread.getName();
        interruptedName = interruptedThread.getName();

        interruptedThread.start();
        Thread.sleep(MILLIS); // pause to ensure expected ordering of lock acquisition
        interrupterThread.start();

        interruptedThread.join();
        interrupterThread.join();

        Thread tw = new Thread(simpleWaiter);
        Thread tn = new Thread(simpleNotifier);
        simpleWaitName = tw.getName();
        simpleNotifyName = tn.getName();

        tw.start();
        Thread.sleep(50);
        tn.start();

        tw.join();
        tn.join();

        // sleep so we know the event is recorded
        Thread.sleep(500);
    }

    static class Helper {
        public Thread interrupted;

        public synchronized void interrupted() throws InterruptedException {
            wait();
        }

        public synchronized void interrupt() throws InterruptedException {
            interrupted.interrupt();
        }

        public synchronized void simpleWait() throws InterruptedException {
            wait();
        }

        public synchronized void simpleNotify() throws InterruptedException {
            Thread.sleep(2 * MILLIS);
            notify();
        }
    }
}
