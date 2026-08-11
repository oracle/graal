/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.guards.optimistic.memory;

import java.util.ArrayList;
import java.util.Optional;

import org.graalvm.collections.Pair;

import jdk.graal.compiler.guards.optimistic.OptimisticFixedGuardNode;
import jdk.graal.compiler.guards.optimistic.OptimisticGuardNode;

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.serviceprovider.SpeculationReasonGroup;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.SpeculationReason;
import jdk.vm.ci.meta.TriState;

/**
 * Introduces {@link OptimisticMemoryEdge} nodes where possible. These edges capture possible
 * aliasing relationships between memory accesses inside loops.
 * <p/>
 *
 * If speculation is allowed, this phase places guards with associated speculations. Otherwise
 * (i.e., in hosted compilations on SubstrateVM) it associates the aliasing condition with the loop
 * begin node in the form of an {@linkplain LoopBeginNode#getInterIterationAliasingGuard()
 * inter-iteration aliasing guard}.
 *
 * @see OptimisticMemoryEdge
 */
public class OptimisticAliasingAnalysisPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    public static class Options {
        // @formatter:off
        @Option(help = "Use speculation and deoptimization in optimistic aliasing analysis.", type = OptionType.Debug)
        public static final OptionKey<Boolean> OptimisticAliasingUseSpeculation = new OptionKey<>(true);
        // @formatter:on
    }

    /**
     * Only analyze loops containing up to this number of write nodes. Larger loops are highly
     * unlikely to be vectorizable, so we can save compile time by giving up on them earlier.
     */
    private static final int MAX_WRITES_PER_LOOP = 16;

    /**
     * Stop certain searches in the graph after this number of iterations, returning an unknown
     * value.
     */
    private static final int MAX_SEARCH_ITERATIONS = 32;

    public OptimisticAliasingAnalysisPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public float codeSizeIncrease() {
        return 2.0f;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        /* This phase introduces guards that will need frame states assigned. */
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (!graph.isAfterStage(StageFlag.FLOATING_READS)) {
            /*
             * This phase works by modifying the memory graph, so it can't do anything if there is
             * no memory graph. We don't want to make floating reads a formal precondition for this
             * phase, because this phase is a formal precondition for loop vectorization, and loop
             * vectorization can still do some limited work even without floating reads.
             */
            return;
        }

        /* This phase can be run during snippet lowering, where no MidTierContext is available. */
        if (context instanceof MidTierContext midTierContext) {
            if (midTierContext.getProfilingInfo() != null && midTierContext.getProfilingInfo().getDeoptimizationCount(DeoptimizationReason.Aliasing) > 0) {
                return;
            }
        }

        if (graph.hasLoops()) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();
            for (Loop loop : loopsData.countedLoops()) {
                CountedLoopInfo cli = loop.counted();
                boolean overflowChecked = cli.loopCanNeverOverflow();
                if (!overflowChecked) {
                    assert !cli.counterNeverOverflows() : "canNeverOverflow was false";
                    assert cli.getOverFlowGuard() == null : "If canNeverOverflow was false the guard must not have been created yet";
                    if (graph.getGuardsStage().allowsFloatingGuards()) {
                        cli.createOverFlowGuard();
                        overflowChecked = true;
                    }
                }
                // only process loops without overflow
                if (overflowChecked) {
                    analyseLoop(loop, context.getConstantReflection());
                }
            }
        }
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        if (!graphState.isAfterStage(StageFlag.OPTIMISTIC_ALIASING)) {
            graphState.setAfterStage(StageFlag.OPTIMISTIC_ALIASING);
        }
        graphState.addFutureStageRequirement(StageFlag.OPTIMISTIC_GUARDS);
    }

    private static class BaseOffsetPair {
        BaseOffsetPair(ValueNode base, InductionVariable offset) {
            this.base = base;
            this.offset = offset;
        }

        public ValueNode base;
        public InductionVariable offset;
    }

    private static BaseOffsetPair rawAddressBaseOffsetPair(OffsetAddressNode offsetAddress, Loop loop) {
        // Due to OptimizeAddressesInLoopsPhase the address should have a loop invariant base and a
        // varying offset.
        ValueNode addressBase = offsetAddress.getBase();
        ValueNode addressOffset = offsetAddress.getOffset();
        InductionVariable offsetIv = loop.getInductionVariables().get(addressOffset);
        if (loop.isOutsideLoop(addressBase) && offsetIv != null) {
            return new BaseOffsetPair(addressBase, offsetIv);
        }
        return null;
    }

    private static ValueNode getBase(AddressableMemoryAccess access, Loop loop) {
        AddressNode address = access.getAddress();
        if (address instanceof OffsetAddressNode) {
            OffsetAddressNode offsetAddress = (OffsetAddressNode) address;
            ValueNode base = offsetAddress.getBase();
            if (base.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp) {
                /*
                 * Nodes returned by this method will be compared by pointer equality. Strip pis,
                 * value anchors, etc., so that we compare the underlying node of interest.
                 */
                return GraphUtil.unproxifyExceptLoopProxies(base);
            } else {
                BaseOffsetPair baseOffsetPair = rawAddressBaseOffsetPair(offsetAddress, loop);
                if (baseOffsetPair != null) {
                    return baseOffsetPair.base;
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    private static InductionVariable getOffsetInductionVariable(AddressableMemoryAccess access, Loop loop) {
        AddressNode address = access.getAddress();
        if (address instanceof OffsetAddressNode) {
            OffsetAddressNode offsetAddress = (OffsetAddressNode) address;
            InductionVariable iv = loop.getInductionVariables().get(offsetAddress.getOffset());
            if (iv != null) {
                return iv;
            } else {
                BaseOffsetPair baseOffsetPair = rawAddressBaseOffsetPair(offsetAddress, loop);
                if (baseOffsetPair != null) {
                    return baseOffsetPair.offset;
                } else {
                    return null;
                }
            }
        } else {
            return null;
        }
    }

    private void analyseLoop(Loop loop, ConstantReflectionProvider constantReflection) {
        int writes = loop.whole().nodes().filter(WriteNode.class).count();
        if (writes > MAX_WRITES_PER_LOOP) {
            return;
        }
        if (loop.whole().nodes().filter(WriteNode.class).filter(
                        n -> ((WriteNode) n).ordersMemoryAccesses()).isNotEmpty()) {
            // Ordered writes are considered to alias everything in the loop.
            return;
        }
        /*
         * This analysis must not depend on a guard introduced by a previous analysis. The old guard
         * might not be an immediate predecessor of the loop anymore, so newly introduced guard
         * conditions could be anchored after the guard. This would be not schedulable.
         */
        loop.loopBegin().setInterIterationAliasingGuard(null);
        for (FloatingReadNode read : loop.whole().nodes().filter(FloatingReadNode.class)) {
            ValueNode readBase = getBase(read, loop);
            if (readBase != null && readBase.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp && loop.isOutsideLoop(readBase)) {
                boolean continueAnalysis = analyseLoopRead(loop, read, constantReflection);
                if (!continueAnalysis) {
                    return;
                }
            }
        }
        for (ReadNode read : loop.whole().nodes().filter(ReadNode.class)) {
            if (read.ordersMemoryAccesses()) {
                // Ordered reads are considered to alias everything in the loop.
                return;
            }
            ValueNode readBase = getBase(read, loop);
            if (readBase != null && readBase.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp && loop.isOutsideLoop(readBase)) {
                boolean continueAnalysis = analyseLoopRead(loop, read, constantReflection);
                if (!continueAnalysis) {
                    return;
                }
            } else if (readBase != null && readBase.stamp(NodeView.DEFAULT) instanceof IntegerStamp && loop.isOutsideLoop(readBase)) {
                // This is an unsafe access to a raw off-heap address in a long.
                boolean continueAnalysis = analyseLoopRead(loop, read, constantReflection);
                if (!continueAnalysis) {
                    return;
                }
            }
        }
        for (WriteNode write : loop.whole().nodes().filter(WriteNode.class)) {
            ValueNode object = getBase(write, loop);
            if (object != null && loop.isOutsideLoop(object)) {
                boolean continueAnalysis = analyseLoopWriteAndPrecedingWrites(loop, write, constantReflection);
                if (!continueAnalysis) {
                    return;
                }
            }
        }
    }

    @SuppressWarnings("try")
    private boolean analyseLoopWriteAndPrecedingWrites(Loop loop, WriteNode write, ConstantReflectionProvider constantReflection) {
        // Look at this write and all possibly aliasing writes preceding it in the loop. Do not look
        // at later writes: Those will be visited when this method is called on them. Overall this
        // ensures that all pairs of writes in the loop are visited once.
        MemoryKill lastLocationAccess = write.getLastLocationAccess();

        ArrayList<WriteNode> potentiallyAliasingWritesBeforeWrite = new ArrayList<>();

        while (lastLocationAccess != null && (lastLocationAccess instanceof OptimisticMemoryEdge || !loop.isOutsideLoop(lastLocationAccess.asNode()))) {
            // Skip optimistic edges we may already have created for earlier writes in the loop.
            while (lastLocationAccess instanceof OptimisticMemoryEdge) {
                lastLocationAccess = ((OptimisticMemoryEdge) lastLocationAccess).getConservativeEdge();
            }
            if (loop.isOutsideLoop(lastLocationAccess.asNode())) {
                break;
            }

            if (lastLocationAccess instanceof WriteNode) {
                // potential aliasing
                WriteNode potentiallyAliasingWrite = (WriteNode) lastLocationAccess;
                ValueNode object = getBase(potentiallyAliasingWrite, loop);
                if (object != null && loop.isOutsideLoop(object)) {
                    potentiallyAliasingWritesBeforeWrite.add(potentiallyAliasingWrite);
                    lastLocationAccess = potentiallyAliasingWrite.getLastLocationAccess();
                } else {
                    return false;
                }
            } else if (lastLocationAccess instanceof MemoryPhiNode) {
                MemoryPhiNode phi = (MemoryPhiNode) lastLocationAccess;
                if (phi.merge() != loop.loopBegin()) {
                    // control flow inside the loop
                    return false;
                } else {
                    // reached the start of the loop, we're done
                    break;
                }
            } else {
                return false;
            }
        }

        if (lastLocationAccess != null && potentiallyAliasingWritesBeforeWrite.size() != 0) {
            SpeculationLog.Speculation speculation = trySpeculation(loop.loopBegin());
            if (speculation != null) {
                // place optimistic aliasing guards
                LogicNode isAliasing = testPotentialAliasing(loop, write, potentiallyAliasingWritesBeforeWrite, false, constantReflection);
                if (isAliasing.isTautology()) {
                    // Definite aliasing, no use placing optimistic guards.
                    return false;
                }

                try (DebugCloseable position = write.withNodeSourcePosition()) {
                    OptimisticMemoryEdge edge = buildOptimisticMemoryEdge(loop, write, isAliasing, lastLocationAccess, speculation);
                    if (edge == null) {
                        return false;
                    }
                    write.setLastLocationAccess(edge);
                }
            }
        }

        return true;
    }

    /**
     * Analyze the given read for aliasing with writes in the loop. Place optimistic aliasing edges
     * as appropriate.
     *
     * @return {@code true} if any possible aliasing was handled and the analysis can continue;
     *         {@code false} if there is definite aliasing, or some aliasing pattern that we don't
     *         know how to handle, and analysis of this loop should stop
     */
    @SuppressWarnings("try")
    private <Read extends ValueNode & AddressableMemoryAccess> boolean analyseLoopRead(Loop loop, Read read, ConstantReflectionProvider constantReflection) {
        MemoryKill lastLocationAccess = read.getLastLocationAccess();
        MemoryPhiNode loopPhi = null;

        ArrayList<WriteNode> potentialAliasingWritesBeforeRead = new ArrayList<>();
        ArrayList<WriteNode> potentialAliasingWritesAfterRead = new ArrayList<>();

        // find lastLocationAccess outside loop
        ArrayList<WriteNode> potentialAliasingWrites = potentialAliasingWritesBeforeRead;
        while (lastLocationAccess != null && !loop.isOutsideLoop(lastLocationAccess.asNode())) {
            if (lastLocationAccess instanceof WriteNode) {
                // potential aliasing
                WriteNode write = (WriteNode) lastLocationAccess;
                ValueNode writeBase = getBase(write, loop);
                if (writeBase != null && loop.isOutsideLoop(writeBase)) {
                    potentialAliasingWrites.add(write);
                    lastLocationAccess = write.getLastLocationAccess();
                } else {
                    return false;
                }
            } else if (lastLocationAccess instanceof MemoryPhiNode) {
                MemoryPhiNode phi = (MemoryPhiNode) lastLocationAccess;
                if (phi.merge() != loop.loopBegin()) {
                    return false;
                } else if (loopPhi == null) {
                    // this is the first time we see a loop phi
                    loopPhi = phi;
                    ValueNode singleBackValue = phi.singleBackValueOrThis();
                    if (singleBackValue == phi) {
                        singleBackValue = null;
                    }
                    lastLocationAccess = (MemoryKill) singleBackValue;

                    // we have crossed the loop header
                    // all writes we find from now on are after the read
                    potentialAliasingWrites = potentialAliasingWritesAfterRead;
                } else if (loopPhi == phi) {
                    // this is the second time we see this phi
                    lastLocationAccess = (MemoryKill) phi.valueAt(loop.loopBegin().forwardEnd());
                } else {
                    // This case is an endless loop due to an unexpected graph shape => bailout.
                    // This can happen very rarely on inverted loops, when a read's last location
                    // access is a memory phi on the loop begin which doesn't loop around itself but
                    // has a looping memory phi on the same loop begin as its input. We cannot
                    // currently do anything useful with this shape.
                    return false;
                }
            } else {
                return false;
            }
        }

        if (lastLocationAccess != null && (potentialAliasingWritesAfterRead.size() + potentialAliasingWritesBeforeRead.size()) != 0) {
            SpeculationLog.Speculation speculation = trySpeculation(loop.loopBegin());
            if (speculation != null) {
                // If a branch condition might read the result of a write from an earlier iteration,
                // don't analyze array indices, only whether the accesses are to the same object.
                boolean useStrictObjectEquality = nodeUsedByIfConditionInLoop(loop, read) && !potentialAliasingWritesAfterRead.isEmpty();
                // place optimistic aliasing guards
                LogicNode aliasingWritesBefore = testPotentialAliasing(loop, read, potentialAliasingWritesBeforeRead, useStrictObjectEquality, constantReflection);
                LogicNode aliasingWritesAfter = testPotentialAliasing(loop, read, potentialAliasingWritesAfterRead, useStrictObjectEquality, constantReflection);

                if ((aliasingWritesBefore == null || aliasingWritesBefore.isTautology()) && (aliasingWritesAfter == null || aliasingWritesAfter.isTautology())) {
                    // Definite aliasing, no use placing optimistic guards.
                    return false;
                }

                LogicNode isAliasing;
                if (aliasingWritesBefore == null) {
                    isAliasing = aliasingWritesAfter;
                } else if (aliasingWritesAfter == null) {
                    isAliasing = aliasingWritesBefore;
                } else {
                    isAliasing = LogicNode.or(aliasingWritesBefore, aliasingWritesAfter, BranchProbabilityNode.SLOW_PATH_PROFILE);
                }

                try (DebugCloseable position = read.withNodeSourcePosition()) {
                    OptimisticMemoryEdge edge = buildOptimisticMemoryEdge(loop, read, isAliasing, lastLocationAccess, speculation);
                    if (edge == null) {
                        return false;
                    }
                    read.setLastLocationAccess(edge);
                }
            }
        }

        return true;
    }

    private static boolean nodeUsedByIfConditionInLoop(Loop loop, ValueNode node) {
        NodeBitMap visited = new NodeBitMap(node.graph());
        NodeStack toProcess = new NodeStack();
        toProcess.push(node);
        while (!toProcess.isEmpty()) {
            Node cur = toProcess.pop();
            visited.mark(cur);
            for (Node usage : cur.usages()) {
                if (visited.isMarked(usage) || loop.isOutsideLoop(usage) || (usage instanceof MemoryPhiNode && ((MemoryPhiNode) usage).merge() == loop.loopBegin())) {
                    continue;
                }
                if (usage instanceof LogicNode) {
                    return true;
                }
                if (!(usage instanceof ValueNode)) {
                    // ignore things like frame states
                    continue;
                }
                toProcess.push(usage);
            }
        }
        return false;
    }

    /**
     * Try to build an optimistic memory edge with a guard that captures the possible aliasing
     * between {@code access} and {@code lastLocationAccess}.
     *
     * @return the optimistic memory edge, or {@code null} if no edge could be built because we
     *         cannot build a valid guard
     */
    private OptimisticMemoryEdge buildOptimisticMemoryEdge(Loop loop,
                    MemoryAccess access, LogicNode isAliasing, MemoryKill lastLocationAccess,
                    SpeculationLog.Speculation speculation) {
        StructuredGraph graph = loop.loopBegin().graph();
        GuardingNode guard = null;
        if (!canUseSpeculation(graph)) {
            GraalError.guarantee(speculation.equals(SpeculationLog.NO_SPECULATION), "expected no speculation, can not speculate on %s", speculation);
            if (!canAssignValidState(loop.loopBegin().forwardEnd())) {
                return null;
            }
            guard = recordLoopAliasing(loop.loopBegin(), isAliasing);
        } else if (graph.getGuardsStage().allowsFloatingGuards()) {
            GraalError.guarantee(!speculation.equals(SpeculationLog.NO_SPECULATION), "need proper speculation: %s", speculation);
            AbstractBeginNode anchor = AbstractBeginNode.prevBegin(loop.entryPoint());
            if (!canAssignValidState(anchor)) {
                return null;
            }
            guard = graph.unique(new OptimisticGuardNode(isAliasing, anchor, DeoptimizationReason.Aliasing, DeoptimizationAction.InvalidateRecompile, speculation, true, null));
        } else {
            GraalError.guarantee(!speculation.equals(SpeculationLog.NO_SPECULATION), "need proper speculation: %s", speculation);
            if (!canAssignValidState(loop.loopBegin().forwardEnd())) {
                return null;
            }
            guard = buildOptimisticFixedGuard(loop.loopBegin(), isAliasing, speculation);
        }
        OptimisticMemoryEdge edge = graph.unique(new OptimisticMemoryEdge(guard, access.getLastLocationAccess(), lastLocationAccess, access.getLocationIdentity()));
        graph.getOptimizationLog().report(OptimisticAliasingAnalysisPhase.class, "OptimisticMemoryEdgeInsertion", access.asNode());
        return edge;
    }

    /**
     * Returns {@code true} if we can build a deoptimizing guard inserted or anchored at the
     * {@code anchoringPoint}. Returns {@code false} if we can't do this because the dominating
     * frame state is not valid for deoptimization.
     */
    private static boolean canAssignValidState(FixedNode anchoringPoint) {
        FrameState lastState = GraphUtil.findLastFrameState(anchoringPoint);
        if (lastState == null || !lastState.isValidForDeoptimization()) {
            return false;
        }
        return true;
    }

    /**
     * Build a guard with the given condition and place it before the loop begin.
     */
    protected OptimisticFixedGuardNode buildOptimisticFixedGuard(LoopBeginNode loopBegin, LogicNode isAliasing, SpeculationLog.Speculation speculation) {
        StructuredGraph graph = loopBegin.graph();
        OptimisticFixedGuardNode fixedGuard = graph.add(createOptimisticFixedGuard(isAliasing, speculation));
        AbstractEndNode forwardEnd = loopBegin.forwardEnd();
        FixedWithNextNode pred = (FixedWithNextNode) forwardEnd.predecessor();
        pred.setNext(fixedGuard);
        fixedGuard.setNext(forwardEnd);
        return fixedGuard;
    }

    protected OptimisticFixedGuardNode createOptimisticFixedGuard(LogicNode isAliasing, SpeculationLog.Speculation speculation) {
        return new OptimisticFixedGuardNode(isAliasing, DeoptimizationReason.Aliasing, DeoptimizationAction.InvalidateRecompile, speculation, true, null);
    }

    /**
     * Record the aliasing condition in the shared optimistic guard at the loop begin. Create the
     * guard if the loop begin doesn't have one yet.
     */
    private GuardingNode recordLoopAliasing(LoopBeginNode loopBegin, LogicNode isAliasing) {
        StructuredGraph graph = loopBegin.graph();
        OptimisticLoopAliasGuardNode aliasGuard = (OptimisticLoopAliasGuardNode) loopBegin.getInterIterationAliasingGuard();
        if (aliasGuard != null && aliasGuard.getGuard() == null) {
            /*
             * The guard from an earlier run of this phase was optimized out, indicating that
             * aliasing was impossible. Build a new guard based on current information.
             */
            aliasGuard = null;
        }
        if (aliasGuard == null) {
            OptimisticFixedGuardNode fixedGuard = buildOptimisticFixedGuard(loopBegin, isAliasing, SpeculationLog.NO_SPECULATION);
            aliasGuard = graph.addWithoutUnique(new OptimisticLoopAliasGuardNode(fixedGuard));
            loopBegin.setInterIterationAliasingGuard(aliasGuard);
        }
        LogicNode existingCondition = aliasGuard.getGuard().getCondition();
        if (!aliasGuard.getGuard().isNegated()) {
            /*
             * In buildOptimisticFixedGuard we always build a guard with negated=true. If this guard
             * is no longer negated, it must have been canonicalized in some way. We must compensate
             * by negating its condition.
             */
            existingCondition = graph.addOrUnique(LogicNegationNode.create(existingCondition));
        }
        if (existingCondition != isAliasing) {
            LogicNode combinedCondition = LogicNode.or(existingCondition, isAliasing, BranchProbabilityNode.NOT_LIKELY_PROFILE);
            aliasGuard.getGuard().setCondition(graph.addOrUnique(combinedCondition), true);
        }
        return aliasGuard;
    }

    private static final SpeculationReasonGroup ALIAS_LOOP_SPECULATIONS = new SpeculationReasonGroup("AliasLoop", ResolvedJavaMethod.class, int.class, DeoptimizationReason.class);

    private static SpeculationLog.Speculation trySpeculation(LoopBeginNode loopBeginNode) {
        if (!canUseSpeculation(loopBeginNode.graph())) {
            return SpeculationLog.NO_SPECULATION;
        }
        FrameState frameState = loopBeginNode.stateAfter();
        ResolvedJavaMethod method = null;
        int bci = 0;
        if (frameState != null) {
            method = frameState.getMethod();
            bci = frameState.bci;
        }
        SpeculationReason reason = ALIAS_LOOP_SPECULATIONS.createSpeculationReason(method, bci, DeoptimizationReason.Aliasing);
        SpeculationLog speculationLog = loopBeginNode.graph().getSpeculationLog();
        if (speculationLog.maySpeculate(reason)) {
            return speculationLog.speculate(reason);
        }
        return null;
    }

    // Test aliasing between an access (which may be a read or a write) and a list of writes. The
    // writes are either all before or all after the access, as given by writesAfterAccess.
    private static <ValueAccess extends ValueNode & AddressableMemoryAccess> LogicNode testPotentialAliasing(Loop loop, ValueAccess access, ArrayList<WriteNode> potentialAliasingWrites,
                    boolean useStrictObjectEquality, ConstantReflectionProvider constantReflection) {
        LogicNode isAliasing = null;
        for (WriteNode write : potentialAliasingWrites) {
            LogicNode accessWriteAliasing = testLoopAccessAliasing(loop, access, write, useStrictObjectEquality, constantReflection);
            if (accessWriteAliasing.isTautology()) {
                return accessWriteAliasing;
            }
            if (isAliasing == null) {
                isAliasing = accessWriteAliasing;
            } else {
                isAliasing = LogicNode.or(isAliasing, accessWriteAliasing, BranchProbabilityNode.SLOW_PATH_PROFILE);
            }
        }
        return isAliasing;
    }

    // Test aliasing between an access (which may be a read or a write) and a write. The write comes
    // after the access iff writeAfterAccess is true. If useStrictObjectEquality is true, any
    // accesses to the same object are considered aliasing; otherwise, analyze indices to find safe
    // cases of writes after reads.
    // Returns a LogicNode that evaluates to true if the accesses alias in a way that prevents
    // vectorization.
    private static <ValueAccess extends ValueNode & AddressableMemoryAccess> LogicNode testLoopAccessAliasing(Loop loop, ValueAccess access, WriteNode write,
                    boolean useStrictObjectEquality, ConstantReflectionProvider constantReflection) {
        StructuredGraph graph = loop.loopBegin().graph();
        boolean accessIsRead = access instanceof FloatingReadNode || access instanceof ReadNode;
        assert access.getAddress() instanceof OffsetAddressNode && write.getAddress() instanceof OffsetAddressNode : access.getAddress() + " " + write.getAddress();

        ValueNode accessBase = getBase(access, loop);
        ValueNode writeBase = getBase(write, loop);
        LogicNode objectEquals = null;
        ValueNode accessLow = null;
        ValueNode accessHigh = null;
        ValueNode writeLow = null;
        ValueNode writeHigh = null;
        boolean rawPointerComparison = false;

        Stamp accessBaseStamp = accessBase.stamp(NodeView.DEFAULT);
        Stamp writeBaseStamp = writeBase.stamp(NodeView.DEFAULT);
        if (accessBaseStamp instanceof AbstractObjectStamp && writeBaseStamp instanceof AbstractObjectStamp) {
            /* Both the access and the write are to on-heap addresses. */
            objectEquals = CompareNode.createCompareNode(graph, CanonicalCondition.EQ, accessBase, writeBase, constantReflection, NodeView.DEFAULT);
        } else if (accessBaseStamp instanceof IntegerStamp && writeBaseStamp instanceof IntegerStamp) {
            /* Both the access and the write are to raw off-heap addresses. */
            rawPointerComparison = true;

            Pair<ValueNode, ValueNode> accessBounds = accessBounds(access, loop);
            accessLow = accessBounds.getLeft();
            accessHigh = accessBounds.getRight();
            Pair<ValueNode, ValueNode> writeBounds = accessBounds(write, loop);
            writeLow = writeBounds.getLeft();
            writeHigh = writeBounds.getRight();

            if (accessLow == null || writeLow == null) {
                // We cannot analyze these accesses and must assume aliasing.
                return LogicConstantNode.tautology(graph);
            } else {
                // The objects are disjoint if accessHigh < writeLow or writeHigh < accessLow.
                // Otherwise they overlap. These are raw pointers, so use unsigned comparisons.
                LogicNode writeBelowAccess = CompareNode.createCompareNode(graph, CanonicalCondition.BT, writeHigh, accessLow, constantReflection, NodeView.DEFAULT);
                LogicNode accessBelowWrite = CompareNode.createCompareNode(graph, CanonicalCondition.BT, accessHigh, writeLow, constantReflection, NodeView.DEFAULT);
                objectEquals = LogicNode.and(writeBelowAccess, true, accessBelowWrite, true, BranchProbabilityNode.SLOW_PATH_PROFILE);
            }
        } else {
            /*
             * We're comparing an on-heap access/write to a possibly off-heap access/write using the
             * MemorySegment API. We cannot analyze these accesses yet and must assume aliasing.
             */
            return LogicConstantNode.tautology(graph);
        }

        boolean writeAfterRead = false;
        if (accessIsRead) {
            // As far as the caller could tell, writes in this loop are not guaranteed to be after
            // the read. However, if we can prove that this particular write uses the read's value,
            // then we can guarantee the ordering for this write.
            writeAfterRead = isWriteAfterNodeInLoop(write, access, loop) == TriState.TRUE;
        }

        LogicNode mayAlias = objectEquals;
        if (writeAfterRead && !useStrictObjectEquality) {
            // if the access is to the same index, we know the loop iterations are independent
            // therefore writes that come after the read don't alias regardless of object equality
            InductionVariable readIndex = getOffsetInductionVariable(access, loop);
            InductionVariable writeIndex = getOffsetInductionVariable(write, loop);

            if (readIndex != null && readIndex.isConstantStride() && writeIndex != null && writeIndex.isConstantStride()) {
                CanonicalCondition lessThanOp = CanonicalCondition.LT;
                if (rawPointerComparison) {
                    lessThanOp = CanonicalCondition.BT;
                } else {
                    assert accessLow == null && writeLow == null : accessLow + " " + writeLow;
                    accessLow = readIndex.initNode();
                    writeLow = writeIndex.initNode();
                }

                LogicNode badIndices;
                // The accesses are OK if we only ever write elements that have already been read or
                // have been skipped by the read. To ensure the read always "runs ahead" of the
                // write in this way, check that both the initial read index and the read stride are
                // greater than or equal to the initial write index and the write stride,
                // respectively. The case for negative strides is symmetric.
                if (readIndex.constantStride() > 0 && readIndex.constantStride() >= writeIndex.constantStride()) {
                    badIndices = CompareNode.createCompareNode(graph, lessThanOp, accessLow, writeLow, constantReflection, NodeView.DEFAULT);
                } else if (readIndex.constantStride() < 0 && readIndex.constantStride() <= writeIndex.constantStride()) {
                    badIndices = CompareNode.createCompareNode(graph, lessThanOp, writeLow, accessLow, constantReflection, NodeView.DEFAULT);
                } else {
                    // indices may cross
                    badIndices = LogicConstantNode.tautology(graph);
                }

                // When constantStride > 0:
                // mayAlias is equivalent to (a == b) && (a.readIndex < a.writeIndex)
                // When constantStride < 0:
                // mayAlias is equivalent to (a == b) && (a.writeIndex < a.readIndex)
                mayAlias = LogicNode.and(objectEquals, false, badIndices, false, BranchProbabilityNode.SLOW_PATH_PROFILE);
            }
        }

        return mayAlias;
    }

    private static TriState isWriteAfterNodeInLoop(WriteNode write, Node node, Loop loop) {
        NodeFlood worklist = node.graph().createNodeFlood();

        int iterations = 0;
        worklist.add(node);
        for (Node n : worklist) {
            if (n == write) {
                return TriState.TRUE;
            }
            if (n instanceof PhiNode || n instanceof FrameState) {
                continue;
            }
            for (Node usage : n.usages()) {
                if (!worklist.isMarked(usage) && !loop.isOutsideLoop(usage)) {
                    worklist.add(usage);
                }
            }
            iterations++;
            if (iterations >= MAX_SEARCH_ITERATIONS) {
                return TriState.UNKNOWN;
            }
        }

        return TriState.FALSE;
    }

    /**
     * Computes the lowest and highest byte addresses touched by a raw memory access in a loop. The
     * highest address is the address of the last byte accessed.
     */
    private static Pair<ValueNode, ValueNode> accessBounds(AddressableMemoryAccess access, Loop loop) {
        StructuredGraph graph = loop.loopBegin().graph();
        ValueNode accessBase = getBase(access, loop);
        InductionVariable accessIndex = getOffsetInductionVariable(access, loop);
        if (accessIndex == null) {
            return Pair.create(null, null);
        }
        ValueNode accessStart = AddNode.create(accessBase, accessIndex.initNode(), NodeView.DEFAULT);
        ValueNode accessEnd = AddNode.create(accessBase, accessIndex.extremumNode(true, accessBase.stamp(NodeView.DEFAULT)), NodeView.DEFAULT);

        // Ensure that the lower component of the pair is always the smaller of the bounds.
        ValueNode accessLow;
        ValueNode accessHigh;
        if (accessIndex.direction() == Direction.Up) {
            accessLow = accessStart;
            accessHigh = accessEnd;
        } else {
            accessLow = accessEnd;
            accessHigh = accessStart;
        }
        int accessSize = accessSizeInBytes(access);
        ValueNode sizeMinusOne = ConstantNode.forIntegerStamp(accessHigh.stamp(NodeView.DEFAULT), accessSize - 1, graph);
        accessHigh = AddNode.create(accessHigh, sizeMinusOne, NodeView.DEFAULT);

        return Pair.create(accessLow, accessHigh);
    }

    /**
     * Returns the number of bytes touched by a memory access.
     */
    private static int accessSizeInBytes(AddressableMemoryAccess access) {
        Stamp stamp;
        if (access instanceof ReadNode read) {
            stamp = read.getAccessStamp(NodeView.DEFAULT);
        } else if (access instanceof WriteNode write) {
            stamp = write.getAccessStamp(NodeView.DEFAULT);
        } else if (access instanceof FloatingReadNode read) {
            stamp = read.stamp(NodeView.DEFAULT);
        } else {
            throw GraalError.shouldNotReachHereUnexpectedValue(access);
        }
        return ((PrimitiveStamp) stamp).getBits() / Byte.SIZE;
    }

    public static boolean canUseSpeculation(StructuredGraph graph) {
        return graph.getSpeculationLog() != null && Options.OptimisticAliasingUseSpeculation.getValue(graph.getOptions());
    }
}
