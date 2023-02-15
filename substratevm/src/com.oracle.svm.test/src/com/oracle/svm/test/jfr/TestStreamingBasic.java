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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordedClass;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Check to make sure 1. The events that are emitted are found in the stream 2. The resulting JFR
 * dump is readable and events can be read that match the events that were streamed.
 */
public class TestStreamingBasic extends StreamingTest {
    private static final int MILLIS = 20;
    private static final int THREADS = 3;
    private static final int EXPECTED_EVENTS = THREADS * 2;
    final Helper helper = new Helper();
    private AtomicLong remainingStringEventsInStream = new AtomicLong(EXPECTED_EVENTS);
    volatile int flushes = 0;
    HashSet<String> streamEvents = new HashSet<>();

    @Override
    public String[] getTestedEvents() {
        return new String[]{JfrEvent.JavaMonitorWait.getName()};
    }

    @Override
    public void validateEvents() throws Throwable {
        List<RecordedEvent> events = getEvents(dumpLocation);
        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (event.<RecordedClass> getValue("monitorClass").getName().equals(Helper.class.getName()) && event.getDuration().toMillis() >= MILLIS - 1) {
                if (!streamEvents.contains(eventThread)) {
                    continue;
                }
                streamEvents.remove(eventThread);
            }
        }
        assertTrue("Not all expected monitor wait events were found in the JFR file", streamEvents.isEmpty());
    }

    @Test
    public void test() throws Exception {
        Runnable r = () -> {
            try {
                helper.doEvent();
                emittedEvents.incrementAndGet();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        var rs = createStream();
        rs.enable("jdk.JavaMonitorWait").withThreshold(Duration.ofMillis(MILLIS - 1)).withStackTrace();
        rs.onEvent("jdk.JavaMonitorWait", event -> {
            String thread = event.getThread("eventThread").getJavaName();
            if (!event.getClass("monitorClass").getName().equals(Helper.class.getName())) {
                return;
            }
            if (streamEvents.contains(thread)) {
                return;
            }
            streamEvents.add(thread);
            remainingStringEventsInStream.decrementAndGet();
        });

        rs.onFlush(() -> {
            try {
                if (flushes == 0) {
                    Stressor.execute(THREADS, r);
                    // at this point all expected events should be generated
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            flushes++;
        });

        rs.startAsync();
        Stressor.execute(THREADS, r);

        // Wait until all events have been emitted.
        while (emittedEvents.get() < EXPECTED_EVENTS) {
            Thread.sleep(10);
        }
        int flushCount = flushes;
        /*
         * At this point we can expect to have found all the events after the 2 flushes Scenario:
         * Next flush is occurring while emittedEvents.get() is incremented up to EXPECTED_EVENTS
         * and therefore doesn't contain all the events. But the flush after the next one must
         * contain all remaining events.
         */
        while (remainingStringEventsInStream.get() > 0) {
            assertFalse("Not all expected monitor wait events were found in the JFR stream. Remaining:" + remainingStringEventsInStream.get(),
                            flushes > (flushCount + 1) && remainingStringEventsInStream.get() > 0);
        }

        File directory = new File(".");
        dumpLocation = new File(directory.getAbsolutePath(), "TestStreaming.jfr").toPath();
        rs.dump(dumpLocation);

        closeStream();
    }

    static class Helper {
        public synchronized void doEvent() throws InterruptedException {
            wait(MILLIS);
        }
    }
}
