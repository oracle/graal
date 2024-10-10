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

import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Operation parameter that allows an operation to get and set the value of a local. This class is
 * intended to be used in combination with the {@link ConstantOperand} annotation.
 * <p>
 * When a local accessor is declared as a constant operand, the corresponding builder method will
 * take a {@link BytecodeLocal} argument representing the local to be updated. Whenever possible
 * using {@link LocalAccessor} should be preferred over
 * {@link BytecodeNode#getLocalValue(int, Frame, int)} and builtin operations should be preferred
 * over using {@link LocalAccessor}.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;Operation
 * &#64;ConstantOperand(type = LocalAccessor.class)
 * public static final class GetLocalAccessor {
 *     &#64;Specialization
 *     public static Object perform(VirtualFrame frame, LocalAccessor accessor,
 *                     @Bind BytecodeNode node) {
 *         return accessor.getObject(node, frame);
 *     }
 * }
 * </pre>
 *
 * @since 24.2
 */
public final class LocalAccessor {

    private final int localOffset;
    private final int localIndex;

    private LocalAccessor(int localOffset, int localIndex) {
        this.localOffset = localOffset;
        this.localIndex = localIndex;
    }

    /**
     * Returns a string representation of a {@link LocalAccessor}.
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        return String.format("LocalAccessor[localOffset=%d, localIndex=%d]", localOffset, localIndex);
    }

    /**
     * Stores an object into the local.
     *
     * @since 24.2
     */
    public void setObject(BytecodeNode node, VirtualFrame frame, Object value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        node.setLocalValueInternal(frame, localOffset, localIndex, value);
    }

    /**
     * Stores an int into the local.
     *
     * @since 24.2
     */
    public void setInt(BytecodeNode node, VirtualFrame frame, int value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        node.setLocalValueInternalInt(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a long into the local.
     *
     * @since 24.2
     */
    public void setLong(BytecodeNode node, VirtualFrame frame, long value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        node.setLocalValueInternalLong(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a short into the local.
     *
     * @since 24.2
     */
    public void setBoolean(BytecodeNode node, VirtualFrame frame, boolean value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        node.setLocalValueInternalBoolean(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a short into the local.
     *
     * @since 24.2
     */
    public void setByte(BytecodeNode node, VirtualFrame frame, byte value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        node.setLocalValueInternalByte(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a float into the local.
     *
     * @since 24.2
     */
    public void setFloat(BytecodeNode node, VirtualFrame frame, float value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        node.setLocalValueInternalFloat(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a double into the local.
     *
     * @since 24.2
     */
    public void setDouble(BytecodeNode node, VirtualFrame frame, double value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        node.setLocalValueInternalDouble(frame, localOffset, localIndex, value);
    }

    /**
     * Loads an Object from a local.
     *
     * @since 24.2
     */
    public Object getObject(BytecodeNode node, VirtualFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        return node.getLocalValueInternal(frame, localOffset, localIndex);
    }

    /**
     * Loads a boolean from a local.
     *
     * @since 24.2
     */
    public boolean getBoolean(BytecodeNode node, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        return node.getLocalValueInternalBoolean(frame, localOffset, localIndex);
    }

    /**
     * Loads a byte from a local.
     *
     * @since 24.2
     */
    public byte getByte(BytecodeNode node, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        return node.getLocalValueInternalByte(frame, localOffset, localIndex);
    }

    /**
     * Loads an int from a local.
     *
     * @since 24.2
     */
    public int getInt(BytecodeNode node, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        return node.getLocalValueInternalInt(frame, localOffset, localIndex);
    }

    /**
     * Loads a long from a local.
     *
     * @since 24.2
     */
    public long getLong(BytecodeNode node, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        return node.getLocalValueInternalLong(frame, localOffset, localIndex);
    }

    /**
     * Loads a float from a local.
     *
     * @since 24.2
     */
    public float getFloat(BytecodeNode node, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        return node.getLocalValueInternalFloat(frame, localOffset, localIndex);
    }

    /**
     * Loads a double from a local.
     *
     * @since 24.2
     */
    public double getDouble(BytecodeNode node, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(node);
        return node.getLocalValueInternalDouble(frame, localOffset, localIndex);
    }

    private static final int CACHE_SIZE = 64;

    @CompilationFinal(dimensions = 1) private static final LocalAccessor[] CACHE = createCache();

    private static LocalAccessor[] createCache() {
        LocalAccessor[] setters = new LocalAccessor[64];
        for (int i = 0; i < setters.length; i++) {
            setters[i] = new LocalAccessor(i, i);
        }
        return setters;
    }

    /**
     * Obtains an existing {@link LocalAccessor}.
     *
     * This method is invoked by the generated code and should not be called directly.
     *
     * @since 24.2
     */
    public static LocalAccessor constantOf(BytecodeLocal local) {
        int offset = local.getLocalOffset();
        int index = local.getLocalIndex();
        assert offset <= index;
        if (index == offset && offset < CACHE_SIZE) {
            return CACHE[offset];
        }
        return new LocalAccessor(offset, index);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalAccessor otherSetter && this.localOffset == otherSetter.localOffset && this.localIndex == otherSetter.localIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(localOffset, localIndex);
    }

}
