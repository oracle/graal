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

/**
 * Factory for creating <em>wrapper nodes</em>. The instrumentation framework inserts a wrapper
 * between an {@link Instrumentable} guest language node (called the <em>delegate</em>) and its
 * parent for the purpose of interposing on execution events at the delegate and reporting those
 * events to the instrumentation framework.
 * </p>
 * <p>
 * Wrapper implementations can be generated automatically: see the {@link Instrumentable}
 * annotation.
 *
 * @param <T> the type of delegate node this factory operates on
 * @since 0.12
 * @deprecated use {@link GenerateWrapper} and implement
 *             {@link InstrumentableNode#createWrapper(ProbeNode)} instead.
 */
@Deprecated
public interface InstrumentableFactory<T extends Node> {

    /**
     * Returns a new, never adopted, unshared {@link WrapperNode wrapper} node implementation for a
     * particular {@link Instrumentable} node of the guest language AST called its <em>delegate</em>
     * . The returned wrapper implementation must extend the same type that is annotated with
     * {@link Instrumentable}.
     * </p>
     * <p>
     * A wrapper forwards the following events concerning the delegate to the given {@link ProbeNode
     * probe} for propagation through the instrumentation framework, e.g. to
     * {@linkplain ExecutionEventListener event listeners} bound to this guest language program
     * location:
     * <ul>
     * <li>{@linkplain ProbeNode#onEnter(com.oracle.truffle.api.frame.VirtualFrame) onEnter(Frame)}:
     * an <em>execute</em> method on the delegate is ready to be called;</li>
     * <li>{@linkplain ProbeNode#onReturnValue(com.oracle.truffle.api.frame.VirtualFrame, Object)
     * onReturnValue(Frame,Object)}: an <em>execute</em> method on the delegate has just returned a
     * (possibly <code>null</code>) value;</li>
     * <li>
     * {@linkplain ProbeNode#onReturnExceptionalOrUnwind(com.oracle.truffle.api.frame.VirtualFrame, Throwable, boolean)
     * onReturnExceptionalOrUnwind(Frame,Throwable,boolean)}: an <em>execute</em> method on the
     * delegate has just thrown an exception.</li>
     * </ul>
     * </p>
     *
     * @param node the {@link Instrumentable} <em>delegate</em> to be adopted by the wrapper
     * @param probe the {@link ProbeNode probe node} to be adopted and sent execution events by the
     *            wrapper
     * @return a {@link WrapperNode wrapper} implementation
     * @since 0.12
     */
    WrapperNode createWrapper(T node, ProbeNode probe);

    /**
     * Nodes that the instrumentation framework inserts into guest language ASTs (between
     * {@link Instrumentable} guest language nodes and their parents) for the purpose of interposing
     * on execution events and reporting them via the instrumentation framework.
     *
     * @see #createWrapper(Node, ProbeNode)
     * @since 0.12
     * @deprecated use {@link GenerateWrapper} and implement
     *             {@link InstrumentableNode#createWrapper(ProbeNode)} instead.
     */
    @Deprecated
    public interface WrapperNode {

        /**
         * The {@link Instrumentable} guest language node, adopted as a child, whose execution
         * events the wrapper reports to the instrumentation framework.
         *
         * @since 0.12
         */
        Node getDelegateNode();

        /**
         * A child of the wrapper, through which the wrapper reports execution events related to the
         * guest language <em>delegate</em> node.
         *
         * @since 0.12
         */
        ProbeNode getProbeNode();

    }

}
