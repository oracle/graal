/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common.cfs;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodes.*;
import com.oracle.graal.compiler.common.type.ObjectStamp;
import com.oracle.graal.phases.graph.PostOrderNodeIterator;
import com.oracle.graal.phases.tiers.PhaseContext;

import java.util.ArrayList;

/**
 * <p>
 * For readability purposes the code realizing control-flow-sensitive reductions is chopped into
 * several classes in an inheritance hierarchy, this class being their common ancestor. That way,
 * many dependencies can be ruled out immediately (e.g., private members of a class aren't needed by
 * other classes). The whole thing is reminiscent of trait-based patterns, minus their
 * disadvantages.
 * </p>
 *
 *
 * <p>
 * This class makes available little more than a few fields and a few utility methods used
 * throughout the remaining components making up control-flow sensitive reductions.
 * </p>
 * */
public abstract class BaseReduction extends PostOrderNodeIterator<State> {

    protected static final DebugMetric metricCheckCastRemoved = Debug.metric("CheckCastRemoved");
    protected static final DebugMetric metricGuardingPiNodeRemoved = Debug.metric("GuardingPiNodeRemoved");
    protected static final DebugMetric metricFixedGuardNodeRemoved = Debug.metric("FixedGuardNodeRemoved");
    protected static final DebugMetric metricMethodResolved = Debug.metric("MethodResolved");

    /**
     * <p>
     * Upon visiting a {@link com.oracle.graal.nodes.FixedNode FixedNode} in
     * {@link #node(com.oracle.graal.nodes.FixedNode)}, an impossible path may be detected. We'd
     * like to insert an unconditional deoptimizing node as a hint for Dead Code Elimination to kill
     * that branch. However that can't be made on the go (a
     * {@link com.oracle.graal.nodes.ControlSinkNode} can't have successors). Thus their insertion
     * is postponed till the end of a round of
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction}.
     * </p>
     *
     * @see State#impossiblePath()
     * @see com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#finished()
     * */
    public static class PostponedDeopt {

        private final boolean goesBeforeFixed;
        private final FixedWithNextNode fixed;
        private final DeoptimizationReason deoptReason;

        public PostponedDeopt(boolean goesBeforeFixed, FixedWithNextNode fixed, DeoptimizationReason deoptReason) {
            this.goesBeforeFixed = goesBeforeFixed;
            this.fixed = fixed;
            this.deoptReason = deoptReason;
        }

        public void doRewrite(LogicNode falseConstant) {
            StructuredGraph graph = fixed.graph();
            // have to insert a FixedNode other than a ControlSinkNode
            FixedGuardNode buckStopsHere = graph.add(new FixedGuardNode(falseConstant, deoptReason, DeoptimizationAction.None));
            if (goesBeforeFixed) {
                fixed.replaceAtPredecessor(buckStopsHere);
            } else {
                graph.addAfterFixed(fixed, buckStopsHere);
            }
        }

    }

    protected static class PostponedDeopts extends ArrayList<PostponedDeopt> {

        private static final long serialVersionUID = 7188324432387121238L;

        /**
         * Enqueue adding a {@link com.oracle.graal.nodes.DeoptimizeNode} right before the fixed
         * argument, will be done once we're done traversing the graph.
         *
         * @see #finished()
         * */
        void addDeoptBefore(FixedWithNextNode fixed, DeoptimizationReason deoptReason) {
            add(new PostponedDeopt(true, fixed, deoptReason));
        }

        /**
         * Enqueue adding a {@link com.oracle.graal.nodes.DeoptimizeNode} right after the fixed
         * argument, will be done once we're done traversing the graph.
         *
         * @see #finished()
         * */
        void addDeoptAfter(FixedWithNextNode fixed, DeoptimizationReason deoptReason) {
            add(new PostponedDeopt(false, fixed, deoptReason));
        }

    }

    /**
     * <p>
     * One of the promises of
     * {@link com.oracle.graal.phases.common.cfs.EquationalReasoner#deverbosify(com.oracle.graal.graph.Node)}
     * is that a "maximally reduced" node is returned. That is achieved in part by leveraging
     * {@link com.oracle.graal.graph.Node#canonical(com.oracle.graal.graph.spi.CanonicalizerTool)}.
     * Doing so, in turn, requires this subclass of
     * {@link com.oracle.graal.graph.spi.CanonicalizerTool}.
     * </p>
     * */
    public final class Tool implements CanonicalizerTool {

        private final PhaseContext context;

        public Tool(PhaseContext context) {
            this.context = context;
        }

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

        /**
         * Postpone
         * {@link com.oracle.graal.nodes.util.GraphUtil#tryKillUnused(com.oracle.graal.graph.Node)}
         * until {@link FlowSensitiveReduction#finished()} for the reasons covered there.
         * */
        @Override
        public void removeIfUnused(Node node) {
            // GraphUtil.tryKillUnused(node);
        }

        @Override
        public boolean canonicalizeReads() {
            return false;
        }
    } // end of class FlowSensitiveReduction.Tool

    protected final LogicConstantNode trueConstant;
    protected final LogicConstantNode falseConstant;
    protected final ConstantNode nullConstant;

    protected final CanonicalizerTool tool;
    protected final StructuredGraph graph;

    protected EquationalReasoner reasoner;

    protected final PostponedDeopts postponedDeopts = new PostponedDeopts();

    protected BaseReduction(FixedNode start, State initialState, PhaseContext context) {
        super(start, initialState);
        graph = start.graph();
        trueConstant = LogicConstantNode.tautology(graph);
        falseConstant = LogicConstantNode.contradiction(graph);
        nullConstant = ConstantNode.defaultForKind(Kind.Object, graph); // ConstantNode.forObject(null,
                                                                        // metaAccess, graph);
        tool = new Tool(context);
        reasoner = new EquationalReasoner(graph, tool, trueConstant, falseConstant, nullConstant);
    }

    /**
     * <p>
     * Test whether the output's stamp is an upcast of that of the input. For example, upon
     * replacing a CheckCastNode in
     * {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReduction#lowerCheckCastAnchorFriendlyWay(com.oracle.graal.nodes.java.CheckCastNode, com.oracle.graal.nodes.ValueNode)}
     * we don't want to be left with an upcast, as it loses precision.
     * </p>
     *
     * <p>
     * As usual with object stamps, they can be compared along different dimensions (alwaysNull,
     * etc.) It's enough for one such dimension to show precision loss for the end result to be
     * reported as such.
     * </p>
     *
     * */
    protected static boolean precisionLoss(ValueNode input, ValueNode output) {
        ObjectStamp inputStamp = (ObjectStamp) input.stamp();
        ObjectStamp outputStamp = (ObjectStamp) output.stamp();
        if (FlowUtil.isMorePrecise(inputStamp.type(), outputStamp.type())) {
            return true;
        }
        if (lessThan(outputStamp.alwaysNull(), inputStamp.alwaysNull())) {
            return true;
        }
        if (lessThan(outputStamp.nonNull(), inputStamp.nonNull())) {
            return true;
        }
        if (lessThan(outputStamp.isExactType(), inputStamp.isExactType())) {
            return true;
        }
        return false;
    }

    private static boolean lessThan(boolean a, boolean b) {
        return a == false && b == true;
    }

}
