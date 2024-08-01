/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Accessor class used to abstract away frame and bytecode array accesses in the generated code.
 *
 * Do not use directly.
 *
 * @since 24.2
 */
public abstract sealed class BytecodeDSLAccess permits BytecodeDSLCheckedAccess, BytecodeDSLUncheckedAccess {

    private static volatile BytecodeDSLAccess safeSingleton;
    private static volatile BytecodeDSLAccess unsafeSingleton;

    /**
     * Obtains an accessor. Used by generated code; do not use directly.
     *
     * @since 24.2
     */
    public static final BytecodeDSLAccess lookup(Object token, boolean allowUnsafe) {
        BytecodeRootNodes.checkToken(token);
        BytecodeDSLAccess impl;
        if (allowUnsafe && !Boolean.getBoolean("truffle.dsl.DisableUnsafeBytecodeDSLAccess")) {
            impl = unsafeSingleton;
            if (impl == null) {
                impl = unsafeSingleton = createUnsafe();
            }
        } else {
            impl = safeSingleton;
            if (impl == null) {
                impl = safeSingleton = createSafe();
            }
        }
        return impl;
    }

    BytecodeDSLAccess() {
    }

    private static BytecodeDSLAccess createSafe() {
        return new BytecodeDSLCheckedAccess();
    }

    private static BytecodeDSLAccess createUnsafe() {
        return new BytecodeDSLUncheckedAccess();
    }

    /**
     * Returns a {@link ByteArraySupport} to use for byte array accesses.
     *
     * @since 24.2
     */
    public abstract ByteArraySupport getByteArraySupport();

    /**
     * Reads from an Object array.
     *
     * @since 24.2
     */
    public abstract <T> T readObject(T[] arr, int index);

    /**
     * Writes to an Object array.
     *
     * @since 24.2
     */
    public abstract <T> void writeObject(T[] arr, int index, T value);

    /**
     * Casts a value to the given class. Also assumes non-null.
     *
     * @since 24.2
     */
    public abstract <T> T uncheckedCast(Object arr, Class<T> clazz);

    /**
     * Reads a tag from the frame.
     *
     * @since 24.2
     */
    public abstract byte getTag(Frame frame, int slot);

    /**
     * Reads an object from the frame.
     *
     * @since 24.2
     */
    public abstract Object getObject(Frame frame, int slot);

    /**
     * Reads a boolean from the frame.
     *
     * @since 24.2
     */
    public abstract boolean getBoolean(Frame frame, int slot);

    /**
     * Reads an int from the frame.
     *
     * @since 24.2
     */
    public abstract int getInt(Frame frame, int slot);

    /**
     * Reads a long from the frame.
     *
     * @since 24.2
     */
    public abstract long getLong(Frame frame, int slot);

    /**
     * Reads a byte from the frame.
     *
     * @since 24.2
     */
    public abstract byte getByte(Frame frame, int slot);

    /**
     * Reads a float from the frame.
     *
     * @since 24.2
     */
    public abstract float getFloat(Frame frame, int slot);

    /**
     * Reads a double from the frame.
     *
     * @since 24.2
     */
    public abstract double getDouble(Frame frame, int slot);

    /**
     * Reads an object from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract Object uncheckedGetObject(Frame frame, int slot);

    /**
     * Reads a boolean from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract boolean uncheckedGetBoolean(Frame frame, int slot);

    /**
     * Reads a byte from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract byte uncheckedGetByte(Frame frame, int slot);

    /**
     * Reads an int from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract int uncheckedGetInt(Frame frame, int slot);

    /**
     * Reads a long from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract long uncheckedGetLong(Frame frame, int slot);

    /**
     * Reads a float from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract float uncheckedGetFloat(Frame frame, int slot);

    /**
     * Reads a double from the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract double uncheckedGetDouble(Frame frame, int slot);

    /**
     * Stores an object into the frame without checking the slot's tag.
     *
     * @since 24.2
     */
    public abstract void uncheckedSetObject(Frame frame, int slot, Object value);

    /**
     * Reads the value of a slot from the frame.
     *
     * @since 24.2
     */
    @SuppressWarnings("static-method")
    public final Object getValue(Frame frame, int slot) {
        return frame.getValue(slot);
    }

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
     * Copies an Object from one slot to another.
     *
     * @since 24.2
     */
    public abstract void copyObject(Frame frame, int srcSlot, int dstSlot);

    /**
     * Copies a primitive from one slot to another.
     *
     * @since 24.2
     */
    public abstract void copyPrimitive(Frame frame, int srcSlot, int dstSlot);

    /**
     * Clears a frame slot.
     *
     * @since 24.2
     */
    public abstract void clear(Frame frame, int slot);
}
