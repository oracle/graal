/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.phases;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.Phase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.truffle.KnownTruffleTypes;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Early inlining phase for Truffle.
 * <p>
 * This phase inlines calls to methods annotated with {@code @CompilerDirectives.EarlyInline} before
 * the regular inlining phases. It is deliberately conservative and only inlines:
 * <ul>
 * <li>direct calls to eligible target methods,</li>
 * <li>methods explicitly marked with the early-inline annotation,</li>
 * <li>methods that pass standard inlining preconditions.</li>
 * </ul>
 * <p>
 * The goal is to expose simple partial evaluated methods and directive patterns early, so that
 * subsequent optimizations in particular {@code @ExplodeLoop} work on them correctly.
 */
public final class TruffleEarlyInliningPhase extends Phase {

    static class Options {
        //@formatter:off
        @Option(help = "Maximum number of early inlining iterations. Intended as a safeguard against accidental unbounded recursion.",
                        type = OptionType.Debug, stability = OptionStability.EXPERIMENTAL)
        public static final OptionKey<Integer> TruffleEarlyInliningMaxDepth = new OptionKey<>(128);
        //@formatter:on
    }

    private final Providers providers;
    private final KnownTruffleTypes types;
    private final Function<ResolvedJavaMethod, StructuredGraph> lookup;
    protected final CanonicalizerPhase canonicalizer;
    private final int maxDepth;

    public TruffleEarlyInliningPhase(OptionValues options, CanonicalizerPhase canonicalizer, KnownTruffleTypes types, Providers providers, Function<ResolvedJavaMethod, StructuredGraph> lookup) {
        this.types = types;
        this.providers = providers;
        this.lookup = lookup;
        this.canonicalizer = canonicalizer;
        this.maxDepth = Options.TruffleEarlyInliningMaxDepth.getValue(options);
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    @Override
    @SuppressWarnings("try")
    protected void run(StructuredGraph graph) {
        EconomicSet<Node> canonicalizableNodes = EconomicSet.create();
        boolean progress = true;
        int depth = 0;
        while (progress) {
            progress = false;
            if (depth > maxDepth) {
                /*
                 * We intentionally use a simple iteration/depth limit instead of managing an
                 * explicit inlining tree or recursion detection.
                 *
                 * Hitting this limit typically indicates an unintended recursive early-inlining
                 * pattern, or an early-inlined helper that expands into calls that are again
                 * annotated for early inlining without a clear bound.
                 *
                 * If recursion in early-inline methods is a valid use case, this phase needs to be
                 * extended with proper cycle detection or a more precise inlining policy.
                 */
                throw new PermanentBailoutException("Early inlining exceeded the maximum depth of %s for method %s.", maxDepth, graph.method());
            }
            List<Invoke> workList = new ArrayList<>();
            for (Node node : graph.getNodes()) {
                if (!(node instanceof Invoke invoke)) {
                    continue;
                }
                if (shouldInline(invoke)) {
                    workList.add(invoke);
                }
            }
            for (Invoke invoke : workList) {
                if (shouldInline(invoke)) {
                    inlineCall(canonicalizableNodes, graph, invoke);
                    progress = true;
                }
            }

            if (!canonicalizableNodes.isEmpty()) {
                /*
                 * Canonicalize nodes affected by inlining, which can expose additional early
                 * inlining opportunities in the next iteration.
                 */
                canonicalizer.applyIncremental(graph, providers, canonicalizableNodes);
                canonicalizableNodes.clear();
            }
            depth++;
        }
    }

    private void inlineCall(EconomicSet<Node> canonicalizableNodes, StructuredGraph targetGraph, Invoke invoke) {
        inlineGraph(canonicalizableNodes, targetGraph, invoke, lookup.apply(invoke.getTargetMethod()));
    }

    private static void inlineGraph(EconomicSet<Node> canonicalizableNodes, StructuredGraph targetGraph, Invoke invoke,
                    StructuredGraph inlineGraph) {
        ResolvedJavaMethod targetMethod = invoke.getTargetMethod();
        StructuredGraph graph = invoke.asFixedNode().graph();
        assert graph == targetGraph : Assertions.errorMessageContext("call", invoke, "invoke", invoke, "graph", invoke.asFixedNode().graph(), "targetGraph", targetGraph);
        assert inlineGraph.method().equals(targetMethod);
        canonicalizableNodes.addAll(InliningUtil.inlineForCanonicalization(invoke, inlineGraph, true, targetMethod, null, "TruffleEarlyInliningPhase", "TruffleEarlyInliningPhase"));
    }

    /**
     * Determines whether {@code invoke} is a candidate for early inlining.
     */
    private boolean shouldInline(Invoke invoke) {
        if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            return false;
        }
        if (!invoke.isAlive()) {
            return false;
        }
        ResolvedJavaMethod targetMethod = invoke.getTargetMethod();
        if (!shouldInlineTarget(targetMethod)) {
            return false;
        }
        if (!invoke.getInvokeKind().isDirect()) {
            return false;
        }
        Map<ResolvedJavaType, AnnotationValue> declaredAnnotationValues = AnnotationValueSupport.getDeclaredAnnotationValues(targetMethod);
        if (!declaredAnnotationValues.containsKey(types.CompilerDirectives_EarlyInline)) {
            return false;
        }
        String failureMessage = InliningUtil.checkInvokeConditions(invoke);
        if (failureMessage != null) {
            return false;
        }
        return true;
    }

    private static boolean shouldInlineTarget(ResolvedJavaMethod targetMethod) {
        if (targetMethod == null) {
            return false;
        }
        if (!targetMethod.canBeInlined()) {
            return false;
        }
        if (targetMethod.isNative()) {
            return false;
        }

        if (!targetMethod.getDeclaringClass().isInitialized()) {
            return false;
        }
        return true;
    }
}
