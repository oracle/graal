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

import com.oracle.truffle.espresso.runtime.StaticObject;

public final class OperandStack {

    private final Object[] refs;
    private final long[] primitives;

    public OperandStack(int maxStackSize) {
        this.refs = new Object[maxStackSize];
        this.primitives = new long[maxStackSize];
    }

    public int peekInt(int slot) {
        return (int) primitives[slot];
    }

    public int popInt(int slot) {
        int result = peekInt(slot);
        primitives[slot] = 0;
        return result;
    }

    void putRawObject(int slot, Object value) {
        refs[slot] = value;
    }

    public void putObject(int slot, StaticObject value) {
        assert value != null : "use putRawObject to store host nulls";
        refs[slot] = value;
    }

    public StaticObject popObject(int slot) {
        // nulls-out the slot, use peekObject to read only
        Object result = refs[slot];
        putRawObject(slot, null);
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    public long peekLong(int slot) {
        return primitives[slot];
    }

    public long popLong(int slot) {
        long result = peekLong(slot);
        primitives[slot] = 0;
        return result;
    }

    public StaticObject peekObject(int slot) {
        Object result = refs[slot];
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    Object peekRawObject(int slot) {
        return refs[slot];
    }

    public void putLong(int slot, long value) {
        primitives[slot] = value;
        assert peekLong(slot) == value;
    }

    public void putInt(int slot, int value) {
        primitives[slot] = value;
    }

    // endregion Stack operations

    public void dup1(int top) {
        // value1 -> value1, value1
        primitives[top] = primitives[top - 1];
        refs[top] = refs[top - 1];
    }

    public void dupx1(int top) {
        // value2, value1 -> value1, value2, value1
        Object r1 = refs[top - 1];
        long p1 = primitives[top - 1];

        Object r2 = refs[top - 2];
        long p2 = primitives[top - 2];

        refs[top - 2] = r1;
        primitives[top - 2] = p1;

        refs[top - 1] = r2;
        primitives[top - 1] = p2;

        refs[top] = r1;
        primitives[top] = p1;
    }

    public void dupx2(int top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        Object r1 = refs[top - 1];
        long p1 = primitives[top - 1];

        Object r2 = refs[top - 2];
        long p2 = primitives[top - 2];

        Object r3 = refs[top - 3];
        long p3 = primitives[top - 3];

        refs[top - 3] = r1;
        primitives[top - 3] = p1;

        refs[top - 2] = r3;
        primitives[top - 2] = p3;

        refs[top - 1] = r2;
        primitives[top - 1] = p2;

        refs[top] = r1;
        primitives[top] = p1;
    }

    public void dup2(int top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        Object r1 = refs[top - 1];
        long p1 = primitives[top - 1];
        Object r2 = refs[top - 2];
        long p2 = primitives[top - 2];
        refs[top] = r2;
        primitives[top] = p2;
        refs[top + 1] = r1;
        primitives[top + 1] = p1;
    }

    public void swapSingle(int top) {
        // value2, value1 -> value1, value2
        Object r1 = refs[top - 1];
        long p1 = primitives[top - 1];
        Object r2 = refs[top - 2];
        long p2 = primitives[top - 2];

        refs[top - 2] = r1;
        primitives[top - 2] = p1;

        refs[top - 1] = r2;
        primitives[top - 1] = p2;
    }

    public void dup2x1(int top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        Object r1 = refs[top - 1];
        long p1 = primitives[top - 1];
        Object r2 = refs[top - 2];
        long p2 = primitives[top - 2];
        Object r3 = refs[top - 3];
        long p3 = primitives[top - 3];

        refs[top - 3] = r2;
        primitives[top - 3] = p2;

        refs[top - 2] = r1;
        primitives[top - 2] = p1;

        refs[top - 1] = r3;
        primitives[top - 1] = p3;

        refs[top] = r2;
        primitives[top] = p2;

        refs[top + 1] = r1;
        primitives[top + 1] = p1;
    }

    public void dup2x2(int top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        Object r1 = refs[top - 1];
        long p1 = primitives[top - 1];
        Object r2 = refs[top - 2];
        long p2 = primitives[top - 2];
        Object r3 = refs[top - 3];
        long p3 = primitives[top - 3];
        Object r4 = refs[top - 4];
        long p4 = primitives[top - 4];

        refs[top - 4] = r2;
        primitives[top - 4] = p2;

        refs[top - 3] = r1;
        primitives[top - 3] = p1;

        refs[top - 2] = r4;
        primitives[top - 2] = p4;

        refs[top - 1] = r3;
        primitives[top - 1] = p3;

        refs[top] = r2;
        primitives[top] = p2;

        refs[top + 1] = r1;
        primitives[top + 1] = p1;
    }

    public void clear(int slot) {
        primitives[slot] = 0;
        refs[slot] = null;
    }
}
