/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.descriptors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.jni.ModifiedUtf8;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * A <tt>ByteSequence</tt> is a readable sequence of <code>byte</code> values. This interface
 * provides uniform, read-only access to different kinds of <code>byte</code> sequences. Implements
 * a slice "view" over a byte array.
 */
public abstract class ByteSequence {

    protected final int hashCode;

    @CompilationFinal(dimensions = 1) //
    protected final byte[] value;

    public static final ByteSequence EMPTY = ByteSequence.create("");

    ByteSequence(final byte[] underlyingBytes, int hashCode) {
        this.value = Objects.requireNonNull(underlyingBytes);
        this.hashCode = hashCode;
    }

    static int hashOfRange(final byte[] bytes, int offset, int length) {
        int h = 0;
        if (length > 0) {
            h = 1;
            for (int i = 0; i < length; ++i) {
                h = 31 * h + bytes[offset + i];
            }
        }
        return h;
    }

    public static ByteSequence wrap(final byte[] underlyingBytes) {
        return wrap(underlyingBytes, 0, underlyingBytes.length);
    }

    public static ByteSequence wrap(final byte[] underlyingBytes, int offset, int length) {
        if ((length > 0 && offset >= underlyingBytes.length) || offset + (long) length > underlyingBytes.length || length < 0 || offset < 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere("ByteSequence illegal bounds: offset: " + offset + " length: " + length + " bytes length: " + underlyingBytes.length);
        }
        return new ByteSequence(underlyingBytes, hashOfRange(underlyingBytes, offset, length)) {
            @Override
            public int length() {
                return length;
            }

            @Override
            public int offset() {
                return offset;
            }
        };
    }

    public static ByteSequence create(String str) {
        final byte[] bytes = ModifiedUtf8.fromJavaString(str);
        return ByteSequence.wrap(bytes, 0, bytes.length);
    }

    public static ByteSequence from(ByteBuffer buffer) {
        int length = buffer.remaining();
        if (buffer.hasArray()) {
            int offset = buffer.position() + buffer.arrayOffset();
            byte[] array = buffer.array();
            return wrap(array, offset, length);
        } else {
            byte[] data = new byte[length];
            buffer.get(data);
            return wrap(data);
        }
    }

    /**
     * Returns the length of this byte sequence. The length is the number of <code>byte</code>s in
     * the sequence.
     *
     * @return the number of <code>byte</code>s in this sequence
     */
    public abstract int length();

    public abstract int offset();

    /**
     * Returns the <code>byte</code> value at the specified index. An index ranges from zero to
     * <tt>length() - 1</tt>. The first <code>byte</code> value of the sequence is at index zero,
     * the next at index one, and so on, as for array indexing.
     *
     * @param index the index of the <code>byte</code> value to be returned
     *
     * @return the specified <code>byte</code> value
     *
     * @throws IndexOutOfBoundsException if the <tt>index</tt> argument is negative or not less than
     *             <tt>length()</tt>
     */
    public byte byteAt(int index) {
        return value[index + offset()];
    }

    public int unsignedByteAt(int index) {
        return byteAt(index) & 0xff;
    }

    final byte[] getUnderlyingBytes() {
        return value;
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    public final ByteSequence subSequence(int offset, int length) {
        if (offset == 0 && length == length()) {
            return this;
        }
        return wrap(getUnderlyingBytes(), offset() + offset, length);
    }

    public final boolean contentEquals(ByteSequence other) {
        if (length() != other.length()) {
            return false;
        }
        for (int i = 0; i < length(); ++i) {
            if (byteAt(i) != other.byteAt(i)) {
                return false;
            }
        }
        return true;
    }

    public final boolean contentStartsWith(ByteSequence other) {
        if (length() < other.length()) {
            return false;
        }
        for (int i = 0; i < other.length(); ++i) {
            if (byteAt(i) != other.byteAt(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        try {
            return ModifiedUtf8.toJavaString(getUnderlyingBytes(), offset(), length());
        } catch (IOException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public String toHexString() {
        StringBuilder r = new StringBuilder(length() * 2);
        for (int i = 0; i < length(); ++i) {
            byte b = byteAt(i);
            r.append(HEX[(b >> 4) & 0xf]);
            r.append(HEX[b & 0xf]);
        }
        return r.toString();
    }

    public int lastIndexOf(byte b) {
        for (int i = length() - 1; i >= 0; i--) {
            if (byteAt(i) == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Writes this sequence into the destination byte array.
     *
     * @param dest the destination
     * @param index index in the destination array to start writing the sequence
     */
    public void writeTo(byte[] dest, int index) {
        System.arraycopy(getUnderlyingBytes(), offset(), dest, index, length());
    }

    static void writePositiveLongString(long v, byte[] dest, int offset, int length) {
        assert length == positiveLongStringSize(v);
        long i = v;
        int digit = length;
        // in '456', '4' is digit 1, '5' is digit 2, etc.
        while (i >= 10) {
            long q = i / 10;
            long r = i - (10 * q);
            dest[offset + --digit] = (byte) ('0' + r);
            i = q;
        }
        assert digit == 1;
        dest[offset] = (byte) ('0' + i);
    }

    static int positiveLongStringSize(long x) {
        assert x >= 0;
        long p = 10;
        for (int i = 1; i < 10; i++) {
            if (x < p) {
                return i;
            }
            p = 10 * p;
        }
        return 10;
    }

    public void writeTo(ByteBuffer bb) {
        bb.put(getUnderlyingBytes(), offset(), length());
    }

    public ByteSequence concat(ByteSequence next) {
        byte[] data = new byte[this.length() + next.length()];
        writeTo(data, 0);
        next.writeTo(data, this.length());
        return wrap(data);
    }
}
