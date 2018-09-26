/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

/** @since 0.8 or earlier */
public final class FrameUtil {
    private FrameUtil() {
    }

    /**
     * Read a frame slot that is guaranteed to be of the desired kind (either previously checked by
     * a guard or statically known).
     *
     * @param frameSlot the slot of the variable
     * @throws IllegalStateException if the slot kind does not match
     * @see Frame#getObject(FrameSlot)
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
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
     * @since 0.8 or earlier
     */
    public static float getFloatSafe(Frame frame, FrameSlot frameSlot) {
        try {
            return frame.getFloat(frameSlot);
        } catch (FrameSlotTypeException e) {
            throw new IllegalStateException();
        }
    }
}
