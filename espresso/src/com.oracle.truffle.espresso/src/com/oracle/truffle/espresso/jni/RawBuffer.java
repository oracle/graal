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

package com.oracle.truffle.espresso.jni;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.meta.EspressoError;

public final class RawBuffer implements AutoCloseable {
    private ByteBuffer buffer;
    private TruffleObject pointer;

    public RawBuffer(ByteBuffer buffer, TruffleObject pointer) {
        this.buffer = buffer;
        this.pointer = pointer;
    }

    public static RawBuffer getNativeString(String name) {
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        int length = ((int) (name.length() * encoder.averageBytesPerChar())) + 1;
        for (;;) {
            if (length <= 0) {
                throw EspressoError.shouldNotReachHere();
            }
            // Be super safe with the size of the buffer.
            ByteBuffer bb = NativeUtils.allocateDirect(length);
            encoder.reset();
            CoderResult result = encoder.encode(CharBuffer.wrap(name), bb, true);

            if (result.isOverflow()) {
                // Not enough space in the buffer
                length <<= 1;
            } else if (result.isUnderflow()) {
                result = encoder.flush(bb);
                if (result.isUnderflow() && (bb.position() < bb.capacity())) {
                    // Encoder encoded entire string, and we have one byte of leeway.
                    bb.put((byte) 0);
                    return new RawBuffer(bb, NativeUtils.byteBufferPointer(bb));
                }
                if (result.isOverflow() || result.isUnderflow()) {
                    length += 1;
                } else {
                    throw EspressoError.shouldNotReachHere();
                }
            } else {
                throw EspressoError.shouldNotReachHere();
            }
        }
    }

    public TruffleObject pointer() {
        return pointer;
    }

    @Override
    public void close() {
        buffer.clear();
        this.buffer = null;
    }
}
