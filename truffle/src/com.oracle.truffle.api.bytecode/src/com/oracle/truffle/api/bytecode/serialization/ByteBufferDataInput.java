/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.serialization;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/**
 * A {@link DataInput} backed by a {@link ByteBuffer}.
 *
 * @see SerializationUtils#createDataInput(ByteBuffer)
 * @since 24.2
 */
final class ByteBufferDataInput implements DataInput {

    private final ByteBuffer buffer;
    private char[] lineBuffer;

    ByteBufferDataInput(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    private static EOFException wrap(BufferUnderflowException ex) {
        EOFException eof = new EOFException();
        eof.addSuppressed(ex);
        return eof;
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        try {
            buffer.get(b, 0, len);
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public int skipBytes(int n) throws IOException {
        int skip = buffer.remaining() > n ? buffer.remaining() : n;
        buffer.position(buffer.position() + skip);
        return skip;
    }

    @Override
    public boolean readBoolean() throws IOException {
        try {
            return buffer.get() != 0;
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public byte readByte() throws IOException {
        try {
            return buffer.get();
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public int readUnsignedByte() throws IOException {
        try {
            return buffer.get() & 0xff;
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public short readShort() throws IOException {
        try {
            return buffer.getShort();
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public int readUnsignedShort() throws IOException {
        try {
            return buffer.getShort() & 0xffff;
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public char readChar() throws IOException {
        try {
            return buffer.getChar();
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public int readInt() throws IOException {
        try {
            return buffer.getInt();
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public long readLong() throws IOException {
        try {
            return buffer.getLong();
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public float readFloat() throws IOException {
        try {
            return buffer.getFloat();
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    @Override
    public double readDouble() throws IOException {
        try {
            return buffer.getDouble();
        } catch (BufferUnderflowException ex) {
            throw wrap(ex);
        }
    }

    private static int get(ByteBuffer buf) {
        if (buf.position() >= buf.limit()) {
            return -1;
        } else {
            return buf.get();
        }
    }

    /**
     * Modified from {@link DataInputStream#readLine()}.
     */
    @Override
    @Deprecated
    public String readLine() throws IOException {
        char[] buf = lineBuffer;

        if (buf == null) {
            buf = lineBuffer = new char[128];
        }

        int room = buf.length;
        int offset = 0;
        int c;

        loop: while (true) {
            switch (c = get(buffer)) {
                case -1:
                case '\n':
                    break loop;

                case '\r':
                    int c2 = get(buffer);
                    if ((c2 != '\n') && (c2 != -1)) {
                        buffer.position(buffer.position() - 1);
                    }
                    break loop;

                default:
                    if (--room < 0) {
                        buf = new char[offset + 128];
                        room = buf.length - offset - 1;
                        System.arraycopy(lineBuffer, 0, buf, 0, offset);
                        lineBuffer = buf;
                    }
                    buf[offset++] = (char) c;
                    break;
            }
        }
        if ((c == -1) && (offset == 0)) {
            return null;
        }
        return String.copyValueOf(buf, 0, offset);
    }

    @Override
    public String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }
}
