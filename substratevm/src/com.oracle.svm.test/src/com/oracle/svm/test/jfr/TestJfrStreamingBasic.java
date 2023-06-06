/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingStream;

/**
 * Check to make sure 1. The events that are emitted are found in the stream 2. The resulting JFR
 * dump is readable and events can be read that match the events that were streamed.
 */
public class TestJfrStreamingBasic extends JfrStreamingTest {
    private static final int THREADS = 3;
    private static final int EXPECTED_EVENTS = THREADS * 2;

    private final MonitorWaitHelper helper = new MonitorWaitHelper();
    private final AtomicInteger emittedEventsPerType = new AtomicInteger(0);
    private final Set<String> seenThreads = Collections.synchronizedSet(new HashSet<>());
    private boolean firstFlush = true;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorWait.getName()};
        RecordingStream stream = startStream(events);

        stream.onEvent(JfrEvent.JavaMonitorWait.getName(), event -> {
            if (event.<RecordedClass> getValue("monitorClass").getName().equals(MonitorWaitHelper.class.getName())) {
                String thread = event.getThread("eventThread").getJavaName();
                seenThreads.add(thread);
            }
        });

        Runnable eventEmitter = () -> {
            helper.doEvent();
            emittedEventsPerType.incrementAndGet();
        };

        stream.onFlush(() -> {
            if (firstFlush) {
                firstFlush = false;
                Stressor.execute(THREADS, eventEmitter);
            }
        });

        Stressor.execute(THREADS, eventEmitter);

        waitUntilTrue(() -> emittedEventsPerType.get() == EXPECTED_EVENTS);
        waitUntilTrue(() -> seenThreads.size() == EXPECTED_EVENTS);

        stopStream(stream, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        int count = 0;
        for (RecordedEvent event : events) {
            if (event.<RecordedClass> getValue("monitorClass").getName().equals(MonitorWaitHelper.class.getName())) {
                String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
                seenThreads.remove(eventThread);
                count++;
            }
        }
        assertEquals(EXPECTED_EVENTS, count);
        assertTrue(seenThreads.isEmpty());
    }
}
