/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import org.graalvm.polyglot.io.ByteSequence;

public final class ElfReader {

    private static final int EI_NIDENT = 16;
    private static final int EI_CLASS = 4;
    private static final int EI_DATA = 5;
    private static final int ELFDATA2MSB = 2;
    private static final int ELFCLASS64 = 2;

    private final ByteSequence byteSequence;
    private final boolean bigEndian;
    private final boolean is64Bit;

    private int position;

    ElfReader(ByteSequence byteSequence) {
        checkIdent(byteSequence);

        this.byteSequence = byteSequence;
        this.bigEndian = isBigEndian(byteSequence);
        this.is64Bit = is64Bit(byteSequence);

        setPosition(EI_NIDENT);
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void skip(int bytes) {
        this.position += bytes;
    }

    public boolean is64Bit() {
        return is64Bit;
    }

    public byte getByte() {
        return byteSequence.byteAt(position++);
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

    public ByteSequence getStringTable(long offset, long size) {
        return byteSequence.subSequence((int) offset, (int) (offset + size));
    }

    private static boolean isBigEndian(ByteSequence ident) {
        return ident.byteAt(EI_DATA) == ELFDATA2MSB;
    }

    private static boolean is64Bit(ByteSequence ident) {
        return ident.byteAt(EI_CLASS) == ELFCLASS64;
    }

    private static void checkIdent(ByteSequence ident) {
        checkIndentByte(ident, 0, 0x7f);
        checkIndentByte(ident, 1, 'E');
        checkIndentByte(ident, 2, 'L');
        checkIndentByte(ident, 3, 'F');
    }

    private static void checkIndentByte(ByteSequence ident, int ind, int val) {
        if (ident.byteAt(ind) != val) {
            throw new LLVMParserException("Invalid ELF file!");
        }
    }
}
