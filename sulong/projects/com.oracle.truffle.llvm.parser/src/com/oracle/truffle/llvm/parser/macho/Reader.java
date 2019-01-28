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

public final class Reader {

    private final ByteSequence byteSequence;
    private final boolean littleEndian;

    private int position;

    public Reader(ByteSequence buffer, boolean littleEndian) {
        this.byteSequence = buffer;
        this.position = 0;
        this.littleEndian = littleEndian;
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

        if (littleEndian) {
            return Short.reverseBytes((short) ret);
        }
        return (short) ret;
    }

    public int getInt() {
        int ret = getByte() & 0xff;
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);
        ret = (ret << 8) | (getByte() & 0xff);

        if (littleEndian) {
            return Integer.reverseBytes(ret);
        }
        return ret;
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

        if (littleEndian) {
            return Long.reverseBytes(ret);
        }
        return ret;
    }

    public byte getByte(int pos) {
        return byteSequence.byteAt(pos++);
    }

    public int getInt(int pos) {
        int ret = getByte(pos) & 0xff;
        ret = (ret << 8) | (getByte(pos + 1) & 0xff);
        ret = (ret << 8) | (getByte(pos + 2) & 0xff);
        ret = (ret << 8) | (getByte(pos + 3) & 0xff);

        if (littleEndian) {
            return Integer.reverseBytes(ret);
        }
        return ret;
    }

    public ByteSequence slice() {
        return byteSequence.subSequence(position, byteSequence.length());
    }

    public void get(byte[] compressedData) {
        int length = compressedData.length;
        for (int writeIdx = 0; writeIdx < length; writeIdx++) {
            compressedData[writeIdx] = getByte();
        }
    }
}
