/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.BlockNode.ElementExceptionHandler;
import com.oracle.truffle.api.nodes.BlockNode.VoidElement;

/**
 * Represents a standard node for guest language blocks. Using standard blocks in a guest language
 * is not strictly necessary, but recommended as it allows the optimizing runtime to split
 * compilation of very big methods into multiple compilation units. Block nodes may be executed
 * using a start index to resume the execution at a particular location.
 * <p>
 * The return value of execute methods return the return value of the last block element. Depending
 * on the needed return types, the last element should implement {@link VoidElement},
 * {@link GenericElement} and {@link TypedElement} and create new block nodes using
 * {@link #create(Node[])}. Callers of block nodes must only call supported execute methods of the
 * block. The following execute methods are supported if the last block element is of type:
 * <ul>
 * <li>{@link VoidElement}: Only {@link #executeVoid(VirtualFrame, int, ElementExceptionHandler)
 * void} execution is supported.
 * <li>{@link GenericElement}: {@link #executeVoid(VirtualFrame, int, ElementExceptionHandler) Void}
 * and {@link #execute(VirtualFrame, int, ElementExceptionHandler) generic} execution is supported.
 * <li>{@link TypedElement}: {@link #executeVoid(VirtualFrame, int, ElementExceptionHandler) Void},
 * {@link #execute(VirtualFrame, int, ElementExceptionHandler) generic},
 * {@link #executeBoolean(VirtualFrame, int, ElementExceptionHandler) boolean},
 * {@link #executeByte(VirtualFrame, int, ElementExceptionHandler) byte},
 * {@link #executeShort(VirtualFrame, int, ElementExceptionHandler) short},
 * {@link #executeInt(VirtualFrame, int, ElementExceptionHandler) int},
 * {@link #executeChar(VirtualFrame, int, ElementExceptionHandler) character},
 * {@link #executeLong(VirtualFrame, int, ElementExceptionHandler) long},
 * {@link #executeFloat(VirtualFrame, int, ElementExceptionHandler) float} and
 * {@link #executeDouble(VirtualFrame, int, ElementExceptionHandler) double} execution is supported.
 * </ul>
 * If an execution is not supported then a {@link ClassCastException} is thrown by the block execute
 * method at runtime. This class is intended to be implemented by Truffle runtime implementation
 * only and must not be subclassed by language or tool implementations.
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
public abstract class BlockNode<T extends Node & VoidElement> extends Node {

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
     * Executes the block using a start index and returns no return value.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract void executeVoid(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler);

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns no value. This method should be preferred to calling
     * {@linkplain #executeVoid(VirtualFrame, int, ElementExceptionHandler) executeVoid(frame, 0,
     * null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract void executeVoid(VirtualFrame frame);

    /**
     * Executes the block using a start index and returns a generic value. This method requires the
     * last block element node to implement {@link GenericElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract Object execute(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler);

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a generic value. This method should be preferred to calling
     * {@linkplain #execute(VirtualFrame, int, ElementExceptionHandler) execute(frame, 0, null)} if
     * possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract Object execute(VirtualFrame frame);

    /**
     * Executes the block using a start index and returns a byte value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract byte executeByte(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a byte value. This method should be preferred to calling
     * {@linkplain #executeByte(VirtualFrame, int, ElementExceptionHandler) executeByte(frame, 0,
     * null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract byte executeByte(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Executes the block using a start index and returns a short value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract short executeShort(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a short value. This method should be preferred to calling
     * {@linkplain #executeShort(VirtualFrame, int, ElementExceptionHandler) executeShort(frame, 0,
     * null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract short executeShort(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Executes the block using a start index and returns an int value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract int executeInt(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns an int value. This method should be preferred to calling
     * {@linkplain #executeInt(VirtualFrame, int, ElementExceptionHandler) executeInt(frame, 0,
     * null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract int executeInt(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Executes the block using a start index and returns a char value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract char executeChar(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a char value. This method should be preferred to calling
     * {@linkplain #executeChar(VirtualFrame, int, ElementExceptionHandler) executeChar(frame, 0,
     * null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract char executeChar(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Executes the block using a start index and returns a float value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract float executeFloat(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a float value. This method should be preferred to calling
     * {@linkplain #executeFloat(VirtualFrame, int, ElementExceptionHandler) executeFloat(frame, 0,
     * null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract float executeFloat(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Executes the block using a start index and returns a double value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract double executeDouble(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a double value. This method should be preferred to calling
     * {@linkplain #executeDouble(VirtualFrame, int, ElementExceptionHandler) executeDouble(frame,
     * 0, null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract double executeDouble(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Executes the block using a start index and returns a long value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract long executeLong(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a long value. This method should be preferred to calling
     * {@linkplain #executeLong(VirtualFrame, int, ElementExceptionHandler) executeLong(frame, 0,
     * null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract long executeLong(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Executes the block using a start index and returns a boolean value. This method requires the
     * last block element node to implement {@link TypedElement}, otherwise a
     * {@link ClassCastException} is thrown.
     *
     * @param frame the frame to execute the block in.
     * @param startElementIndex the start index of execute. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @param exceptionHandler exception handler instance to invoke for each element or
     *            <code>null</code> if not needed. Must be a
     *            {@link CompilerAsserts#partialEvaluationConstant(Object) partial evaluation
     *            constant}.
     * @since 19.3
     */
    public abstract boolean executeBoolean(VirtualFrame frame, int startElementIndex, ElementExceptionHandler exceptionHandler) throws UnexpectedResultException;

    /**
     * This method executes the block from start index 0 without an element exception handler and
     * returns a boolean value. This method should be preferred to calling
     * {@linkplain #executeBoolean(VirtualFrame, int, ElementExceptionHandler) executeBoolean(frame,
     * 0, null)} if possible, as it is more efficient to execute in the interpreter and to compile.
     *
     * @since 19.3
     */
    public abstract boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException;

    /**
     * Returns the elements of the block node. Elements of block nodes are provided using
     * {@link #create(Node[])}.
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
     * {@link Node} and implement {@link VoidElement}. The last element of a block might also
     * implement {@link GenericElement} or {@link TypedElement} in order to provide a return value
     * to the block.
     *
     * @since 19.3
     */
    public static <T extends Node & VoidElement> BlockNode<T> create(T[] elements) {
        Objects.requireNonNull(elements);
        if (elements.length == 0) {
            throw new IllegalArgumentException("Empty blocks are not allowed.");
        }
        return NodeAccessor.ACCESSOR.createBlockNode(elements);
    }

    /**
     * Represents the minimal required interface for block node elements. This interface is intended
     * to be implemented by language implementations and provide an implementation of
     * {@link #executeVoid(VirtualFrame)}.
     *
     * @see BlockNode
     * @since 19.3
     */
    public interface VoidElement extends NodeInterface {

        /**
         * Executes the block element, but does not provide any return value.
         *
         * @since 19.3
         */
        void executeVoid(VirtualFrame frame);

    }

    /**
     * Represents the required interface for block node elements that may return values. This
     * interface is intended to be implemented by language implementations and provide an
     * implementation of {@link #execute(VirtualFrame)}.
     *
     * @see BlockNode
     * @since 19.3
     */
    public interface GenericElement extends VoidElement {

        /**
         * Executes the block element and returns a generic value.
         *
         * @since 19.3
         */
        Object execute(VirtualFrame frame);

        /**
         * {@inheritDoc}
         *
         * @since 19.3
         */
        @Override
        default void executeVoid(VirtualFrame frame) {
            execute(frame);
        }

    }

    /**
     * Represents the required interface for block node elements that may return typed values. This
     * interface is intended to be implemented by language implementations and provide an
     * implementation of {@link #execute(VirtualFrame)}.
     *
     * @see BlockNode
     * @since 19.3
     */
    public interface TypedElement extends GenericElement {

        /**
         * Executes the block element and returns a byte value if supported. If a byte value is not
         * supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        byte executeByte(VirtualFrame frame) throws UnexpectedResultException;

        /**
         * Executes the block element and returns a short value if supported. If a short value is
         * not supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        short executeShort(VirtualFrame frame) throws UnexpectedResultException;

        /**
         * Executes the block element and returns an int value if supported. If an int value is not
         * supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        int executeInt(VirtualFrame frame) throws UnexpectedResultException;

        /**
         * Executes the block element and returns a char value if supported. If a char value is not
         * supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        char executeChar(VirtualFrame frame) throws UnexpectedResultException;

        /**
         * Executes the block element and returns a long value if supported. If a long value is not
         * supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        long executeLong(VirtualFrame frame) throws UnexpectedResultException;

        /**
         * Executes the block element and returns a float value if supported. If a float value is
         * not supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        float executeFloat(VirtualFrame frame) throws UnexpectedResultException;

        /**
         * Executes the block element and returns a double value if supported. If a double value is
         * not supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        double executeDouble(VirtualFrame frame) throws UnexpectedResultException;

        /**
         * Executes the block element and returns a boolean value if supported. If a boolean value
         * is not supported an {@link UnexpectedResultException} is thrown.
         *
         * @since 19.3
         */
        boolean executeBoolean(VirtualFrame frame) throws UnexpectedResultException;
    }

    /**
     * Represents an exception handler interface that can be used to execute code in the exception
     * handler for each element intercepting its index.
     *
     * @since 19.3
     */
    public interface ElementExceptionHandler {
        /**
         * Notified on exception for each element. Exceptions are always rethrown unless this method
         * throws a different exception.
         *
         * @param frame the frame passed to the block
         * @param e the exception that was caught
         * @param elementIndex the index of the element that was currently executed.
         * @since 19.3
         */
        @SuppressWarnings("unused")
        void onBlockElementException(VirtualFrame frame, Throwable e, int elementIndex);
    }

}

@SuppressWarnings("serial")
class BlockNodeSnippets {
    // BEGIN: com.oracle.truffle.api.nodes.BlockNodeSnippets.LanguageBlockNode
    // language base node
    abstract class LanguageNode extends Node implements BlockNode.GenericElement {

        @Override
        public abstract Object execute(VirtualFrame frame);

    }

    final class LanguageBlockNode extends LanguageNode {

        @Child private BlockNode<LanguageNode> block;

        LanguageBlockNode(LanguageNode[] elements) {
            this.block = BlockNode.create(elements);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return block.execute(frame, 0, null);
        }

        public void executeVoid(VirtualFrame frame) {
            block.executeVoid(frame, 0, null);
        }
    }
    // END: com.oracle.truffle.api.nodes.BlockNodeSnippets.LanguageBlockNode

    // BEGIN: com.oracle.truffle.api.nodes.BlockNodeSnippets.ResumableBlockNode
    final class YieldException extends ControlFlowException {

    }

    final class ResumableBlockNode extends LanguageNode
                    implements ElementExceptionHandler {

        @CompilationFinal private FrameSlot indexSlot;
        @Child private BlockNode<LanguageNode> block;

        ResumableBlockNode(LanguageNode[] elements) {
            this.block = BlockNode.create(elements);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            frame.setInt(getIndexSlot(), 0);
            return block.execute(frame, 0, null);
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
            block.execute(frame, startIndex, this);
        }

        @Override
        public void onBlockElementException(VirtualFrame frame,
                        Throwable e, int elementIndex) {
            if (e instanceof YieldException) {
                // store index to be able to resume later
                frame.setInt(getIndexSlot(), elementIndex);
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
