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

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.svm.test.jfr.events.StringEvent;
import org.junit.Test;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;
import com.oracle.svm.core.thread.VirtualThreads;

/**
 * This test ensures that java level events emittred by virtual threads have the correct java thread
 * ID information, associated with them. In particular this should help catch out of date
 * substitutions related to the Java level JFR EventWriter.
 */

public class TestJavaLevelVirtualThreadEvents extends JfrRecordingTest {
    private static final int MILLIS = 50;
    private static final int THREADS = 5;
    private static final String[] TESTED_EVENTS = new String[]{"jdk.ThreadSleep", "jdk.VirtualThreadStart", "jdk.VirtualThreadEnd", "jdk.VirtualThreadPinned", "com.jfr.String"};
    private static final Map<Long, List<String>> seenThreads = Collections.synchronizedMap(new HashMap<>());

    @Test
    public void test() throws Throwable {
        Recording recording = startRecording(TESTED_EVENTS);
        Runnable r = () -> {
            try {
                seenThreads.put(Thread.currentThread().threadId(), new ArrayList<>(List.of(TESTED_EVENTS)));
                /*
                 * Pinning the current thread here will result in a VirtualThreadPinned event
                 * emitted when it attempts to yield at the subsequent Thread.sleep() call
                 */
                VirtualThreads.singleton().pinCurrent();
                Thread.sleep(MILLIS);
                com.oracle.svm.test.jfr.events.StringEvent stringEvent = new StringEvent();
                stringEvent.begin();
                stringEvent.message = "some message body";
                stringEvent.commit();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        VirtualStressor.execute(THREADS, r);

        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        for (RecordedEvent event : events) {
            String eventName = event.getEventType().getName();
            RecordedThread eventThread = event.getThread("eventThread");
            if (eventThread == null) {
                continue;
            }
            long tid = eventThread.getJavaThreadId();
            List<String> remainingEvents = seenThreads.get(tid);
            if (remainingEvents == null) {
                continue;
            }
            remainingEvents.remove(eventName);
            if (remainingEvents.isEmpty()) {
                seenThreads.remove(tid);
            }

        }
        assertTrue(seenThreads.isEmpty());
    }
}
