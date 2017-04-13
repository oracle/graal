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
