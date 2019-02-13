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
package com.oracle.truffle.api.frame;

/**
 * Represents a frame containing values of local variables of the guest language. Instances of this
 * type must not be stored in a field or cast to {@link java.lang.Object}.
 * 
 * @since 0.8 or earlier
 */
public interface Frame {

    /**
     * @return the object describing the layout of this frame
     * @since 0.8 or earlier
     */
    FrameDescriptor getFrameDescriptor();

    /**
     * Retrieves the arguments object from this frame. The runtime assumes that the arguments object
     * is never null.
     *
     * @return the arguments used when calling this method
     * @since 0.8 or earlier
     */
    Object[] getArguments();

    /**
     * Read access to a local variable of type {@link Object}.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     * @since 0.8 or earlier
     */
    Object getObject(FrameSlot slot) throws FrameSlotTypeException;

    /**
     * Write access to a local variable of type {@link Object}.
     *
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     * @since 0.8 or earlier
     */
    void setObject(FrameSlot slot, Object value);

    /**
     * Read access to a local variable of type byte.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     * @throws FrameSlotTypeException
     * @since 0.8 or earlier
     */
    byte getByte(FrameSlot slot) throws FrameSlotTypeException;

    /**
     * Write access to a local variable of type byte.
     *
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    /** @since 0.8 or earlier */
    void setByte(FrameSlot slot, byte value);

    /**
     * Read access to a local variable of type boolean.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     * @since 0.8 or earlier
     */
    boolean getBoolean(FrameSlot slot) throws FrameSlotTypeException;

    /**
     * Write access to a local variable of type boolean.
     *
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     * @since 0.8 or earlier
     */
    void setBoolean(FrameSlot slot, boolean value);

    /**
     * Read access to a local variable of type int.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     * @since 0.8 or earlier
     */
    int getInt(FrameSlot slot) throws FrameSlotTypeException;

    /**
     * Write access to a local variable of type int.
     *
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     * @since 0.8 or earlier
     */
    void setInt(FrameSlot slot, int value);

    /**
     * Read access to a local variable of type long.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     * @since 0.8 or earlier
     */
    long getLong(FrameSlot slot) throws FrameSlotTypeException;

    /**
     * Write access to a local variable of type long.
     *
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     * @since 0.8 or earlier
     */
    void setLong(FrameSlot slot, long value);

    /**
     * Read access to a local variable of type float.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     * @since 0.8 or earlier
     */
    float getFloat(FrameSlot slot) throws FrameSlotTypeException;

    /**
     * Write access to a local variable of type float.
     *
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     * @since 0.8 or earlier
     */
    void setFloat(FrameSlot slot, float value);

    /**
     * Read access to a local variable of type double.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable
     * @since 0.8 or earlier
     */
    double getDouble(FrameSlot slot) throws FrameSlotTypeException;

    /**
     * Write access to a local variable of type double.
     *
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     * @since 0.8 or earlier
     */
    void setDouble(FrameSlot slot, double value);

    /**
     * Read access to a local variable of any type.
     *
     * @param slot the slot of the local variable
     * @return the current value of the local variable or defaultValue if unset
     * @since 0.8 or earlier
     */
    Object getValue(FrameSlot slot);

    /**
     * Materializes this frame, which allows it to be stored in a field or cast to
     * {@link java.lang.Object}.
     *
     * @return the new materialized frame
     * @since 0.8 or earlier
     */
    MaterializedFrame materialize();

    /**
     * Check whether the given {@link FrameSlot} is of type object.
     * 
     * @since 0.8 or earlier
     */
    boolean isObject(FrameSlot slot);

    /**
     * Check whether the given {@link FrameSlot} is of type byte.
     * 
     * @since 0.8 or earlier
     */
    boolean isByte(FrameSlot slot);

    /**
     * Check whether the given {@link FrameSlot} is of type boolean.
     * 
     * @since 0.8 or earlier
     */
    boolean isBoolean(FrameSlot slot);

    /**
     * Check whether the given {@link FrameSlot} is of type int.
     * 
     * @since 0.8 or earlier
     */
    boolean isInt(FrameSlot slot);

    /**
     * Check whether the given {@link FrameSlot} is of type long.
     * 
     * @since 0.8 or earlier
     */
    boolean isLong(FrameSlot slot);

    /**
     * Check whether the given {@link FrameSlot} is of type float.
     * 
     * @since 0.8 or earlier
     */
    boolean isFloat(FrameSlot slot);

    /**
     * Check whether the given {@link FrameSlot} is of type double.
     * 
     * @since 0.8 or earlier
     */
    boolean isDouble(FrameSlot slot);
}
