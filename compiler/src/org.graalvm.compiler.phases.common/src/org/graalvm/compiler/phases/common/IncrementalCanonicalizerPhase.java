/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common;

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.util.EconomicSetNodeEventListener;

/**
 * A phase suite that applies {@linkplain CanonicalizerPhase canonicalization} to a graph after all
 * phases in the suite have been applied if any of the phases changed the graph.
 */
public abstract class IncrementalCanonicalizerPhase<C extends CoreProviders> extends BasePhase<C> {

    protected final CanonicalizerPhase canonicalizer;

    public IncrementalCanonicalizerPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public static class ApplyIncremental implements DebugCloseable {
        private final EconomicSetNodeEventListener listener;
        private final StructuredGraph graph;
        private final CoreProviders context;
        private final CanonicalizerPhase canonicalizer;
        private final Graph.NodeEventScope scope;

        public ApplyIncremental(StructuredGraph graph, CoreProviders context, CanonicalizerPhase canonicalizer) {
            assert canonicalizer != null;
            this.graph = graph;
            this.context = context;
            this.canonicalizer = canonicalizer;
            this.listener = new EconomicSetNodeEventListener();
            scope = graph.trackNodeEvents(listener);
        }

        @Override
        public void close() {
            scope.close();
            if (!listener.getNodes().isEmpty()) {
                canonicalizer.applyIncremental(graph, context, listener.getNodes(), null, false);
            }
        }
    }

    @Override
    protected DebugCloseable beforeApply(StructuredGraph graph, C context) {
        if (canonicalizer != null) {
            return new ApplyIncremental(graph, context, canonicalizer);
        }
        return null;
    }
}
