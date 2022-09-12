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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.Test;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorWaitTimeout extends JfrTest {
    private static final int MILLIS = 50;
    static Helper helper = new Helper();
    static String timeOutName;
    static String notifierName;
    static String simpleWaitName;
    static String simpleNotifyName;
    private boolean timeoutFound = false;
    private boolean simpleWaitFound = false;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.JavaMonitorWait"};
    }

    @Override
    public void analyzeEvents() {
        List<RecordedEvent> events;
        try {
            events = getEvents("TestJavaMonitorWaitTimeout");
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
            if (!eventThread.equals(notifierName) &&
                            !eventThread.equals(timeOutName) &&
                            !eventThread.equals(simpleNotifyName) &&
                            !eventThread.equals(simpleWaitName)) {
                continue;
            }
            if (!struct.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }
            assertTrue("Event is wrong duration.", isGreaterDuration(Duration.ofMillis(MILLIS), event.getDuration()));
            if (eventThread.equals(timeOutName)) {
                assertTrue("Notifier of timeout thread should be null", notifThread == null);
                assertTrue("Should have timed out.", struct.<Boolean> getValue("timedOut").booleanValue());
                timeoutFound = true;
            } else if (eventThread.equals(simpleWaitName)) {
                assertTrue("Notifier of simple wait is incorrect", notifThread.equals(simpleNotifyName));
                simpleWaitFound = true;
            }

        }
        assertTrue("Couldn't find expected wait events. SimpleWaiter: " + simpleWaitFound + " timeout: " + timeoutFound,
                        simpleWaitFound && timeoutFound);
    }

    @Test
    public void test() throws Exception {
        Runnable unheardNotifier = () -> {
            try {
                helper.unheardNotify();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        Runnable timouter = () -> {
            try {
                helper.timeout();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
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
        Thread unheardNotifierThread = new Thread(unheardNotifier);
        Thread timeoutThread = new Thread(timouter);
        timeOutName = timeoutThread.getName();
        notifierName = unheardNotifierThread.getName();

        timeoutThread.start();
        Thread.sleep(10);
        unheardNotifierThread.start();

        timeoutThread.join();
        unheardNotifierThread.join();

        Thread tw = new Thread(simpleWaiter);
        Thread tn = new Thread(simpleNotifier);
        simpleWaitName = tw.getName();
        simpleNotifyName = tn.getName();

        tw.start();
        Thread.sleep(10);
        tn.start();

        tw.join();
        tn.join();

        // sleep so we know the event is recorded
        Thread.sleep(500);
    }

    static class Helper {
        public synchronized void timeout() throws InterruptedException {
            wait(MILLIS);
        }

        public synchronized void unheardNotify() throws InterruptedException {
            Thread.sleep(2 * MILLIS);
            // notify after timeout
            notify();
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
