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

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class BoxedStack implements OperandStack {

    private final Object[] stack;
    private final byte[] stackTag;
    private int stackSize;

    private static boolean isEspressoReference(Object instance) {
        return instance != null && (instance instanceof StaticObject || instance.getClass().isArray());
    }

    public BoxedStack(int maxStackSize) {
        this.stack = new Object[maxStackSize];
        this.stackTag = new byte[maxStackSize];
        this.stackSize = 0;
    }

    // region Operand stack operations

    @Override
    public int stackIndex() {
        return stackSize;
    }

    @Override
    public void popVoid(int slots) {
        assert slots == 1 || slots == 2;
        stackSize -= slots;
        assert stackSize >= 0;
    }

    @Override
    public void pushObject(Object value) {
        assert value != null;
        assert isEspressoReference(value);
        stackTag[stackSize] = (byte) FrameSlotKind.Object.ordinal();
        stack[stackSize++] = value;
    }

    @Override
    public void pushInt(int value) {
        stackTag[stackSize] = (byte) FrameSlotKind.Int.ordinal();
        stack[stackSize++] = value;
    }

    public void pushIllegal() {
        stackTag[stackSize] = (byte) FrameSlotKind.Illegal.ordinal();
        stack[stackSize++] = null;
    }

    @Override
    public void pushLong(long value) {
        pushIllegal();
        stackTag[stackSize] = (byte) FrameSlotKind.Long.ordinal();
        stack[stackSize++] = value;
    }

    @Override
    public void pushFloat(float value) {
        stackTag[stackSize] = (byte) FrameSlotKind.Float.ordinal();
        stack[stackSize++] = value;
    }

    @Override
    public void pushDouble(double value) {
        pushIllegal();
        stackTag[stackSize] = (byte) FrameSlotKind.Double.ordinal();
        stack[stackSize++] = value;
    }

    public FrameSlotKind peekTag() {
        // TODO(peterssen): Avoid recreating values() array.
        return KIND_VALUES.get(stackTag[stackSize - 1]);
    }

    @Override
    public Object popObject() {
        assert peekTag() == FrameSlotKind.Object;
        Object top = stack[--stackSize];
        assert top != null : "Use StaticObject.NULL";
        assert isEspressoReference(top);
        return top;
    }

    @Override
    public int popInt() {
        assert peekTag() == FrameSlotKind.Int;
        return (int) stack[--stackSize];
    }

    @Override
    public float popFloat() {
        assert peekTag() == FrameSlotKind.Float;
        return (float) stack[--stackSize];
    }

    @Override
    public long popLong() {
        assert peekTag() == FrameSlotKind.Long;
        long ret = (long) stack[--stackSize];
        popIllegal();
        return ret;
    }

    @Override
    public double popDouble() {
        assert peekTag() == FrameSlotKind.Double;
        double ret = (double) stack[--stackSize];
        popIllegal();
        return ret;
    }

    private void popIllegal() {
        assert peekTag() == FrameSlotKind.Illegal;
        assert stackSize > 0;
        --stackSize;
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
    public void dup1() {
        assert numberOfSlots(peekTag()) == 1;
        pushUnsafe1(peekUnsafe1(), peekTag());
    }

    public Object popUnsafe1() {
        return stack[--stackSize];
    }

    public Object peekUnsafe1() {
        return stack[stackSize - 1];
    }

    public void pushUnsafe1(Object value, FrameSlotKind kind) {
        // assert numberOfSlots(kind) == 1;
        stackTag[stackSize] = (byte) kind.ordinal();
        stack[stackSize++] = value;
    }

    @Override
    public void swapSingle() {
        // value2, value1 → value1, value2
        FrameSlotKind tag1 = peekTag();
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1();

        FrameSlotKind tag2 = peekTag();
        assert numberOfSlots(tag2) == 1;
        Object elem2 = popUnsafe1();

        pushUnsafe1(elem1, tag1);
        pushUnsafe1(elem2, tag2);
    }

    @Override
    public void dupx1() {
        // value2, value1 → value1, value2, value1
        FrameSlotKind tag1 = peekTag();
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1();

        FrameSlotKind tag2 = peekTag();
        assert numberOfSlots(tag2) == 1;
        Object elem2 = popUnsafe1();

        pushUnsafe1(elem1, tag1);
        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
    }

    @Override
    public void dupx2() {
        // value3, value2, value1 → value1, value3, value2, value1
        FrameSlotKind tag1 = peekTag();
        assert numberOfSlots(tag1) == 1;
        Object elem1 = popUnsafe1();

        FrameSlotKind tag2 = peekTag();
        Object elem2 = popUnsafe1();

        FrameSlotKind tag3 = peekTag();
        Object elem3 = popUnsafe1();

        pushUnsafe1(elem1, tag1);
        pushUnsafe1(elem3, tag3);
        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
    }

    @Override
    public void dup2() {
        // {value2, value1} → {value2, value1}, {value2, value1}
        FrameSlotKind tag1 = peekTag();
        Object elem1 = popUnsafe1();

        FrameSlotKind tag2 = peekTag();
        Object elem2 = popUnsafe1();

        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
    }

    @Override
    public void dup2x1() {
        // value3, {value2, value1} → {value2, value1}, value3, {value2, value1}
        FrameSlotKind tag1 = peekTag();
        Object elem1 = popUnsafe1();

        FrameSlotKind tag2 = peekTag();
        Object elem2 = popUnsafe1();

        FrameSlotKind tag3 = peekTag();
        assert numberOfSlots(tag3) == 1;
        Object elem3 = popUnsafe1();

        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
        pushUnsafe1(elem3, tag3);
        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
    }

    @Override
    public void dup2x2() {
        // {value4, value3}, {value2, value1} → {value2, value1}, {value4, value3}, {value2, value1}
        FrameSlotKind tag1 = peekTag();
        Object elem1 = popUnsafe1();
        FrameSlotKind tag2 = peekTag();
        Object elem2 = popUnsafe1();
        FrameSlotKind tag3 = peekTag();
        Object elem3 = popUnsafe1();
        FrameSlotKind tag4 = peekTag();
        Object elem4 = popUnsafe1();
        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
        pushUnsafe1(elem4, tag4);
        pushUnsafe1(elem3, tag3);
        pushUnsafe1(elem2, tag2);
        pushUnsafe1(elem1, tag1);
    }

    @Override
    public Object peekReceiver(MethodInfo method) {
        assert !method.isStatic();
        int slots = method.getSignature().getNumberOfSlotsForParameters();
        Object receiver = stack[stackSize - slots - 1];
        assert isEspressoReference(receiver);
        return receiver;
    }

    @Override
    public void clear() {
        stackSize = 0;
    }
}
