/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;
import org.graalvm.polyglot.io.ByteSequence;

public final class ElfSectionHeaderTable {
    private static final int ELF32_SHTENT_SIZE = 40;
    private static final int ELF64_SHTENT_SIZE = 64;

    public static final class Entry {
        private final int shName;
        private final int shType;
        private final long shFlags;
        private final long shAddr;
        private final long shOffset;
        private final long shSize;
        private final int shLink;
        private final int shInfo;
        private final long shAddralign;
        private final long shEntsize;

        private Entry(int shName, int shType, long shFlags, long shAddr, long shOffset, long shSize, int shLink, int shInfo, long shAddralign, long shEntsize) {
            assert shSize < Long.MAX_VALUE;
            this.shName = shName;
            this.shType = shType;
            this.shFlags = shFlags;
            this.shAddr = shAddr;
            this.shOffset = shOffset;
            this.shSize = shSize;
            this.shLink = shLink;
            this.shInfo = shInfo;
            this.shAddralign = shAddralign;
            this.shEntsize = shEntsize;
        }

        public int getType() {
            return shType;
        }

        protected long getFlags() {
            return shFlags;
        }

        public String getName(ElfSectionHeaderTable sht) {
            return sht.getString(shName);
        }

        public long getOffset() {
            return shOffset;
        }

        public long getSize() {
            return shSize;
        }

        public int getLink() {
            return shLink;
        }

        public long getEntrySize() {
            return shEntsize;
        }

        public long getShAddr() {
            return shAddr;
        }

        public long getShAddralign() {
            return shAddralign;
        }

        public long getShEntsize() {
            return shEntsize;
        }

        public long getShFlags() {
            return shFlags;
        }

        public int getShInfo() {
            return shInfo;
        }

        public int getShLink() {
            return shLink;
        }

        public int getShName() {
            return shName;
        }

        public long getShOffset() {
            return shOffset;
        }

        public long getShSize() {
            return shSize;
        }

        public int getShType() {
            return shType;
        }
    }

    private final Entry[] entries;
    private final Map<Integer, String> stringMap;
    private final ByteSequence stringTable;

    private ElfSectionHeaderTable(Entry[] entries, ByteSequence stringTable) {
        this.entries = entries;
        this.stringMap = new HashMap<>();
        this.stringTable = stringTable;
    }

    public static ElfSectionHeaderTable create(ElfHeader header, ElfReader buffer) {
        Entry[] entries = new Entry[header.getShnum()];
        buffer.setPosition((int) header.getShoff());
        for (int cntr = 0; cntr < entries.length; cntr++) {
            entries[cntr] = readEntry(header, buffer);
        }

        // read string table
        ByteSequence data = null;
        if (header.getShstrndx() < entries.length) {
            Entry e = entries[header.getShstrndx()];
            if (e.getSize() > 0) {
                data = buffer.getStringTable(e.getOffset(), e.getSize());
            }
        }
        return new ElfSectionHeaderTable(entries, data);
    }

    public Entry[] getEntries() {
        return entries;
    }

    public Entry getEntry(String name) {
        for (Entry e : entries) {
            if (e.getName(this).equals(name)) {
                return e;
            }
        }
        return null;
    }

    private String getString(int ind) {
        if (stringTable == null || ind >= stringTable.length()) {
            return "";
        }
        String str = stringMap.get(ind);
        if (str == null) {
            final StringBuilder buf = new StringBuilder();
            int pos = ind;
            byte b = stringTable.byteAt(pos++);
            while (b != 0) {
                buf.append((char) b);
                b = stringTable.byteAt(pos++);
            }
            str = buf.toString();
            stringMap.put(ind, str);
        }
        return str;
    }

    private static Entry readEntry(ElfHeader header, ElfReader buffer) {
        if (buffer.is64Bit()) {
            return readEntry64(header, buffer);
        } else {
            return readEntry32(header, buffer);
        }
    }

    private static Entry readEntry32(ElfHeader header, ElfReader buffer) {
        int shName = buffer.getInt();
        int shType = buffer.getInt();
        long shFlags = buffer.getInt();
        long shAddr = buffer.getInt();
        long shOffset = buffer.getInt();
        long shSize = buffer.getInt();
        int shLink = buffer.getInt();
        int shInfo = buffer.getInt();
        long shAddralign = buffer.getInt();
        long shEntsize = buffer.getInt();

        buffer.skip(header.getShentsize() - ELF32_SHTENT_SIZE);

        return new Entry(shName, shType, shFlags, shAddr, shOffset, shSize, shLink, shInfo, shAddralign, shEntsize);
    }

    private static Entry readEntry64(ElfHeader header, ElfReader buffer) {
        int shName = buffer.getInt();
        int shType = buffer.getInt();
        long shFlags = buffer.getLong();
        long shAddr = buffer.getLong();
        long shOffset = buffer.getLong();
        long shSize = buffer.getLong();
        int shLink = buffer.getInt();
        int shInfo = buffer.getInt();
        long shAddralign = buffer.getLong();
        long shEntsize = buffer.getLong();

        buffer.skip(header.getShentsize() - ELF64_SHTENT_SIZE);

        return new Entry(shName, shType, shFlags, shAddr, shOffset, shSize, shLink, shInfo, shAddralign, shEntsize);
    }
}
