/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmstat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;

import jdk.vm.ci.meta.JavaKind;

/**
 * The {@link AbstractPerfDataEntry} class is provided for creation, access, and update of
 * performance data (a.k.a. instrumentation) in a specific memory region which is accessible as
 * shared memory.
 */
public abstract class AbstractPerfDataEntry implements PerfDataEntry {
    private static final String STABLE_SUPPORTED_PREFIX = "java.";
    private static final String UNSTABLE_SUPPORTED_PREFIX = "com.sun.";
    private static final byte F_SUPPORTED = 1;
    private static final int SIZEOF_PERFDATAENTRY = 20;

    private final byte[] name;
    private final byte flags;
    private final int unit;
    protected Word valuePtr;

    AbstractPerfDataEntry(String name, PerfUnit unit) {
        this.name = getBytes(name);
        this.flags = getFlags(name);
        this.unit = unit.intValue();
    }

    void allocate(PerfVariability variability, JavaKind type, int arraySize) {
        assert valuePtr.isNull();

        int metadataSize = computeMetadataSize(type);
        int valueSize = computeValueSize(type, arraySize);
        int totalSize = computeTotalSize(metadataSize, valueSize);

        PerfMemory perfMemory = ImageSingletons.lookup(PerfMemory.class);
        Word entryPtr = perfMemory.allocate(totalSize);

        int valueOffset = metadataSize;
        writeMetadata(entryPtr, totalSize, arraySize, type, variability, valueOffset);
        valuePtr = entryPtr.add(valueOffset);

        perfMemory.markUpdated();
    }

    private static int computeValueSize(JavaKind type, int arraySize) {
        int dSize = type.getByteCount();
        int dLen = arraySize == 0 ? 1 : arraySize;
        return dSize * dLen;
    }

    private int computeMetadataSize(JavaKind type) {
        int dSize = type.getByteCount();
        int namelen = name.length + 1;  // include null terminator
        int metadataSize = SIZEOF_PERFDATAENTRY + namelen;
        int padLength = ((metadataSize % dSize) == 0) ? 0 : dSize - (metadataSize % dSize);
        metadataSize += padLength;
        return metadataSize;
    }

    private static int computeTotalSize(int metadataSize, int valueSize) {
        return NumUtil.roundUp(metadataSize + valueSize, 8);
    }

    private void writeMetadata(Word entryStart, int totalSize, int arraySize, JavaKind type, PerfVariability variability, int valueOffset) {
        Word pos = entryStart;
        // entry length in bytes
        pos.writeInt(0, totalSize);
        pos = pos.add(Integer.BYTES);

        // offset of the data item name
        pos.writeInt(0, SIZEOF_PERFDATAENTRY);
        pos = pos.add(Integer.BYTES);

        // length of the vector. If 0, then scalar
        pos.writeInt(0, arraySize);
        pos = pos.add(Integer.BYTES);

        // type of the data item
        pos.writeByte(0, NumUtil.safeToByte(type.getTypeChar()));
        pos = pos.add(1);

        // flags indicating misc attributes
        pos.writeByte(0, flags);
        pos = pos.add(1);

        // unit of measure for the data type
        pos.writeByte(0, NumUtil.safeToByte(unit));
        pos = pos.add(1);

        // variability classification of data type
        pos.writeByte(0, NumUtil.safeToByte(variability.intValue()));
        pos = pos.add(1);

        // offset where the actual data starts
        pos.writeInt(0, valueOffset);
        pos = pos.add(Integer.BYTES);

        // write the name
        writeNullTerminatedString(pos, name);
    }

    private static byte getFlags(String name) {
        if (isStableSupported(name) || isUnstableSupported(name)) {
            return F_SUPPORTED;
        } else {
            return 0;
        }
    }

    private static boolean isStableSupported(String name) {
        return name.startsWith(STABLE_SUPPORTED_PREFIX);
    }

    private static boolean isUnstableSupported(String name) {
        return name.startsWith(UNSTABLE_SUPPORTED_PREFIX);
    }

    protected static void writeNullTerminatedString(Word pointer, byte[] bytes) {
        Word pos = writeBytes(pointer, bytes);
        pos.writeByte(0, (byte) 0);
        pos = pos.add(1);
    }

    protected static void writeNullTerminatedString(Word pointer, byte[] bytes, int length) {
        Word pos = writeBytes(pointer, bytes, length);
        pos.writeByte(0, (byte) 0);
        pos = pos.add(1);
    }

    protected static Word writeBytes(Word pointer, byte[] bytes) {
        return writeBytes(pointer, bytes, bytes.length);
    }

    protected static Word writeBytes(Word pointer, byte[] bytes, int length) {
        Word pos = pointer;
        for (int i = 0; i < length; i++) {
            pos.writeByte(0, bytes[i]);
            pos = pos.add(1);
        }
        return pos;
    }

    /**
     * Convert string to an array of UTF-8 bytes.
     */
    static byte[] getBytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] getBytes(String s, int maxLengthInBytes) {
        byte[] bytes = getBytes(s);
        if (bytes.length > maxLengthInBytes) {
            return Arrays.copyOf(bytes, maxLengthInBytes);
        }
        return bytes;
    }
}
