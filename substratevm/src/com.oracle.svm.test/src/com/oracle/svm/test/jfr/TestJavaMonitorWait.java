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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import jdk.jfr.consumer.RecordedClass;
import org.junit.Test;

import com.oracle.svm.test.jfr.JfrTest;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.consumer.RecordedThread;

public class TestJavaMonitorWait extends JfrTest {
    private static final int MILLIS = 50;
    private static final int COUNT = 10;
    private String producerName;
    private String consumerName;
    static Helper helper = new Helper();

    @Override
    public String[] getTestedEvents() {
        return new String[]{"jdk.JavaMonitorWait"};
    }
    @Override
    public void analyzeEvents() {
        List<RecordedEvent> events;
        try {
            events = getEvents(recording, "jdk.JavaMonitorWait");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int prodCount = 0;
        int consCount = 0;
        String lastEventThreadName = null; //should alternate if buffer is 1
        for (RecordedEvent event : events) {
            RecordedObject struct = event;
            String eventThread = struct.<RecordedThread>getValue("eventThread").getJavaName();
            String notifThread = struct.<RecordedThread>getValue("notifier") != null ? struct.<RecordedThread>getValue("notifier").getJavaName() : null;
            assertTrue("No event thread",eventThread != null);
            if (!eventThread.equals(producerName) && !eventThread.equals(consumerName) ) {
                continue;
            }
            assertTrue( "Wrong event type", event.getEventType().getName().equals("jdk.JavaMonitorWait"));
            assertTrue("Wrong event duration", isEqualDuration(Duration.ofMillis(MILLIS), event.getDuration()));
            assertFalse("Wrong monitor class.",
                    !struct.<RecordedClass>getValue("monitorClass").getName().equals(Helper.class.getName())
                            && (eventThread.equals(consumerName) ||eventThread.equals(producerName)));

            assertFalse("Should not have timed out.", struct.<Boolean>getValue("timedOut").booleanValue());

            if (lastEventThreadName == null) {
                lastEventThreadName = notifThread;
            }
            assertTrue("Not alternating", lastEventThreadName.equals(notifThread));
            if (eventThread.equals(producerName)) {
                prodCount++;
                assertTrue("Wrong notifier", notifThread.equals(consumerName));
            } else if (eventThread.equals(consumerName)) {
                consCount++;
                assertTrue("Wrong notifier", notifThread.equals(producerName));
            }
            lastEventThreadName = eventThread;
        }
        assertFalse("Wrong number of events: "+prodCount + " "+consCount,
                abs(prodCount - consCount) > 1 || abs(consCount-COUNT) >1);
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
        Thread tc = new Thread(consumer);
        Thread tp = new Thread(producer);
        producerName = tp.getName();
        consumerName = tc.getName();
        tp.start();
        tc.start();
        tp.join();
        tc.join();

        // sleep so we know the event is recorded
        Thread.sleep(500);
    }

    static class Helper {
        private int count = 0;
        private final int bufferSize = 1;

        public synchronized void produce() throws InterruptedException {
            for (int i = 0; i< COUNT; i++) {
                while (count >= bufferSize) {
                    wait();
                }
                Thread.sleep(MILLIS);
                count++;
                notify();
            }
        }

        public synchronized void consume() throws InterruptedException {
            for (int i = 0; i< COUNT; i++) {
                while (count == 0) {
                    wait();
                }
                Thread.sleep(MILLIS);
                count--;
                notify();
            }
        }
    }
}
