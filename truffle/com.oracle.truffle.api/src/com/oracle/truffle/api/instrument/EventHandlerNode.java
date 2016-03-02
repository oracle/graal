/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * An Instrumentation-managed {@link Node} that synchronously propagates notification of AST
 * Execution Events through the {@linkplain Instrumenter Instrumentation Framework} .
 * 
 * @since 0.8 or earlier
 */
public abstract class EventHandlerNode extends Node implements InstrumentationNode {
    /** @since 0.8 or earlier */
    protected EventHandlerNode() {
    }

    /**
     * An AST node's execute method is about to be called.
     * 
     * @since 0.8 or earlier
     */
    public abstract void enter(Node node, VirtualFrame frame);

    /**
     * An AST Node's {@code void}-valued execute method has just returned.
     * 
     * @since 0.8 or earlier
     */
    public abstract void returnVoid(Node node, VirtualFrame frame);

    /**
     * An AST Node's execute method has just returned a value (boxed if primitive).
     * 
     * @since 0.8 or earlier
     */
    public abstract void returnValue(Node node, VirtualFrame frame, Object result);

    /**
     * An AST Node's execute method has just thrown an exception.
     * 
     * @since 0.8 or earlier
     */
    public abstract void returnExceptional(Node node, VirtualFrame frame, Throwable exception);

    /**
     * Gets the {@link Probe} that manages this chain of event handling.
     * 
     * @since 0.8 or earlier
     */
    public abstract Probe getProbe();

}
