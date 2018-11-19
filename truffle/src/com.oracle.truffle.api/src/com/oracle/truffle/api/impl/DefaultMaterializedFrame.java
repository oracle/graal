/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;

/**
 * This is an implementation-specific class. Do not use or instantiate it. Instead, use
 * {@link TruffleRuntime#createMaterializedFrame(Object[])} or {@link Frame#materialize()} to create
 * a {@link MaterializedFrame}.
 */
final class DefaultMaterializedFrame implements MaterializedFrame {

    private final DefaultVirtualFrame wrapped;

    DefaultMaterializedFrame(DefaultVirtualFrame wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Object[] getArguments() {
        return wrapped.getArguments();
    }

    @Override
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        return wrapped.getObject(slot);
    }

    @Override
    public void setObject(FrameSlot slot, Object value) {
        wrapped.setObject(slot, value);
    }

    @Override
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        return wrapped.getByte(slot);
    }

    @Override
    public void setByte(FrameSlot slot, byte value) {
        wrapped.setByte(slot, value);
    }

    @Override
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        return wrapped.getBoolean(slot);
    }

    @Override
    public void setBoolean(FrameSlot slot, boolean value) {
        wrapped.setBoolean(slot, value);
    }

    @Override
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        return wrapped.getInt(slot);
    }

    @Override
    public void setInt(FrameSlot slot, int value) {
        wrapped.setInt(slot, value);
    }

    @Override
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        return wrapped.getLong(slot);
    }

    @Override
    public void setLong(FrameSlot slot, long value) {
        wrapped.setLong(slot, value);
    }

    @Override
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        return wrapped.getFloat(slot);
    }

    @Override
    public void setFloat(FrameSlot slot, float value) {
        wrapped.setFloat(slot, value);
    }

    @Override
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        return wrapped.getDouble(slot);
    }

    @Override
    public void setDouble(FrameSlot slot, double value) {
        wrapped.setDouble(slot, value);
    }

    @Override
    public Object getValue(FrameSlot slot) {
        return wrapped.getValue(slot);
    }

    @Override
    public MaterializedFrame materialize() {
        return this;
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
        return wrapped.getFrameDescriptor();
    }

    @Override
    public boolean isObject(FrameSlot slot) {
        return wrapped.isObject(slot);
    }

    @Override
    public boolean isByte(FrameSlot slot) {
        return wrapped.isByte(slot);
    }

    @Override
    public boolean isBoolean(FrameSlot slot) {
        return wrapped.isBoolean(slot);
    }

    @Override
    public boolean isInt(FrameSlot slot) {
        return wrapped.isInt(slot);
    }

    @Override
    public boolean isLong(FrameSlot slot) {
        return wrapped.isLong(slot);
    }

    @Override
    public boolean isFloat(FrameSlot slot) {
        return wrapped.isFloat(slot);
    }

    @Override
    public boolean isDouble(FrameSlot slot) {
        return wrapped.isDouble(slot);
    }
}
