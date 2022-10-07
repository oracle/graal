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

import java.util.List;

import org.junit.Test;
import org.junit.Assert;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorWaitTimeout extends JfrTest {
    private static final int MILLIS = 50;
    static final Helper helper = new Helper();
    static Thread unheardNotifierThread;
    static Thread timeoutThread;

    static Thread simpleWaitThread;
    static Thread simpleNotifyThread;
    private boolean timeoutFound = false;
    private boolean simpleWaitFound = false;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.JavaMonitorWait"};
    }

    @Override
    public void validateEvents() throws Throwable {
        List<RecordedEvent> events;
        events = getEvents("TestJavaMonitorWaitTimeout");

        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread> getValue("eventThread").getJavaName();
            String notifThread = struct.<RecordedThread> getValue("notifier") != null ? struct.<RecordedThread> getValue("notifier").getJavaName() : null;
            if (!eventThread.equals(unheardNotifierThread.getName()) &&
                            !eventThread.equals(timeoutThread.getName()) &&
                            !eventThread.equals(simpleNotifyThread.getName()) &&
                            !eventThread.equals(simpleWaitThread.getName())) {
                continue;
            }
            if (!struct.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName())) {
                continue;
            }
            assertTrue("Event is wrong duration:" + event.getDuration().toMillis(), event.getDuration().toMillis() >= MILLIS);
            if (eventThread.equals(timeoutThread.getName())) {
                assertTrue("Notifier of timeout thread should be null", notifThread == null);
                assertTrue("Should have timed out.", struct.<Boolean> getValue("timedOut").booleanValue());
                timeoutFound = true;
            } else if (eventThread.equals(simpleWaitThread.getName())) {
                assertTrue("Notifier of simple wait is incorrect", notifThread.equals(simpleNotifyThread.getName()));
                simpleWaitFound = true;
            }

        }
        assertTrue("Couldn't find expected wait events. SimpleWaiter: " + simpleWaitFound + " timeout: " + timeoutFound,
                        simpleWaitFound && timeoutFound);
    }

    private static void testTimeout() throws InterruptedException {
        Runnable unheardNotifier = () -> {
            helper.unheardNotify();
        };

        Runnable timouter = () -> {
            try {
                helper.timeout();
            } catch (InterruptedException e) {
                Assert.fail(e.getMessage());
            }
        };

        unheardNotifierThread = new Thread(unheardNotifier);
        timeoutThread = new Thread(timouter);

        timeoutThread.start();
        timeoutThread.join();

        // wait for timeout before trying to notify
        unheardNotifierThread.start();
        unheardNotifierThread.join();

    }

    private static void testWaitNotify() throws Exception {
        Runnable simpleWaiter = () -> {
            helper.simpleWait();
        };

        Runnable simpleNotifier = () -> {
            try {
                while (!simpleWaitThread.getState().equals(Thread.State.WAITING)) {
                    Thread.sleep(10);
                }
                helper.simpleNotify();
            } catch (InterruptedException e) {
                Assert.fail(e.getMessage());
            }
        };

        simpleWaitThread = new Thread(simpleWaiter);
        simpleNotifyThread = new Thread(simpleNotifier);

        simpleWaitThread.start();
        simpleNotifyThread.start();
        simpleWaitThread.join();
        simpleNotifyThread.join();
    }

    @Test
    public void test() throws Exception {
        testTimeout();
        testWaitNotify();
    }

    static class Helper {
        public synchronized void timeout() throws InterruptedException {
            wait(MILLIS);
        }

        public synchronized void unheardNotify() {
            notify();
        }

        public synchronized void simpleWait() {
            try {
                wait();
            } catch (Exception e) {
                Assert.fail(e.getMessage());
            }
        }

        public synchronized void simpleNotify() throws InterruptedException {
            Thread.sleep(MILLIS);
            notify();
        }
    }
}
