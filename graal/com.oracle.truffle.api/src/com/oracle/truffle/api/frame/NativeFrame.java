/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.*;

/**
 * Represents a native frame without any local variables. Instances of this type must not be stored
 * in a field or cast to {@link java.lang.Object}.
 */
public class NativeFrame implements VirtualFrame, PackedFrame {

    private PackedFrame caller;
    private Arguments arguments;

    public NativeFrame(PackedFrame caller, Arguments arguments) {
        this.caller = caller;
        this.arguments = arguments;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Arguments> T getArguments(Class<T> clazz) {
        return (T) arguments;
    }

    @Override
    public Object getObject(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public byte getByte(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public void setByte(FrameSlot slot, byte value) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean getBoolean(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public int getInt(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public long getLong(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public float getFloat(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public double getDouble(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public Object getValue(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public PackedFrame pack() {
        return this;
    }

    @Override
    public PackedFrame getCaller() {
        return caller;
    }

    @Override
    public MaterializedFrame materialize() {
        throw unsupportedInNativeFrame();
    }

    @Override
    public VirtualFrame unpack() {
        return this;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean isObject(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean isByte(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean isBoolean(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean isInt(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean isLong(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean isFloat(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    @Override
    public boolean isDouble(FrameSlot slot) {
        throw unsupportedInNativeFrame();
    }

    private static UnsupportedOperationException unsupportedInNativeFrame() {
        throw new UnsupportedOperationException("native frame");
    }
}
