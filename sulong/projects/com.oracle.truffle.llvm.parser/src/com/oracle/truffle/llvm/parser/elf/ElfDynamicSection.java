/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.polyglot.io.ByteSequence;

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
    private final ByteSequence buffer;

    private ElfDynamicSection(ElfSectionHeaderTable sht, Entry[] entries, ElfReader buffer) {
        this.entries = entries;
        long strTabAddress = Arrays.stream(entries).filter(e -> e.getTag() == DT_STRTAB).map(e -> addressToOffset(sht, e.getValue())).findAny().orElse(0L);
        long strTabSize = Arrays.stream(entries).filter(e -> e.getTag() == DT_STRSZ).map(e -> e.getValue()).findAny().orElse(0L);
        this.buffer = buffer.getStringTable(strTabAddress, strTabSize);
    }

    public static ElfDynamicSection create(ElfSectionHeaderTable sht, ElfReader buffer) {
        ElfSectionHeaderTable.Entry dynamiSHEntry = getDynamiSHEntry(sht);
        if (dynamiSHEntry != null) {
            long offset = dynamiSHEntry.getOffset();
            long size = dynamiSHEntry.getSize();
            return new ElfDynamicSection(sht, readEntries(buffer, offset, size), buffer);
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

    private static Entry[] readEntries(final ElfReader buffer, long offset, long size) {
        if (size == 0) {
            return new Entry[0];
        }
        buffer.setPosition((int) offset);

        // load each of the section header entries
        List<Entry> entries = new ArrayList<>();
        for (long cntr = 0; cntr < size; cntr += buffer.is64Bit() ? 16 : 8) {
            long tag = buffer.is64Bit() ? buffer.getLong() : buffer.getInt();
            long unionValue = buffer.is64Bit() ? buffer.getLong() : buffer.getInt();
            entries.add(new Entry(tag, unionValue));
        }
        return entries.toArray(new Entry[entries.size()]);
    }

    public List<String> getDTNeeded() {
        return getEntry(DT_NEEDED);
    }

    private static Stream<String> splitPaths(String path) {
        return Arrays.asList(path.split(":")).stream();
    }

    public Stream<String> getDTRunPathStream() {
        return getEntryStream(DT_RUNPATH).flatMap(ElfDynamicSection::splitPaths);
    }

    public List<String> getDTRPath() {
        return getEntryStream(DT_RPATH).flatMap(ElfDynamicSection::splitPaths).collect(Collectors.toList());
    }

    private static ElfSectionHeaderTable.Entry getDynamiSHEntry(ElfSectionHeaderTable sht) {
        for (ElfSectionHeaderTable.Entry e : sht.getEntries()) {
            if (".dynamic".equals(e.getName(sht))) {
                return e;
            }
        }
        return null;
    }

    private List<String> getEntry(int tag) {
        return getEntryStream(tag).collect(Collectors.toList());
    }

    private Stream<String> getEntryStream(int tag) {
        return Arrays.stream(entries).filter(e -> e.getTag() == tag).map(e -> getString(e.getValue()));
    }

    private String getString(long offset) {
        if (buffer.length() == 0) {
            return "";
        }

        int pos = (int) offset;
        StringBuilder sb = new StringBuilder();

        byte b = buffer.byteAt(pos++);
        while (b != 0) {
            sb.append((char) b);
            b = buffer.byteAt(pos++);
        }

        return sb.toString();
    }
}
