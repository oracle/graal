/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.classfile;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Operations for sequentially scanning data items in a class file. Any IO exceptions that occur
 * during scanning are converted to {@link ClassFormatError}s.
 */
public final class ClassfileStream {
    private final ClasspathFile classfile;
    private final ByteBuffer data;
    private final byte[] bytes;

    public ClassfileStream(byte[] bytes, ClasspathFile classfile) {
        this.bytes = bytes;
        this.data = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        this.classfile = classfile;
    }

    public char readChar() {
        try {
            return data.getChar();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public float readFloat() {
        try {
            return data.getFloat();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public long readS8() {
        try {
            return data.getLong();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public double readDouble() {
        try {
            return data.getDouble();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public int readU1() {
        try {
            return data.get() & 0xff;
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public int readU2() {
        try {
            return data.getChar();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public int readS4() {
        try {
            return data.getInt();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public int readS1() {
        try {
            return data.get();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public int readS2() {
        try {
            return data.getShort();
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public byte[] readByteArray(int len) {
        try {
            final byte[] buf = new byte[len];
            data.get(buf);
            return buf;
        } catch (BufferUnderflowException e) {
            throw eofError();
        }
    }

    public ByteSequence readByteSequenceUTF() {
        int utflen = readU2();
        int start = getPosition();
        skip(utflen);
        return ByteSequence.wrap(bytes, start, utflen);
    }

    public void skip(int nBytes) {
        try {
            data.position(data.position() + nBytes);
        } catch (IllegalArgumentException e) {
            throw eofError();
        }
    }

    byte[] getByteRange(int startPosition, int numBytes) {
        try {
            byte[] result = new byte[numBytes];
            System.arraycopy(bytes, startPosition, result, 0, numBytes);
            return result;
        } catch (IndexOutOfBoundsException e) {
            throw eofError();
        }
    }

    public boolean isAtEndOfFile() {
        return data.remaining() == 0;
    }

    public void checkEndOfFile() {
        if (!isAtEndOfFile()) {
            throw classFormatError("Extra bytes", classfile);
        }
    }

    public int getPosition() {
        return data.position();
    }

    public ClassFormatError classFormatError(String format, Object... args) {
        Meta meta = EspressoContext.get(null).getMeta();
        throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, String.format(format, args) + " in classfile " + classfile);
    }

    public ClassFormatError ioError(IOException ioException) {
        throw classFormatError("%s", ioException);
    }

    public ClassFormatError eofError() {
        throw classFormatError("Truncated class file");
    }
}
