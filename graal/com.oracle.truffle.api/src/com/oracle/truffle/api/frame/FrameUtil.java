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
