/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * Operation parameter that allows an operation to get, set, and clear the value of a local from a
 * materialized frame of the current root node or an outer root node. This class can only be used if
 * the root node supports {@link GenerateBytecode#enableMaterializedLocalAccesses() materialized
 * local accesses}.
 * <p>
 * To use a materialized local accessor, declare a {@link ConstantOperand} on the operation. The
 * corresponding builder method for the operation will take a {@link BytecodeLocal} argument for the
 * local to be accessed. At run time, a {@link MaterializedLocalAccessor} for the local will be
 * supplied as a parameter to the operation.
 * <p>
 * Materialized local accessors are useful to implement behaviour that cannot be implemented with
 * the builtin materialized local operations (like StoreLocalMaterialized), nor with plain
 * {@link LocalAccessor local accessors}.
 * <p>
 * All of the materialized accessor methods take a {@link BytecodeNode bytecode node} and the
 * current {@link MaterializedFrame frame}. The bytecode node should be the current bytecode node
 * (not necessarily corresponding to the root node that declares the local); it should be
 * compilation-final. The frame should contain the local and be materialized.
 * <p>
 * Example usage:
 *
 * <pre>
 * &#64;Operation
 * &#64;ConstantOperand(type = MaterializedLocalAccessor.class)
 * public static final class GetMaterializedLocal {
 *     &#64;Specialization
 *     public static Object perform(VirtualFrame frame,
 *                     MaterializedLocalAccessor accessor,
 *                     MaterializedFrame materializedFrame,
 *                     &#64;Bind BytecodeNode bytecodeNode) {
 *         return accessor.getObject(bytecodeNode, materializedFrame);
 *     }
 * }
 * </pre>
 *
 * @since 24.2
 */
public final class MaterializedLocalAccessor {
    private final int rootIndex;
    private final int localOffset;
    private final int localIndex;

    private MaterializedLocalAccessor(int rootIndex, int localOffset, int localIndex) {
        this.rootIndex = rootIndex;
        this.localOffset = localOffset;
        this.localIndex = localIndex;
    }

    /**
     * Returns a string representation of a {@link MaterializedLocalAccessor}.
     *
     * @since 24.2
     */
    @Override
    public String toString() {
        return String.format("MaterializedLocalAccessor[rootIndex=%d, localOffset=%d, localIndex=%d]", rootIndex, localOffset, localIndex);
    }

    /**
     * Loads an object from the local.
     *
     * @since 24.2
     */
    public Object getObject(BytecodeNode bytecodeNode, MaterializedFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalValueInternal(frame, localOffset, localIndex);
    }

    /**
     * Loads a boolean from the local.
     *
     * @since 24.2
     */
    public boolean getBoolean(BytecodeNode bytecodeNode, MaterializedFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalValueInternalBoolean(frame, localOffset, localIndex);
    }

    /**
     * Loads a byte from the local.
     *
     * @since 24.2
     */
    public byte getByte(BytecodeNode bytecodeNode, MaterializedFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalValueInternalByte(frame, localOffset, localIndex);
    }

    /**
     * Loads an int from the local.
     *
     * @since 24.2
     */
    public int getInt(BytecodeNode bytecodeNode, MaterializedFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalValueInternalInt(frame, localOffset, localIndex);
    }

    /**
     * Loads a long from the local.
     *
     * @since 24.2
     */
    public long getLong(BytecodeNode bytecodeNode, MaterializedFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalValueInternalLong(frame, localOffset, localIndex);
    }

    /**
     * Loads a float from the local.
     *
     * @since 24.2
     */
    public float getFloat(BytecodeNode bytecodeNode, MaterializedFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalValueInternalFloat(frame, localOffset, localIndex);
    }

    /**
     * Loads a double from the local.
     *
     * @since 24.2
     */
    public double getDouble(BytecodeNode bytecodeNode, MaterializedFrame frame) throws UnexpectedResultException {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalValueInternalDouble(frame, localOffset, localIndex);
    }

    /**
     * Stores an object into the local.
     *
     * @since 24.2
     */
    public void setObject(BytecodeNode bytecodeNode, MaterializedFrame frame, Object value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.setLocalValueInternal(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a boolean into the local.
     *
     * @since 24.2
     */
    public void setBoolean(BytecodeNode bytecodeNode, MaterializedFrame frame, boolean value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.setLocalValueInternalBoolean(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a byte into the local.
     *
     * @since 24.2
     */
    public void setByte(BytecodeNode bytecodeNode, MaterializedFrame frame, byte value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.setLocalValueInternalByte(frame, localOffset, localIndex, value);
    }

    /**
     * Stores an int into the local.
     *
     * @since 24.2
     */
    public void setInt(BytecodeNode bytecodeNode, MaterializedFrame frame, int value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.setLocalValueInternalInt(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a long into the local.
     *
     * @since 24.2
     */
    public void setLong(BytecodeNode bytecodeNode, MaterializedFrame frame, long value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.setLocalValueInternalLong(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a float into the local.
     *
     * @since 24.2
     */
    public void setFloat(BytecodeNode bytecodeNode, MaterializedFrame frame, float value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.setLocalValueInternalFloat(frame, localOffset, localIndex, value);
    }

    /**
     * Stores a double into the local.
     *
     * @since 24.2
     */
    public void setDouble(BytecodeNode bytecodeNode, MaterializedFrame frame, double value) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.setLocalValueInternalDouble(frame, localOffset, localIndex, value);
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
    public void clear(BytecodeNode bytecodeNode, MaterializedFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        declaringBytecodeNode.clearLocalValueInternal(frame, localOffset, localIndex);
    }

    /**
     * Checks whether the local has been {@link #clear cleared} (and a new value has not been set).
     * <p>
     * This method also returns {@code true} if a local has not been initialized and no
     * {@link GenerateBytecode#defaultLocalValue() default local value} is specified.
     *
     * @since 24.2
     */
    public boolean isCleared(BytecodeNode bytecodeNode, MaterializedFrame frame) {
        CompilerAsserts.partialEvaluationConstant(this);
        CompilerAsserts.partialEvaluationConstant(bytecodeNode);
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.isLocalClearedInternal(frame, localOffset, localIndex);
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
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalNameInternal(localOffset, localIndex);
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
        BytecodeNode declaringBytecodeNode = bytecodeNode.getBytecodeRootNode().getRootNodes().getNode(rootIndex).getBytecodeNode();
        CompilerAsserts.partialEvaluationConstant(declaringBytecodeNode);
        return declaringBytecodeNode.getLocalInfoInternal(localOffset, localIndex);
    }

    private static final int CACHE_SIZE = 64;

    @CompilationFinal(dimensions = 1) private static final MaterializedLocalAccessor[] CACHE = createCache();

    private static MaterializedLocalAccessor[] createCache() {
        MaterializedLocalAccessor[] setters = new MaterializedLocalAccessor[CACHE_SIZE];
        for (int i = 0; i < setters.length; i++) {
            setters[i] = new MaterializedLocalAccessor(0, i, i);
        }
        return setters;
    }

    /**
     * Obtains a {@link MaterializedLocalAccessor}.
     *
     * This method is invoked by the generated code and should not be called directly.
     *
     * @since 24.2
     */
    public static MaterializedLocalAccessor constantOf(int rootIndex, BytecodeLocal local) {
        int offset = local.getLocalOffset();
        int index = local.getLocalIndex();
        assert offset <= index;
        if (rootIndex == 0 && index == offset && offset < CACHE_SIZE) {
            return CACHE[offset];
        }
        return new MaterializedLocalAccessor(rootIndex, offset, index);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof MaterializedLocalAccessor otherAccessor && this.rootIndex == otherAccessor.rootIndex && this.localOffset == otherAccessor.localOffset &&
                        this.localIndex == otherAccessor.localIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootIndex, localOffset, localIndex);
    }

}
