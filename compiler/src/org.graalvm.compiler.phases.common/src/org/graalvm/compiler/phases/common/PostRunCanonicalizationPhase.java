/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Optional;

import org.graalvm.compiler.nodes.GraphState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.BasePhase;

/**
 * The base classes for phases that always want to apply {@link CanonicalizerPhase#applyIncremental}
 * to a graph after the phase has been applied if the graph changed.
 */
public abstract class PostRunCanonicalizationPhase<C extends CoreProviders> extends BasePhase<C> {

    protected final CanonicalizerPhase canonicalizer;

    /**
     * Primary constructor for incremental canonicalization. Subclasses must provide a non-null
     * {@link CanonicalizerPhase}
     */
    public PostRunCanonicalizationPhase(CanonicalizerPhase canonicalizer) {
        assert canonicalizer != null;
        this.canonicalizer = canonicalizer;
    }

    @Override
    protected ApplyScope applyScope(StructuredGraph graph, C context) {
        return new IncrementalCanonicalizerPhase.Apply(graph, context, canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return this.canonicalizer.notApplicableTo(graphState);
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        canonicalizer.updateGraphState(graphState);
    }
}
