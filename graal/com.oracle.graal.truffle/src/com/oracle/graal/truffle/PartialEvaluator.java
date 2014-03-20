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
import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.java.*;
import com.oracle.graal.loop.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.nodes.virtual.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.truffle.nodes.asserts.*;
import com.oracle.graal.truffle.nodes.frame.*;
import com.oracle.graal.truffle.nodes.frame.NewFrameNode.VirtualOnlyInstanceNode;
import com.oracle.graal.truffle.phases.*;
import com.oracle.graal.virtual.phases.ea.*;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * Class performing the partial evaluation starting from the root node of an AST.
 */
public class PartialEvaluator {

    private final Providers providers;
    private final ResolvedJavaMethod executeHelperMethod;
    private final CanonicalizerPhase canonicalizer;
    private final GraphBuilderConfiguration config;
    private Set<Constant> constantReceivers;
    private final TruffleCache truffleCache;
    private final ResolvedJavaType frameType;

    public PartialEvaluator(Providers providers, TruffleCache truffleCache, GraphBuilderConfiguration config) {
        this.providers = providers;
        CustomCanonicalizer customCanonicalizer = new PartialEvaluatorCanonicalizer(providers.getMetaAccess(), providers.getConstantReflection());
        this.canonicalizer = new CanonicalizerPhase(!ImmutableCode.getValue(), customCanonicalizer);
        this.config = config;
        this.truffleCache = truffleCache;
        this.frameType = providers.getMetaAccess().lookupJavaType(FrameWithoutBoxing.class);
        try {
            executeHelperMethod = providers.getMetaAccess().lookupJavaMethod(OptimizedCallTarget.class.getDeclaredMethod("executeHelper", PackedFrame.class, Arguments.class));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    public StructuredGraph createGraph(final OptimizedCallTarget callTarget, final Assumptions assumptions) {
        try (Scope s = Debug.scope("TruffleTree")) {
            Debug.dump(callTarget, "truffle tree");
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        if (TraceTruffleCompilationHistogram.getValue()) {
            constantReceivers = new HashSet<>();
        }

        final StructuredGraph graph = new StructuredGraph(executeHelperMethod);

        try (Scope s = Debug.scope("CreateGraph", graph)) {
            new GraphBuilderPhase.Instance(providers.getMetaAccess(), config, TruffleCompilerImpl.Optimizations).apply(graph);

            // Replace thisNode with constant.
            ParameterNode thisNode = graph.getParameter(0);
            thisNode.replaceAndDelete(ConstantNode.forObject(callTarget, providers.getMetaAccess(), graph));

            // Canonicalize / constant propagate.
            PhaseContext baseContext = new PhaseContext(providers, assumptions);
            canonicalizer.apply(graph, baseContext);

            // Intrinsify methods.
            new ReplaceIntrinsicsPhase(providers.getReplacements()).apply(graph);

            NewFrameNode newFrameNode = graph.getNodes(NewFrameNode.class).first();
            if (newFrameNode == null) {
                throw GraalInternalError.shouldNotReachHere("frame not found");
            }

            Debug.dump(graph, "Before inlining");

            // Make sure frame does not escape.
            expandTree(graph, assumptions);

            if (Thread.interrupted()) {
                return graph;
            }

            new VerifyFrameDoesNotEscapePhase().apply(graph, false);

            if (TraceTruffleCompilationHistogram.getValue() && constantReceivers != null) {
                DebugHistogram histogram = Debug.createHistogram("Expanded Truffle Nodes");
                for (Constant c : constantReceivers) {
                    histogram.add(c.asObject().getClass().getSimpleName());
                }
                new DebugHistogramAsciiPrinter(TTY.out().out()).print(histogram);
            }

            canonicalizer.apply(graph, baseContext);
            Map<ResolvedJavaMethod, StructuredGraph> graphCache = null;
            if (CacheGraphs.getValue()) {
                graphCache = new HashMap<>();
            }
            HighTierContext tierContext = new HighTierContext(providers, assumptions, graphCache, new PhaseSuite<HighTierContext>(), OptimisticOptimizations.NONE);

            for (NeverPartOfCompilationNode neverPartOfCompilationNode : graph.getNodes(NeverPartOfCompilationNode.class)) {
                Throwable exception = new VerificationError(neverPartOfCompilationNode.getMessage());
                throw GraphUtil.approxSourceException(neverPartOfCompilationNode, exception);
            }

            // EA frame and clean up.
            new PartialEscapePhase(true, canonicalizer).apply(graph, tierContext);
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
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        return graph;
    }

    private void expandTree(StructuredGraph graph, Assumptions assumptions) {
        PhaseContext phaseContext = new PhaseContext(providers, assumptions);
        TruffleExpansionLogger expansionLogger = null;
        if (TraceTruffleExpansion.getValue()) {
            expansionLogger = new TruffleExpansionLogger(graph);
        }
        boolean changed;
        do {
            changed = false;
            for (MethodCallTargetNode methodCallTargetNode : graph.getNodes(MethodCallTargetNode.class)) {
                InvokeKind kind = methodCallTargetNode.invokeKind();
                if (kind == InvokeKind.Static || (kind == InvokeKind.Special && (methodCallTargetNode.receiver().isConstant() || isFrame(methodCallTargetNode.receiver())))) {
                    if (TraceTruffleCompilationHistogram.getValue() && kind == InvokeKind.Special) {
                        ConstantNode constantNode = (ConstantNode) methodCallTargetNode.arguments().first();
                        constantReceivers.add(constantNode.asConstant());
                    }
                    Replacements replacements = providers.getReplacements();
                    Class<? extends FixedWithNextNode> macroSubstitution = replacements.getMacroSubstitution(methodCallTargetNode.targetMethod());
                    if (macroSubstitution != null) {
                        InliningUtil.inlineMacroNode(methodCallTargetNode.invoke(), methodCallTargetNode.targetMethod(), macroSubstitution);
                        changed = true;
                        continue;
                    }

                    StructuredGraph inlineGraph = replacements.getMethodSubstitution(methodCallTargetNode.targetMethod());
                    if (inlineGraph == null && !Modifier.isNative(methodCallTargetNode.targetMethod().getModifiers()) &&
                                    methodCallTargetNode.targetMethod().getAnnotation(CompilerDirectives.SlowPath.class) == null) {
                        inlineGraph = parseGraph(methodCallTargetNode.targetMethod(), methodCallTargetNode.arguments(), assumptions, phaseContext);
                    }

                    if (inlineGraph != null) {
                        int nodeCountBefore = graph.getNodeCount();
                        Mark mark = graph.getMark();
                        if (TraceTruffleExpansion.getValue()) {
                            expansionLogger.preExpand(methodCallTargetNode, inlineGraph);
                        }
                        List<Node> invokeUsages = methodCallTargetNode.invoke().asNode().usages().snapshot();
                        Map<Node, Node> inlined = InliningUtil.inline(methodCallTargetNode.invoke(), inlineGraph, false);
                        if (TraceTruffleExpansion.getValue()) {
                            expansionLogger.postExpand(inlined);
                        }
                        if (Debug.isDumpEnabled()) {
                            int nodeCountAfter = graph.getNodeCount();
                            Debug.dump(graph, "After inlining %s %+d (%d)", methodCallTargetNode.targetMethod().toString(), nodeCountAfter - nodeCountBefore, nodeCountAfter);
                        }
                        canonicalizer.applyIncremental(graph, phaseContext, invokeUsages, mark);
                        changed = true;
                    }
                }

                if (Thread.interrupted()) {
                    return;
                }

                if (graph.getNodeCount() > TruffleCompilerOptions.TruffleGraphMaxNodes.getValue()) {
                    throw new BailoutException("Truffle compilation is exceeding maximum node count: " + graph.getNodeCount());
                }
            }
        } while (changed);

        if (TraceTruffleExpansion.getValue()) {
            expansionLogger.print();
        }
    }

    private boolean isFrame(ValueNode receiver) {
        return receiver instanceof NewFrameNode || Objects.equals(ObjectStamp.typeOrNull(receiver.stamp()), frameType);
    }

    private StructuredGraph parseGraph(final ResolvedJavaMethod targetMethod, final NodeInputList<ValueNode> arguments, final Assumptions assumptions, final PhaseContext phaseContext) {

        StructuredGraph graph = truffleCache.lookup(targetMethod, arguments, assumptions, canonicalizer);

        if (targetMethod.getAnnotation(ExplodeLoop.class) != null) {
            assert graph.hasLoops() : graph + " does not contain a loop";
            final StructuredGraph graphCopy = graph.copy();
            final List<Node> modifiedNodes = new ArrayList<>();
            for (ParameterNode param : graphCopy.getNodes(ParameterNode.class)) {
                ValueNode arg = arguments.get(param.index());
                if (arg.isConstant()) {
                    Constant constant = arg.asConstant();
                    for (Node usage : param.usages()) {
                        if (usage instanceof Canonicalizable) {
                            modifiedNodes.add(usage);
                        }
                    }
                    param.replaceAndDelete(ConstantNode.forConstant(constant, phaseContext.getMetaAccess(), graphCopy));
                }
            }
            try (Scope s = Debug.scope("TruffleUnrollLoop", targetMethod)) {

                canonicalizer.applyIncremental(graphCopy, phaseContext, modifiedNodes);
                boolean unrolled;
                do {
                    unrolled = false;
                    LoopsData loopsData = new LoopsData(graphCopy);
                    loopsData.detectedCountedLoops();
                    for (LoopEx ex : innerLoopsFirst(loopsData.countedLoops())) {
                        if (ex.counted().isConstantMaxTripCount()) {
                            long constant = ex.counted().constantMaxTripCount();
                            LoopTransformations.fullUnroll(ex, phaseContext, canonicalizer);
                            Debug.dump(graphCopy, "After loop unrolling %d times", constant);
                            unrolled = true;
                            break;
                        }
                    }
                } while (unrolled);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }

            return graphCopy;
        } else {
            return graph;
        }
    }

    private static List<LoopEx> innerLoopsFirst(Collection<LoopEx> loops) {
        ArrayList<LoopEx> sortedLoops = new ArrayList<>(loops);
        Collections.sort(sortedLoops, new Comparator<LoopEx>() {

            @Override
            public int compare(LoopEx o1, LoopEx o2) {
                return o2.lirLoop().depth - o1.lirLoop().depth;
            }
        });
        return sortedLoops;
    }

}
