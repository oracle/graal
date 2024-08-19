/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Operation parameter that allows an operation to update a contiguous range of locals. This class
 * is intended to be used in combination with the {@link ConstantOperand} annotation.
 * <p>
 * When a local setter range is declared as a constant operand, the corresponding builder method
 * will take a {@link BytecodeLocal} array argument representing the locals to be updated.
 *
 * @since 24.2
 */
public final class LocalSetterRange {

    private final int start;
    private final int length;

    private LocalSetterRange(int start, int length) {
        this.start = start;
        this.length = length;
    }

    /**
     * Returns the length of the range.
     *
     * @since 24.2
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns a string representation of a {@link LocalSetterRange}.
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        if (length == 0) {
            return "LocalSetterRange[]";
        }
        return String.format("LocalSetterRange[%d...%d]", start, start + length - 1);
    }

    private void checkBounds(int offset) {
        if (offset >= length) {
            CompilerDirectives.transferToInterpreter();
            throw new ArrayIndexOutOfBoundsException(offset);
        }
    }

    /**
     * Stores an object into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setObject(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, Object value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValue(bci, frame, start + offset, value);
    }

    /**
     * Stores an int into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setInt(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, int value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValueInt(bci, frame, start + offset, value);
    }

    /**
     * Stores a long into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setLong(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, long value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValueLong(bci, frame, start + offset, value);
    }

    /**
     * Stores a short into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setShort(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, short value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValueShort(bci, frame, start + offset, value);
    }

    /**
     * Stores a boolean into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setBoolean(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, boolean value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValueBoolean(bci, frame, start + offset, value);
    }

    /**
     * Stores a byte into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setByte(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, byte value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValueByte(bci, frame, start + offset, value);
    }

    /**
     * Stores a float into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setFloat(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, float value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValueFloat(bci, frame, start + offset, value);
    }

    /**
     * Stores a double into the local at the given offset into the range.
     *
     * @since 24.2
     */
    public void setDouble(BytecodeNode bytecode, int bci, VirtualFrame frame, int offset, double value) {
        CompilerAsserts.partialEvaluationConstant(this);
        checkBounds(offset);
        bytecode.setLocalValueDouble(bci, frame, start + offset, value);
    }

    private static final int CACHE_MAX_START = 32;
    private static final int CACHE_MAX_LENGTH = 16;

    @CompilationFinal(dimensions = 2) private static final LocalSetterRange[][] CACHE = createArray();

    private static LocalSetterRange[][] createArray() {
        LocalSetterRange[][] array = new LocalSetterRange[CACHE_MAX_LENGTH][CACHE_MAX_START];
        for (int length = 0; length < CACHE_MAX_LENGTH; length++) {
            for (int start = 0; start < CACHE_MAX_START; start++) {
                array[length][start] = new LocalSetterRange(start, length);
            }
        }
        return array;
    }

    /**
     * Creates a local setter range given an array of bytecode locals created by the builder. The
     * array of bytecode locals must locals with consective localOffset. The returned value may
     * return an interned instance of {@link LocalSetterRange} to improve memory footprint.
     *
     * @since 24.2
     */
    public static LocalSetterRange constantOf(BytecodeLocal[] locals) {
        if (locals.length == 0) {
            return CACHE[0][0];
        }
        int start = locals[0].getLocalOffset();
        for (int i = 1; i < locals.length; i++) {
            if (start + i != locals[i].getLocalOffset()) {
                throw new IllegalArgumentException("Invalid locals provided. Only contigous locals must be provided for LocalSetterRange.");
            }
        }
        int length = locals.length;
        if (start < CACHE_MAX_START && length < CACHE_MAX_LENGTH) {
            return CACHE[length][start];
        } else {
            return new LocalSetterRange(start, length);
        }
    }

}
