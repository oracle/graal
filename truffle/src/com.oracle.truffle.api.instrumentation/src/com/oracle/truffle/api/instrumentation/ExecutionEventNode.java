/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ProbeNode.EventProviderWithInputChainNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * An event node created by an {@link ExecutionEventNodeFactory} for a specific locations of a guest
 * language program to listen to instrumentation events. In addition to
 * {@link ExecutionEventListener listeners} event nodes allow to store state for a particular
 * {@link EventContext program location}.
 *
 * @since 0.12
 */
@NodeInfo(cost = NodeCost.NONE)
@SuppressWarnings("unused")
public abstract class ExecutionEventNode extends Node {
    /** @since 0.12 */
    protected ExecutionEventNode() {
    }

    /**
     * Invoked immediately before the {@link EventContext#getInstrumentedNode() instrumented node}
     * is executed. The order in which multiple event listeners are notified matches the order they
     * are
     * {@link Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
     * attached}.
     *
     * @param frame the current frame used in the instrumented node
     * @since 0.12
     */
    protected void onEnter(VirtualFrame frame) {
        // do nothing by default
    }

    /**
     * Invoked immediately after each return value event of instrumented input child node that match
     * the
     * {@link Instrumenter#attachExecutionEventFactory(SourceSectionFilter, SourceSectionFilter, ExecutionEventNodeFactory)
     * input filter}. Whether, when and how often input child value events are triggered depends on
     * the semantics of the instrumented node. For example, if the instrumented node represents
     * binary arithmetic then two input value events will be triggered for index <code>0</code> and
     * <code>1</code>. For short-circuited child values not all input child nodes may be executed
     * therefore they might not trigger events for {@link #getInputCount() all inputs}. For
     * instrumented loop nodes input value events with the same <code>index</code> may be triggered
     * many times.
     * <p>
     * The total number of input nodes that may produce input events can be accessed using
     * {@link #getInputCount()}. Other input contexts than the currently input context can be
     * accessed using {@link #getInputContext(int)}.
     * <p>
     * Input values can be {@link #saveInputValue(VirtualFrame, int, Object) saved} to make them
     * {@link #getSavedInputValues(VirtualFrame)} accessible in
     * {@link #onReturnValue(VirtualFrame, Object) onReturn} or
     * {@link #onReturnExceptional(VirtualFrame, Throwable) onReturnExceptional} events.
     *
     * @param frame the current frame in use
     * @param inputContext the event context of the input child node
     * @param inputIndex the child index of the input
     * @param inputValue the return value of the input child
     * @see #saveInputValue(VirtualFrame, int, Object)
     * @see #getSavedInputValues(VirtualFrame)
     * @since 0.33
     */
    protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
        // do nothing by default
    }

    /**
     * Invoked immediately after an {@link EventContext#getInstrumentedNode() instrumented node} is
     * successfully executed. The order in which multiple event listeners are notified matches the
     * order they are
     * {@link Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
     * attached}.
     *
     * @param frame the frame that was used for executing instrumented node
     * @since 0.12
     */
    protected void onReturnValue(VirtualFrame frame, Object result) {
    }

    /**
     * Invoked immediately after an {@link EventContext#getInstrumentedNode() instrumented node} did
     * not successfully execute. The order in which multiple event listeners are notified matches
     * the order they are
     * {@link Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
     * attached}.
     *
     * @param frame the frame that was used for executing instrumented node
     * @param exception the exception that occurred during the node's execution
     * @since 0.12
     */
    protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        // do nothing by default
    }

    /**
     * Invoked when an {@link EventContext#getInstrumentedNode() instrumented node} is unwound from
     * the execution stack by {@link EventContext#createUnwind(Object) unwind throwable} thrown in
     * this node implementation. Any nodes between the instrumented ones are unwound off without any
     * notification. The default implementation returns <code>null</code> to indicate continue
     * unwind.
     *
     * @param frame the frame that was used for executing instrumented node
     * @param info an info associated with the unwind - the object passed to
     *            {@link EventContext#createUnwind(Object)}
     * @return <code>null</code> to continue to unwind the parent node,
     *         {@link ProbeNode#UNWIND_ACTION_REENTER} to reenter the current node, or an interop
     *         value to return that value early from the current node (void nodes just return,
     *         ignoring the return value).
     * @since 0.31
     */
    protected Object onUnwind(VirtualFrame frame, Object info) {
        return null;
    }

    /**
     * Invoked when an event node is removed from the AST. This happens if the underlying binding,
     * language/instrument or engine is disposed. Event nodes are removed lazily. This means that
     * {@link #onDispose(VirtualFrame)} is invoked the next time the particular part of the AST is
     * executed. If the {@link EventContext#getInstrumentedNode() instrumented node} is not invoked
     * anymore after it was disposed then {@link #onDispose(VirtualFrame)} might or might not be
     * executed.
     *
     * @param frame the frame that was used for executing instrumented node
     * @since 0.12
     */
    protected void onDispose(VirtualFrame frame) {
    }

    /**
     * Returns the event context of an input by index. The returned input context matches the input
     * context provided in {@link #onInputValue(VirtualFrame, EventContext, int, Object)}. The total
     * number of instrumented input nodes can be accessed using {@link #getInputCount()}. This
     * method returns a constant event context for a constant input index, when called in partially
     * evaluated code paths.
     *
     * @param index the context index
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     * @see #onInputValue(VirtualFrame, EventContext, int, Object)
     * @see #getInputCount()
     * @since 0.33
     */
    protected final EventContext getInputContext(int index) {
        if (index < 0 || index >= getInputCount()) {
            CompilerDirectives.transferToInterpreter();
            throw new IndexOutOfBoundsException(String.valueOf(index));
        }
        EventProviderWithInputChainNode node = getChainNode();
        if (node == null) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("should not be reachable as input count should be 0");
        }
        return node.getInputContext(index);
    }

    /**
     * Returns the total number of instrumented input nodes that may produce
     * {@link #onInputValue(VirtualFrame, EventContext, int, Object) input events} when executed.
     *
     * @see #onInputValue(VirtualFrame, EventContext, int, Object)
     * @since 0.33
     */
    protected final int getInputCount() {
        EventProviderWithInputChainNode node = getChainNode();
        if (node == null) {
            return 0;
        } else {
            return node.getInputCount();
        }
    }

    /**
     * Saves an input value reported by
     * {@link #onInputValue(VirtualFrame, EventContext, int, Object)} for use in later events. Saved
     * input values can be restored using {@link #getSavedInputValues(VirtualFrame)} in
     * {@link #onReturnValue(VirtualFrame, Object) onReturn} or
     * {@link #onReturnExceptional(VirtualFrame, Throwable) onReturnExceptional} events. The
     * implementation ensures that a minimal number of frame slots is used to save input values. It
     * uses the AST structure to reuse frame slots efficiently whenever possible. The used frame
     * slots are freed if the event binding is {@link EventBinding#dispose() disposed}.
     *
     * @param frame the frame to store the input value in
     * @param inputIndex the child input index
     * @param inputValue the input value
     * @throws IllegalArgumentException for invalid input indexes for non-existing input nodes.
     * @see #onInputValue(VirtualFrame, EventContext, int, Object)
     * @since 0.33
     */
    protected final void saveInputValue(VirtualFrame frame, int inputIndex, Object inputValue) {
        EventProviderWithInputChainNode node = getChainNode();
        if (node != null) {
            node.saveInputValue(frame, inputIndex, inputValue);
        }
    }

    /**
     * Returns all saved input values. For valid input indices that did not save any value
     * <code>null</code> is returned. If all inputs were filtered or a <code>null</code> input
     * filter was provided then an empty array is returned.
     *
     * @param frame the frame to read the input values from.
     * @see #saveInputValue(VirtualFrame, int, Object)
     * @see #onInputValue(VirtualFrame, EventContext, int, Object)
     * @since 0.33
     */
    protected final Object[] getSavedInputValues(VirtualFrame frame) {
        EventProviderWithInputChainNode node = getChainNode();
        if (node != null) {
            return node.getSavedInputValues(frame);
        } else {
            return EventProviderWithInputChainNode.EMPTY_ARRAY;
        }
    }

    private EventProviderWithInputChainNode getChainNode() {
        Node parent = getParent();
        if (parent instanceof EventProviderWithInputChainNode) {
            return (EventProviderWithInputChainNode) parent;
        } else {
            return null;
        }
    }

}
