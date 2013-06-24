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
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.debug.*;
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
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.truffle.nodes.*;
import com.oracle.graal.truffle.nodes.NewFrameNode.VirtualOnlyInstanceNode;
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
    private final ResolvedJavaType nodeClass;
    private final ResolvedJavaMethod executeHelperMethod;
    private final CustomCanonicalizer customCanonicalizer;
    private final ResolvedJavaType[] skippedExceptionTypes;
    private final Replacements replacements;
    private Set<Constant> constantReceivers;
    private final HotSpotGraphCache cache;

    public PartialEvaluator(GraalCodeCacheProvider runtime, MetaAccessProvider metaAccessProvider) {
        this.metaAccessProvider = metaAccessProvider;
        this.nodeClass = runtime.lookupJavaType(com.oracle.truffle.api.nodes.Node.class);
        this.customCanonicalizer = new PartialEvaluatorCanonicalizer(runtime, nodeClass);
        this.skippedExceptionTypes = TruffleCompilerImpl.getSkippedExceptionTypes(metaAccessProvider);
        this.replacements = Graal.getRequiredCapability(Replacements.class);
        this.cache = HotSpotGraalRuntime.graalRuntime().getCache();

        try {
            executeHelperMethod = runtime.lookupJavaMethod(OptimizedCallTarget.class.getDeclaredMethod("executeHelper", PackedFrame.class, Arguments.class));
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

            @Override
            public void run() {
                new GraphBuilderPhase(metaAccessProvider, config, TruffleCompilerImpl.Optimizations).apply(graph);

                // Replace thisNode with constant.
                LocalNode thisNode = graph.getLocal(0);
                thisNode.replaceAndDelete(ConstantNode.forObject(node, metaAccessProvider, graph));

                // Canonicalize / constant propagate.
                CanonicalizerPhase.Instance canonicalizerPhase = new CanonicalizerPhase.Instance(metaAccessProvider, assumptions, !AOTCompilation.getValue(), null, customCanonicalizer);
                canonicalizerPhase.apply(graph);

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
                    histogram.print(TTY.out().out());
                }

                // Additional inlining.
                final PhasePlan plan = new PhasePlan();
                GraphBuilderPhase graphBuilderPhase = new GraphBuilderPhase(metaAccessProvider, config, TruffleCompilerImpl.Optimizations);
                plan.addPhase(PhasePosition.AFTER_PARSING, graphBuilderPhase);
                plan.addPhase(PhasePosition.AFTER_PARSING, canonicalizerPhase);
                plan.addPhase(PhasePosition.AFTER_PARSING, new ReplaceIntrinsicsPhase(replacements));

                new ConvertDeoptimizeToGuardPhase().apply(graph);
                canonicalizerPhase.apply(graph);
                new DeadCodeEliminationPhase().apply(graph);

                InliningPhase inliningPhase = new InliningPhase(metaAccessProvider, null, replacements, assumptions, cache, plan, OptimisticOptimizations.NONE);
                inliningPhase.setCustomCanonicalizer(customCanonicalizer);
                inliningPhase.apply(graph);

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                // Canonicalize / constant propagate.
                canonicalizerPhase.apply(graph);

                for (NeverPartOfCompilationNode neverPartOfCompilationNode : graph.getNodes(NeverPartOfCompilationNode.class)) {
                    Throwable exception = new VerificationError(neverPartOfCompilationNode.getMessage());
                    throw GraphUtil.approxSourceException(neverPartOfCompilationNode, exception);
                }

                // EA frame and clean up.
                new VerifyFrameDoesNotEscapePhase().apply(graph, false);
                new PartialEscapePhase(false, new CanonicalizerPhase(!AOTCompilation.getValue())).apply(graph, new HighTierContext(metaAccessProvider, assumptions, replacements));
                new VerifyNoIntrinsicsLeftPhase().apply(graph, false);
                for (MaterializeFrameNode materializeNode : graph.getNodes(MaterializeFrameNode.class).snapshot()) {
                    materializeNode.replaceAtUsages(materializeNode.getFrame());
                    graph.removeFixed(materializeNode);
                }
                for (VirtualOnlyInstanceNode virtualOnlyNode : graph.getNodes(VirtualOnlyInstanceNode.class)) {
                    virtualOnlyNode.setAllowMaterialization(true);
                }

                // Convert deopt to guards.
                new ConvertDeoptimizeToGuardPhase().apply(graph);

                // Canonicalize / constant propagate.
                canonicalizerPhase.apply(graph);
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
                            inlineGraph = parseGraph(config, methodCallTargetNode.targetMethod(), methodCallTargetNode.arguments(), assumptions, !AOTCompilation.getValue());
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
            }
        } while (changed && newFrameNode.isAlive() && newFrameNode.usages().isNotEmpty());
    }

    private StructuredGraph parseGraph(final GraphBuilderConfiguration config, final ResolvedJavaMethod targetMethod, final NodeInputList<ValueNode> arguments, final Assumptions assumptions,
                    final boolean canonicalizeReads) {

        final StructuredGraph graph = new StructuredGraph(targetMethod);
        Debug.scope("parseGraph", targetMethod, new Runnable() {

            @Override
            public void run() {
                new GraphBuilderPhase(metaAccessProvider, config, TruffleCompilerImpl.Optimizations).apply(graph);
                // Pass on constant arguments.
                for (LocalNode local : graph.getNodes(LocalNode.class)) {
                    ValueNode arg = arguments.get(local.index());
                    if (arg instanceof NewFrameNode) {
                        local.setStamp(arg.stamp());
                    } else if (arg.isConstant()) {
                        Constant constant = arg.asConstant();
                        local.replaceAndDelete(ConstantNode.forConstant(constant, metaAccessProvider, graph));
                    }
                }

                // Canonicalize / constant propagate.
                new CanonicalizerPhase.Instance(metaAccessProvider, assumptions, canonicalizeReads, null, customCanonicalizer).apply(graph);

                // Intrinsify methods.
                new ReplaceIntrinsicsPhase(replacements).apply(graph);

                // Inline trivial getter methods
                new InlineTrivialGettersPhase(metaAccessProvider, assumptions, customCanonicalizer).apply(graph);

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
                                    LoopTransformations.fullUnroll(ex, metaAccessProvider, assumptions, canonicalizeReads);
                                    Debug.dump(graph, "After loop unrolling %d times", constant);
                                    new CanonicalizerPhase.Instance(metaAccessProvider, assumptions, canonicalizeReads, null, customCanonicalizer).apply(graph);
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
