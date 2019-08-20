/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.heap;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

/**
 * Provides access to the raw underlying memory of a {@link Buffer}. For {@link Buffer#isDirect()
 * direct} buffers, the address of the raw native memory encapsulated by the buffer is returned. For
 * {@link Buffer#hasArray() array-based} buffers, the array is {@link PinnedObject pinned} and the
 * address to the pinned Java memory is returned.
 *
 * Not all kinds of buffers are supported. For example, when you create an array-based
 * {@link ByteBuffer} and {@link ByteBuffer#asIntBuffer() wrap} it as an {@link IntBuffer}, a
 * complicated nested structure is created that is not supported yet.
 */
public final class PinnedBuffer implements AutoCloseable {

    @TargetClass(className = "java.nio.Buffer")
    static final class Target_java_nio_Buffer {
        @Alias long address;
    }

    private final Buffer buffer;
    private final PinnedObject arrayPin;
    private final int shift;
    private boolean open;

    /**
     * Creates an open {@link PinnedBuffer} for the provided {@link Buffer}.
     */
    public static PinnedBuffer open(Buffer buffer) {
        return new PinnedBuffer(buffer);
    }

    private PinnedBuffer(Buffer buffer) {
        this.buffer = buffer;

        if (buffer == null || buffer.isDirect()) {
            arrayPin = null;
        } else if (buffer.hasArray()) {
            arrayPin = PinnedObject.create(buffer.array());
        } else {
            throw VMError.shouldNotReachHere("cannot pin non-direct non-array Buffer");
        }

        if (buffer == null || buffer instanceof ByteBuffer) {
            shift = 0;
        } else if (buffer instanceof CharBuffer || buffer instanceof ShortBuffer) {
            shift = 1;
        } else if (buffer instanceof IntBuffer || buffer instanceof FloatBuffer) {
            shift = 2;
        } else if (buffer instanceof LongBuffer || buffer instanceof DoubleBuffer) {
            shift = 3;
        } else {
            throw VMError.shouldNotReachHere("Unknown Buffer type");
        }

        open = true;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public boolean isOpen() {
        return open;
    }

    /**
     * Returns a pointer to the specified buffer element. The method does not take the
     * {@link Buffer#position()} of the buffer into account, but you can provide the
     * {@link Buffer#position()} of the buffer as the argument of this method.
     */
    public <T extends PointerBase> T addressOf(int position) {
        assert open;
        if (arrayPin != null) {
            return arrayPin.addressOfArrayElement(buffer.arrayOffset() + position);
        } else if (buffer == null) {
            return WordFactory.nullPointer();
        } else {
            long address = SubstrateUtil.cast(buffer, Target_java_nio_Buffer.class).address;
            return WordFactory.pointer(address + (position << shift));
        }
    }

    /**
     * Releases the pin for the buffer. After this call, the previous results of {@link #addressOf}
     * are no longer valid and must not be used anymore.
     */
    @Override
    public void close() {
        assert open;
        open = false;
        if (arrayPin != null) {
            arrayPin.close();
        }
    }
}
