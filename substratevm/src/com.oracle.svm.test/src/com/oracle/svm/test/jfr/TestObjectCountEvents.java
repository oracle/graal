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

import com.oracle.svm.core.jfr.JfrEvent;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public class TestObjectCountEvents extends JfrRecordingTest {
    private static final int SKIPPED_COUNT = 3;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.ObjectCount.getName(), JfrEvent.ObjectCountAfterGC.getName()};
        Recording recording = startRecording(events);

        for (int i = 0; i < SKIPPED_COUNT; i++) {
            System.gc();
        }

        stopRecording(recording, TestObjectCountEvents::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        Set<Integer> seen = new HashSet<>();
        Set<Integer> seenAfterGC = new HashSet<>();
        int max = 0;
        int min = Integer.MAX_VALUE;
        assertTrue(events.size() > 0);
        for (RecordedEvent event : events) {
            assertTrue("Objects should have a non-zero total size", event.<Long> getValue("totalSize") > 0);
            assertTrue("Object counts should be non-zero", event.<Long> getValue("count") > 0);
            int gcId = event.<Integer> getValue("gcId");
            if (max < gcId) {
                max = gcId;
            }
            if (min > gcId) {
                min = gcId;
            }
            seenAfterGC.add(gcId);
            if (event.getEventType().getName().equals(JfrEvent.ObjectCount.getName())) {
                seen.add(gcId);
            }

        }
        int skippedCount = max - min - seen.size() + 1;
        assertTrue("Not every GC should result in jdk.ObjectCount events.", skippedCount >= SKIPPED_COUNT);
        int skippedCountAfterGc = max - min - seenAfterGC.size() + 1;
        assertTrue("Every GC should result in jdk.ObjectCountAfterGC events.", skippedCountAfterGc == 0);
    }
}
