/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.phases.common.inlining.walker;

import com.oracle.graal.api.meta.MetaUtil;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.graph.FixedNodeProbabilityCache;

import java.util.LinkedList;
import java.util.function.ToDoubleFunction;

import static com.oracle.graal.compiler.common.GraalOptions.CapInheritedRelevance;

/**
 * <p>
 * A {@link CallsiteHolder} whose graph has been already copied and thus can be modified without
 * affecting the original (usually cached) version.
 * </p>
 *
 * <p>
 * An instance of this class is "explorable" in that any {@link Invoke} nodes it contains are
 * candidates for depth-first search for further inlining opportunities (as realized by
 * {@link InliningData})
 * </p>
 */
public final class CallsiteHolderExplorable extends CallsiteHolder {

    private final StructuredGraph graph;
    private final LinkedList<Invoke> remainingInvokes;
    private final double probability;
    private final double relevance;

    private final ToDoubleFunction<FixedNode> probabilities;
    private final ComputeInliningRelevance computeInliningRelevance;

    public CallsiteHolderExplorable(StructuredGraph graph, double probability, double relevance) {
        assert graph != null;
        this.graph = graph;
        this.probability = probability;
        this.relevance = relevance;
        remainingInvokes = new InliningIterator(graph).apply();
        if (remainingInvokes.isEmpty()) {
            probabilities = null;
            computeInliningRelevance = null;
        } else {
            probabilities = new FixedNodeProbabilityCache();
            computeInliningRelevance = new ComputeInliningRelevance(graph, probabilities);
            computeProbabilities();
        }
    }

    @Override
    public ResolvedJavaMethod method() {
        return graph == null ? null : graph.method();
    }

    @Override
    public boolean hasRemainingInvokes() {
        return !remainingInvokes.isEmpty();
    }

    @Override
    public StructuredGraph graph() {
        return graph;
    }

    public Invoke popInvoke() {
        return remainingInvokes.removeFirst();
    }

    public void pushInvoke(Invoke invoke) {
        remainingInvokes.push(invoke);
    }

    public boolean containsInvoke(Invoke invoke) {
        for (Invoke i : graph().getInvokes()) {
            if (i == invoke) {
                return true;
            }
        }
        return false;
    }

    public void computeProbabilities() {
        computeInliningRelevance.compute();
    }

    public double invokeProbability(Invoke invoke) {
        return probability * probabilities.applyAsDouble(invoke.asNode());
    }

    public double invokeRelevance(Invoke invoke) {
        return Math.min(CapInheritedRelevance.getValue(), relevance) * computeInliningRelevance.getRelevance(invoke);
    }

    @Override
    public String toString() {
        return (graph != null ? MetaUtil.format("%H.%n(%p)", method()) : "<null method>") + remainingInvokes;
    }
}
