/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Operation parameter that allows an operation to get, set, or clear locals declared in a
 * contiguous range.
 * <p>
 * To use a local range accessor, declare a {@link ConstantOperand} on the operation. The
 * corresponding builder method for the operation will take a {@link BytecodeLocal} array argument
 * for the locals to be accessed. These locals must be allocated sequentially during building. At
 * run time, a {@link LocalRangeAccessor} for the locals will be supplied as a parameter to the
 * operation.
 * <p>
 * All of the accessor methods take a {@link BytecodeNode}, the current {@link Frame}, and an
 * offset. The offset should be a valid compilation-final index into the array of locals. See the
 * {@link LocalAccessor} javadoc for restrictions on the other parameters and usage recommendations.
 *
 * @since 24.2
 */
public final class LocalRangeAccessor {

    private final int startOffset;
    private final int startIndex;
    private final int length;

    private LocalRangeAccessor(int startOffset, int startIndex, int length) {
        this.startOffset = startOffset;
        this.startIndex = startIndex;
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

    @Override
    public int hashCode() {
        return Objects.hash(length, startIndex, startOffset);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        } else {
            LocalRangeAccessor other = (LocalRangeAccessor) obj;
            return length == other.length && startIndex == other.startIndex && startOffset == other.startOffset;
        }
    }

    /**
     * Returns a string representation of a {@link LocalRangeAccessor}.
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        if (length == 0) {
            return "LocalRangeAccessor[]";
        }
        return String.format("LocalRangeAccessor[%d...%d]", startOffset, startOffset + length - 1);
    }

    /**
     * Loads an object from the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public Object getObject(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.getLocalValueInternal(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Loads a boolean from the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public boolean getBoolean(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.getLocalValueInternalBoolean(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Loads a byte from the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public byte getByte(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.getLocalValueInternalByte(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Loads an int from the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public int getInt(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.getLocalValueInternalInt(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Loads a long from the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public long getLong(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.getLocalValueInternalLong(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Loads a float from the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public float getFloat(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.getLocalValueInternalFloat(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Loads a double from the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public double getDouble(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.getLocalValueInternalDouble(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Stores an object into the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public void setObject(BytecodeNode bytecodeNode, VirtualFrame frame, int offset, Object value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.setLocalValueInternal(frame, startOffset + offset, startIndex + offset, value);
    }

    /**
     * Stores a boolean into the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public void setBoolean(BytecodeNode bytecodeNode, VirtualFrame frame, int offset, boolean value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.setLocalValueInternalBoolean(frame, startOffset + offset, startIndex + offset, value);
    }

    /**
     * Stores a byte into the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public void setByte(BytecodeNode bytecodeNode, VirtualFrame frame, int offset, byte value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.setLocalValueInternalByte(frame, startOffset + offset, startIndex + offset, value);
    }

    /**
     * Stores an int into the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public void setInt(BytecodeNode bytecodeNode, VirtualFrame frame, int offset, int value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.setLocalValueInternalInt(frame, startOffset + offset, startIndex + offset, value);
    }

    /**
     * Stores a long into the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public void setLong(BytecodeNode bytecodeNode, VirtualFrame frame, int offset, long value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.setLocalValueInternalLong(frame, startOffset + offset, startIndex + offset, value);
    }

    /**
     * Stores a float into the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public void setFloat(BytecodeNode bytecodeNode, VirtualFrame frame, int offset, float value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.setLocalValueInternalFloat(frame, startOffset + offset, startIndex + offset, value);
    }

    /**
     * Stores a double into the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     */
    public void setDouble(BytecodeNode bytecodeNode, VirtualFrame frame, int offset, double value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.setLocalValueInternalDouble(frame, startOffset + offset, startIndex + offset, value);
    }

    /**
     * Clears the local at the given offset into the range.
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     * @see LocalAccessor#clear
     */
    public void clear(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        bytecodeNode.clearLocalValueInternal(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Checks whether the local at the given offset into the range has been {@link #clear cleared}
     * (and a new value has not been set).
     *
     * @param offset a partial evaluation constant offset into the range.
     * @since 24.2
     * @see LocalAccessor#isCleared
     */
    public boolean isCleared(BytecodeNode bytecodeNode, VirtualFrame frame, int offset) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        checkBounds(offset);
        return bytecodeNode.isLocalClearedInternal(frame, startOffset + offset, startIndex + offset);
    }

    /**
     * Returns the name associated with the local.
     *
     * @since 24.2
     * @see BytecodeNode#getLocalName(int, int)
     */
    public Object getLocalName(BytecodeNode bytecodeNode, int offset) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        return bytecodeNode.getLocalNameInternal(startOffset + offset, startIndex + offset);
    }

    /**
     * Returns the info associated with the local.
     *
     * @since 24.2
     * @see BytecodeNode#getLocalInfo(int, int)
     */
    public Object getLocalInfo(BytecodeNode bytecodeNode, int offset) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        CompilerAsserts.partialEvaluationConstant(offset);
        return bytecodeNode.getLocalInfoInternal(startOffset + offset, startIndex + offset);
    }

    private void checkBounds(int offset) {
        if (offset < 0 || offset >= length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new ArrayIndexOutOfBoundsException(offset);
        }
    }

    private static final int CACHE_MAX_START = 32;
    private static final int CACHE_MAX_LENGTH = 16;

    @CompilationFinal(dimensions = 2) private static final LocalRangeAccessor[][] CACHE = createArray();

    private static LocalRangeAccessor[][] createArray() {
        LocalRangeAccessor[][] array = new LocalRangeAccessor[CACHE_MAX_LENGTH][CACHE_MAX_START];
        for (int length = 0; length < CACHE_MAX_LENGTH; length++) {
            for (int start = 0; start < CACHE_MAX_START; start++) {
                array[length][start] = new LocalRangeAccessor(start, start, length);
            }
        }
        return array;
    }

    /**
     * Obtains a {@link LocalRangeAccessor}.
     *
     * This method is invoked by the generated code and should not be called directly.
     *
     * @since 24.2
     */
    public static LocalRangeAccessor constantOf(BytecodeLocal[] locals) {
        if (locals.length == 0) {
            return CACHE[0][0];
        }
        int startOffset = locals[0].getLocalOffset();
        int startIndex = locals[0].getLocalIndex();
        for (int i = 1; i < locals.length; i++) {
            if (startOffset + i != locals[i].getLocalOffset() || startIndex + i != locals[i].getLocalIndex()) {
                throw new IllegalArgumentException("Invalid locals provided. Only contiguous locals must be provided for LocalRangeAccessor.");
            }
        }
        int length = locals.length;
        if (startIndex == startOffset && startOffset < CACHE_MAX_START && length < CACHE_MAX_LENGTH) {
            return CACHE[length][startOffset];
        } else {
            return new LocalRangeAccessor(startOffset, startIndex, length);
        }
    }

}
