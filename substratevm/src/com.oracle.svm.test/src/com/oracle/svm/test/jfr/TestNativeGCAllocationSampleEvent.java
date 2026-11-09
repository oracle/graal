/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.graalvm.nativeimage.ImageInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.shared.NeverInline;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/** Allocation sample coverage that does not depend on serial-collector TLAB internals. */
public class TestNativeGCAllocationSampleEvent {
    private static volatile Object allocated;

    @BeforeClass
    public static void checkForJFR() {
        assumeTrue("skipping JFR tests", !ImageInfo.inImageCode() || HasJfrSupport.get());
    }

    @Test
    public void instanceAndArraySamplesHaveTypesWeightsAndStacks() throws Exception {
        Path path = Files.createTempFile("allocation-samples", ".jfr");
        try {
            try (Recording recording = new Recording()) {
                recording.enable("jdk.ObjectAllocationSample").with("throttle", "off").withStackTrace();
                recording.start();
                allocatePayloads();
                allocateArrays();
                recording.stop();
                recording.dump(path);
            }
            validateEvents(RecordingFile.readAllEvents(path));
        } finally {
            allocated = null;
            Files.deleteIfExists(path);
        }
    }

    private static void validateEvents(List<RecordedEvent> events) {
        boolean foundPayload = false;
        boolean foundArray = false;
        for (RecordedEvent event : events) {
            if (!event.getEventType().getName().equals("jdk.ObjectAllocationSample") ||
                            event.getThread().getJavaThreadId() != Thread.currentThread().threadId()) {
                continue;
            }
            String type = event.getClass("objectClass").getName();
            if (type.equals(Payload.class.getName()) || type.equals(byte[].class.getName())) {
                assertTrue("sample weight must be positive", event.getLong("weight") > 0);
                assertNotNull("allocation stack must be recorded", event.getStackTrace());
                String allocator = type.equals(Payload.class.getName()) ? "allocatePayloads" : "allocateArrays";
                assertTrue("allocation stack must identify the allocating method", event.getStackTrace().getFrames().stream().anyMatch(frame -> frame.getMethod().getName().equals(allocator)));
                foundPayload |= type.equals(Payload.class.getName());
                foundArray |= type.equals(byte[].class.getName());
            }
        }
        assertTrue("missing instance allocation samples", foundPayload);
        assertTrue("missing array allocation samples", foundArray);
    }

    @NeverInline("Keep the allocating method in the recorded stack.")
    private static void allocatePayloads() {
        for (int i = 0; i < 1_000_000; i++) {
            allocated = new Payload(i);
        }
    }

    @NeverInline("Keep the allocating method in the recorded stack.")
    private static void allocateArrays() {
        for (int i = 0; i < 64; i++) {
            allocated = new byte[1024 * 1024];
        }
    }

    private static final class Payload {
        final int value;

        Payload(int value) {
            this.value = value;
        }
    }
}
