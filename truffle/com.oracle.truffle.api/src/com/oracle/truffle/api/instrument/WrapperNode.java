/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.Node;

/**
 * A specialized {@link Node} instance that must be inserted into a Truffle AST in order to enable
 * {@linkplain Instrumenter instrumentation} at a particular Guest Language (GL) node.
 * <p>
 * The implementation must be GL-specific. A wrapper <em>decorates</em> a GL AST node (the wrapper's
 * <em>child</em>) by acting as a transparent <em>proxy</em> with respect to the GL's execution
 * semantics.
 * <p>
 * Instrumentation at the wrapped node is implemented by an instance of {@link EventHandlerNode}
 * attached as a second child of the {@link WrapperNode}.
 * <p>
 * A wrapper is obliged to notify its attached {@link EventHandlerNode} when execution events occur
 * at the wrapped AST node during program execution.
 * <p>
 * When a GL AST is cloned, the {@link WrapperNode}, its {@link EventHandlerNode} and any
 * {@linkplain ProbeInstrument instrumentation} are also cloned; they are in effect part of the GL
 * AST. An instance of {@link Probe} represents abstractly the instrumentation at a particular
 * location in a GL AST; it tracks all the copies of the Wrapper and attached instrumentation, and
 * acts as a single point of access for tools.
 * <p>
 * Implementation guidelines:
 * <ol>
 * <li>Each GL implementation must implement a WrapperNode implementation for each AST context in
 * which Instrumentation is to be supported.</li>
 * <li>The wrapper type should descend from the <em>GL-specific node class</em>.</li>
 * <li>Must have a field: {@code @Child private <GL>Node child;}</li>
 * <li>Must have a field: {@code @Child private EventHandlerNode eventHandlerNode;}</li>
 * <li>The wrapper must act as a <em>proxy</em> for its child, which means implementing every
 * possible <em>execute-</em> method that gets called on guest language AST node types by their
 * parents, and passing along each call to its child.</li>
 * <li>Method {@code Probe getProbe()} should be implemented as {@code eventHandlerNode.getProbe();}
 * <li>Method {@code insertProbe(EventHandlerNode)} should be implemented as
 * {@code this.eventHandlerNode=insert(eventHandlerNode);}</li>
 * <li>Most importantly, Wrappers must be implemented so that Truffle optimization will reduce their
 * runtime overhead to zero when there are no attached {@link ProbeInstrument}s.</li>
 * </ol>
 * <p>
 *
 * @see ProbeInstrument
 * @since 0.8 or earlier
 */
public interface WrapperNode extends InstrumentationNode {

    /**
     * Gets the node being "wrapped", i.e. the AST node for which {@linkplain EventHandlerNode
     * execution events} will be reported through the Instrumentation Framework.
     * 
     * @since 0.8 or earlier
     */
    Node getChild();

    /**
     * Gets the {@link Probe} responsible for installing this wrapper.
     * 
     * @since 0.8 or earlier
     */
    Probe getProbe();

    /**
     * Implementation support for completing a newly created wrapper node.
     * 
     * @since 0.8 or earlier
     */
    void insertEventHandlerNode(EventHandlerNode eventHandlerNode);
}
