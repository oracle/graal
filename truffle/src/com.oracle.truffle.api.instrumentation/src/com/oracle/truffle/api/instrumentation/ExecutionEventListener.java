/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * A listener attached by an {@link Instrumenter} to specific locations of a guest language program
 * to listen to execution events.
 *
 * @since 0.12
 */
public interface ExecutionEventListener {

    /**
     * Invoked immediately before the {@link EventContext#getInstrumentedNode() instrumented node}
     * is executed. The order in which multiple event listeners are notified matches the order they
     * are
     * {@link Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
     * attached} .
     *
     * @param context indicating the current location in the guest language AST
     * @param frame the frame that was used for executing instrumented node
     * @since 0.12
     */
    void onEnter(EventContext context, VirtualFrame frame);

    /**
     * Invoked immediately after each return value event of child nodes that match the
     * {@link Instrumenter#attachExecutionEventListener(SourceSectionFilter, SourceSectionFilter, ExecutionEventListener)
     * input filter}. Event listeners cannot save input values for later events. If that is required
     * attach an event node factory instead.
     *
     * @param context indicating the current location in the guest language AST
     * @param frame the current frame in use
     * @param inputContext the event context of the input child node
     * @param inputIndex the child index of the input
     * @param inputValue the return value of the input child
     * @since 0.30
     * @deprecated in 20.0. input value notifications are not functional for
     *             {@link ExecutionEventListener listeners}. Use {@link ExecutionEventNodeFactory
     *             event node factories} instead.
     */
    @Deprecated
    default void onInputValue(EventContext context, VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
    }

    /**
     * Invoked immediately after an {@link EventContext#getInstrumentedNode() instrumented node} is
     * successfully executed. The order in which multiple event listeners are notified matches the
     * order they are
     * {@link Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
     * attached}.
     *
     * @param context indicating the current location in the guest language AST
     * @param frame the frame that was used for executing instrumented node
     * @since 0.12
     */
    void onReturnValue(EventContext context, VirtualFrame frame, Object result);

    /**
     * Invoked immediately after an {@link EventContext#getInstrumentedNode() instrumented node} did
     * not successfully execute. The order in which multiple event listeners are notified matches
     * the order they are
     * {@link Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
     * attached}.
     * <p>
     * When the <code>exception</code> is an instance of {@link ThreadDeath} the execution was
     * abruptly interrupted. {@link EventContext#createUnwind(Object)} creates a {@link ThreadDeath}
     * to unwind nodes off, for instance. Listener instances that threw an unwind throwable get
     * called {@link #onUnwind(EventContext, VirtualFrame, Object)} instead.
     *
     * @param context indicating the current location in the guest language AST
     * @param frame the frame that was used for executing instrumented node
     * @param exception the exception that occurred during the node's execution
     * @since 0.12
     */
    void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception);

    /**
     * Invoked when an {@link EventContext#getInstrumentedNode() instrumented node} is unwound from
     * the execution stack by {@link EventContext#createUnwind(Object) unwind throwable} thrown in
     * this listener instance. Any nodes between the instrumented ones are unwound off without any
     * notification. The default implementation returns <code>null</code>.
     *
     * @param context indicating the current location in the guest language AST
     * @param frame the frame that was used for executing instrumented node
     * @param info an info associated with the unwind - the object passed to
     *            {@link EventContext#createUnwind(Object)}
     * @return <code>null</code> to continue to unwind the parent node,
     *         {@link ProbeNode#UNWIND_ACTION_REENTER} to reenter the current node, or an interop
     *         value to return that value early from the current node (void nodes just return,
     *         ignoring the return value).
     * @since 0.31
     */
    default Object onUnwind(EventContext context, VirtualFrame frame, Object info) {
        return null;
    }
}
