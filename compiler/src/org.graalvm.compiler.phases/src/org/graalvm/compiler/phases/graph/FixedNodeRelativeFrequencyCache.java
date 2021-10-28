/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.graph;

import static org.graalvm.compiler.nodes.cfg.ControlFlowGraph.multiplyRelativeFrequencies;

import java.util.function.ToDoubleFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

/**
 * Compute relative frequencies for fixed nodes on the fly and cache them at
 * {@link AbstractBeginNode}s.
 */
public class FixedNodeRelativeFrequencyCache implements ToDoubleFunction<FixedNode> {

    private static final CounterKey computeNodeRelativeFrequencyCounter = DebugContext.counter("ComputeNodeRelativeFrequency");

    private final EconomicMap<FixedNode, Double> cache = EconomicMap.create(Equivalence.IDENTITY);

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

        assert current instanceof AbstractBeginNode;
        Double cachedValue = cache.get(current);
        if (cachedValue != null) {
            return cachedValue;
        }

        ControlFlowGraph cfg = ControlFlowGraph.compute(node.graph(), false, false, false, false);

        double relativeFrequency = 0.0;
        if (current.predecessor() == null) {
            if (current instanceof AbstractMergeNode) {
                relativeFrequency = handleMerge(current, relativeFrequency, cfg);
            } else {
                assert current instanceof StartNode;
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

    private double handleMerge(FixedNode current, double relativeFrequency, ControlFlowGraph cfg) {
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
            result = multiplyRelativeFrequencies(result, cfg.localLoopFrequency(((LoopBeginNode) current)));
        }
        return result;
    }

    private static FixedNode findBegin(FixedNode node) {
        FixedNode current = node;
        while (true) {
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
