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

import com.oracle.svm.shared.AlwaysInline;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;

/**
 * Owns the materialized operand-stack pointer and caches up to two primitive operand-stack
 * slots. A category-2 value occupies both slots, with its payload in
 * {@link #tosPrimitive1}; {@link #tosPrimitive0} is still materialized because a level of
 * two can also represent two category-1 values. A category-2 value can straddle the cache
 * and memory, with its first slot materialized and its payload in {@link #tosPrimitive0}.
 * Each stack operation updates {@link #top} while compensating for changes in
 * {@link #tosLevel}, so the materialized stack pointer changes only when values enter or
 * leave memory.
 */
final class InterpreterVirtualStack {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final int T_BOOLEAN = 4;
    private static final int T_CHAR = 5;
    private static final int T_FLOAT = 6;
    private static final int T_DOUBLE = 7;
    private static final int T_BYTE = 8;
    private static final int T_SHORT = 9;
    private static final int T_INT = 10;
    private static final int T_LONG = 11;
    private static final int T_OBJECT = 12;

    /** First stack slot above the materialized operand stack. */
    long top;
    private long tosPrimitive0;
    private long tosPrimitive1;
    int tosLevel;

    InterpreterVirtualStack(long top) {
        this.top = top;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void avoidHoistingTop() {
        top = GraalDirectives.opaque(top);
    }

    /** Gives updated stack fields path-local identities and pins them before threaded dispatch. */
    @AlwaysInline("Prepare updated stack values before threaded dispatch")
    void anchorUpdatedValues() {
        top = GraalDirectives.anchorValue(GraalDirectives.opaque(top));
        if (tosLevel >= 1) {
            tosPrimitive0 = GraalDirectives.anchorValue(GraalDirectives.opaque(tosPrimitive0));
        }
        if (tosLevel >= 2) {
            tosPrimitive1 = GraalDirectives.anchorValue(GraalDirectives.opaque(tosPrimitive1));
        }
    }

    @AlwaysInline("Kill dependencies on unused cached primitive returns")
    void killUnusedFields() {
        if (tosLevel == 0) {
            tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
            tosPrimitive1 = GraalDirectives.arbitraryValue(tosPrimitive1);
        } else if (tosLevel == 1) {
            tosPrimitive1 = GraalDirectives.arbitraryValue(tosPrimitive1);
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void materialize(InterpreterState state) {
        if (tosLevel == 1) {
            state.setLongStatic(top, tosPrimitive0);
            top++;
            tosLevel = 0;
        } else if (tosLevel == 2) {
            state.setLongStatic(top, tosPrimitive0);
            state.setLongStatic(top + 1, tosPrimitive1);
            top += 2;
            tosLevel = 0;
        }
        killUnusedFields();
    }

    @AlwaysInline("Materialize before an outlined stack operation without passing InterpreterVirtualStack")
    long beginOutlinedCall(InterpreterState state) {
        materialize(state);
        return top;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void endOutlinedCall(int slotDelta) {
        assert tosLevel == 0;
        top += slotDelta;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushInt(InterpreterState state, int value) {
        if (tosLevel == 0) {
            tosPrimitive0 = value;
            tosLevel = 1;
        } else if (tosLevel == 1) {
            tosPrimitive1 = value;
            tosLevel = 2;
        } else {
            state.setLongStatic(top, tosPrimitive0);
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = value;
            top++;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushFloat(InterpreterState state, float value) {
        pushInt(state, Float.floatToRawIntBits(value));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushLong(InterpreterState state, long value) {
        if (tosLevel == 0) {
            tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
            tosPrimitive1 = value;
            tosLevel = 2;
        } else if (tosLevel == 1) {
            state.setLongStatic(top, tosPrimitive0);
            tosPrimitive1 = value;
            top++;
            tosLevel = 2;
        } else {
            // We cannot distinguish whether we saved one long or two ints.
            state.setLongStatic(top, tosPrimitive0);
            state.setLongStatic(top + 1, tosPrimitive1);
            tosPrimitive1 = value;
            top += 2;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushDouble(InterpreterState state, double value) {
        pushLong(state, Double.doubleToRawLongBits(value));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushObject(InterpreterState state, Object value) {
        materialize(state);
        state.putObject(top, value);
        top++;
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    void pushKind(InterpreterState state, JavaKind kind, Object value) {
        switch (kind) {
            case Boolean -> pushInt(state, ((Boolean) value) ? 1 : 0);
            case Byte -> pushInt(state, (Byte) value);
            case Short -> pushInt(state, (Short) value);
            case Char -> pushInt(state, (Character) value);
            case Int -> pushInt(state, (Integer) value);
            case Float -> pushFloat(state, (Float) value);
            case Long -> pushLong(state, (Long) value);
            case Double -> pushDouble(state, (Double) value);
            case Object -> pushObject(state, value);
            default -> throw InterpreterUtil.shouldNotReachHere("%s", kind);
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushReturnAddress(InterpreterState state, int targetBCI) {
        materialize(state);
        state.putReturnAddress(top, targetBCI);
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    int popInt(InterpreterState state) {
        if (tosLevel == 0) {
            int value = state.popInt(top, -1);
            top--;
            return value;
        } else if (tosLevel == 1) {
            tosLevel = 0;
            return GraalDirectives.assumeInt(tosPrimitive0);
        } else {
            tosLevel = 1;
            return GraalDirectives.assumeInt(tosPrimitive1);
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    float popFloat(InterpreterState state) {
        if (tosLevel == 0) {
            float value = state.popFloat(top, -1);
            top--;
            return value;
        } else if (tosLevel == 1) {
            tosLevel = 0;
            return GraalDirectives.assumeFloat(tosPrimitive0);
        } else {
            tosLevel = 1;
            return GraalDirectives.assumeFloat(tosPrimitive1);
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    long popLong(InterpreterState state) {
        if (tosLevel == 0) {
            long value = state.popLong(top, -1);
            top -= 2;
            return value;
        } else if (tosLevel == 1) {
            top--;
            tosLevel = 0;
            return tosPrimitive0;
        } else {
            tosLevel = 0;
            return tosPrimitive1;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    double popDouble(InterpreterState state) {
        return Double.longBitsToDouble(popLong(state));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    Object popObject(InterpreterState state) {
        if (tosLevel == 0) {
            Object value = state.popObject(top, -1);
            top--;
            return value;
        } else {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    private Object popKind(InterpreterState state, int basicType) {
        return switch (basicType) {
            case T_BOOLEAN -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                boolean value = (popInt(state) & 1) != 0;
                anchorUpdatedValues();
                yield value;
            }
            case T_BYTE -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                byte value = (byte) popInt(state);
                anchorUpdatedValues();
                yield value;
            }
            case T_SHORT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                short value = (short) popInt(state);
                anchorUpdatedValues();
                yield value;
            }
            case T_CHAR -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                char value = (char) popInt(state);
                anchorUpdatedValues();
                yield value;
            }
            case T_INT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                int value = popInt(state);
                anchorUpdatedValues();
                yield value;
            }
            case T_FLOAT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                float value = popFloat(state);
                anchorUpdatedValues();
                yield value;
            }
            case T_LONG -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                long value = popLong(state);
                anchorUpdatedValues();
                yield value;
            }
            case T_DOUBLE -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                double value = popDouble(state);
                anchorUpdatedValues();
                yield value;
            }
            case T_OBJECT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                Object value = popObject(state);
                anchorUpdatedValues();
                yield value;
            }
            default -> {
                GraalDirectives.injectSwitchCaseProbability(0.0);
                throw InterpreterUtil.shouldNotReachHereAtRuntime();
            }
        };
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pop1(InterpreterState state) {
        pop1(state, true);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pop1(InterpreterState state, boolean clear) {
        if (tosLevel == 0) {
            if (clear) {
                state.clearReference(top, -1);
            }
            top--;
        } else if (tosLevel == 1) {
            tosLevel = 0;
        } else {
            tosLevel = 1;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pop2(InterpreterState state) {
        pop2(state, true);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pop2(InterpreterState state, boolean clear) {
        if (tosLevel == 0) {
            if (clear) {
                state.clearReference(top, -1);
                state.clearReference(top, -2);
            }
            top -= 2;
        } else if (tosLevel == 1) {
            if (clear) {
                state.clearReference(top, -1);
            }
            top--;
            tosLevel = 0;
        } else {
            tosLevel = 0;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    int peekInt(InterpreterState state, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return GraalDirectives.assumeInt(tosPrimitive1);
            }
            if (offset == -2) {
                return GraalDirectives.assumeInt(tosPrimitive0);
            }
        } else if (tosLevel == 1 && offset == -1) {
            return GraalDirectives.assumeInt(tosPrimitive0);
        }
        assert offset < -tosLevel;
        return state.getIntStatic(top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    float peekFloat(InterpreterState state, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return GraalDirectives.assumeFloat(tosPrimitive1);
            }
            if (offset == -2) {
                return GraalDirectives.assumeFloat(tosPrimitive0);
            }
        } else if (tosLevel == 1 && offset == -1) {
            return GraalDirectives.assumeFloat(tosPrimitive0);
        }
        assert offset < -tosLevel;
        return state.getFloatStatic(top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    long peekLong(InterpreterState state, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return tosPrimitive1;
            }
            if (offset == -2) {
                return tosPrimitive0;
            }
        } else if (tosLevel == 1 && offset == -1) {
            return tosPrimitive0;
        }
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.popLong(top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    double peekDouble(InterpreterState state, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return Double.longBitsToDouble(tosPrimitive1);
            }
            if (offset == -2) {
                return Double.longBitsToDouble(tosPrimitive0);
            }
        } else if (tosLevel == 1 && offset == -1) {
            return Double.longBitsToDouble(tosPrimitive0);
        }
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.popDouble(top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    Object peekObject(InterpreterState state, long offset) {
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.peekObject(top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup1(InterpreterState state) {
        if (tosLevel == 0) {
            copySlot(state, -1, 0);
            top++;
        } else if (tosLevel == 1) {
            tosPrimitive1 = tosPrimitive0;
            tosLevel = 2;
        } else {
            fillSlot(state, 0, tosPrimitive0);
            tosPrimitive0 = tosPrimitive1;
            top++;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dupx1(InterpreterState state) {
        if (tosLevel == 0) {
            copySlot(state, -1, 0);
            copySlot(state, -2, -1);
            copySlot(state, 0, -2);
        } else if (tosLevel == 1) {
            copySlot(state, -1, 0);
            overwriteSlot(state, -1, tosPrimitive0);
        } else {
            fillSlot(state, 0, tosPrimitive1);
        }
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dupx2(InterpreterState state) {
        if (tosLevel == 0) {
            copySlot(state, -1, 0);
            copySlot(state, -2, -1);
            copySlot(state, -3, -2);
            copySlot(state, 0, -3);
        } else if (tosLevel == 1) {
            copySlot(state, -1, 0);
            copySlot(state, -2, -1);
            overwriteSlot(state, -2, tosPrimitive0);
        } else {
            copySlot(state, -1, 0);
            overwriteSlot(state, -1, tosPrimitive1);
        }
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2(InterpreterState state) {
        if (tosLevel == 0) {
            copySlot(state, -2, 0);
            copySlot(state, -1, 1);
        } else if (tosLevel == 1) {
            fillSlot(state, 0, tosPrimitive0);
            copySlot(state, -1, 1);
        } else {
            fillSlot(state, 0, tosPrimitive0);
            fillSlot(state, 1, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2x1(InterpreterState state) {
        if (tosLevel == 0) {
            copySlot(state, -2, 0);
            copySlot(state, -1, 1);
            copySlot(state, -3, -1);
            copySlot(state, 0, -3);
            copySlot(state, 1, -2);
        } else if (tosLevel == 1) {
            copySlot(state, -1, 1);
            copySlot(state, -2, 0);
            copySlot(state, -1, -2);
            overwriteSlot(state, -1, tosPrimitive0);
        } else {
            copySlot(state, -1, 1);
            overwriteSlot(state, -1, tosPrimitive0);
            overwriteSlot(state, 0, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2x2(InterpreterState state) {
        if (tosLevel == 0) {
            copySlot(state, -1, 1);
            copySlot(state, -2, 0);
            copySlot(state, -3, -1);
            copySlot(state, -4, -2);
            copySlot(state, 0, -4);
            copySlot(state, 1, -3);
        } else if (tosLevel == 1) {
            copySlot(state, -1, 1);
            copySlot(state, -2, 0);
            copySlot(state, -3, -1);
            copySlot(state, 1, -3);
            overwriteSlot(state, -2, tosPrimitive0);
        } else {
            copySlot(state, -1, 1);
            copySlot(state, -2, 0);
            overwriteSlot(state, -2, tosPrimitive0);
            overwriteSlot(state, -1, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void swap(InterpreterState state) {
        if (tosLevel == 0) {
            swapSlot(state, -1, -2);
        } else if (tosLevel == 1) {
            copySlot(state, -1, 0);
            overwriteSlot(state, -1, tosPrimitive0);
            top++;
            tosLevel = 0;
        } else {
            long tmp = tosPrimitive0;
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = tmp;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void copySlot(InterpreterState state, long srcOffset, long dstOffset) {
        state.copyStatic(top, srcOffset, top, dstOffset);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void swapSlot(InterpreterState state, long srcOffset, long dstOffset) {
        state.swapStatic(top, srcOffset, top, dstOffset);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void fillSlot(InterpreterState state, long offset, long value) {
        state.setLongStatic(top, offset, value);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void overwriteSlot(InterpreterState state, long offset, long value) {
        state.clearReference(top, offset);
        fillSlot(state, offset, value);
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    void popArguments(InterpreterState state, int[] argKinds, Object[] args, Object appendix) {
        long index = args.length - 1;
        if (appendix != null) {
            assert uncheckedBasicTypeAt(argKinds, index) == T_OBJECT;
            uncheckedPutArgument(args, index, appendix);
            index--;
        }
        if (tosLevel > 0 && index >= 0) {
            uncheckedPutArgument(args, index, popKind(state, uncheckedBasicTypeAt(argKinds, index)));
            index--;
        }
        if (tosLevel > 0 && index >= 0) {
            uncheckedPutArgument(args, index, popKind(state, uncheckedBasicTypeAt(argKinds, index)));
            index--;
        }
        materialize(state);

        /*
         * Keep the branchy argument-pop loop colder than its entry. A higher loop weight makes
         * linear scan introduce fast-path spills around the loop. If this probability changes,
         * recheck the loop entry and backedge in invokestaticHandler0.
         */
        for (; GraalDirectives.injectBranchProbability(0.3, index >= 0); index--) {
            uncheckedPutArgument(args, index, popKind(state, uncheckedBasicTypeAt(argKinds, index)));
        }
        discardCachedValues();
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    private static int uncheckedBasicTypeAt(int[] argKinds, long index) {
        return UNSAFE.getInt(argKinds, Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE);
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    private static void uncheckedPutArgument(Object[] args, long index, Object value) {
        UNSAFE.putReference(args, Unsafe.ARRAY_OBJECT_BASE_OFFSET + index * Unsafe.ARRAY_OBJECT_INDEX_SCALE, value);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void discardCachedValues() {
        tosLevel = 0;
    }
}
