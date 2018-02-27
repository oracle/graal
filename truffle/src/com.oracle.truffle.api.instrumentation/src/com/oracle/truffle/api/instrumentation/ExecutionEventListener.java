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
     */
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
