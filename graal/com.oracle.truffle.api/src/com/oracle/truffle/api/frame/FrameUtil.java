/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.frame;

public final class FrameUtil {

    /**
     * Write access to a local variable of type {@link Object}.
     * 
     * Sets the frame slot type to {@link Object} if it isn't already.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    public static void setObjectSafe(Frame frame, FrameSlot slot, Object value) {
        if (slot.getType() != Object.class) {
            slot.setType(Object.class);
        }
        try {
            frame.setObject(slot, value);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Write access to a local variable of type {@code boolean}.
     * 
     * Sets the frame slot type to {@code boolean} if it isn't already.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    public static void setBooleanSafe(Frame frame, FrameSlot slot, boolean value) {
        if (slot.getType() != boolean.class) {
            slot.setType(boolean.class);
        }
        try {
            frame.setBoolean(slot, value);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Write access to a local variable of type {@code int}.
     * 
     * Sets the frame slot type to {@code int} if it isn't already.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    public static void setIntSafe(Frame frame, FrameSlot slot, int value) {
        if (slot.getType() != int.class) {
            slot.setType(int.class);
        }
        try {
            frame.setInt(slot, value);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Write access to a local variable of type {@code long}.
     * 
     * Sets the frame slot type to {@code long} if it isn't already.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    public static void setLongSafe(Frame frame, FrameSlot slot, long value) {
        if (slot.getType() != long.class) {
            slot.setType(long.class);
        }
        try {
            frame.setLong(slot, value);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Write access to a local variable of type {@code float}.
     * 
     * Sets the frame slot type to {@code float} if it isn't already.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    public static void setFloatSafe(Frame frame, FrameSlot slot, float value) {
        if (slot.getType() != float.class) {
            slot.setType(float.class);
        }
        try {
            frame.setFloat(slot, value);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Write access to a local variable of type {@code double}.
     * 
     * Sets the frame slot type to {@code double} if it isn't already.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    public static void setDoubleSafe(Frame frame, FrameSlot slot, double value) {
        if (slot.getType() != double.class) {
            slot.setType(double.class);
        }
        try {
            frame.setDouble(slot, value);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }
}
