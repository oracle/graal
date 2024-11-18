/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameExtensions;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

final class FrameExtensionsSafe extends FrameExtensions {

    static final FrameExtensionsSafe INSTANCE = new FrameExtensionsSafe();

    FrameExtensionsSafe() {
        // no direct instances
    }

    @Override
    public Object getObject(Frame frame, int slot) {
        return frame.getObject(slot);
    }

    @Override
    public Object uncheckedGetObject(Frame frame, int slot) {
        try {
            return frame.getObject(slot);
        } catch (FrameSlotTypeException e) {
            return frame.getValue(slot);
        }
    }

    @Override
    public boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectBoolean(slot);
    }

    @Override
    public byte expectByte(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectByte(slot);
    }

    @Override
    public int expectInt(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectInt(slot);
    }

    @Override
    public long expectLong(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectLong(slot);
    }

    @Override
    public Object expectObject(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectObject(slot);
    }

    @Override
    public float expectFloat(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectFloat(slot);
    }

    @Override
    public double expectDouble(Frame frame, int slot) throws UnexpectedResultException {
        return frame.expectDouble(slot);
    }

    @Override
    public void setObject(Frame frame, int slot, Object value) {
        frame.setObject(slot, value);
    }

    @Override
    public void setBoolean(Frame frame, int slot, boolean value) {
        frame.setBoolean(slot, value);
    }

    @Override
    public void setByte(Frame frame, int slot, byte value) {
        frame.setByte(slot, value);
    }

    @Override
    public void setInt(Frame frame, int slot, int value) {
        frame.setInt(slot, value);
    }

    @Override
    public void setLong(Frame frame, int slot, long value) {
        frame.setLong(slot, value);
    }

    @Override
    public void setFloat(Frame frame, int slot, float value) {
        frame.setFloat(slot, value);
    }

    @Override
    public void setDouble(Frame frame, int slot, double value) {
        frame.setDouble(slot, value);
    }

    @Override
    public void copy(Frame frame, int srcSlot, int dstSlot) {
        frame.copy(srcSlot, dstSlot);
    }

    @Override
    public void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length) {
        srcFrame.copyTo(srcOffset, dstFrame, dstOffset, length);
    }

    @Override
    public void clear(Frame frame, int slot) {
        frame.clear(slot);
    }

    @Override
    public void resetFrame(Frame frame) {
        ((FrameWithoutBoxing) frame).reset();
    }

}
