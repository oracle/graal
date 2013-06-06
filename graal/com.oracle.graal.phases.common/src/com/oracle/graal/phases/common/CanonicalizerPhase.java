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
package com.oracle.graal.phases.common;

import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.NodeChangedListener;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class CanonicalizerPhase extends BasePhase<PhaseContext> {

    private static final int MAX_ITERATION_PER_NODE = 10;
    private static final DebugMetric METRIC_CANONICALIZED_NODES = Debug.metric("CanonicalizedNodes");
    private static final DebugMetric METRIC_CANONICALIZATION_CONSIDERED_NODES = Debug.metric("CanonicalizationConsideredNodes");
    private static final DebugMetric METRIC_INFER_STAMP_CALLED = Debug.metric("InferStampCalled");
    private static final DebugMetric METRIC_STAMP_CHANGED = Debug.metric("StampChanged");
    private static final DebugMetric METRIC_SIMPLIFICATION_CONSIDERED_NODES = Debug.metric("SimplificationConsideredNodes");
    public static final DebugMetric METRIC_GLOBAL_VALUE_NUMBERING_HITS = Debug.metric("GlobalValueNumberingHits");

    private final boolean canonicalizeReads;

    public interface CustomCanonicalizer {

        ValueNode canonicalize(ValueNode node);
    }

    public CanonicalizerPhase(boolean canonicalizeReads) {
        this.canonicalizeReads = canonicalizeReads;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        new Instance(context.getRuntime(), context.getAssumptions(), canonicalizeReads).run(graph);
    }

    public static class Instance extends Phase {

        private final int newNodesMark;
        private final Assumptions assumptions;
        private final MetaAccessProvider runtime;
        private final CustomCanonicalizer customCanonicalizer;
        private final Iterable<Node> initWorkingSet;
        private final boolean canonicalizeReads;

        private NodeWorkList workList;
        private Tool tool;

        public Instance(MetaAccessProvider runtime, Assumptions assumptions, boolean canonicalizeReads) {
            this(runtime, assumptions, canonicalizeReads, null, 0, null);
        }

        /**
         * @param runtime
         * @param assumptions
         * @param workingSet the initial working set of nodes on which the canonicalizer works,
         *            should be an auto-grow node bitmap
         * @param customCanonicalizer
         */
        public Instance(MetaAccessProvider runtime, Assumptions assumptions, boolean canonicalizeReads, Iterable<Node> workingSet, CustomCanonicalizer customCanonicalizer) {
            this(runtime, assumptions, canonicalizeReads, workingSet, 0, customCanonicalizer);
        }

        /**
         * @param newNodesMark only the {@linkplain Graph#getNewNodes(int) new nodes} specified by
         *            this mark are processed otherwise all nodes in the graph are processed
         */
        public Instance(MetaAccessProvider runtime, Assumptions assumptions, boolean canonicalizeReads, int newNodesMark, CustomCanonicalizer customCanonicalizer) {
            this(runtime, assumptions, canonicalizeReads, null, newNodesMark, customCanonicalizer);
        }

        public Instance(MetaAccessProvider runtime, Assumptions assumptions, boolean canonicalizeReads, Iterable<Node> workingSet, int newNodesMark, CustomCanonicalizer customCanonicalizer) {
            super("Canonicalizer");
            this.newNodesMark = newNodesMark;
            this.assumptions = assumptions;
            this.runtime = runtime;
            this.canonicalizeReads = canonicalizeReads;
            this.customCanonicalizer = customCanonicalizer;
            this.initWorkingSet = workingSet;
        }

        @Override
        protected void run(StructuredGraph graph) {
            if (initWorkingSet == null) {
                workList = graph.createNodeWorkList(newNodesMark == 0, MAX_ITERATION_PER_NODE);
            } else {
                workList = graph.createNodeWorkList(false, MAX_ITERATION_PER_NODE);
                workList.addAll(initWorkingSet);
            }
            if (newNodesMark > 0) {
                workList.addAll(graph.getNewNodes(newNodesMark));
            }
            tool = new Tool(workList, runtime, assumptions, canonicalizeReads);
            processWorkSet(graph);
        }

        private void processWorkSet(StructuredGraph graph) {
            NodeChangedListener nodeChangedListener = new NodeChangedListener() {

                @Override
                public void nodeChanged(Node node) {
                    workList.addAgain(node);
                }
            };
            graph.trackInputChange(nodeChangedListener);
            graph.trackUsagesDroppedZero(nodeChangedListener);

            for (Node n : workList) {
                processNode(n, graph);
            }

            graph.stopTrackingInputChange();
            graph.stopTrackingUsagesDroppedZero();
        }

        private void processNode(Node node, StructuredGraph graph) {
            if (node.isAlive()) {
                METRIC_PROCESSED_NODES.increment();

                if (tryGlobalValueNumbering(node, graph)) {
                    return;
                }
                int mark = graph.getMark();
                if (!tryKillUnused(node)) {
                    if (!tryCanonicalize(node, graph)) {
                        tryInferStamp(node, graph);
                    }
                }

                for (Node newNode : graph.getNewNodes(mark)) {
                    workList.add(newNode);
                }
            }
        }

        private static boolean tryKillUnused(Node node) {
            if (node.isAlive() && GraphUtil.isFloatingNode().apply(node) && node.usages().isEmpty()) {
                GraphUtil.killWithUnusedFloatingInputs(node);
                return true;
            }
            return false;
        }

        public static boolean tryGlobalValueNumbering(Node node, StructuredGraph graph) {
            if (node.getNodeClass().valueNumberable()) {
                Node newNode = graph.findDuplicate(node);
                if (newNode != null) {
                    assert !(node instanceof FixedNode || newNode instanceof FixedNode);
                    node.replaceAtUsages(newNode);
                    node.safeDelete();
                    METRIC_GLOBAL_VALUE_NUMBERING_HITS.increment();
                    Debug.log("GVN applied and new node is %1s", newNode);
                    return true;
                }
            }
            return false;
        }

        public boolean tryCanonicalize(final Node node, final StructuredGraph graph) {
            boolean result = baseTryCanonicalize(node, graph);
            if (!result && customCanonicalizer != null && node instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) node;
                ValueNode canonical = customCanonicalizer.canonicalize(valueNode);
                result = performReplacement(node, graph, canonical);
            }
            return result;
        }

        public boolean baseTryCanonicalize(final Node node, final StructuredGraph graph) {
            if (node instanceof Canonicalizable) {
                assert !(node instanceof Simplifiable);
                METRIC_CANONICALIZATION_CONSIDERED_NODES.increment();
                return Debug.scope("CanonicalizeNode", node, new Callable<Boolean>() {

                    public Boolean call() {
                        ValueNode canonical = ((Canonicalizable) node).canonical(tool);
                        return performReplacement(node, graph, canonical);
                    }
                });
            } else if (node instanceof Simplifiable) {
                Debug.log("Canonicalizer: simplifying %s", node);
                METRIC_SIMPLIFICATION_CONSIDERED_NODES.increment();
                Debug.scope("SimplifyNode", node, new Runnable() {

                    public void run() {
                        ((Simplifiable) node).simplify(tool);
                    }
                });
            }
            return node.isDeleted();
        }

// @formatter:off
//     cases:                                           original node:
//                                         |Floating|Fixed-unconnected|Fixed-connected|
//                                         --------------------------------------------
//                                     null|   1    |        X        |       3       |
//                                         --------------------------------------------
//                                 Floating|   2    |        X        |       4       |
//       canonical node:                   --------------------------------------------
//                        Fixed-unconnected|   X    |        X        |       5       |
//                                         --------------------------------------------
//                          Fixed-connected|   2    |        X        |       6       |
//                                         --------------------------------------------
//       X: must not happen (checked with assertions)
// @formatter:on
        private boolean performReplacement(final Node node, final StructuredGraph graph, ValueNode canonical) {
            if (canonical == node) {
                Debug.log("Canonicalizer: work on %s", node);
                return false;
            } else {
                Debug.log("Canonicalizer: replacing %s with %s", node, canonical);
                METRIC_CANONICALIZED_NODES.increment();
                if (node instanceof FloatingNode) {
                    if (canonical == null) {
                        // case 1
                        graph.removeFloating((FloatingNode) node);
                    } else {
                        // case 2
                        assert !(canonical instanceof FixedNode) || (canonical.predecessor() != null || canonical instanceof StartNode || canonical instanceof MergeNode) : node + " -> " + canonical +
                                        " : replacement should be floating or fixed and connected";
                        graph.replaceFloating((FloatingNode) node, canonical);
                    }
                } else {
                    assert node instanceof FixedWithNextNode && node.predecessor() != null : node + " -> " + canonical + " : node should be fixed & connected (" + node.predecessor() + ")";
                    FixedWithNextNode fixedWithNext = (FixedWithNextNode) node;

                    // When removing a fixed node, new canonicalization opportunities for its
// successor
                    // may arise
                    assert fixedWithNext.next() != null;
                    tool.addToWorkList(fixedWithNext.next());

                    if (canonical == null) {
                        // case 3
                        graph.removeFixed(fixedWithNext);
                    } else if (canonical instanceof FloatingNode) {
                        // case 4
                        graph.replaceFixedWithFloating(fixedWithNext, (FloatingNode) canonical);
                    } else {
                        assert canonical instanceof FixedNode;
                        if (canonical.predecessor() == null) {
                            assert !canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " shouldn't have successors";
                            // case 5
                            graph.replaceFixedWithFixed(fixedWithNext, (FixedWithNextNode) canonical);
                        } else {
                            assert canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " should have successors";
                            // case 6
                            node.replaceAtUsages(canonical);
                            graph.removeFixed(fixedWithNext);
                        }
                    }
                }
                return true;
            }
        }

        /**
         * Calls {@link ValueNode#inferStamp()} on the node and, if it returns true (which means
         * that the stamp has changed), re-queues the node's usages . If the stamp has changed then
         * this method also checks if the stamp now describes a constant integer value, in which
         * case the node is replaced with a constant.
         */
        private void tryInferStamp(Node node, StructuredGraph graph) {
            if (node.isAlive() && node instanceof ValueNode) {
                ValueNode valueNode = (ValueNode) node;
                METRIC_INFER_STAMP_CALLED.increment();
                if (valueNode.inferStamp()) {
                    METRIC_STAMP_CHANGED.increment();
                    if (valueNode.stamp() instanceof IntegerStamp && valueNode.integerStamp().lowerBound() == valueNode.integerStamp().upperBound()) {
                        ValueNode replacement = ConstantNode.forIntegerKind(valueNode.kind(), valueNode.integerStamp().lowerBound(), graph);
                        Debug.log("Canonicalizer: replacing %s with %s (inferStamp)", valueNode, replacement);
                        valueNode.replaceAtUsages(replacement);
                    } else {
                        for (Node usage : valueNode.usages()) {
                            workList.addAgain(usage);
                        }
                    }
                }
            }
        }

        private static final class Tool implements SimplifierTool {

            private final NodeWorkList nodeWorkSet;
            private final MetaAccessProvider runtime;
            private final Assumptions assumptions;
            private final boolean canonicalizeReads;

            public Tool(NodeWorkList nodeWorkSet, MetaAccessProvider runtime, Assumptions assumptions, boolean canonicalizeReads) {
                this.nodeWorkSet = nodeWorkSet;
                this.runtime = runtime;
                this.assumptions = assumptions;
                this.canonicalizeReads = canonicalizeReads;
            }

            @Override
            public void deleteBranch(FixedNode branch) {
                branch.predecessor().replaceFirstSuccessor(branch, null);
                GraphUtil.killCFG(branch);
            }

            /**
             * @return an object that can be used for recording assumptions or {@code null} if
             *         assumptions are not allowed in the current context.
             */
            @Override
            public Assumptions assumptions() {
                return assumptions;
            }

            @Override
            public MetaAccessProvider runtime() {
                return runtime;
            }

            @Override
            public void addToWorkList(Node node) {
                nodeWorkSet.addAgain(node);
            }

            @Override
            public void removeIfUnused(Node node) {
                tryKillUnused(node);
            }

            @Override
            public boolean canonicalizeReads() {
                return canonicalizeReads;
            }
        }
    }
}
