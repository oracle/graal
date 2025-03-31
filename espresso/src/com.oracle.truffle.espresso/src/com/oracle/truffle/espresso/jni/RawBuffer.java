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
import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class RawBuffer implements AutoCloseable {
    private ByteBuffer buffer;
    private TruffleObject pointer;

    public RawBuffer(ByteBuffer buffer, TruffleObject pointer) {
        this.buffer = buffer;
        this.pointer = pointer;
    }

    public static RawBuffer getNativeString(ByteSequence seq) {
        ByteBuffer bb = NativeUtils.allocateDirect(seq.length() + 1);
        seq.writeTo(bb);
        bb.put((byte) 0);
        return new RawBuffer(bb, NativeUtils.byteBufferPointer(bb));
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

    @TruffleBoundary
    public static RawBuffer getNativeHeapPointer(StaticObject obj, EspressoContext ctx) {
        assert obj.getKlass().isArray();
        JavaKind componentKind = ((ArrayKlass) obj.getKlass()).getComponentType().getJavaKind();
        ByteBuffer bb = NativeUtils.allocateDirect(obj.length(ctx.getLanguage()) * componentKind.getByteCount());
        switch (componentKind) {
            case Boolean -> {
                boolean[] array = obj.unwrap(ctx.getLanguage());
                for (boolean b : array) {
                    bb.put((byte) (b ? 1 : 0));
                }
            }
            case Byte -> {
                byte[] array = obj.unwrap(ctx.getLanguage());
                for (byte b : array) {
                    bb.put(b);
                }
            }
            case Short -> {
                short[] array = obj.unwrap(ctx.getLanguage());
                for (short s : array) {
                    bb.putShort(s);
                }
            }
            case Char -> {
                char[] array = obj.unwrap(ctx.getLanguage());
                for (char c : array) {
                    bb.putChar(c);
                }
            }
            case Int -> {
                int[] array = obj.unwrap(ctx.getLanguage());
                for (int i : array) {
                    bb.putInt(i);
                }
            }
            case Float -> {
                float[] array = obj.unwrap(ctx.getLanguage());
                for (float f : array) {
                    bb.putFloat(f);
                }
            }
            case Long -> {
                long[] array = obj.unwrap(ctx.getLanguage());
                for (long l : array) {
                    bb.putLong(l);
                }
            }
            case Double -> {
                double[] array = obj.unwrap(ctx.getLanguage());
                for (double d : array) {
                    bb.putDouble(d);
                }
            }
            default -> throw ctx.getMeta().throwExceptionWithMessage(ctx.getMeta().java_lang_IllegalArgumentException, "Unsupported java heap access in downcall stub: " + obj.getKlass());
        }
        return new RawBuffer(bb, NativeUtils.byteBufferPointer(bb));
    }

    @TruffleBoundary
    public void writeBack(StaticObject obj, EspressoContext ctx) {
        assert obj.getKlass().isArray();
        JavaKind componentKind = ((ArrayKlass) obj.getKlass()).getComponentType().getJavaKind();
        ByteBuffer bb = buffer.rewind();
        switch (componentKind) {
            case Boolean -> {
                boolean[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.get() != 0;
                }
            }
            case Byte -> {
                byte[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.get();
                }
            }
            case Short -> {
                short[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.getShort();
                }
            }
            case Char -> {
                char[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.getChar();
                }
            }
            case Int -> {
                int[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.getInt();
                }
            }
            case Float -> {
                float[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.getFloat();
                }
            }
            case Long -> {
                long[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.getLong();
                }
            }
            case Double -> {
                double[] array = obj.unwrap(ctx.getLanguage());
                for (int i = 0; i < array.length; i++) {
                    array[i] = bb.getDouble();
                }
            }
            default -> throw ctx.getMeta().throwExceptionWithMessage(ctx.getMeta().java_lang_IllegalArgumentException, "Unsupported java heap access in downcall stub: " + obj.getKlass());
        }
    }

    @Override
    public void close() {
        buffer.clear();
        this.buffer = null;
    }

    public static class Buffers {
        private final ArrayList<RawBuffer> buffers = new ArrayList<>(0);
        private final ArrayList<StaticObject> arrays = new ArrayList<>(0);

        public void writeBack(EspressoContext ctx) {
            assert buffers.size() == arrays.size();
            for (int i = 0; i < buffers.size(); i++) {
                RawBuffer b = buffers.get(i);
                StaticObject target = arrays.get(i);
                b.writeBack(target, ctx);
                b.close();
            }
        }

        public void add(RawBuffer b, StaticObject obj) {
            buffers.add(b);
            arrays.add(obj);
        }
    }
}
