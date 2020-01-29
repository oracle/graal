/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.scanner;

import java.util.Arrays;

import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class RecordBuffer {

    private static final int INITIAL_BUFFER_SIZE = 8;

    private long[] opBuffer = new long[INITIAL_BUFFER_SIZE];

    private int size = 0;
    private int index = 1;

    void addOpNoCheck(long op) {
        assert size < opBuffer.length;
        opBuffer[size++] = op;
    }

    void addOp(long op) {
        ensureFits(1);
        addOpNoCheck(op);
    }

    void ensureFits(long numOfAdditionalOps) {
        if (size >= opBuffer.length - numOfAdditionalOps) {
            int newLength = opBuffer.length;
            while (size >= newLength - numOfAdditionalOps) {
                newLength *= 2;
            }
            opBuffer = Arrays.copyOf(opBuffer, newLength);
        }
    }

    long[] getOps() {
        return Arrays.copyOfRange(opBuffer, 1, size);
    }

    void invalidate() {
        size = 0;
        index = 1;
    }

    public long getAt(int pos) {
        return opBuffer[pos + 1];
    }

    public int getId() {
        if (size <= 0) {
            throw new LLVMParserException("Record Id not set!");
        }
        long id = opBuffer[0];
        if (id != (int) id) {
            throw new LLVMParserException("invalid record id " + id);
        }
        return (int) id;
    }

    /**
     * Returns the size (not including the record id).
     */
    public int size() {
        return size - 1;
    }

    public long read() {
        assert index < size;
        return opBuffer[index++];
    }

    public void skip() {
        index++;
    }

    public int readInt() {
        long read = read();
        return toUnsignedIntExact(read);
    }

    private static int toUnsignedIntExact(long read) {
        if (Type.fitsIntoUnsignedInt(read)) {
            return Type.toUnsignedInt(read);
        }
        throw new ArithmeticException("unsigned integer overflow");
    }

    public boolean readBoolean() {
        return read() != 0;
    }

    public int remaining() {
        return size - index;
    }

    public void checkEnd(String message) {
        if (remaining() > 0) {
            throw new LLVMParserException(message);
        }
    }

    public static String describe(long id, long[] args) {
        final StringBuilder builder = new StringBuilder();
        builder.append("<id=").append(id).append(" - ");
        for (int i = 0; i < args.length; i++) {
            builder.append("op").append(i).append('=').append(args[i]);
            if (i != args.length - 1) {
                builder.append(", ");
            }
        }
        builder.append('>');
        return builder.toString();
    }

    public long readSignedValue() {
        long v = read();
        if ((v & 1L) == 1L) {
            v = v >>> 1;
            return v == 0 ? Long.MIN_VALUE : -v;
        } else {
            return v >>> 1;
        }
    }

    public String readString() {
        int length = remaining();
        StringBuilder string = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            string.append((char) opBuffer[index + i]);
        }
        index += length;
        return string.toString();
    }

    public String readUnicodeString() {
        // We use a byte array, so "new String(...)" is able to handle Unicode Characters correctly
        final byte[] bytes = new byte[remaining()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (opBuffer[index + i] & 0xFF);
        }
        index += bytes.length;
        return new String(bytes);
    }

    public long[] dumpArray() {
        return getOps();
    }
}
