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
package com.oracle.truffle.api.frame;

import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Internal frame accessor methods for internal Truffle components that are trusted.
 */
public abstract class FrameExtensions {

    protected FrameExtensions() {
    }

    /**
     * Reads an object from the frame.
     *
     * @since 24.2
     */
    public abstract Object getObject(Frame frame, int slot) throws FrameSlotTypeException;

    /**
     * Stores an Object into the frame.
     *
     * @since 24.2
     */
    public abstract void setObject(Frame frame, int slot, Object value);

    /**
     * Stores a boolean into the frame.
     *
     * @since 24.2
     */
    public abstract void setBoolean(Frame frame, int slot, boolean value);

    /**
     * Stores a byte into the frame.
     *
     * @since 24.2
     */
    public abstract void setByte(Frame frame, int slot, byte value);

    /**
     * Stores an int into the frame.
     *
     * @since 24.2
     */
    public abstract void setInt(Frame frame, int slot, int value);

    /**
     * Stores a long into the frame.
     *
     * @since 24.2
     */
    public abstract void setLong(Frame frame, int slot, long value);

    public final void setLongOrObject(Frame frame, int slot, Object value) {
        if (value instanceof Long l) {
            setLong(frame, slot, l);
        } else {
            setObject(frame, slot, value);
        }
    }

    public final void setIntOrObject(Frame frame, int slot, Object value) {
        if (value instanceof Integer l) {
            setInt(frame, slot, l);
        } else {
            setObject(frame, slot, value);
        }
    }

    public final void setByteOrObject(Frame frame, int slot, Object value) {
        if (value instanceof Byte l) {
            setByte(frame, slot, l);
        } else {
            setObject(frame, slot, value);
        }
    }

    public final void setBooleanOrObject(Frame frame, int slot, Object value) {
        if (value instanceof Boolean l) {
            setBoolean(frame, slot, l);
        } else {
            setObject(frame, slot, value);
        }
    }

    public final void setFloatOrObject(Frame frame, int slot, Object value) {
        if (value instanceof Float l) {
            setFloat(frame, slot, l);
        } else {
            setObject(frame, slot, value);
        }
    }

    public final void setDoubleOrObject(Frame frame, int slot, Object value) {
        if (value instanceof Double l) {
            setDouble(frame, slot, l);
        } else {
            setObject(frame, slot, value);
        }
    }

    /**
     * Stores a float into the frame.
     *
     * @since 24.2
     */
    public abstract void setFloat(Frame frame, int slot, float value);

    /**
     * Stores a double into the frame.
     *
     * @since 24.2
     */
    public abstract void setDouble(Frame frame, int slot, double value);

    /**
     * Reads a boolean from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract boolean expectBoolean(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a byte from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract byte expectByte(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads an int from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract int expectInt(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a long from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract long expectLong(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads an Object from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract Object expectObject(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a float from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract float expectFloat(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads a double from the frame, throwing an {@link UnexpectedResultException} with the slot
     * value when the tag does not match.
     *
     * @since 24.2
     */
    public abstract double expectDouble(Frame frame, int slot) throws UnexpectedResultException;

    /**
     * Reads an Object from the frame, recovering gracefully when the slot is not Object.
     *
     * @since 24.2
     */
    public final Object requireObject(Frame frame, int slot) {
        try {
            return expectObject(frame, slot);
        } catch (UnexpectedResultException e) {
            return e.getResult();
        }
    }

    /**
     * Reads an object from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract Object uncheckedGetObject(Frame frame, int slot);

    /**
     * Copies a value from one slot to another.
     *
     * @since 24.2
     */
    public abstract void copy(Frame frame, int srcSlot, int dstSlot);

    /**
     * Copies a range of values from one frame to another.
     *
     * @since 24.2
     */
    public abstract void copyTo(Frame srcFrame, int srcOffset, Frame dstFrame, int dstOffset, int length);

    /**
     * Clears a frame slot.
     *
     * @since 24.2
     */
    public abstract void clear(Frame frame, int slot);

    /**
     * Reads the value of a slot from the frame.
     *
     * @since 24.2
     */
    @SuppressWarnings("static-method")
    public final Object getValue(Frame frame, int slot) {
        return frame.getValue(slot);
    }

    public abstract void resetFrame(Frame frame);

    /**
     * Reads an object from the frame.
     *
     * @since 25.1
     */
    public Object getObject(Frame frame, long slot) throws FrameSlotTypeException {
        return getObject(frame, (int) slot);
    }

    /**
     * Stores an Object into the frame.
     *
     * @since 25.1
     */
    public void setObject(Frame frame, long slot, Object value) {
        setObject(frame, (int) slot, value);
    }

    /**
     * Stores a boolean into the frame.
     *
     * @since 25.1
     */
    public void setBoolean(Frame frame, long slot, boolean value) {
        setBoolean(frame, (int) slot, value);
    }

    /**
     * Stores a byte into the frame.
     *
     * @since 25.1
     */
    public void setByte(Frame frame, long slot, byte value) {
        setByte(frame, (int) slot, value);
    }

    /**
     * Stores an int into the frame.
     *
     * @since 25.1
     */
    public void setInt(Frame frame, long slot, int value) {
        setInt(frame, (int) slot, value);
    }

    /**
     * Stores a long into the frame.
     *
     * @since 25.1
     */
    public void setLong(Frame frame, long slot, long value) {
        setLong(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setLongOrObject(Frame frame, long slot, Object value) {
        setLongOrObject(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setIntOrObject(Frame frame, long slot, Object value) {
        setIntOrObject(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setByteOrObject(Frame frame, long slot, Object value) {
        setByteOrObject(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setBooleanOrObject(Frame frame, long slot, Object value) {
        setBooleanOrObject(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setFloatOrObject(Frame frame, long slot, Object value) {
        setFloatOrObject(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setDoubleOrObject(Frame frame, long slot, Object value) {
        setDoubleOrObject(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setFloat(Frame frame, long slot, float value) {
        setFloat(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public void setDouble(Frame frame, long slot, double value) {
        setDouble(frame, (int) slot, value);
    }

    /**
     * @since 25.1
     */
    public boolean expectBoolean(Frame frame, long slot) throws UnexpectedResultException {
        return expectBoolean(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public byte expectByte(Frame frame, long slot) throws UnexpectedResultException {
        return expectByte(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public int expectInt(Frame frame, long slot) throws UnexpectedResultException {
        return expectInt(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public long expectLong(Frame frame, long slot) throws UnexpectedResultException {
        return expectLong(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public Object expectObject(Frame frame, long slot) throws UnexpectedResultException {
        return expectObject(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public float expectFloat(Frame frame, long slot) throws UnexpectedResultException {
        return expectFloat(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public double expectDouble(Frame frame, long slot) throws UnexpectedResultException {
        return expectDouble(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public Object requireObject(Frame frame, long slot) {
        return requireObject(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    public Object uncheckedGetObject(Frame frame, long slot) {
        return uncheckedGetObject(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    @SuppressWarnings("static-method")
    public byte getTag(Frame frame, long slot) {
        return frame.getTag((int) slot);
    }

    /**
     * @since 25.1
     */
    @SuppressWarnings("static-method")
    public byte getTag(Frame frame, int slot) {
        return frame.getTag(slot);
    }

    /**
     * @since 25.1
     */
    public void copy(Frame frame, long srcSlot, long dstSlot) {
        copy(frame, (int) srcSlot, (int) dstSlot);
    }

    /**
     * @since 25.1
     */
    public void copyTo(Frame srcFrame, long srcOffset, Frame dstFrame, long dstOffset, long length) {
        copyTo(srcFrame, (int) srcOffset, dstFrame, (int) dstOffset, (int) length);
    }

    /**
     * @since 25.1
     */
    public void clear(Frame frame, long slot) {
        clear(frame, (int) slot);
    }

    /**
     * @since 25.1
     */
    @SuppressWarnings("static-method")
    public Object getValue(Frame frame, long slot) {
        return getValue(frame, (int) slot);
    }

}
