/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import jdk.jfr.consumer.RecordedObject;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestGCHeapSummaryEvent extends JfrRecordingTest {
    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.GCHeapSummary.getName()};
        Recording recording = startRecording(events);

        System.gc();

        stopRecording(recording, TestGCHeapSummaryEvent::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        assertTrue(events.size() > 0);
        for (RecordedEvent event : events) {
            RecordedObject heapSpace = event.getValue("heapSpace");
            assertTrue(heapSpace.getLong("start") == -1);
            assertTrue(heapSpace.getLong("committedEnd") == -1);
            assertTrue(heapSpace.getLong("reservedEnd") == -1);
            assertTrue(heapSpace.getLong("committedSize") > 0);
            assertTrue(heapSpace.getLong("reservedSize") >= heapSpace.getLong("committedSize"));
            assertTrue(event.getLong("gcId") >= 0);
            assertTrue(event.getString("when").equals("Before GC") || event.getString("when").equals("After GC"));
            assertTrue(event.getLong("heapUsed") > 0);
        }
    }
}
