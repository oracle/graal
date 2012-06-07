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
package com.oracle.graal.compiler.phases;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.InputChangedListener;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.cri.ri.*;

public class CanonicalizerPhase extends Phase {
    private static final int MAX_ITERATION_PER_NODE = 10;
    private static final DebugMetric METRIC_CANONICALIZED_NODES = Debug.metric("CanonicalizedNodes");
    private static final DebugMetric METRIC_CANONICALIZATION_CONSIDERED_NODES = Debug.metric("CanonicalizationConsideredNodes");
    private static final DebugMetric METRIC_SIMPLIFICATION_CONSIDERED_NODES = Debug.metric("SimplificationConsideredNodes");
    public static final DebugMetric METRIC_GLOBAL_VALUE_NUMBERING_HITS = Debug.metric("GlobalValueNumberingHits");

    private final int newNodesMark;
    private final CiTarget target;
    private final CiAssumptions assumptions;
    private final RiRuntime runtime;
    private final IsImmutablePredicate immutabilityPredicate;
    private final Iterable<Node> initWorkingSet;

    private NodeWorkList workList;
    private Tool tool;

    public CanonicalizerPhase(CiTarget target, RiRuntime runtime, CiAssumptions assumptions) {
        this(target, runtime, assumptions, null, 0, null);
    }

    /**
     * @param target
     * @param runtime
     * @param assumptions
     * @param workingSet the initial working set of nodes on which the canonicalizer works, should be an auto-grow node bitmap
     * @param immutabilityPredicate
     */
    public CanonicalizerPhase(CiTarget target, RiRuntime runtime, CiAssumptions assumptions, Iterable<Node> workingSet, IsImmutablePredicate immutabilityPredicate) {
        this(target, runtime, assumptions, workingSet, 0, immutabilityPredicate);
    }

    /**
     * @param newNodesMark only the {@linkplain Graph#getNewNodes(int) new nodes} specified by
     *            this mark are processed otherwise all nodes in the graph are processed
     */
    public CanonicalizerPhase(CiTarget target, RiRuntime runtime, CiAssumptions assumptions, int newNodesMark, IsImmutablePredicate immutabilityPredicate) {
        this(target, runtime, assumptions, null, newNodesMark, immutabilityPredicate);
    }

    private CanonicalizerPhase(CiTarget target, RiRuntime runtime, CiAssumptions assumptions, Iterable<Node> workingSet, int newNodesMark, IsImmutablePredicate immutabilityPredicate) {
        this.newNodesMark = newNodesMark;
        this.target = target;
        this.assumptions = assumptions;
        this.runtime = runtime;
        this.immutabilityPredicate = immutabilityPredicate;
        this.initWorkingSet = workingSet;
    }

    @Override
    protected void run(StructuredGraph graph) {
        if (initWorkingSet == null) {
            workList = graph.createNodeWorkList(newNodesMark == 0, MAX_ITERATION_PER_NODE);
            if (newNodesMark > 0) {
                workList.addAll(graph.getNewNodes(newNodesMark));
            }
        } else {
            workList = graph.createNodeWorkList(newNodesMark == 0, MAX_ITERATION_PER_NODE);
            workList.addAll(initWorkingSet);
        }
        tool = new Tool(workList, runtime, target, assumptions, immutabilityPredicate);
        processWorkSet(graph);

        while (graph.getUsagesDroppedNodesCount() > 0) {
            for (Node n : graph.getAndCleanUsagesDroppedNodes()) {
                if (!n.isDeleted() && n.usages().size() == 0 && GraphUtil.isFloatingNode().apply(n)) {
                    n.safeDelete();
                }
            }
        }
    }

    public interface IsImmutablePredicate {
        /**
         * Determines if a given constant is an object/array whose current
         * fields/elements will never change.
         */
        boolean apply(RiConstant constant);
    }

    private void processWorkSet(StructuredGraph graph) {
        graph.trackInputChange(new InputChangedListener() {
            @Override
            public void inputChanged(Node node) {
                workList.addAgain(node);
            }
        });

        for (Node n : workList) {
            processNode(n, graph);
        }

        graph.stopTrackingInputChange();
    }

    private void processNode(Node n, StructuredGraph graph) {
        if (n.isAlive()) {
            METRIC_PROCESSED_NODES.increment();

            if (tryGlobalValueNumbering(n, graph)) {
                return;
            }
            int mark = graph.getMark();
            tryCanonicalize(n, graph, tool);

            for (Node node : graph.getNewNodes(mark)) {
                workList.add(node);
            }
        }
    }

    public static boolean tryGlobalValueNumbering(Node n, StructuredGraph graph) {
        if (n.getNodeClass().valueNumberable()) {
            Node newNode = graph.findDuplicate(n);
            if (newNode != null) {
                assert !(n instanceof FixedNode || newNode instanceof FixedNode);
                n.replaceAtUsages(newNode);
                n.safeDelete();
                METRIC_GLOBAL_VALUE_NUMBERING_HITS.increment();
                Debug.log("GVN applied and new node is %1s", newNode);
                return true;
            }
        }
        return false;
    }

    public static void tryCanonicalize(Node node, StructuredGraph graph, SimplifierTool tool) {
        if (node instanceof Canonicalizable) {
            METRIC_CANONICALIZATION_CONSIDERED_NODES.increment();
            ValueNode canonical = ((Canonicalizable) node).canonical(tool);
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
            if (canonical == node) {
                Debug.log("Canonicalizer: work on %s", node);
            } else {
                Debug.log("Canonicalizer: replacing %s with %s", node, canonical);

                METRIC_CANONICALIZED_NODES.increment();
                if (node instanceof FloatingNode) {
                    if (canonical == null) {
                        // case 1
                        graph.removeFloating((FloatingNode) node);
                    } else {
                        // case 2
                        assert !(canonical instanceof FixedNode) || canonical.predecessor() != null : node + " -> " + canonical +
                                        " : replacement should be floating or fixed and connected";
                        graph.replaceFloating((FloatingNode) node, canonical);
                    }
                } else {
                    assert node instanceof FixedWithNextNode && node.predecessor() != null : node + " -> " + canonical + " : node should be fixed & connected (" + node.predecessor() + ")";
                    if (canonical == null) {
                        // case 3
                        graph.removeFixed((FixedWithNextNode) node);
                    } else if (canonical instanceof FloatingNode) {
                        // case 4
                        graph.replaceFixedWithFloating((FixedWithNextNode) node, (FloatingNode) canonical);
                    } else {
                        assert canonical instanceof FixedNode;
                        if (canonical.predecessor() == null) {
                            assert !canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " shouldn't have successors";
                            // case 5
                            graph.replaceFixedWithFixed((FixedWithNextNode) node, (FixedWithNextNode) canonical);
                        } else {
                            assert canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " should have successors";
                            // case 6
                            node.replaceAtUsages(canonical);
                            graph.removeFixed((FixedWithNextNode) node);
                        }
                    }
                }
            }
        } else if (node instanceof Simplifiable) {
            Debug.log("Canonicalizer: simplifying %s", node);
            METRIC_SIMPLIFICATION_CONSIDERED_NODES.increment();
            ((Simplifiable) node).simplify(tool);
        }
    }

    private static final class Tool implements SimplifierTool {

        private final NodeWorkList nodeWorkSet;
        private final RiRuntime runtime;
        private final CiTarget target;
        private final CiAssumptions assumptions;
        private final IsImmutablePredicate immutabilityPredicate;

        public Tool(NodeWorkList nodeWorkSet, RiRuntime runtime, CiTarget target, CiAssumptions assumptions, IsImmutablePredicate immutabilityPredicate) {
            this.nodeWorkSet = nodeWorkSet;
            this.runtime = runtime;
            this.target = target;
            this.assumptions = assumptions;
            this.immutabilityPredicate = immutabilityPredicate;
        }

        @Override
        public void deleteBranch(FixedNode branch) {
            branch.predecessor().replaceFirstSuccessor(branch, null);
            GraphUtil.killCFG(branch);
        }

        /**
         * @return the current target or {@code null} if no target is available in the current context.
         */
        @Override
        public CiTarget target() {
            return target;
        }

        /**
         * @return an object that can be used for recording assumptions or {@code null} if assumptions are not allowed in the current context.
         */
        @Override
        public CiAssumptions assumptions() {
            return assumptions;
        }

        @Override
        public RiRuntime runtime() {
            return runtime;
        }

        @Override
        public void addToWorkList(Node node) {
            nodeWorkSet.add(node);
        }

        @Override
        public boolean isImmutable(RiConstant objectConstant) {
            return immutabilityPredicate != null && immutabilityPredicate.apply(objectConstant);
        }
    }
}
