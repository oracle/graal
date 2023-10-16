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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

import org.junit.Test;

import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This test checks if symbol table JFR trace IDs get reused across successive epochs. If they are
 * reused/duplicated, the validation step will fail because the trace ID key associated with
 * {@link com.oracle.svm.test.jfr.AbstractJfrTest.MonitorWaitHelper} will also be associated with
 * another value that was serialized during a prior epoch.
 */
public class TestSymbolTraceIdUniqueness extends JfrRecordingTest {
    private final MonitorWaitHelper helper = new MonitorWaitHelper();

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.JavaMonitorWait.getName(), JfrEvent.ThreadPark.getName()};

        // Turn off flushing to simplify testing
        Map<String, String> settings = new HashMap<>();
        settings.put("flush-interval", "0");
        Recording recording = startRecording(events, getDefaultConfiguration(), settings);

        // Generate some symbol table entries and trace IDs.
        Heap.getHeap().visitLoadedClasses((clazz) -> LockSupport.parkNanos(clazz, 1));

        // Invoke chunk rotation.
        recording.dump(createTempJfrFile());

        // Emit event that will insert a new symbol table entry who's ID is under test.
        helper.doEvent();

        stopRecording(recording, TestSymbolTraceIdUniqueness::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        boolean found = false;
        for (RecordedEvent event : events) {
            if (event.getEventType().getName().equals(JfrEvent.JavaMonitorWait.getName())) {
                if (event.getClass("monitorClass").getName().equals(MonitorWaitHelper.class.getName())) {
                    found = true;
                    break;
                }

            }
        }
        assertTrue("Class " + MonitorWaitHelper.class.getName() + " has shared duplicate trace ID with another symbol constant pool entry.", found);
    }
}
