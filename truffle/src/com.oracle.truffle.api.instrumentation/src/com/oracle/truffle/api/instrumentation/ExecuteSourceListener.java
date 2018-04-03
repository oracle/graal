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

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;

/**
 * A listener attached by an {@link Instrumenter} to specific sources of a guest language program to
 * listen to execute source events.
 *
 * @since 0.33
 */
public interface ExecuteSourceListener {

    /**
     * Invoked whenever a new {@link Source source} is executed. The order in which multiple source
     * event listeners are notified matches the order they are
     * {@link Instrumenter#attachExecuteSourceListener(SourceFilter, ExecuteSourceListener, boolean)
     * attached}.
     * <p>
     * <b>Implementation Note:</b> Source execute events are notified when the guest language
     * implementation executes a new {@link Source source} by invoking any {@link RootNode} that
     * uses the new source in {@link Node#getSourceSection()}. It assumes that all nodes of an AST
     * have the same {@link Source source} as their root.
     * </p>
     *
     * @param event an event with context information
     * @since 0.33
     */
    void onExecute(ExecuteSourceEvent event);

}
