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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.FloatingNode;
import com.oracle.graal.nodes.calc.IsNullNode;
import com.oracle.graal.nodes.extended.LoadHubNode;
import com.oracle.graal.nodes.extended.NullCheckNode;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.compiler.common.type.IllegalStamp;
import com.oracle.graal.nodes.type.StampTool;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.phases.common.DeadCodeEliminationPhase;
import com.oracle.graal.phases.tiers.PhaseContext;

import java.lang.reflect.Modifier;

import static com.oracle.graal.api.meta.DeoptimizationAction.InvalidateReprofile;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;

/**
 * <p>
 * In a nutshell, {@link com.oracle.graal.phases.common.cfs.FlowSensitiveReductionPhase} makes a
 * single pass in dominator-based order over the graph:
 * <ol>
 * <li>collecting properties of interest at control-splits; as well as for check-casts,
 * guarding-pis, null-checks, and fixed-guards. Such flow-sensitive information is tracked via a
 * dedicated {@link com.oracle.graal.phases.common.cfs.State state instance} for each control-flow
 * path.</li>
 * <li>performing rewritings that are safe at specific program-points. This comprises:
 * <ul>
 * <li>simplification of side-effects free expressions, via
 * {@link com.oracle.graal.phases.common.cfs.EquationalReasoner#deverbosify(com.oracle.graal.graph.Node)}
 * <ul>
 * <li>
 * at certain {@link com.oracle.graal.nodes.FixedNode}, see
 * {@link #deverbosifyInputsInPlace(com.oracle.graal.nodes.ValueNode)}</li>
 * <li>
 * including for devirtualization, see
 * {@link #deverbosifyInputsCopyOnWrite(com.oracle.graal.nodes.java.MethodCallTargetNode)}</li>
 * </ul>
 * </li>
 * <li>simplification of control-flow:
 * <ul>
 * <li>
 * by simplifying the input-condition to an {@link com.oracle.graal.nodes.IfNode}</li>
 * <li>
 * by eliminating redundant check-casts, guarding-pis, null-checks, and fixed-guards; where
 * "redundancy" is determined using flow-sensitive information. In these cases, redundancy can be
 * due to:
 * <ul>
 * <li>an equivalent, existing, guarding node is already in scope (thus, use it as replacement and
 * remove the redundant one)</li>
 * <li>"always fails" (thus, replace the node in question with <code>FixedGuardNode(false)</code>)</li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * </ul>
 * </li>
 * </ol>
 * </p>
 *
 * <p>
 * Metrics for this phase are displayed starting with <code>FSR-</code>prefix, their counters are
 * hosted in {@link com.oracle.graal.phases.common.cfs.BaseReduction},
 * {@link com.oracle.graal.phases.common.cfs.EquationalReasoner} and
 * {@link com.oracle.graal.phases.common.cfs.State}.
 * </p>
 *
 * @see com.oracle.graal.phases.common.cfs.CheckCastReduction
 * @see com.oracle.graal.phases.common.cfs.GuardingPiReduction
 * @see com.oracle.graal.phases.common.cfs.FixedGuardReduction
 *
 */
public class FlowSensitiveReduction extends FixedGuardReduction {

    public FlowSensitiveReduction(StartNode start, State initialState, PhaseContext context) {
        super(start, initialState, context);
    }

    /**
     * <p>
     * This method performs two kinds of cleanup:
     * <ol>
     * <li>
     * marking as unreachable certain code-paths, as described in
     * {@link com.oracle.graal.phases.common.cfs.BaseReduction.PostponedDeopt}</li>
     * <li>
     * Removing nodes not in use that were added during this phase, as described next.</li>
     * </ol>
     * </p>
     *
     *
     * <p>
     * Methods like
     * {@link com.oracle.graal.phases.common.cfs.FlowUtil#replaceInPlace(com.oracle.graal.graph.Node, com.oracle.graal.graph.Node, com.oracle.graal.graph.Node)}
     * may result in old inputs becoming disconnected from the graph. It's not advisable to
     * {@link com.oracle.graal.nodes.util.GraphUtil#tryKillUnused(com.oracle.graal.graph.Node)} at
     * that moment, because one of the inputs that might get killed is one of {@link #nullConstant},
     * {@link #falseConstant}, or {@link #trueConstant}; which thus could get killed too early,
     * before another invocation of
     * {@link com.oracle.graal.phases.common.cfs.EquationalReasoner#deverbosify(com.oracle.graal.graph.Node)}
     * needs them. To recap,
     * {@link com.oracle.graal.nodes.util.GraphUtil#tryKillUnused(com.oracle.graal.graph.Node)} also
     * recursively visits the inputs of the its argument.
     * </p>
     *
     * <p>
     * This method goes over all of the nodes that deverbosification might have added, which are
     * either:
     * <ul>
     * <li>
     * {@link com.oracle.graal.nodes.calc.FloatingNode}, added by
     * {@link com.oracle.graal.phases.common.cfs.EquationalReasoner#deverbosifyFloatingNode(com.oracle.graal.nodes.calc.FloatingNode)}
     * ; or</li>
     * <li>
     * {@link com.oracle.graal.nodes.java.MethodCallTargetNode}, added by
     * {@link #deverbosifyInputsCopyOnWrite(com.oracle.graal.nodes.java.MethodCallTargetNode)}</li>
     * </ul>
     *
     * Checking if they aren't in use, proceeding to remove them in that case.
     * </p>
     *
     */
    @Override
    public void finished() {
        if (!postponedDeopts.isEmpty()) {
            for (PostponedDeopt postponed : postponedDeopts) {
                postponed.doRewrite(falseConstant);
            }
            new DeadCodeEliminationPhase().apply(graph);
        }
        for (MethodCallTargetNode mcn : graph.getNodes().filter(MethodCallTargetNode.class)) {
            if (mcn.isAlive() && FlowUtil.lacksUsages(mcn)) {
                mcn.safeDelete();
            }
        }
        for (Node n : graph.getNodes().filter(FloatingNode.class)) {
            GraphUtil.tryKillUnused(n);
        }
        assert !isAliveWithoutUsages(trueConstant);
        assert !isAliveWithoutUsages(falseConstant);
        assert !isAliveWithoutUsages(nullConstant);
        super.finished();
    }

    private static boolean isAliveWithoutUsages(FloatingNode node) {
        return node.isAlive() && FlowUtil.lacksUsages(node);
    }

    private void registerControlSplit(Node pred, BeginNode begin) {
        assert pred != null && begin != null;
        assert !state.isUnreachable;

        if (begin instanceof LoopExitNode) {
            state.clear();
            /*
             * TODO return or not? (by not returning we agree it's ok to update the state as below)
             */
        }

        if (pred instanceof IfNode) {
            registerIfNode((IfNode) pred, begin);
        } else if (pred instanceof TypeSwitchNode) {
            registerTypeSwitchNode((TypeSwitchNode) pred, begin);
        }
    }

    private void registerIfNode(IfNode ifNode, BeginNode begin) {
        final boolean isThenBranch = (begin == ifNode.trueSuccessor());

        if (ifNode.condition() instanceof LogicConstantNode) {
            final LogicConstantNode constCond = (LogicConstantNode) ifNode.condition();
            if (isThenBranch != constCond.getValue()) {
                state.impossiblePath();
                // let IfNode(constant) prune the dead-code control-path
            }
        }

        if (state.isUnreachable) {
            if (!(ifNode.condition() instanceof LogicConstantNode)) {
                // if condition constant, no need to add a Deopt node
                postponedDeopts.addDeoptAfter(begin, UnreachedCode);
            }
        } else {
            state.addFact(isThenBranch, ifNode.condition(), begin);
        }
    }

    /**
     * TODO When tracking integer-stamps, the state at each successor of a TypeSwitchNode should
     * track an integer-stamp for the LoadHubNode (meet over the constants leading to that
     * successor). However, are LoadHubNode-s shared frequently enough?
     */
    private void registerTypeSwitchNode(TypeSwitchNode typeSwitch, BeginNode begin) {
        if (typeSwitch.value() instanceof LoadHubNode) {
            LoadHubNode loadHub = (LoadHubNode) typeSwitch.value();
            ResolvedJavaType type = null;
            for (int i = 0; i < typeSwitch.keyCount(); i++) {
                if (typeSwitch.keySuccessor(i) == begin) {
                    if (type == null) {
                        type = typeSwitch.typeAt(i);
                    } else {
                        type = FlowUtil.widen(type, typeSwitch.typeAt(i));
                    }
                }
            }
            if (type == null) {
                // `begin` denotes the default case of the TypeSwitchNode
                return;
            }
            // it's unwarranted to assume loadHub.object() to be non-null
            state.trackCC(loadHub.getValue(), type, begin);
        }
    }

    /**
     *
     * <p>
     * Reduce input nodes based on the state at the program point for the argument (ie, based on
     * "valid facts" only, without relying on any floating-guard-assumption).
     * </p>
     *
     * <p>
     * For each (direct or indirect) child, a copy-on-write version is made in case any of its
     * children changed, with the copy accommodating the updated children. If the parent was shared,
     * copy-on-write prevents the updates from becoming visible to anyone but the invoker of this
     * method.
     * </p>
     *
     * <p>
     * <b> Please note the parent node is mutated upon any descendant changing. No copy-on-write is
     * performed for the parent node itself. </b>
     * </p>
     *
     * <p>
     * In more detail, for each direct {@link com.oracle.graal.nodes.ValueNode} input of the node at
     * hand,
     *
     * <ol>
     * <li>
     * Obtain a lazy-copied version (via spanning tree) of the DAG rooted at the input-usage in
     * question. Lazy-copying is done by walking a spanning tree of the original DAG, stopping at
     * non-FloatingNodes but transitively walking FloatingNodes and their inputs. Upon arriving at a
     * (floating) node N, the state's facts are checked to determine whether a constant C can be
     * used instead in the resulting lazy-copied DAG. A NodeBitMap is used to realize the spanning
     * tree.</li>
     *
     * <li>
     * Provided one or more N-to-C node replacements took place, the resulting lazy-copied DAG has a
     * parent different from the original (ie different object identity) which indicates the
     * (copied, updated) DAG should replace the original via replaceFirstInput(), and inferStamp()
     * should be invoked to reflect the updated inputs.</li>
     *
     * </ol>
     * </p>
     *
     * @return whether any reduction was performed on the inputs of the arguments.
     */
    public boolean deverbosifyInputsInPlace(ValueNode parent) {
        boolean changed = false;
        for (ValueNode i : FlowUtil.distinctValueAndConditionInputs(parent)) {
            assert !(i instanceof GuardNode) : "This phase not intended to run during MidTier";
            ValueNode j = (ValueNode) reasoner.deverbosify(i);
            if (i != j) {
                changed = true;
                FlowUtil.replaceInPlace(parent, i, j);
            }
        }
        if (changed) {
            FlowUtil.inferStampAndCheck(parent);
        }
        return changed;
    }

    /**
     * Similar to {@link #deverbosifyInputsInPlace(com.oracle.graal.nodes.ValueNode)}, except that
     * not the parent but a fresh clone is updated upon any of its children changing.
     *
     * @return the original parent if no updated took place, a copy-on-write version of it
     *         otherwise.
     *
     */
    private MethodCallTargetNode deverbosifyInputsCopyOnWrite(MethodCallTargetNode parent) {
        final CallTargetNode.InvokeKind ik = parent.invokeKind();
        final boolean shouldTryDevirt = (ik == CallTargetNode.InvokeKind.Interface || ik == CallTargetNode.InvokeKind.Virtual);
        boolean shouldDowncastReceiver = shouldTryDevirt;
        MethodCallTargetNode changed = null;
        for (ValueNode i : FlowUtil.distinctValueAndConditionInputs(parent)) {
            ValueNode j = (ValueNode) reasoner.deverbosify(i);
            if (shouldDowncastReceiver) {
                shouldDowncastReceiver = false;
                j = reasoner.downcast(j);
            }
            if (i != j) {
                assert j != parent;
                if (changed == null) {
                    changed = (MethodCallTargetNode) parent.copyWithInputs();
                    reasoner.added.add(changed);
                    // copyWithInputs() implies graph.unique(changed)
                    assert changed.isAlive();
                    assert FlowUtil.lacksUsages(changed);
                }
                FlowUtil.replaceInPlace(changed, i, j);
            }
        }
        if (changed == null) {
            return parent;
        }
        FlowUtil.inferStampAndCheck(changed);
        /*
         * No need to rememberSubstitution() because not called from deverbosify(). In detail, it's
         * only deverbosify() that skips visited nodes (thus we'd better have recorded any
         * substitutions we want for them). Not this case.
         */
        return changed;
    }

    /**
     * Precondition: This method assumes that either:
     *
     * <ul>
     * <li>the state has already stabilized (ie no more pending iterations in the "iterative"
     * dataflow algorithm); or</li>
     * <li>any rewritings made based on the state in its current form are conservative enough to be
     * safe.</li>
     * </ul>
     *
     * <p>
     * The overarching goal is to perform just enough rewriting to trigger other phases (
     * {@link com.oracle.graal.graph.spi.SimplifierTool SimplifierTool},
     * {@link com.oracle.graal.phases.common.DeadCodeEliminationPhase DeadCodeEliminationPhase},
     * etc) to perform the bulk of rewriting, thus lowering the maintenance burden.
     * </p>
     *
     */
    @Override
    protected void node(FixedNode node) {

        assert node.isAlive();

        /*-------------------------------------------------------------------------------------
         * Step 1: Unreachable paths are still visited (PostOrderNodeIterator requires all ends
         * of a merge to have been visited), but time is saved by neither updating the state nor
         * rewriting anything while on an an unreachable path.
         *-------------------------------------------------------------------------------------
         */
        if (state.isUnreachable) {
            return;
        }

        /*-------------------------------------------------------------------------------------
         * Step 2: For an AbstractBeginNode, determine whether this path is reachable, register
         * any associated guards.
         *-------------------------------------------------------------------------------------
         */
        if (node instanceof BeginNode) {
            BeginNode begin = (BeginNode) node;
            Node pred = node.predecessor();

            if (pred != null) {
                registerControlSplit(pred, begin);
            }
            return;
        }

        /*-------------------------------------------------------------------------------------
         * Step 3: Check whether EquationalReasoner caches should be cleared upon state updates.
         *-------------------------------------------------------------------------------------
         */
        reasoner.updateState(state);

        /*-------------------------------------------------------------------------------------
         * Step 4: Whatever special-case handling makes sense for the FixedNode at hand before
         * its inputs are reduced.
         *-------------------------------------------------------------------------------------
         */

        if (node instanceof AbstractEndNode) {
            visitAbstractEndNode((AbstractEndNode) node);
            return;
        } else if (node instanceof Invoke) {
            visitInvoke((Invoke) node);
            return;
        } else if (node instanceof CheckCastNode) {
            // it's important not to call deverbosification for visitCheckCastNode()
            visitCheckCastNode((CheckCastNode) node);
            return;
        } else if (node instanceof GuardingPiNode) {
            visitGuardingPiNode((GuardingPiNode) node);
            return;
        } else if (node instanceof NullCheckNode) {
            visitNullCheckNode((NullCheckNode) node);
            return;
        } else if (node instanceof FixedGuardNode) {
            visitFixedGuardNode((FixedGuardNode) node);
            return;
        } else if (node instanceof ConditionAnchorNode) {
            // ConditionAnchorNode shouldn't occur during HighTier
            return;
        }

        /*-------------------------------------------------------------------------------------
         * Step 5: After special-case handling, we do our best for those FixedNode-s
         * where the effort to reduce their inputs might pay off.
         *
         * Why is this useful? For example, by the time the BeginNode for an If-branch
         * is visited (in general a ControlSplitNode), the If-condition will have gone already
         * through simplification (and thus potentially have been reduced to a
         * LogicConstantNode).
         *-------------------------------------------------------------------------------------
         */
        boolean paysOffToReduce = false;
        if (node instanceof ControlSplitNode) {
            // desire to simplify control flow
            paysOffToReduce = true;
        } else if (node instanceof ReturnNode) {
            paysOffToReduce = true;
        } else if (node instanceof AccessFieldNode || node instanceof AccessArrayNode) {
            // desire to remove null-checks
            paysOffToReduce = true;
        }

        // TODO comb remaining FixedWithNextNode subclasses, pick those with chances of paying-off

        // TODO UnsafeLoadNode takes a condition

        if (paysOffToReduce) {
            deverbosifyInputsInPlace(node);
        }

        /*---------------------------------------------------------------------------------------
         * Step 6: Any additional special-case handling, this time after having inputs reduced.
         * For example, leverage anchors provided by the FixedNode, to add facts to the factbase.
         *---------------------------------------------------------------------------------------
         */

        // TODO some nodes are GuardingNodes (eg, FixedAccessNode) we could use them to track state
        // TODO other nodes are guarded (eg JavaReadNode), thus *their* guards could be replaced.

    }

    /**
     * In case the scrutinee:
     *
     * <ul>
     * <li>is known to be null, an unconditional deopt is added.</li>
     * <li>is known to be non-null, the NullCheckNode is removed.</li>
     * <li>otherwise, the NullCheckNode is lowered to a FixedGuardNode which then allows using it as
     * anchor for state-tracking.</li>
     * </ul>
     *
     * <p>
     * Precondition: the input (ie, object) hasn't been deverbosified yet.
     * </p>
     */
    private void visitNullCheckNode(NullCheckNode ncn) {
        ValueNode object = ncn.getObject();
        if (state.isNull(object)) {
            postponedDeopts.addDeoptBefore(ncn, NullCheckException);
            state.impossiblePath();
            return;
        }
        if (state.isNonNull(object)) {
            /*
             * Redundant NullCheckNode. Unlike GuardingPiNode or FixedGuardNode, NullCheckNode-s
             * aren't used as GuardingNode-s, thus in this case can be removed without further ado.
             */
            assert FlowUtil.lacksUsages(ncn);
            graph.removeFixed(ncn);
            return;
        }
        /*
         * Lower the NullCheckNode to a FixedGuardNode which then allows using it as anchor for
         * state-tracking. TODO the assumption here is that the code emitted for the resulting
         * FixedGuardNode is as efficient as for NullCheckNode.
         */
        IsNullNode isNN = graph.unique(IsNullNode.create(object));
        reasoner.added.add(isNN);
        FixedGuardNode nullCheck = graph.add(FixedGuardNode.create(isNN, UnreachedCode, InvalidateReprofile, true));
        graph.replaceFixedWithFixed(ncn, nullCheck);

        state.trackNN(object, nullCheck);
    }

    /**
     * The {@link com.oracle.graal.nodes.AbstractEndNode} at the end of the current code path
     * contributes values to {@link com.oracle.graal.nodes.PhiNode}s. Now is a good time to
     * {@link EquationalReasoner#deverbosify(com.oracle.graal.graph.Node)
     * EquationalReasoner#deverbosify} those values.
     *
     * <p>
     * Precondition: inputs haven't been deverbosified yet.
     * </p>
     */
    private void visitAbstractEndNode(AbstractEndNode endNode) {
        MergeNode merge = endNode.merge();
        for (PhiNode phi : merge.phis()) {
            if (phi instanceof ValuePhiNode && phi.getKind() == Kind.Object) {
                assert phi.verify();
                int index = merge.phiPredecessorIndex(endNode);
                ValueNode original = phi.valueAt(index);
                ValueNode reduced = (ValueNode) reasoner.deverbosify(original);
                if (reduced != original) {
                    phi.setValueAt(index, reduced);
                    // `original` if unused will be removed in finished()
                }
            }
        }
    }

    /**
     * <p>
     * For one or more `invoke` arguments, flow-sensitive information may suggest their narrowing or
     * simplification. In those cases, a new
     * {@link com.oracle.graal.nodes.java.MethodCallTargetNode MethodCallTargetNode} is prepared
     * just for this callsite, consuming reduced arguments.
     * </p>
     *
     * <p>
     * Specializing the {@link com.oracle.graal.nodes.java.MethodCallTargetNode
     * MethodCallTargetNode} as described above may enable two optimizations:
     * <ul>
     * <li>
     * devirtualization of an {@link com.oracle.graal.nodes.CallTargetNode.InvokeKind#Interface} or
     * {@link com.oracle.graal.nodes.CallTargetNode.InvokeKind#Virtual} callsite (devirtualization
     * made possible after narrowing the type of the receiver)</li>
     * <li>
     * (future work) actual-argument-aware inlining, ie, to specialize callees on the types of
     * arguments other than the receiver (examples: multi-methods, the inlining problem, lambdas as
     * arguments).</li>
     *
     * </ul>
     * </p>
     *
     * <p>
     * Precondition: inputs haven't been deverbosified yet.
     * </p>
     */
    private void visitInvoke(Invoke invoke) {
        if (invoke.asNode().stamp() instanceof IllegalStamp) {
            return; // just to be safe
        }
        boolean isMethodCallTarget = invoke.callTarget() instanceof MethodCallTargetNode;
        if (!isMethodCallTarget) {
            return;
        }
        FlowUtil.replaceInPlace(invoke.asNode(), invoke.callTarget(), deverbosifyInputsCopyOnWrite((MethodCallTargetNode) invoke.callTarget()));
        MethodCallTargetNode callTarget = (MethodCallTargetNode) invoke.callTarget();
        if (callTarget.invokeKind() != CallTargetNode.InvokeKind.Interface && callTarget.invokeKind() != CallTargetNode.InvokeKind.Virtual) {
            return;
        }
        ValueNode receiver = callTarget.receiver();
        if (receiver == null) {
            return;
        }
        if (!FlowUtil.hasLegalObjectStamp(receiver)) {
            return;
        }
        Witness w = state.typeInfo(receiver);
        ResolvedJavaType type;
        ResolvedJavaType stampType = StampTool.typeOrNull(receiver);
        if (w == null || w.cluelessAboutType()) {
            // can't improve on stamp but wil try to devirtualize anyway
            type = stampType;
        } else {
            type = FlowUtil.tighten(w.type(), stampType);
        }
        if (type == null) {
            return;
        }
        ResolvedJavaMethod method = type.resolveMethod(callTarget.targetMethod(), invoke.getContextType());
        if (method == null) {
            return;
        }
        if (method.canBeStaticallyBound() || Modifier.isFinal(type.getModifiers())) {
            metricMethodResolved.increment();
            callTarget.setInvokeKind(CallTargetNode.InvokeKind.Special);
            callTarget.setTargetMethod(method);
        }
    }

}
