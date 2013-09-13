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

import static com.oracle.graal.compiler.GraalDebugConfig.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.truffle.nodes.asserts.*;
import com.oracle.graal.truffle.nodes.frame.*;
import com.oracle.graal.truffle.nodes.frame.NewFrameNode.VirtualOnlyInstanceNode;
import com.oracle.graal.truffle.phases.*;
import com.oracle.graal.truffle.printer.*;
import com.oracle.graal.truffle.printer.method.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public class PartialEvaluator {

    private final MetaAccessProvider metaAccessProvider;
    private final ResolvedJavaMethod executeHelperMethod;
    private final CanonicalizerPhase canonicalizer;
    private final ResolvedJavaType[] skippedExceptionTypes;
    private final Replacements replacements;
    private Set<Constant> constantReceivers;
    private final HotSpotGraphCache cache;
    private final TruffleCache truffleCache;

    public PartialEvaluator(MetaAccessProvider metaAccessProvider, Replacements replacements, TruffleCache truffleCache) {
        this.metaAccessProvider = metaAccessProvider;
        CustomCanonicalizer customCanonicalizer = new PartialEvaluatorCanonicalizer(metaAccessProvider);
        this.canonicalizer = new CanonicalizerPhase(!AOTCompilation.getValue(), customCanonicalizer);
        this.skippedExceptionTypes = TruffleCompilerImpl.getSkippedExceptionTypes(metaAccessProvider);
        this.replacements = replacements;
        this.cache = HotSpotGraalRuntime.graalRuntime().getCache();
        this.truffleCache = truffleCache;

        try {
            executeHelperMethod = metaAccessProvider.lookupJavaMethod(OptimizedCallTarget.class.getDeclaredMethod("executeHelper", PackedFrame.class, Arguments.class));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    public StructuredGraph createGraph(final OptimizedCallTarget node, final Assumptions assumptions) {
        if (Dump.getValue() != null && Dump.getValue().contains("Truffle")) {
            RootNode root = node.getRootNode();
            if (root != null) {
                new GraphPrintVisitor().beginGroup("TruffleGraph").beginGraph(node.toString()).visit(root).printToNetwork();
            }
        }

        if (TraceTruffleCompilationDetails.getValue()) {
            constantReceivers = new HashSet<>();
        }

        final GraphBuilderConfiguration config = GraphBuilderConfiguration.getDefault();
        config.setSkippedExceptionTypes(skippedExceptionTypes);

        final StructuredGraph graph = new StructuredGraph(executeHelperMethod);

        if (TruffleInlinePrinter.getValue()) {
            InlinePrinterProcessor.initialize();
        }

        Debug.scope("createGraph", graph, new Runnable() {

            @SuppressWarnings("deprecation")
            @Override
            public void run() {
                new GraphBuilderPhase(metaAccessProvider, config, TruffleCompilerImpl.Optimizations).apply(graph);

                // Replace thisNode with constant.
                LocalNode thisNode = graph.getLocal(0);
                thisNode.replaceAndDelete(ConstantNode.forObject(node, metaAccessProvider, graph));

                // Canonicalize / constant propagate.
                PhaseContext baseContext = new PhaseContext(metaAccessProvider, assumptions, replacements);
                canonicalizer.apply(graph, baseContext);

                // Intrinsify methods.
                new ReplaceIntrinsicsPhase(replacements).apply(graph);

                NewFrameNode newFrameNode = graph.getNodes(NewFrameNode.class).first();
                if (newFrameNode == null) {
                    throw GraalInternalError.shouldNotReachHere("frame not found");
                }

                Debug.dump(graph, "Before inlining");

                // Make sure frame does not escape.
                expandTree(config, graph, newFrameNode, assumptions);

                if (TruffleInlinePrinter.getValue()) {
                    InlinePrinterProcessor.printTree();
                    InlinePrinterProcessor.reset();
                }

                if (TraceTruffleCompilationDetails.getValue() && constantReceivers != null) {
                    DebugHistogram histogram = Debug.createHistogram("Expanded Truffle Nodes");
                    for (Constant c : constantReceivers) {
                        histogram.add(c.asObject().getClass().getSimpleName());
                    }
                    new DebugHistogramAsciiPrinter(TTY.out().out()).print(histogram);
                }

                // Additional inlining.
                final PhasePlan plan = new PhasePlan();
                GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(metaAccessProvider, config, TruffleCompilerImpl.Optimizations);
                plan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
                canonicalizer.addToPhasePlan(plan, baseContext);
                plan.addPhase(PhasePosition.AFTER_PARSING, new ReplaceIntrinsicsPhase(replacements));

                new ConvertDeoptimizeToGuardPhase().apply(graph);
                canonicalizer.apply(graph, baseContext);
                new DeadCodeEliminationPhase().apply(graph);

                HighTierContext context = new HighTierContext(metaAccessProvider, assumptions, replacements, cache, plan, OptimisticOptimizations.NONE);
                InliningPhase inliningPhase = new InliningPhase(canonicalizer);
                inliningPhase.apply(graph, context);

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                // Canonicalize / constant propagate.
                canonicalizer.apply(graph, context);

                for (NeverPartOfCompilationNode neverPartOfCompilationNode : graph.getNodes(NeverPartOfCompilationNode.class)) {
                    Throwable exception = new VerificationError(neverPartOfCompilationNode.getMessage());
                    throw GraphUtil.approxSourceException(neverPartOfCompilationNode, exception);
                }

                // EA frame and clean up.
                new VerifyFrameDoesNotEscapePhase().apply(graph, false);
                new PartialEscapePhase(false, canonicalizer).apply(graph, context);
                new VerifyNoIntrinsicsLeftPhase().apply(graph, false);
                for (MaterializeFrameNode materializeNode : graph.getNodes(MaterializeFrameNode.class).snapshot()) {
                    materializeNode.replaceAtUsages(materializeNode.getFrame());
                    graph.removeFixed(materializeNode);
                }
                for (VirtualObjectNode virtualObjectNode : graph.getNodes(VirtualObjectNode.class)) {
                    if (virtualObjectNode instanceof VirtualOnlyInstanceNode) {
                        VirtualOnlyInstanceNode virtualOnlyInstanceNode = (VirtualOnlyInstanceNode) virtualObjectNode;
                        virtualOnlyInstanceNode.setAllowMaterialization(true);
                    } else if (virtualObjectNode instanceof VirtualInstanceNode) {
                        VirtualInstanceNode virtualInstanceNode = (VirtualInstanceNode) virtualObjectNode;
                        ResolvedJavaType type = virtualInstanceNode.type();
                        if (type.getAnnotation(CompilerDirectives.ValueType.class) != null) {
                            virtualInstanceNode.setIdentity(false);
                        }
                    }
                }

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                // Canonicalize / constant propagate.
                canonicalizer.apply(graph, context);
            }
        });

        return graph;
    }

    private void expandTree(GraphBuilderConfiguration config, StructuredGraph graph, NewFrameNode newFrameNode, Assumptions assumptions) {
        boolean changed;
        do {
            changed = false;
            for (Node usage : newFrameNode.usages().snapshot()) {
                if (usage instanceof MethodCallTargetNode && !usage.isDeleted()) {
                    MethodCallTargetNode methodCallTargetNode = (MethodCallTargetNode) usage;
                    InvokeKind kind = methodCallTargetNode.invokeKind();
                    if (kind == InvokeKind.Special || kind == InvokeKind.Static) {
                        if (TruffleInlinePrinter.getValue()) {
                            InlinePrinterProcessor.addInlining(MethodHolder.getNewTruffleExecuteMethod(methodCallTargetNode));
                        }
                        if (TraceTruffleCompilationDetails.getValue() && kind == InvokeKind.Special && methodCallTargetNode.arguments().first() instanceof ConstantNode) {
                            ConstantNode constantNode = (ConstantNode) methodCallTargetNode.arguments().first();
                            constantReceivers.add(constantNode.asConstant());
                        }
                        StructuredGraph inlineGraph = replacements.getMethodSubstitution(methodCallTargetNode.targetMethod());
                        NewFrameNode otherNewFrame = null;
                        if (inlineGraph == null) {
                            inlineGraph = parseGraph(methodCallTargetNode.targetMethod(), methodCallTargetNode.arguments(), assumptions);
                            otherNewFrame = inlineGraph.getNodes(NewFrameNode.class).first();
                        }

                        int nodeCountBefore = graph.getNodeCount();
                        Map<Node, Node> mapping = InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, false);
                        if (Debug.isDumpEnabled()) {
                            int nodeCountAfter = graph.getNodeCount();
                            Debug.dump(graph, "After inlining %s %+d (%d)", methodCallTargetNode.targetMethod().toString(), nodeCountAfter - nodeCountBefore, nodeCountAfter);
                        }
                        changed = true;

                        if (otherNewFrame != null) {
                            otherNewFrame = (NewFrameNode) mapping.get(otherNewFrame);
                            if (otherNewFrame.isAlive() && otherNewFrame.usages().isNotEmpty()) {
                                expandTree(config, graph, otherNewFrame, assumptions);
                            }
                        }
                    }
                }

                if (graph.getNodeCount() > TruffleCompilerOptions.TruffleGraphMaxNodes.getValue()) {
                    throw new BailoutException("Truffle compilation is exceeding maximum node count: " + graph.getNodeCount());
                }
            }
        } while (changed && newFrameNode.isAlive() && newFrameNode.usages().isNotEmpty());
    }

    private StructuredGraph parseGraph(final ResolvedJavaMethod targetMethod, final NodeInputList<ValueNode> arguments, final Assumptions assumptions) {

        final StructuredGraph graph = truffleCache.lookup(targetMethod, arguments, assumptions);
        Debug.scope("parseGraph", targetMethod, new Runnable() {

            @Override
            public void run() {

                // Canonicalize / constant propagate.
                PhaseContext context = new PhaseContext(metaAccessProvider, assumptions, replacements);
                canonicalizer.apply(graph, context);

                // Intrinsify methods.
                new ReplaceIntrinsicsPhase(replacements).apply(graph);

                // Inline trivial getter methods
                new InlineTrivialGettersPhase(metaAccessProvider, canonicalizer).apply(graph, context);

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                if (graph.hasLoops()) {
                    boolean unrolled;
                    do {
                        unrolled = false;
                        LoopsData loopsData = new LoopsData(graph);
                        loopsData.detectedCountedLoops();
                        for (LoopEx ex : innerLoopsFirst(loopsData.countedLoops())) {
                            if (ex.counted().isConstantMaxTripCount()) {
                                long constant = ex.counted().constantMaxTripCount();
                                if (constant <= TruffleConstantUnrollLimit.getValue() || targetMethod.getAnnotation(ExplodeLoop.class) != null) {
                                    LoopTransformations.fullUnroll(ex, context, canonicalizer);
                                    Debug.dump(graph, "After loop unrolling %d times", constant);
                                    canonicalizer.apply(graph, context);
                                    unrolled = true;
                                    break;
                                }
                            }
                        }
                    } while (unrolled);
                }
            }

            private List<LoopEx> innerLoopsFirst(Collection<LoopEx> loops) {
                ArrayList<LoopEx> sortedLoops = new ArrayList<>(loops);
                Collections.sort(sortedLoops, new Comparator<LoopEx>() {

                    @Override
                    public int compare(LoopEx o1, LoopEx o2) {
                        return o2.lirLoop().depth - o1.lirLoop().depth;
                    }
                });
                return sortedLoops;
            }
        });

        return graph;
    }
}
