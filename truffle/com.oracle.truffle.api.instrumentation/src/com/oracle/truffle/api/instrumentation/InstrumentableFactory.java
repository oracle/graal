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

public interface InstrumentableFactory<T extends Node> {

    /**
     * <p>
     * Returns a new, never adopted, unshared instrumentable {@link WrapperNode wrapper} node
     * implementation for a particular node of the guest language AST. The returned wrapper
     * implementation must extend the same type that is annotated with {@link Instrumentable} and
     * forward all events to the given {@link ProbeNode probe}.
     * </p>
     *
     * @param probe the {@link ProbeNode probe} that should get adopted by the wrapper node.
     * @return a {@link WrapperNode wrapper} implementation
     */
    WrapperNode createWrapper(T node, ProbeNode probe);

    /**
     * Interface for instrumentation wrapper nodes Abstract class provided by
     * {@link InstrumentableFactory#createWrapper(Node, ProbeNode)} to notify the instrumentation
     * API about execution events.
     **/
    public interface WrapperNode {

        /**
         * Returns the original node that this node delegates to.
         */
        Node getDelegateNode();

        /**
         * Returns the probe that was returned by
         * {@link InstrumentableFactory#createWrapper(Node, ProbeNode)}.
         */
        ProbeNode getProbeNode();

    }

}
