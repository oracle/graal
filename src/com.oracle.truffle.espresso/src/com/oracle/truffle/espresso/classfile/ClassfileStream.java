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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.ByteSequence;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.ClasspathFile;

/**
 * Operations for sequentially scanning data items in a class file. Any IO exceptions that occur
 * during scanning are converted to {@link ClassFormatError}s.
 */
public final class ClassfileStream {

    private final int offset;
    private final int length;
    private final ByteArrayInputStream bstream;
    private final DataInputStream stream;
    private final ClasspathFile classfile;
    private final byte[] bytes;

    public ClassfileStream(byte[] bytes, ClasspathFile classfile) {
        this(bytes, 0, bytes.length, classfile);
    }

    public ClassfileStream(ClasspathFile classfile) {
        this(classfile.contents, classfile);
    }

    public ClassfileStream(byte[] bytes, int offset, int length, ClasspathFile classfile) {
        this.offset = offset;
        this.length = length;
        this.bytes = bytes;
        this.bstream = new ByteArrayInputStream(bytes, offset, length);
        this.stream = new DataInputStream(bstream);
        this.classfile = classfile;
    }

    public char readChar() {
        try {
            return stream.readChar();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public float readFloat() {
        try {
            return stream.readFloat();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public long readS8() {
        try {
            return stream.readLong();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public double readDouble() {
        try {
            return stream.readDouble();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public int readU1() {
        try {
            return stream.readUnsignedByte();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public int readU2() {
        try {
            return stream.readUnsignedShort();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public int readS4() {
        try {
            return stream.readInt();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public int readS1() {
        try {
            return stream.readByte();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public int readS2() {
        try {
            return stream.readShort();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public byte[] readByteArray(int len) {
        try {
            final byte[] buf = new byte[len];
            stream.readFully(buf);
            return buf;
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public ByteSequence readByteSequenceUTF() {
        try {
            int utflen = stream.readUnsignedShort();
            int start = getPosition();
            // TODO(peterssen): .skipBytes is NOT O(1).
            stream.skipBytes(utflen);
            return ByteSequence.wrap(bytes, start + offset, utflen);
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public void skip(int nBytes) {
        try {
            stream.skipBytes(nBytes);
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    byte[] getByteRange(int startPosition, int numBytes) {
        byte[] result = new byte[numBytes];
        System.arraycopy(bytes, startPosition, result, 0, numBytes);
        return result;
    }

    public boolean isAtEndOfFile() {
        return bstream.available() == 0;
    }

    public void checkEndOfFile() {
        if (!isAtEndOfFile()) {
            throw new ClassFormatError("Extra bytes in class file");
        }
    }

    public int getPosition() {
        return length - bstream.available();
    }

    public void close() {
        try {
            stream.close();
        } catch (EOFException eofException) {
            throw eofError();
        } catch (IOException ioException) {
            throw ioError(ioException);
        }
    }

    public ClassFormatError classFormatError(String format, Object... args) {
        Meta meta = EspressoLanguage.getCurrentContext().getMeta();
        throw Meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, String.format(format, args) + " in classfile " + classfile);
    }

    public ClassFormatError ioError(IOException ioException) {
        throw classFormatError("%s", ioException);
    }

    public ClassFormatError eofError() {
        throw classFormatError("Truncated class file");
    }
}
