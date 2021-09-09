/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jdk11.jfr;

import jdk.jfr.Recording;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import java.io.IOException;

public class TestSingleEvent {
    private static final long METADATA_TYPE_ID = 0;
    private static final long CONSTANT_POOL_TYPE_ID = 1;

    @Test
    public void test() throws Exception {
        JFR jfr = new LocalJFR();
        Recording recording = jfr.startRecording("TestSingleEvent");

        StringEvent event = new StringEvent();
        event.message = "Event has been generated!";
        event.commit();

        jfr.endRecording(recording);
        try {
            RecordingInput input = new RecordingInput(recording.getDestination().toFile());
            input.position(16);
            long cpoolPos = input.readRawLong();
            long metadataPos = input.readRawLong();
            verifyMetadata(input, metadataPos);
            verifyConstantPools(input, cpoolPos);
        } finally {
            jfr.cleanupRecording(recording);
        }
    }

    private static void verifyMetadata(RecordingInput input, long metadataPos) throws IOException {
        input.position(metadataPos);
        input.readInt(); // size
        long id = input.readLong();
        assertEquals(METADATA_TYPE_ID, id);
        input.readLong(); // timestamp
        input.readLong(); // duration
        input.readLong(); // metadataId (seems to be always 0?)
        MetadataDescriptor.read(input);
    }

    private void verifyConstantPools(RecordingInput input, long cpoolPos) throws IOException {
        input.position(cpoolPos);
        input.readInt(); // size
        long typeId = input.readLong();
        assertEquals(CONSTANT_POOL_TYPE_ID, typeId);
        input.readLong(); // timestamp
        input.readLong(); // duration
        long delta = input.readLong();
        if (delta != 0) {
            verifyConstantPools(input, cpoolPos + delta);
        }
    }

    public static void main(String[] args) throws Exception {
        new TestSingleEvent().test();
    }
}
