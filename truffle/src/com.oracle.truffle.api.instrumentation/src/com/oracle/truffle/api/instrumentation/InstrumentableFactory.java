/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
