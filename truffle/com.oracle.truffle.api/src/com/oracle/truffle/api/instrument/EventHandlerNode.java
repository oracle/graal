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
 * Execution Events through the Instrumentation Framework.
 */
public abstract class EventHandlerNode extends Node implements InstrumentationNode {

    protected EventHandlerNode() {
    }

    /**
     * An AST node's execute method is about to be called.
     */
    public abstract void enter(Node node, VirtualFrame vFrame);

    /**
     * An AST Node's {@code void}-valued execute method has just returned.
     */
    public abstract void returnVoid(Node node, VirtualFrame vFrame);

    /**
     * An AST Node's execute method has just returned a value (boxed if primitive).
     */
    public abstract void returnValue(Node node, VirtualFrame vFrame, Object result);

    /**
     * An AST Node's execute method has just thrown an exception.
     */
    public abstract void returnExceptional(Node node, VirtualFrame vFrame, Exception exception);

    /**
     * Gets the {@link Probe} that manages this chain of event handling.
     */
    public abstract Probe getProbe();

}
