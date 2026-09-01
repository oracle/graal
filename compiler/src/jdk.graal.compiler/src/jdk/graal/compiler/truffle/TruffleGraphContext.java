/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import com.oracle.truffle.compiler.TruffleCompilable;

import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Provides Truffle compilation state associated with a {@link StructuredGraph}.
 */
public final class TruffleGraphContext {

    private final boolean guestInlining;
    private final TruffleCompilable compilable;

    private TruffleGraphContext(boolean guestInlining, TruffleCompilable compilable) {
        this.guestInlining = guestInlining;
        this.compilable = compilable;
    }

    /**
     * Creates the context for the root of a guest compilation. The guest root can itself be inlined
     * into a host compilation.
     *
     * @param compilable the guest call target represented by the graph
     * @return a root graph context
     */
    public static TruffleGraphContext createRoot(TruffleCompilable compilable) {
        return new TruffleGraphContext(false, compilable);
    }

    /**
     * Creates the context for a guest call target being considered for inlining into another guest
     * call target.
     *
     * @param compilable the guest call target represented by the graph
     * @return a guest inlining graph context
     */
    public static TruffleGraphContext createGuestInlining(TruffleCompilable compilable) {
        return new TruffleGraphContext(true, compilable);
    }

    /**
     * Returns the Truffle compilation state associated with {@code graph}.
     *
     * @param graph a graph created for a Truffle compilation
     * @return the graph's Truffle compilation state
     */
    public static TruffleGraphContext get(StructuredGraph graph) {
        return ((TruffleCompilationIdentifier) graph.compilationId()).getGraphContext();
    }

    /**
     * Returns whether this graph represents a guest call target being considered for inlining into
     * another guest call target.
     *
     * @return {@code true} for a guest inlining candidate graph
     */
    public boolean isGuestInlining() {
        return guestInlining;
    }

    /**
     * Returns the guest call target represented by this graph.
     *
     * @return the graph's guest call target
     */
    public TruffleCompilable getCompilable() {
        return compilable;
    }
}
