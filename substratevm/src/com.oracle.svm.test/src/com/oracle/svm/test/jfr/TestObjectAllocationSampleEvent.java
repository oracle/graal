/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, Red Hat Inc. All rights reserved.
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

import org.junit.Test;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

public class TestObjectAllocationSampleEvent extends JfrRecordingTest {
    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.ObjectAllocationSample.getName()};
        Recording recording = startRecording(events);

        int alignedHeapChunkSize = UnsignedUtils.safeToInt(HeapParameters.getAlignedHeapChunkSize());

        // Allocate large arrays (always need a new TLAB).
        allocateByteArray(2 * alignedHeapChunkSize);
        allocateCharArray(alignedHeapChunkSize);

        stopRecording(recording, TestObjectAllocationSampleEvent::validateEvents);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        long alignedHeapChunkSize = HeapParameters.getAlignedHeapChunkSize().rawValue();

        boolean foundByteArray = false;
        boolean foundCharArray = false;

        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (!eventThread.equals("main")) {
                continue;
            }

            long allocationSize = event.<Long> getValue("weight");
            String className = event.<RecordedClass> getValue("objectClass").getName();

            // >= To account for size of reference
            if (allocationSize >= 2 * alignedHeapChunkSize) {
                // verify previous owner
                if (className.equals(char[].class.getName())) {
                    foundCharArray = true;
                    checkTopStackFrame(event, "slowPathNewArrayLikeObjectWithoutAllocation0");
                } else if (className.equals(byte[].class.getName())) {
                    foundByteArray = true;
                    checkTopStackFrame(event, "slowPathNewArrayLikeObjectWithoutAllocation0");
                }
            }
        }

        assertTrue(foundCharArray);
        assertTrue(foundByteArray);
    }

    @NeverInline("Prevent escape analysis.")
    private static byte[] allocateByteArray(int length) {
        return new byte[length];
    }

    @NeverInline("Prevent escape analysis.")
    private static char[] allocateCharArray(int length) {
        return new char[length];
    }
}
