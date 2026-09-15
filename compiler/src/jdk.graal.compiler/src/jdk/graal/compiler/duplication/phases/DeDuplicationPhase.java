/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.duplication.phases;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.graph.StatelessPostOrderNodeIterator;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.util.CollectionsUtil;

/// Deduplicates compatible fixed and floating nodes on the incoming branches of a control-flow
/// merge by placing a single merged node after the merge. Inputs that differ can be represented
/// by phis, while redundant result phis can be removed.
///
/// For example, the effect can be expressed with the following Java-like pseudocode. Before the
/// optimization, compatible additions and stores occur in both branches:
///
/// ```
/// if (condition) {
///     target.value = left + 1;
/// } else {
///     target.value = right + 1;
/// }
/// ```
///
/// After the optimization, the conditional expression represents a phi at the merge, and only one
/// addition and store remain:
///
/// ```
/// int input = condition ? left : right;
/// target.value = input + 1;
/// ```
public class DeDuplicationPhase extends BasePhase<CoreProviders> {

    public static class Options {

        //@formatter:off
        @Option(help = "Deduplicates statements and expressions before control flow merges if they are equal. " +
                       "This can reduce code size.", type = OptionType.Expert)
        public static final OptionKey<Boolean> OptDeDuplication = new OptionKey<>(true);
        //@formatter:on
    }

    private final CanonicalizerPhase canonicalizer;

    /// Creates a deduplication phase that uses `canonicalizer` for incremental cleanup.
    public DeDuplicationPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(NotApplicable.unlessRunBefore(this, StageFlag.NODE_VECTORIZATION, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState),
                        canonicalizer.notApplicableTo(graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (graph.hasNode(AbstractMergeNode.TYPE)) {
            /*
             * Get the merges in reverse post order - otherwise we would have to visit merges
             * iteratively.
             */
            final ArrayList<MergeNode> queue = new ArrayList<>();
            new StatelessPostOrderNodeIterator(graph.start()) {
                @Override
                protected void merge(AbstractMergeNode merge) {
                    assert merge instanceof MergeNode : merge;
                    queue.add((MergeNode) merge);
                }
            }.apply();

            /*
             * It is tempting to get rid of the ArrayList and do processMerge while iterating, but
             * that would collide with the incremental canonicalization.
             */
            for (MergeNode merge : queue) {
                processMerge(context, merge);
            }
        }
        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            assert GraphOrder.assertSchedulableGraph(graph);
        }
    }

    @SuppressWarnings("try")
    private void processMerge(CoreProviders context, MergeNode merge) {
        boolean progress;
        DebugContext debug = merge.getDebug();
        do {
            if (!merge.isAlive()) {
                return;
            }

            EconomicSetNodeEventListener listener = new EconomicSetNodeEventListener();
            try (NodeEventScope nes = merge.graph().trackNodeEvents(listener)) {
                progress = deduplicateFloatingNodes(merge);
                progress |= deduplicateFixedNodes(merge);
            }

            if (progress && !listener.getNodes().isEmpty()) {
                canonicalizer.applyIncremental(merge.graph(), context, listener.getNodes());
                debug.dump(DebugContext.DETAILED_LEVEL, merge.graph(), "After canonicalization at %s", merge);
            }
        } while (progress);
    }

    @SuppressWarnings("try")
    private static boolean deduplicateFloatingNodes(MergeNode merge) {
        StructuredGraph graph = merge.graph();
        DebugContext debug = graph.getDebug();
        boolean progress = false;
        outer: for (PhiNode phi : merge.phis().snapshot()) {
            ValueNode firstValue = phi.values().get(0);
            Class<?> nodeClass = firstValue == null ? null : firstValue.getClass();

            // only consider floating nodes and check that all predecessors are the same node class
            if (nodeClass == null || !(firstValue instanceof FloatingNode) || firstValue instanceof PhiNode ||
                            CollectionsUtil.anyMatch(phi.values(), node -> node == null || node.getClass() != nodeClass)) {
                debug.log(4, "rejecting %s (%s) at %s because of invalid / non-matching node classes", phi.values().get(0), phi, merge);
                continue;
            }
            // the phi needs to be the only usage for each of its values
            for (Node value : phi.values()) {
                Iterator<Node> iterator = value.usages().iterator();
                assert iterator.hasNext() : "Iterator should have more values " + iterator;
                if (iterator.next() != phi || iterator.hasNext()) {
                    continue outer;
                }
            }

            try (DebugCloseable position = phi.withNodeSourcePosition()) {
                ValueNode merged = NodeMergeProcessor.merge(phi.values().toArray(ValueNode.EMPTY_ARRAY), merge);
                if (merged != null) {
                    progress = true;
                    if (!merged.isAlive()) {
                        merged = graph.addOrUniqueWithInputs(merged);
                    }
                    phi.replaceAtUsages(merged);
                    GraphUtil.killWithUnusedFloatingInputs(phi);
                    graph.getOptimizationLog().report(DeDuplicationPhase.class, "NodeDeduplication", merged);
                }
            }
        }
        return progress;
    }

    @SuppressWarnings("try")
    private static boolean deduplicateFixedNodes(MergeNode merge) {
        StructuredGraph graph = merge.graph();
        DebugContext debug = graph.getDebug();
        boolean progress = false;
        while (true) { // TERMINATION ARGUMENT: guarded by graph shape, deduplicate until exit
                       // condition is met
            CompilationAlarm.checkProgress(graph);
            FixedWithNextNode[] predecessors = CollectionsUtil.mapToArray(merge.forwardEnds(), end -> (FixedWithNextNode) end.predecessor(), FixedWithNextNode[]::new);
            Class<?> nodeClass = predecessors[0].getClass();

            if (merge.stateAfter() != null && merge.graph().isAfterStage(StageFlag.FLOATING_READS) &&
                            (MemoryKill.isMemoryKill(predecessors[0]))) {
                // This could create an unschedulable graph if a read node is used in the frame
                // state of the merge.
                return progress;
            }

            // stop at begin nodes and check that all predecessors are the same node class
            if (predecessors[0] instanceof AbstractBeginNode || CollectionsUtil.anyMatch(predecessors, node -> node.getClass() != nodeClass)) {
                debug.log(4, "rejecting %s at %s because of invalid / non-matching node classes", predecessors[0], merge);
                return progress;
            }
            EconomicSet<PhiNode> phis = EconomicSet.create(Equivalence.IDENTITY);
            for (Node predecessor : predecessors) {
                for (Node usage : predecessor.usages()) {
                    if (merge.isPhiAtMerge(usage)) {
                        phis.add((PhiNode) usage);
                    } else {
                        // non-phi floating usages need to be pulled through the merge first
                        debug.log(4, "rejecting %s at %s because of non-phi usages", predecessors[0], merge);
                        return progress;
                    }
                }
            }
            List<FixedWithNextNode> predecessorList = Arrays.asList(predecessors);
            for (PhiNode phi : phis) {
                if (phi.values().equals(predecessorList)) {
                    if (merge.stateAfter() != null) {
                        // This could create an unschedulable graph if the value is used in the
                        // frame state of the merge.
                        return progress;
                    }
                    // Continue;
                } else {
                    // the cannot be any phis that do not have inputs to all nodes
                    return progress;
                }
            }

            try (DebugCloseable position = merge.withNodeSourcePosition()) {
                FixedWithNextNode merged = NodeMergeProcessor.merge(predecessors, merge);
                if (merged != null) {
                    progress = true;
                    for (FixedWithNextNode fixed : predecessors) {
                        GraphUtil.unlinkFixedNode(fixed);
                    }
                    if (!merged.isAlive()) {
                        merged = graph.addOrUniqueWithInputs(merged);
                    }
                    graph.addAfterFixed(merge, merged);
                    for (PhiNode phi : phis) {
                        phi.replaceAtUsages(merged);
                        GraphUtil.killWithUnusedFloatingInputs(phi);
                    }
                    for (FixedWithNextNode fixed : predecessors) {
                        if (fixed != merged) {
                            GraphUtil.killWithUnusedFloatingInputs(fixed);
                        }
                    }
                    graph.getOptimizationLog().report(DeDuplicationPhase.class, "Deduplication", merged);
                    continue;
                }
            }
            return progress;
        }
    }
}
