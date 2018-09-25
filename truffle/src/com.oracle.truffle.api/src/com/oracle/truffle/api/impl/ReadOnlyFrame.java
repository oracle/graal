/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;

class ReadOnlyFrame implements Frame {
    private final Frame delegate;

    ReadOnlyFrame(Frame delegate) {
        this.delegate = delegate;
    }

    @Override
    @TruffleBoundary
    public FrameDescriptor getFrameDescriptor() {
        return delegate.getFrameDescriptor();
    }

    @Override
    @TruffleBoundary
    public Object[] getArguments() {
        return delegate.getArguments().clone();
    }

    @Override
    @TruffleBoundary
    public Object getObject(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getObject(slot);
    }

    @Override
    @TruffleBoundary
    public void setObject(FrameSlot slot, Object value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public byte getByte(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getByte(slot);
    }

    @Override
    @TruffleBoundary
    public void setByte(FrameSlot slot, byte value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getBoolean(slot);
    }

    @Override
    @TruffleBoundary
    public void setBoolean(FrameSlot slot, boolean value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public int getInt(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getInt(slot);
    }

    @Override
    @TruffleBoundary
    public void setInt(FrameSlot slot, int value) {
        throw newReadonlyAssertionError();
    }

    private static AssertionError newReadonlyAssertionError() {
        return new AssertionError("Unexpected write access.");
    }

    @Override
    @TruffleBoundary
    public long getLong(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getLong(slot);
    }

    @Override
    @TruffleBoundary
    public void setLong(FrameSlot slot, long value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public float getFloat(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getFloat(slot);
    }

    @Override
    @TruffleBoundary
    public void setFloat(FrameSlot slot, float value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public double getDouble(FrameSlot slot) throws FrameSlotTypeException {
        return delegate.getDouble(slot);
    }

    @Override
    @TruffleBoundary
    public void setDouble(FrameSlot slot, double value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public Object getValue(FrameSlot slot) {
        return delegate.getValue(slot);
    }

    @Override
    @TruffleBoundary
    public MaterializedFrame materialize() {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public boolean isObject(FrameSlot slot) {
        return delegate.isObject(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isByte(FrameSlot slot) {
        return delegate.isByte(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isBoolean(FrameSlot slot) {
        return delegate.isBoolean(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isInt(FrameSlot slot) {
        return delegate.isInt(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isLong(FrameSlot slot) {
        return delegate.isLong(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isFloat(FrameSlot slot) {
        return delegate.isFloat(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isDouble(FrameSlot slot) {
        return delegate.isDouble(slot);
    }
}
