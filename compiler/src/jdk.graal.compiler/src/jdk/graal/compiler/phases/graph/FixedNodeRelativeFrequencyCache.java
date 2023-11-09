/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.graph;

import static jdk.graal.compiler.nodes.cfg.ControlFlowGraph.multiplyRelativeFrequencies;

import java.util.function.ToDoubleFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

/**
 * Compute relative frequencies for fixed nodes on the fly and cache them at
 * {@link AbstractBeginNode}s.
 */
public class FixedNodeRelativeFrequencyCache implements ToDoubleFunction<FixedNode> {

    private static final CounterKey computeNodeRelativeFrequencyCounter = DebugContext.counter("ComputeNodeRelativeFrequency");

    private final EconomicMap<FixedNode, Double> cache = EconomicMap.create(Equivalence.IDENTITY);

    private ControlFlowGraph lastCFG = null;
    private Graph.Mark lastCFGMark = null;

    /**
     * <p>
     * Given a {@link FixedNode} this method finds the most immediate {@link AbstractBeginNode}
     * preceding it that either:
     * <ul>
     * <li>has no predecessor (ie, the begin-node is a merge, in particular a loop-begin, or the
     * start-node)</li>
     * <li>has a control-split predecessor</li>
     * </ul>
     * </p>
     *
     * <p>
     * The thus found {@link AbstractBeginNode} is equi-probable with the {@link FixedNode} it was
     * obtained from. When computed for the first time (afterwards a cache lookup returns it) that
     * relative frequency is computed as follows, again depending on the begin-node's predecessor:
     * <ul>
     * <li>No predecessor. In this case the begin-node is either:</li>
     * <ul>
     * <li>a merge-node, whose relative frequency adds up those of its forward-ends</li>
     * <li>a loop-begin, with frequency as above multiplied by the loop-frequency</li>
     * </ul>
     * <li>Control-split predecessor: frequency of the branch times that of the control-split</li>
     * </ul>
     * </p>
     *
     * <p>
     * As an exception to all the above, a frequency of 1 is assumed for a {@link FixedNode} that
     * appears to be dead-code (ie, lacks a predecessor).
     * </p>
     *
     */
    @Override
    public double applyAsDouble(FixedNode node) {
        assert node != null;
        computeNodeRelativeFrequencyCounter.increment(node.getDebug());

        FixedNode current = findBegin(node);
        if (current == null) {
            // this should only appear for dead code
            return 1D;
        }

        assert current instanceof AbstractBeginNode : Assertions.errorMessage(current);
        Double cachedValue = cache.get(current);
        if (cachedValue != null) {
            return cachedValue;
        }

        double relativeFrequency = 0.0;
        if (current.predecessor() == null) {
            if (current instanceof AbstractMergeNode) {
                relativeFrequency = handleMerge(current, relativeFrequency);
            } else {
                assert current instanceof StartNode : Assertions.errorMessage(current);
                relativeFrequency = 1D;
            }
        } else {
            ControlSplitNode split = (ControlSplitNode) current.predecessor();
            relativeFrequency = multiplyRelativeFrequencies(split.probability((AbstractBeginNode) current), applyAsDouble(split));
        }
        assert !Double.isNaN(relativeFrequency) && !Double.isInfinite(relativeFrequency) : current + " " + relativeFrequency;
        cache.put(current, relativeFrequency);
        return relativeFrequency;
    }

    private double handleMerge(FixedNode current, double relativeFrequency) {
        double result = relativeFrequency;
        AbstractMergeNode currentMerge = (AbstractMergeNode) current;
        NodeInputList<EndNode> currentForwardEnds = currentMerge.forwardEnds();
        /*
         * Use simple iteration instead of streams, since the stream infrastructure adds many frames
         * which causes the recursion to overflow the stack earlier than it would otherwise.
         */
        for (AbstractEndNode endNode : currentForwardEnds) {
            result += applyAsDouble(endNode);
        }
        if (current instanceof LoopBeginNode) {
            computeLazyCFG(current);
            result = multiplyRelativeFrequencies(result, lastCFG.localLoopFrequency(((LoopBeginNode) current)));
        }
        return result;
    }

    private void computeLazyCFG(FixedNode node) {
        if (lastCFG == null || !lastCFGMark.isCurrent()) {
            lastCFG = ControlFlowGraph.newBuilder(node.graph()).computeFrequency(true).build();
            lastCFGMark = node.graph().getMark();
        }
    }

    private static FixedNode findBegin(FixedNode node) {
        FixedNode current = node;
        while (true) { // TERMINATION ARGUMENT: processing predecessor nodes in a graph
            CompilationAlarm.checkProgress(node.graph());
            assert current != null;
            Node predecessor = current.predecessor();
            if (current instanceof AbstractBeginNode) {
                if (predecessor == null) {
                    break;
                } else if (predecessor.successors().count() != 1) {
                    assert predecessor instanceof ControlSplitNode : "a FixedNode with multiple successors needs to be a ControlSplitNode: " + current + " / " + predecessor;
                    break;
                }
            } else if (predecessor == null) {
                current = null;
                break;
            }
            current = (FixedNode) predecessor;
        }
        return current;
    }
}
