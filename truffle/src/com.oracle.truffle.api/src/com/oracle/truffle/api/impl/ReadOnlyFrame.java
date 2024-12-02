/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

class ReadOnlyFrame implements Frame {
    private final Frame delegate;

    public Object expectObject(int slot) throws UnexpectedResultException {
        return delegate.expectObject(slot);
    }

    public byte expectByte(int slot) throws UnexpectedResultException {
        return delegate.expectByte(slot);
    }

    public boolean expectBoolean(int slot) throws UnexpectedResultException {
        return delegate.expectBoolean(slot);
    }

    public int expectInt(int slot) throws UnexpectedResultException {
        return delegate.expectInt(slot);
    }

    public long expectLong(int slot) throws UnexpectedResultException {
        return delegate.expectLong(slot);
    }

    public float expectFloat(int slot) throws UnexpectedResultException {
        return delegate.expectFloat(slot);
    }

    public double expectDouble(int slot) throws UnexpectedResultException {
        return delegate.expectDouble(slot);
    }

    public void swap(int first, int second) {
        delegate.swap(first, second);
    }

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

    private static AssertionError newReadonlyAssertionError() {
        return new AssertionError("Unexpected write access.");
    }

    @Override
    @TruffleBoundary
    public MaterializedFrame materialize() {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public Object getObject(int slot) throws FrameSlotTypeException {
        return delegate.getObject(slot);
    }

    @Override
    @TruffleBoundary
    public void setObject(int slot, Object value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public byte getByte(int slot) throws FrameSlotTypeException {
        return delegate.getByte(slot);
    }

    @Override
    @TruffleBoundary
    public void setByte(int slot, byte value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public boolean getBoolean(int slot) throws FrameSlotTypeException {
        return delegate.getBoolean(slot);
    }

    @Override
    @TruffleBoundary
    public void setBoolean(int slot, boolean value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public int getInt(int slot) throws FrameSlotTypeException {
        return delegate.getInt(slot);
    }

    @Override
    @TruffleBoundary
    public void setInt(int slot, int value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public long getLong(int slot) throws FrameSlotTypeException {
        return delegate.getLong(slot);
    }

    @Override
    @TruffleBoundary
    public void setLong(int slot, long value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public float getFloat(int slot) throws FrameSlotTypeException {
        return delegate.getFloat(slot);
    }

    @Override
    @TruffleBoundary
    public void setFloat(int slot, float value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public double getDouble(int slot) throws FrameSlotTypeException {
        return delegate.getDouble(slot);
    }

    @Override
    @TruffleBoundary
    public void setDouble(int slot, double value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public Object getValue(int slot) {
        return delegate.getValue(slot);
    }

    @Override
    @TruffleBoundary
    public void copy(int srcSlot, int destSlot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public byte getTag(int slot) {
        return delegate.getTag(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isObject(int slot) {
        return delegate.isObject(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isByte(int slot) {
        return delegate.isByte(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isBoolean(int slot) {
        return delegate.isBoolean(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isInt(int slot) {
        return delegate.isInt(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isLong(int slot) {
        return delegate.isLong(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isFloat(int slot) {
        return delegate.isFloat(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isDouble(int slot) {
        return delegate.isDouble(slot);
    }

    @Override
    @TruffleBoundary
    public boolean isStatic(int slot) {
        return delegate.isStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void clear(int slot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public Object getAuxiliarySlot(int slot) {
        return delegate.getAuxiliarySlot(slot);
    }

    @Override
    @TruffleBoundary
    public void setAuxiliarySlot(int slot, Object value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public Object getObjectStatic(int slot) {
        return delegate.getObjectStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void setObjectStatic(int slot, Object value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public byte getByteStatic(int slot) {
        return delegate.getByteStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void setByteStatic(int slot, byte value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public boolean getBooleanStatic(int slot) {
        return delegate.getBooleanStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void setBooleanStatic(int slot, boolean value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public int getIntStatic(int slot) {
        return delegate.getIntStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void setIntStatic(int slot, int value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public long getLongStatic(int slot) {
        return delegate.getLongStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void setLongStatic(int slot, long value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public float getFloatStatic(int slot) {
        return delegate.getFloatStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void setFloatStatic(int slot, float value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public double getDoubleStatic(int slot) {
        return delegate.getDoubleStatic(slot);
    }

    @Override
    @TruffleBoundary
    public void setDoubleStatic(int slot, double value) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void copyPrimitiveStatic(int srcSlot, int destSlot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void copyObjectStatic(int srcSlot, int destSlot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void copyStatic(int srcSlot, int destSlot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void swapPrimitiveStatic(int first, int second) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void swapObjectStatic(int first, int second) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void swapStatic(int first, int second) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void clearPrimitiveStatic(int slot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void clearObjectStatic(int slot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void clearStatic(int slot) {
        throw newReadonlyAssertionError();
    }

    @Override
    @TruffleBoundary
    public void copyTo(int srcOffset, Frame dst, int dstOffset, int length) {
        delegate.copyTo(srcOffset, dst, dstOffset, length);
    }
}
