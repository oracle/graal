/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.collection.IntArrayList;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.profiles.BranchProfile;

public final class WasmCodeEntry {

    private final WasmFunction function;
    @CompilationFinal(dimensions = 1) private final byte[] data;
    @CompilationFinal(dimensions = 1) private final byte[] localTypes;
    @CompilationFinal(dimensions = 1) private int[] intConstants;
    @CompilationFinal(dimensions = 2) private int[][] branchTables;
    @CompilationFinal(dimensions = 1) private int[] profileCounters;
    private final int maxStackSize;
    private final BranchProfile errorBranch = BranchProfile.create();

    public WasmCodeEntry(WasmFunction function, byte[] data, byte[] localTypes, int maxStackSize) {
        this.function = function;
        this.data = data;
        this.localTypes = localTypes;
        this.maxStackSize = maxStackSize;
        this.intConstants = null;
        this.profileCounters = null;
    }

    public WasmFunction function() {
        return function;
    }

    public byte[] data() {
        return data;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public byte localType(int index) {
        return localTypes[index];
    }

    @SuppressWarnings("unused")
    public int intConstant(int index) {
        return intConstants[index];
    }

    public void setIntConstants(int[] intConstants) {
        this.intConstants = intConstants;
    }

    public int[] intConstants() {
        return intConstants;
    }

    public int[] branchTable(int index) {
        return branchTables[index];
    }

    public void setBranchTables(int[][] branchTables) {
        this.branchTables = branchTables;
    }

    public void setProfileCount(int size) {
        if (size > 0) {
            this.profileCounters = new int[size];
        } else {
            this.profileCounters = IntArrayList.EMPTY_INT_ARRAY;
        }
    }

    public int[] profileCounters() {
        return profileCounters;
    }

    public int numLocals() {
        return localTypes.length;
    }

    public int functionIndex() {
        return function.index();
    }

    /**
     * A constant holding the maximum value an {@code int} can have, 2<sup>15</sup>-1. The sum of
     * the true and false count must not overflow. This constant is used to check whether one of the
     * counts does not exceed the required maximum value.
     */
    public static final int CONDITION_COUNT_MAX_VALUE = 0x3fff;

    /**
     * Same logic as in {@link com.oracle.truffle.api.profiles.ConditionProfile#profile}.
     *
     * @param index Condition index
     * @param condition Condition value
     * @return {@code condition}
     */
    public static boolean profileCondition(int[] counters, int index, boolean condition) {
        // locals required to guarantee no overflow in multi-threaded environments
        int tf = counters[index];
        int t = tf >>> 16;
        int f = tf & 0xffff;
        boolean val = condition;
        if (val) {
            if (!CompilerDirectives.inInterpreter()) {
                if (t == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (f == 0) {
                    // Make this branch fold during PE
                    val = true;
                }
            } else {
                if (t < CONDITION_COUNT_MAX_VALUE) {
                    counters[index] = ((t + 1) << 16) | f;
                }
            }
        } else {
            if (!CompilerDirectives.inInterpreter()) {
                if (f == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                if (t == 0) {
                    // Make this branch fold during PE
                    val = false;
                }
            } else {
                if (f < CONDITION_COUNT_MAX_VALUE) {
                    counters[index] = (t << 16) | (f + 1);
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

    public void errorBranch() {
        errorBranch.enter();
    }

    @Override
    public String toString() {
        return "wasm-code-entry:" + functionIndex();
    }
}
