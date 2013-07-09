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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.truffle.phases.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.truffle.api.nodes.*;

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

        StructuredGraph resultGraph = null;
        if (cache.containsKey(method)) {
            StructuredGraph graph = cache.get(method);
            if (checkArgumentStamps(graph, arguments)) {
                resultGraph = graph;
            }
        }

        if (resultGraph == null) {
            resultGraph = Debug.sandbox("TruffleCache", new Object[]{metaAccessProvider, method}, DebugScope.getConfig(), new Callable<StructuredGraph>() {

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

                    HighTierContext context = new HighTierContext(metaAccessProvider, new Assumptions(false), replacements);
                    PartialEscapePhase partialEscapePhase = new PartialEscapePhase(false, new CanonicalizerPhase(true));
                    partialEscapePhase.apply(newGraph, context);

                    cache.put(method, newGraph);
                    if (TruffleCompilerOptions.TraceTruffleCacheDetails.getValue()) {
                        TTY.println(String.format("[truffle] added to graph cache method %s with %d nodes.", method, newGraph.getNodeCount()));
                    }
                    return newGraph;
                }
            });
        }

        final StructuredGraph clonedResultGraph = resultGraph.copy();

        Debug.sandbox("TruffleCacheConstants", new Object[]{metaAccessProvider, method}, DebugScope.getConfig(), new Runnable() {

            public void run() {

                Debug.dump(clonedResultGraph, "before applying constants");
                // Pass on constant arguments.
                for (LocalNode local : clonedResultGraph.getNodes(LocalNode.class)) {
                    ValueNode arg = arguments.get(local.index());
                    if (arg.isConstant()) {
                        Constant constant = arg.asConstant();
                        local.replaceAndDelete(ConstantNode.forConstant(constant, metaAccessProvider, clonedResultGraph));
                    } else {
                        local.setStamp(arg.stamp());
                    }
                }
                Debug.dump(clonedResultGraph, "after applying constants");
                optimizeGraph(clonedResultGraph);
            }
        });
        return clonedResultGraph;
    }

    private void optimizeGraph(StructuredGraph newGraph) {

        ConditionalEliminationPhase eliminate = new ConditionalEliminationPhase(metaAccessProvider);
        ConvertDeoptimizeToGuardPhase convertDeoptimizeToGuardPhase = new ConvertDeoptimizeToGuardPhase();

        Assumptions assumptions = new Assumptions(false);
        CanonicalizerPhase.Instance canonicalizerPhase = new CanonicalizerPhase.Instance(metaAccessProvider, assumptions, !AOTCompilation.getValue(), null, null);

        Integer maxNodes = TruffleCompilerOptions.TruffleOperationCacheMaxNodes.getValue();

        contractGraph(newGraph, eliminate, convertDeoptimizeToGuardPhase, canonicalizerPhase);

        while (newGraph.getNodeCount() <= maxNodes) {

            int mark = newGraph.getMark();

            expandGraph(newGraph, maxNodes);

            if (newGraph.getNewNodes(mark).count() == 0) {
                // No progress => exit iterative optimization.
                break;
            }

            contractGraph(newGraph, eliminate, convertDeoptimizeToGuardPhase, canonicalizerPhase);
        }

        if (newGraph.getNodeCount() > maxNodes && (TruffleCompilerOptions.TraceTruffleCacheDetails.getValue() || TruffleCompilerOptions.TraceTrufflePerformanceWarnings.getValue())) {
            TTY.println(String.format("[truffle] PERFORMANCE WARNING: method %s got too large with %d nodes.", newGraph.method(), newGraph.getNodeCount()));
        }
    }

    private static void contractGraph(StructuredGraph newGraph, ConditionalEliminationPhase eliminate, ConvertDeoptimizeToGuardPhase convertDeoptimizeToGuardPhase,
                    CanonicalizerPhase.Instance canonicalizerPhase) {
        // Canonicalize / constant propagate.
        canonicalizerPhase.apply(newGraph);

        // Convert deopt to guards.
        convertDeoptimizeToGuardPhase.apply(newGraph);

        // Conditional elimination.
        eliminate.apply(newGraph);
    }

    private void expandGraph(StructuredGraph newGraph, int maxNodes) {
        NodeBitMap visitedNodes = newGraph.createNodeBitMap(true);
        Queue<AbstractBeginNode> workQueue = new LinkedList<>();
        workQueue.add(newGraph.start());

        while (!workQueue.isEmpty() && newGraph.getNodeCount() <= maxNodes) {
            AbstractBeginNode start = workQueue.poll();
            expandPath(newGraph, maxNodes, visitedNodes, start, workQueue);
        }
    }

    private void expandPath(StructuredGraph newGraph, int maxNodes, NodeBitMap visitedNodes, AbstractBeginNode start, Queue<AbstractBeginNode> workQueue) {
        FixedNode next = start;
        while (!visitedNodes.isMarked(next)) {
            visitedNodes.mark(next);
            if (next instanceof Invoke) {
                Invoke invoke = (Invoke) next;
                next = expandInvoke(invoke);
                if (newGraph.getNodeCount() > maxNodes) {
                    return;
                }
            }

            if (next instanceof ControlSplitNode) {
                ControlSplitNode controlSplitNode = (ControlSplitNode) next;
                AbstractBeginNode maxProbNode = null;
                for (Node succ : controlSplitNode.cfgSuccessors()) {
                    AbstractBeginNode successor = (AbstractBeginNode) succ;
                    if (maxProbNode == null || controlSplitNode.probability(successor) > controlSplitNode.probability(maxProbNode)) {
                        maxProbNode = successor;
                    }
                }
                for (Node succ : controlSplitNode.cfgSuccessors()) {
                    AbstractBeginNode successor = (AbstractBeginNode) succ;
                    if (successor != maxProbNode) {
                        workQueue.add(successor);
                    }
                }
                next = maxProbNode;
            } else if (next instanceof EndNode) {
                EndNode endNode = (EndNode) next;
                next = endNode.merge();
            } else if (next instanceof ControlSinkNode) {
                return;
            } else if (next instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) next;
                next = fixedWithNextNode.next();
            }
        }
    }

    private FixedNode expandInvoke(Invoke invoke) {
        if (invoke.callTarget() instanceof MethodCallTargetNode) {
            final MethodCallTargetNode methodCallTargetNode = (MethodCallTargetNode) invoke.callTarget();
            if ((methodCallTargetNode.invokeKind() == InvokeKind.Special || methodCallTargetNode.invokeKind() == InvokeKind.Static) &&
                            !Modifier.isNative(methodCallTargetNode.targetMethod().getModifiers()) && methodCallTargetNode.targetMethod().getAnnotation(ExplodeLoop.class) == null) {
                Class<? extends FixedWithNextNode> macroSubstitution = replacements.getMacroSubstitution(methodCallTargetNode.targetMethod());
                if (macroSubstitution != null) {
                    return InliningUtil.inlineMacroNode(invoke, methodCallTargetNode.targetMethod(), methodCallTargetNode.graph(), macroSubstitution);
                } else {
                    StructuredGraph inlinedGraph = Debug.scope("ExpandInvoke", methodCallTargetNode.targetMethod(), new Callable<StructuredGraph>() {

                        public StructuredGraph call() {
                            StructuredGraph inlineGraph = replacements.getMethodSubstitution(methodCallTargetNode.targetMethod());
                            if (inlineGraph == null) {
                                inlineGraph = parseGraph(methodCallTargetNode.targetMethod());
                            }
                            return inlineGraph;
                        }
                    });
                    FixedNode fixedNode = (FixedNode) invoke.predecessor();
                    InliningUtil.inline(invoke, inlinedGraph, true);
                    return fixedNode;
                }
            }
        }
        return invoke.asNode();
    }

    private StructuredGraph parseGraph(ResolvedJavaMethod method) {
        StructuredGraph graph = new StructuredGraph(method);
        new GraphBuilderPhase(metaAccessProvider, config, optimisticOptimizations).apply(graph);
        // Intrinsify methods.
        new ReplaceIntrinsicsPhase(replacements).apply(graph);
        return graph;
    }

    private static boolean checkArgumentStamps(StructuredGraph graph, NodeInputList<ValueNode> arguments) {
        assert graph.getNodes(LocalNode.class).count() == arguments.count();
        for (LocalNode localNode : graph.getNodes(LocalNode.class)) {
            Stamp newStamp = localNode.stamp().meet(arguments.get(localNode.index()).stamp());
            if (!newStamp.equals(localNode.stamp())) {
                if (TruffleCompilerOptions.TraceTruffleCacheDetails.getValue()) {
                    TTY.println(String.format("[truffle] graph cache entry too specific for method %s argument %s previous stamp %s new stamp %s.", graph.method(), localNode, localNode.stamp(),
                                    newStamp));
                }
                return false;
            }
        }

        return true;
    }
}
