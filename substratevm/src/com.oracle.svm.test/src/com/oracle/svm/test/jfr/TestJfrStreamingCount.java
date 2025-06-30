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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.oracle.svm.test.jfr.events.ClassEvent;
import com.oracle.svm.test.jfr.events.IntegerEvent;
import com.oracle.svm.test.jfr.events.StringEvent;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

/**
 * Check to make sure 1. All events are accounted for when using streaming (even when there are very
 * many events generated). This test also reduces the maximum JFR file size to force chunk
 * rotations.
 */
public class TestJfrStreamingCount extends JfrStreamingTest {
    private static final int THREADS = 8;
    private static final int COUNT = 4 * 1024;
    private static final int EXPECTED_EVENTS_PER_TYPE = THREADS * COUNT;
    private static final int EXPECTED_TOTAL_EVENTS = EXPECTED_EVENTS_PER_TYPE * 3;

    private final AtomicInteger emittedEventsPerType = new AtomicInteger(0);
    private final AtomicLong classEvents = new AtomicLong(0);
    private final AtomicLong integerEvents = new AtomicLong(0);
    private final AtomicLong stringEvents = new AtomicLong(0);

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"com.jfr.String", "com.jfr.Integer", "com.jfr.Class"};
        RecordingStream stream = startStream(events);

        stream.onEvent("com.jfr.Class", _ -> classEvents.incrementAndGet());
        stream.onEvent("com.jfr.Integer", _ -> integerEvents.incrementAndGet());
        stream.onEvent("com.jfr.String", _ -> stringEvents.incrementAndGet());

        Runnable eventEmitter = () -> {
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

                emittedEventsPerType.incrementAndGet();
            }
        };
        Stressor.execute(THREADS, eventEmitter);

        waitUntilTrue(() -> emittedEventsPerType.get() == EXPECTED_EVENTS_PER_TYPE);
        waitUntilTrue(() -> classEvents.get() == EXPECTED_EVENTS_PER_TYPE && integerEvents.get() == EXPECTED_EVENTS_PER_TYPE && stringEvents.get() == EXPECTED_EVENTS_PER_TYPE);

        stopStream(stream, TestJfrStreamingCount::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) throws Throwable {
        if (events.size() != EXPECTED_TOTAL_EVENTS) {
            throw new Exception("Not all expected events were found in the JFR file");
        }
    }
}
