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
package com.oracle.truffle.api.nodes;

import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode.ElementExecutor;

/**
 * Represents a standard node for guest language blocks. Using standard blocks in a guest language
 * is not strictly necessary, but recommended as it allows the optimizing runtime to split
 * compilation of very big methods into multiple compilation units. Block nodes may be executed with
 * a customizable argument to resume the execution at a particular location.
 * <p>
 * Elements are executed using the {@link ElementExecutor} provided when
 * {@link #create(Node[], ElementExecutor) creating} the block node. When a block is executed then
 * all elements are executed using {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)
 * executeVoid} except the last element which will be executed using the typed execute method also
 * used to execute the block node. This allows to implement boxing elimination of the return value
 * of blocks in the interpreter. For example, if {@link #executeInt(VirtualFrame, int) executeInt}
 * is invoked on the block , then all elements except the last one is executed using
 * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int) executeVoid}, but the last one
 * with {@link ElementExecutor#executeInt(VirtualFrame, Node, int, int) executeInt}.
 * <p>
 * The optimizing runtime may decide to group elements of a block into multiple block compilation
 * units. This may happen if the block is too big to be compiled with a single compilation unit. If
 * the compilation final state of an element is changed, or a node is replaced only the compilation
 * unit of the current block is invalidated and not all compilations units of a block. Therefore, no
 * compilation final assumptions must be taken between elements of a block.
 * <p>
 * <h3>Simple Usage:</h3> The following example shows how a language with untyped execute methods,
 * but with blocks that return values would use the block node.
 * {@link com.oracle.truffle.api.nodes.BlockNodeSnippets.LanguageBlockNode}
 *
 * <h3>Resumable Usage:</h3> The following example shows how the block node can be used to implement
 * resumable blocks, e.g. for generator implementations:
 * {@link com.oracle.truffle.api.nodes.BlockNodeSnippets.ResumableBlockNode}
 *
 * @param <T> the type of the block element node
 * @since 19.3
 */
public abstract class BlockNode<T extends Node> extends Node {

    /**
     * Use when no argument is needed for the block node.
     *
     * @since 19.3
     */
    public static final int NO_ARGUMENT = 0;

    @Children private final T[] elements;

    /**
     * Internal constructor for implementations. Do not use.
     *
     * @since 19.3
     */
    protected BlockNode(T[] elements) {
        this.elements = elements;
        assert getClass().getName().equals("com.oracle.truffle.api.impl.DefaultBlockNode") ||
                        getClass().getName().equals("org.graalvm.compiler.truffle.runtime.OptimizedBlockNode") : "Custom block implementations are not allowed.";
    }

    /**
     * Executes the block and returns no value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom argument that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract void executeVoid(VirtualFrame frame, int argument);

    /**
     * Executes the block and returns a generic value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeGeneric(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract Object executeGeneric(VirtualFrame frame, int argument);

    /**
     * Executes the block and returns a byte value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeByte(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract byte executeByte(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Executes the block and returns a short value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeShort(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract short executeShort(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Executes the block and returns an int value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeInt(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract int executeInt(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Executes the block and returns a char value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeChar(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract char executeChar(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Executes the block and returns a float value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeFloat(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract float executeFloat(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Executes the block and returns a double value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeDouble(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract double executeDouble(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Executes the block and returns a long value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeLong(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract long executeLong(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Executes the block and returns a boolean value. The block implementation calls
     * {@link ElementExecutor#executeVoid(VirtualFrame, Node, int, int)} for all elements of the
     * block except the last element. The last element is executed using
     * {@link ElementExecutor#executeBoolean(VirtualFrame, Node, int, int)}.
     *
     * @param frame the frame to execute the block in.
     * @param argument a custom value that is forwarded to the executor as is. If no argument is
     *            needed then {@link BlockNode#NO_ARGUMENT} should be used.
     * @since 19.3
     */
    public abstract boolean executeBoolean(VirtualFrame frame, int argument) throws UnexpectedResultException;

    /**
     * Returns the elements of the block node. Elements of block nodes are provided using
     * {@link #create(Node[], ElementExecutor)}.
     *
     * @since 19.3
     */
    public final T[] getElements() {
        return elements;
    }

    /**
     * Block nodes always have {@link NodeCost#NONE}.
     *
     * {@inheritDoc}
     *
     * @since 19.3
     */
    @Override
    public final NodeCost getCost() {
        return NodeCost.NONE;
    }

    /**
     * Creates a new block node. The elements array of the block node must not be empty or an
     * {@link IllegalArgumentException} is thrown. Elements of a block must at least extend
     * {@link Node}. An executor must be provided that allows the block node implementation to
     * execute the block elements. The executor must not be <code>null</code>.
     *
     * @since 19.3
     */
    public static <T extends Node> BlockNode<T> create(T[] elements, ElementExecutor<T> executor) {
        Objects.requireNonNull(elements);
        Objects.requireNonNull(executor);
        if (elements.length == 0) {
            throw new IllegalArgumentException("Empty blocks are not allowed.");
        }
        return NodeAccessor.RUNTIME.createBlockNode(elements, executor);
    }

    /**
     * Represents a contract how block element nodes can be executed. Designed for the block node
     * only.
     *
     * @see BlockNode
     * @since 19.3
     */
    public interface ElementExecutor<T extends Node> {
        /**
         * Executes the block node element without expecting any return value.
         *
         * @since 19.3
         */
        void executeVoid(VirtualFrame frame, T node, int index, int argument);

        /**
         * Executes the block node element and expects a generic value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeVoid(VirtualFrame, Node, int, int)} by default.
         *
         * @since 19.3
         */
        default Object executeGeneric(VirtualFrame frame, T node, int index, int argument) {
            executeVoid(frame, node, index, argument);
            return null;
        }

        /**
         * Executes the block node element and expects a boolean value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not a boolean.
         *
         * @since 19.3
         */
        default boolean executeBoolean(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Boolean) {
                return (boolean) result;
            }
            throw new UnexpectedResultException(result);
        }

        /**
         * Executes the block node element and expects a byte value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not a byte.
         *
         * @since 19.3
         */
        default byte executeByte(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Byte) {
                return (byte) result;
            }
            throw new UnexpectedResultException(result);
        }

        /**
         * Executes the block node element and expects a short value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not a short.
         *
         * @since 19.3
         */
        default short executeShort(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Short) {
                return (short) result;
            }
            throw new UnexpectedResultException(result);
        }

        /**
         * Executes the block node element and expects a char value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not a char.
         *
         * @since 19.3
         */
        default char executeChar(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Character) {
                return (char) result;
            }
            throw new UnexpectedResultException(result);
        }

        /**
         * Executes the block node element and expects an int value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not an int.
         *
         * @since 19.3
         */
        default int executeInt(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Integer) {
                return (int) result;
            }
            throw new UnexpectedResultException(result);
        }

        /**
         * Executes the block node element and expects a long value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not a long.
         *
         * @since 19.3
         */
        default long executeLong(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Long) {
                return (long) result;
            }
            throw new UnexpectedResultException(result);
        }

        /**
         * Executes the block node element and expects a float value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not a float.
         *
         * @since 19.3
         */
        default float executeFloat(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Float) {
                return (float) result;
            }
            throw new UnexpectedResultException(result);
        }

        /**
         * Executes the block node element and expects a double value. This method is used for the
         * last element of the block, to provide a value to the block execute method. Forwards to
         * {@link #executeGeneric(VirtualFrame, Node, int, int)} by default and throws an
         * {@link UnexpectedResultException} if the value is not a double.
         *
         * @since 19.3
         */
        default double executeDouble(VirtualFrame frame, T node, int index, int argument) throws UnexpectedResultException {
            Object result = executeGeneric(frame, node, index, argument);
            if (result instanceof Double) {
                return (double) result;
            }
            throw new UnexpectedResultException(result);
        }

    }

}

@SuppressWarnings("serial")
class BlockNodeSnippets {
    // BEGIN: com.oracle.truffle.api.nodes.BlockNodeSnippets.LanguageBlockNode
    // language base node
    abstract class LanguageNode extends Node {

        public abstract Object execute(VirtualFrame frame);

    }

    final class LanguageBlockNode extends LanguageNode
                    implements ElementExecutor<LanguageNode> {

        @Child private BlockNode<LanguageNode> block;

        LanguageBlockNode(LanguageNode[] elements) {
            this.block = BlockNode.create(elements, this);
        }

        @Override
        public void executeVoid(VirtualFrame frame, LanguageNode node,
                        int index, int arg) {
            node.execute(frame);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, LanguageNode node,
                        int index, int arg) {
            return node.execute(frame);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return block.executeGeneric(frame, 0);
        }
    }
    // END: com.oracle.truffle.api.nodes.BlockNodeSnippets.LanguageBlockNode

    // BEGIN: com.oracle.truffle.api.nodes.BlockNodeSnippets.ResumableBlockNode
    final class YieldException extends ControlFlowException {

        final Object result;

        YieldException(Object result) {
            this.result = result;
        }

    }

    final class ResumableBlockNode extends LanguageNode
                    implements ElementExecutor<LanguageNode> {

        @CompilationFinal private FrameSlot indexSlot;
        @Child private BlockNode<LanguageNode> block;

        ResumableBlockNode(LanguageNode[] elements) {
            this.block = BlockNode.create(elements, this);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setInt(getIndexSlot(), 0);
            return block.executeGeneric(frame, 0);
        }

        // Called if the resumable block needs to be
        // resumed later on after a yield.
        public void resume(VirtualFrame frame) {
            getIndexSlot();
            int startIndex;
            try {
                startIndex = frame.getInt(getIndexSlot());
            } catch (FrameSlotTypeException e) {
                // should not happen because the first time
                // the block must be called using execute
                throw new AssertionError();
            }
            block.executeGeneric(frame, startIndex);
        }

        @Override
        public void executeVoid(VirtualFrame frame, LanguageNode node,
                        int elementIndex, int startIndex) {
            executeGeneric(frame, node, elementIndex, startIndex);
        }

        @Override
        public Object executeGeneric(VirtualFrame frame, LanguageNode node,
                        int elementIndex, int startIndex) {
            if (elementIndex >= startIndex) {
                try {
                    return node.execute(frame);
                } catch (YieldException e) {
                    // store index to be able to resume later
                    frame.setInt(getIndexSlot(), elementIndex);
                    return e.result;
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new AssertionError("Invalid start index");
            }
        }

        private FrameSlot getIndexSlot() {
            FrameSlot slot = this.indexSlot;
            if (slot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                FrameDescriptor fd = getRootNode().getFrameDescriptor();
                this.indexSlot = slot = fd.findOrAddFrameSlot(this);
            }
            return slot;
        }

    }
    // END: com.oracle.truffle.api.nodes.BlockNodeSnippets.ResumableBlockNode
}
