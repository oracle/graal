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
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.Stable;
import com.oracle.truffle.espresso.jni.Utf8;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * A <tt>ByteSequence</tt> is a readable sequence of <code>byte</code> values. This interface
 * provides uniform, read-only access to different kinds of <code>byte</code> sequences. Implements
 * a "view" over a byte array.
 */
// TODO(peterssen): Should not be public.
public abstract class ByteSequence {

    protected final int hashCode;

    @Stable @CompilationFinal(dimensions = 1) //
    protected final byte[] value;

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
        if ((length > 0 && offset >= underlyingBytes.length) || offset + length > underlyingBytes.length || length < 0 || offset < 0) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere("ByteSequence illegal bounds: offset: " + offset + " length: " + length + " bytes length: " + underlyingBytes.length);
        }
        return new ByteSequence(underlyingBytes, hashOfRange(underlyingBytes, offset, length)) {
            @Override
            public final int length() {
                return length;
            }

            @Override
            public final int offset() {
                return offset;
            }
        };
    }

    public static ByteSequence create(String str) {
        final byte[] bytes = Utf8.fromJavaString(str);
        return ByteSequence.wrap(bytes, 0, bytes.length);
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

    @Override
    public String toString() {
        try {
            return Utf8.toJavaString(getUnderlyingBytes(), offset(), length());
        } catch (IOException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }
}
