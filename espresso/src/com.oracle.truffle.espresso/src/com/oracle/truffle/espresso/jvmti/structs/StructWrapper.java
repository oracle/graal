/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.jvmti.structs;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.ffi.RawPointer;
import com.oracle.truffle.espresso.ffi.nfi.NativeUtils;
import com.oracle.truffle.espresso.jni.JNIHandles;
import com.oracle.truffle.espresso.jni.JniEnv;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class StructWrapper {
    private final JNIHandles handles;

    private final TruffleObject pointer;
    private final ByteBuffer buffer;

    public TruffleObject pointer() {
        return pointer;
    }

    protected StructWrapper(JniEnv jni, TruffleObject pointer, long capacity) {
        this.handles = jni.getHandles();

        this.pointer = pointer;
        this.buffer = NativeUtils.directByteBuffer(pointer, capacity);
    }

    protected boolean getBoolean(int offset) {
        return buffer.get(offset) != 0;
    }

    protected void putBoolean(int offset, boolean value) {
        buffer.put(offset, (byte) (value ? 1 : 0));
    }

    protected byte getByte(int offset) {
        return buffer.get(offset);
    }

    protected void putByte(int offset, byte value) {
        buffer.put(offset, value);
    }

    protected char getChar(int offset) {
        return buffer.getChar(offset);
    }

    protected void putChar(int offset, char value) {
        buffer.putChar(offset, value);
    }

    protected short getShort(int offset) {
        return buffer.getShort(offset);
    }

    protected void putShort(int offset, short value) {
        buffer.putShort(offset, value);
    }

    protected int getInt(int offset) {
        return buffer.getInt(offset);
    }

    protected void putInt(int offset, int value) {
        buffer.putInt(offset, value);
    }

    protected float getFloat(int offset) {
        return buffer.getFloat(offset);
    }

    protected void putFloat(int offset, float value) {
        buffer.putFloat(offset, value);
    }

    protected long getLong(int offset) {
        return buffer.getLong(offset);
    }

    protected void putLong(int offset, long value) {
        buffer.putLong(offset, value);
    }

    protected double getDouble(int offset) {
        return buffer.getDouble(offset);
    }

    protected void putDouble(int offset, double value) {
        buffer.putDouble(offset, value);
    }

    protected TruffleObject getPointer(int offset) {
        return RawPointer.create(buffer.getLong(offset));
    }

    protected void putPointer(int offset, TruffleObject value) {
        buffer.putLong(offset, NativeUtils.interopAsPointer(value));
    }

    protected StaticObject getObject(int offset) {
        return handles.get(Math.toIntExact(buffer.getLong(offset)));
    }

    protected void putObject(int offset, StaticObject value) {
        buffer.putLong(offset, handles.createLocal(value));
    }
}
