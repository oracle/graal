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
     * are {@link Instrumenter#attachListener(SourceSectionFilter, ExecutionEventListener) attached}
     * .
     *
     * @param frame the current frame used in the instrumented node
     * @since 0.12
     */
    protected void onEnter(VirtualFrame frame) {
        // do nothing by default
    }

    /**
     * Invoked immediately after an {@link EventContext#getInstrumentedNode() instrumented node} is
     * successfully executed. The order in which multiple event listeners are notified matches the
     * order they are
     * {@link Instrumenter#attachListener(SourceSectionFilter, ExecutionEventListener) attached}.
     *
     * @param frame the frame that was used for executing instrumented node
     * @since 0.12
     */
    protected void onReturnValue(VirtualFrame frame, Object result) {
        // do nothing by default
    }

    /**
     * Invoked immediately after an {@link EventContext#getInstrumentedNode() instrumented node} did
     * not successfully execute. The order in which multiple event listeners are notified matches
     * the order they are
     * {@link Instrumenter#attachListener(SourceSectionFilter, ExecutionEventListener) attached}.
     *
     * @param frame the frame that was used for executing instrumented node
     * @since 0.12
     */
    protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
        // do nothing by default
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

}
