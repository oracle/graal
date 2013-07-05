/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import static com.oracle.graal.phases.GraalOptions.*;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.truffle.phases.*;

/**
 * Implementation of a cache for Truffle graphs for improving partial evaluation time.
 */
public final class TruffleCache {

    private final MetaAccessProvider metaAccessProvider;
    private final GraphBuilderConfiguration config;
    private final OptimisticOptimizations optimisticOptimizations;
    private final Replacements replacements;

    private final HashMap<ResolvedJavaMethod, StructuredGraph> cache = new HashMap<>();

    public TruffleCache(MetaAccessProvider metaAccessProvider, GraphBuilderConfiguration config, OptimisticOptimizations optimisticOptimizations, Replacements replacements) {
        this.metaAccessProvider = metaAccessProvider;
        this.config = config;
        this.optimisticOptimizations = optimisticOptimizations;
        this.replacements = replacements;
    }

    public StructuredGraph lookup(final ResolvedJavaMethod method, final NodeInputList<ValueNode> arguments) {

        if (cache.containsKey(method)) {
            StructuredGraph graph = cache.get(method);
            if (checkArgumentStamps(graph, arguments)) {
                return graph;
            }
        }

        StructuredGraph resultGraph = Debug.sandbox("TruffleCache", new Object[]{metaAccessProvider, method}, DebugScope.getConfig(), new Callable<StructuredGraph>() {

            public StructuredGraph call() {
                StructuredGraph newGraph = parseGraph(method);

                // Get stamps from actual arguments.
                List<Stamp> stamps = new ArrayList<>();
                for (ValueNode arg : arguments) {
                    stamps.add(arg.stamp());
                }

                if (cache.containsKey(method)) {
                    // Make sure stamps are generalized based on previous stamps.
                    StructuredGraph graph = cache.get(method);
                    for (LocalNode localNode : graph.getNodes(LocalNode.class)) {
                        int index = localNode.index();
                        Stamp stamp = stamps.get(index);
                        stamps.set(index, stamp.meet(localNode.stamp()));
                    }
                }

                // Set stamps into graph before optimizing.
                for (LocalNode localNode : newGraph.getNodes(LocalNode.class)) {
                    int index = localNode.index();
                    Stamp stamp = stamps.get(index);
                    localNode.setStamp(stamp);
                }

                optimizeGraph(newGraph);
                cache.put(method, newGraph);
                if (TruffleCompilerOptions.TraceTruffleCompilationDetails.getValue()) {
                    TTY.println(String.format("[truffle] added to graph cache method %s with %d nodes.", method, newGraph.getNodeCount()));
                }
                return newGraph;
            }
        });
        return resultGraph;
    }

    private void optimizeGraph(StructuredGraph newGraph) {
        // Canonicalize / constant propagate.
        Assumptions assumptions = new Assumptions(false);
        CanonicalizerPhase.Instance canonicalizerPhase = new CanonicalizerPhase.Instance(metaAccessProvider, assumptions, !AOTCompilation.getValue(), null, null);
        canonicalizerPhase.apply(newGraph);

        // Intrinsify methods.
        new ReplaceIntrinsicsPhase(replacements).apply(newGraph);

        // Convert deopt to guards.
        new ConvertDeoptimizeToGuardPhase().apply(newGraph);
    }

    private StructuredGraph parseGraph(ResolvedJavaMethod method) {
        StructuredGraph graph = new StructuredGraph(method);
        new GraphBuilderPhase(metaAccessProvider, config, optimisticOptimizations).apply(graph);
        return graph;
    }

    private static boolean checkArgumentStamps(StructuredGraph graph, NodeInputList<ValueNode> arguments) {
        assert graph.getNodes(LocalNode.class).count() == arguments.count();
        for (LocalNode localNode : graph.getNodes(LocalNode.class)) {
            Stamp newStamp = localNode.stamp().meet(arguments.get(localNode.index()).stamp());
            if (!newStamp.equals(localNode.stamp())) {
                if (TruffleCompilerOptions.TraceTruffleCompilationDetails.getValue()) {
                    TTY.println(String.format("[truffle] graph cache entry too specific for method %s argument %s previous stamp %s new stamp %s.", graph.method(), localNode, localNode.stamp(),
                                    newStamp));
                }
                return false;
            }
        }

        return true;
    }
}
