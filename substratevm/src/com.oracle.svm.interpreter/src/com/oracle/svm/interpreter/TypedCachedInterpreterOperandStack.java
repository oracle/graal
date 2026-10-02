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
 * Operand-stack overlay caching up to two primitive values. Template states identify each value
 * as int, float, long, or double, in bottom-to-top order. Integer payloads use long fields and
 * floating-point payloads use double fields so they can remain in floating-point registers.
 * Float payloads occupy the low 32 raw bits, without numeric widening. Longs and doubles each
 * occupy two JVM slots when materialized. References and profiling/debugging remain materialized.
 */
final class TypedCachedInterpreterOperandStack extends InterpreterOperandStack {
    // Primitive value kinds, independent of their JVM slot widths.
    private static final int KIND_INT = 1;
    private static final int KIND_FLOAT = 2;
    private static final int KIND_LONG = 3;
    private static final int KIND_DOUBLE = 4;

    static final int STATE_TOS_INT = 3;
    static final int STATE_TOS_FLOAT = 4;
    static final int STATE_TOS_LONG = 5;
    static final int STATE_TOS_DOUBLE = 6;
    static final int STATE_TOS_INT_INT = 7;
    static final int STATE_TOS_INT_FLOAT = 8;
    static final int STATE_TOS_INT_LONG = 9;
    static final int STATE_TOS_INT_DOUBLE = 10;
    static final int STATE_TOS_FLOAT_INT = 11;
    static final int STATE_TOS_FLOAT_FLOAT = 12;
    static final int STATE_TOS_FLOAT_LONG = 13;
    static final int STATE_TOS_FLOAT_DOUBLE = 14;
    static final int STATE_TOS_LONG_INT = 15;
    static final int STATE_TOS_LONG_FLOAT = 16;
    static final int STATE_TOS_LONG_LONG = 17;
    static final int STATE_TOS_LONG_DOUBLE = 18;
    static final int STATE_TOS_DOUBLE_INT = 19;
    static final int STATE_TOS_DOUBLE_FLOAT = 20;
    static final int STATE_TOS_DOUBLE_LONG = 21;
    static final int STATE_TOS_DOUBLE_DOUBLE = 22;

    private long tosPrimitive0;
    private long tosPrimitive1;
    private double tosFloating0;
    private double tosFloating1;

    TypedCachedInterpreterOperandStack(long top) {
        super(top);
    }

    @Override
    boolean isValidState(int state) {
        return state >= STATE_NORMAL && state <= STATE_TOS_DOUBLE_DOUBLE;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int firstKind() {
        if (state >= STATE_TOS_INT_INT) {
            return (state - STATE_TOS_INT_INT) / 4 + KIND_INT;
        }
        return state >= STATE_TOS_INT ? state - STATE_TOS_INT + KIND_INT : 0;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int secondKind() {
        return state >= STATE_TOS_INT_INT ? (state - STATE_TOS_INT_INT) % 4 + KIND_INT : 0;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private static int category(int kind) {
        return kind == 0 ? 0 : kind <= KIND_FLOAT ? 1 : 2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private static boolean isFloating(int kind) {
        return kind == KIND_FLOAT || kind == KIND_DOUBLE;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int firstCategory() {
        return category(firstKind());
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int secondCategory() {
        return category(secondKind());
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int cachedSlots() {
        return firstCategory() + secondCategory();
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int topKind() {
        return secondKind() == 0 ? firstKind() : secondKind();
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private int topCategory() {
        return category(topKind());
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private static int singleState(int kind) {
        assert kind >= KIND_INT && kind <= KIND_DOUBLE;
        return STATE_TOS_INT + kind - KIND_INT;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private static int pairState(int first, int second) {
        assert first >= KIND_INT && first <= KIND_DOUBLE && second >= KIND_INT && second <= KIND_DOUBLE;
        return STATE_TOS_INT_INT + (first - KIND_INT) * 4 + second - KIND_INT;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private long rawPrimitive0() {
        return switch (firstKind()) {
            case KIND_FLOAT -> Float.floatToRawIntBits(GraalDirectives.assumeFloat(tosFloating0));
            case KIND_DOUBLE -> Double.doubleToRawLongBits(tosFloating0);
            default -> tosPrimitive0;
        };
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private long rawPrimitive1() {
        return switch (secondKind()) {
            case KIND_FLOAT -> Float.floatToRawIntBits(GraalDirectives.assumeFloat(tosFloating1));
            case KIND_DOUBLE -> Double.doubleToRawLongBits(tosFloating1);
            default -> tosPrimitive1;
        };
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
            frame.setPrimitive(top, first - 1, rawPrimitive0());
            if (second != 0) {
                frame.setPrimitive(top, first + second - 1, rawPrimitive1());
            }
            top += first + second;
        }
        setState(STATE_NORMAL);
        tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
        tosPrimitive1 = GraalDirectives.arbitraryValue(tosPrimitive1);
        tosFloating0 = GraalDirectives.arbitraryValue(tosFloating0);
        tosFloating1 = GraalDirectives.arbitraryValue(tosFloating1);
    }

    @AlwaysInline("Kill dependencies on unused cached primitive values")
    void killUnusedFields() {
        if (firstKind() == 0 || isFloating(firstKind())) {
            tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
        }
        if (secondKind() == 0 || isFloating(secondKind())) {
            tosPrimitive1 = GraalDirectives.arbitraryValue(tosPrimitive1);
        }
        if (!isFloating(firstKind())) {
            tosFloating0 = GraalDirectives.arbitraryValue(tosFloating0);
        }
        if (!isFloating(secondKind())) {
            tosFloating1 = GraalDirectives.arbitraryValue(tosFloating1);
        }
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void pushCached(InterpreterFrame frame, long primitive, double floating, int kind) {
        int first = firstKind();
        int second = secondKind();
        if (second != 0) {
            frame.setPrimitive(top, category(first) - 1, rawPrimitive0());
            top += category(first);
            tosPrimitive0 = tosPrimitive1;
            tosFloating0 = tosFloating1;
            first = second;
        }
        if (first == 0) {
            if (isFloating(kind)) {
                tosFloating0 = floating;
            } else {
                tosPrimitive0 = primitive;
            }
            setState(singleState(kind));
        } else {
            if (isFloating(kind)) {
                tosFloating1 = floating;
            } else {
                tosPrimitive1 = primitive;
            }
            setState(pairState(first, kind));
        }
        killUnusedFields();
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushInt(InterpreterFrame frame, int value) {
        if (state == STATE_PROFILING || state == STATE_DEBUGGING) {
            super.pushInt(frame, value);
        } else {
            pushCached(frame, value, 0, KIND_INT);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushFloat(InterpreterFrame frame, float value) {
        if (state == STATE_PROFILING || state == STATE_DEBUGGING) {
            super.pushFloat(frame, value);
        } else {
            // Store raw bits, not a numeric widening, to preserve NaN payloads and signed zero.
            pushCached(frame, 0, GraalDirectives.packFloat(value), KIND_FLOAT);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushLong(InterpreterFrame frame, long value) {
        if (state == STATE_PROFILING || state == STATE_DEBUGGING) {
            super.pushLong(frame, value);
        } else {
            pushCached(frame, value, 0, KIND_LONG);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushDouble(InterpreterFrame frame, double value) {
        if (state == STATE_PROFILING || state == STATE_DEBUGGING) {
            super.pushDouble(frame, value);
        } else {
            pushCached(frame, 0, value, KIND_DOUBLE);
        }
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
    private void popCachedValue() {
        setState(secondKind() == 0 ? STATE_NORMAL : singleState(firstKind()));
        killUnusedFields();
    }

    @Override
    @AlwaysInline("Materialize cached arguments before the argument loop")
    void popArguments(InterpreterFrame frame, byte[] argumentKinds, Object[] arguments, long argumentIndex) {
        if (state == STATE_PROFILING || state == STATE_DEBUGGING) {
            super.popArguments(frame, argumentKinds, arguments, argumentIndex);
            return;
        }
        materialize(frame);
        assert state == STATE_NORMAL;
        super.popArguments(frame, argumentKinds, arguments, argumentIndex);
        setState(STATE_NORMAL);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int popInt(InterpreterFrame frame) {
        if (firstKind() == 0) {
            return super.popInt(frame);
        }
        if (topKind() != KIND_INT) {
            UnreachablePathNode.unreachable();
        }
        int value = GraalDirectives.assumeInt(secondKind() == 0 ? tosPrimitive0 : tosPrimitive1);
        popCachedValue();
        return value;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float popFloat(InterpreterFrame frame) {
        if (firstKind() == 0) {
            return super.popFloat(frame);
        }
        if (topKind() != KIND_FLOAT) {
            UnreachablePathNode.unreachable();
        }
        float value = GraalDirectives.assumeFloat(secondKind() == 0 ? tosFloating0 : tosFloating1);
        popCachedValue();
        return value;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long popLong(InterpreterFrame frame) {
        if (firstKind() == 0) {
            return super.popLong(frame);
        }
        if (topKind() != KIND_LONG) {
            UnreachablePathNode.unreachable();
        }
        long value = secondKind() == 0 ? tosPrimitive0 : tosPrimitive1;
        popCachedValue();
        return value;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double popDouble(InterpreterFrame frame) {
        if (firstKind() == 0) {
            return super.popDouble(frame);
        }
        if (topKind() != KIND_DOUBLE) {
            UnreachablePathNode.unreachable();
        }
        double value = secondKind() == 0 ? tosFloating0 : tosFloating1;
        popCachedValue();
        return value;
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
            if (topCategory() != 1) {
                UnreachablePathNode.unreachable();
            }
            popCachedValue();
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop2(InterpreterFrame frame, boolean clear) {
        if (firstCategory() == 0) {
            super.pop2(frame, clear);
        } else if (topCategory() == 2) {
            popCachedValue();
        } else {
            pop1(frame, clear);
            pop1(frame, clear);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int peekInt(InterpreterFrame frame, long offset) {
        if (offset < -cachedSlots()) {
            return super.peekInt(frame, offset + cachedSlots());
        }
        if (secondKind() == KIND_INT && offset == -1) {
            return GraalDirectives.assumeInt(tosPrimitive1);
        }
        if (firstKind() == KIND_INT && offset == -secondCategory() - 1) {
            return GraalDirectives.assumeInt(tosPrimitive0);
        }
        UnreachablePathNode.unreachable();
        return 0;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float peekFloat(InterpreterFrame frame, long offset) {
        if (offset < -cachedSlots()) {
            return super.peekFloat(frame, offset + cachedSlots());
        }
        if (secondKind() == KIND_FLOAT && offset == -1) {
            return GraalDirectives.assumeFloat(tosFloating1);
        }
        if (firstKind() == KIND_FLOAT && offset == -secondCategory() - 1) {
            return GraalDirectives.assumeFloat(tosFloating0);
        }
        UnreachablePathNode.unreachable();
        return 0;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long peekLong(InterpreterFrame frame, long offset) {
        if (offset < -cachedSlots()) {
            return super.peekLong(frame, offset + cachedSlots());
        }
        if (secondKind() == KIND_LONG && offset == -1) {
            return tosPrimitive1;
        }
        if (firstKind() == KIND_LONG && offset == -secondCategory() - 1) {
            return tosPrimitive0;
        }
        UnreachablePathNode.unreachable();
        return 0;
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double peekDouble(InterpreterFrame frame, long offset) {
        if (offset < -cachedSlots()) {
            return super.peekDouble(frame, offset + cachedSlots());
        }
        if (secondKind() == KIND_DOUBLE && offset == -1) {
            return tosFloating1;
        }
        if (firstKind() == KIND_DOUBLE && offset == -secondCategory() - 1) {
            return tosFloating0;
        }
        UnreachablePathNode.unreachable();
        return 0;
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
            if (topKind() == KIND_FLOAT) {
                pushFloat(frame, peekFloat(frame, -1));
            } else {
                pushInt(frame, peekInt(frame, -1));
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx1(InterpreterFrame frame) {
        if (firstCategory() == 0) {
            super.dupx1(frame);
        } else if (firstCategory() == 1 && secondCategory() == 0) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, rawPrimitive0());
            top++;
        } else if (firstCategory() == 1 && secondCategory() == 1) {
            frame.setPrimitive(top, 0, rawPrimitive1());
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
        } else if (firstCategory() == 1 && secondCategory() == 0) {
            copySlot(frame, -1, 0);
            copySlot(frame, -2, -1);
            overwriteSlot(frame, -2, rawPrimitive0());
            top++;
        } else if (firstCategory() == 1 && secondCategory() == 1) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, rawPrimitive1());
            top++;
        } else if (firstCategory() == 2 && secondCategory() == 1) {
            frame.setPrimitive(top, 0, rawPrimitive1());
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
            if (topKind() == KIND_DOUBLE) {
                pushDouble(frame, peekDouble(frame, -1));
            } else {
                pushLong(frame, peekLong(frame, -1));
            }
        } else if (firstCategory() == 1 && secondCategory() == 0) {
            frame.setPrimitive(top, 0, rawPrimitive0());
            copySlot(frame, -1, 1);
            top += 2;
        } else if (firstCategory() == 1 && secondCategory() == 1) {
            frame.setPrimitive(top, 0, rawPrimitive0());
            frame.setPrimitive(top, 1, rawPrimitive1());
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
        } else if (firstCategory() == 1 && secondCategory() == 0) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            copySlot(frame, -1, -2);
            overwriteSlot(frame, -1, rawPrimitive0());
            top += 2;
        } else if (firstCategory() == 1 && secondCategory() == 1) {
            copySlot(frame, -1, 1);
            overwriteSlot(frame, -1, rawPrimitive0());
            overwriteSlot(frame, 0, rawPrimitive1());
            top += 2;
        } else if (firstCategory() == 2 && secondCategory() == 0) {
            copySlot(frame, -1, 1);
            overwriteCategory2(frame, -1, rawPrimitive0());
            top += 2;
        } else if (firstCategory() == 1 && secondCategory() == 2) {
            frame.setPrimitive(top, 1, rawPrimitive1());
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
        } else if (firstCategory() == 1 && secondCategory() == 0) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            copySlot(frame, -3, -1);
            copySlot(frame, 1, -3);
            overwriteSlot(frame, -2, rawPrimitive0());
            top += 2;
        } else if (firstCategory() == 1 && secondCategory() == 1) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            overwriteSlot(frame, -2, rawPrimitive0());
            overwriteSlot(frame, -1, rawPrimitive1());
            top += 2;
        } else if (firstCategory() == 2 && secondCategory() == 0) {
            copySlot(frame, -1, 1);
            copySlot(frame, -2, 0);
            overwriteCategory2(frame, -2, rawPrimitive0());
            top += 2;
        } else if (firstCategory() == 1 && secondCategory() == 2) {
            copySlot(frame, -1, 1);
            overwriteCategory2(frame, -1, rawPrimitive1());
            top += 2;
        } else if (firstCategory() == 2 && secondCategory() == 2) {
            frame.setPrimitive(top, 1, rawPrimitive1());
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
        } else if (firstCategory() == 1 && secondCategory() == 0) {
            copySlot(frame, -1, 0);
            overwriteSlot(frame, -1, rawPrimitive0());
            top++;
            setState(STATE_NORMAL);
        } else if (firstCategory() == 1 && secondCategory() == 1) {
            long value = tosPrimitive0;
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = value;
            double floating = tosFloating0;
            tosFloating0 = tosFloating1;
            tosFloating1 = floating;
            setState(pairState(secondKind(), firstKind()));
            killUnusedFields();
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
