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
package jdk.graal.compiler.vector.phases;

import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.LoopVectorizationKeepPostLoop;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeConditional;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeDeopts;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeFoldShaped;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeGather;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeIntegerMinMax;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeLoops;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeMapShaped;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeNegativeStride;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeReachabilityFences;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeSafepoints;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeSequence;
import static jdk.graal.compiler.vector.replacements.VectorIntrinsics.Options.Vectorization;

import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.guards.optimistic.memory.OptimisticLoopAliasGuardNode;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticMemoryEdge;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode.DeoptBranch;
import jdk.graal.compiler.vector.phases.LoopVectorizationPhase.VectorizableLoopInfo;
import jdk.graal.compiler.vector.replacements.VirtualConditionalNode;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.CompressionNode.CompressionOp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.ShortCircuitOrNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.FloatingIntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerTestNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.PointerEqualsNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.StateSplitProxyNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryLoadNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.AccessArrayNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.DerivedOffsetInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.OriginalLimitCheckedIV;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FloatableThreadLocalAccess;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.OrderedMemoryAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizableAllocation;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.graal.compiler.replacements.arraycopy.ArrayCopyWithDelayedLoweringNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;

/**
 * This class is a collection of utility methods for determining if and how a loop can be
 * vectorized. It is meant to be used both by {@link LoopVectorizationPhase} itself and by various
 * loop transformation phases that want to decide whether to optimize a loop themselves or to leave
 * it to the loop vectorizer. These other loop transformations should preferably query this
 * information via {@link VectorLoopUtility#potentialVectorLoop}.
 */
public final class LoopVectorizationAnalysis {

    public static class Options {
        // @formatter:off
        @Option(help = "The maximal number of fixed body nodes to consider in loop vectorization", type = OptionType.Debug)
        public static final OptionKey<Integer> LoopVectorizationMaxBodyNodes = new OptionKey<>(16);
        // @formatter:on
    }

    private LoopVectorizationAnalysis() {
        GraalError.shouldNotReachHere("LoopVectorizationAnalysis is not meant to be instantiated"); // ExcludeFromJacocoGeneratedReport
    }

    /**
     * Detect whether the given loop is vectorizable. This can be used at any point before
     * {@link LoopVectorizationPhase} to predict vectorizability, including in the high tier. Before
     * actual loop vectorization, the result of this method is based on heuristics and is not
     * guaranteed to be precise. Especially before {@link FloatingReadPhase} this analysis makes
     * optimistic assumptions about the future shape of the loop.
     *
     * @param loop the loop to be checked for vectorizability
     * @param preVectorizationCheck {@code true} iff this check is run by a phase prior to actual
     *            loop vectorization
     * @param providers a {@link CoreProviders} instance
     *
     * @return {@code null} if the loop is not vectorizable, a valid non-{@code null}
     *         {@link VectorizableLoopInfo} otherwise
     */
    public static VectorizableLoopInfo detectVectorizableLoop(Loop loop, boolean preVectorizationCheck, CoreProviders providers) {
        StructuredGraph graph = loop.loopBegin().graph();

        if (preVectorizationCheck && !VectorLoopUtility.Options.RespectVectorization.getValue(graph.getOptions())) {
            GraalError.shouldNotReachHere(
                            "Pre-vectorization checks should not call detectVectorizableLoop directly if RespectVectorization is false; use VectorLoopUtility.potentialVectorLoop instead");
        }

        final boolean beforeHighTierLowering = graph.isBeforeStage(StageFlag.HIGH_TIER_LOWERING);
        final boolean beforeGuardMovement = GraalOptions.SpeculativeGuardMovement.getValue(graph.getOptions()) && graph.isBeforeStage(StageFlag.GUARD_MOVEMENT);
        final boolean beforeEA = GraalOptions.PartialEscapeAnalysis.getValue(graph.getOptions()) && graph.isBeforeStage(StageFlag.FINAL_PARTIAL_ESCAPE);
        final boolean assumeConditionalCollapse = beforeEA && graph.isBeforeStage(StageFlag.HIGH_TIER_LOWERING);

        DebugContext debug = graph.getDebug();
        debug.log(DebugContext.DETAILED_LEVEL, "detect if %s is vectorizable (preVectorizationCheck=%b, beforeGuardMovement=%b)", loop, preVectorizationCheck, beforeGuardMovement);
        if (!(Vectorization.getValue(graph.getOptions()) && VectorizeLoops.getValue(graph.getOptions()))) {
            debug.log(DebugContext.DETAILED_LEVEL, "no, loop vectorization is disabled (Vectorization=%b, VectorizeLoops=%b)", Vectorization.getValue(graph.getOptions()),
                            VectorizeLoops.getValue(graph.getOptions()));
            return null;
        }
        if (!passesBasicStructuralChecks(loop, preVectorizationCheck)) {
            return null;
        }

        // currently SIMD PhiNodes in a loop cause vectorization problems so disallow them
        for (PhiNode phi : loop.loopBegin().phis().snapshot()) {
            if (phi.stamp(NodeView.DEFAULT) instanceof SimdStamp) {
                return null;
            }
        }

        VectorArchitecture arch = ((VectorLoweringProvider) providers.getLowerer()).getVectorArchitecture();
        boolean allowFloatingPointConditionals = arch.supportsFloatingPointConditionalMoves();
        int maxBodyNodes = Options.LoopVectorizationMaxBodyNodes.getValue(graph.getOptions());

        boolean afterFloatingReads = graph.isAfterStage(StageFlag.FLOATING_READS);
        AbstractBeginNode body = loop.counted().getBody();
        ArrayList<FixedNode> bodyNodes = new ArrayList<>();
        EconomicMap<WriteNode, InductionVariable> candidateWrites = EconomicMap.create(Equivalence.IDENTITY);
        ArrayList<IfNode> ifNodesToConditionalize = new ArrayList<>();
        boolean seenWrite = false;
        FixedNode node = body.getBlockNodes().first();
        if (node == loop.loopBegin()) {
            node = ((LoopBeginNode) node).next();
        }
        boolean seenSafepoint = false;
        boolean seenObjectWrite = false;
        boolean vectDeopt = false;
        while (node != null) {
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "look at loop body node %s", node);
            if (recordBodyNode(node)) {
                bodyNodes.add(node);
                if (bodyNodes.size() > maxBodyNodes) {
                    debug.log(DebugContext.DETAILED_LEVEL, "don't vectorize loop %s with body nodes exceeding maximum of %s", loop, maxBodyNodes);
                    return null;
                }
            }
            FixedNode next = (node instanceof FixedWithNextNode ? ((FixedWithNextNode) node).next() : null);

            if (node instanceof LoadIndexedNode) {
                LoadIndexedNode load = (LoadIndexedNode) node;
                if (!isVectorizableLoadIndex(load, loop, arch, providers)) {
                    loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize index %s for %s", load.index(), load);
                    return null;
                }
            }

            if (preVectorizationCheck && beforeEA) {
                if (node instanceof Virtualizable) {
                    // Escape analysis might remove these allocation nodes, be optimistic
                    node = next;
                    continue;
                }
            }

            if (preVectorizationCheck && beforeHighTierLowering) {
                if (node instanceof ArrayCopyWithDelayedLoweringNode) {
                    /*
                     * Array copy nodes can be lowered to loops that themselves can be vectorized.
                     * At an early point in the compilation pipeline we cannot ensure how such a
                     * node may look like after lowering, thus we assume its optimistically
                     * vectorizable.
                     */
                    node = next;
                    continue;
                }
            }

            if (node instanceof WriteNode || (!afterFloatingReads && node instanceof StoreIndexedNode)) {
                seenWrite = true;
                if (!vectorizableWrite(node, loop, preVectorizationCheck, arch, beforeGuardMovement, candidateWrites)) {
                    return null;
                }
                if (node instanceof WriteNode write) {
                    if (write.value().stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp) {
                        seenObjectWrite = true;
                    }
                }
                node = next;
                continue;
            }

            if (node instanceof ReachabilityFenceNode && VectorizeReachabilityFences.getValue(graph.getOptions())) {
                ReachabilityFenceNode fence = (ReachabilityFenceNode) node;
                for (Node input : fence.inputs()) {
                    if (!isVectorizableComputationRoot(input, loop, arch, preVectorizationCheck, null)) {
                        return null;
                    }
                }

                node = next;
                continue;
            }

            if (node instanceof SafepointNode && VectorizeSafepoints.getValue(node.getOptions())) {
                if (((SafepointNode) node).next() instanceof LoopEndNode) {
                    seenSafepoint = true;
                    node = next;
                    continue;
                }
            }

            if (simpleVectorizableBodyNode(node, preVectorizationCheck, beforeEA)) {
                node = next;
                continue;
            }

            if (node instanceof IfNode) {

                IfNode ifNode = (IfNode) node;

                if (loop.counted().isInverted() && node == loop.counted().getLimitTest()) {
                    bodyNodes.remove(node);
                    node = loop.counted().getCountedExit() == ((IfNode) node).trueSuccessor() ? ((IfNode) node).falseSuccessor() : ((IfNode) node).trueSuccessor();
                    continue;
                }

                if (!vectorizableCondition(ifNode.condition(), loop, arch, preVectorizationCheck, null)) {
                    return null;
                }

                // preVectorizationCheck && assumeConditionalCollapse ==> ignore guard usages on the
                // begins before speculative guard movement
                next = vectorizableConditional(ifNode, bodyNodes, ifNodesToConditionalize, allowFloatingPointConditionals, preVectorizationCheck && assumeConditionalCollapse);
                if (next != null) {
                    node = next;
                    continue;
                }

                next = vectorizableDeopt(ifNode, seenWrite);
                if (next != null) {
                    vectDeopt = true;
                    node = next;
                    continue;
                }

                // only relevant if this method is called before converting deopts to guards
                if (beforeGuardMovement) {
                    next = null;
                    if (isNonReturnControlSink(ifNode.trueSuccessor())) {
                        next = ifNode.falseSuccessor();
                    } else if (isNonReturnControlSink(ifNode.falseSuccessor())) {
                        next = ifNode.trueSuccessor();
                    }
                    if (next != null) {
                        debug.log(DebugContext.DETAILED_LEVEL, "assume %s will be eliminated by speculative guard movement", ifNode);
                        node = next;
                        continue;
                    }
                }

                // Some kind of unsupported control flow.
                debug.log(DebugContext.DETAILED_LEVEL, "Loop %s not vectorizable because of unsupported control flow %s", loop, ifNode);
                return null;
            }

            if (node instanceof FixedGuardNode fixedGuard) {
                if (vectorizableCondition(fixedGuard.condition(), loop, arch, preVectorizationCheck, null)) {
                    // see vectorizableDeopt(IfNode, boolean)
                    if (!seenWrite && VectorizeDeopts.getValue(fixedGuard.getOptions())) {
                        vectDeopt = true;
                        node = fixedGuard.next();
                        continue;
                    }
                    /*
                     * Don't check for potential guard movement because vectorizable condition
                     * likely depends on something inside the loop which cannot be speculated on.
                     */
                } else {
                    // Non-vectorizable condition might be able to float out of the loop.
                    if (fixedGuard.canFloat() && beforeHighTierLowering) {
                        debug.log(DebugContext.DETAILED_LEVEL, "assume %s will be eliminated by speculative guard movement", fixedGuard);
                        node = fixedGuard.next();
                        continue;
                    }
                }

                debug.log(DebugContext.DETAILED_LEVEL, "non-vectorizable fixed guard: %s", node);
                return null;
            }

            if (node instanceof LoopEndNode) {
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "found vectorizable-looking loop end %s", node);
                if (preVectorizationCheck) {
                    if (!allPhisLookLikeSimdifiableFolds(loop, bodyNodes, arch, beforeGuardMovement)) {
                        return null;
                    }
                }

                // Success!

                /*
                 * If this loop is vectorizable ensure that we already created an overflow guard for
                 * it, we try to preserve a framestate for later insertion though its better to
                 * create it as early as possible.
                 */
                if (graph.getGuardsStage().allowsFloatingGuards()) {
                    loop.counted().createOverFlowGuard();
                }

                if ((seenSafepoint || vectDeopt) && seenObjectWrite) {
                    // vector deopts and write barriers - need to ensure no early exit (deopt)
                    // is taken in the loop because we execute write barriers only after the
                    // vector loop
                    return null;
                }

                return new VectorizableLoopInfo(candidateWrites, bodyNodes, ifNodesToConditionalize);
            }

            // all other nodes are not vectorizable (this also applies to guards that are still part
            // of the loop as they are lowered to control flow in the guard lowering phase)
            debug.log(DebugContext.DETAILED_LEVEL, "non-vectorizable body node: %s", node);
            return null;
        }

        // we didn't find the end of the loop
        // that means there is complex control flow inside the loop, so we can't vectorize it
        debug.log(DebugContext.DETAILED_LEVEL, "did not find the end of the loop");
        return null;
    }

    /**
     * Returns {@code true} if this loop should be vectorized in "keep post loop" mode. In this
     * mode, a loop like
     *
     * <pre>
     * for (i = start; i < end; i++) {
     *     ...
     * }
     * </pre>
     *
     * is vectorized as:
     *
     * <pre>
     * simdIterations = VectorLoop(...);
     * // original loop as "post loop":
     * for (i = start + simdIterations; i < end; i++) {
     *     ...
     * }
     * </pre>
     *
     * That is, the original loop is not destroyed by loop vectorization but kept around to handle
     * any scalar iterations left over after the simdified vector loop.
     *
     * Otherwise, we are in the default mode where loop vectorization does eliminate the original
     * loop completely, replacing it with one or more vector consumers and possibly a vector loop.
     * Scalar tail iterations after the main simdified loop are regenerated from these consumers.
     */
    public static boolean keepPostLoop(Loop loop) {
        return LoopVectorizationKeepPostLoop.getValue(loop.loopBegin().getOptions());
    }

    /**
     * Check whether the loop passes the most basic checks, such as being a counted loop, and not
     * being trivially uninteresting (empty, or never entered).
     */
    private static boolean passesBasicStructuralChecks(Loop loop, boolean preVectorizationCheck) {
        LoopBeginNode loopBegin = loop.loopBegin();
        DebugContext debug = loopBegin.getDebug();
        if (loopBegin.isOsrLoop()) {
            debug.log(DebugContext.DETAILED_LEVEL, "don't vectorize OSR loop %s", loopBegin);
            return false;
        }
        if (LoopUtility.excludeLoopFromOptimizer(loop)) {
            debug.log(DebugContext.DETAILED_LEVEL, "don't vectorize strip mined outer %s", loopBegin);
            return false;
        }
        if (loopBegin.isPreLoop() || loopBegin.isMainLoop() || loopBegin.isPostLoop()) {
            // The pre and post loops generated by partial unrolling are so short that vectorization
            // is unlikely to be meaningful. The partially unrolled main loop is almost certainly
            // not simdifiable because partial unrolling changes the strides of memory accesses.
            // SIMD vectorization is more likely to be able to simdify partially unrolled loops.
            debug.log(DebugContext.DETAILED_LEVEL, "don't vectorize partially unrolled loop %s", loopBegin);
            return false;
        } else {
            assert loopBegin.isSimpleLoop() : "unexpected loop type";
        }
        if (!loop.detectCounted()) {
            return false;
        }
        if (loopBegin.graph().isAfterStage(StageFlag.OPTIMISTIC_ALIASING)) {
            OptimisticLoopAliasGuardNode aliasingGuard = (OptimisticLoopAliasGuardNode) loopBegin.getInterIterationAliasingGuard();
            if (aliasingGuard != null && aliasingGuard.isAliasing().isTautology()) {
                debug.log(DebugContext.DETAILED_LEVEL, "cannot vectorize loop %s with definite inter-iteration aliasing", loopBegin);
                return false;
            }
        }
        if (!preVectorizationCheck) {
            if (!loop.counted().loopMightBeEntered()) {
                debug.log(DebugContext.DETAILED_LEVEL, "Loop %s cannot be entered, it won't be vectorized", loopBegin);
                return false;
            }
            if (!(loop.counted().counterNeverOverflows() || loop.counted().getOverFlowGuard() != null)) {
                debug.log(DebugContext.DETAILED_LEVEL, "loop counter might overflow");
                return false;
            }
            if (hasObjectPhi(loop)) {
                return false;
            }
            if (hasTransitiveOutsidePhiUsages(loop)) {
                return false;
            }
        }
        if (PrimitiveStamp.getBits(loop.counted().getLimitCheckedIV().valueNode().stamp(NodeView.DEFAULT)) > JavaKind.Int.getBitCount()) {
            // This loop has a long counter. This can be OK, as long as all indexed accesses use a
            // separate int induction variable. But if anywhere in the loop we have a lossy
            // long-induction-variable-to-int conversion, we would probably have to generate costly
            // bounds checks that make this loop unattractive for vectorization.
            if (hasNonInductiveLongToIntConversion(loop)) {
                debug.log(DebugContext.DETAILED_LEVEL, "long induction variable with lossy conversion to int");
                return false;
            }
        }

        if (hasDerivedConvertedIVsThatNeedToPreserveOverflowSemantics(loop)) {
            /*
             * GR-52598: This loop has complex int arithmetic that can overflow. We only check that
             * the loop counter IV does not overflow but do not have support for checking
             * intermediate IVs for overflow at the moment.
             */
            debug.log(DebugContext.DETAILED_LEVEL, "int induction variables that can overflow offsets and thus cannot be proven to not overflow");
            return false;
        }

        if (loopBegin.isCompilerInverted() && keepPostLoop(loop)) {
            /*
             * If we're looking at an inverted loop with an equality termination check, like:
             *
             * @formatter:off
             * do {
             *     ...
             * } while (i != limit);
             * @formatter:on
             *
             * then we cannot use a post loop. The number of iterations computed by the vector loop may be off by one.
             */
            AbstractBeginNode countedExit = loop.counted().getCountedExit();
            FixedNode cursor = countedExit;
            while (!(cursor instanceof IfNode)) {
                cursor = (FixedNode) cursor.predecessor();
            }
            IfNode exitCheck = (IfNode) cursor;
            if (exitCheck.condition() instanceof IntegerEqualsNode) {
                debug.log(DebugContext.DETAILED_LEVEL, "inverted post loop's equality exit check may be off by one from what loop vectorization would need");
                return false;
            }
        }
        AbstractBeginNode body = loop.counted().getBody();
        if (body.getBlockNodes().isEmpty()) {
            debug.log(DebugContext.DETAILED_LEVEL, "loop contains no blocks");
            return false;
        }

        return true;
    }

    /**
     * Check for induction variable computations that feed into address computations but have
     * overflow semantics that would make loop vectorization illegal.
     *
     * @return {@code true} if the loop has an IV with problematic overflow, meaning that the loop
     *         must not be vectorized
     */
    private static boolean hasDerivedConvertedIVsThatNeedToPreserveOverflowSemantics(Loop loop) {
        InductionVariable counterIV = loop.counted().getLimitCheckedIV();
        for (InductionVariable iv : loop.getInductionVariables().getValues()) {
            if (iv == counterIV) {
                /* We have an overflow guard for the counter IV. */
                continue;
            }

            /*
             * The overflow property checked here is only relevant for address computations. Check
             * if this IV is used directly in an address. If it is used indirectly via another IV,
             * that IV will be checked separately.
             */
            boolean mightHaveAddressUsage = false;
            for (Node usage : iv.valueNode().usages()) {
                if (usage instanceof AddressNode) {
                    mightHaveAddressUsage = true;
                    break;
                }
            }
            if (!mightHaveAddressUsage) {
                continue;
            }

            /*
             * Search this IV's base IVs to for the problematic pattern of a possibly overflowing IV
             * behind an integer conversion.
             */
            InductionVariable currentIV = iv;
            boolean seenConversion = false;
            while (currentIV != null) {
                if (currentIV == counterIV) {
                    /* We have an overflow guard for the counter IV. */
                    break;
                }
                /*
                 * Strip mining guarantees that either the counter IV never overflows or an overflow
                 * guard is present for the loop. We want to cover cases here where the IV was
                 * actually the "OLD" original IV before strip mining. Since strip mining builds a
                 * new condition we have to "look through" that condition and figure out if are in
                 * fact dealing with the old, guaranteed to never overflow, IV.
                 */
                if (isCounterIVHiddenBehindStripMining(loop, currentIV, counterIV)) {
                    break;
                }
                if (currentIV.valueNode() instanceof IntegerConvertNode<?>) {
                    seenConversion = true;
                } else if (seenConversion) {
                    if (currentIV instanceof BasicInductionVariable || currentIV instanceof DerivedOffsetInductionVariable || currentIV.valueNode() instanceof PiNode) {
                        /*
                         * When an additive operation overflows, the sign will change. If this is an
                         * additive IV or a Pi and its sign is constant, we know it won't overflow.
                         */
                        IntegerStamp stamp = (IntegerStamp) currentIV.valueNode().stamp(NodeView.DEFAULT);
                        boolean mightOverflow = !(stamp.isNegative() || stamp.isPositive());
                        if (!mightOverflow) {
                            break;
                        }
                    }
                    /**
                     * Problematic: We have a shape like the following:
                     *
                     * <pre>
                     *     SomeIV
                     *        |
                     *   Offset/Scale    (= currentIV)
                     *        |
                     *       ...
                     *        |
                     *   SignExtend      (seenConversion)
                     *        |
                     *       ...         (includes original iv)
                     *        |
                     *   AddressNode
                     * </pre>
                     *
                     * Even if SomeIV does not overflow, offsetting or scaling it might overflow.
                     * The loop vectorizer would want to transform address computations based on
                     * such IVs by computing only the IV's start value, and later during vector
                     * lowering adding indices to that start value.
                     *
                     * That is, we would transform a computation like
                     *
                     * <pre>
                     * (long) (someIv.init + i * someIV.increment + 1)
                     * </pre>
                     *
                     * to
                     *
                     * <pre>
                     * (long) someIv.init + 1L + (long) (i * someIV.increment)
                     * </pre>
                     *
                     * which would change the semantics if the original computation can overflow as
                     * int before the sign extension to long.
                     *
                     * Do not attempt loop vectorization in such cases.
                     */
                    return true;
                }
                currentIV = currentIV instanceof DerivedInductionVariable derivedIV ? derivedIV.getBase() : null;
            }
        }
        return false;
    }

    /**
     * Determines whether the current induction variable reconstructs the original limit-checked IV
     * of this strip-mined loop. The explicit provenance is required because unrelated IVs can have
     * the same inner-counter plus outer-phi shape.
     */
    private static boolean isCounterIVHiddenBehindStripMining(Loop loop, InductionVariable currentIV, InductionVariable counterIV) {
        return loop.loopBegin().isCountedStripMinedInner() && loop.parent() != null && currentIV instanceof DerivedOffsetInductionVariable offsetIV &&
                        offsetIV.getBase() == counterIV && offsetIV.valueNode() instanceof AddNode &&
                        isOriginalLimitCheckedIV(offsetIV.getOffset(), loop.parent());
    }

    private static boolean isOriginalLimitCheckedIV(ValueNode value, Loop loop) {
        return loop.loopBegin().isCountedStripMinedOuter() && value instanceof OriginalLimitCheckedIV && value instanceof ValuePhiNode phi && phi.merge() == loop.loopBegin();
    }

    /**
     * Check if any loop phis have usages inside the loop which themselves have usages outside the
     * loop. If this is the case, we must not transform these phis to folds because we cannot
     * smuggle such intermediate values out of a vector loop.
     */
    private static boolean hasTransitiveOutsidePhiUsages(Loop loop) {
        for (ValuePhiNode phi : loop.loopBegin().valuePhis()) {
            if (loop.getInductionVariables().containsKey(phi)) {
                continue;
            }
            NodeFlood flood = new NodeFlood(phi.graph());
            for (Node n : phi.usages()) {
                if (!loop.isOutsideLoop(n)) {
                    flood.add(n);
                }
            }
            for (Node usage : flood) {
                if (phi == usage) {
                    continue;
                }
                if (usage instanceof PhiNode && ((PhiNode) usage).merge() == phi.merge()) {
                    continue;
                }
                if (loop.isOutsideLoop(usage)) {
                    phi.getDebug().log(DebugContext.DETAILED_LEVEL, "loop not vectorizable because %s has transitive outside usage %s", phi, usage);
                    return true;
                }
                if (usage instanceof VirtualState) {
                    continue;
                }
                flood.addAll(usage.usages());
            }
        }
        return false;
    }

    private static boolean hasNonInductiveLongToIntConversion(Loop loop) {
        for (Node inductiveValue : loop.getInductionVariables().getKeys()) {
            for (Node usage : inductiveValue.usages()) {
                if (loop.isOutsideLoop(usage) || loop.getInductionVariables().containsKey(usage)) {
                    continue;
                }
                if (!(usage instanceof NarrowNode)) {
                    continue;
                }
                NarrowNode narrow = (NarrowNode) usage;
                if (PrimitiveStamp.getBits(narrow.getValue().stamp(NodeView.DEFAULT)) > JavaKind.Int.getBitCount()) {
                    // We found a narrow of an induction variable that is not itself an induction
                    // variable because the narrowing loses information. Such nodes spell trouble,
                    // so reject this loop.
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasObjectPhi(Loop loop) {
        for (PhiNode phiNode : loop.loopBegin().phis()) {
            if (phiNode.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp) {
                phiNode.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s not vectorizable because of object stamp %s %s", loop, phiNode, phiNode.stamp(NodeView.DEFAULT));
                return true;
            }
        }
        return false;
    }

    /**
     * Determine whether this node should be recorded as part of the loop body. Purely structural
     * nodes (begin nodes, anchors, the loop end) are not considered part of the body for the
     * purposes of vectorization.
     */
    private static boolean recordBodyNode(FixedNode node) {
        if (node instanceof BeginNode || node instanceof ValueAnchorNode) {
            return false;
        }
        return true;
    }

    /**
     * Determine whether the given node is acceptable for vectorization without complex checks.
     */
    private static boolean simpleVectorizableBodyNode(FixedNode node, boolean preVectorizationCheck, boolean beforeEA) {
        if (node instanceof BeginNode) {
            // this node can be safely ignored
            return true;
        } else if (node instanceof ValueAnchorNode) {
            // this can be ignored if there are no usages
            return node.usages().isEmpty();
        }
        if (node instanceof LoadIndexedNode) {
            // the index computation is checked separately
            return true;
        }
        if (node instanceof ReadNode || node instanceof LoadFieldNode) {
            return !((OrderedMemoryAccess) node).ordersMemoryAccesses();
        }
        if (node instanceof IntegerDivRemNode && ((IntegerDivRemNode) node).getY().isConstant() && !((IntegerDivRemNode) node).getY().isDefaultConstant()) {
            if (node.graph().isAfterStage(StageFlag.FLOATING_READS)) {
                // this will probably only be vectorizable if the second operand is a constant
                return ((IntegerDivRemNode) node).getY().isConstant();
            } else {
                // subject to later optimizations, be optimistic during the pre-vectorization check
                return preVectorizationCheck;
            }
        }
        if (preVectorizationCheck && beforeEA) {
            if (isPotentiallyEscapeAnalyzableWrite(node)) {
                return true;
            }
        }
        if (node.graph().isBeforeStage(StageFlag.FLOATING_READS)) {
            if (isPotentiallyLoopInvariantNode(node)) {
                // we must wait until lowering to decide further
                return true;
            }
        }

        return false;
    }

    private static boolean isPotentiallyEscapeAnalyzableWrite(Node node) {
        return node instanceof UnsafeMemoryStoreNode || node instanceof StoreFieldNode || (MemoryKill.isSingleMemoryKill(node) && node instanceof MemoryAccess) || node instanceof RawStoreNode;
    }

    private static boolean isPotentiallyLoopInvariantNode(Node node) {
        return node instanceof UnsafeMemoryLoadNode || node instanceof RawLoadNode || node instanceof FixedGuardNode || node instanceof ArrayLengthNode ||
                        (node instanceof FloatableThreadLocalAccess && ((FloatableThreadLocalAccess) node).canFloat());
    }

    private static boolean vectorizableWrite(FixedNode node, Loop loop, boolean preVectorizationCheck, VectorArchitecture arch, boolean optimisticWrites,
                    EconomicMap<WriteNode, InductionVariable> candidateWrites) {
        if (!VectorizeMapShaped.getValue(node.getOptions())) {
            return false;
        }
        ValueNode writeValue = null;
        InductionVariable addressIv = null;
        if (node instanceof WriteNode) {
            WriteNode write = (WriteNode) node;
            if (node.graph().isAfterStage(StageFlag.OPTIMISTIC_ALIASING)) {
                // check aliasing
                MemoryKill lastLocationAccess = write.getLastLocationAccess();
                if (lastLocationAccess instanceof OptimisticMemoryEdge) {
                    OptimisticMemoryEdge memoryEdge = (OptimisticMemoryEdge) lastLocationAccess;
                    MemoryKill optimisticEdge = memoryEdge.getOptimisticEdge();
                    while (optimisticEdge instanceof OptimisticMemoryEdge) {
                        optimisticEdge = ((OptimisticMemoryEdge) optimisticEdge).getOptimisticEdge();
                    }
                    MemoryKill conservativeEdge = memoryEdge.getConservativeEdge();
                    if (!(loop.isOutsideLoop(optimisticEdge.asNode()) || optimisticEdge instanceof MemoryPhiNode) || !(conservativeEdge instanceof WriteNode)) {
                        node.getDebug().log(DebugContext.DETAILED_LEVEL, "unvectorizable aliasing for %s: optimistic edge %s, conservative edge %s", write, optimisticEdge, conservativeEdge);
                        return false;
                    }
                } else if (lastLocationAccess instanceof MemoryPhiNode) {
                    MemoryPhiNode memoryPhi = (MemoryPhiNode) lastLocationAccess;
                    if (memoryPhi.merge() != loop.loopBegin() || !(memoryPhi.singleBackValueOrThis() instanceof WriteNode)) {
                        node.getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize write with lastLocationAccess %s", memoryPhi);
                        return false;
                    }
                } else if (!(lastLocationAccess instanceof WriteNode) || !loop.isOutsideLoop(lastLocationAccess.asNode())) {
                    node.getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize write with lastLocationAccess %s", lastLocationAccess);
                    return false;
                } else if (loop.isOutsideLoop(lastLocationAccess.asNode())) {
                    GraalError.shouldNotReachHere(String.format("unexpected lastLocationAccess %s for %s", lastLocationAccess, node)); // ExcludeFromJacocoGeneratedReport
                }
            }
            if (!write.canDeoptimize() && write.getAddress() instanceof OffsetAddressNode) {
                writeValue = write.value();
                addressIv = getInductionVariable(loop, write.getAddress());
            } else {
                node.getDebug().log(DebugContext.DETAILED_LEVEL, "write %s can deoptimize or has non-vectorizable address", loop, write);
                return false;
            }
        } else {
            StoreIndexedNode store = (StoreIndexedNode) node;
            writeValue = store.value();
            addressIv = getInductionVariable(loop, store.index());
        }
        if (!isVectorizableComputationRoot(writeValue, loop, arch, preVectorizationCheck, null)) {
            node.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s not vectorizable because of value %s for %s", loop, writeValue, node);
            return false;
        }
        if (!(optimisticWrites || canVectorizeIv(node.graph(), addressIv))) {
            node.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s not vectorizable because of non-vectorizable address/index IV %s %s", loop, addressIv, node);
            return false;
        }
        if (preVectorizationCheck && !optimisticWrites) {
            if (addressIv == null || !addressIv.isConstantStride() ||
                            !likelySimdifiableStride(arch, node, addressIv, writeValue.stamp(NodeView.DEFAULT))) {
                node.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s (likely) not simdifiable because of address/index IV %s", loop, addressIv);
                return false;
            }
        }
        if (!preVectorizationCheck) {
            if (addressIv == null || !addressIv.isConstantStride()) {
                return false;
            }
        }
        if (addressIv != null) {
            assert node instanceof WriteNode || (preVectorizationCheck && node instanceof StoreIndexedNode) : node + " preVec=" + preVectorizationCheck;
            if (node instanceof WriteNode) {
                candidateWrites.put((WriteNode) node, addressIv);
            }
        }
        return true;
    }

    private static boolean isVectorizableLoadIndex(LoadIndexedNode load, Loop loop, VectorArchitecture arch, CoreProviders providers) {
        ValueNode index = load.index();
        Stamp loadStamp = load.stamp(NodeView.DEFAULT);
        if (load.elementKind().isNumericInteger() && load.elementKind().getByteCount() < load.getStackKind().getByteCount()) {
            // Loop vectorization cares about proper sub-word stamps.
            loadStamp = IntegerStamp.create(load.elementKind().getByteCount() * Byte.SIZE);
        } else if (loadStamp instanceof ObjectStamp) {
            /*
             * If object references are usually compressed in memory, loop vectorization cares about
             * the proper compressed stamp.
             */
            DefaultJavaLoweringProvider javaLowerer = ((VectorLoweringProvider) providers.getLowerer()).getBasicLoweringProvider();
            loadStamp = javaLowerer.implicitStoreConvert(JavaKind.Object, load).stamp(NodeView.DEFAULT);
        }
        if (!arch.isVectorizable(loadStamp)) {
            return false;
        }
        InductionVariable indexIv = getInductionVariable(loop, index);
        load.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "load %s index %s iv %s", load, index, indexIv);
        if (indexIv != null) {
            if (!canVectorizeIv(index.graph(), indexIv)) {
                return false;
            }
            if (!indexIv.isConstantStride() || !likelySimdifiableStride(arch, load, indexIv, loadStamp)) {
                load.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s (likely) not simdifiable because of load index IV %s", loop, indexIv);
                return false;
            }
            return true;
        }

        // See if this is load's index depends on another LoadIndexed in the same loop. This would
        // be a gather operation, which we may or may not be able to support.
        NodeFlood flood = index.graph().createNodeFlood();
        flood.add(index);
        for (Node node : flood) {
            if (loop.isOutsideLoop(node)) {
                continue;
            }
            if (node instanceof LoadIndexedNode) {
                if (!VectorizeGather.getValue(index.getOptions())) {
                    load.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s not vectorizable because of not allowed gather %s", loop, load);
                    return false;
                }
                if (arch != null) {
                    Stamp indexStamp = index.stamp(NodeView.DEFAULT);
                    if (arch.getSupportedVectorGatherLength(loadStamp, indexStamp, arch.getMaxVectorLength()) <= 1) {
                        load.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s not vectorizable because of unsupported gather %s", loop, load);
                        return false;
                    }
                }
            }
            flood.addAll(node.inputs());
        }

        // More checks will be performed by isVectorizableComputationRoot, be optimistic for now.
        return true;
    }

    private static boolean likelySimdifiableStride(VectorArchitecture arch, ValueNode node, InductionVariable iv, Stamp writeValueStamp) {
        long writeAddressStride = iv.constantStride();
        final long absStride;
        try {
            absStride = NumUtil.safeAbs(writeAddressStride, IntegerStamp.getBits(iv.strideNode().stamp(NodeView.DEFAULT)));
        } catch (ArithmeticException e) {
            return false;
        }
        if (node instanceof StoreIndexedNode || node instanceof LoadIndexedNode) {
            return absStride == 1;
        } else {
            assert node instanceof WriteNode && ((WriteNode) node).getAddress() instanceof OffsetAddressNode : node + " adr=" + ((WriteNode) node).getAddress();
            // We can likely only simdify this write if the stride in bytes equals the stamp's size
            // in bytes.
            if (writeValueStamp instanceof PrimitiveStamp) {
                int bits = PrimitiveStamp.getBits(writeValueStamp);
                if (bits < Byte.SIZE) {
                    // booleans still need a full byte
                    return absStride == 1;
                } else {
                    return absStride == bits / Byte.SIZE;
                }
            } else if (arch.isVectorizableObjectStamp(writeValueStamp)) {
                return absStride == arch.getVectorStride(writeValueStamp);
            } else {
                return false;
            }
        }
    }

    /**
     * Check whether the given node inside the loop is likely to be the root of a vectorizable
     * computation, i.e., whether it and certain inputs are vectorizable. This is different from
     * {@link #isVectorizableAsInnerNode(Node, Loop, VectorArchitecture, boolean, NodeFlood)}, which
     * only checks a node without its inputs and is for internal use by the loop vectorizer only.
     *
     * This version of this method is meant for use during loop vectorization. The pre-analysis
     * should use
     * {@link #isVectorizableComputationRoot(Node, Loop, VectorArchitecture, boolean, NodeFlood)}
     * instead, as the vector architecture can inform predictions about vectorizability before
     * lowering of certain nodes.
     */
    public static boolean isVectorizableComputationRoot(Node value, Loop loop, VectorArchitecture arch) {
        return isVectorizableComputationRoot(value, loop, arch, false, null);
    }

    private static boolean isVectorizableComputationRoot(Node value, Loop loop, VectorArchitecture arch, boolean preVectorizationCheck, NodeFlood inputFlood) {
        NodeFlood flood = inputFlood != null ? inputFlood : new NodeFlood(value.graph());
        flood.add(value);
        for (Node node : flood) {
            if (node instanceof ValueNode && ((ValueNode) node).stamp(NodeView.DEFAULT) instanceof SimdStamp) {
                loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize SIMD value %s", node);
                return false;
            }
            if (loop.isOutsideLoop(node)) {
                continue;
            }
            if (node instanceof FloatingReadNode || node instanceof ReadNode) {
                AddressableMemoryAccess read = (AddressableMemoryAccess) node;
                if (!isVectorizableReadAddress(read.getAddress(), read.asNode().stamp(NodeView.DEFAULT), loop, arch, preVectorizationCheck, flood)) {
                    loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize address %s for %s", read.getAddress(), read);
                    return false;
                }
                /*
                 * TODO (GR-50688): If we are after OptimisticAliasingAnalysis, we could check
                 * floating reads' lastLocationAccesses the same way that loop vectorization does.
                 * Any lastLocationAccess inside the loop should be an OptimisticMemoryEdge. A
                 * simple implementation of this idea regressed scimark.sor.small. Revisit this and
                 * find out how we can implement this check properly.
                 */
            }
            boolean assumeVectorizableRead = node instanceof MemoryAccess || node instanceof AccessArrayNode || node instanceof UnsafeAccessNode || node instanceof UnsafeMemoryLoadNode;
            if (assumeVectorizableRead) {
                continue;
            }
            if (node instanceof ValuePhiNode) {
                ValuePhiNode phi = (ValuePhiNode) node;
                if (loop.getInductionVariables().get(phi) != null) {
                    // assume this will be vectorized as a sequence or a memory access index
                    continue;
                }
                if (VectorizeConditional.getValue(value.getOptions()) && !phi.isLoopPhi()) {
                    // a phi on a merge; assume we will transform this into a conditional
                    flood.addAll(phi.values());
                    continue;
                }
                if (VectorizeFoldShaped.getValue(value.getOptions()) && loop.loopBegin().isPhiAtMerge(phi)) {
                    // LoopVectorizationPhase cant handle phis on loop-begin as inputs
                    boolean valueOnLoopMerge = false;
                    for (ValueNode val : phi.values()) {
                        if (val != phi && loop.loopBegin().isPhiAtMerge(val)) {
                            valueOnLoopMerge = true;
                            break;
                        }
                    }
                    // recursive paths containing multiple phis on loop-begin can not be vectorized
                    if ((preVectorizationCheck || !valueOnLoopMerge) && isVectorizableFold(loop, phi)) {
                        flood.addAll(phi.values());
                        continue;
                    }
                }

                if (RemoveEmptyLoopsPhase.isOptimizablePhi(phi, loop)) {
                    flood.addAll(phi.values());
                    continue;
                }

                loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize phi %s flowing to %s", node, value);
                return false;
            }
            if (node instanceof FloatingIntegerDivRemNode && ((FloatingIntegerDivRemNode<?>) node).getGuard() != null) {
                // inner nodes with guard edges are not vectorizable currently
                return false;
            }
            if (isDivRem(node)) {
                if (isSimdifiableDivRemByConstant(node, arch)) {
                    flood.addAll(node.inputs());
                    continue;
                } else {
                    loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize loop because of div %s", value);
                    return false;
                }
            }
            if (value instanceof FloatConvertNode floatConvert) {
                if (preVectorizationCheck && !isSimdifiableFloatConvert(floatConvert, arch)) {
                    loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize loop because of float convert %s", value);
                    return false;
                }
            }
            if (node instanceof ArithmeticOperation || node instanceof ConstantNode || node instanceof IntegerConvertNode || node instanceof ArrayLengthNode) {
                flood.addAll(node.inputs());
                continue;
            }
            if (isSimdifiableDivRemByConstant(node, arch)) {
                flood.addAll(node.inputs());
                continue;
            }
            if (node instanceof PiNode) {
                flood.add(((PiNode) node).object());
                continue;
            }
            if (node instanceof ValueNode && ((ValueNode) node).stamp(NodeView.DEFAULT).isPointerStamp()) {
                // We're probably copying some references.
                continue;
            }
            if (VectorizeIntegerMinMax.getValue(value.getOptions()) && node instanceof ConditionalNode && MinMaxNode.fromConditional((ConditionalNode) node) != null) {
                flood.add(((ConditionalNode) node).trueValue());
                flood.add(((ConditionalNode) node).falseValue());
                continue;
            }
            if (VectorizeConditional.getValue(value.getOptions()) && node instanceof ConditionalNode) {
                flood.add(((ConditionalNode) node).trueValue());
                flood.add(((ConditionalNode) node).falseValue());
                // Check the condition.
                if (!isVectorizableAsInnerNode(node, loop, arch, preVectorizationCheck, flood)) {
                    loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize value %s flowing to %s", node, value);
                    return false;
                }
                continue;
            }
            if (node instanceof VirtualConditionalNode) {
                flood.add(((VirtualConditionalNode) node).conditional());
                continue;
            }
            if (!isVectorizableAsInnerNode(node, loop, arch, preVectorizationCheck, flood)) {
                loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "can't vectorize value %s flowing to %s", node, value);
                return false;
            }
        }
        return true;
    }

    private static boolean isDivRem(Node value) {
        return value instanceof IntegerDivRemNode || value instanceof FloatingIntegerDivRemNode<?>;
    }

    private static boolean isVectorizableReadAddress(AddressNode address, Stamp readStamp, Loop loop, VectorArchitecture arch, boolean preVectorizationCheck, NodeFlood flood) {
        if (!(address instanceof OffsetAddressNode)) {
            return false;
        }

        OffsetAddressNode offsetAddress = (OffsetAddressNode) address;
        InductionVariable iv = getInductionVariable(loop, address);
        if (iv != null) {
            return canVectorizeIv(address.graph(), iv);
        } else if (preVectorizationCheck && loop.isOutsideLoop(offsetAddress)) {
            // A fixed read that we hope will float out of the loop.
            return true;
        } else if (VectorizeGather.getValue(address.getOptions()) && isPotentialGatherAddress(offsetAddress, loop, arch, preVectorizationCheck, flood)) {
            if (preVectorizationCheck && arch != null) {
                Stamp offsetStamp = offsetAddress.getOffset().stamp(NodeView.DEFAULT);
                if (arch.getSupportedVectorGatherLength(readStamp, offsetStamp, arch.getMaxVectorLength()) <= 1) {
                    return false;
                }
            }
            flood.add(offsetAddress.getOffset());
            return true;
        } else if (preVectorizationCheck && address.graph().isBeforeStage(StageFlag.GUARD_MOVEMENT)) {
            // LoadIndex nodes have been lowered to reads, but speculative guard movement hasn't run
            // yet. This might be the reason that we have no IV for this index computation. Be
            // optimistic.
            return true;
        }

        return false;
    }

    public static InductionVariable getInductionVariable(Loop loop, OffsetAddressNode address) {
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        if (loop.isOutsideLoop(address.getBase())) {
            return ivs.get(address.getOffset());
        } else {
            return ivs.get(address.getBase());
        }
    }

    /**
     * A read from an address with a loop-invariant base and a vectorizable offset can be vectorized
     * as a gather operation.
     *
     * This version of this method is meant for use during loop vectorization. The pre-analysis
     * should use
     * {@link #isPotentialGatherAddress(OffsetAddressNode, Loop, VectorArchitecture, boolean, NodeFlood)}
     * instead.
     */
    public static boolean isPotentialGatherAddress(OffsetAddressNode address, Loop loop, VectorArchitecture arch) {
        return isPotentialGatherAddress(address, loop, arch, false, null);
    }

    private static boolean isPotentialGatherAddress(OffsetAddressNode address, Loop loop, VectorArchitecture arch, boolean preVectorizationCheck, NodeFlood flood) {
        return loop.isOutsideLoop(address.getBase()) && isVectorizableComputationRoot(address.getOffset(), loop, arch, preVectorizationCheck, flood);
    }

    @SuppressWarnings("unused")
    private static boolean isSimdifiableDivRemByConstant(Node value, VectorArchitecture arch) {
        ValueNode divisorNode = null;
        Stamp stamp = null;
        if (value instanceof IntegerDivRemNode) {
            divisorNode = ((IntegerDivRemNode) value).getY();
            stamp = ((IntegerDivRemNode) value).stamp(NodeView.DEFAULT);
        } else if (value instanceof FloatingIntegerDivRemNode) {
            divisorNode = ((FloatingIntegerDivRemNode<?>) value).getY();
            stamp = ((FloatingIntegerDivRemNode<?>) value).stamp(NodeView.DEFAULT);
        }
        if (divisorNode != null && divisorNode.isJavaConstant()) {
            long divisor = divisorNode.asJavaConstant().asLong();
            if (CodeUtil.isPowerOf2(divisor)) {
                // This will be expanded to some shifting/masking and simple arithmetic.
                return true;
            } else if (arch != null) {
                // Division by a constant can be optimized to multiplication by a magic constant and
                // some further fiddling. However, the multiplication needs the *high* bits. Try to
                // predict how this will be optimized. If the div/rem is on int or smaller, it uses
                // a long multiply; if it's a long div/rem, it uses mulHigh.
                Stamp divRemStamp = stamp;
                Stamp longStamp = StampFactory.forKind(JavaKind.Long);
                ArithmeticOpTable longTable = ArithmeticOpTable.forStamp(longStamp);
                ArithmeticOpTable.Op op = (PrimitiveStamp.getBits(divRemStamp) <= 32 ? longTable.getMul() : longTable.getMulHigh());
                return arch.getSupportedVectorArithmeticLength(longStamp, arch.getMaxVectorLength(longStamp), op) > 1;
            }
        }

        return false;
    }

    private static boolean isSimdifiableFloatConvert(FloatConvertNode floatConvert, VectorArchitecture arch) {
        return arch.getSupportedVectorConvertLength(floatConvert.stamp(NodeView.DEFAULT), floatConvert.getValue().stamp(NodeView.DEFAULT), arch.getMaxVectorLength(),
                        floatConvert.getFloatConvert()) > 1;
    }

    public static boolean isVectorizableAsInnerNode(Node v, Loop loop, VectorArchitecture arch) {
        return isVectorizableAsInnerNode(v, loop, arch, false, null);
    }

    /**
     * Determine whether the given node is vectorizable in the sense that it can be included in a
     * vector operation's inner nodes. In most cases this doesn't take the node's inputs into
     * account. Most users should use
     * {@link #isVectorizableComputationRoot(Node, Loop, VectorArchitecture)} instead.
     */
    public static boolean isVectorizableAsInnerNode(Node v, Loop loop, VectorArchitecture arch, boolean preVectorizationCheck, NodeFlood flood) {
        Node value = v;
        // do not vectorize nodes representing SIMD values until fully supported by
        // LoopVectorization
        if (value instanceof ValueNode && ((ValueNode) value).stamp(NodeView.DEFAULT) instanceof SimdStamp) {
            return false;
        }
        if (value instanceof FloatingIntegerDivRemNode && ((FloatingIntegerDivRemNode<?>) value).getGuard() != null) {
            // inner nodes with guard edges are not vectorizable currently
            return false;
        }
        if (isDivRem(value) && !isSimdifiableDivRemByConstant(value, arch)) {
            return false;
        }
        if (preVectorizationCheck && value instanceof FloatConvertNode floatConvert) {
            return isSimdifiableFloatConvert(floatConvert, arch);
        }
        if (value instanceof ArithmeticOperation || value instanceof ReinterpretNode || value instanceof ConstantNode) {
            return true;
        }
        if (VectorizeIntegerMinMax.getValue(value.graph().getOptions()) && value instanceof ConditionalNode && MinMaxNode.fromConditional((ConditionalNode) value) != null) {
            return true;
        }
        if (VectorizeConditional.getValue(value.graph().getOptions())) {
            if (value instanceof VirtualConditionalNode) {
                value = ((VirtualConditionalNode) value).conditional();
            }
            if (value instanceof ConditionalNode) {
                // Don't allow conditional nodes if their condition is loop-invariant.
                ConditionalNode conditional = (ConditionalNode) value;
                if (loop.isOutsideLoop(conditional.condition())) {
                    return false;
                } else {
                    return vectorizableCondition(conditional.condition(), loop, arch, preVectorizationCheck, flood);
                }
            }
            if (value instanceof ShortCircuitOrNode) {
                // We can't guarantee short-circuiting behavior in a vector loop.
                return false;
            }
            if (value instanceof LogicNode) {
                // Logic nodes are checked by their usages (If and Conditional nodes). If we get
                // here, those checks have succeeded, so we don't need to check anything else.
                return true;
            }
        }
        return false;
    }

    private static InductionVariable getInductionVariable(Loop loop, Node index) {
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        if (index instanceof OffsetAddressNode) {
            OffsetAddressNode address = (OffsetAddressNode) index;
            if (loop.isOutsideLoop(address.getBase())) {
                return ivs.get(address.getOffset());
            } else {
                return ivs.get(address.getBase());
            }
        } else {
            return ivs.get(index);
        }
    }

    public static boolean canVectorizeIv(StructuredGraph graph, InductionVariable iv) {
        return iv != null && iv.isConstantStride() && (VectorizeNegativeStride.getValue(graph.getOptions()) || iv.constantStride() > 0);
    }

    public static boolean isVectorizableCompare(CompareNode compare, Loop loop, VectorArchitecture arch) {
        Stamp xStamp = compare.getX().stamp(NodeView.DEFAULT);
        // Can only vectorize primitive compares or pointer equals comparisons of object pointers.
        if (xStamp instanceof PrimitiveStamp) {
            return true;
        } else if (compare instanceof PointerEqualsNode && arch.isVectorizableObjectStamp(xStamp)) {
            return true;
        } else if (compare instanceof ObjectEqualsNode && isOptimizableObjectEquals((ObjectEqualsNode) compare, loop) != null) {
            return true;
        }
        return false;
    }

    public static boolean isVectorizableIsNull(IsNullNode isNull, VectorArchitecture arch) {
        Stamp inputStamp = isNull.getValue().stamp(NodeView.DEFAULT);
        return arch.isVectorizableObjectStamp(inputStamp);
    }

    private static boolean vectorizableCondition(LogicNode condition, Loop loop, VectorArchitecture arch, boolean preVectorizationCheck, NodeFlood flood) {
        if (loop.isOutsideLoop(condition)) {
            /*
             * During the pre-vectorization check, we can have loop invariant conditions that
             * haven't been speculated/hoisted/peeled/etc. yet. During actual vectorization, we can
             * just use the value from outside the loop. In either case, this doesn't prevent
             * vectorization.
             */
            return true;
        }
        // only support simple comparisons and null checks for now
        if (!((condition instanceof CompareNode && isVectorizableCompare((CompareNode) condition, loop, arch)) ||
                        condition instanceof IntegerTestNode ||
                        (condition instanceof IsNullNode isNull && isVectorizableIsNull(isNull, arch)))) {
            condition.getDebug().log(DebugContext.DETAILED_LEVEL, "currently unsupported condition %s", condition);
            return false;
        }
        for (Node conditionInput : condition.inputs()) {
            if (!isVectorizableComputationRoot(conditionInput, loop, arch, preVectorizationCheck, flood)) {
                condition.getDebug().log(DebugContext.DETAILED_LEVEL, "condition %s's input %s is not vectorizable", condition, conditionInput);
                return false;
            }
        }
        return true;
    }

    private static FixedNode vectorizableConditional(IfNode ifNode, ArrayList<FixedNode> bodyNodes, ArrayList<IfNode> ifNodesToConditionalize, boolean allowFloatingPointConditionals,
                    boolean ignoreAnchored) {
        if (VectorizeConditional.getValue(ifNode.getOptions()) && canBeConvertedToConditional(ifNode, allowFloatingPointConditionals, ignoreAnchored)) {
            // Find the next node in the loop to visit after this if.
            FixedNode next = null;
            FixedNode trueEnd = ifNode.trueSuccessor().next();
            FixedNode falseEnd = ifNode.falseSuccessor().next();
            if (trueEnd instanceof EndNode && trueEnd.hasExactlyOneUsage() && falseEnd instanceof EndNode && falseEnd.hasExactlyOneUsage() &&
                            trueEnd.usages().first() == falseEnd.usages().first()) {
                Node usage = trueEnd.usages().first();
                if (usage instanceof MergeNode && ((MergeNode) usage).forwardEndCount() == 2) {
                    next = ((MergeNode) usage).next();
                } else {
                    ifNode.getDebug().log(DebugContext.DETAILED_LEVEL, "expected merge with 2 forward ends for %s, got: %s", ifNode, usage);
                    return null;
                }
            } else if (trueEnd instanceof LoopEndNode && falseEnd instanceof LoopEndNode) {
                next = trueEnd;
            } else {
                ifNode.getDebug().log(DebugContext.DETAILED_LEVEL, "some kind of unsupported control flow at ", ifNode);
                return null;
            }
            ifNodesToConditionalize.add(ifNode);
            // All such if nodes will be converted to conditionals, or we will abort
            // vectorization of this loop. So don't store it in bodyNodes after all.
            assert bodyNodes.get(bodyNodes.size() - 1) == ifNode : bodyNodes + " if=" + ifNode;
            bodyNodes.remove(bodyNodes.size() - 1);
            return next;
        }
        return null;
    }

    private static boolean canBeConvertedToConditional(IfNode ifNode, boolean allowFloatingPointConditionals, boolean ignoreAnchored) {
        FixedNode nextTrueNode = ifNode.trueSuccessor().next();
        FixedNode nextFalseNode = ifNode.falseSuccessor().next();
        if (nextTrueNode instanceof AbstractEndNode && nextFalseNode instanceof AbstractEndNode) {
            return ConditionalMoveOptimizationPhase.canBeOptimized(ifNode, (AbstractEndNode) nextTrueNode, (AbstractEndNode) nextFalseNode, allowFloatingPointConditionals, ignoreAnchored);
        } else {
            return false;
        }
    }

    /**
     * We can vectorize certain deopts in loops, for example:
     *
     * <pre>
     * for (int i = 0; i < a.length; i++) {
     *     if (Double.isNan(a[i])) {  // if this is never true dynamically...
     *         foundNaN = true;       // ... then this is a Deopt UnreachedCode
     *     }
     *     b[i] = a[i] * 2;
     * }
     * </pre>
     *
     * If the {@code if}'s body has never been entered, it will be represented as a
     * {@code Deopt UnreachedCode}. We might also have null checks or bounds checks left over in the
     * loop. We can vectorize the condition and turn a deopt into a vectorized guard as long as the
     * deopt precedes any side effects in the loop body (i.e., {@code seenWrite} is {@code false})
     * and we can ensure that it will have a correct frame state.
     *
     * If {@code ifNode} is a deopt exit out of the loop and no writes precede it in the loop, this
     * method returns the {@code ifNode}'s successor that continues the loop body. Otherwise,
     * returns {@code null} to signal that this is not a vectorizable deopt.
     */
    private static FixedNode vectorizableDeopt(IfNode ifNode, boolean seenWrite) {
        if (!seenWrite && VectorizeDeopts.getValue(ifNode.getOptions())) {
            FixedNode next = null;
            // Allow if nodes that can deopt if we haven't seen any writes yet. Vectorized
            // deopts must precede any side effects inside the loop.
            DeoptData deoptData = getDeoptExit(ifNode);
            if (deoptData == null) {
                ifNode.getDebug().log(DebugContext.DETAILED_LEVEL, "found no unique deopt branch at %s", ifNode);
                return null;
            }
            if (deoptData.deoptBranch() == DeoptBranch.TRUE_DEOPT_BRANCH) {
                next = ifNode.falseSuccessor();
            } else {
                next = ifNode.trueSuccessor();
            }
            return next;
        }
        return null;
    }

    public static class DeoptData {
        // The branch of the original if on which to deopt.
        private DeoptBranch branch;
        // The deopt itself.
        private DeoptimizeNode deopt;

        DeoptData(DeoptBranch deoptBranch, DeoptimizeNode deopt) {
            this.branch = deoptBranch;
            this.deopt = deopt;
        }

        public DeoptBranch deoptBranch() {
            return branch;
        }

        DeoptimizeNode deopt() {
            return deopt;
        }
    }

    /**
     * Check if {@code begin} leads directly to a control sink other than a normal
     * {@link ReturnNode}. This path is probably some sort of guard.
     */
    private static boolean isNonReturnControlSink(AbstractBeginNode begin) {
        FixedNode node = begin;
        while (node instanceof BeginNode) {
            node = ((BeginNode) node).next();
        }
        return node instanceof ControlSinkNode && !(node instanceof ReturnNode);
    }

    /**
     * If exactly one of the {@code ifNode}'s successors is a deopt, return a corresponding
     * {@link DeoptData} instance. Return null otherwise.
     */
    public static DeoptData getDeoptExit(IfNode ifNode) {
        DeoptimizeNode trueDeopt = findDeoptAtLoopExit(ifNode.trueSuccessor());
        DeoptimizeNode falseDeopt = findDeoptAtLoopExit(ifNode.falseSuccessor());
        if (trueDeopt != null && falseDeopt == null) {
            return new DeoptData(DeoptBranch.TRUE_DEOPT_BRANCH, trueDeopt);
        } else if (trueDeopt == null && falseDeopt != null) {
            return new DeoptData(DeoptBranch.FALSE_DEOPT_BRANCH, falseDeopt);
        } else {
            return null;
        }
    }

    private static DeoptimizeNode findDeoptAtLoopExit(FixedNode possibleExitNode) {
        FixedNode exit = possibleExitNode;
        /*
         * Skip all begins. Also skip all state split proxies that captured a precise deopt state
         * but are no longer relevant after FSA.
         */
        while (exit instanceof BeginNode || (exit instanceof StateSplitProxyNode stateSplitProxy && stateSplitProxy.hasNoUsages() && stateSplitProxy.stateAfter() == null)) {
            exit = ((FixedWithNextNode) exit).next();
        }
        if (exit instanceof DeoptimizeNode) {
            return (DeoptimizeNode) exit;
        } else {
            return null;
        }
    }

    /**
     * Check the phis on the loop to determine if we are likely to turn them into simdifiable vector
     * folds. The loop vectorizer builds high-level fold operations even for computations where we
     * will usually be unable to generate SIMD code. For example, summing a floating-point array
     * will be "vectorized" into a high-level fold, but we will not generate SIMD code for it
     * because we would have to reassociate the operation. Such vectorizable but not simdifiable
     * folds should *not* block unrolling of the loop.
     */
    private static boolean allPhisLookLikeSimdifiableFolds(Loop loop, ArrayList<FixedNode> bodyNodes, VectorArchitecture arch, boolean beforeGuardMovement) {
        StructuredGraph graph = loop.loopBegin().graph();
        int potentiallyVectorizableFolds = 0;
        final boolean vectorizeFolds = VectorizeFoldShaped.getValue(graph.getOptions());
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        for (PhiNode phi : loop.loopBegin().phis().snapshot()) {
            graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "check loop phi %s for simdifiability", phi);
            if (ivs.get(phi) != null) {
                continue;
            }
            if (beforeGuardMovement && onlyUsedByLoopFrameStates(phi, loop)) {
                // The loop frame state and its inputs are removed by
                // RemoveLoopFrameStatesPhase before loop vectorization.
                continue;
            }
            if (phi instanceof ValuePhiNode) {
                boolean vectorizablePhi = vectorizeFolds && isVectorizableComputationRoot(phi, loop, arch, true, null);
                boolean simdifiablePhi = isSimdifiablePhi(phi);
                if (vectorizablePhi && simdifiablePhi) {
                    potentiallyVectorizableFolds++;
                } else {
                    graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s is not %s because of phi %s", loop, (vectorizablePhi ? "simdifiable" : "vectorizable"), phi);
                    return false;
                }
            }
        }

        // Before speculative guard movement (and guard lowering to fixed guards) we can't tell if
        // loops are really empty.
        boolean emptyLoop = !beforeGuardMovement && isEmptyLoopBody(bodyNodes);
        if (emptyLoop && potentiallyVectorizableFolds == 0) {
            graph.getDebug().log(DebugContext.DETAILED_LEVEL, "Loop %s is empty, it won't be vectorized", loop);
            return false;
        }

        graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "found %d simdifiable-looking phis", potentiallyVectorizableFolds);
        return true;
    }

    private static boolean isSimdifiablePhi(PhiNode phi) {
        if (phi.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
            return true;
        }
        if (phi.stamp(NodeView.DEFAULT) instanceof FloatStamp && phi.singleBackValueOrThis() instanceof BinaryArithmeticNode<?>) {
            BinaryArithmeticNode<?> binary = (BinaryArithmeticNode<?>) phi.singleBackValueOrThis();
            return binary.getArithmeticOp().isAssociative() && binary.getArithmeticOp().isCommutative();
        }
        return false;
    }

    private static boolean onlyUsedByLoopFrameStates(ValueNode value, Loop loop) {
        FrameState loopBeginState = loop.loopBegin().stateAfter();
        if (loopBeginState == null && value.hasUsages()) {
            return false;
        }
        EconomicSet<FrameState> exitStates = EconomicSet.create();
        for (LoopExitNode exit : loop.loopBegin().loopExits()) {
            if (exit.stateAfter() != null) {
                exitStates.add(exit.stateAfter());
            }
        }

        for (Node usage : value.usages()) {
            NodeFlood flood = new NodeFlood(value.graph());
            flood.add(usage);
            for (Node n : flood) {
                if (n == loopBeginState || (n instanceof FrameState && exitStates.contains((FrameState) n))) {
                    continue;
                }
                if (n instanceof VirtualState || n instanceof ProxyNode) {
                    flood.addAll(n.usages());
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isEmptyLoopBody(ArrayList<FixedNode> bodyNodes) {
        assert !bodyNodes.isEmpty() : "expect at least a loop end";
        if (bodyNodes.size() == 1) {
            return bodyNodes.get(0) instanceof LoopEndNode;
        }
        if (bodyNodes.size() == 2) {
            return bodyNodes.get(0) instanceof SafepointNode && bodyNodes.get(1) instanceof LoopEndNode;
        }
        return false;
    }

    /**
     * Detect a pattern of {@code Uncompress(x) == y} where {@code x} is inside the loop and
     * {@code y} is outside. Loop vectorization preprocessing can change this to
     * {@code x == Compress(y)}.
     *
     * @return a pair of the uncompression and the other node if the pattern is matched;
     *         {@code null} otherwise
     */
    public static Pair<CompressionNode, ValueNode> isOptimizableObjectEquals(ObjectEqualsNode objectEquals, Loop loop) {
        ValueNode x = objectEquals.getX();
        ValueNode y = objectEquals.getY();
        CompressionNode uncompress = null;
        ValueNode other = null;
        if (x instanceof CompressionNode && ((CompressionNode) x).getOp() == CompressionOp.Uncompress) {
            uncompress = (CompressionNode) x;
            other = y;
        } else if (y instanceof CompressionNode && ((CompressionNode) y).getOp() == CompressionOp.Uncompress) {
            uncompress = (CompressionNode) y;
            other = x;
        }
        if (uncompress != null && !loop.isOutsideLoop(uncompress) && loop.isOutsideLoop(other)) {
            return Pair.create(uncompress, other);
        }
        return null;
    }

    /**
     * Checks if {@code phi} is a vectorizable fold in {@code loop}.
     *
     * @param loop loop containing {@code phi}
     * @param phi {@link ValuePhiNode} to check
     * @return true if {@code phi} is probably a vectorizable fold
     */
    public static boolean isVectorizableFold(Loop loop, ValuePhiNode phi) {
        if (loop.getInductionVariables().containsKey(phi)) {
            // not a fold but if we can vectorize sequences, this is known to be vectorizable
            return VectorizeSequence.getValue(phi.getOptions());
        }

        // this phi is for sure recursive if it contains a value
        // inside the loop which is not a phi at the loop begin
        boolean isRecursive = false;
        for (Node input : phi.values()) {
            if (!loop.isOutsideLoop(input) && !loop.loopBegin().isPhiAtMerge(input)) {
                isRecursive = true;
                break;
            }
        }
        if (!isRecursive) {
            return false;
        }

        // check if there are multiple non-IV ValuePhis in the loop
        boolean seenNonIV = false;
        boolean needRecursiveCheck = false;
        for (PhiNode p : loop.loopBegin().phis()) {
            if (loop.getInductionVariables().containsKey(p) || !(p instanceof ValuePhiNode)) {
                continue;
            }
            if (seenNonIV) {
                // This is the second non-IV value phi on this loop begin, we need the complex
                // check.
                needRecursiveCheck = true;
                break;
            } else {
                seenNonIV = true;
            }
        }
        if (!needRecursiveCheck) {
            // Only non-IV value phi on this loop begin.
            return true;
        }

        // upwards search for recursive path through other non-IV phis
        NodeFlood flood = new NodeFlood(phi.graph());
        flood.addAll(phi.values());
        for (Node value : flood) {
            if (loop.isOutsideLoop(value) || value == loop.loopBegin().stateAfter() || (loop.getInductionVariables().containsKey(value) && VectorizeSequence.getValue(phi.getOptions()))) {
                continue;
            }
            if (value != phi && loop.loopBegin().isPhiAtMerge(value) && value instanceof ValuePhiNode) {
                if (!isVectorizableFoldSeenPhi(loop, phi, (ValuePhiNode) value)) {
                    // Recursive paths crossing a single phi that has another phi as non-recursive
                    // input (case 1) are OK. Recursive paths crossing multiple phis (case 2) cannot
                    // be vectorized. Call to avoid detecting case 1 as non-vectorizable.
                    /*-
                     * 1)    ...   ...             2)     ...    ______,
                     *         \   /                        \   /      |
                     * value-> PHI_1     ______,       ...  PHI_1 <-value
                     *            \     /      |         \ /           |
                     *       phi-> PHI_0  ...  |         ADD   ...     |
                     *             /   \ /     |           \   /       |
                     *             |   ADD     |     phi-> PHI_0  ...  |
                     *            ...    \_____|           /   \ /     |
                     *                                     |   ADD     |
                     *                                    ...    \_____|
                     */
                    return false;
                }
                // if the check returned true, we do not need to elaborate this phi further
            } else {
                flood.addAll(value.inputs());
            }
        }

        return true;
    }

    private static boolean isVectorizableFoldSeenPhi(Loop loop, ValuePhiNode root, ValuePhiNode phi) {
        if (loop.getInductionVariables().containsKey(phi)) {
            // folds depending on induction variables are possible if we can vectorize sequences
            return VectorizeSequence.getValue(phi.getOptions());
        }
        if (isRecursivePhi(loop, phi)) {
            // this would be a fold of a fold, partially vectorizing this loop
            // is not worth it (GR-20247)
            return false;
        }

        // upwards search to find recursive path back to root
        NodeFlood flood = new NodeFlood(phi.graph());
        flood.addAll(phi.values());
        for (Node value : flood) {
            if (loop.isOutsideLoop(value) || (loop.getInductionVariables().containsKey(value) && VectorizeSequence.getValue(phi.getOptions()))) {
                continue;
            }
            if (value == root) {
                // found recursive path containing multiple phis. this is not vectorizable
                return false;
            } else if (loop.loopBegin().isPhiAtMerge(value) &&
                            value instanceof ValuePhiNode && isRecursivePhi(loop, (ValuePhiNode) value)) {
                // this would be a fold of a fold, partially vectorizing this loop
                // is not worth it (GR-20247)
                return false;
            } else {
                flood.addAll(value.inputs());
            }
        }

        // if no path back to root is found, root may be vectorizable
        return true;
    }

    private static boolean isRecursivePhi(Loop loop, ValuePhiNode phi) {
        if (loop.getInductionVariables().containsKey(phi)) {
            // not a fold but if we can vectorize sequences, this is known to be vectorizable
            return VectorizeSequence.getValue(phi.getOptions());
        }
        boolean isRecursive = false;
        for (Node input : phi.values()) {
            if (!loop.isOutsideLoop(input) && !loop.loopBegin().isPhiAtMerge(input)) {
                isRecursive = true;
                break;
            }
        }
        return isRecursive;
    }

    /**
     * Determines if peeling can harm vectorization and thus should be avoided. Peeling and
     * vectorization mostly have not many interactions that are harmful except vector
     * materialization and alignment. In general, aligining accesses in the vectorized loop version
     * can be performance critical, yet there is no good proxy to when peeling can cause such
     * problems and when not.
     * <p/>
     *
     * A more immediate problem is the interaction for vector loops that write to newly allocated
     * arrays. For such cases peeling a loop can cause problems because we cannot fold the array
     * allocation's zeroing into the actual vector loop propagating the array contents. An example
     * would be
     *
     * <pre>
     * int[] arr = new int[len];
     * for (int i = 0; i < arr.length; i++) {
     *     arr[i] = someVectComputation;
     * }
     * </pre>
     *
     * Peeling such loops is generally not beneficial and should be avoided. Thus, we filter out
     * exactly those patterns.
     * <p/>
     *
     * Another pattern involves loops with a small constant number of iterations that is a multiple
     * of common vector sizes:
     *
     * <pre>
     *     for (int i = 0; i < 32; i++) {
     *         ...
     *     }
     * </pre>
     *
     * If we vectorize this loop, we are likely able to cover all of its iterations with full-sized
     * vectors, with no need for tail processing with smaller vectors or scalar code. On the other
     * hand, peeling it will create the worst case for tail processing since we will likely miss the
     * preferred vector size by 1. Therefore, we also filter out such loops.
     */
    public static boolean peelingMayHarmVectorization(Loop loop) {
        /* Heuristic magic numbers. */
        final int maxConstantIterations = 1024;
        final int desiredVectorAlignment = 4;  // e.g., 4 floats in a 16-byte vector
        if (VectorLoopUtility.isConstantLoopCount(loop, maxConstantIterations)) {
            /*
             * isConstantLoopCount also finds "hidden" constants like "either 0 or 32 iterations".
             * Check if the max trip count node's stamp guarantees that the value is a multiple of
             * the desired alignment.
             */
            IntegerStamp maxTripCountStamp = (IntegerStamp) loop.counted().maxTripCountNode().stamp(NodeView.DEFAULT);
            GraalError.guarantee(CodeUtil.isPowerOf2(desiredVectorAlignment), "must be a power of 2: %s", desiredVectorAlignment);
            long mayBeSetLowBits = maxTripCountStamp.mayBeSet() & (desiredVectorAlignment - 1);
            return mayBeSetLowBits == 0;
        }

        boolean peelingMayHarmVectorization = false;
        boolean seenWrite = false;
        for (WriteNode w : loop.inside().nodes().filter(WriteNode.class)) {
            if (seenWrite) {
                // we already saw a write, 2 writes indicate a different pattern
                return false;
            }
            seenWrite = true;
            AddressNode address = w.getAddress();
            ValueNode base = address.getBase();
            if (address instanceof OffsetAddressNode offSet) {
                InductionVariable offsetIV = loop.getInductionVariables().get(offSet.getOffset());
                if (offsetIV != null) {
                    BasicInductionVariable bIV = offsetIV.getRootIV();
                    if (base instanceof VirtualizableAllocation && bIV.initNode().isConstant() &&
                                    bIV.initNode().asJavaConstant().asLong() == 0) {
                        peelingMayHarmVectorization = true;
                    }
                }
            }
        }
        return peelingMayHarmVectorization;
    }
}
