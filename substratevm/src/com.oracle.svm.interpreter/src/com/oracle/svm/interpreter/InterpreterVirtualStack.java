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
    void materialize(long[] primitives) {
        if (tosLevel == 1) {
            setPrimitive(primitives, top, 0, tosPrimitive0);
            top++;
            tosLevel = 0;
        } else if (tosLevel == 2) {
            setPrimitive(primitives, top, 0, tosPrimitive0);
            setPrimitive(primitives, top + 1, 0, tosPrimitive1);
            top += 2;
            tosLevel = 0;
        }
        killUnusedFields();
    }

    @AlwaysInline("Materialize before an outlined stack operation without passing InterpreterVirtualStack")
    long beginOutlinedCall(long[] primitives) {
        materialize(primitives);
        return top;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void endOutlinedCall(int slotDelta) {
        assert tosLevel == 0;
        top += slotDelta;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushInt(long[] primitives, int value) {
        if (tosLevel == 0) {
            tosPrimitive0 = value;
            tosLevel = 1;
        } else if (tosLevel == 1) {
            tosPrimitive1 = value;
            tosLevel = 2;
        } else {
            setPrimitive(primitives, top, 0, tosPrimitive0);
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = value;
            top++;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushFloat(long[] primitives, float value) {
        pushInt(primitives, Float.floatToRawIntBits(value));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushLong(long[] primitives, long value) {
        if (tosLevel == 0) {
            tosPrimitive0 = GraalDirectives.arbitraryValue(tosPrimitive0);
            tosPrimitive1 = value;
            tosLevel = 2;
        } else if (tosLevel == 1) {
            setPrimitive(primitives, top, 0, tosPrimitive0);
            tosPrimitive1 = value;
            top++;
            tosLevel = 2;
        } else {
            // We cannot distinguish whether we saved one long or two ints.
            setPrimitive(primitives, top, 0, tosPrimitive0);
            setPrimitive(primitives, top + 1, 0, tosPrimitive1);
            tosPrimitive1 = value;
            top += 2;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushDouble(long[] primitives, double value) {
        pushLong(primitives, Double.doubleToRawLongBits(value));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushObject(long[] primitives, Object[] references, Object value) {
        materialize(primitives);
        setReference(references, top, 0, value);
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushReturnAddress(long[] primitives, Object[] references, int targetBCI) {
        materialize(primitives);
        setReference(references, top, 0, ReturnAddress.create(targetBCI));
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    int popInt(long[] primitives) {
        if (tosLevel == 0) {
            int value = (int) getPrimitive(primitives, top, -1);
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
    float popFloat(long[] primitives) {
        if (tosLevel == 0) {
            float value = Float.intBitsToFloat((int) getPrimitive(primitives, top, -1));
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
    long popLong(long[] primitives) {
        if (tosLevel == 0) {
            long value = getPrimitive(primitives, top, -1);
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
    double popDouble(long[] primitives) {
        return Double.longBitsToDouble(popLong(primitives));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    Object popObject(Object[] references) {
        if (tosLevel == 0) {
            Object value = getReference(references, top, -1);
            setReference(references, top, -1, null);
            top--;
            return value;
        } else {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    private Object popKind(long[] primitives, Object[] references, int basicType) {
        return switch (basicType) {
            case T_BOOLEAN -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                boolean value = (popInt(primitives) & 1) != 0;
                anchorUpdatedValues();
                yield value;
            }
            case T_BYTE -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                byte value = (byte) popInt(primitives);
                anchorUpdatedValues();
                yield value;
            }
            case T_SHORT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                short value = (short) popInt(primitives);
                anchorUpdatedValues();
                yield value;
            }
            case T_CHAR -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                char value = (char) popInt(primitives);
                anchorUpdatedValues();
                yield value;
            }
            case T_INT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                int value = popInt(primitives);
                anchorUpdatedValues();
                yield value;
            }
            case T_FLOAT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                float value = popFloat(primitives);
                anchorUpdatedValues();
                yield value;
            }
            case T_LONG -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                long value = popLong(primitives);
                anchorUpdatedValues();
                yield value;
            }
            case T_DOUBLE -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                double value = popDouble(primitives);
                anchorUpdatedValues();
                yield value;
            }
            case T_OBJECT -> {
                GraalDirectives.injectSwitchCaseProbability(1.0 / 9.0);
                avoidHoistingTop();
                Object value = popObject(references);
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
    void pop1(Object[] references) {
        pop1(references, true);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pop1(Object[] references, boolean clear) {
        if (tosLevel == 0) {
            if (clear) {
                setReference(references, top, -1, null);
            }
            top--;
        } else if (tosLevel == 1) {
            tosLevel = 0;
        } else {
            tosLevel = 1;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pop2(Object[] references) {
        pop2(references, true);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pop2(Object[] references, boolean clear) {
        if (tosLevel == 0) {
            if (clear) {
                setReference(references, top, -1, null);
                setReference(references, top, -2, null);
            }
            top -= 2;
        } else if (tosLevel == 1) {
            if (clear) {
                setReference(references, top, -1, null);
            }
            top--;
            tosLevel = 0;
        } else {
            tosLevel = 0;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    int peekInt(long[] primitives, long offset) {
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
        return (int) getPrimitive(primitives, top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    float peekFloat(long[] primitives, long offset) {
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
        return Float.intBitsToFloat((int) getPrimitive(primitives, top, offset + tosLevel));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    long peekLong(long[] primitives, long offset) {
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
        return getPrimitive(primitives, top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    double peekDouble(long[] primitives, long offset) {
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
        return Double.longBitsToDouble(getPrimitive(primitives, top, offset + tosLevel));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    Object peekObject(Object[] references, long offset) {
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return getReference(references, top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup1(long[] primitives, Object[] references) {
        if (tosLevel == 0) {
            copySlot(primitives, references, -1, 0);
            top++;
        } else if (tosLevel == 1) {
            tosPrimitive1 = tosPrimitive0;
            tosLevel = 2;
        } else {
            fillSlot(primitives, 0, tosPrimitive0);
            tosPrimitive0 = tosPrimitive1;
            top++;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dupx1(long[] primitives, Object[] references) {
        if (tosLevel == 0) {
            copySlot(primitives, references, -1, 0);
            copySlot(primitives, references, -2, -1);
            copySlot(primitives, references, 0, -2);
        } else if (tosLevel == 1) {
            copySlot(primitives, references, -1, 0);
            overwriteSlot(primitives, references, -1, tosPrimitive0);
        } else {
            fillSlot(primitives, 0, tosPrimitive1);
        }
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dupx2(long[] primitives, Object[] references) {
        if (tosLevel == 0) {
            copySlot(primitives, references, -1, 0);
            copySlot(primitives, references, -2, -1);
            copySlot(primitives, references, -3, -2);
            copySlot(primitives, references, 0, -3);
        } else if (tosLevel == 1) {
            copySlot(primitives, references, -1, 0);
            copySlot(primitives, references, -2, -1);
            overwriteSlot(primitives, references, -2, tosPrimitive0);
        } else {
            copySlot(primitives, references, -1, 0);
            overwriteSlot(primitives, references, -1, tosPrimitive1);
        }
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2(long[] primitives, Object[] references) {
        if (tosLevel == 0) {
            copySlot(primitives, references, -2, 0);
            copySlot(primitives, references, -1, 1);
        } else if (tosLevel == 1) {
            fillSlot(primitives, 0, tosPrimitive0);
            copySlot(primitives, references, -1, 1);
        } else {
            fillSlot(primitives, 0, tosPrimitive0);
            fillSlot(primitives, 1, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2x1(long[] primitives, Object[] references) {
        if (tosLevel == 0) {
            copySlot(primitives, references, -2, 0);
            copySlot(primitives, references, -1, 1);
            copySlot(primitives, references, -3, -1);
            copySlot(primitives, references, 0, -3);
            copySlot(primitives, references, 1, -2);
        } else if (tosLevel == 1) {
            copySlot(primitives, references, -1, 1);
            copySlot(primitives, references, -2, 0);
            copySlot(primitives, references, -1, -2);
            overwriteSlot(primitives, references, -1, tosPrimitive0);
        } else {
            copySlot(primitives, references, -1, 1);
            overwriteSlot(primitives, references, -1, tosPrimitive0);
            overwriteSlot(primitives, references, 0, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2x2(long[] primitives, Object[] references) {
        if (tosLevel == 0) {
            copySlot(primitives, references, -1, 1);
            copySlot(primitives, references, -2, 0);
            copySlot(primitives, references, -3, -1);
            copySlot(primitives, references, -4, -2);
            copySlot(primitives, references, 0, -4);
            copySlot(primitives, references, 1, -3);
        } else if (tosLevel == 1) {
            copySlot(primitives, references, -1, 1);
            copySlot(primitives, references, -2, 0);
            copySlot(primitives, references, -3, -1);
            copySlot(primitives, references, 1, -3);
            overwriteSlot(primitives, references, -2, tosPrimitive0);
        } else {
            copySlot(primitives, references, -1, 1);
            copySlot(primitives, references, -2, 0);
            overwriteSlot(primitives, references, -2, tosPrimitive0);
            overwriteSlot(primitives, references, -1, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void swap(long[] primitives, Object[] references) {
        if (tosLevel == 0) {
            swapSlot(primitives, references, -1, -2);
        } else if (tosLevel == 1) {
            copySlot(primitives, references, -1, 0);
            overwriteSlot(primitives, references, -1, tosPrimitive0);
            top++;
            tosLevel = 0;
        } else {
            long tmp = tosPrimitive0;
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = tmp;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void copySlot(long[] primitives, Object[] references, long srcOffset, long dstOffset) {
        setPrimitive(primitives, top, dstOffset, getPrimitive(primitives, top, srcOffset));
        setReference(references, top, dstOffset, getReference(references, top, srcOffset));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void swapSlot(long[] primitives, Object[] references, long srcOffset, long dstOffset) {
        long primitive = getPrimitive(primitives, top, srcOffset);
        setPrimitive(primitives, top, srcOffset, getPrimitive(primitives, top, dstOffset));
        setPrimitive(primitives, top, dstOffset, primitive);

        Object reference = getReference(references, top, srcOffset);
        setReference(references, top, srcOffset, getReference(references, top, dstOffset));
        setReference(references, top, dstOffset, reference);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void fillSlot(long[] primitives, long offset, long value) {
        setPrimitive(primitives, top, offset, value);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void overwriteSlot(long[] primitives, Object[] references, long offset, long value) {
        setReference(references, top, offset, null);
        fillSlot(primitives, offset, value);
    }

    @AlwaysInline("Keep operand-stack access in bytecode-handler stubs")
    private static long getPrimitive(long[] primitives, long slot, long slotOffset) {
        return UNSAFE.getLong(primitives, primitiveOffset(slot, slotOffset));
    }

    @AlwaysInline("Keep operand-stack access in bytecode-handler stubs")
    private static void setPrimitive(long[] primitives, long slot, long slotOffset, long value) {
        UNSAFE.putLong(primitives, primitiveOffset(slot, slotOffset), value);
    }

    @AlwaysInline("Keep operand-stack access in bytecode-handler stubs")
    private static Object getReference(Object[] references, long slot, long slotOffset) {
        return UNSAFE.getReference(references, referenceOffset(slot, slotOffset));
    }

    @AlwaysInline("Keep operand-stack access in bytecode-handler stubs")
    private static void setReference(Object[] references, long slot, long slotOffset, Object value) {
        UNSAFE.putReference(references, referenceOffset(slot, slotOffset), value);
    }

    @AlwaysInline("Keep operand-stack access in bytecode-handler stubs")
    private static long primitiveOffset(long slot, long slotOffset) {
        return Unsafe.ARRAY_LONG_BASE_OFFSET + ((slot + slotOffset) * Unsafe.ARRAY_LONG_INDEX_SCALE);
    }

    @AlwaysInline("Keep operand-stack access in bytecode-handler stubs")
    private static long referenceOffset(long slot, long slotOffset) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + ((slot + slotOffset) * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    void popArguments(long[] primitives, Object[] references, int[] argKinds, Object[] args, Object appendix) {
        long index = args.length - 1;
        if (appendix != null) {
            assert uncheckedBasicTypeAt(argKinds, index) == T_OBJECT;
            uncheckedPutArgument(args, index, appendix);
            index--;
        }
        if (tosLevel > 0 && index >= 0) {
            uncheckedPutArgument(args, index, popKind(primitives, references, uncheckedBasicTypeAt(argKinds, index)));
            index--;
        }
        if (tosLevel > 0 && index >= 0) {
            uncheckedPutArgument(args, index, popKind(primitives, references, uncheckedBasicTypeAt(argKinds, index)));
            index--;
        }
        materialize(primitives);

        /*
         * Keep the branchy argument-pop loop colder than its entry. A higher loop weight makes
         * linear scan introduce fast-path spills around the loop. If this probability changes,
         * recheck the loop entry and backedge in invokestaticHandler0.
         */
        for (; GraalDirectives.injectBranchProbability(0.3, index >= 0); index--) {
            uncheckedPutArgument(args, index, popKind(primitives, references, uncheckedBasicTypeAt(argKinds, index)));
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
