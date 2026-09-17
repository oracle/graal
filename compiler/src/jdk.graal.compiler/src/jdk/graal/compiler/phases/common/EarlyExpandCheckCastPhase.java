/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.core.common.GraalOptions.OptConvertDeoptsToGuards;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Optimize {@code checkcast} bytecodes early in the compilation pipeline by expanding them to
 * control flow making them amenable to code duplication, escape analysis and loop unrolling.
 *
 * The {@code checkcast} bytecode checks that the top of the operand stack can be cast to a given
 * type. Its semantics are shown by the following pseudo code
 *
 * <pre>
 * if (!(object == null || object instanceof clazz)) {
 *     throw new ClassCastException();
 * }
 * </pre>
 *
 * which is commonly represented by Graal via a guard (or explicit control flow, this phase handles
 * both).
 *
 * A Graal guard would look like
 *
 * <pre>
 * guard(obj instanceof class | null) // instance of that allows null, i.e., null yields true
 * </pre>
 *
 * This representation can then be lowered to a null check and a type check excluding null of the
 * form
 *
 * <pre>
 * guard(obj == null || obj instanceof class) // short circuit or operation
 * </pre>
 *
 * The short circuit logic representation of this operation is suboptimal for optimization because
 * it does not expose control flow (yet). {@link ShortCircuitOrNode} operations are bad for the
 * optimizer because they hide the real control flow of the operation. Thus, this phase expands the
 * control flow earlier for checkcast and lets the high tier optimizer handle the control flow.
 *
 * This phase explicitly only looks for checkcast patterns and ignores others. Foremost, it does not
 * handle general {@link ShortCircuitOrNode} expansion in high tier, as that involves complex pi
 * building which is non-trivial. This phase only processes known checkcast patterns.
 *
 * It takes a pattern of the form
 *
 * <pre>
 * g1 = guard(obj instanceof class | null);
 * pi1 = pi(g1,obj); // stamp == union(null,class)
 * </pre>
 *
 * rewrites it to
 *
 * <pre>
 * g1 = guard(obj == null || obj instanceof class);
 * pi1 = pi(g1, obj); // stamp == union(null,class)
 * </pre>
 *
 * expands it to
 *
 * <pre>
 * if (obj == null || obj instanceof class) {
 *     pi1 = pi(predecessor, obj); // stamp == union(null,class)
 * } else {
 *     deopt(ClassCastException);
 * }
 * </pre>
 *
 * and finally expands the {@link ShortCircuitOrNode} to
 *
 * <pre>
 * if (obj == null) {
 *     goto trueSucc;
 * } else {
 *     if (obj instanceof Class) {
 *          goto trueSucc;
 *     } else {
 *          goto falseSucc;
 *     }
 * }
 * trueSucc:
 *      pi1 = pi(predecessor, obj); // stamp == union(null,class)
 * falseSucc:
 *      deopt(ClassCastException);
 * </pre>
 *
 * the pi nodes at that position no longer are correct since they hang off the merge now at the
 * {@code trueSucc} labels. Thus, we build a new phi with the correct pi nodes on both conditions.
 *
 * <pre>
 * if (obj == null) {
 *     p1 = pi(predecessor, pistamp = null);
 *     goto trueSucc;
 * } else {
 *     if (obj instanceof class) {
 *         p2 = pi(predecessor, pistamp = class non null);
 *         goto trueSucc;
 *     } else {
 *         goto falseSucc;
 *     }
 * }
 * trueSucc:
 *      merge: pi = phi(p1,p2) // stamp = union(null,class)
 * falseSucc:
 *      deopt(ClassCastException);
 * </pre>
 */
public class EarlyExpandCheckCastPhase extends PostRunCanonicalizationPhase<HighTierContext> {

    public EarlyExpandCheckCastPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunBefore(this, StageFlag.HIGH_TIER_LOWERING, graphState));
    }

    /**
     * Expand the given {@link InstanceOfNode} which represents a {@code checkcast} bytecode to its
     * individual logic parts: a {@link IsNullNode} null check and a {@code InstanceOfNode}
     * typecheck without {@code null}. If any individual logic node optimizes during construction
     * already to something simpler this method returns {@code null} because expansion is not
     * strictly necessary. The optimizer can take care of the pattern else where.
     */
    @SuppressWarnings("try")
    private static LogicNode expandCheckCastInstanceOfToShortCircuitOr(InstanceOfNode instanceOfNode) {
        try (DebugCloseable closable = instanceOfNode.withNodeSourcePosition()) {
            StructuredGraph graph = instanceOfNode.graph();
            final ValueNode originalInput = instanceOfNode.getValue();
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before expanding checkCast %s", instanceOfNode);
            ValueNode object = instanceOfNode.getValue();
            LogicNode newTypeCheck = graph.addWithoutUniqueWithInputs(InstanceOfNode.create(instanceOfNode.type(), object, instanceOfNode.profile(), instanceOfNode.getAnchor()));
            if (!(newTypeCheck instanceof InstanceOfNode && ((InstanceOfNode) newTypeCheck).getValue() == originalInput)) {
                newTypeCheck.safeDelete();
                return null;
            }
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After expanding checkCast %s type guard %s", instanceOfNode, newTypeCheck);
            LogicNode newNullCheck = graph.addWithoutUnique(IsNullNode.create(object));
            if (!(newNullCheck instanceof IsNullNode && ((IsNullNode) newNullCheck).getValue() == originalInput)) {
                newNullCheck.safeDelete();
                return null;
            }
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After expanding checkCast %s null guard %s", instanceOfNode, newNullCheck);
            LogicNode newCondition = LogicNode.or(newNullCheck, newTypeCheck, BranchProbabilityNode.NOT_LIKELY_PROFILE);
            instanceOfNode.replaceAndDelete(newCondition);
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After expanding checkCast %s", instanceOfNode);
            return newCondition;
        }
    }

    /**
     * Duplicate the condition input {@code input} to {@code usage} if {@code input}
     * {@link ValueNode#hasMoreThanOneUsage()}.
     */
    @SuppressWarnings("unchecked")
    private static InstanceOfNode ensureSingleUsage(FixedNode usage, InstanceOfNode input) {
        StructuredGraph graph = usage.graph();
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before duplicating input %s of %s", input, usage);
        if (!input.hasExactlyOneUsage()) {
            InstanceOfNode copy = (InstanceOfNode) input.copyWithInputs(true);
            usage.replaceFirstInput(input, copy);
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After duplicating input %s of %s, new input %s", input, usage, copy);
            return copy;
        }
        return input;
    }

    private static ResolvedJavaType getCheckedType(LogicNode condition) {
        if (condition instanceof InstanceOfNode) {
            InstanceOfNode instanceOfNode = (InstanceOfNode) condition;
            if (instanceOfNode.allowsNull()) {
                return instanceOfNode.type().getType();
            }
        }
        return null;
    }

    private static ResolvedJavaType isCheckCastOf(ResolvedJavaType guardCheckedType, PiNode piNode, ValueNode typeCheckInput) {
        Stamp piStamp = piNode.piStamp();
        if (typeCheckInput != piNode.object()) {
            return null;
        }
        if (!(piStamp instanceof ObjectStamp)) {
            return null;
        }
        ObjectStamp os = (ObjectStamp) piStamp;
        ResolvedJavaType checkedType = os.type();
        if (os.nonNull()) {
            // not a check cast, stamp does not allow null
            return null;
        }
        if (guardCheckedType.equals(checkedType)) {
            return checkedType;
        }
        return null;
    }

    private static class CheckCast {
        FixedNode fixedUsage;
        InstanceOfNode condition;
        Runnable beforeAction;
        EconomicSet<PiNode> checkCastPis;

        CheckCast(FixedNode fixedUsage, InstanceOfNode condition, Runnable beforeAction) {
            this.fixedUsage = fixedUsage;
            this.condition = condition;
            this.beforeAction = beforeAction;
        }

    }

    @SuppressWarnings("try")
    private static void runEpilog(StructuredGraph graph, EconomicSetNodeEventListener changedNodes, HighTierContext context) {
        if (OptConvertDeoptsToGuards.getValue(graph.getOptions())) {
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before rewriting new deopts again to guards");
            LazyValue<LoopsData> lazyLoops = new LazyValue<>(() -> context.getLoopsDataProvider().getLoopsData(graph));
            for (Node n : changedNodes.getNodes()) {
                if (n.isAlive() && n instanceof DeoptimizeNode) {
                    DeoptimizeNode d = (DeoptimizeNode) n;
                    assert d.isAlive() : d;
                    try (DebugCloseable closable = d.withNodeSourcePosition()) {
                        ConvertDeoptimizeToGuardPhase.propagateFixed(d, d, context, lazyLoops, true);
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings("try")
    protected void run(final StructuredGraph graph, HighTierContext context) {
        List<CheckCast> checkCasts = new ArrayList<>(4);
        for (FixedGuardNode fixedGuard : graph.getNodes(FixedGuardNode.TYPE)) {
            processGuard(checkCasts, fixedGuard);
        }
        for (IfNode ifNode : graph.getNodes(IfNode.TYPE)) {
            processIf(checkCasts, ifNode);
        }
        if (!checkCasts.isEmpty()) {
            EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.NODE_ADDED));
            try (Graph.NodeEventScope scope = graph.trackNodeEvents(ec)) {
                expandCheckCast(graph, checkCasts);
            }
            runEpilog(graph, ec, context);
        }
    }

    private static void expandCheckCast(final StructuredGraph graph, List<CheckCast> checkCasts) {
        for (CheckCast checkcast : checkCasts) {
            InstanceOfNode uniqueUsageLogic = ensureSingleUsage(checkcast.fixedUsage, checkcast.condition);
            graph.getOptimizationLog().report(DebugContext.VERY_DETAILED_LEVEL, EarlyExpandCheckCastPhase.class, "Early Expand CheckCast", uniqueUsageLogic);
            LogicNode newCondition = expandCheckCastInstanceOfToShortCircuitOr(uniqueUsageLogic);
            if (newCondition != null) {
                final FrameState lastState = GraphUtil.findLastFrameState(checkcast.fixedUsage);
                if (checkcast.beforeAction != null) {
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before running beforeAction");
                    checkcast.beforeAction.run();
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After running beforeAction");
                }
                if (newCondition instanceof ShortCircuitOrNode) {
                    ShortCircuitOrNode binary = (ShortCircuitOrNode) newCondition;
                    GraalError.guarantee(binary.hasExactlyOneUsage(), "Must have exactly one usage at %s", binary);
                    Node usage = binary.singleUsage();
                    GraalError.guarantee(usage instanceof IfNode, "Must have exactly one if node usage at %s", binary);
                    GuardPhiNode guardPhi = ExpandLogicPhase.processIf(binary.getX(), binary.isXNegated(), binary.getY(), binary.isYNegated(), (IfNode) usage,
                                    binary.getShortCircuitProbability().getDesignatedSuccessorProbability(), true);
                    final BeginNode nullGuard = (BeginNode) guardPhi.valueAt(0);
                    final BeginNode typeGuard = (BeginNode) guardPhi.valueAt(1);
                    GraalError.guarantee(nullGuard.predecessor() instanceof IfNode &&
                                    (((IfNode) nullGuard.predecessor()).condition() instanceof IsNullNode || ((IfNode) nullGuard.predecessor()).condition() instanceof LogicConstantNode),
                                    "Must be null check part of checkcast %s", nullGuard);
                    GraalError.guarantee(typeGuard.predecessor() instanceof IfNode &&
                                    (((IfNode) typeGuard.predecessor()).condition() instanceof InstanceOfNode || ((IfNode) typeGuard.predecessor()).condition() instanceof LogicConstantNode),
                                    "Must be type check part of checkcast %s", typeGuard);
                    MergeNode newMerge = (MergeNode) guardPhi.merge();
                    // Mark this merge for exploration so duplication simulation does not prune it
                    // by frequency and can still simplify the unlikely null branch.
                    newMerge.setDuplicationHint(MergeNode.DuplicationHint.EXPLORE);
                    assert newMerge.stateAfter() == null : newMerge + " " + newMerge.stateAfter();
                    newMerge.setStateAfter(lastState.duplicateWithVirtualState());
                    if (checkcast.checkCastPis != null) {
                        // build 2 new pis and a phi for each pi
                        for (PiNode checkCastPi : checkcast.checkCastPis) {
                            PiNode nullPortionCheckCast = graph.addWithoutUnique(new PiNode(checkCastPi.object(), StampFactory.alwaysNull(), nullGuard));
                            PiNode typeCheckPortionCheckCast = graph.addWithoutUnique(new PiNode(checkCastPi.object(), ((ObjectStamp) checkCastPi.piStamp()).asNonNull(), typeGuard));
                            ValuePhiNode vpn = graph.addWithoutUnique(new ValuePhiNode(checkCastPi.stamp(NodeView.DEFAULT).unrestricted(), newMerge));
                            vpn.addInput(nullPortionCheckCast);
                            vpn.addInput(typeCheckPortionCheckCast);
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating phi %s for %s", vpn, checkCastPi);
                            vpn.inferStamp();
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After infer stamp on %s", vpn);
                            checkCastPi.replaceAndDelete(vpn);
                            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After replacing %s with %s", checkCastPi, vpn);
                        }
                    }
                    guardPhi.safeDelete();
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After expanding %s", uniqueUsageLogic);
                }
            }
        }
    }

    private static void processIf(List<CheckCast> checkCasts, IfNode ifNode) {
        ResolvedJavaType checkedType = getCheckedType(ifNode.condition());
        if (checkedType != null) {
            CheckCast p = new CheckCast(ifNode, (InstanceOfNode) ifNode.condition(), null);
            checkCasts.add(p);
            for (Node usage : ifNode.trueSuccessor().usages()) {
                if (usage instanceof PiNode) {
                    ResolvedJavaType piChecked = isCheckCastOf(checkedType, (PiNode) usage, ((InstanceOfNode) ifNode.condition()).getValue());
                    if (piChecked != null) {
                        if (p.checkCastPis == null) {
                            p.checkCastPis = EconomicSet.create();
                        }
                        p.checkCastPis.add((PiNode) usage);
                    }
                }
            }
        }
    }

    private static void processGuard(List<CheckCast> checkCasts, FixedGuardNode fixedGuard) {
        ResolvedJavaType checkedType = getCheckedType(fixedGuard.condition());
        if (checkedType != null) {
            // only process guards for check casts
            if (fixedGuard.hasMoreThanOneUsage()) {
                return;
            }
            PiNode checkedPi = null;
            if (fixedGuard.hasUsages()) {
                Node singleUsage = fixedGuard.singleUsage();
                if (!(singleUsage instanceof PiNode)) {
                    return;
                }
                checkedPi = (PiNode) singleUsage;
                ResolvedJavaType piCheckedType = isCheckCastOf(checkedType, checkedPi, ((InstanceOfNode) fixedGuard.condition()).getValue());
                if (piCheckedType == null) {
                    return;
                }
            }
            CheckCast p = new CheckCast(fixedGuard, (InstanceOfNode) fixedGuard.condition(), () -> {
                fixedGuard.lowerToIf();
            });
            if (checkedPi != null) {
                p.checkCastPis = EconomicSet.create();
                p.checkCastPis.add(checkedPi);
            }
            checkCasts.add(p);
        }
    }

}
