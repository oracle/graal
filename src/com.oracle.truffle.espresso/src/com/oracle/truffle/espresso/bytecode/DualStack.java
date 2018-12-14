/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;

import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;

public final class DualStack implements OperandStack {

    private final Object[] stack;
    private final long[] primitiveStack;

    private final byte[] stackTag;
    private int stackSize;

    public DualStack(int maxStackSize) {
        this.stack = new Object[maxStackSize];
        this.primitiveStack = new long[maxStackSize];
        this.stackTag = new byte[maxStackSize];
        this.stackSize = 0;
    }

    // region Operand stack operations

    @Override
    public void popVoid(int slots) {
        assert slots == 1 || slots == 2;
        stackSize -= slots;
        assert stackSize >= 0;
    }

    @Override
    public void pushObject(StaticObject value) {
        assert value != null;
        stackTag[stackSize] = (byte) JavaKind.Object.ordinal();
        stack[stackSize++] = value;
    }

    @Override
    public void pushReturnAddress(int bci) {
        assert bci >= 0;
        stackTag[stackSize] = (byte) JavaKind.ReturnAddress.ordinal();
        stack[stackSize++] = ReturnAddress.create(bci);
    }

    @Override
    public void pushInt(int value) {
        stackTag[stackSize] = (byte) JavaKind.Int.ordinal();
        stack[stackSize] = null;
        primitiveStack[stackSize] = value;
        stackSize++;
    }

    public void pushIllegal() {
        stackTag[stackSize] = (byte) JavaKind.Illegal.ordinal();
        stack[stackSize++] = null;
    }

    @Override
    public void pushLong(long value) {
        pushIllegal();
        stackTag[stackSize] = (byte) JavaKind.Long.ordinal();
        stack[stackSize] = null;
        primitiveStack[stackSize] = value;
        stackSize++;
    }

    @Override
    public void pushFloat(float value) {
        stackTag[stackSize] = (byte) JavaKind.Float.ordinal();
        stack[stackSize] = null;
        primitiveStack[stackSize] = Float.floatToRawIntBits(value);
        stackSize++;
    }

    @Override
    public void pushDouble(double value) {
        pushIllegal();
        stackTag[stackSize] = (byte) JavaKind.Double.ordinal();
        stack[stackSize] = null;
        primitiveStack[stackSize] = Double.doubleToRawLongBits(value);
        stackSize++;
    }

    public JavaKind peekTag() {
        return KIND_VALUES.get(stackTag[stackSize - 1]);
    }

    @Override
    public StaticObject popObject() {
        assert peekTag() == JavaKind.Object;
        Object top = stack[--stackSize];
        assert top != null : "Use StaticObject.NULL";
        return (StaticObject) top;
    }

    @Override
    public Object popReturnAddressOrObject() {
        assert peekTag() == JavaKind.Object || peekTag() == JavaKind.ReturnAddress;
        Object top = stack[--stackSize];
        assert top != null : "Use StaticObject.NULL";
        assert top instanceof StaticObject || top instanceof ReturnAddress;
        return top;
    }

    @Override
    public int popInt() {
        assert peekTag() == JavaKind.Int;
        return (int) primitiveStack[--stackSize];
    }

    @Override
    public float popFloat() {
        assert peekTag() == JavaKind.Float;
        return Float.intBitsToFloat((int) primitiveStack[--stackSize]);
    }

    @Override
    public long popLong() {
        assert peekTag() == JavaKind.Long;
        long ret = primitiveStack[--stackSize];
        popIllegal();
        return ret;
    }

    @Override
    public double popDouble() {
        assert peekTag() == JavaKind.Double;
        double ret = Double.longBitsToDouble(primitiveStack[--stackSize]);
        popIllegal();
        return ret;
    }

    public void popIllegal() {
        assert peekTag() == JavaKind.Illegal;
        assert stackSize > 0;
        --stackSize;
    }

    static int numberOfSlots(JavaKind kind) {
        assert kind != null;
        if (kind == JavaKind.Long || kind == JavaKind.Double) {
            return 2;
        }
        // Illegal takes 1 slot.
        return 1;
    }

    @Override
    public void dup1() {
        assert numberOfSlots(peekTag()) == 1;
        stack[stackSize] = stack[stackSize - 1];
        primitiveStack[stackSize] = primitiveStack[stackSize - 1];
        stackTag[stackSize] = stackTag[stackSize - 1];
        ++stackSize;
    }

    @Override
    public void swapSingle() {
        // value2, value1 → value1, value2
        assert numberOfSlots(KIND_VALUES.get(stackTag[stackSize - 1])) == 1;
        assert numberOfSlots(KIND_VALUES.get(stackTag[stackSize - 2])) == 1;

        Object o1 = stack[stackSize - 1];
        long p1 = primitiveStack[stackSize - 1];
        byte t1 = stackTag[stackSize - 1];

        stack[stackSize - 1] = stack[stackSize - 2];
        primitiveStack[stackSize - 1] = primitiveStack[stackSize - 2];
        stackTag[stackSize - 1] = stackTag[stackSize - 2];

        stack[stackSize - 2] = o1;
        primitiveStack[stackSize - 2] = p1;
        stackTag[stackSize - 2] = t1;
    }

    @Override
    public void dupx1() {
        // value2, value1 → value1, value2, value1
        assert numberOfSlots(KIND_VALUES.get(stackTag[stackSize - 1])) == 1;
        assert numberOfSlots(KIND_VALUES.get(stackTag[stackSize - 2])) == 1;

        Object o1 = stack[stackSize - 1];
        long p1 = primitiveStack[stackSize - 1];
        byte t1 = stackTag[stackSize - 1];

        Object o2 = stack[stackSize - 2];
        long p2 = primitiveStack[stackSize - 2];
        byte t2 = stackTag[stackSize - 2];

        stack[stackSize - 2] = o1;
        primitiveStack[stackSize - 2] = p1;
        stackTag[stackSize - 2] = t1;

        stack[stackSize - 1] = o2;
        primitiveStack[stackSize - 1] = p2;
        stackTag[stackSize - 1] = t2;

        stack[stackSize] = o1;
        primitiveStack[stackSize] = p1;
        stackTag[stackSize] = t1;
        ++stackSize;
    }

    @Override
    public void dupx2() {
        // value3, value2, value1 → value1, value3, value2, value1
        JavaKind tag1 = peekTag();
        assert numberOfSlots(tag1) == 1;

        Object o1 = stack[stackSize - 1];
        long p1 = primitiveStack[stackSize - 1];
        byte t1 = stackTag[stackSize - 1];

        Object o2 = stack[stackSize - 2];
        long p2 = primitiveStack[stackSize - 2];
        byte t2 = stackTag[stackSize - 2];

        Object o3 = stack[stackSize - 3];
        long p3 = primitiveStack[stackSize - 3];
        byte t3 = stackTag[stackSize - 3];

        stack[stackSize - 3] = o1;
        primitiveStack[stackSize - 3] = p1;
        stackTag[stackSize - 3] = t1;

        stack[stackSize - 2] = o3;
        primitiveStack[stackSize - 2] = p3;
        stackTag[stackSize - 2] = t3;

        stack[stackSize - 1] = o2;
        primitiveStack[stackSize - 1] = p2;
        stackTag[stackSize - 1] = t2;

        stack[stackSize] = o1;
        primitiveStack[stackSize] = p1;
        stackTag[stackSize] = t1;
        ++stackSize;
    }

    @Override
    public void dup2() {
        // {value2, value1} → {value2, value1}, {value2, value1}

        Object o1 = stack[stackSize - 1];
        long p1 = primitiveStack[stackSize - 1];
        byte t1 = stackTag[stackSize - 1];

        Object o2 = stack[stackSize - 2];
        long p2 = primitiveStack[stackSize - 2];
        byte t2 = stackTag[stackSize - 2];

        stack[stackSize] = o2;
        primitiveStack[stackSize] = p2;
        stackTag[stackSize] = t2;
        ++stackSize;

        stack[stackSize] = o1;
        primitiveStack[stackSize] = p1;
        stackTag[stackSize] = t1;
        ++stackSize;
    }

    @Override
    public void dup2x1() {
        // value3, {value2, value1} → {value2, value1}, value3, {value2, value1}

        Object o1 = stack[stackSize - 1];
        long p1 = primitiveStack[stackSize - 1];
        byte t1 = stackTag[stackSize - 1];

        Object o2 = stack[stackSize - 2];
        long p2 = primitiveStack[stackSize - 2];
        byte t2 = stackTag[stackSize - 2];

        Object o3 = stack[stackSize - 3];
        long p3 = primitiveStack[stackSize - 3];
        byte t3 = stackTag[stackSize - 3];

        stack[stackSize - 3] = o2;
        primitiveStack[stackSize - 3] = p2;
        stackTag[stackSize - 3] = t2;

        stack[stackSize - 2] = o1;
        primitiveStack[stackSize - 2] = p1;
        stackTag[stackSize - 2] = t1;

        stack[stackSize - 1] = o3;
        primitiveStack[stackSize - 1] = p3;
        stackTag[stackSize - 1] = t3;

        stack[stackSize] = o2;
        primitiveStack[stackSize] = p2;
        stackTag[stackSize] = t2;
        ++stackSize;

        stack[stackSize] = o1;
        primitiveStack[stackSize] = p1;
        stackTag[stackSize] = t1;
        ++stackSize;
    }

    @Override
    public void dup2x2() {
        // {value4, value3}, {value2, value1} → {value2, value1}, {value4, value3}, {value2, value1}

        Object o1 = stack[stackSize - 1];
        long p1 = primitiveStack[stackSize - 1];
        byte t1 = stackTag[stackSize - 1];

        Object o2 = stack[stackSize - 2];
        long p2 = primitiveStack[stackSize - 2];
        byte t2 = stackTag[stackSize - 2];

        Object o3 = stack[stackSize - 3];
        long p3 = primitiveStack[stackSize - 3];
        byte t3 = stackTag[stackSize - 3];

        Object o4 = stack[stackSize - 4];
        long p4 = primitiveStack[stackSize - 4];
        byte t4 = stackTag[stackSize - 4];

        stack[stackSize - 4] = o2;
        primitiveStack[stackSize - 4] = p2;
        stackTag[stackSize - 4] = t2;

        stack[stackSize - 3] = o1;
        primitiveStack[stackSize - 3] = p1;
        stackTag[stackSize - 3] = t1;

        stack[stackSize - 2] = o4;
        primitiveStack[stackSize - 2] = p4;
        stackTag[stackSize - 2] = t4;

        stack[stackSize - 1] = o3;
        primitiveStack[stackSize - 1] = p3;
        stackTag[stackSize - 1] = t3;

        stack[stackSize] = o2;
        primitiveStack[stackSize] = p2;
        stackTag[stackSize] = t2;
        ++stackSize;

        stack[stackSize] = o1;
        primitiveStack[stackSize] = p1;
        stackTag[stackSize] = t1;
        ++stackSize;
    }

    @Override
    public StaticObject peekReceiver(MethodInfo method) {
        assert !Modifier.isStatic(method.getModifiers());
        int slots = method.getSignature().getNumberOfSlotsForParameters();
        Object receiver = stack[stackSize - slots - 1];
        return (StaticObject) receiver;
    }

    @Override
    public void clear() {
        stackSize = 0;
    }

    // endregion Stack operations
}
