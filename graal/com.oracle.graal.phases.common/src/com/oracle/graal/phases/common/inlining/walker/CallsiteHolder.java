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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.ToDoubleFunction;

import static com.oracle.graal.compiler.common.GraalOptions.CapInheritedRelevance;

/**
 * Information about a graph that will potentially be inlined. This includes tracking the
 * invocations in graph that will subject to inlining themselves.
 */
public class CallsiteHolder {

    private final StructuredGraph graph;
    private final LinkedList<Invoke> remainingInvokes;
    private final double probability;
    private final double relevance;

    private final ToDoubleFunction<FixedNode> probabilities;
    private final ComputeInliningRelevance computeInliningRelevance;

    public CallsiteHolder(StructuredGraph graph, double probability, double relevance) {
        this.graph = graph;
        if (graph == null) {
            this.remainingInvokes = new LinkedList<>();
        } else {
            LinkedList<Invoke> invokes = new InliningIterator(graph).apply();
            assert invokes.size() == count(graph.getInvokes());
            this.remainingInvokes = invokes;
        }
        this.probability = probability;
        this.relevance = relevance;

        if (graph != null && !remainingInvokes.isEmpty()) {
            probabilities = new FixedNodeProbabilityCache();
            computeInliningRelevance = new ComputeInliningRelevance(graph, probabilities);
            computeProbabilities();
        } else {
            probabilities = null;
            computeInliningRelevance = null;
        }
    }

    private static int count(Iterable<Invoke> invokes) {
        int count = 0;
        Iterator<Invoke> iterator = invokes.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }
        return count;
    }

    /**
     * Gets the method associated with the {@linkplain #graph() graph} represented by this object.
     */
    public ResolvedJavaMethod method() {
        return graph.method();
    }

    public boolean hasRemainingInvokes() {
        return !remainingInvokes.isEmpty();
    }

    /**
     * The graph about which this object contains inlining information.
     */
    public StructuredGraph graph() {
        return graph;
    }

    public Invoke popInvoke() {
        return remainingInvokes.removeFirst();
    }

    public void pushInvoke(Invoke invoke) {
        remainingInvokes.push(invoke);
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
