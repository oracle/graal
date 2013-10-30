/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
        frame.setObject(slot, value);
    }

    /**
     * Write access to a local variable of type {@code byte}.
     * 
     * Sets the frame slot type to {@code byte} if it isn't already.
     * 
     * @param slot the slot of the local variable
     * @param value the new value of the local variable
     */
    public static void setByteSafe(Frame frame, FrameSlot slot, byte value) {
        frame.setByte(slot, value);
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
        frame.setBoolean(slot, value);
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
        frame.setInt(slot, value);
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
        frame.setLong(slot, value);
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
        frame.setFloat(slot, value);
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
        frame.setDouble(slot, value);
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     * 
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getObject(FrameSlot)
     */
    public static Object getObjectSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getObject(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     * 
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getByte(FrameSlot)
     */
    public static byte getByteSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getByte(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     * 
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getBoolean(FrameSlot)
     */
    public static boolean getBooleanSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getBoolean(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     * 
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getInt(FrameSlot)
     */
    public static int getIntSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getInt(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     * 
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getLong(FrameSlot)
     */
    public static long getLongSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getLong(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     * 
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getDouble(FrameSlot)
     */
    public static double getDoubleSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getDouble(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     * 
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getFloat(FrameSlot)
     */
    public static float getFloatSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getFloat(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }
}
