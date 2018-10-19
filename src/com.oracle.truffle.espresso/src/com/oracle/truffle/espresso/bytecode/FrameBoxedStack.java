/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.bytecode;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class FrameBoxedStack implements FrameOperandStack {

    private final Object[] stack;
    private final byte[] stackTag;

    private final FrameSlot stackSizeSlot;

    private static boolean isEspressoReference(Object instance) {
        return instance != null && (instance instanceof StaticObject || instance.getClass().isArray());
    }

    public FrameBoxedStack(int maxStackSize, FrameSlot stackSizeSlot) {
        this.stack = new Object[maxStackSize];
        this.stackTag = new byte[maxStackSize];
        this.stackSizeSlot = stackSizeSlot;
    }

    // region Operand stack operations

    @Override
    public int stackIndex(final VirtualFrame frame) {
        return FrameUtil.getIntSafe(frame, stackSizeSlot);
    }

    private int getAndAdd(final VirtualFrame frame, int delta) {
        int stackSize = stackIndex(frame);
        frame.setInt(stackSizeSlot, stackSize + delta);
        return stackSize;
    }

    private int addAndGet(final VirtualFrame frame, int delta) {
        int stackSize = stackIndex(frame);
        stackSize += delta;
        frame.setInt(stackSizeSlot, stackSize);
        return stackSize;
    }

    @Override
    public void popVoid(final VirtualFrame frame, int slots) {
        assert slots == 1 || slots == 2;
        int stackSize = addAndGet(frame, -slots);
        assert stackSize >= 0;
    }

    @Override
    public void pushObject(final VirtualFrame frame, Object value) {
        assert value != null;
        assert isEspressoReference(value);
        int stackSize = getAndAdd(frame, 1);
        stackTag[stackSize] = (byte) FrameSlotKind.Object.ordinal();
        stack[stackSize] = value;
    }

    @Override
    public void pushInt(final VirtualFrame frame, int value) {
        int stackSize = getAndAdd(frame, 1);
        stackTag[stackSize] = (byte) FrameSlotKind.Int.ordinal();
        stack[stackSize] = value;
    }

    public void pushIllegal(final VirtualFrame frame) {
        int stackSize = getAndAdd(frame, 1);
        stackTag[stackSize] = (byte) FrameSlotKind.Illegal.ordinal();
        stack[stackSize] = null;
    }

    @Override
    public void pushLong(final VirtualFrame frame, long value) {
        pushIllegal(frame);
        int stackSize = getAndAdd(frame, 1);
        stackTag[stackSize] = (byte) FrameSlotKind.Long.ordinal();
        stack[stackSize] = value;
    }

    @Override
    public void pushFloat(final VirtualFrame frame, float value) {
        int stackSize = getAndAdd(frame, 1);
        stackTag[stackSize] = (byte) FrameSlotKind.Float.ordinal();
        stack[stackSize] = value;
    }

    @Override
    public void pushDouble(final VirtualFrame frame, double value) {
        pushIllegal(frame);
        int stackSize = getAndAdd(frame, 1);
        stackTag[stackSize] = (byte) FrameSlotKind.Double.ordinal();
        stack[stackSize] = value;
    }

    public FrameSlotKind peekTag(final VirtualFrame frame) {
        // TODO(peterssen): Avoid recreating values() array.
        int stackSize = stackIndex(frame);
        return KIND_VALUES.get(stackTag[stackSize - 1]);
    }

    @Override
    public Object popObject(final VirtualFrame frame) {
        assert peekTag(frame) == FrameSlotKind.Object;
        int stackSize = addAndGet(frame, -1);
        Object top = stack[stackSize];
        assert top != null : "Use StaticObject.NULL";
        assert isEspressoReference(top);
        return top;
    }

    @Override
    public int popInt(final VirtualFrame frame) {
        assert peekTag(frame) == FrameSlotKind.Int;
        int stackSize = addAndGet(frame, -1);
        return (int) stack[stackSize];
    }

    @Override
    public float popFloat(final VirtualFrame frame) {
        assert peekTag(frame) == FrameSlotKind.Float;
        int stackSize = addAndGet(frame, -1);
        return (float) stack[stackSize];
    }

    @Override
    public long popLong(final VirtualFrame frame) {
        assert peekTag(frame) == FrameSlotKind.Long;
        int stackSize = addAndGet(frame, -1);
        long ret = (long) stack[stackSize];
        popIllegal(frame);
        return ret;
    }

    @Override
    public double popDouble(final VirtualFrame frame) {
        assert peekTag(frame) == FrameSlotKind.Double;
        int stackSize = addAndGet(frame, -1);
        double ret = (double) stack[stackSize];
        popIllegal(frame);
        return ret;
    }

    private void popIllegal(final VirtualFrame frame) {
        assert peekTag(frame) == FrameSlotKind.Illegal;
        int stackSize = addAndGet(frame, -1);
        // assert stackSize > 0;
    }

    private static int numberOfSlots(FrameSlotKind kind) {
        assert kind != null;
        if (kind == FrameSlotKind.Long || kind == FrameSlotKind.Double) {
            return 2;
        }
        // Illegal take 1 slot.
        return 1;
    }

    @Override
    public void dup1(final VirtualFrame frame) {
        assert numberOfSlots(peekTag(frame)) == 1;
        pushUnsafe1(frame, peekUnsafe1(frame), peekTag(frame));
    }

    public Object popUnsafe1(final VirtualFrame frame) {
        int stackSize = addAndGet(frame, -1);
        return stack[stackSize];
    }

    public Object peekUnsafe1(final VirtualFrame frame) {
        int stackSize = stackIndex(frame);
        return stack[stackSize - 1];
    }

    public void pushUnsafe1(final VirtualFrame frame, Object value, FrameSlotKind kind) {
        // assert numberOfSlots(kind) == 1;
        int stackSize = getAndAdd(frame, 1);
        stackTag[stackSize] = (byte) kind.ordinal();
        stack[stackSize] = value;
    }

    @Override
    public void swapSingle(final VirtualFrame frame) {
        // value2, value1 → value1, value2
        FrameSlotKind tag1 = peekTag(frame);
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1(frame);

        FrameSlotKind tag2 = peekTag(frame);
        assert numberOfSlots(tag2) == 1;
        Object elem2 = popUnsafe1(frame);

        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem2, tag2);
    }

    @Override
    public void dupx1(final VirtualFrame frame) {
        // value2, value1 → value1, value2, value1
        FrameSlotKind tag1 = peekTag(frame);
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1(frame);

        FrameSlotKind tag2 = peekTag(frame);
        assert numberOfSlots(tag2) == 1;
        Object elem2 = popUnsafe1(frame);

        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dupx2(final VirtualFrame frame) {
        // value3, value2, value1 → value1, value3, value2, value1
        FrameSlotKind tag1 = peekTag(frame);
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1(frame);

        FrameSlotKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);

        FrameSlotKind tag3 = peekTag(frame);
        Object elem3 = popUnsafe1(frame);

        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem3, tag3);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dup2(final VirtualFrame frame) {
        // {value2, value1} → {value2, value1}, {value2, value1}
        FrameSlotKind tag1 = peekTag(frame);
        Object elem1 = popUnsafe1(frame);

        FrameSlotKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);

        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dup2x1(final VirtualFrame frame) {
        // value3, {value2, value1} → {value2, value1}, value3, {value2, value1}
        FrameSlotKind tag1 = peekTag(frame);
        Object elem1 = popUnsafe1(frame);

        FrameSlotKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);

        FrameSlotKind tag3 = peekTag(frame);
        assert numberOfSlots(tag3) == 1;
        Object elem3 = popUnsafe1(frame);

        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem3, tag3);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public void dup2x2(final VirtualFrame frame) {
        // {value4, value3}, {value2, value1} → {value2, value1}, {value4, value3}, {value2, value1}
        FrameSlotKind tag1 = peekTag(frame);
        Object elem1 = popUnsafe1(frame);
        FrameSlotKind tag2 = peekTag(frame);
        Object elem2 = popUnsafe1(frame);
        FrameSlotKind tag3 = peekTag(frame);
        Object elem3 = popUnsafe1(frame);
        FrameSlotKind tag4 = peekTag(frame);
        Object elem4 = popUnsafe1(frame);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
        pushUnsafe1(frame, elem4, tag4);
        pushUnsafe1(frame, elem3, tag3);
        pushUnsafe1(frame, elem2, tag2);
        pushUnsafe1(frame, elem1, tag1);
    }

    @Override
    public Object peekReceiver(final VirtualFrame frame, MethodInfo method) {
        assert !method.isStatic();
        int slots = method.getSignature().getNumberOfSlotsForParameters();
        int stackSize = stackIndex(frame);
        Object receiver = stack[stackSize - slots - 1];
        assert isEspressoReference(receiver);
        return receiver;
    }

    @Override
    public void clear(final VirtualFrame frame) {
        frame.setInt(stackSizeSlot, 0);
    }
}
