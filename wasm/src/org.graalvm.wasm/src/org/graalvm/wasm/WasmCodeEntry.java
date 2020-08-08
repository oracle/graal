/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

public final class WasmCodeEntry {
    private final WasmFunction function;
    @CompilationFinal(dimensions = 1) private final byte[] data;
    @CompilationFinal(dimensions = 1) private FrameSlot[] localSlots;
    @CompilationFinal(dimensions = 1) private FrameSlot[] stackSlots;
    @CompilationFinal(dimensions = 1) private byte[] localTypes;
    @CompilationFinal(dimensions = 1) private byte[] byteConstants;
    @CompilationFinal(dimensions = 1) private int[] intConstants;
    @CompilationFinal(dimensions = 1) private long[] longConstants;
    @CompilationFinal(dimensions = 2) private int[][] branchTables;
    @CompilationFinal(dimensions = 1) private int[] profileCounters;

    public WasmCodeEntry(WasmFunction function, byte[] data) {
        this.function = function;
        this.data = data;
        this.localSlots = null;
        this.stackSlots = null;
        this.localTypes = null;
        this.byteConstants = null;
        this.intConstants = null;
        this.longConstants = null;
        this.profileCounters = null;
    }

    public WasmFunction function() {
        return function;
    }

    public byte[] data() {
        return data;
    }

    public FrameSlot localSlot(int index) {
        return localSlots[index];
    }

    public FrameSlot stackSlot(int index) {
        return stackSlots[index];
    }

    public void initLocalSlots(FrameDescriptor frameDescriptor) {
        localSlots = new FrameSlot[localTypes.length];
        for (int i = 0; i != localTypes.length; ++i) {
            FrameSlot localSlot = frameDescriptor.addFrameSlot(i, frameSlotKind(localTypes[i]));
            localSlots[i] = localSlot;
        }
    }

    private static FrameSlotKind frameSlotKind(byte valueType) {
        switch (valueType) {
            case ValueTypes.I32_TYPE:
                return FrameSlotKind.Int;
            case ValueTypes.I64_TYPE:
                return FrameSlotKind.Long;
            case ValueTypes.F32_TYPE:
                return FrameSlotKind.Float;
            case ValueTypes.F64_TYPE:
                return FrameSlotKind.Double;
            default:
                Assert.fail(String.format("Unknown value type: 0x%02X", valueType));
        }
        return null;
    }

    public void initStackSlots(FrameDescriptor frameDescriptor, int maxStackSize) {
        stackSlots = new FrameSlot[maxStackSize];
        for (int i = 0; i != maxStackSize; ++i) {
            FrameSlot stackSlot = frameDescriptor.addFrameSlot(localSlots.length + i, FrameSlotKind.Long);
            stackSlots[i] = stackSlot;
        }
    }

    public void setLocalTypes(byte[] localTypes) {
        this.localTypes = localTypes;
    }

    public byte localType(int index) {
        return localTypes[index];
    }

    public byte byteConstant(int index) {
        return byteConstants[index];
    }

    public void setByteConstants(byte[] byteConstants) {
        this.byteConstants = byteConstants;
    }

    public int intConstant(int index) {
        return intConstants[index];
    }

    public void setIntConstants(int[] intConstants) {
        this.intConstants = intConstants;
    }

    public long longConstant(int index) {
        return longConstants[index];
    }

    public void setLongConstants(long[] longConstants) {
        this.longConstants = longConstants;
    }

    public int[] branchTable(int index) {
        return branchTables[index];
    }

    public void setBranchTables(int[][] branchTables) {
        this.branchTables = branchTables;
    }

    public void setProfileCount(int size) {
        if (size > 0) {
            this.profileCounters = new int[size * 2];
        }
    }

    public int numLocals() {
        return localTypes.length;
    }

    public int functionIndex() {
        return function.index();
    }

    /**
     * A constant holding the maximum value an {@code int} can have, 2<sup>30</sup>-1. The sum of
     * the true and false count must not overflow. This constant is used to check whether one of the
     * counts does not exceed the required maximum value.
     */
    public static final int CONDITION_COUNT_MAX_VALUE = 0x3fffffff;

    /**
     * Same logic as in {@link com.oracle.truffle.api.profiles.ConditionProfile#profile}.
     *
     * @param index Condition index
     * @param condition Condition value
     * @return {@code condition}
     */
    public boolean profileCondition(int index, boolean condition) {
        // locals required to guarantee no overflow in multi-threaded environments
        int t = this.profileCounters[index * 2];
        int f = this.profileCounters[index * 2 + 1];
        boolean val = condition;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < CONDITION_COUNT_MAX_VALUE) {
                    ++this.profileCounters[index * 2];
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < CONDITION_COUNT_MAX_VALUE) {
                    ++this.profileCounters[index * 2 + 1];
                }
            }
        }

        if (CompilerDirectives.inInterpreter()) {
            // no branch probability calculation in the interpreter
            return val;
        } else {
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
    }

    @Override
    public String toString() {
        return "wasm-code-entry:" + functionIndex();
    }
}
