/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, 2026, Red Hat Inc. All rights reserved.
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.oracle.svm.test.jfr.events.StringEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * Check that Java-level events emitted by long-lived virtual threads still resolve their thread
 * metadata after a chunk rotation.
 */
public class TestJavaLevelVirtualThreadChunkRotation extends JfrRecordingTest {
    private static final int THREADS = 3;
    private static final String BEFORE_PREFIX = "before-";
    private static final String AFTER_PREFIX = "after-";
    private static final String SHORT_LIVED_AFTER_PREFIX = "short-after-";

    private final AtomicInteger emittedBeforeRotation = new AtomicInteger(0);
    private final Set<Long> expectedAfterRotationThreads = Collections.synchronizedSet(new HashSet<>()); // noEconomicSet(synchronization)
    private volatile long expectedShortLivedAfterRotationThread;

    private volatile boolean proceed;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"com.jfr.String"};
        Recording recording = startRecording(events);

        Runnable eventEmitter = () -> {
            long threadId = Thread.currentThread().threadId();

            emitStringEvent(BEFORE_PREFIX + threadId);
            emittedBeforeRotation.incrementAndGet();

            while (!proceed) {
                // Busy-wait so the virtual thread stays mounted on the carrier.
            }

            emitStringEvent(AFTER_PREFIX + threadId);
            expectedAfterRotationThreads.add(threadId);
        };

        List<Thread> threads = VirtualStressor.executeAsync(THREADS, eventEmitter);
        waitUntilTrue(() -> emittedBeforeRotation.get() == THREADS);

        recording.dump(createTempJfrFile());
        proceed = true;

        VirtualStressor.join(threads);
        stopRecording(recording, this::validateEvents);
    }

    @Test
    public void testShortLivedThreadAfterChunkRotation() throws Throwable {
        String[] events = new String[]{"com.jfr.String"};
        Recording recording = startRecording(events);

        recording.dump(createTempJfrFile());

        Thread thread = Thread.ofVirtual().start(() -> {
            long threadId = Thread.currentThread().threadId();
            expectedShortLivedAfterRotationThread = threadId;
            emitStringEvent(SHORT_LIVED_AFTER_PREFIX + threadId);
        });
        thread.join();

        stopRecording(recording, this::validateShortLivedAfterRotationEvent);
    }

    private void validateEvents(List<RecordedEvent> events) {
        int afterRotationEvents = 0;
        for (RecordedEvent event : events) {
            String message = event.getString("message");
            if (message != null && message.startsWith(AFTER_PREFIX)) {
                RecordedThread eventThread = event.getThread("eventThread");
                assertNotNull("Virtual thread data is missing after chunk rotation.", eventThread);
                expectedAfterRotationThreads.remove(eventThread.getJavaThreadId());
                afterRotationEvents++;
            }
        }

        assertEquals(THREADS, afterRotationEvents);
        assertTrue(expectedAfterRotationThreads.isEmpty());
    }

    private void validateShortLivedAfterRotationEvent(List<RecordedEvent> events) {
        assertTrue(expectedShortLivedAfterRotationThread > 0);

        int matchingEvents = 0;
        for (RecordedEvent event : events) {
            String message = event.getString("message");
            if (message != null && message.equals(SHORT_LIVED_AFTER_PREFIX + expectedShortLivedAfterRotationThread)) {
                RecordedThread eventThread = event.getThread("eventThread");
                assertNotNull("Short-lived virtual thread data is missing after chunk rotation.", eventThread);
                assertEquals(expectedShortLivedAfterRotationThread, eventThread.getJavaThreadId());
                matchingEvents++;
            }
        }

        assertEquals(1, matchingEvents);
    }

    private static void emitStringEvent(String message) {
        StringEvent stringEvent = new StringEvent();
        stringEvent.message = message;
        stringEvent.commit();
    }
}
