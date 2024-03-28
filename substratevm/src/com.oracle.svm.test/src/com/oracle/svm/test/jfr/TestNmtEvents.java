/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;

import java.util.List;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import org.junit.Test;

import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

public class TestNmtEvents extends JfrRecordingTest {
    private static final int ALLOCATION_SIZE = 1024 * 16;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{
                        JfrEvent.NativeMemoryUsage.getName(),
                        JfrEvent.NativeMemoryUsageTotal.getName(),
                        "svm.NativeMemoryUsageTotalPeak",
                        "svm.NativeMemoryUsagePeak"
        };

        Recording recording = startRecording(events);

        Pointer ptr = NativeMemory.malloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtCategory.Code);

        /* Force a chunk rotation to trigger periodic event emission. */
        recording.dump(createTempJfrFile());

        NativeMemory.free(ptr);

        stopRecording(recording, TestNmtEvents::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        boolean foundNativeMemoryUsage = false;
        boolean foundNativeMemoryUsageTotal = false;
        boolean foundNativeMemoryUsageTotalPeak = false;
        boolean foundNativeMemoryUsagePeak = false;

        assertTrue(events.size() >= 4);
        for (RecordedEvent e : events) {
            if (e.getEventType().getName().equals(JfrEvent.NativeMemoryUsage.getName()) &&
                            e.getString("type").equals(NmtCategory.Code.getName())) {
                foundNativeMemoryUsage = true;

            }
            if (e.getEventType().getName().equals("svm.NativeMemoryUsageTotalPeak")) {
                foundNativeMemoryUsageTotalPeak = true;
            }
            if (e.getEventType().getName().equals("svm.NativeMemoryUsagePeak") &&
                            e.getString("type").equals(NmtCategory.Code.getName())) {
                foundNativeMemoryUsagePeak = true;
            }
            if (e.getEventType().getName().equals(JfrEvent.NativeMemoryUsageTotal.getName())) {
                foundNativeMemoryUsageTotal = true;
            }
        }
        assertTrue(foundNativeMemoryUsage);
        assertTrue(foundNativeMemoryUsageTotal);
        assertTrue(foundNativeMemoryUsagePeak);
        assertTrue(foundNativeMemoryUsageTotalPeak);
    }
}
