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

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.oracle.svm.test.jfr.events.ClassEvent;
import com.oracle.svm.test.jfr.events.IntegerEvent;
import com.oracle.svm.test.jfr.events.StringEvent;

/**
 * This test induces repeated chunk rotations and spawns several threads that create java and native
 * events. The goal of this test is to repeatedly create and remove nodes from the
 * JfrBufferNodeLinkedList. Unlike TestStreamingCount, each thread in this test will only do a small
 * amount of work before dying. 80 Threads in total should spawn.
 *
 */

public class TestStreamingStress extends JfrStreamingTest {
    private static final int THREADS = 8;
    private static final int COUNT = 10;
    private static final int EXPECTED_EVENTS = THREADS * COUNT * 10;
    final Helper helper = new Helper();
    private int iterations = 10;
    private AtomicLong remainingClassEvents = new AtomicLong(EXPECTED_EVENTS);
    private AtomicLong remainingIntegerEvents = new AtomicLong(EXPECTED_EVENTS);
    private AtomicLong remainingStringEvents = new AtomicLong(EXPECTED_EVENTS);
    private AtomicLong remainingWaitEvents = new AtomicLong(EXPECTED_EVENTS);
    volatile int flushes = 0;
    volatile boolean doneCollection = false;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"com.jfr.String"};
    }

    @Test
    public void test() throws Exception {
        Runnable r = () -> {
            for (int i = 0; i < COUNT; i++) {
                StringEvent stringEvent = new StringEvent();
                stringEvent.message = "StringEvent has been generated as part of TestConcurrentEvents.";
                stringEvent.commit();

                IntegerEvent integerEvent = new IntegerEvent();
                integerEvent.number = Integer.MAX_VALUE;
                integerEvent.commit();

                ClassEvent classEvent = new ClassEvent();
                classEvent.clazz = Math.class;
                classEvent.commit();
                try {
                    helper.doEvent();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                emittedEvents.incrementAndGet();
            }
        };
        var rs = createStream();
        rs.enable("com.jfr.String");
        rs.enable("com.jfr.Integer");
        rs.enable("com.jfr.Class");
        rs.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofNanos(0));
        rs.onEvent("com.jfr.Class", event -> {
            remainingClassEvents.decrementAndGet();
        });
        rs.onEvent("com.jfr.Integer", event -> {
            remainingIntegerEvents.decrementAndGet();
        });
        rs.onEvent("com.jfr.String", event -> {
            remainingStringEvents.decrementAndGet();
        });
        rs.onEvent("jdk.JavaMonitorWait", event -> {
            if (!event.getClass("monitorClass").getName().equals(Helper.class.getName())) {
                return;
            }
            remainingWaitEvents.decrementAndGet();
        });

        File directory = new File(".");
        dumpLocation = new File(directory.getAbsolutePath(), "TestStreamingStress.jfr").toPath();

        Runnable rotateChunk = () -> {
            try {
                if (flushes % 3 == 0 && !doneCollection) {
                    rs.dump(dumpLocation); // force chunk rotation
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            flushes++;
        };

        rs.onFlush(rotateChunk);
        rs.startAsync();
        while (iterations > 0) {
            Stressor.execute(THREADS, r);
            iterations--;
        }
        while (emittedEvents.get() < EXPECTED_EVENTS) {
            Thread.sleep(10);
        }

        int flushCount = flushes;
        while (remainingClassEvents.get() > 0 || remainingIntegerEvents.get() > 0 || remainingStringEvents.get() > 0 || remainingWaitEvents.get() > 0) {
            assertFalse("Not all expected events were found in the stream. Class:" + remainingClassEvents.get() + " Integer:" + remainingIntegerEvents.get() + " String:" +
                            remainingStringEvents.get() + " Wait:" + remainingWaitEvents.get(),
                            flushes > (flushCount + 1) && (remainingClassEvents.get() > 0 || remainingIntegerEvents.get() > 0 ||
                                            remainingStringEvents.get() > 0 || remainingWaitEvents.get() > 0));
        }
        doneCollection = true;

        rs.dump(dumpLocation);
        closeStream();
    }

    static class Helper {
        public synchronized void doEvent() throws InterruptedException {
            wait(0, 1);
        }
    }
}
