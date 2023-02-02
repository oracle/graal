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
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;

import com.oracle.svm.core.jfr.JfrChunkWriter;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.JfrType;

import com.oracle.svm.test.jfr.utils.poolparsers.ClassConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ClassLoaderConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.FrameTypeConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.GCCauseConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.GCNameConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.MethodConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ModuleConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.PackageConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.StacktraceConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.SymbolConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ThreadConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ThreadGroupConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.ThreadStateConstantPoolParser;
import com.oracle.svm.test.jfr.utils.poolparsers.VMOperationConstantPoolParser;
import jdk.jfr.Recording;
import org.junit.Assert;

public class JfrFileParser {

    private static final HashMap<Long, ConstantPoolParser> supportedConstantPools;

    static {
        supportedConstantPools = new HashMap<>();

        supportedConstantPools.put(JfrType.Class.getId(), new ClassConstantPoolParser());
        supportedConstantPools.put(JfrType.ClassLoader.getId(), new ClassLoaderConstantPoolParser());
        supportedConstantPools.put(JfrType.Package.getId(), new PackageConstantPoolParser());
        supportedConstantPools.put(JfrType.Module.getId(), new ModuleConstantPoolParser());

        supportedConstantPools.put(JfrType.Symbol.getId(), new SymbolConstantPoolParser());
        supportedConstantPools.put(JfrType.Method.getId(), new MethodConstantPoolParser());
        supportedConstantPools.put(JfrType.StackTrace.getId(), new StacktraceConstantPoolParser());
        supportedConstantPools.put(JfrType.FrameType.getId(), new FrameTypeConstantPoolParser());

        supportedConstantPools.put(JfrType.Thread.getId(), new ThreadConstantPoolParser());
        supportedConstantPools.put(JfrType.ThreadGroup.getId(), new ThreadGroupConstantPoolParser());
        supportedConstantPools.put(JfrType.ThreadState.getId(), new ThreadStateConstantPoolParser());

        supportedConstantPools.put(JfrType.GCName.getId(), new GCNameConstantPoolParser());
        supportedConstantPools.put(JfrType.GCCause.getId(), new GCCauseConstantPoolParser());
        supportedConstantPools.put(JfrType.VMOperation.getId(), new VMOperationConstantPoolParser());
    }

    public static HashMap<Long, ConstantPoolParser> getSupportedConstantPools() {
        return supportedConstantPools;
    }

    private static Positions parserFileHeader(RecordingInput input) throws IOException {
        byte[] fileMagic = new byte[JfrChunkWriter.FILE_MAGIC.length];
        input.readFully(fileMagic); // File magic.
        assertEquals("File magic is not correct!", new String(JfrChunkWriter.FILE_MAGIC), new String(fileMagic));
        assertEquals("JFR version major is not correct!", JfrChunkWriter.JFR_VERSION_MAJOR, input.readRawShort());
        assertEquals("JFR version minor is not correct!", JfrChunkWriter.JFR_VERSION_MINOR, input.readRawShort());
        assertTrue("Chunk size is invalid!", input.readRawLong() > 0); // Chunk size.

        long constantPoolPosition = input.readRawLong();
        assertTrue("Constant pool positions is invalid!", constantPoolPosition > 0);
        long metadataPosition = input.readRawLong();
        assertTrue("Metadata positions is null!", metadataPosition != 0);

        long startingTime = input.readRawLong();
        assertTrue("Starting time is invalid!", startingTime > 0); // Starting time.
        Assert.assertTrue("Starting time is bigger than current time!", startingTime < JfrTicks.currentTimeNanos());
        input.readRawLong(); // Duration.
        assertTrue("Chunk start tick is invalid!", input.readRawLong() > 0); // ChunkStartTick.
        assertTrue("Tick frequency is invalid!", input.readRawLong() > 0); // Tick frequency.
        int shouldUseCompressedInt = input.readRawInt();
        assertTrue("Compressed int must be either 0 or 1!", shouldUseCompressedInt == 0 || shouldUseCompressedInt == 1); // ChunkWriteTick.

        return new Positions(constantPoolPosition, metadataPosition);
    }

    private static void parseMetadataHeader(RecordingInput input, long metadataPosition) throws IOException {
        input.position(metadataPosition); // Seek to starting position of metadata region.
        assertTrue("Metadata size is invalid!", input.readInt() > 0); // Size of metadata.
        assertEquals(JfrChunkWriter.METADATA_TYPE_ID, input.readLong()); // Metadata region ID.
        assertTrue("Metadata timestamp is invalid!", input.readLong() > 0); // Timestamp.
        input.readLong(); // Duration.
        input.readLong(); // Metadata ID.
    }

    private static void parseMetadata(RecordingInput input, long metadataPosition) throws IOException {
        parseMetadataHeader(input, metadataPosition);
        MetadataDescriptor.read(input);
    }

    private static long parseConstantPoolHeader(RecordingInput input, long constantPoolPosition) throws IOException {
        input.position(constantPoolPosition); // Seek to starting position of constant pools.
        // Size of constant pools.
        assertTrue("Constant pool size is invalid!", input.readInt() > 0);
        // Constant pools region ID.
        assertEquals(JfrChunkWriter.CONSTANT_POOL_TYPE_ID, input.readLong());
        assertTrue("Constant pool timestamp is invalid!", input.readLong() > 0); // Timestamp.
        input.readLong(); // Duration.
        long deltaNext = input.readLong(); // Offset to a next constant pools region.
        assertTrue(input.readBoolean()); // Flush.
        return deltaNext;
    }

    private static void compareFoundAndExpectedIds() {
        for (ConstantPoolParser parser : supportedConstantPools.values()) {
            parser.compareFoundAndExpectedIds();
        }
    }

    private static void verifyConstantPools(RecordingInput input, long constantPoolPosition) throws IOException {
        long deltaNext;
        long currentConstantPoolPosition = constantPoolPosition;
        do {
            deltaNext = parseConstantPoolHeader(input, currentConstantPoolPosition);
            long numberOfCPs = input.readInt();
            for (int i = 0; i < numberOfCPs; i++) {
                ConstantPoolParser constantPoolParser = supportedConstantPools.get(input.readLong());
                Assert.assertNotNull("Unknown constant pool!", constantPoolParser);
                constantPoolParser.parse(input);
            }
            compareFoundAndExpectedIds();
            currentConstantPoolPosition += deltaNext;
        } while (deltaNext != 0);
    }

    public static void parse(Recording recording) throws IOException {
        RecordingInput input = new RecordingInput(recording.getDestination().toFile());
        Positions positions = parserFileHeader(input);
        verifyConstantPools(input, positions.getConstantPoolPosition());
        parseMetadata(input, positions.getMetadataPosition());
    }

    private static class Positions {

        private final long constantPoolPosition;
        private final long metadataPosition;

        Positions(long constantPoolPosition, long metadataPositions) {
            this.constantPoolPosition = constantPoolPosition;
            this.metadataPosition = metadataPositions;
        }

        public long getConstantPoolPosition() {
            return constantPoolPosition;
        }

        public long getMetadataPosition() {
            return metadataPosition;
        }
    }
}
