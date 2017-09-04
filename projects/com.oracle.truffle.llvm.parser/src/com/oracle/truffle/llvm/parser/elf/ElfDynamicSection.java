/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.elf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ElfDynamicSection {

    private static final int DT_NEEDED = 1;
    private static final int DT_STRTAB = 5;
    private static final int DT_STRSZ = 10;
    private static final int DT_RPATH = 15;
    private static final int DT_RUNPATH = 29;

    private static final class Entry {
        private final long tag;
        // 2 union values: word, addr
        private final long unionValue;

        private Entry(long tag, long value) {
            this.tag = tag;
            this.unionValue = value;
        }

        public int getTag() {
            return (int) tag;
        }

        public long getValue() {
            return unionValue;
        }

    }

    private final Entry[] entries;
    private final ByteBuffer buffer;
    private final long strTabAddress;
    private final long strTabSize;

    private ElfDynamicSection(ElfSectionHeaderTable sht, Entry[] entries, ByteBuffer buffer) {
        this.entries = entries;
        this.buffer = buffer.duplicate();
        this.strTabAddress = Arrays.stream(entries).filter(e -> e.getTag() == DT_STRTAB).map(e -> addressToOffset(sht, e.getValue())).findAny().orElse(0L);
        this.strTabSize = Arrays.stream(entries).filter(e -> e.getTag() == DT_STRSZ).map(e -> e.getValue()).findAny().orElse(0L);
    }

    public static ElfDynamicSection create(ElfSectionHeaderTable sht, ByteBuffer buffer, boolean is64Bit) {
        ElfSectionHeaderTable.Entry dynamiSHEntry = getDynamiSHEntry(sht);
        if (dynamiSHEntry != null) {
            long offset = dynamiSHEntry.getOffset();
            long size = dynamiSHEntry.getSize();
            return new ElfDynamicSection(sht, readEntries(buffer, is64Bit, offset, size), buffer);
        } else {
            return null;
        }
    }

    private static long addressToOffset(ElfSectionHeaderTable sht, long offset) {
        for (ElfSectionHeaderTable.Entry e : sht.getEntries()) {
            long lower = e.getShAddr();
            long upper = e.getShSize() + e.getShAddr();
            if (offset >= lower && offset < upper) {
                return offset - lower + e.getOffset();
            }
        }
        return offset;
    }

    private static Entry[] readEntries(final ByteBuffer buffer, boolean is64Bit, long offset, long size) {
        if (size == 0) {
            return new Entry[0];
        }
        buffer.position((int) offset);

        // load each of the section header entries
        List<Entry> entries = new ArrayList<>();
        for (long cntr = 0; cntr < size; cntr += is64Bit ? 16 : 8) {
            long tag = is64Bit ? buffer.getLong() : buffer.getInt();
            long unionValue = is64Bit ? buffer.getLong() : buffer.getInt();
            entries.add(new Entry(tag, unionValue));
        }
        return entries.toArray(new Entry[entries.size()]);
    }

    public List<String> getDTNeeded() {
        return getEntry(DT_NEEDED);
    }

    public List<String> getDTRunPath() {
        return getEntry(DT_RUNPATH);
    }

    public List<String> getDTRPath() {
        return getEntry(DT_RPATH);
    }

    private static ElfSectionHeaderTable.Entry getDynamiSHEntry(ElfSectionHeaderTable sht) {
        for (ElfSectionHeaderTable.Entry e : sht.getEntries()) {
            if (e.getName(sht).equals(".dynamic")) {
                return e;
            }
        }
        return null;
    }

    private List<String> getEntry(int tag) {
        return Arrays.stream(entries).filter(e -> e.getTag() == tag).map(e -> getString(e.getValue())).collect(Collectors.toList());
    }

    private String getString(long offset) {
        if (strTabAddress == 0 || strTabSize == 0) {
            return "";
        }

        buffer.position((int) (strTabAddress + offset));
        StringBuilder sb = new StringBuilder();

        byte b = buffer.get();
        while (b != 0) {
            sb.append((char) b);
            b = buffer.get();
        }

        return sb.toString();
    }

}
