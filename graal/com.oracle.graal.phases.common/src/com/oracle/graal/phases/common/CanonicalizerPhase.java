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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

public class CanonicalizerPhase extends BasePhase<PhaseContext> {

    private static final int MAX_ITERATION_PER_NODE = 10;
    private static final DebugMetric METRIC_CANONICALIZED_NODES = Debug.metric("CanonicalizedNodes");
    private static final DebugMetric METRIC_PROCESSED_NODES = Debug.metric("ProcessedNodes");
    private static final DebugMetric METRIC_CANONICALIZATION_CONSIDERED_NODES = Debug.metric("CanonicalizationConsideredNodes");
    private static final DebugMetric METRIC_INFER_STAMP_CALLED = Debug.metric("InferStampCalled");
    private static final DebugMetric METRIC_STAMP_CHANGED = Debug.metric("StampChanged");
    private static final DebugMetric METRIC_SIMPLIFICATION_CONSIDERED_NODES = Debug.metric("SimplificationConsideredNodes");
    private static final DebugMetric METRIC_GLOBAL_VALUE_NUMBERING_HITS = Debug.metric("GlobalValueNumberingHits");

    private final boolean canonicalizeReads;
    private final CustomCanonicalizer customCanonicalizer;

    public interface CustomCanonicalizer {

        Node canonicalize(Node node);
    }

    public CanonicalizerPhase(boolean canonicalizeReads) {
        this(canonicalizeReads, null);
    }

    public CanonicalizerPhase(boolean canonicalizeReads, CustomCanonicalizer customCanonicalizer) {
        this.canonicalizeReads = canonicalizeReads;
        this.customCanonicalizer = customCanonicalizer;
    }

    public boolean getCanonicalizeReads() {
        return canonicalizeReads;
    }

    @Override
    protected void run(StructuredGraph graph, PhaseContext context) {
        new Instance(context, canonicalizeReads, customCanonicalizer).run(graph);
    }

    /**
     * @param newNodesMark only the {@linkplain Graph#getNewNodes(Mark) new nodes} specified by this
     *            mark are processed
     */
    public void applyIncremental(StructuredGraph graph, PhaseContext context, Mark newNodesMark) {
        applyIncremental(graph, context, newNodesMark, true);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Mark newNodesMark, boolean dumpGraph) {
        new Instance(context, canonicalizeReads, newNodesMark, customCanonicalizer).apply(graph, dumpGraph);
    }

    /**
     * @param workingSet the initial working set of nodes on which the canonicalizer works, should
     *            be an auto-grow node bitmap
     */
    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet) {
        applyIncremental(graph, context, workingSet, true);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet, boolean dumpGraph) {
        new Instance(context, canonicalizeReads, workingSet, customCanonicalizer).apply(graph, dumpGraph);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet, Mark newNodesMark) {
        applyIncremental(graph, context, workingSet, newNodesMark, true);
    }

    public void applyIncremental(StructuredGraph graph, PhaseContext context, Iterable<? extends Node> workingSet, Mark newNodesMark, boolean dumpGraph) {
        new Instance(context, canonicalizeReads, workingSet, newNodesMark, customCanonicalizer).apply(graph, dumpGraph);
    }

    private static final class Instance extends Phase {

        private final Mark newNodesMark;
        private final PhaseContext context;
        private final CustomCanonicalizer customCanonicalizer;
        private final Iterable<? extends Node> initWorkingSet;
        private final boolean canonicalizeReads;

        private NodeWorkList workList;
        private Tool tool;

        private Instance(PhaseContext context, boolean canonicalizeReads, CustomCanonicalizer customCanonicalizer) {
            this(context, canonicalizeReads, null, null, customCanonicalizer);
        }

        private Instance(PhaseContext context, boolean canonicalizeReads, Iterable<? extends Node> workingSet, CustomCanonicalizer customCanonicalizer) {
            this(context, canonicalizeReads, workingSet, null, customCanonicalizer);
        }

        private Instance(PhaseContext context, boolean canonicalizeReads, Mark newNodesMark, CustomCanonicalizer customCanonicalizer) {
            this(context, canonicalizeReads, null, newNodesMark, customCanonicalizer);
        }

        private Instance(PhaseContext context, boolean canonicalizeReads, Iterable<? extends Node> workingSet, Mark newNodesMark, CustomCanonicalizer customCanonicalizer) {
            super("Canonicalizer");
            this.newNodesMark = newNodesMark;
            this.context = context;
            this.canonicalizeReads = canonicalizeReads;
            this.customCanonicalizer = customCanonicalizer;
            this.initWorkingSet = workingSet;
        }

        @Override
        protected void run(StructuredGraph graph) {
            boolean wholeGraph = newNodesMark == null || newNodesMark.isStart();
            if (initWorkingSet == null) {
                workList = graph.createIterativeNodeWorkList(wholeGraph, MAX_ITERATION_PER_NODE);
            } else {
                workList = graph.createIterativeNodeWorkList(false, MAX_ITERATION_PER_NODE);
                workList.addAll(initWorkingSet);
            }
            if (!wholeGraph) {
                workList.addAll(graph.getNewNodes(newNodesMark));
            }
            tool = new Tool();
            processWorkSet(graph);
        }

        private void processWorkSet(StructuredGraph graph) {
            NodeEventListener listener = new NodeEventListener() {

                public void nodeAdded(Node node) {
                    workList.add(node);
                }

                public void inputChanged(Node node) {
                    workList.add(node);
                }

                public void usagesDroppedToZero(Node node) {
                    workList.add(node);
                }

            };
            try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
                for (Node n : workList) {
                    processNode(n);
                }
            }
        }

        private void processNode(Node node) {
            if (node.isAlive()) {
                METRIC_PROCESSED_NODES.increment();

                NodeClass nodeClass = node.getNodeClass();
                if (tryGlobalValueNumbering(node, nodeClass)) {
                    return;
                }
                StructuredGraph graph = (StructuredGraph) node.graph();
                if (!GraphUtil.tryKillUnused(node)) {
                    if (!tryCanonicalize(node, nodeClass)) {
                        if (node instanceof ValueNode) {
                            ValueNode valueNode = (ValueNode) node;
                            boolean improvedStamp = tryInferStamp(valueNode);
                            Constant constant = valueNode.stamp().asConstant();
                            if (constant != null && !(node instanceof ConstantNode)) {
                                valueNode.replaceAtUsages(InputType.Value, ConstantNode.forConstant(valueNode.stamp(), constant, context.getMetaAccess(), graph));
                                GraphUtil.tryKillUnused(valueNode);
                            } else if (improvedStamp) {
                                // the improved stamp may enable additional canonicalization
                                if (!tryCanonicalize(valueNode, nodeClass)) {
                                    valueNode.usages().forEach(workList::add);
                                }
                            }
                        }
                    }
                }
            }
        }

        public static boolean tryGlobalValueNumbering(Node node, NodeClass nodeClass) {
            if (nodeClass.valueNumberable() && !nodeClass.isLeafNode()) {
                Node newNode = node.graph().findDuplicate(node);
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

        public boolean tryCanonicalize(final Node node, NodeClass nodeClass) {
            boolean result = baseTryCanonicalize(node, nodeClass);
            if (!result && customCanonicalizer != null) {
                Node canonical = customCanonicalizer.canonicalize(node);
                result = performReplacement(node, canonical);
            }
            return result;
        }

        private static AutoCloseable getCanonicalizeableContractAssertion(Node node) {
            boolean needsAssertion = false;
            assert (needsAssertion = true) == true;
            if (needsAssertion) {
                Mark mark = node.graph().getMark();
                return () -> {
                    assert mark.equals(node.graph().getMark()) : "new node created while canonicalizing " + node.getClass().getSimpleName() + " " + node + ": " +
                                    node.graph().getNewNodes(mark).snapshot();
                };
            } else {
                return null;
            }
        }

        public boolean baseTryCanonicalize(final Node node, NodeClass nodeClass) {
            if (nodeClass.isCanonicalizable()) {
                METRIC_CANONICALIZATION_CONSIDERED_NODES.increment();
                try (Scope s = Debug.scope("CanonicalizeNode", node)) {
                    Node canonical;
                    try (AutoCloseable verify = getCanonicalizeableContractAssertion(node)) {
                        canonical = ((Canonicalizable) node).canonical(tool);
                    }
                    if (performReplacement(node, canonical)) {
                        return true;
                    }
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
            }

            if (nodeClass.isSimplifiable()) {
                Debug.log("Canonicalizer: simplifying %s", node);
                METRIC_SIMPLIFICATION_CONSIDERED_NODES.increment();
                try (Scope s = Debug.scope("SimplifyNode", node)) {
                    node.simplify(tool);
                } catch (Throwable e) {
                    throw Debug.handle(e);
                }
                return node.isDeleted();
            }
            return false;
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
//                              ControlSink|   X    |        X        |       7       |
//                                         --------------------------------------------
//       X: must not happen (checked with assertions)
// @formatter:on
        private boolean performReplacement(final Node node, Node newCanonical) {
            if (newCanonical == node) {
                Debug.log("Canonicalizer: work on %1s", node);
                return false;
            } else {
                Node canonical = newCanonical;
                Debug.log("Canonicalizer: replacing %1s with %1s", node, canonical);
                METRIC_CANONICALIZED_NODES.increment();
                StructuredGraph graph = (StructuredGraph) node.graph();
                if (canonical != null && !canonical.isAlive()) {
                    assert !canonical.isDeleted();
                    canonical = graph.addOrUniqueWithInputs(canonical);
                }
                if (node instanceof FloatingNode) {
                    if (canonical == null) {
                        // case 1
                        node.replaceAtUsages(null);
                        graph.removeFloating((FloatingNode) node);
                    } else {
                        // case 2
                        assert !(canonical instanceof FixedNode) || (canonical.predecessor() != null || canonical instanceof StartNode || canonical instanceof MergeNode) : node + " -> " + canonical +
                                        " : replacement should be floating or fixed and connected";
                        graph.replaceFloating((FloatingNode) node, canonical);
                    }
                } else {
                    assert node instanceof FixedNode && node.predecessor() != null : node + " -> " + canonical + " : node should be fixed & connected (" + node.predecessor() + ")";
                    FixedNode fixed = (FixedNode) node;
                    if (canonical instanceof ControlSinkNode) {
                        // case 7
                        fixed.replaceAtPredecessor(canonical);
                        GraphUtil.killCFG(fixed);
                        return true;
                    } else {
                        assert fixed instanceof FixedWithNextNode;
                        FixedWithNextNode fixedWithNext = (FixedWithNextNode) fixed;
                        // When removing a fixed node, new canonicalization
                        // opportunities for its successor may arise
                        assert fixedWithNext.next() != null;
                        tool.addToWorkList(fixedWithNext.next());
                        if (canonical == null) {
                            // case 3
                            node.replaceAtUsages(null);
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
                }
                return true;
            }
        }

        /**
         * Calls {@link ValueNode#inferStamp()} on the node and, if it returns true (which means
         * that the stamp has changed), re-queues the node's usages. If the stamp has changed then
         * this method also checks if the stamp now describes a constant integer value, in which
         * case the node is replaced with a constant.
         */
        private boolean tryInferStamp(ValueNode node) {
            if (node.isAlive()) {
                METRIC_INFER_STAMP_CALLED.increment();
                if (node.inferStamp()) {
                    METRIC_STAMP_CHANGED.increment();
                    for (Node usage : node.usages()) {
                        workList.add(usage);
                    }
                    return true;
                }
            }
            return false;
        }

        private final class Tool implements SimplifierTool {

            @Override
            public void deleteBranch(Node branch) {
                branch.predecessor().replaceFirstSuccessor(branch, null);
                GraphUtil.killCFG(branch, this);
            }

            /**
             * @return an object that can be used for recording assumptions or {@code null} if
             *         assumptions are not allowed in the current context.
             */
            @Override
            public Assumptions assumptions() {
                return context.getAssumptions();
            }

            @Override
            public MetaAccessProvider getMetaAccess() {
                return context.getMetaAccess();
            }

            @Override
            public ConstantReflectionProvider getConstantReflection() {
                return context.getConstantReflection();
            }

            @Override
            public void addToWorkList(Node node) {
                workList.add(node);
            }

            public void addToWorkList(Iterable<? extends Node> nodes) {
                workList.addAll(nodes);
            }

            @Override
            public void removeIfUnused(Node node) {
                GraphUtil.tryKillUnused(node);
            }

            @Override
            public boolean canonicalizeReads() {
                return canonicalizeReads;
            }
        }
    }
}
