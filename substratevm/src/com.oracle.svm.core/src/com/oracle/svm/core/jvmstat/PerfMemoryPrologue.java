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

import jdk.graal.compiler.word.Word;

/**
 * Class representing the 2.0 version of the HotSpot PerfData instrumentation buffer header.
 * <p>
 * The PerfDataBufferPrologue class supports parsing of the version specific portions of the
 * PerfDataPrologue C structure:
 *
 * <pre>
 * typedef struct {
 *   jint   magic;            // magic number - 0xcafec0c0
 *   jbyte  byte_order;       // byte order of the buffer
 *   jbyte  major_version;    // major and minor version numbers
 *   jbyte  minor_version;
 *   jbyte  accessible;       // ready to access
 *   jint used;               // number of PerfData memory bytes used
 *   jint overflow;           // number of bytes of overflow
 *   jlong mod_time_stamp;    // time stamp of the last structural modification
 *   jint entry_offset;       // offset of the first PerfDataEntry
 *   jint num_entries;        // number of allocated PerfData entries
 * } PerfDataPrologue
 * </pre>
 *
 */
public final class PerfMemoryPrologue {

    private static final byte MAJOR_VERSION = 2;
    private static final byte MINOR_VERSION = 0;

    // these constants should match their #define counterparts in perfMemory.hpp
    static final byte PERFDATA_BIG_ENDIAN = 0;
    static final byte PERFDATA_LITTLE_ENDIAN = 1;
    static final int PERFDATA_MAGIC = 0xcafec0c0;

    /*
     * the following constants must match the field offsets and sizes in the PerfDataPrologue
     * structure in perfMemory.hpp
     */
    static final int PERFDATA_PROLOGUE_OFFSET = 0;
    static final int PERFDATA_PROLOGUE_MAGIC_OFFSET = 0;
    static final int PERFDATA_PROLOGUE_BYTEORDER_OFFSET = 4;
    static final int PERFDATA_PROLOGUE_BYTEORDER_SIZE = 1;         // sizeof(byte)
    static final int PERFDATA_PROLOGUE_MAJOR_OFFSET = 5;
    static final int PERFDATA_PROLOGUE_MAJOR_SIZE = 1;             // sizeof(byte)
    static final int PERFDATA_PROLOGUE_MINOR_OFFSET = 6;
    static final int PERFDATA_PROLOGUE_MINOR_SIZE = 1;             // sizeof(byte)
    static final int PERFDATA_PROLOGUE_ACCESSIBLE_OFFSET = 7;
    static final int PERFDATA_PROLOGUE_ACCESSIBLE_SIZE = 1;        // sizeof(byte)
    static final int PERFDATA_PROLOGUE_USED_OFFSET = 8;
    static final int PERFDATA_PROLOGUE_USED_SIZE = 4;              // sizeof(int)
    static final int PERFDATA_PROLOGUE_OVERFLOW_OFFSET = 12;
    static final int PERFDATA_PROLOGUE_OVERFLOW_SIZE = 4;          // sizeof(int)
    static final int PERFDATA_PROLOGUE_MODTIMESTAMP_OFFSET = 16;
    static final int PERFDATA_PROLOGUE_MODTIMESTAMP_SIZE = 8;      // sizeof(long)
    static final int PERFDATA_PROLOGUE_ENTRYOFFSET_OFFSET = 24;
    static final int PERFDATA_PROLOGUE_ENTRYOFFSET_SIZE = 4;       // sizeof(int)
    static final int PERFDATA_PROLOGUE_NUMENTRIES_OFFSET = 28;
    static final int PERFDATA_PROLOGUE_NUMENTRIES_SIZE = 4;        // sizeof(int)

    static final int PERFDATA_PROLOGUE_SIZE = 32;  // sizeof(struct PerfDataPrologue)

    // names for counters that expose prologue fields
    static final String PERFDATA_MAJOR_NAME = "sun.perfdata.majorVersion";
    static final String PERFDATA_MINOR_NAME = "sun.perfdata.minorVersion";
    static final String PERFDATA_BUFFER_SIZE_NAME = "sun.perfdata.size";
    static final String PERFDATA_BUFFER_USED_NAME = "sun.perfdata.used";
    static final String PERFDATA_OVERFLOW_NAME = "sun.perfdata.overflow";
    static final String PERFDATA_MODTIMESTAMP_NAME = "sun.perfdata.timestamp";
    static final String PERFDATA_NUMENTRIES_NAME = "sun.perfdata.entries";

    private PerfMemoryPrologue() {
    }

    public static void initialize(Word perfMemory, boolean platformIsBigEndian) {
        Word pos = perfMemory.add(PERFDATA_PROLOGUE_MAGIC_OFFSET);

        // the magic number is always stored in big-endian format
        pos.writeInt(0, platformIsBigEndian ? PERFDATA_MAGIC : Integer.reverseBytes(PERFDATA_MAGIC));
        pos = pos.add(Integer.BYTES);

        pos.writeByte(0, platformIsBigEndian ? PERFDATA_BIG_ENDIAN : PERFDATA_LITTLE_ENDIAN);
        pos = pos.add(1);

        pos.writeByte(0, MAJOR_VERSION);
        pos = pos.add(1);

        pos.writeByte(0, MINOR_VERSION);
        pos = pos.add(1);

        // not yet accessible
        pos.writeByte(0, (byte) 0);
        pos = pos.add(1);

        // used = 0
        pos.writeInt(0, 0);
        pos = pos.add(Integer.BYTES);

        // overflow = 0
        pos.writeInt(0, 0);
        pos = pos.add(Integer.BYTES);

        // mod_time_stamp = 0;
        pos.writeLong(0, 0);
        pos = pos.add(Long.BYTES);

        // entry_offset = PERFDATA_PROLOG_SIZE;
        pos.writeInt(0, PERFDATA_PROLOGUE_SIZE);
        pos = pos.add(Integer.BYTES);

        // num_entries = 0
        pos.writeInt(0, 0);
        pos = pos.add(Integer.BYTES);
    }

    /**
     * Get the buffer overflow amount. This value is non-zero if the SubstrateVM has overflowed the
     * instrumentation memory buffer. The target SubstrateVM can be restarted with
     * -XX:PerfDataMemSize=X to create a larger memory buffer.
     *
     * @return int - overflow amount in bytes
     */
    public static int getOverflow(Word perfMemory) {
        return perfMemory.readInt(PERFDATA_PROLOGUE_OVERFLOW_OFFSET);
    }

    public static void setOverflow(Word perfMemory, int overflowSize) {
        perfMemory.writeInt(PERFDATA_PROLOGUE_OVERFLOW_OFFSET, overflowSize);
    }

    public static void addOverflow(Word perfMemory, int size) {
        int oldOverflow = getOverflow(perfMemory);
        setOverflow(perfMemory, oldOverflow + size);
    }

    public static void setAccessible(Word perfMemory, boolean value) {
        perfMemory.writeByte(PERFDATA_PROLOGUE_ACCESSIBLE_OFFSET, (byte) (value ? 1 : 0));
    }

    public static void markUpdated(Word perfMemory, long initialTime) {
        perfMemory.writeLong(PERFDATA_PROLOGUE_MODTIMESTAMP_OFFSET, System.nanoTime() - initialTime);
    }

    public static int getPrologueSize() {
        return PERFDATA_PROLOGUE_SIZE;
    }

    /**
     * Get the number of entries.
     */
    public static int getNumEntries(Word perfMemory) {
        return perfMemory.readInt(PERFDATA_PROLOGUE_NUMENTRIES_OFFSET);
    }

    public static void incrementNumEntries(Word perfMemory) {
        perfMemory.writeInt(PERFDATA_PROLOGUE_NUMENTRIES_OFFSET, getNumEntries(perfMemory) + 1);
    }

    public static int getUsed(Word perfMemory) {
        return perfMemory.readInt(PERFDATA_PROLOGUE_USED_OFFSET);
    }

    public static void setUsed(Word perfMemory, int top) {
        perfMemory.writeInt(PERFDATA_PROLOGUE_USED_OFFSET, top);
    }
}
