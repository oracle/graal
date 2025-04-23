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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Operation parameter that allows an operation to get, set, and clear the value of a local.
 * <p>
 * To use a local accessor, declare a {@link ConstantOperand} on the operation. The corresponding
 * builder method for the operation will take a {@link BytecodeLocal} argument for the local to be
 * accessed. At run time, a {@link LocalAccessor} for the local will be supplied as a parameter to
 * the operation.
 * <p>
 * Local accessors are useful to implement behaviour that cannot be implemented with the builtin
 * local operations (like StoreLocal). For example, if an operation produces multiple outputs, it
 * can write one of the outputs to a local using a local accessor. Prefer builtin operations when
 * possible, since they automatically work with {@link GenerateBytecode#boxingEliminationTypes()
 * boxing elimination}. Local accessors should be preferred over {@link BytecodeNode} helpers like
 * {@link BytecodeNode#getLocalValue(int, Frame, int)}, since the helpers use extra indirection.
 * <p>
 * All of the accessor methods take a {@link BytecodeNode bytecode node} and the current
 * {@link VirtualFrame frame}. The bytecode node should be the current bytecode node and correspond
 * to the root that declares the local; it should be compilation-final. The frame should contain the
 * local.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;Operation
 * &#64;ConstantOperand(type = LocalAccessor.class)
 * public static final class GetLocal {
 *     &#64;Specialization
 *     public static Object perform(VirtualFrame frame,
 *                     LocalAccessor accessor,
 *                     &#64;Bind BytecodeNode bytecodeNode) {
 *         return accessor.getObject(bytecodeNode, frame);
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
     * Loads an object from the local.
     *
     * @since 24.2
     */
    public Object getObject(BytecodeNode bytecodeNode, VirtualFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalValueInternal(frame, localOffset, localIndex);
    }

    /**
     * Loads a boolean from the local.
     *
     * @since 24.2
     */
    public boolean getBoolean(BytecodeNode bytecodeNode, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalValueInternalBoolean(frame, localOffset, localIndex);
    }

    /**
     * Loads a byte from the local.
     *
     * @since 24.2
     */
    public byte getByte(BytecodeNode bytecodeNode, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalValueInternalByte(frame, localOffset, localIndex);
    }

    /**
     * Loads an int from the local.
     *
     * @since 24.2
     */
    public int getInt(BytecodeNode bytecodeNode, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalValueInternalInt(frame, localOffset, localIndex);
    }

    /**
     * Loads a long from the local.
     *
     * @since 24.2
     */
    public long getLong(BytecodeNode bytecodeNode, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalValueInternalLong(frame, localOffset, localIndex);
    }

    /**
     * Loads a float from the local.
     *
     * @since 24.2
     */
    public float getFloat(BytecodeNode bytecodeNode, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalValueInternalFloat(frame, localOffset, localIndex);
    }

    /**
     * Loads a double from the local.
     *
     * @since 24.2
     */
    public double getDouble(BytecodeNode bytecodeNode, VirtualFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalValueInternalDouble(frame, localOffset, localIndex);
    }

    /**
     * Stores an object into the local.
     *
     * @since 24.2
     */
    public void setObject(BytecodeNode bytecodeNode, VirtualFrame frame, Object value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.setLocalValueInternal(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a short into the local.
     *
     * @since 24.2
     */
    public void setBoolean(BytecodeNode bytecodeNode, VirtualFrame frame, boolean value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.setLocalValueInternalBoolean(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a byte into the local.
     *
     * @since 24.2
     */
    public void setByte(BytecodeNode bytecodeNode, VirtualFrame frame, byte value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.setLocalValueInternalByte(frame, localOffset, localIndex, value);
    }

    /**
     * Stores an int into the local.
     *
     * @since 24.2
     */
    public void setInt(BytecodeNode bytecodeNode, VirtualFrame frame, int value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.setLocalValueInternalInt(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a long into the local.
     *
     * @since 24.2
     */
    public void setLong(BytecodeNode bytecodeNode, VirtualFrame frame, long value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.setLocalValueInternalLong(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a float into the local.
     *
     * @since 24.2
     */
    public void setFloat(BytecodeNode bytecodeNode, VirtualFrame frame, float value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.setLocalValueInternalFloat(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a double into the local.
     *
     * @since 24.2
     */
    public void setDouble(BytecodeNode bytecodeNode, VirtualFrame frame, double value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.setLocalValueInternalDouble(frame, localOffset, localIndex, value);
    }

    /**
     * Clears the local from the frame.
     * <p>
     * Clearing the slot marks the frame slot as
     * {@link com.oracle.truffle.api.frame.FrameSlotKind#Illegal illegal}. An exception will be
     * thrown if it is read before being set. Clearing does <i>not</i> insert a
     * {@link GenerateBytecode#defaultLocalValue() default local value}, if specified.
     *
     * @since 24.2
     */
    public void clear(BytecodeNode bytecodeNode, VirtualFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        bytecodeNode.clearLocalValueInternal(frame, localOffset, localIndex);
    }

    /**
     * Checks whether the local has been {@link #clear cleared} (and a new value has not been set).
     * <p>
     * This method also returns {@code true} if a local has not been initialized and no
     * {@link GenerateBytecode#defaultLocalValue() default local value} is specified.
     *
     * @since 24.2
     */
    public boolean isCleared(BytecodeNode bytecodeNode, VirtualFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.isLocalClearedInternal(frame, localOffset, localIndex);
    }

    /**
     * Returns the name associated with the local.
     *
     * @since 24.2
     * @see BytecodeNode#getLocalName(int, int)
     */
    public Object getLocalName(BytecodeNode bytecodeNode) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalNameInternal(localOffset, localIndex);
    }

    /**
     * Returns the info associated with the local.
     *
     * @since 24.2
     * @see BytecodeNode#getLocalInfo(int, int)
     */
    public Object getLocalInfo(BytecodeNode bytecodeNode) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        return bytecodeNode.getLocalInfoInternal(localOffset, localIndex);
    }

    private static final int CACHE_SIZE = 64;

    @CompilationFinal(dimensions = 1) private static final LocalAccessor[] CACHE = createCache();

    private static LocalAccessor[] createCache() {
        LocalAccessor[] setters = new LocalAccessor[CACHE_SIZE];
        for (int i = 0; i < setters.length; i++) {
            setters[i] = new LocalAccessor(i, i);
        }
        return setters;
    }

    /**
     * Obtains a {@link LocalAccessor}.
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
        return obj instanceof LocalAccessor otherAccessor && this.localOffset == otherAccessor.localOffset && this.localIndex == otherAccessor.localIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(localOffset, localIndex);
    }

}
