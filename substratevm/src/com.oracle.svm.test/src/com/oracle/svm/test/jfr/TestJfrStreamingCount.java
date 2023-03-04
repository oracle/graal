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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.oracle.svm.test.jfr.events.ClassEvent;
import com.oracle.svm.test.jfr.events.IntegerEvent;
import com.oracle.svm.test.jfr.events.StringEvent;

import jdk.jfr.consumer.RecordedEvent;

/**
 * Check to make sure 1. All events are accounted for when using streaming (even when there are very
 * many events generated). This test also forces a chunk rotation after the first flush as a sanity
 * check for potential flush/rotation clashes.
 */

public class TestStreamingCount extends JfrStreamingTest {
    private static final int THREADS = 8;
    private static final int COUNT = 1024;
    private static final int EXPECTED_EVENTS = THREADS * COUNT;
    private AtomicLong remainingClassEvents = new AtomicLong(EXPECTED_EVENTS);
    private AtomicLong remainingIntegerEvents = new AtomicLong(EXPECTED_EVENTS);
    private AtomicLong remainingStringEvents = new AtomicLong(EXPECTED_EVENTS);
    volatile int flushes = 0;

    @Override
    public String[] getTestedEvents() {
        return new String[]{"com.jfr.String"};
    }

    @Override
    public void validateEvents() throws Throwable {
        // Tally up a selection of the events in the dump as a quick check they match the expected
        // number.
        List<RecordedEvent> events = getEvents(dumpLocation);
        if (events.size() != EXPECTED_EVENTS) {
            throw new Exception("Not all expected events were found in the JFR file");
        }
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
                emittedEvents.incrementAndGet();
            }
        };

        var rs = createStream();
        rs.enable("com.jfr.String");
        rs.enable("com.jfr.Integer");
        rs.enable("com.jfr.Class");
        rs.onEvent("com.jfr.Class", event -> {
            remainingClassEvents.decrementAndGet();
        });
        rs.onEvent("com.jfr.Integer", event -> {
            remainingIntegerEvents.decrementAndGet();
        });
        rs.onEvent("com.jfr.String", event -> {
            remainingStringEvents.decrementAndGet();
        });

        File directory = new File(".");
        dumpLocation = new File(directory.getAbsolutePath(), "TestStreamingCount.jfr").toPath();

        Runnable rotateChunk = () -> {
            try {
                if (flushes == 0) {
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
        Stressor.execute(THREADS, r);

        while (emittedEvents.get() < EXPECTED_EVENTS) {
            Thread.sleep(10);
        }

        int flushCount = flushes;
        while (remainingClassEvents.get() > 0 || remainingIntegerEvents.get() > 0 || remainingStringEvents.get() > 0) {
            assertFalse("Not all expected events were found in the stream. Class:" + remainingClassEvents.get() + " Integer:" + remainingIntegerEvents.get() + " String:" + remainingStringEvents.get(),
                            flushes > (flushCount + 1) && (remainingClassEvents.get() > 0 || remainingIntegerEvents.get() > 0 || remainingStringEvents.get() > 0));
        }
        closeStream();
    }
}
