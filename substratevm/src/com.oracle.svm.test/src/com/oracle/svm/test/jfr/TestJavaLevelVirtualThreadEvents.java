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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.oracle.svm.core.thread.Target_jdk_internal_vm_Continuation;
import com.oracle.svm.test.jfr.events.StringEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * This test ensures that Java level events emitted by virtual threads have the correct thread ID
 * associated with them.
 */
public class TestJavaLevelVirtualThreadEvents extends JfrRecordingTest {
    private final Map<Long, List<String>> remainingThreadEvents = Collections.synchronizedMap(new HashMap<>());

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{"jdk.ThreadSleep", "jdk.VirtualThreadStart", "jdk.VirtualThreadEnd", "jdk.VirtualThreadPinned", "com.jfr.String"};
        Recording recording = startRecording(events);
        Runnable r = () -> {
            try {
                var oldValue = this.remainingThreadEvents.put(getCurrentThreadId(), new ArrayList<>(List.of(events)));
                assertNull(oldValue);

                /*
                 * Pinning the current thread will result in a VirtualThreadPinned event when it
                 * attempts to yield at the subsequent Thread.sleep() call.
                 */
                Target_jdk_internal_vm_Continuation.pin();
                Thread.sleep(50);

                com.oracle.svm.test.jfr.events.StringEvent stringEvent = new StringEvent();
                stringEvent.begin();
                stringEvent.message = "some message body";
                stringEvent.commit();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };

        VirtualStressor.execute(5, r);
        stopRecording(recording, this::validateEvents);
    }

    private void validateEvents(List<RecordedEvent> events) {
        for (RecordedEvent event : events) {
            String eventName = event.getEventType().getName();
            RecordedThread eventThread = event.getThread("eventThread");
            assertNotNull(eventThread);

            long threadId = eventThread.getId();
            List<String> remainingEvents = remainingThreadEvents.get(threadId);
            if (remainingEvents != null) {
                remainingEvents.remove(eventName);
                if (remainingEvents.isEmpty()) {
                    remainingThreadEvents.remove(threadId);
                }
            }
        }

        assertTrue(this.remainingThreadEvents.isEmpty());
    }

    @SuppressWarnings("deprecation")
    private static long getCurrentThreadId() {
        return Thread.currentThread().getId();
    }
}
