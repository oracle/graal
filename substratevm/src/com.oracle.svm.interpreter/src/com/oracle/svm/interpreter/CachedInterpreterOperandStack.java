/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import com.oracle.svm.core.graal.nodes.UnreachablePathNode;
import com.oracle.svm.shared.AlwaysInline;

import jdk.graal.compiler.api.directives.GraalDirectives;

/**
 * Operand-stack overlay used by bytecode handlers. Up to two primitive values are kept in the
 * threaded-handler state instead of being written to the frame. Each value occupies one cache
 * field, but category-2 values occupy two JVM slots when materialized. Categories are ordered
 * bottom-to-top; category 1 covers int/float and category 2 covers long/double. Object values are
 * always materialized so that reference-map and stack-walking semantics remain unchanged.
 * Profiling and debugging use the materialized stack without caching primitives.
 */
final class CachedInterpreterOperandStack extends InterpreterOperandStack {
    // Primitive cache categories, in bottom-to-top order. References are never cached.
    static final int STATE_TOS_CAT1 = 3;
    static final int STATE_TOS_CAT2 = 4;
    static final int STATE_TOS_CAT1_CAT1 = 5;
    static final int STATE_TOS_CAT1_CAT2 = 6;
    static final int STATE_TOS_CAT2_CAT1 = 7;
    static final int STATE_TOS_CAT2_CAT2 = 8;

    private long tosPrimitive0;
    private long tosPrimitive1;

    CachedInterpreterOperandStack(long top) {
        super(top);
    }

    @Override
    boolean isValidState(int state) {
        return state >= STATE_NORMAL && state <= STATE_TOS_CAT2_CAT2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int firstCategory() {
        return switch (getState()) {
            case STATE_TOS_CAT1, STATE_TOS_CAT1_CAT1, STATE_TOS_CAT1_CAT2 -> 1;
            case STATE_TOS_CAT2, STATE_TOS_CAT2_CAT1, STATE_TOS_CAT2_CAT2 -> 2;
            default -> 0;
        };
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int secondCategory() {
        return switch (getState()) {
            case STATE_TOS_CAT1_CAT1, STATE_TOS_CAT2_CAT1 -> 1;
            case STATE_TOS_CAT1_CAT2, STATE_TOS_CAT2_CAT2 -> 2;
            default -> 0;
        };
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int cachedSlots() {
        return firstCategory() + secondCategory();
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int topCategory() {
        return secondCategory() == 0 ? firstCategory() : secondCategory();
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private static int singleState(int category) {
        assert category == 1 || category == 2;
        return category == 1 ? STATE_TOS_CAT1 : STATE_TOS_CAT2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private static int pairState(int first, int second) {
        assert (first == 1 || first == 2) && (second == 1 || second == 2);
        if (first == 1) {
            return second == 1 ? STATE_TOS_CAT1_CAT1 : STATE_TOS_CAT1_CAT2;
        }
        return second == 1 ? STATE_TOS_CAT2_CAT1 : STATE_TOS_CAT2_CAT2;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long topForFrameStackOperation() {
        return top + cachedSlots();
    }

    @AlwaysInline("Materialize cached primitives before direct frame-stack access")
    long materializeForFrameStackOperation(InterpreterFrame frame) {
        materialize(frame);
        return top;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void applyFrameStackOperationDelta(int slotDelta) {
        assert cachedSlots() == 0;
        top += slotDelta;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void materialize(InterpreterFrame frame) {
        if (getState() == STATE_PROFILING || getState() == STATE_DEBUGGING) {
            return;
        }
        int first = firstCategory();
        int second = secondCategory();
        if (first != 0) {
            frame.setPrimitive(top, first - 1, tosPrimitive0);
            if (second != 0) {
                frame.setPrimitive(top, first + second - 1, tosPrimitive1);
            }
            top += first + second;
        }
        setState(STATE_NORMAL);
        killUnusedFields();
    }

    @AlwaysInline("Kill dependencies on unused cached primitive values")
    void killUnusedFields() {
        if (firstCategory() == 0) {
            tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
        }
        if (secondCategory() == 0) {
            tosPrimitive1 = GraalDirectives.arbitraryValue(tosPrimitive1);
        }
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void pushPrimitive(InterpreterFrame frame, long value, int category) {
        int first = firstCategory();
        int second = secondCategory();
        if (first == 0) {
            tosPrimitive0 = value;
            setState(singleState(category));
        } else if (second == 0) {
            tosPrimitive1 = value;
            setState(pairState(first, category));
        } else {
            frame.setPrimitive(top, first - 1, tosPrimitive0);
            top += first;
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = value;
            setState(pairState(second, category));
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushInt(InterpreterFrame frame, int value) {
        if (getState() == STATE_PROFILING || getState() == STATE_DEBUGGING) {
            super.pushInt(frame, value);
        } else {
            pushPrimitive(frame, value, 1);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushFloat(InterpreterFrame frame, float value) {
        pushInt(frame, Float.floatToRawIntBits(value));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushLong(InterpreterFrame frame, long value) {
        if (getState() == STATE_PROFILING || getState() == STATE_DEBUGGING) {
            super.pushLong(frame, value);
        } else {
            pushPrimitive(frame, value, 2);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushDouble(InterpreterFrame frame, double value) {
        pushLong(frame, Double.doubleToRawLongBits(value));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushObject(InterpreterFrame frame, Object value) {
        materialize(frame);
        super.pushObject(frame, value);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushReturnAddress(InterpreterFrame frame, int targetBCI) {
        materialize(frame);
        super.pushReturnAddress(frame, targetBCI);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private long popCached(int category) {
        if (topCategory() != category) {
            UnreachablePathNode.unreachable();
        }
        if (secondCategory() == 0) {
            setState(STATE_NORMAL);
            return tosPrimitive0;
        }
        setState(singleState(firstCategory()));
        return tosPrimitive1;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int popInt(InterpreterFrame frame) {
        return firstCategory() == 0 ? super.popInt(frame) : GraalDirectives.assumeInt(popCached(1));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float popFloat(InterpreterFrame frame) {
        return firstCategory() == 0 ? super.popFloat(frame) : GraalDirectives.assumeFloat(popCached(1));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long popLong(InterpreterFrame frame) {
        return firstCategory() == 0 ? super.popLong(frame) : popCached(2);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double popDouble(InterpreterFrame frame) {
        return Double.longBitsToDouble(popLong(frame));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object popObject(InterpreterFrame frame) {
        if (firstCategory() != 0) {
            // Objects are never cached, so verified bytecode cannot enter this variant.
            UnreachablePathNode.unreachable();
        }
        return super.popObject(frame);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop1(InterpreterFrame frame, boolean clear) {
        if (firstCategory() == 0) {
            super.pop1(frame, clear);
        } else {
            popCached(1);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop2(InterpreterFrame frame, boolean clear) {
        if (firstCategory() == 0) {
            super.pop2(frame, clear);
        } else if (topCategory() == 2) {
            popCached(2);
        } else {
            pop1(frame, clear);
            pop1(frame, clear);
        }
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private long peekCached(long offset, int category) {
        if (secondCategory() == category && offset == -1) {
            return tosPrimitive1;
        }
        if (firstCategory() == category && offset == -secondCategory() - 1) {
            return tosPrimitive0;
        }
        // A cached payload has a known category; padding slots are not values.
        UnreachablePathNode.unreachable();
        return 0;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int peekInt(InterpreterFrame frame, long offset) {
        if (firstCategory() == 0) {
            return super.peekInt(frame, offset);
        }
        if (offset < -cachedSlots()) {
            return (int) frame.getPrimitive(top, offset + cachedSlots());
        }
        return GraalDirectives.assumeInt(peekCached(offset, 1));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float peekFloat(InterpreterFrame frame, long offset) {
        if (firstCategory() == 0) {
            return super.peekFloat(frame, offset);
        }
        if (offset < -cachedSlots()) {
            return Float.intBitsToFloat((int) frame.getPrimitive(top, offset + cachedSlots()));
        }
        return GraalDirectives.assumeFloat(peekCached(offset, 1));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long peekLong(InterpreterFrame frame, long offset) {
        if (firstCategory() == 0) {
            return super.peekLong(frame, offset);
        }
        if (offset < -cachedSlots()) {
            return frame.getPrimitive(top, offset + cachedSlots());
        }
        return peekCached(offset, 2);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double peekDouble(InterpreterFrame frame, long offset) {
        return Double.longBitsToDouble(peekLong(frame, offset));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object peekObject(InterpreterFrame frame, long offset) {
        if (firstCategory() == 0) {
            return super.peekObject(frame, offset);
        }
        if (offset >= -cachedSlots()) {
            UnreachablePathNode.unreachable();
        }
        return frame.getReference(top, offset + cachedSlots());
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup1(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.dup1(frame);
        } else {
            pushInt(frame, peekInt(frame, -1));
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx1(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.dupx1(frame);
        } else if (getState() == STATE_TOS_CAT1) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, tosPrimitive0);
            top++;
        } else if (getState() == STATE_TOS_CAT1_CAT1) {
            frame.setPrimitive(top, 0, tosPrimitive1);
            top++;
        } else {
            UnreachablePathNode.unreachable();
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx2(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.dupx2(frame);
        } else if (getState() == STATE_TOS_CAT1) {
            copySlot(frame, -1, 0);
            copySlot(frame, -2, -1);
            overwriteSlot(frame, -2, tosPrimitive0);
            top++;
        } else if (getState() == STATE_TOS_CAT1_CAT1) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, tosPrimitive1);
            top++;
        } else if (getState() == STATE_TOS_CAT2_CAT1) {
            frame.setPrimitive(top, 0, tosPrimitive1);
            top++;
        } else {
            UnreachablePathNode.unreachable();
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.dup2(frame);
        } else if (topCategory() == 2) {
            pushLong(frame, peekLong(frame, -1));
        } else if (getState() == STATE_TOS_CAT1) {
            frame.setPrimitive(top, 0, tosPrimitive0);
            copySlot(frame, -1, 1);
            top += 2;
        } else if (getState() == STATE_TOS_CAT1_CAT1) {
            frame.setPrimitive(top, 0, tosPrimitive0);
            frame.setPrimitive(top, 1, tosPrimitive1);
            top += 2;
        } else {
            UnreachablePathNode.unreachable();
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x1(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.dup2x1(frame);
        } else if (getState() == STATE_TOS_CAT1) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            copySlot(frame, -1, -2);
            overwriteSlot(frame, -1, tosPrimitive0);
            top += 2;
        } else if (getState() == STATE_TOS_CAT1_CAT1) {
            copySlot(frame, -1, 1);
            overwriteSlot(frame, -1, tosPrimitive0);
            overwriteSlot(frame, 0, tosPrimitive1);
            top += 2;
        } else if (getState() == STATE_TOS_CAT2) {
            copySlot(frame, -1, 1);
            overwriteCategory2(frame, -1, tosPrimitive0);
            top += 2;
        } else if (getState() == STATE_TOS_CAT1_CAT2) {
            frame.setPrimitive(top, 1, tosPrimitive1);
            top += 2;
        } else {
            UnreachablePathNode.unreachable();
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x2(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.dup2x2(frame);
        } else if (getState() == STATE_TOS_CAT1) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            copySlot(frame, -3, -1);
            copySlot(frame, 1, -3);
            overwriteSlot(frame, -2, tosPrimitive0);
            top += 2;
        } else if (getState() == STATE_TOS_CAT1_CAT1) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            overwriteSlot(frame, -2, tosPrimitive0);
            overwriteSlot(frame, -1, tosPrimitive1);
            top += 2;
        } else if (getState() == STATE_TOS_CAT2) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            overwriteCategory2(frame, -2, tosPrimitive0);
            top += 2;
        } else if (getState() == STATE_TOS_CAT1_CAT2) {
            copySlot(frame, -1, 1);
            overwriteCategory2(frame, -1, tosPrimitive1);
            top += 2;
        } else if (getState() == STATE_TOS_CAT2_CAT2) {
            frame.setPrimitive(top, 1, tosPrimitive1);
            top += 2;
        } else {
            UnreachablePathNode.unreachable();
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void swap(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.swap(frame);
        } else if (getState() == STATE_TOS_CAT1) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, tosPrimitive0);
            top++;
            setState(STATE_NORMAL);
        } else if (getState() == STATE_TOS_CAT1_CAT1) {
            long value = tosPrimitive0;
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = value;
        } else {
            UnreachablePathNode.unreachable();
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void clearOperandStack(InterpreterFrame frame) {
        materialize(frame);
        super.clearOperandStack(frame);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void copySlot(InterpreterFrame frame, long srcOffset, long dstOffset) {
        frame.setPrimitive(top, dstOffset, frame.getPrimitive(top, srcOffset));
        frame.setReference(top, dstOffset, frame.getReference(top, srcOffset));
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void overwriteSlot(InterpreterFrame frame, long offset, long value) {
        frame.setReference(top, offset, null);
        frame.setPrimitive(top, offset, value);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void overwriteCategory2(InterpreterFrame frame, long offset, long value) {
        frame.setReference(top, offset, null);
        overwriteSlot(frame, offset + 1, value);
    }
}
