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

    @AlwaysInline("Keep InterpreterVirtualStack virtual-expanded")
    void discardCachedValues() {
        tosLevel = 0;
    }
}
