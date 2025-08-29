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
import com.oracle.svm.core.genscavenge.SerialAndEpsilonGCOptions;
import com.oracle.svm.core.jfr.JfrEvent;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedThread;

/**
 * Objects are allocated outside the TLAB if they don't fit into the current TLAB and the remaining
 * space in the current TLAB is larger than the refill waste limit. Additionally, the objects must
 * be smaller than the {@link SerialAndEpsilonGCOptions#LargeArrayThreshold}.
 */
public class TestObjectAllocationOutsideTLABEvent extends JfrRecordingTest {

    private static final String TEST_THREAD_NAME = "eventTestThread";

    @Test
    public void test() throws Throwable {
        String[] events = new String[]{JfrEvent.ObjectAllocationOutsideTLAB.getName()};
        Recording recording = startRecording(events);

        /* Do a GC before the allocations to avoid that the GC interferes with the test. */
        System.gc();

        /*
         * Use a separate thread for allocating the objects, to have better control over the TLAB
         * and to make sure the objects are actually allocated outside the TLAB.
         */
        Thread testThread = new Thread(() -> {
            int arrayDataSize = arrayDataSizeForOutOfTLABAllocation();

            /* Allocate a small object to make sure we have a TLAB. */
            GraalDirectives.blackhole(new Object());

            /* Allocate large arrays that don't fit into the TLAB. */
            GraalDirectives.blackhole(allocateByteArray(arrayDataSize / Byte.BYTES));
            GraalDirectives.blackhole(allocateCharArray(arrayDataSize / Character.BYTES));
        }, TEST_THREAD_NAME);

        testThread.start();
        testThread.join();

        stopRecording(recording, TestObjectAllocationOutsideTLABEvent::validateEvents);
    }

    @NeverInline("Prevent escape analysis.")
    private static byte[] allocateByteArray(int length) {
        return new byte[length];
    }

    @NeverInline("Prevent escape analysis.")
    private static char[] allocateCharArray(int length) {
        return new char[length];
    }

    /**
     * Returns the required array data size in bytes such that an array is too large to be allocated
     * in the TLAB, yet still small enough to fit within an aligned heap chunk.
     */
    private static int arrayDataSizeForOutOfTLABAllocation() {
        long largeObjectThreshold = SerialAndEpsilonGCOptions.LargeArrayThreshold.getValue();
        return NumUtil.safeToInt(largeObjectThreshold - 512);
    }

    private static void validateEvents(List<RecordedEvent> events) {
        int arrayDataSize = arrayDataSizeForOutOfTLABAllocation();

        boolean foundByteArray = false;
        boolean foundCharArray = false;

        for (RecordedEvent event : events) {
            String eventThread = event.<RecordedThread> getValue("eventThread").getJavaName();
            if (!eventThread.equals(TEST_THREAD_NAME)) {
                continue;
            }

            long allocationSize = event.<Long> getValue("allocationSize");
            String className = event.<RecordedClass> getValue("objectClass").getName();

            if (allocationSize >= arrayDataSize) {
                if (className.equals(char[].class.getName())) {
                    foundCharArray = true;
                } else if (className.equals(byte[].class.getName())) {
                    foundByteArray = true;
                }
                checkTopStackFrame(event, "slowPathNewArrayLikeObjectWithoutAllocation0");
            }

        }

        assertTrue(foundByteArray);
        assertTrue(foundCharArray);
    }
}
