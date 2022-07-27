/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class EspressoFrame {

    private EspressoFrame() {
        throw EspressoError.shouldNotReachHere();
    }

    /**
     * Bytecode execution frames are built on
     * {@link com.oracle.truffle.api.frame.FrameDescriptor.Builder#addSlot(FrameSlotKind, Object, Object)
     * indexed frame slots}, and contain one slot for the BCI followed by the locals and the stack
     * ("values").
     */
    private static final int BCI_SLOT = 0;
    private static final int VALUES_START = 1;

    public static FrameDescriptor createFrameDescriptor(int locals, int stack) {
        int slotCount = locals + stack;
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(slotCount + VALUES_START);
        int bciSlot = builder.addSlot(FrameSlotKind.Static, null, null); // BCI
        assert bciSlot == BCI_SLOT;
        int valuesStart = builder.addSlots(slotCount, FrameSlotKind.Static); // locals + stack
        assert valuesStart == VALUES_START;
        return builder.build();
    }

    // region Operand stack accessors

    public static void dup1(Frame frame, int top) {
        // value1 -> value1, value1
        copyStatic(frame, top - 1, top);
    }

    public static void dupx1(Frame frame, int top) {
        // value2, value1 -> value1, value2, value1
        copyStatic(frame, top - 1, top);
        copyStatic(frame, top - 2, top - 1);
        copyStatic(frame, top, top - 2);
    }

    public static void dupx2(Frame frame, int top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        copyStatic(frame, top - 1, top);
        copyStatic(frame, top - 2, top - 1);
        copyStatic(frame, top - 3, top - 2);
        copyStatic(frame, top, top - 3);
    }

    public static void dup2(Frame frame, int top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 1, top + 1);
    }

    public static void swapSingle(Frame frame, int top) {
        // value2, value1 -> value1, value2
        frame.swapPrimitiveStatic(top - 1, top - 2);
        frame.swapObjectStatic(top - 1, top - 2);
    }

    public static void dup2x1(Frame frame, int top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 1, top + 1);
        copyStatic(frame, top - 3, top - 1);
        copyStatic(frame, top, top - 3);
        copyStatic(frame, top + 1, top - 2);
    }

    public static void dup2x2(Frame frame, int top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        copyStatic(frame, top - 1, top + 1);
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 3, top - 1);
        copyStatic(frame, top - 4, top - 2);
        copyStatic(frame, top, top - 4);
        copyStatic(frame, top + 1, top - 3);
    }

    private static void copyStatic(Frame frame, int src, int dst) {
        frame.copyPrimitiveStatic(src, dst);
        frame.copyObjectStatic(src, dst);
    }

    public static int popInt(Frame frame, int slot) {
        int result = frame.getIntStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static StaticObject peekObject(Frame frame, int slot) {
        Object result = frame.getObjectStatic(slot);
        assert result != null;
        return (StaticObject) result;
    }

    /**
     * Reads and clear the operand stack slot.
     */
    public static StaticObject popObject(Frame frame, int slot) {
        // nulls-out the slot, use peekObject to read only
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        return (StaticObject) result;
    }

    public static float popFloat(Frame frame, int slot) {
        float result = frame.getFloatStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static long popLong(Frame frame, int slot) {
        long result = frame.getLongStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static double popDouble(Frame frame, int slot) {
        double result = frame.getDoubleStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    static Object popReturnAddressOrObject(Frame frame, int slot) {
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        assert result instanceof StaticObject || result instanceof ReturnAddress;
        return result;
    }

    static void putReturnAddress(Frame frame, int slot, int targetBCI) {
        frame.setObjectStatic(slot, ReturnAddress.create(targetBCI));
    }

    public static void putObject(Frame frame, int slot, StaticObject value) {
        assert value != null;
        frame.setObjectStatic(slot, value);
    }

    public static void putInt(Frame frame, int slot, int value) {
        frame.setIntStatic(slot, value);
    }

    public static void putFloat(Frame frame, int slot, float value) {
        frame.setFloatStatic(slot, value);
    }

    public static void putLong(Frame frame, int slot, long value) {
        frame.setLongStatic(slot + 1, value);
    }

    public static void putDouble(Frame frame, int slot, double value) {
        frame.setDoubleStatic(slot + 1, value);
    }

    private static void clearReference(Frame frame, int slot) {
        frame.clearObjectStatic(slot);
    }

    private static void clearPrimitive(Frame frame, int slot) {
        frame.clearPrimitiveStatic(slot);
    }

    public static void clear(Frame frame, int slot) {
        clearPrimitive(frame, slot);
        clearReference(frame, slot);
    }

    // endregion Operand stack accessors

    // region Local accessors

    public static void clearLocal(Frame frame, int localSlot) {
        clear(frame, VALUES_START + localSlot);
    }

    public static void setLocalObject(Frame frame, int localSlot, StaticObject value) {
        assert value != null;
        frame.setObjectStatic(VALUES_START + localSlot, value);
    }

    static void setLocalObjectOrReturnAddress(Frame frame, int localSlot, Object value) {
        frame.setObjectStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalInt(Frame frame, int localSlot, int value) {
        frame.setIntStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalFloat(Frame frame, int localSlot, float value) {
        frame.setFloatStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalLong(Frame frame, int localSlot, long value) {
        frame.setLongStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalDouble(Frame frame, int localSlot, double value) {
        frame.setDoubleStatic(VALUES_START + localSlot, value);
    }

    public static int getLocalInt(Frame frame, int localSlot) {
        return frame.getIntStatic(VALUES_START + localSlot);
    }

    public static StaticObject getLocalObject(Frame frame, int localSlot) {
        Object result = frame.getObjectStatic(VALUES_START + localSlot);
        assert result != null;
        return (StaticObject) result;
    }

    public static Object getRawLocalObject(Frame frame, int localSlot) {
        return frame.getObjectStatic(VALUES_START + localSlot);
    }

    static int getLocalReturnAddress(Frame frame, int localSlot) {
        Object result = frame.getObjectStatic(VALUES_START + localSlot);
        assert result != null;
        return ((ReturnAddress) result).getBci();
    }

    public static float getLocalFloat(Frame frame, int localSlot) {
        return frame.getFloatStatic(VALUES_START + localSlot);
    }

    public static long getLocalLong(Frame frame, int localSlot) {
        return frame.getLongStatic(VALUES_START + localSlot);
    }

    public static double getLocalDouble(Frame frame, int localSlot) {
        return frame.getDoubleStatic(VALUES_START + localSlot);
    }

    // endregion Local accessors

    static void setBCI(Frame frame, int bci) {
        frame.setIntStatic(BCI_SLOT, bci);
    }

    static int getBCI(Frame frame) {
        return frame.getIntStatic(BCI_SLOT);
    }

    public static int startingStackOffset(int maxLocals) {
        return VALUES_START + maxLocals;
    }
}
