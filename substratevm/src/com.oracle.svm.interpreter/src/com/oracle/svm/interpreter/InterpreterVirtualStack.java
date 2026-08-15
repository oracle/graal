/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Owns the materialized operand-stack pointer and caches up to two primitive operand-stack
 * slots. A category-2 value occupies both slots, with its payload in
 * {@link #tosPrimitive1}; {@link #tosPrimitive0} is still materialized because a level of
 * two can also represent two category-1 values. Each stack operation updates {@link #top}
 * while compensating for changes in {@link #tosLevel}, so the materialized stack pointer
 * changes only when values enter or leave memory.
 */
final class InterpreterVirtualStack {
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
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
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
    int peekIntAtOffset(InterpreterState state, long offset) {
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
    Object peekObjectAtOffset(InterpreterState state, long offset) {
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.peekObject(top, offset + tosLevel);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    float peekFloat(long sp, InterpreterState state, long offset) {
        return GraalDirectives.assumeFloat(peekPrimitive(sp, state, offset));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    long peekLong(long sp, InterpreterState state, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return tosPrimitive1;
            }
            if (offset == -2) {
                return tosPrimitive0;
            }
        }
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.popLong(sp + tosLevel, offset);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    double peekDouble(long sp, InterpreterState state, long offset) {
        if (tosLevel == 2) {
            if (offset == -1) {
                return Double.longBitsToDouble(tosPrimitive1);
            }
            if (offset == -2) {
                return Double.longBitsToDouble(tosPrimitive0);
            }
        }
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.popDouble(sp + tosLevel, offset);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    Object peekObject(long sp, InterpreterState state, long depth) {
        if (tosLevel != 0) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.peekObject(sp, -1 - depth);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    Object peekObjectAtOffset(long sp, InterpreterState state, long offset) {
        if (offset >= -tosLevel) {
            throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
        return state.peekObject(sp + tosLevel, offset);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void pushReturnAddress(InterpreterState state, int targetBCI) {
        materialize(state);
        top++;
        state.putReturnAddress(top, -1, targetBCI);
    }

    @AlwaysInline("Materialize before an outlined stack operation without passing InterpreterVirtualStack")
    long materializedTop(InterpreterState state) {
        materialize(state);
        return top;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void adjustTop(int slotDelta) {
        assert tosLevel == 0;
        top += slotDelta;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void replaceTopWithInt(int consumedSlots, int value) {
        replaceTopWithPrimitive(consumedSlots, value);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void replaceTopWithFloat(int consumedSlots, float value) {
        replaceTopWithPrimitive(consumedSlots, Float.floatToRawIntBits(value));
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void replaceTopWithLong(InterpreterState state, int consumedSlots, long value) {
        materializeSurvivors(state, consumedSlots);
        pushLong(state, value);
        top -= consumedSlots;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void replaceTopWithDouble(InterpreterState state, int consumedSlots, double value) {
        materializeSurvivors(state, consumedSlots);
        pushLong(state, Double.doubleToRawLongBits(value));
        top -= consumedSlots;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void replaceTopWithObject(InterpreterState state, int consumedSlots, Object value) {
        long logicalTop = top + tosLevel;
        materializeSurvivors(state, consumedSlots);
        state.putObject(logicalTop - consumedSlots, value);
        top += 1 - consumedSlots;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void discardCachedValues() {
        tosLevel = 0;
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
    void clear() {
        top += tosLevel;
        tosLevel = 0;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void popPrimitive1() {
        int oldTosLevel = tosLevel;
        if (tosLevel == 2) {
            tosLevel = 1;
        } else if (tosLevel == 1) {
            tosLevel = 0;
        }
        top += oldTosLevel - tosLevel - 1;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void popPrimitive2() {
        int oldTosLevel = tosLevel;
        tosLevel = 0;
        top += oldTosLevel - tosLevel - 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup1(InterpreterState state) {
        if (tosLevel == 0) {
            state.dup1(top);
            top++;
        } else if (tosLevel == 1) {
            tosPrimitive1 = tosPrimitive0;
            tosLevel = 2;
        } else {
            state.setLongStatic(top, tosPrimitive0);
            tosPrimitive0 = tosPrimitive1;
            top++;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dupx1(InterpreterState state) {
        if (tosLevel == 0) {
            state.dupx1(top);
        } else if (tosLevel == 1) {
            state.copyStatic(top, -1, top, 0);
            state.setLongStatic(top, -1, tosPrimitive0);
            state.clearReference(top, -1);
        } else {
            state.setLongStatic(top, tosPrimitive1);
        }
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dupx2(InterpreterState state) {
        if (tosLevel == 0) {
            state.dupx2(top);
        } else if (tosLevel == 1) {
            state.copyStatic(top, -1, top, 0);
            state.copyStatic(top, -2, top, -1);
            putCachedPrimitive(state, top, -2, tosPrimitive0);
        } else {
            state.copyStatic(top, -1, top, 0);
            putCachedPrimitive(state, top, -1, tosPrimitive1);
        }
        top++;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2(InterpreterState state) {
        if (tosLevel == 0) {
            state.dup2(top);
        } else if (tosLevel == 1) {
            putCachedPrimitive(state, top, 0, tosPrimitive0);
            state.copyStatic(top, -1, top, 1);
        } else {
            putCachedPrimitive(state, top, 0, tosPrimitive0);
            putCachedPrimitive(state, top, 1, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2x1(InterpreterState state) {
        if (tosLevel == 0) {
            state.dup2x1(top);
        } else if (tosLevel == 1) {
            state.copyStatic(top, -1, top, 1);
            state.copyStatic(top, -2, top, 0);
            state.copyStatic(top, -1, top, -2);
            putCachedPrimitive(state, top, -1, tosPrimitive0);
        } else {
            state.copyStatic(top, -1, top, 1);
            putCachedPrimitive(state, top, -1, tosPrimitive0);
            putCachedPrimitive(state, top, 0, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void dup2x2(InterpreterState state) {
        if (tosLevel == 0) {
            state.dup2x2(top);
        } else if (tosLevel == 1) {
            state.copyStatic(top, -1, top, 1);
            state.copyStatic(top, -2, top, 0);
            state.copyStatic(top, -3, top, -1);
            state.copyStatic(top, 1, top, -3);
            putCachedPrimitive(state, top, -2, tosPrimitive0);
        } else {
            state.copyStatic(top, -1, top, 1);
            state.copyStatic(top, -2, top, 0);
            putCachedPrimitive(state, top, -2, tosPrimitive0);
            putCachedPrimitive(state, top, -1, tosPrimitive1);
        }
        top += 2;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void swap(InterpreterState state) {
        if (tosLevel == 0) {
            state.swapSingle(top);
        } else if (tosLevel == 1) {
            state.copyStatic(top, -1, top, 0);
            state.setLongStatic(top, -1, tosPrimitive0);
            state.clearReference(top, -1);
            top++;
            tosLevel = 0;
        } else {
            long tmp = tosPrimitive0;
            tosPrimitive0 = tosPrimitive1;
            tosPrimitive1 = tmp;
        }
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private static void putCachedPrimitive(InterpreterState state, long sp, long offset, long value) {
        state.clearReference(sp, offset);
        state.setLongStatic(sp, offset, value);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private long peekPrimitive(long sp, InterpreterState state, long offset) {
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
        assert offset < -tosLevel;
        return state.peekPrimitive(sp + tosLevel, offset);
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void replaceTopWithPrimitive(int consumedSlots, long value) {
        int oldTosLevel = tosLevel;
        int survivors = Math.max(0, tosLevel - consumedSlots);
        assert survivors <= 1;
        if (survivors == 0) {
            tosPrimitive0 = value;
            tosLevel = 1;
        } else {
            tosPrimitive1 = value;
            tosLevel = 2;
        }
        top += oldTosLevel - tosLevel + 1 - consumedSlots;
    }

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    private void materializeSurvivors(InterpreterState state, int consumedSlots) {
        int oldTosLevel = tosLevel;
        long logicalTop = top + tosLevel;
        int survivors = Math.max(0, tosLevel - consumedSlots);
        if (survivors == 2) {
            state.setLongStatic(logicalTop - 2, tosPrimitive0);
            state.setLongStatic(logicalTop - 1, tosPrimitive1);
        } else if (survivors == 1) {
            state.setLongStatic(logicalTop - tosLevel, tosPrimitive0);
        }
        tosLevel = 0;
        top += oldTosLevel;
    }
}
