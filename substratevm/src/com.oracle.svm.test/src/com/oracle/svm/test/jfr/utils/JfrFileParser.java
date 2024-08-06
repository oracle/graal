/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.jfr.JfrCheckpointType;
import com.oracle.svm.core.jfr.JfrChunkFileWriter;
import com.oracle.svm.core.jfr.JfrReservedEvent;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.utils.poolparsers.AbstractSerializerParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ClassConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ClassLoaderConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.FrameTypeConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.GCCauseConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.GCNameConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.GCWhenConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.MethodConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ModuleConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.MonitorInflationCauseConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.NmtCategoryConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.OldObjectConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.PackageConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.StacktraceConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.SymbolConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ThreadConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ThreadGroupConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ThreadStateConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.VMOperationConstantPoolParser;

public class JfrFileParser {
    private final Path path;
    private final HashMap<Long, ConstantPoolParser> supportedConstantPools = new HashMap<>();
    private final ArrayList<AbstractSerializerParser> serializerParsers = new ArrayList<>();

    @SuppressWarnings("this-escape")
    public JfrFileParser(Path path) {
        this.path = path;

        addParser(JfrType.Class, new ClassConstantPoolParser(this));
        addParser(JfrType.ClassLoader, new ClassLoaderConstantPoolParser(this));
        addParser(JfrType.Package, new PackageConstantPoolParser(this));
        addParser(JfrType.Module, new ModuleConstantPoolParser(this));

        addParser(JfrType.Symbol, new SymbolConstantPoolParser(this));
        addParser(JfrType.Method, new MethodConstantPoolParser(this));
        addParser(JfrType.StackTrace, new StacktraceConstantPoolParser(this));

        addParser(JfrType.Thread, new ThreadConstantPoolParser(this));
        addParser(JfrType.ThreadGroup, new ThreadGroupConstantPoolParser(this));

        addParser(JfrType.FrameType, new FrameTypeConstantPoolParser(this));
        addParser(JfrType.ThreadState, new ThreadStateConstantPoolParser(this));
        addParser(JfrType.GCName, new GCNameConstantPoolParser(this));
        addParser(JfrType.GCCause, new GCCauseConstantPoolParser(this));
        addParser(JfrType.VMOperation, new VMOperationConstantPoolParser(this));
        addParser(JfrType.MonitorInflationCause, new MonitorInflationCauseConstantPoolParser(this));
        addParser(JfrType.GCWhen, new GCWhenConstantPoolParser(this));
        addParser(JfrType.OldObject, new OldObjectConstantPoolParser(this));

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            addParser(JfrType.NMTType, new NmtCategoryConstantPoolParser(this));
        }
    }

    private void addParser(JfrType type, ConstantPoolParser parser) {
        supportedConstantPools.put(type.getId(), parser);
        if (parser instanceof AbstractSerializerParser p) {
            serializerParsers.add(p);
        }
    }

    public HashMap<Long, ConstantPoolParser> getSupportedConstantPools() {
        return supportedConstantPools;
    }

    public void verify() throws IOException {
        RecordingInput input = new RecordingInput(path.toFile());
        FileHeaderInfo header = parseFileHeader(input);
        parseMetadata(input, header.metadataPosition);

        Collection<Long> constantPoolOffsets = getConstantPoolOffsets(input, header.checkpointPosition);
        verifyConstantPools(input, constantPoolOffsets);
    }

    private static void parseMetadata(RecordingInput input, long metadataPosition) throws IOException {
        parseMetadataHeader(input, metadataPosition);
        MetadataDescriptor.read(input);
    }

    private static void parseMetadataHeader(RecordingInput input, long metadataPosition) throws IOException {
        input.position(metadataPosition);
        assertTrue("Metadata size is invalid!", input.readInt() > 0);
        assertEquals(JfrReservedEvent.METADATA.getId(), input.readLong());
        assertTrue("Metadata timestamp is invalid!", input.readLong() > 0);
        input.readLong(); // Duration.
        assertTrue("Metadata ID is invalid!", input.readLong() > 0);
    }

    private static FileHeaderInfo parseFileHeader(RecordingInput input) throws IOException {
        byte[] fileMagic = new byte[JfrChunkFileWriter.FILE_MAGIC.length];
        input.readFully(fileMagic); // File magic.
        assertEquals("File magic is not correct!", new String(JfrChunkFileWriter.FILE_MAGIC), new String(fileMagic));
        assertEquals("JFR version major is not correct!", JfrChunkFileWriter.JFR_VERSION_MAJOR, input.readRawShort());
        assertEquals("JFR version minor is not correct!", JfrChunkFileWriter.JFR_VERSION_MINOR, input.readRawShort());
        assertTrue("Chunk size is invalid!", input.readRawLong() > 0);

        long checkpointPosition = input.readRawLong();
        assertTrue("Checkpoint positions is invalid!", checkpointPosition > 0);
        long metadataPosition = input.readRawLong();
        assertTrue("Metadata position is invalid", metadataPosition > 0);

        long startingTime = input.readRawLong();
        assertTrue("Starting time is invalid!", startingTime > 0);
        assertTrue("Starting time is bigger than current time!", startingTime <= JfrTicks.currentTimeNanos());
        input.readRawLong(); // Duration.
        assertTrue("Chunk start tick is invalid!", input.readRawLong() > 0);
        assertTrue("Tick frequency is invalid!", input.readRawLong() > 0);
        int shouldUseCompressedInt = input.readRawInt();
        assertTrue("Compressed int must be either 0 or 1!", shouldUseCompressedInt == 0 || shouldUseCompressedInt == 1);

        return new FileHeaderInfo(checkpointPosition, metadataPosition);
    }

    private static ArrayDeque<Long> getConstantPoolOffsets(RecordingInput input, long initialCheckpointPosition) throws IOException {
        ArrayDeque<Long> constantPoolOffsets = new ArrayDeque<>();
        long deltaNext;
        long currentCheckpointPosition = initialCheckpointPosition;
        do {
            input.position(currentCheckpointPosition);
            assertTrue("Constant pool size is invalid!", input.readInt() > 0);
            assertEquals(JfrReservedEvent.CHECKPOINT.getId(), input.readLong());
            assertTrue("Constant pool timestamp is invalid!", input.readLong() > 0);
            input.readLong(); // Duration.
            deltaNext = input.readLong();
            assertTrue("Delta to next checkpoint is invalid!", deltaNext <= 0);
            byte checkpointType = input.readByte();
            assertTrue("Checkpoint type is invalid!", checkpointType == JfrCheckpointType.Flush.getId() || checkpointType == JfrCheckpointType.Threads.getId());

            constantPoolOffsets.addFirst(input.position());

            currentCheckpointPosition += deltaNext;
        } while (deltaNext != 0);

        return constantPoolOffsets;
    }

    private void verifyConstantPools(RecordingInput input, Collection<Long> constantPoolOffsets) throws IOException {
        for (Long offset : constantPoolOffsets) {
            input.position(offset);
            int numberOfCPs = input.readInt();
            for (int i = 0; i < numberOfCPs; i++) {
                ConstantPoolParser constantPoolParser = supportedConstantPools.get(input.readLong());
                assertNotNull("Unknown constant pool!", constantPoolParser);
                constantPoolParser.parse(input);
            }
            compareFoundAndExpectedIds();
        }

        verifySerializers();
    }

    private void verifySerializers() {
        for (ConstantPoolParser parser : serializerParsers) {
            assertFalse("Serializer data must always be present in the chunk.", parser.isEmpty());
        }
    }

    private void compareFoundAndExpectedIds() {
        for (ConstantPoolParser parser : supportedConstantPools.values()) {
            parser.compareFoundAndExpectedIds();
        }
    }

    private record FileHeaderInfo(long checkpointPosition, long metadataPosition) {
    }
}
