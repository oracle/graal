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

import java.util.List;

import jdk.graal.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.junit.Test;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

public class TestNmtEvents extends JfrRecordingTest {
    private static final int ALLOCATION_SIZE = 1024 * 16;

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{
                        JfrEvent.NativeMemoryUsage.getName(),
                        JfrEvent.NativeMemoryUsageTotal.getName(),
                        "jdk.NativeMemoryUsagePeak",
                        "jdk.NativeMemoryUsageTotalPeak"
        };
        Recording recording = startRecording(events);

        Pointer ptr = NativeMemory.malloc(Word.unsigned(ALLOCATION_SIZE), NmtCategory.Code);

        /* Force a chunk rotation to trigger periodic event emission. */
        recording.dump(createTempJfrFile());

        NativeMemory.free(ptr);

        stopRecording(recording, TestNmtEvents::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        assertTrue(events.size() >= 4);

        boolean foundNativeMemoryUsage = false;
        boolean foundNativeMemoryUsagePeak = false;
        boolean foundNativeMemoryUsageTotal = false;
        boolean foundNativeMemoryUsageTotalPeak = false;

        for (RecordedEvent e : events) {
            String eventName = e.getEventType().getName();
            if (eventName.equals(JfrEvent.NativeMemoryUsage.getName()) && e.getString("type").equals(NmtCategory.Code.getName())) {
                foundNativeMemoryUsage = true;
            }

            if (eventName.equals("jdk.NativeMemoryUsageTotalPeak")) {
                foundNativeMemoryUsageTotalPeak = true;
            }

            if (eventName.equals("jdk.NativeMemoryUsagePeak") && e.getString("type").equals(NmtCategory.Code.getName())) {
                foundNativeMemoryUsagePeak = true;
            }

            if (eventName.equals(JfrEvent.NativeMemoryUsageTotal.getName())) {
                foundNativeMemoryUsageTotal = true;
            }
        }

        assertTrue(foundNativeMemoryUsage);
        assertTrue(foundNativeMemoryUsagePeak);
        assertTrue(foundNativeMemoryUsageTotal);
        assertTrue(foundNativeMemoryUsageTotalPeak);
    }
}
