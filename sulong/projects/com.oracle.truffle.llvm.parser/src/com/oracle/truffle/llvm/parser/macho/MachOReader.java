/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.macho;

import org.graalvm.polyglot.io.ByteSequence;

public final class MachOReader {

    private static final long MH_MAGIC = 0xFEEDFACEL;
    private static final long MH_CIGAM = 0xCEFAEDFEL;
    private static final long MH_MAGIC_64 = 0xFEEDFACFL;
    private static final long MH_CIGAM_64 = 0xCFFAEDFEL;

    private static final String INTERMEDIATE_SEGMENT = "";
    private static final String BITCODE_SECTION = "__bitcode";
    private static final String WLLVM_SEGMENT = "__WLLVM";
    private static final String WLLVM_BITCODE_SECTION = "__llvm_bc";

    private static final int MH_OBJECT = 0x1; /* relocatable object file */
    private static final int MH_EXECUTE = 0x2; /* demand paged executable file */
    // currently unused types:
    @SuppressWarnings("unused") private static final int MH_FVMLIB = 0x3;
    @SuppressWarnings("unused") private static final int MH_CORE = 0x4;
    @SuppressWarnings("unused") private static final int MH_PRELOAD = 0x5;
    @SuppressWarnings("unused") private static final int MH_DYLIB = 0x6;
    @SuppressWarnings("unused") private static final int MH_DYLINKER = 0x7;
    @SuppressWarnings("unused") private static final int MH_BUNDLE = 0x8;
    @SuppressWarnings("unused") private static final int MH_DYLIB_STUB = 0x9;

    private final ByteSequence byteSequence;
    private final boolean bigEndian;
    private final boolean is64Bit;

    private int position;

    private MachOReader(ByteSequence buffer) {
        this.byteSequence = buffer;
        this.position = 0;

        int ret = byteSequence.byteAt(position++) & 0xff;
        ret = (ret << 8) | (byteSequence.byteAt(position++) & 0xff);
        ret = (ret << 8) | (byteSequence.byteAt(position++) & 0xff);
        ret = (ret << 8) | (byteSequence.byteAt(position++) & 0xff);
        long magic = Integer.toUnsignedLong(ret);

        if (!MachOFile.isMachOMagicNumber(magic)) {
            throw new IllegalArgumentException("Invalid Mach-O file!");
        }

        is64Bit = isMachO64MagicNumber(magic);
        bigEndian = !isReversedByteOrder(magic);
    }

    public static MachOFile create(ByteSequence buffer) {
        MachOReader reader = new MachOReader(buffer);
        MachOHeader header = MachOHeader.create(reader);
        MachOLoadCommandTable loadCommandTable = MachOLoadCommandTable.create(header, reader);
        return new MachOFile(header, loadCommandTable, reader.byteSequence);
    }

    public boolean is64Bit() {
        return is64Bit;
    }

    public byte getByte() {
        return byteSequence.byteAt(position++);
    }

    public int position() {
        return position;
    }

    public void position(int newPosition) {
        assert position <= newPosition;
        position = newPosition;
    }

    public short getShort() {
        int ret = getByte() & 0xff;
        ret = (ret << 8) | (getByte() & 0xff);

        if (bigEndian) {
            return (short) ret;
        } else {
            return Short.reverseBytes((short) ret);
        }
    }

    public int getInt() {
        int ret = getByte() & 0xff;
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);

        if (bigEndian) {
            return ret;
        } else {
            return Integer.reverseBytes(ret);
        }
    }

    public long getLong() {
        long ret = getByte() & 0xff;
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);

        if (bigEndian) {
            return ret;
        } else {
            return Long.reverseBytes(ret);
        }
    }

    private static boolean isMachO64MagicNumber(long magic) {
        return magic == MH_MAGIC_64 || magic == MH_CIGAM_64;
    }

    private static boolean isReversedByteOrder(long magic) {
        return magic == MH_CIGAM || magic == MH_CIGAM_64;
    }

    public byte getByte(int pos) {
        return byteSequence.byteAt(pos++);
    }

    public int getInt(int pos) {
        int ret = getByte(pos) & 0xff;
        ret = (ret << 8) | (getByte(pos + 1) & 0xff);
        ret = (ret << 8) | (getByte(pos + 2) & 0xff);
        ret = (ret << 8) | (getByte(pos + 3) & 0xff);

        if (bigEndian) {
            return ret;
        } else {
            return Integer.reverseBytes(ret);
        }
    }
}
