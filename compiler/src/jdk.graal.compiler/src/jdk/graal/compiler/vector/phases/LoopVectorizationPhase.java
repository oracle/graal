/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeConditional;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeDeopts;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeFoldShaped;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeGather;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeIntegerMinMax;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeMapShaped;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeReachabilityFences;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeSafepoints;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VectorizeSequence;
import static jdk.graal.compiler.vector.phases.LoopVectorizationPhase.Options.VerifyLoopVectorization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.Pair;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.guards.optimistic.memory.OptimisticLoopAliasGuardNode;
import jdk.graal.compiler.guards.optimistic.memory.OptimisticMemoryEdge;
import jdk.graal.compiler.vector.nodes.ShiftableVectorNode;
import jdk.graal.compiler.vector.nodes.VectorLogicNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.consumer.FoldVectorNode;
import jdk.graal.compiler.vector.nodes.consumer.LowerableVectorConsumer;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorGuardNode.DeoptBranch;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopMarkerNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorLoopNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorReachabilityFenceNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorSafepointNode;
import jdk.graal.compiler.vector.nodes.consumer.VectorWriteNode;
import jdk.graal.compiler.vector.nodes.op.CompareVectorNode;
import jdk.graal.compiler.vector.nodes.op.FloatingVectorGatherNode;
import jdk.graal.compiler.vector.nodes.op.MapVectorNode;
import jdk.graal.compiler.vector.nodes.op.VectorIsNullNode;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.producer.FloatingVectorReadNode;
import jdk.graal.compiler.vector.nodes.producer.InvariantVectorLogicNode;
import jdk.graal.compiler.vector.nodes.producer.SequenceVectorNode;
import jdk.graal.compiler.vector.nodes.subgraph.SubGraphUtil;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;
import jdk.graal.compiler.vector.phases.LoopVectorizationAnalysis.DeoptData;
import jdk.graal.compiler.vector.replacements.VirtualConditionalNode;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.VoidStamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.DuplicationReplacement;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.CompressionNode.CompressionOp;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardedValueNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.ScheduleResult;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.calc.MinMaxNode;
import jdk.graal.compiler.nodes.calc.ObjectEqualsNode;
import jdk.graal.compiler.nodes.calc.PointerEqualsNode;
import jdk.graal.compiler.nodes.calc.ShiftNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.PostRunCanonicalizationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerExactOverflowNode;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.architecture.VectorLoweringProvider;

/**
 * Loop (auto-)vectorization detects code patterns in loops that can take advantage of
 * <a href="https://en.wikipedia.org/wiki/SIMD">SIMD</a> (single instruction multiple data)
 * instructions for increased throughput. For example, a SIMD add instruction on CPUs with the AVX2
 * instruction set performs up to 4 {@code double} or 8 {@code int} add operations at the same time.
 * Loop vectorization transforms loops into a form that exploits such operations to perform the
 * computations from multiple independent loop iterations in parallel. Speedups are often close to
 * the theoretical factor given by the number of iterations executed in parallel.
 * </p>
 *
 * The main patterns handled by our loop vectorizer are "map" and "fold" loops. Map loops correspond
 * to the operation of {@link Stream#map}. They transform arrays element by element to produce new
 * array values, for example:
 *
 * <pre>
 * for (int i = 0; i < a.length; i++) {
 *     a[i] = b[i] + c[i];
 * }
 * </pre>
 *
 * The vectorized version of this loop loads, adds, and stores multiple elements in parallel. In
 * pseudocode:
 *
 * <pre>
 * for (int i = 0; i < a.length - 3; i += 4) {
 *     a[i, i+1, i+2, i+3] = b[i, i+1, i+2, i+3] + c[i, i+1, i+2, i+3];
 * }
 * [omitting cleanup code for the remaining elements]
 * </pre>
 *
 * In this example, the loop has been vectorized with a vector length of 4, and we can expect a
 * speedup of close to 4x. The exact vector length chosen for any loop depends on the exact
 * operations used and on the features offered by the target CPU.
 * </p>
 *
 * Fold (also known as "reduce", corresponding to {@link Stream#reduce}) loops read arrays and apply
 * some operation to combine all elements into a single one, for example:
 *
 * <pre>
 * int sum = 0;
 * for (int i = 0; i < a.length; i++) {
 *     sum += a[i];
 * }
 * </pre>
 *
 * Assuming a vector length of 8, loop vectorization transforms this into a loop like this:
 *
 * <pre>
 * int_x_8 partialSum = {0, 0, 0, 0, 0, 0, 0, 0};
 * for (int i = 0; i < a.length - 7; i += 7) {
 *     partialSum += a[i, i+1, i+2, ..., i+7];
 * }
 * int sum = partialSum[0] + partialSum[1] + ... + partialSum[7];
 * [omitting cleanup code for the remaining elements]
 * </pre>
 *
 * As in this example the main loop is vectorized with a vector length of 8, the expected speedup is
 * close to 8x.
 * </p>
 *
 * Note that fold operations are typically not available for floating-point values: Floating point
 * computations would give differently rounded results because loop vectorization would rearrange
 * the order of arithmetic operations. This problem does not apply to integer computations.
 * </p>
 *
 * Depending on available hardware features, the loop vectorizer also handles certain kinds of
 * conditional code in loops, gather operations (indirect array reads of the form
 * {@code array[indices[i]]}), and many combinations of these basic patterns.
 * </p>
 *
 * @implNote This phase only handles the high level part of recognizing potentially vectorizable
 *           operations. It analyzes loops and builds corresponding "vector consumers" like
 *           {@link VectorWriteNode} or {@link FoldVectorNode}. These are inserted before the loop
 *           in question, and the corresponding scalar nodes are removed from the original loop.
 *           This should leave an empty loop that can then be removed by
 *           {@link RemoveEmptyLoopsPhase}.
 *           </p>
 *
 *           This phase is mostly target- and VM-independent. It generates high-level vector
 *           representations of operations without knowing whether we will actually be able to
 *           generate SIMD code for them. (We can always fall back to scalar code equivalent to the
 *           original loop.) In particular, we even generate fold nodes for floating-point
 *           computations. The high-level representation is useful for target-independent testing
 *           and for fusion of vector loops.
 *           </p>
 *
 *           Key steps in the rest of the loop vectorization pipeline:
 *           </p>
 *
 *           Mid tier:
 *           <ul>
 *           <li>{@link VectorMaterializationPhase} fuses vector operations and array
 *           initializations to avoid useless allocations and writes</li>
 *           </ul>
 *
 *           Low tier:
 *           <ul>
 *           <li>{@link VectorLoweringPhase} analyzes high-level vector operations to determine
 *           actually supported SIMD lengths on the target and sets up the structure of the final
 *           vector (or scalar) loop and the tail consumer code</li>
 *           <li>{@link VectorConsumerPhase} places fixed-length versions of the high-level vector
 *           operations in the vector loop and the tail consumer structures</li>
 *           <li>{@link SimdifyVectorPhase} transforms these high-level vector nodes into SIMD nodes
 *           </ul>
 */
public class LoopVectorizationPhase extends PostRunCanonicalizationPhase<MidTierContext> {

    public static class Options {

        //@formatter:off
        @Option(help = "Enable vectorization of loops")
        public static final OptionKey<Boolean> VectorizeLoops = new OptionKey<>(true);
        @Option(help = "Enable vectorization of loops implementing a higher-order 'map' function.")
        public static final OptionKey<Boolean> VectorizeMapShaped = new OptionKey<>(true);
        @Option(help = "Enable vectorization of loops implementing a higher-order 'fold' function.")
        public static final OptionKey<Boolean> VectorizeFoldShaped = new OptionKey<>(true);
        @Option(help = "Enable vectorization of loops with conditional deopts before writes.")
        public static final OptionKey<Boolean> VectorizeDeopts = new OptionKey<>(true);
        @Option(help = "Enable vectorization of loops with negative strides.")
        public static final OptionKey<Boolean> VectorizeNegativeStride = new OptionKey<>(true);
        @Option(help = "Enable vectorization of sequence values.")
        public static final OptionKey<Boolean> VectorizeSequence = new OptionKey<>(true);
        @Option(help = "Enable vectorization of conditional code.")
        public static final OptionKey<Boolean> VectorizeConditional = new OptionKey<>(true);
        @Option(help = "Enable vectorization of vector gather operations.")
        public static final OptionKey<Boolean> VectorizeGather = new OptionKey<>(true);
        @Option(help = "Enable vectorization of loops with safepoints.")
        public static final OptionKey<Boolean> VectorizeSafepoints = new OptionKey<>(true);
        @Option(help = "Enable vectorization of loops with reachability fences.")
        public static final OptionKey<Boolean> VectorizeReachabilityFences = new OptionKey<>(true);
        @Option(help = "Enable vectorization of integer min/max operations.")
        public static final OptionKey<Boolean> VectorizeIntegerMinMax = new OptionKey<>(true);

        @Option(help = "Keep the original loop as the post-loop during loop vectorization", type = OptionType.Debug)
        public static final OptionKey<Boolean> LoopVectorizationKeepPostLoop = new OptionKey<>(false);
        @Option(help = "Run expensive checks to verify the graph after loop vectorization.", type = OptionType.Debug)
        public static final OptionKey<Boolean> VerifyLoopVectorization = new OptionKey<>(false);
        //@formatter:on
    }

    public static final CounterKey LoopsConsidered = DebugContext.counter("LoopVectorization_LoopsConsidered");
    public static final CounterKey LoopsPassingStructuralChecks = DebugContext.counter("LoopVectorization_LoopsPassingStructuralChecks");

    // When compiling code to run on SVM, we should never try to vectorize loops with deopts because
    // this conflicts with SVM's transformation of deopts into guards. This flag is set by the
    // constructor based on a flag received from the compiler configuration.
    private final boolean allowVectorizationOfDeopts;

    /**
     * Flag indicating whether the phase plan contains optimistic aliasing analysis. Only used for
     * phase plan verification.
     */
    private final boolean optimisticAliasingAnalysisEnabled;

    public LoopVectorizationPhase(boolean allowVectorizationOfDeopts, boolean optimisticAliasingAnalysisEnabled, CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
        this.allowVectorizationOfDeopts = allowVectorizationOfDeopts;
        this.optimisticAliasingAnalysisEnabled = optimisticAliasingAnalysisEnabled;
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    /**
     * This class models the suspended effects of vectorizing some part of the loop. The
     * {@link #apply} method performs the actual destructive modifications of the graph. The
     * intention is to first collect the effects of vectorizing different operations in the graph,
     * but to only apply the effects once we know that everything will succeed.
     */
    private static class VectorizationEffects {
        /**
         * Applies vectorization effects to the graph, destructively modifying or removing scalar
         * computations that are replaced by vectorized forms.
         *
         * @param insertionPoint the program point after which new vector consumers are to be
         *            inserted
         * @param vectorizedOperations filled in by the method with the vectorized operations it
         *            builds
         * @param keepPostLoop if {@code true}, generate vector code for the case where remaining
         *            iterations are processed by the original post loop
         * @param vectorLength the number of loop iterations to be processed by each vectorized
         *            operation
         *
         * @return the last inserted consumer, to be used as the insertion point for later consumers
         */
        @SuppressWarnings("unused")
        FixedWithNextNode apply(FixedWithNextNode insertionPoint, ArrayList<LowerableVectorConsumer> vectorizedOperations, boolean keepPostLoop, ValueNode vectorLength) {
            return insertionPoint;
        }

        /**
         * Returns an instance that doesn't need to apply any effects.
         */
        static VectorizationEffects nop() {
            return new VectorizationEffects();
        }
    }

    /** Special {@link VectorizationEffects} subclass to be used when vectorizing guards. */
    private abstract static class GuardVectorizationEffects extends VectorizationEffects {
        /**
         * Change the guard inputs of any new nodes created since {@code beforeVectorization}. To be
         * used when the post loop is kept while vectorizing the loop. In this case, newly created
         * vector nodes still refer to guards inside the original (now post) loop. Change them to
         * point to the corresponding vectorized guards instead.
         */
        abstract void rewireGuardEdgesFromPostLoop(Graph.Mark beforeVectorization);

        private static final GuardVectorizationEffects NOP_INSTANCE = new GuardVectorizationEffects() {
            @Override
            void rewireGuardEdgesFromPostLoop(Mark beforeVectorization) {
                /* Nothing to do. */
            }
        };

        /**
         * Returns an instance that doesn't need to apply any effects.
         */
        static GuardVectorizationEffects nop() {
            return NOP_INSTANCE;
        }
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        /*
                         * It can make sense to run loop vectorization without optimistic aliasing
                         * analysis, so only check this constraint if that phase is enabled. If it
                         * is, it must run before loop vectorization.
                         */
                        optimisticAliasingAnalysisEnabled ? NotApplicable.unlessRunAfter(this, StageFlag.OPTIMISTIC_ALIASING, graphState)
                                        : ALWAYS_APPLICABLE,
                        NotApplicable.unlessRunAfter(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunAfter(this, StageFlag.VALUE_PROXY_REMOVAL, graphState));
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops();
    }

    @SuppressWarnings("try")
    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        if (graph.hasLoops()) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);

            // for vectorization we want a broader set of "potential" IVs
            for (Loop loop : loopsData.loops()) {
                // get ivs with proper deopt path liveness
                loop.getInductionVariables(true, true);
            }

            loopsData.detectCountedLoops();

            boolean preprocessingChanged = false;
            for (Loop loop : loopsData.countedLoops()) {
                preprocessingChanged |= preprocessLoop(graph, loop);
            }
            if (preprocessingChanged) {
                loopsData = context.getLoopsDataProvider().getLoopsData(graph);
                loopsData.detectCountedLoops();
            }
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after preprocessing for loop vectorization, changed: %s", preprocessingChanged);

            ArrayList<Loop> vectorizedLoops = new ArrayList<>();
            Mark beforeVectorization = graph.getMark();
            final EconomicSetNodeEventListener inputChanges = new EconomicSetNodeEventListener(EnumSet.of(NodeEvent.INPUT_CHANGED, NodeEvent.CONTROL_FLOW_CHANGED));
            try (NodeEventScope scope = graph.trackNodeEvents(inputChanges)) {
                for (Loop loop : loopsData.countedLoops()) {
                    maybeRefreshIVs(loop, inputChanges);
                    if (!loop.counted().loopMightBeEntered()) {
                        // This loop has 0 iterations, don't try to vectorize it.
                        continue;
                    }
                    boolean vectorizedLoop = false;
                    DebugContext debug = graph.getDebug();
                    try (Indent indent = debug.logAndIndent(DebugContext.VERBOSE_LEVEL, "consider %s for vectorization", loop)) {
                        LoopsConsidered.increment(debug);
                        VectorizableLoopInfo vectorizableLoop = detectVectorizableLoop(loop, context.getProviders());
                        if (vectorizableLoop != null) {
                            debug.log(DebugContext.VERBOSE_LEVEL, "loop looks vectorizable: %s", vectorizableLoop);
                            LoopsPassingStructuralChecks.increment(debug);
                            vectorizedLoop = vectorizeLoop(graph, context, loop, vectorizableLoop);
                        } else {
                            debug.log(DebugContext.VERBOSE_LEVEL, "loop not detected as vectorizable");
                        }
                    }

                    if (vectorizedLoop) {
                        graph.getOptimizationLog().report(LoopVectorizationPhase.class, "LoopVectorization", loop.loopBegin());
                        vectorizedLoops.add(loop);
                        // Any virtual conditionals in the vectorized loop should be turned into an
                        // actual conditional node (to be removed by dead code elimination).
                        for (Node loopNode : graph.getNewNodes(beforeVectorization)) {
                            if (loopNode instanceof VirtualConditionalNode) {
                                ((VirtualConditionalNode) loopNode).commit();
                            }
                        }
                    } else {
                        debug.log(DebugContext.VERBOSE_LEVEL, "did not vectorize this loop");
                    }
                }
            }
            if (VectorizeIntegerMinMax.getValue(graph.getOptions())) {
                /*
                 * Any leftover integer min/max we introduced should revert back to a conditional
                 * form.
                 */
                for (Node newNode : graph.getNewNodes(beforeVectorization)) {
                    if (newNode instanceof MinMaxNode) {
                        MinMaxNode<?> minMax = (MinMaxNode<?>) newNode;
                        ValueNode conditionalForm = graph.addOrUniqueWithInputs(minMax.asConditional(context.getLowerer()));
                        minMax.replaceAndDelete(conditionalForm);
                    }
                }
            }
            if (VectorizeConditional.getValue(graph.getOptions())) {
                // Any leftover virtual conditionals we introduced should revert back to their
                // original form as if nodes.
                for (Node newNode : graph.getNewNodes(beforeVectorization)) {
                    if (newNode instanceof VirtualConditionalNode) {
                        ((VirtualConditionalNode) newNode).revert();
                    }
                }
            }

            cleanUpVectorizedLoops(graph, vectorizedLoops, context, canonicalizer);

            if (VerifyLoopVectorization.getValue(graph.getOptions())) {
                verifyLoopVectorization(graph, context);
            }
        }

    }

    /**
     * Perform small local preprocessing steps that we expect to help vectorizability.
     *
     * @return {@code true} if this method made changes to the graph that invalidated loops data
     */
    private static boolean preprocessLoop(StructuredGraph graph, Loop loop) {
        boolean changed = false;

        /**
         * Maybe convert uncompress(x) == y operations to x == compress(y), see
         * {@link LoopVectorizationAnalysis#isOptimizableObjectEquals}.
         */
        int convertedObjectEquals = 0;
        for (ObjectEqualsNode objectEquals : loop.inside().nodes().filter(ObjectEqualsNode.class)) {
            Pair<CompressionNode, ValueNode> optimizableEquals = LoopVectorizationAnalysis.isOptimizableObjectEquals(objectEquals, loop);
            if (optimizableEquals != null) {
                CompressionNode uncompress = optimizableEquals.getLeft();
                assert uncompress.getOp() == CompressionOp.Uncompress : uncompress + " " + uncompress.getOp();
                ValueNode other = optimizableEquals.getRight();
                ValueNode originalCompressed = uncompress.getValue();
                ValueNode otherCompressed = graph.addOrUniqueWithInputs(uncompress.reverse(other));
                ValueNode replacement = graph.addOrUniqueWithInputs(PointerEqualsNode.create(originalCompressed, otherCompressed, NodeView.DEFAULT));
                objectEquals.replaceAtUsages(replacement);
                convertedObjectEquals++;
                changed = true;
            }
        }
        graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after converting %d object equals nodes to shift uncompressions out of the loop", convertedObjectEquals);

        /**
         * Try to merge loop ends, i.e., transform
         *
         * <pre>
         *     loop {
         *         ...
         *         if (condition) {
         *             someStuff();
         *             continue;
         *         } else {
         *             otherStuff();
         *             continue;
         *         }
         *         // no more code here
         *     }
         * </pre>
         *
         * to
         *
         * <pre>
         *     loop {
         *         ...
         *         if (condition) {
         *             someStuff();
         *         } else {
         *             otherStuff();
         *         }
         *         continue;  // single loop end
         *     }
         * </pre>
         *
         * If all the code in the branches can float, we may be able to transform this to a
         * conditional and vectorize it.
         */
        if (loop.loopBegin().getLoopEndCount() == 2) {
            boolean merged = VectorLoopUtility.mergeLoopEnds(loop.loopBegin());
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after merging loop ends on %s", loop.loopBegin());
            changed |= merged;
        }

        if (changed) {
            new DeadCodeEliminationPhase().apply(graph);
        }
        return changed;
    }

    /**
     * If some input controlling the loop's counter or other induction variables changed, delete and
     * recompute the loop's IVs.
     */
    private static void maybeRefreshIVs(Loop loop, EconomicSetNodeEventListener inputChangedEvents) {
        assert loop.isCounted() : "Must be counted " + loop;
        EconomicSet<Node> changedNodes = inputChangedEvents.getNodes();

        boolean mustRefresh = false;
        if (changedNodes.contains(loop.counted().getLimitTest().condition())) {
            mustRefresh = true;
        } else if (changedNodes.contains(loop.counted().getLimitCheckedIV().strideNode())) {
            mustRefresh = true;
        } else {
            for (Node ivNode : loop.getInductionVariables().getKeys()) {
                if (changedNodes.contains(ivNode)) {
                    mustRefresh = true;
                    break;
                }
            }
        }
        if (mustRefresh) {
            // Resetting the loop's counted status deletes all of the precomputed IVs.
            loop.resetCounted();
            // get ivs with proper deopt path liveness
            loop.getInductionVariables(true, true);
            loop.detectCounted();
        }

        // Replacing a value with a vectorized value may change loops' init or limit values with
        // equivalent ones. It should never change the countedness.
        assert loop.isCounted() : "Must be counted " + loop;
    }

    // Nothing should be left over in the original loop after it is vectorized. Run sub-phases to
    // remove the (now empty) original loops, and check that we really eliminated all of them.
    private static void cleanUpVectorizedLoops(StructuredGraph graph, List<Loop> vectorizedLoops, MidTierContext context, CanonicalizerPhase canonicalizer) {
        new DeadCodeEliminationPhase().apply(graph);
        for (Loop vectorizedLoop : vectorizedLoops) {
            canonicalizer.applyIncremental(graph, context, vectorizedLoop.inside().nodes());
        }
        new RemoveEmptyLoopsPhase(canonicalizer).apply(graph, context);

        for (Loop vectorizedLoop : vectorizedLoops) {
            /**
             * In {@linkplain LoopVectorizationAnalysis#keepPostLoop post loop mode}, the original
             * loop survives. It will be marked as a post loop. Don't complain about it.
             */
            if (vectorizedLoop.loopBegin().isAlive() && !vectorizedLoop.loopBegin().isPostLoop()) {
                throw new PermanentBailoutException("expected to remove %s after vectorization", vectorizedLoop);
            }
        }
    }

    private static boolean verifyLoopVectorization(StructuredGraph graph, CoreProviders context) {
        assert GraphOrder.assertNonCyclicGraph(graph);
        assert graph.getGuardsStage().areFrameStatesAtDeopts() : graph.getGraphState();
        if (Assertions.detailedAssertionsEnabled(graph.getOptions())) {
            // We can't use graph.assertSchedulableGraph() after frame state assignment.
            new SchedulePhase(SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true).apply(graph, context);
        }
        return true;
    }

    @SuppressWarnings("try")
    private boolean vectorizeLoop(StructuredGraph graph, MidTierContext context, Loop loop, VectorizableLoopInfo vectorizableLoop) {
        EconomicMap<WriteNode, InductionVariable> writes = vectorizableLoop.writes;
        ArrayList<FixedNode> bodyNodes = vectorizableLoop.bodyNodes;
        ArrayList<IfNode> ifNodesToConditionalize = vectorizableLoop.ifNodesToConditionalize;

        DebugContext debug = graph.getDebug();
        // Convert if nodes to conditional nodes, if we consider it useful.
        if (VectorizeConditional.getValue(graph.getOptions()) && !ifNodesToConditionalize.isEmpty()) {
            boolean convertedIfNodes = convertIfNodesToConditionals(loop, vectorizableLoop);
            if (!convertedIfNodes) {
                // This loop contains something we consider unprofitable to vectorize.
                debug.log(DebugContext.DETAILED_LEVEL, "failed to perform necessary if conversions for vectorization");
                return false;
            }
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after converting %d if nodes to conditionals", ifNodesToConditionalize.size());
        }
        if (VectorizeIntegerMinMax.getValue(graph.getOptions())) {
            int minMaxCount = recognizeIntegerMinMax(graph, loop.whole().nodes());
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after recognizing %s integer min/max operations", minMaxCount);
        }

        // Collect all the values we will want to vectorize in the loop. These may be
        // shared between different maps and folds.
        VectorArchitecture arch = ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
        LoopValues toVectorize = VectorLoop.valuesToVectorize(loop, arch, bodyNodes, writes, VectorizeDeopts.getValue(graph.getOptions()), VectorizeMapShaped.getValue(graph.getOptions()),
                        VectorizeFoldShaped.getValue(graph.getOptions()), VectorizeReachabilityFences.getValue(graph.getOptions()), VectorizeSafepoints.getValue(graph.getOptions()));
        if (toVectorize == null) {
            // This loop contains some operation we can't vectorize. No need to go on.
            debug.log(DebugContext.DETAILED_LEVEL, "failed to collect values to vectorize");
            return false;
        } else if (toVectorize.isEmpty()) {
            if (!ifNodesToConditionalize.isEmpty()) {
                debug.log(DebugContext.DETAILED_LEVEL, "nothing to vectorize but loop contains control flow, can't vectorize");
                return false;
            }
            debug.log(DebugContext.DETAILED_LEVEL, "empty loop, nothing to do");
            return false;
        }
        debug.log(DebugContext.VERBOSE_LEVEL, "will try to vectorize: %s", toVectorize);
        if ((toVectorize.deopts > 0 && !(allowVectorizationOfDeopts && VectorizeDeopts.getValue(graph.getOptions()))) ||
                        (toVectorize.safepoints > 0 && !(allowVectorizationOfDeopts && VectorizeSafepoints.getValue(graph.getOptions())))) {
            // Don't vectorize this loop as it contains deopts but we are not allowed to
            // vectorize them.
            debug.log(DebugContext.DETAILED_LEVEL, "loop contains %d deopts and %d safepoints, but we're not allowed to vectorize them", toVectorize.deopts, toVectorize.safepoints);
            return false;
        }
        if (toVectorize.deopts > 0 || toVectorize.safepoints > 0) {
            WriteNode objectWrite = null;
            for (WriteNode write : writes.getKeys()) {
                if (write.value().stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp) {
                    objectWrite = write;
                    break;
                }
            }
            if (objectWrite != null) {
                // For vectorized writes to object arrays, the corresponding write
                // barrier is only executed after the vector loop. This is only legal if
                // we are sure that the loop terminates normally. Hence we cannot
                // vectorize such a loop if it also contains a deopt.
                debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize loop containing %s writing to an object array and deopts", objectWrite);
                return false;
            }
        }
        if (!context.getTarget().arch.supportsUnalignedMemoryAccess() && toVectorize.operations() > 1) {
            // We have more than one operation to vectorize in the loop. We will want to
            // generate a consumer group for them. However, consumer groups cannot in
            // general ensure common alignment of their members. So skip this loop if
            // the target does not allow unaligned memory accesses.
            debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize loop containing %d operations if we're not allowed to form vector consumer groups due to alignment", toVectorize.operations());
            return false;
        }

        // Insertion point for new fixed nodes before all vector consumers.
        graph.addBeforeFixed(loop.loopBegin().forwardEnd(), graph.add(new BeginNode()));
        Graph.Mark beforeVectorization = graph.getMark();

        EconomicMap<Pair<ValueNode, Direction>, VectorNode> vectorizedValues = EconomicMap.create();
        ArrayList<LowerableVectorConsumer> vectorizedOperations = new ArrayList<>();

        GuardVectorizationEffects deoptEffects = GuardVectorizationEffects.nop();
        EconomicSet<IfNode> ifNodesToVectorize = EconomicSet.create();
        VectorLoop.DeoptStrategy deoptStrategy = VectorLoop.DeoptStrategy.forValues(toVectorize);
        if (VectorizeDeopts.getValue(graph.getOptions()) && allowVectorizationOfDeopts && toVectorize.deopts > 0) {
            if (deoptStrategy == null) {
                // Can't vectorize this loop because it contains deopts and *both* maps
                // and folds.
                debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize loop with deopts and both maps and folds");
                return false;
            }

            VectorLoop deoptVectorLoop = new VectorLoop(loop, bodyNodes, null, true, toVectorize.allValues, vectorizedValues, context, canonicalizer);
            deoptEffects = deoptVectorLoop.vectorizeDeopts(deoptStrategy, ifNodesToVectorize);
            if (deoptEffects == null) {
                // The deopt couldn't be vectorized, but the graph hasn't been modified
                // yet. Don't try to vectorize this loop further since the deopts have
                // to precede everything else in the loop.
                debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize all deopts in the loop");
                return false;
            }
        } else if (toVectorize.deopts > 0) {
            return false;
        }

        VectorizationEffects mapEffects = VectorizationEffects.nop();
        if (VectorizeMapShaped.getValue(graph.getOptions()) && toVectorize.maps > 0) {
            VectorLoop mapVectorLoop = new VectorLoop(loop, bodyNodes, writes, VectorizeMapShaped.getValue(graph.getOptions()), toVectorize.allValues, vectorizedValues, context, canonicalizer);
            mapEffects = mapVectorLoop.vectorizeMapShaped();
            if (mapEffects == null) {
                debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize all maps in the loop");
                return false;
            }
        } else if (toVectorize.maps > 0) {
            return false;
        }

        VectorizationEffects foldEffects = VectorizationEffects.nop();
        if (VectorizeFoldShaped.getValue(graph.getOptions()) && toVectorize.folds > 0) {
            VectorLoop foldVectorLoop = new VectorLoop(loop, bodyNodes, null, true, toVectorize.allValues, vectorizedValues, context, canonicalizer);
            foldEffects = foldVectorLoop.vectorizeFoldShaped(ifNodesToVectorize, writes);
            if (foldEffects == null) {
                debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize all folds in the loop");
                return false;
            }
        } else if (toVectorize.folds > 0) {
            return false;
        }

        VectorizationEffects reachabilityFenceEffects = VectorizationEffects.nop();
        if (VectorizeReachabilityFences.getValue(graph.getOptions()) && toVectorize.reachabilityFences > 0) {
            VectorLoop reachabilityFenceVectorLoop = new VectorLoop(loop, bodyNodes, null, true, toVectorize.allValues, vectorizedValues, context, canonicalizer);
            reachabilityFenceEffects = reachabilityFenceVectorLoop.vectorizeReachabilityFences();
            if (reachabilityFenceEffects == null) {
                debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize all reachability fences in the loop");
                return false;
            }
        } else if (toVectorize.reachabilityFences > 0) {
            return false;
        }

        VectorizationEffects safepointEffects = VectorizationEffects.nop();
        if (VectorizeSafepoints.getValue(graph.getOptions()) && allowVectorizationOfDeopts && toVectorize.safepoints > 0) {
            if (deoptStrategy == null) {
                return false;
            }
            VectorLoop safepointVectorLoop = new VectorLoop(loop, bodyNodes, null, false, toVectorize.allValues, vectorizedValues, context, canonicalizer);
            safepointEffects = safepointVectorLoop.vectorizeSafepoint(deoptStrategy);
            if (safepointEffects == null) {
                // Couldn't vectorize a safepoint in the loop.
                return false;
            }
        } else if (toVectorize.safepoints > 0) {
            return false;
        }

        if (graph.getNewNodes(beforeVectorization).filter(FloatingVectorGatherNode.class).count() > 1) {
            /* See note on lowering floating to fixed gathers below. */
            debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize loop with more than one gather operation");
            return false;
        }

        /*
         * Compute the vector length and share it across all consumers we will generate. This can
         * involve placing a fixed division node before the loop begin. Therefore, we want this to
         * happen exactly once, before any other changes to the graph.
         */
        ValueNode vectorLength = VectorLoop.getVectorLength(graph, loop);

        try (DebugCloseable position = loop.loopBegin().withNodeSourcePosition()) {

            boolean keepPostLoop = Options.LoopVectorizationKeepPostLoop.getValue(graph.getOptions());
            final OptimisticLoopAliasGuardNode nonSpeculativeAliasing = (OptimisticLoopAliasGuardNode) loop.loopBegin().getInterIterationAliasingGuard();
            boolean requirePostLoop = nonSpeculativeAliasing != null && !nonSpeculativeAliasing.isAliasing().isContradiction();

            if ((keepPostLoop/* by option */ || requirePostLoop /* by analysis */) && loop.counted().isInverted()) {
                /**
                 * If we keep the post loop and the loop is inverted we are missing its protection
                 * since we enter the post loop after the main loop unconditionally. Every tail
                 * counted loop needs protection, see
                 * See the counted loop information for details.
                 */
                debug.log(DebugContext.DETAILED_LEVEL, "not vectorizing inverted loop because a post loop is needed (by option: %s, by analysis: %s)", keepPostLoop, requirePostLoop);
                return false;
            }

            FixedWithNextNode consumerInsertionPoint = graph.add(new BeginNode());
            FixedWithNextNode startingInsertionPoint = consumerInsertionPoint;
            graph.addBeforeFixed(loop.loopBegin().forwardEnd(), consumerInsertionPoint);

            AliasingBranchInfo aliasingBranchInfo = null;

            if (requirePostLoop) {
                aliasingBranchInfo = new AliasingBranchInfo(graph, loop, nonSpeculativeAliasing.isAliasing(), consumerInsertionPoint);
                keepPostLoop = true;
                consumerInsertionPoint = aliasingBranchInfo.getConsumerInsertionPoint();
            }

            // If we got here, everything in the loop is vectorizable. Construct all the
            // vector operations.
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "before applying vectorization effects on %s", loop.loopBegin());
            consumerInsertionPoint = deoptEffects.apply(consumerInsertionPoint, vectorizedOperations, keepPostLoop, vectorLength);
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after applying vector deopt effects");
            consumerInsertionPoint = mapEffects.apply(consumerInsertionPoint, vectorizedOperations, keepPostLoop, vectorLength);
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after applying vector map effects");
            consumerInsertionPoint = foldEffects.apply(consumerInsertionPoint, vectorizedOperations, keepPostLoop, vectorLength);
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after applying vector fold effects");
            consumerInsertionPoint = reachabilityFenceEffects.apply(consumerInsertionPoint, vectorizedOperations, keepPostLoop, vectorLength);
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after applying vector reachability fence effects");
            consumerInsertionPoint = safepointEffects.apply(consumerInsertionPoint, vectorizedOperations, keepPostLoop, vectorLength);
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after applying vector safepoint effects");
            if (keepPostLoop) {
                deoptEffects.rewireGuardEdgesFromPostLoop(beforeVectorization);
                graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after rewiring guard edges from the post loop to new vector guards");
            }

            /*
             * Lower floating placeholder nodes to fixed nodes. We must be careful about ordering.
             * Gather nodes may depend on reads for their indices, but reads cannot depend on
             * gathers. Reads are independent of each other. Above we exclude multiple gathers, so
             * there are no gather-gather dependencies to worry about. Fixing gathers first ensures
             * that all fixed reads will precede them, so any possible dependencies are respected.
             */
            for (FloatingVectorGatherNode floatingVectorGather : graph.getNewNodes(beforeVectorization).filter(FloatingVectorGatherNode.class)) {
                floatingVectorGather.replaceWithFixedGather(startingInsertionPoint, beforeVectorization);
            }
            for (FloatingVectorReadNode floatingVectorRead : graph.getNewNodes(beforeVectorization).filter(FloatingVectorReadNode.class)) {
                floatingVectorRead.replaceWithFixedRead(startingInsertionPoint, beforeVectorization);
            }
            graph.getDebug().dump(DebugContext.DETAILED_LEVEL, graph, "after fixing floating vector reads and gathers");

            setTrustedBodyIterations(loop, vectorizedOperations);

            if (vectorizedOperations.size() > 1 || keepPostLoop) {
                VectorLoop.createVectorLoop(graph, loop, consumerInsertionPoint, vectorizedOperations, keepPostLoop);
            }
            if (aliasingBranchInfo != null) {
                aliasingBranchInfo.fixUpLoopPhiInits();
            }
        }
        debug.log(DebugContext.VERBOSE_LEVEL, "successfully vectorized this loop with %d vector consumers", vectorizedOperations.size());
        return true;
    }

    public static class VectorizableLoopInfo {
        EconomicMap<WriteNode, InductionVariable> writes;
        ArrayList<FixedNode> bodyNodes;
        ArrayList<IfNode> ifNodesToConditionalize;

        VectorizableLoopInfo(EconomicMap<WriteNode, InductionVariable> writes, ArrayList<FixedNode> bodyNodes, ArrayList<IfNode> ifNodesToConditionalize) {
            this.writes = writes;
            this.bodyNodes = bodyNodes;
            this.ifNodesToConditionalize = ifNodesToConditionalize;
        }

        @Override
        public String toString() {
            return String.format("VectorizableLoopInfo(%d writes, %d bodyNodes, %d ifNodesToConditionalize)", writes.size(), bodyNodes.size(), ifNodesToConditionalize.size());
        }
    }

    private static VectorizableLoopInfo detectVectorizableLoop(Loop loop, CoreProviders providers) {
        return LoopVectorizationAnalysis.detectVectorizableLoop(loop, false, providers);
    }

    /**
     * Describes the extra control flow needed to handle non-speculative aliasing guards. If the
     * optimistic aliasing analysis has determined that accesses in the loop can alias, and we
     * cannot handle this aliasing using speculation and deoptimization, this sets up a code
     * structure like:
     *
     * <pre>
     * if (aliasing) {
     *     // Do nothing here, fall through to original scalar loop.
     * } else {
     *     // Try to use SIMD code.
     *     VectorConsumer1;
     *     ...
     *     VectorConsumerN;
     *     VectorLoop;
     * }
     * // Original loop: Handle all iterations in the aliasing case, only tail iterations in the SIMD case.
     * loop {
     *     ...
     * }
     * </pre>
     *
     * Usage notes: The
     * {@link AliasingBranchInfo#AliasingBranchInfo(StructuredGraph, Loop, LogicNode, FixedWithNextNode)}
     * constructor must be called before creating the vector consumers. It will set up the branching
     * structure. Then, {@link #getConsumerInsertionPoint()} returns the position inside the branch
     * where consumers should be created. Finally, after creating the consumers,
     * {@link #fixUpLoopPhiInits()} must be called to ensure that all phis of the loop have their
     * initial values set correctly.
     */
    private static final class AliasingBranchInfo {

        FixedWithNextNode consumerInsertionPoint;
        MergeNode merge;
        EconomicMap<PhiNode, ValueNode> phiInitValues;

        private AliasingBranchInfo(StructuredGraph graph, Loop loop, LogicNode isAliasing, FixedWithNextNode consumerInsertionPoint) {
            this.consumerInsertionPoint = setUpBranchingStructure(graph, isAliasing, consumerInsertionPoint);
            this.phiInitValues = phiInitValues(loop);
        }

        public FixedWithNextNode getConsumerInsertionPoint() {
            return consumerInsertionPoint;
        }

        /**
         * Fix up the initial values of phis on the post loop. After creating a branching structure
         * like
         *
         * <pre>
         * if (aliasing) {
         *     // do nothing
         * } else {
         *     ...vector consumers...
         * }
         * // merge here
         * loop {
         *     // phis...
         *     ...
         * }
         * </pre>
         *
         * the loop's phis can refer to values coming from the vector consumers. Because these don't
         * dominate the loop, the SSA property is violated. This method fixes these by adding new
         * phis at the merge using the init values saved before the vector consumers were created.
         */
        public void fixUpLoopPhiInits() {
            MapCursor<PhiNode, ValueNode> cursor = phiInitValues.getEntries();
            while (cursor.advance()) {
                PhiNode loopPhi = cursor.getKey();
                ValueNode originalInitValue = cursor.getValue();
                ValueNode vectorizedInitValue = loopPhi.firstValue();
                if (originalInitValue != vectorizedInitValue) {
                    PhiNode mergePhi;
                    if (loopPhi instanceof ValuePhiNode valuePhi) {
                        /*
                         * The original stamp should be OK for the duplicate, but let's be
                         * conservative to avoid surprises with canonicalization.
                         */
                        mergePhi = valuePhi.graph().addWithoutUnique(new ValuePhiNode(valuePhi.stamp(NodeView.DEFAULT).unrestricted(), merge));
                    } else {
                        mergePhi = loopPhi.duplicateOn(merge);
                    }
                    mergePhi.addInput(originalInitValue);
                    mergePhi.addInput(vectorizedInitValue);
                    loopPhi.replaceFirstInput(vectorizedInitValue, mergePhi);
                }
            }
        }

        private FixedWithNextNode setUpBranchingStructure(StructuredGraph graph, LogicNode isAliasing, FixedWithNextNode consumerInsertionPoint) {
            BeginNode aliasingBegin = graph.add(new BeginNode());
            BeginNode nonAliasingBegin = graph.add(new BeginNode());
            IfNode aliasingIf = graph.add(new IfNode(graph.addOrUnique(isAliasing), aliasingBegin, nonAliasingBegin, BranchProbabilityNode.NOT_LIKELY_PROFILE));
            EndNode aliasingEnd = graph.add(new EndNode());
            EndNode nonAliasingEnd = graph.add(new EndNode());
            aliasingBegin.setNext(aliasingEnd);
            nonAliasingBegin.setNext(nonAliasingEnd);
            this.merge = graph.add(new MergeNode());
            merge.addForwardEnd(aliasingEnd);
            merge.addForwardEnd(nonAliasingEnd);
            FixedNode next = consumerInsertionPoint.next();
            consumerInsertionPoint.setNext(null);
            consumerInsertionPoint.setNext(aliasingIf);
            merge.setNext(next);
            return nonAliasingBegin;
        }

        private static EconomicMap<PhiNode, ValueNode> phiInitValues(Loop loop) {
            EconomicMap<PhiNode, ValueNode> initValues = EconomicMap.create();
            for (PhiNode phi : loop.loopBegin().phis()) {
                initValues.put(phi, phi.firstValue());
            }
            return initValues;
        }
    }

    @SuppressWarnings("try")
    private static boolean convertIfNodesToConditionals(Loop loop, VectorizableLoopInfo vectorizableLoop) {
        if (vectorizableLoop.ifNodesToConditionalize.isEmpty()) {
            return true;
        }
        DebugContext debug = loop.loopBegin().getDebug();
        // Check the phis on this loop to see if we would generate a floating-point fold. If we
        // would, don't bother converting if nodes, since the loop would not be simdified
        // anyway. In that case, we would just introduce conditional nodes that
        // ConditionalMoveOptimizationPhase considered unprofitable.
        for (PhiNode phi : loop.loopBegin().phis()) {
            if (phi.stamp(NodeView.DEFAULT) instanceof FloatStamp) {
                debug.log(DebugContext.DETAILED_LEVEL, "don't if convert in loop with floating-point fold on %s", phi);
                return false;
            }
        }

        LoopBeginNode loopBegin = loop.loopBegin();
        for (IfNode ifNode : vectorizableLoop.ifNodesToConditionalize) {
            AbstractEndNode trueEnd = (AbstractEndNode) ifNode.trueSuccessor().next();
            try (DebugCloseable position = ifNode.withNodeSourcePosition()) {
                if (trueEnd.merge() instanceof MergeNode) {
                    // This is a diamond-shaped if statement that we can turn into a conditional.
                    MergeNode merge = (MergeNode) trueEnd.merge();
                    for (PhiNode phi : merge.phis()) {
                        if (phi instanceof ValuePhiNode) {
                            VirtualConditionalNode virtualConditional = VirtualConditionalNode.forPhi((ValuePhiNode) phi, phi.graph());
                            phi.replaceAtUsages(virtualConditional, n -> n != virtualConditional);
                        } else {
                            debug.log(DebugContext.DETAILED_LEVEL, "can't if convert non-value phi %s", phi);
                            return false;
                        }
                    }
                }
            }
        }

        // Notify the loop that its body has changed, and recompute the body.
        loop.invalidateFragments();
        vectorizableLoop.bodyNodes = new ArrayList<>();
        loop.counted().getBody().getBlockNodes().snapshotTo(vectorizableLoop.bodyNodes);
        loopBegin.graph().getDebug().dump(DebugContext.DETAILED_LEVEL, loopBegin.graph(), "after converting if nodes to conditionals in %s", loopBegin);

        return true;
    }

    /**
     * Recognize any conditional nodes representing integer min/max operations inside the loop.
     * Replace them by explicit min/max nodes for the duration of loop vectorization. Any leftover
     * min/max must be reverted to a conditional form if loop vectorization fails.
     *
     * @return the number of min/max nodes recognized
     */
    private static int recognizeIntegerMinMax(StructuredGraph graph, NodeBitMap loopNodes) {
        int recognized = 0;
        for (Node node : loopNodes.snapshot()) {
            if (node instanceof ConditionalNode && ((ConditionalNode) node).stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                ConditionalNode conditional = (ConditionalNode) node;
                ValueNode minMax = MinMaxNode.fromConditional(conditional);
                if (minMax != null) {
                    recognized++;
                    Mark beforeAdd = graph.getMark();
                    minMax = graph.addOrUniqueWithInputs(minMax);
                    conditional.replaceAtUsages(minMax);
                    /*
                     * The conditional was part of the loop, so its new min/max representation must
                     * be part of the loop as well.
                     */
                    for (Node newNode : graph.getNewNodes(beforeAdd)) {
                        if (!(newNode instanceof ConstantNode)) {
                            loopNodes.markAndGrow(newNode);
                        }
                    }
                    /*
                     * Clean up because the rest of loop vectorization (specifically, the code
                     * checking usages of reads) is sensitive to dead code inside the loop.
                     */
                    NodeFlood flood = new NodeFlood(graph);
                    flood.add(conditional);
                    for (Node n : flood) {
                        /*
                         * GR-66889: Don't delete constants, IVs may be hanging on to them even if
                         * they have no explicit usages in the graph (see Loop.calcScaleTo).
                         */
                        if (n.hasNoUsages() && !(n instanceof ConstantNode)) {
                            flood.addAll(n.inputs());
                            n.safeDelete();
                        }
                    }
                }
            }
        }
        return recognized;
    }

    /**
     * Set loop frequency information on the vectorized operations. Uses the vector length (the
     * original loop's number of iterations) if it's constant, otherwise profiled or injected data
     * if available.
     */
    private static void setTrustedBodyIterations(Loop loop, List<LowerableVectorConsumer> consumers) {
        ProfileSource loopFrequencySource = loop.localFrequencySource();
        double trustedLoopFrequency = ProfileSource.isTrusted(loopFrequencySource) ? loop.localLoopFrequency() : -1;
        double trustedBodyIterations = trustedLoopFrequency;
        if (!loop.counted().isInverted() && trustedLoopFrequency >= 1.0) {
            /*
             * The frequency for head-counted loops indicates the number of loop exit checks, i.e.,
             * it is 1 greater than the number of executions of the loop body.
             */
            trustedBodyIterations -= 1.0;
        }
        for (LowerableVectorConsumer consumer : consumers) {
            if (consumer.getLength().isJavaConstant()) {
                // The consumer length (== number of loop iterations) is constant, use it directly.
                consumer.setTrustedBodyIterations(consumer.getLength().asJavaConstant().asLong());
            } else {
                consumer.setTrustedBodyIterations(trustedBodyIterations);
            }
        }
    }

    private static class LoopValues {
        // The roots of the values used by operations we want to vectorize, e.g., a write's value or
        // a deopt's condition.
        ArrayList<ValueNode> rootValues;
        // All value nodes to vectorize, i.e., including all subexpressions of the root values.
        NodeBitMap allValues;
        // The number of deopts to vectorize.
        int deopts;
        // The number of maps to vectorize.
        int maps;
        // The number of folds to vectorize.
        int folds;
        // The number of reachability fences to vectorize.
        int reachabilityFences;
        // The number of safepoints to vectorize (should not exceed 1).
        int safepoints;
        // The number of complex phis that aren't vectorized but affect the deoptimization strategy.
        int complexPhis;

        LoopValues(StructuredGraph graph) {
            this.rootValues = new ArrayList<>();
            this.allValues = new NodeBitMap(graph);
            this.deopts = 0;
            this.maps = 0;
            this.folds = 0;
            this.reachabilityFences = 0;
            this.safepoints = 0;
            this.complexPhis = 0;
        }

        public int operations() {
            return deopts + maps + folds + reachabilityFences + safepoints;
        }

        public boolean isEmpty() {
            // Do not count safepoints. A loop containing only a safepoint is considered empty.
            return deopts == 0 && maps == 0 && folds == 0 && reachabilityFences == 0;
        }

        @Override
        public String toString() {
            return String.format("LoopValues(%d rootValues, %d deopts, %d maps, %d folds, %d reachability fences, %d safepoints; %d complex phis)",
                            rootValues.size(), deopts, maps, folds, reachabilityFences, safepoints, complexPhis);
        }
    }

    private static final class VectorLoop {

        private final Loop loop;
        private final List<FixedNode> bodyNodes;
        private final StructuredGraph graph;
        private final EconomicMap<WriteNode, InductionVariable> vectorizableWrites;
        private final EconomicMap<Pair<ValueNode, Direction>, VectorNode> vectorizedValues;
        private final NodeBitMap valuesToVectorize;
        private final boolean useInputVectors;
        private final CoreProviders context;
        private final CanonicalizerPhase canonicalizer;
        private final VectorArchitecture arch;

        private Node currentNodeToVectorize;

        /**
         * The induction variable defining the direction in which vector elements are consumed. For
         * writes, this is IV of the address offset. For other consumers, it is just the loop
         * counter. All memory accesses (both reads and writes) involved in vectorizing a given
         * computation must have the same direction; this is ensured by checking every IV we
         * encounter with {@link #isCompatibleWithCurrentIv}.
         */
        private InductionVariable currentInductionVariable;

        protected enum DeoptStrategy {
            // When deoptimizing, use an exact value in the frame state for all induction variables.
            // Does *not* work for folds because we cannot access a fold's internal partial state.
            // Does *not* work for complex phis either because we cannot easily compute the phi's
            // value after a given number of iterations.
            DEOPT_PRECISE,
            // When deoptimizing, reset the values in the frame state of all induction variables and
            // folds to their initial values. Does *not* work for maps, since side-effects will
            // already have taken place, but we would pretend that the loop hasn't even been
            // entered.
            DEOPT_TO_INITIAL;

            protected static DeoptStrategy forValues(LoopValues toVectorize) {
                if (toVectorize.folds + toVectorize.complexPhis == 0) {
                    return DEOPT_PRECISE;
                } else if (toVectorize.maps == 0) {
                    return DEOPT_TO_INITIAL;
                } else {
                    assert toVectorize.maps > 0 : toVectorize.maps;
                    assert toVectorize.folds + toVectorize.complexPhis > 0 : toVectorize.folds + " and " + toVectorize.complexPhis;
                    if (toVectorize.deopts > 0 || toVectorize.safepoints > 0) {
                        // Can't vectorize loops with deopts and *both* maps and folds (or other
                        // complex phis).
                        return null;
                    } else {
                        // There is no deoptimizing operation in the loop, so it doesn't matter
                        // which strategy we choose here.
                        return DEOPT_PRECISE;
                    }
                }
            }
        }

        private VectorLoop(Loop loop, List<FixedNode> bodyNodes, EconomicMap<WriteNode, InductionVariable> vectorizableWrites, boolean useInputVectors, NodeBitMap valuesToVectorize,
                        EconomicMap<Pair<ValueNode, Direction>, VectorNode> vectorizedValues, CoreProviders context, CanonicalizerPhase canonicalizer) {
            this.loop = loop;
            this.bodyNodes = bodyNodes;
            this.graph = loop.loopBegin().graph();
            this.vectorizableWrites = vectorizableWrites;
            this.vectorizedValues = vectorizedValues;
            this.valuesToVectorize = valuesToVectorize;
            this.useInputVectors = useInputVectors;
            this.context = context;
            this.canonicalizer = canonicalizer;
            this.arch = ((VectorLoweringProvider) context.getLowerer()).getVectorArchitecture();
        }

        @SuppressWarnings("try")
        public VectorizationEffects vectorizeMapShaped() {
            if (vectorizableWrites.isEmpty()) {
                return VectorizationEffects.nop();
            }

            DebugContext debug = loop.loopBegin().getDebug();
            // Iterate over all writes in the loop and see if we can vectorize them: The value
            // written must be vectorizable, and all possibly aliasing writes inside the loop must
            // be guarded with optimistic memory edges.
            List<FixedNode> loopWrites = CollectionsUtil.filterToList(bodyNodes, (node) -> node instanceof WriteNode);
            for (FixedNode node : loopWrites) {
                WriteNode write = (WriteNode) node;
                try (Indent indent = debug.logAndIndent(DebugContext.DETAILED_LEVEL, "try to vectorize %s", write)) {
                    // Check this write.
                    if (!vectorizableWrites.containsKey(write)) {
                        debug.log(DebugContext.DETAILED_LEVEL, "this write was not considered for vectorization (non-vectorizable IV)");
                        return null;
                    }
                    if (hasOnlySupportedUsages(write)) {
                        this.currentNodeToVectorize = write;
                        this.currentInductionVariable = vectorizableWrites.get(write);
                        VectorNode vector = vectorizeValue(write.value(), null);
                        if (vector == null) {
                            debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize write's value %s", write.value());
                            return null;
                        }
                    } else {
                        return null;
                    }
                }
            }

            return new VectorizationEffects() {
                @Override
                FixedWithNextNode apply(FixedWithNextNode insertionPoint, ArrayList<LowerableVectorConsumer> vectorizedOperations, boolean keepPostLoop, ValueNode vectorLength) {
                    // All writes are found to be vectorizable, and their values are vectorized.
                    // Build the vector writes themselves.
                    FixedWithNextNode nextInsertionPoint = insertionPoint;
                    for (FixedNode node : loopWrites) {
                        WriteNode write = (WriteNode) node;
                        VectorWriteNode vectorWrite = vectorizeWrite(nextInsertionPoint, vectorLength, write, vectorizableWrites.get(write), keepPostLoop);
                        vectorizedOperations.add(vectorWrite);
                        nextInsertionPoint = vectorWrite;
                    }
                    return nextInsertionPoint;
                }
            };
        }

        private VectorWriteNode vectorizeWrite(FixedWithNextNode insertionPoint, ValueNode vectorLength, WriteNode write, InductionVariable iv, boolean keepPostLoop) {
            assert loop.loopBegin().loopEnds().count() == 1 : "trying to vectorize map loop with more than one end";
            this.currentNodeToVectorize = write;
            this.currentInductionVariable = iv;

            MemoryKill lastLocationAccess = write.getLastLocationAccess();
            if (lastLocationAccess instanceof MemoryPhiNode) {
                MemoryPhiNode memoryPhi = (MemoryPhiNode) lastLocationAccess;
                assert memoryPhi.merge() == loop.loopBegin() : memoryPhi + " merge=" + memoryPhi.merge() + " loop " + loop;
                assert memoryPhi.singleBackValueOrThis() instanceof WriteNode : memoryPhi.singleBackValueOrThis();
                lastLocationAccess = (MemoryKill) memoryPhi.valueAt(loop.loopBegin().forwardEnd());
            }
            assert vectorizedValues.containsKey(Pair.create(write.value(), iv.direction())) : "Vect values " + vectorizedValues + " must contain " + write.value() + "->" + iv.direction();
            VectorNode vector = vectorizedValues.get(Pair.create(write.value(), iv.direction()));
            return createWriteVector(insertionPoint, vectorLength, write, iv, vector, lastLocationAccess, keepPostLoop);
        }

        private boolean hasOnlySupportedUsages(WriteNode write) {
            // we can't vectorize the write node if it is actively used within the loop
            for (Node usage : write.usages()) {
                if (!loop.isOutsideLoop(usage) && !(usage instanceof MemoryPhiNode) && !(usage instanceof OptimisticMemoryEdge) && !(usage instanceof WriteNode)) {
                    write.getDebug().log(DebugContext.DETAILED_LEVEL, "unvectorizable aliasing usage %s for write", usage);
                    return false;
                }
                if (usage instanceof MemoryPhiNode) {
                    MemoryPhiNode memoryPhi = (MemoryPhiNode) usage;
                    // If another operation in this loop might read from this write, we must be
                    // careful: We have little control over whether the read would be scheduled
                    // before or after the write, and we cannot even predict at this point whether
                    // the other operation will be vectorized at all. We might overwrite memory
                    // before the use has had a chance to read the old values it wanted. Therefore,
                    // forbid such constructs.
                    if (hasUnsafeReadUsage(memoryPhi, loop)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static boolean hasUnsafeReadUsage(MemoryPhiNode memoryPhi, Loop loop) {
            for (Node usage : memoryPhi.usages()) {
                if (usage instanceof FloatingReadNode || usage instanceof ReadNode) {
                    if (!loop.isOutsideLoop(usage)) {
                        // The write is used by a read without an optimistic memory edge, i.e.,
                        // there is definite aliasing between them. This is unsafe.
                        memoryPhi.getDebug().log(DebugContext.DETAILED_LEVEL, "write has unsafe read usage %s", usage);
                        return true;
                    }
                }
            }
            if (memoryPhi.merge() == loop.loopBegin() && loop.counted().isInverted()) {
                /*
                 * For inverted loops we can't necessarily rely on Loop::isOutsideLoop. A floating
                 * read that has no usages in the loop would appear to be outside the loop because
                 * we don't model memory antidependences. According to the scheduler, it might still
                 * have to live inside the loop and precede the write that we're currently trying to
                 * vectorize. Find all relevant floating read usages and check if their address
                 * depends on a loop phi; if yes, that indicates that the read really belongs to the
                 * loop and can alias the write.
                 */
                NodeFlood outsideUsages = new NodeFlood(loop.loopBegin().graph());
                outsideUsages.addAll(memoryPhi.usages().filter(u -> loop.isOutsideLoop(u) && (u instanceof OptimisticMemoryEdge || u instanceof FloatingReadNode)));
                for (Node usage : outsideUsages) {
                    outsideUsages.addAll(usage.usages().filter(u -> loop.isOutsideLoop(u) && (u instanceof OptimisticMemoryEdge || u instanceof FloatingReadNode)));
                }
                for (FloatingReadNode floatingRead : outsideUsages.getVisited().filter(FloatingReadNode.class)) {
                    NodeFlood addressInputs = new NodeFlood(loop.loopBegin().graph());
                    addressInputs.add(floatingRead.getAddress());
                    for (Node n : addressInputs) {
                        CompilationAlarm.checkProgress(loop.loopBegin().graph());
                        if (n instanceof PhiNode addressPhi && addressPhi.merge() == loop.loopBegin()) {
                            /* This read must be considered to belong to the loop. */
                            memoryPhi.getDebug().log(DebugContext.DETAILED_LEVEL, "write has unsafe read usage %s (inverted loop, only outside usages of read)", floatingRead);
                            return true;
                        }
                        FixedNode fixedPosition = n instanceof FixedNode fixed ? fixed : n instanceof PhiNode phi ? phi.merge() : null;
                        if (fixedPosition != null && loop.isOutsideLoop(fixedPosition)) {
                            /*
                             * We're definitely before the loop, no need to search through this
                             * node's inputs. They cannot be in the loop.
                             */
                            continue;
                        }
                        if (n instanceof PiNode pi) {
                            /* We only care about the value input. */
                            addressInputs.add(pi.getOriginalNode());
                            continue;
                        }
                        addressInputs.addAll(n.inputs());
                    }
                }
            }
            return false;
        }

        @SuppressWarnings("try")
        private VectorWriteNode createWriteVector(FixedWithNextNode insertionPoint, ValueNode vectorLength, WriteNode write, InductionVariable iv, VectorNode vector, MemoryKill lastLocationAccess,
                        boolean keepPostLoop) {
            assert isCompatibleWithCurrentIv(iv) : iv + " must be compatible with current iv";
            OffsetAddressNode address = (OffsetAddressNode) write.getAddress();
            AddressNode startAddress = createStartAddress(iv, address);

            VectorWriteNode vectorWrite;
            try (DebugCloseable position = write.withNodeSourcePosition()) {
                GuardingNode guard = null;
                MemoryKill lla = lastLocationAccess;
                while (!loop.isOutsideLoop(lla.asNode())) {
                    // potential aliasing inside loop
                    if (lla instanceof OptimisticMemoryEdge) {
                        OptimisticMemoryEdge edge = (OptimisticMemoryEdge) lla;
                        guard = MultiGuardNode.addGuard(guard, edge.getGuard());
                        lla = edge.getOptimisticEdge();
                    } else {
                        break;
                    }
                }
                // If the write's last location access still refers to a memory phi on the loop,
                // rewire it: The newly vectorized write lives before the loop (and the loop will be
                // eliminated).
                if (lla instanceof MemoryPhiNode && ((MemoryPhiNode) lla).merge() == loop.loopBegin()) {
                    lla = (MemoryKill) ((MemoryPhiNode) lla).valueAt(loop.loopBegin().forwardEnd());
                }

                vectorWrite = graph.add(
                                new VectorWriteNode(startAddress, write.getLocationIdentity(), vector.asNode(), vectorLength, (int) iv.constantStride(), false, guard, write.getBarrierType()));
                vectorWrite.setLastLocationAccess(lla);

                graph.addAfterFixed(insertionPoint, vectorWrite);

                if (keepPostLoop) {
                    /*
                     * Don't remove the original write. In fact, do nothing. Memory phis are fixed
                     * up below.
                     */
                } else {
                    write.replaceAtUsages(vectorWrite);
                    graph.removeFixed(write);
                }

                for (PhiNode phi : loop.loopBegin().phis()) {
                    if (phi instanceof MemoryPhiNode && ((MemoryPhiNode) phi).getLocationIdentity().equals(write.getLocationIdentity())) {
                        int forwardIndex = phi.merge().phiPredecessorIndex(loop.loopBegin().forwardEnd());
                        phi.setValueAt(forwardIndex, vectorWrite);
                    }
                }
            }
            return vectorWrite;
        }

        @SuppressWarnings("try")
        private static void createVectorLoop(StructuredGraph graph, Loop loop, FixedWithNextNode insertionPoint, ArrayList<LowerableVectorConsumer> vectorConsumers, boolean keepPostLoop) {
            try (DebugCloseable position = loop.loopBegin().withNodeSourcePosition()) {
                InductionVariable counterIv = loop.counted().getLimitCheckedIV();
                ValueNode loopCounter = counterIv.valueNode();

                ValueNode commonVectorLength = vectorConsumers.get(0).getLength();
                for (LowerableVectorConsumer consumer : vectorConsumers) {
                    GraalError.guarantee(consumer.getLength() == commonVectorLength, "inconsistent lengths within vector loop");
                }

                IntegerStamp loopCounterStamp = (IntegerStamp) loopCounter.stamp(NodeView.DEFAULT);
                // The vector loop processes 0 to vectorLength elements, reflect this in its stamp.
                IntegerStamp vectorLengthStamp = (IntegerStamp) commonVectorLength.stamp(NodeView.DEFAULT);
                GraalError.guarantee(loopCounterStamp.isCompatible(vectorLengthStamp), "loop counter stamp does not match vector length");
                /*
                 * The vectorLengthStamp may be unrestricted in case it is an overflow guard. We
                 * only care about the non-negative values it can have.
                 */
                IntegerStamp vectorLoopStamp = IntegerStamp.create(vectorLengthStamp.getBits(), 0, vectorLengthStamp.upperBound());
                VectorLoopNode vectorLoop = graph.add(new VectorLoopNode(commonVectorLength, counterIv.direction(), vectorConsumers, vectorLoopStamp, keepPostLoop));
                graph.addAfterFixed(insertionPoint, vectorLoop);

                if (keepPostLoop) {
                    loop.loopBegin().setPostLoop();

                    /*
                     * Fixup the loop counter and other IVs. The VectorLoop's value is the number of
                     * iterations it has processed. Therefore step by this amount.
                     */
                    LoopUtility.stepLoopIVs(graph, loop, vectorLoop);

                    fixLoopInvariantPhis(graph, loop, vectorLoop);
                }

                VectorLoopMarkerNode vectorLoopMarker = graph.addWithoutUnique(new VectorLoopMarkerNode());
                for (LowerableVectorConsumer consumer : vectorConsumers) {
                    consumer.setVectorLoopMarker(vectorLoopMarker);
                }
            }
        }

        /**
         * Adjust all non-recursive loop phis on the scalar post-loop. These are phis of the form
         * {@code phi(<loop not entered>, <loop entered>)}. The "loop not entered" case must take
         * into account whether the vector loop was entered.
         */
        private static void fixLoopInvariantPhis(StructuredGraph graph, Loop loop, VectorLoopNode vectorLoop) {
            for (ValuePhiNode phi : loop.loopBegin().valuePhis()) {
                GraalError.guarantee(phi.valueCount() == 2, "loop must have one end, phi must have two values");
                if (loop.isOutsideLoop(phi.valueAt(1))) {
                    ValueNode notEntered = phi.valueAt(0);
                    ValueNode entered = phi.valueAt(1);
                    ValueNode zero = ConstantNode.forIntegerStamp(vectorLoop.stamp(NodeView.DEFAULT), 0, graph);
                    LogicNode vectorLoopEntered = IntegerLessThanNode.create(zero, vectorLoop, NodeView.DEFAULT);
                    ValueNode vectorLoopPhiValue = ConditionalNode.create(vectorLoopEntered, entered, notEntered, NodeView.DEFAULT);
                    phi.setValueAt(0, graph.addOrUniqueWithInputs(vectorLoopPhiValue));
                }
            }
        }

        private AddressNode createStartAddress(InductionVariable iv, OffsetAddressNode address) {
            ValueNode base;
            ValueNode offset;
            if (address.getOffset().equals(iv.valueNode())) {
                // address is in form [base, iv]
                assert loop.isOutsideLoop(address.getBase()) : address.getBase() + " must be outside of " + loop + " inside=" + loop.inside();
                base = address.getBase();
                offset = iv.initNode();
            } else {
                // address is in form [iv, offset]
                assert address.getBase().equals(iv.valueNode()) : address.getBase() + "!=" + iv.valueNode();
                base = iv.initNode();
                offset = address.getOffset();
            }

            if (iv.constantStride() < 0) {
                // we are accessing the array from back to front -> adjust the initial index by
                // stride as the stride will be subtracted before doing the access
                offset = BinaryArithmeticNode.add(graph, offset, graph.unique(ConstantNode.forIntegerStamp(offset.stamp(NodeView.DEFAULT), -iv.constantStride())), NodeView.DEFAULT);
            }
            return graph.unique(new OffsetAddressNode(base, offset));
        }

        // The loop only contains nodes that we know we can vectorize.
        private boolean loopContainsOnlyVectorizableNodes(EconomicSet<IfNode> ifNodesToVectorize, EconomicMap<WriteNode, InductionVariable> writes) {
            for (FixedNode node : bodyNodes) {
                if (node instanceof LoopEndNode) {
                    return true;
                } else if (VectorizeMapShaped.getValue(node.graph().getOptions()) && node instanceof WriteNode && writes.containsKey((WriteNode) node)) {
                    // A write that was vectorized; it is no longer part of the loop body.
                    continue;
                } else if (VectorizeDeopts.getValue(node.graph().getOptions()) && node instanceof IfNode && ifNodesToVectorize.contains((IfNode) node)) {
                    // A deopt that was vectorized; it is no longer part of the loop body.
                    continue;
                } else if (VectorizeReachabilityFences.getValue(node.graph().getOptions()) && node instanceof ReachabilityFenceNode) {
                    continue;
                } else if (VectorizeSafepoints.getValue(node.graph().getOptions()) && node instanceof SafepointNode) {
                    continue;
                } else if (!(node instanceof BeginNode || node instanceof ValueAnchorNode || node instanceof ReadNode)) {
                    loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "non-vectorizable node while trying to vectorize folds: %s", node);
                    return false;
                }
            }
            loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "did not find loop end while trying to vectorize folds");
            return false;
        }

        @SuppressWarnings("try")
        public VectorizationEffects vectorizeFoldShaped(EconomicSet<IfNode> ifNodesToVectorize, EconomicMap<WriteNode, InductionVariable> writes) {
            if (loopContainsOnlyVectorizableNodes(ifNodesToVectorize, writes)) {
                assert loop.loopBegin().loopEnds().count() == 1 : "trying to vectorize fold loop with more than one end";

                AbstractEndNode beforeLoop = loop.loopBegin().forwardEnd();
                EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
                EconomicMap<PhiNode, Function<ValueNode, FoldVectorNode>> foldMap = EconomicMap.create();

                DebugContext debug = loop.loopBegin().getDebug();
                List<PhiNode> loopPhis = loop.loopBegin().phis().snapshot();
                for (PhiNode phi : loopPhis) {
                    if (ivs.get(phi) != null) {
                        // don't try to vectorize induction variables
                        // they will be removed by the RemoveEmptyLoopsPhase anyway
                        continue;
                    }

                    ValueNode value = phi.singleBackValueOrThis();
                    while (value instanceof PiNode pi) {
                        /*
                         * PiNodes don't satisfy
                         * LoopVectorizationAnalysis#isVectorizableAsInnerNode; thus, avoid them as
                         * subgraph root as well
                         */
                        value = pi.getOriginalNode();
                    }
                    if (value == phi) {
                        debug.log(DebugContext.DETAILED_LEVEL, "phi %s does not have a proper single back value: %s", phi, value);
                        return null;
                    }
                    if (phi instanceof ValuePhiNode) {
                        try (Indent indent = debug.logAndIndent(DebugContext.DETAILED_LEVEL, "try to vectorize %s, singleBackValueOrThis: %s", phi, value)) {
                            if (LoopVectorizationAnalysis.isVectorizableComputationRoot(phi, loop, arch)) {
                                /*
                                 * Usages of the phi outside the loop will be replaced by the fold
                                 * we will construct. Usages of the phi inside the loop will
                                 * disappear when we remove the loop. We cannot deal with usages
                                 * inside the loop which have outside usages themselves; this is
                                 * excluded by the loop vectorization analysis.
                                 */
                                if (hasOutsideUsages(phi, loop)) {
                                    if (RemoveEmptyLoopsPhase.isOptimizablePhi(phi, loop)) {
                                        // no need to vectorize
                                        continue;
                                    }
                                    this.currentNodeToVectorize = phi;
                                    this.currentInductionVariable = getLoopIV();
                                    ArrayList<ValueNode> vectorInputs = new ArrayList<>();
                                    ArrayList<ValueNode> scalarInputs = new ArrayList<>();
                                    try (DebugCloseable position = value.withNodeSourcePosition()) {
                                        StructuredGraph innerGraph = createInnerGraph(value, vectorInputs, scalarInputs, phi);
                                        if (innerGraph != null) {
                                            Function<ValueNode, FoldVectorNode> foldVector = vectorLength -> new FoldVectorNode(innerGraph, phi.valueAt(beforeLoop), vectorLength,
                                                            currentInductionVariable.direction(), vectorInputs, scalarInputs);
                                            // Record this fold. Don't add it to the graph yet
                                            // because there might still be non-vectorizable phis in
                                            // this loop.
                                            debug.log(DebugContext.DETAILED_LEVEL, "success vectorizing %s with value %s", phi, value);
                                            foldMap.put(phi, foldVector);
                                        } else {
                                            // Cannot vectorize this phi, so don't vectorize any
                                            // folds in this loop.
                                            debug.log(DebugContext.DETAILED_LEVEL, "failed to create inner graph for fold %s", phi);
                                            return null;
                                        }
                                    }
                                }
                            } else {
                                debug.log(DebugContext.DETAILED_LEVEL, "non-vectorizable value %s for potential fold phi %s", value, phi);
                                return null;
                            }
                        }
                    }
                }

                return new VectorizationEffects() {
                    @Override
                    FixedWithNextNode apply(FixedWithNextNode insertionPoint, ArrayList<LowerableVectorConsumer> vectorizedOperations, boolean keepPostLoop, ValueNode vectorLength) {
                        // If we got here, all relevant phis in the loop are indeed vectorizable.
                        // Add all their folds to the graph and replace usages.
                        FixedWithNextNode nextInsertionPoint = insertionPoint;
                        for (PhiNode phi : loopPhis) {
                            if (foldMap.containsKey(phi)) {
                                FoldVectorNode foldVector = foldMap.get(phi).apply(vectorLength);

                                vectorizedOperations.add(foldVector);
                                graph.addAfterFixed(nextInsertionPoint, graph.add(foldVector));
                                nextInsertionPoint = foldVector;

                                if (keepPostLoop) {
                                    /*
                                     * Don't remove the original phi, just let its computation in
                                     * the post loop start from the fold's result.
                                     */
                                    phi.setValueAt(beforeLoop, foldVector);
                                } else {
                                    phi.replaceAtUsages(foldVector);
                                }
                            }
                        }
                        return nextInsertionPoint;
                    }
                };
            }
            return null;
        }

        private static boolean hasOutsideUsages(ValueNode node, Loop loop) {
            for (Node usage : node.usages()) {
                if (loop.isOutsideLoop(usage)) {
                    return true;
                }
            }
            return false;
        }

        @SuppressWarnings("try")
        public VectorizationEffects vectorizeReachabilityFences() {
            ArrayList<Function<ValueNode, VectorReachabilityFenceNode>> vectorizedReachabilityFences = new ArrayList<>();
            ArrayList<ReachabilityFenceNode> originalFences = new ArrayList<>();

            DebugContext debug = loop.loopBegin().getDebug();
            for (FixedNode node : bodyNodes) {
                if (!(node instanceof ReachabilityFenceNode)) {
                    continue;
                }
                ReachabilityFenceNode fence = (ReachabilityFenceNode) node;
                originalFences.add(fence);

                try (Indent indent = debug.logAndIndent(DebugContext.DETAILED_LEVEL, "try to vectorize reachability fence %s", fence)) {
                    this.currentNodeToVectorize = fence;
                    this.currentInductionVariable = getLoopIV();
                    ValueNode[] vectors = new ValueNode[fence.inputs().count()];
                    int i = 0;
                    for (Node object : fence.inputs()) {
                        VectorNode vector = vectorizeValue((ValueNode) object, null);
                        if (vector == null) {
                            debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize fence's object %s", object);
                            return null;
                        }
                        vectors[i++] = vector.asNode();
                    }

                    try (DebugCloseable position = fence.withNodeSourcePosition()) {
                        Function<ValueNode, VectorReachabilityFenceNode> vectorFence = vectorLength -> new VectorReachabilityFenceNode(vectorLength, currentInductionVariable.direction(), vectors);
                        vectorizedReachabilityFences.add(vectorFence);
                    }
                }
            }

            return new VectorizationEffects() {
                @Override
                FixedWithNextNode apply(FixedWithNextNode insertionPoint, ArrayList<LowerableVectorConsumer> vectorizedOperations, boolean keepPostLoop, ValueNode vectorLength) {
                    FixedWithNextNode nextInsertionPoint = insertionPoint;
                    for (Function<ValueNode, VectorReachabilityFenceNode> vectorFenceFactory : vectorizedReachabilityFences) {
                        VectorReachabilityFenceNode vectorFence = vectorFenceFactory.apply(vectorLength);
                        vectorizedOperations.add(vectorFence);
                        graph.addAfterFixed(nextInsertionPoint, graph.add(vectorFence));
                        nextInsertionPoint = vectorFence;
                    }
                    if (keepPostLoop) {
                        /*
                         * Nothing to do, do not eliminate the reachability fences from the post
                         * loop.
                         */
                    } else {
                        for (ReachabilityFenceNode fence : originalFences) {
                            graph.removeFixed(fence);
                        }
                    }
                    return nextInsertionPoint;
                }
            };
        }

        private static class VectorizedGuard {
            Function<ValueNode, VectorGuardNode> vectorGuard;
            IfNode ifNode;
            DeoptData deoptData;

            VectorizedGuard(Function<ValueNode, VectorGuardNode> vectorGuard, IfNode ifNode, DeoptData deoptData) {
                this.vectorGuard = vectorGuard;
                this.ifNode = ifNode;
                this.deoptData = deoptData;
            }

            AbstractBeginNode nonDeoptimizingBranch() {
                return deoptData.deoptBranch() == DeoptBranch.TRUE_DEOPT_BRANCH ? ifNode.falseSuccessor() : ifNode.trueSuccessor();
            }

        }

        @SuppressWarnings("try")
        public GuardVectorizationEffects vectorizeDeopts(DeoptStrategy deoptStrategy, EconomicSet<IfNode> ifNodesToVectorize) {
            ArrayList<VectorizedGuard> vectorizedGuards = new ArrayList<>();

            DebugContext debug = loop.loopBegin().getDebug();
            for (FixedNode node : bodyNodes) {
                if (!(node instanceof IfNode)) {
                    continue;
                }
                IfNode ifNode = (IfNode) node;
                DeoptData deoptData = LoopVectorizationAnalysis.getDeoptExit(ifNode);

                try (Indent indent = debug.logAndIndent(DebugContext.DETAILED_LEVEL, "try to vectorize deopt %s, condition %s", ifNode, ifNode.condition())) {
                    if (deoptData == null) {
                        /*
                         * When running after GuardRangeGrouping, the if may exit the loop with a
                         * DynamicDeoptimizeNode. In this case we get null here because we can only
                         * vectorize static DeoptimizeNodes.
                         */
                        debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize deopt at %s, found no deopt exit", ifNode);
                        return null;
                    }
                    this.currentNodeToVectorize = ifNode;
                    this.currentInductionVariable = getLoopIV();
                    Stamp condStamp = ifNode.condition().stamp(NodeView.DEFAULT);
                    assert condStamp instanceof VoidStamp : ifNode + " cond=" + ifNode.condition() + " " + condStamp;
                    VectorLogicNode conditionVector = (VectorLogicNode) vectorizeValue(ifNode.condition(), null);
                    if (conditionVector == null) {
                        debug.log(DebugContext.DETAILED_LEVEL, "failed to vectorize deopt's condition %s", ifNode.condition());
                        return null;
                    }

                    try (DebugCloseable position = ifNode.withNodeSourcePosition()) {
                        ArrayList<ArrayList<Position>> vectorPositions = new ArrayList<>();
                        ArrayList<VectorNode> stateVectors = new ArrayList<>();
                        DeoptimizeNode resetState = adjustDeoptFrameState(deoptData.deopt(), vectorPositions, stateVectors, deoptStrategy);
                        if (resetState == null) {
                            debug.log(DebugContext.DETAILED_LEVEL, "failed to construct a legal vectorized deopt state");
                            return null;
                        }
                        double deoptProbability = (deoptData.deoptBranch() == DeoptBranch.TRUE_DEOPT_BRANCH ? ifNode.getTrueSuccessorProbability() : 1.0 - ifNode.getTrueSuccessorProbability());
                        Function<ValueNode, VectorGuardNode> vectorGuard = vectorLength -> new VectorGuardNode(conditionVector, vectorLength, currentInductionVariable.direction(),
                                        deoptData.deoptBranch(), deoptProbability, resetState, vectorPositions, stateVectors);
                        vectorizedGuards.add(new VectorizedGuard(vectorGuard, ifNode, deoptData));
                        ifNodesToVectorize.add(ifNode);
                        debug.log(DebugContext.DETAILED_LEVEL, "success vectorizing deopt %s with condition %s", ifNode, ifNode.condition());
                    }
                }
            }

            return new GuardVectorizationEffects() {
                EconomicMap<IfNode, VectorGuardNode> ifToVectorGuardMapping = null;

                @Override
                FixedWithNextNode apply(FixedWithNextNode insertionPoint, ArrayList<LowerableVectorConsumer> vectorizedOperations, boolean keepPostLoop, ValueNode vectorLength) {
                    // If we got here, all the deopts in the loop are vectorizable. Perform the
                    // actual modification of the graph.
                    if (keepPostLoop && !vectorizedGuards.isEmpty()) {
                        ifToVectorGuardMapping = EconomicMap.create();
                    }
                    FixedWithNextNode nextInsertionPoint = insertionPoint;
                    for (VectorizedGuard vectorized : vectorizedGuards) {
                        VectorGuardNode vectorGuard = graph.add(vectorized.vectorGuard.apply(vectorLength));
                        if (ifToVectorGuardMapping != null) {
                            ifToVectorGuardMapping.put(vectorized.ifNode, vectorGuard);
                        }
                        vectorizedOperations.add(vectorGuard);
                        graph.addAfterFixed(nextInsertionPoint, vectorGuard);
                        nextInsertionPoint = vectorGuard;
                        if (keepPostLoop) {
                            // Nothing to do, do not eliminate the guard from the post loop.
                        } else {
                            /*
                             * This deopt becomes the new guard for everything guarded by the if
                             * node's non-deoptimizing branch.
                             */
                            AbstractBeginNode nonDeoptimizingBranch = vectorized.nonDeoptimizingBranch();
                            nonDeoptimizingBranch.replaceAtUsages(vectorGuard, InputType.Guard);
                            graph.removeSplitPropagate(vectorized.ifNode, nonDeoptimizingBranch);
                        }
                    }
                    return nextInsertionPoint;
                }

                @Override
                void rewireGuardEdgesFromPostLoop(Mark beforeVectorization) {
                    for (VectorizedGuard vectorized : vectorizedGuards) {
                        IfNode ifNode = vectorized.ifNode;
                        GraalError.guarantee(ifNode.isAlive(), "must only be called when post loops are kept, %s should still be alive in the post loop", ifNode);
                        VectorGuardNode vectorGuard = ifToVectorGuardMapping.get(ifNode);
                        GraalError.guarantee(vectorGuard != null, "must have a vectorized guard for %s", ifNode);
                        vectorized.nonDeoptimizingBranch().replaceAtUsages(vectorGuard, usage -> graph.isNew(beforeVectorization, usage));
                    }
                }
            };
        }

        private DeoptimizeNode adjustDeoptFrameState(DeoptimizeNode deopt, ArrayList<ArrayList<Position>> vectorPositions, ArrayList<VectorNode> stateVectors, DeoptStrategy deoptStrategy) {
            boolean atLoopExit = loop.isOutsideLoop(deopt);
            assert atLoopExit : "deopts should be recognized as loop exits";
            FrameState newState = adjustVectorFrameState(deopt.stateBefore(), vectorPositions, stateVectors, deoptStrategy, atLoopExit);
            if (newState == null) {
                return null;
            }
            return new DeoptimizeNode(deopt.getAction(), deopt.getReason(), deopt.getDebugId(), deopt.getSpeculation(), newState);
        }

        /**
         * Copies the given state and its nested outer states, replacing vectorizable inputs by
         * their vectorized versions. The input positions and vectorized values are recorded in
         * {@code vectorPositions} and {@code stateVectors}. The {@code vectorPositions} contain a
         * separate inner list for each nested state, while the {@code stateVectors} are a flat list
         * containing vectorized values from all nested states. If a value in some state cannot be
         * vectorized, this method returns {@code null}.
         */
        private FrameState adjustVectorFrameState(FrameState newState, ArrayList<ArrayList<Position>> vectorPositions, ArrayList<VectorNode> stateVectors, DeoptStrategy deoptStrategy,
                        boolean atLoopExit) {
            FrameState current = newState;
            FrameState firstVectorized = null;
            FrameState lastVectorized = null;
            while (current != null) {
                FrameState duplicate = current.duplicate();
                ArrayList<Position> currentPositions = new ArrayList<>();
                FrameState vectorized = adjustOneVectorFrameState(duplicate, currentPositions, stateVectors, deoptStrategy, atLoopExit);
                if (vectorized == null) {
                    return null;
                }
                vectorPositions.add(currentPositions);
                if (firstVectorized == null) {
                    firstVectorized = vectorized;
                }
                if (lastVectorized != null) {
                    lastVectorized.setOuterFrameState(vectorized);
                }
                lastVectorized = vectorized;
                current = current.outerFrameState();
            }
            return firstVectorized;
        }

        /**
         * Vectorize values in one frame state, without taking its nested outer state into account.
         * Must only be called from {@link #adjustVectorFrameState}. Must be called on a copy of the
         * original state, as this is a destructive operation.
         */
        private FrameState adjustOneVectorFrameState(FrameState newState, ArrayList<Position> vectorPositions, ArrayList<VectorNode> stateVectors, DeoptStrategy deoptStrategy, boolean atLoopExit) {
            // When we deoptimize, we want to get back to a legal state that captures all the
            // side-effects which may have taken place. For map loops we use the precise strategy,
            // which tracks the loop counter and other induction variables. For fold loops, we reset
            // to a state before the loop. Loops with deopts and both maps and folds are not
            // allowed.
            DebugContext debug = loop.loopBegin().getDebug();
            for (Position position : newState.inputPositions()) {
                Node input = position.get(newState);
                if (input instanceof PhiNode && ((PhiNode) input).merge() == loop.loopBegin()) {
                    position.set(newState, ((PhiNode) input).firstValue());
                } else if (input instanceof VirtualObjectNode) {
                    debug.log(DebugContext.DETAILED_LEVEL, "can't construct vector deopt frame state with virtual object %s", input);
                    return null;
                }

                if (deoptStrategy == DeoptStrategy.DEOPT_PRECISE) {
                    SequenceVectorNode vectorized = (input instanceof ValueNode ? vectorizeInductionVariable((ValueNode) input, loop.counted().getDirection()) : null);
                    if (vectorized != null) {
                        // We vectorized this induction variable. Save the vector node in the state
                        // and remember that this is a state vector from which we will later want to
                        // extract the first element.
                        position.set(newState, vectorized.asNode());
                        vectorPositions.add(position);
                        stateVectors.add(vectorized);
                    } else if (input != null && loop.getInductionVariables().containsKey(input)) {
                        debug.log(DebugContext.DETAILED_LEVEL, "can't construct precise vector deopt frame state with non-IV input %s", input);
                        return null;
                    } else if (input == newState.outerFrameState()) {
                        /* Nested frame states must be handled by the caller. */
                    } else {
                        if (input != null && !isLoopInvariant(input, atLoopExit)) {
                            debug.log(DebugContext.DETAILED_LEVEL, "can't construct precise vector deopt frame state with loop variant input %s", input);
                            return null;
                        }
                    }
                } else {
                    assert deoptStrategy == DeoptStrategy.DEOPT_TO_INITIAL : deoptStrategy;
                    if (input instanceof PhiNode && ((PhiNode) input).merge() == loop.loopBegin()) {
                        // Simply reset this phi's value in the state to the value at the loop
                        // entry.
                        assert !(input instanceof MemoryPhiNode) : "can't use DEOPT_TO_INITIAL with loops containing maps";
                        position.set(newState, ((PhiNode) input).valueAt(loop.loopBegin().forwardEnd()));
                    } else if (input == newState.outerFrameState()) {
                        /* Nested frame states must be handled by the caller. */
                    } else if (input != null) {
                        InductionVariable iv = loop.getInductionVariables().get(input);
                        if (iv != null) {
                            /*
                             * We are dealing with a regular induction variable - use the entry
                             * value of the iv when we deopt back to the start.
                             */
                            position.set(newState, iv.entryTripValue());
                        } else if (!isLoopInvariant(input, atLoopExit)) {
                            /*
                             * We cannot easily reset any other computations inside the loop to
                             * their loop entry values.
                             */
                            debug.log(DebugContext.DETAILED_LEVEL, "can't deopt to initial value of %s", input);
                            return null;
                        }
                    }
                }
            }
            return newState;
        }

        /**
         * Determines whether the given node is loop invariant, i.e., whether it can be evaluated
         * <b>before</b> the current loop. This is not the same as being outside the loop, since we
         * might be looking at a value at a deopt exiting the loop: In this case, "outside" means
         * "post-dominating". {@code atLoopExit} is true iff we are at such a loop exit.
         */
        private boolean isLoopInvariant(Node value, boolean atLoopExit) {
            if (value == null) {
                return true;
            }

            boolean outsideLoop = loop.isOutsideLoop(value);
            if (!atLoopExit && outsideLoop) {
                return true;
            }
            if (atLoopExit && outsideLoop) {
                // We are at a loop exit (i.e., a deopt as opposed to a safepoint) and looking at
                // values in its frame state. This value is outside the loop, but we don't know if
                // that's because it's loop invariant or only needed by the frame state after
                // exiting the loop. We need to check if the value depends on anything in the loop.
                return !dependsOnAnyLoopNode(value);
            }
            if (!atLoopExit && !outsideLoop && value instanceof FrameState) {
                // We are at a safepoint frame state, looking at its outer frame state input. For
                // the purposes of building a vector frame state we can consider this loop invariant
                // if all of its value inputs (including those of its outer frame states) are
                // outside the loop. Since we're not at a loop exit, "outside the loop" here means
                // "loop invariant".
                FrameState state = (FrameState) value;
                while (state != null) {
                    for (ValueNode stateValue : state.values()) {
                        if (stateValue != null && !loop.isOutsideLoop(stateValue)) {
                            return false;
                        }
                    }
                    state = state.outerFrameState();
                }
                return true;
            }

            assert !outsideLoop : value;
            return false;
        }

        private boolean dependsOnAnyLoopNode(Node initialNode) {
            if (!loop.isOutsideLoop(initialNode)) {
                return true;
            }

            ScheduleResult schedule = ((StructuredGraph) initialNode.graph()).getLastSchedule();
            HIRBlock loopBeginBlock = (schedule != null ? schedule.getNodeToBlockMap().get(loop.loopBegin()) : null);

            NodeFlood worklist = new NodeFlood(initialNode.graph());
            worklist.add(initialNode);
            for (Node node : worklist) {
                for (Node input : node.inputs()) {
                    if (input == null) {
                        continue;
                    } else if (!loop.isOutsideLoop(input)) {
                        return true;
                    } else {
                        if (schedule != null && input instanceof FixedNode && !schedule.getNodeToBlockMap().isNew(input)) {
                            HIRBlock fixedNodeBlock = schedule.getNodeToBlockMap().get(input);
                            if (fixedNodeBlock.dominates(loopBeginBlock)) {
                                // Nothing above this node can depend on a loop node.
                                continue;
                            }
                        }
                        worklist.add(input);
                    }
                }
            }
            return false;
        }

        @SuppressWarnings("try")
        public VectorizationEffects vectorizeSafepoint(DeoptStrategy deoptStrategy) {
            SafepointNode foundSafepoint = null;
            int safepoints = 0;
            for (FixedNode node : bodyNodes) {
                if (node instanceof SafepointNode) {
                    foundSafepoint = (SafepointNode) node;
                    safepoints++;
                }
            }
            // We could only have more than one safepoint if the loop contained some complicated
            // control flow. We would not be able to generate a high level vector representation of
            // such loops since we represent loops by a linear sequence of vector consumers.
            assert safepoints <= 1 : "loops with more than one safepoint not supported";
            SafepointNode safepoint = foundSafepoint;
            if (safepoint == null) {
                return VectorizationEffects.nop();
            }
            if (deoptStrategy == null) {
                // Cannot vectorize the loop containing this safepoint because we can't safely
                // deopt.
                return null;
            }

            ArrayList<ArrayList<Position>> vectorPositions = new ArrayList<>();
            ArrayList<VectorNode> stateVectors = new ArrayList<>();
            boolean atLoopExit = loop.isOutsideLoop(safepoint);
            assert !atLoopExit : "safepoints must not be considered loop exits";
            FrameState vectorState = adjustVectorFrameState(safepoint.stateBefore(), vectorPositions, stateVectors, deoptStrategy, atLoopExit);
            if (vectorState == null) {
                return null;
            }
            safepoint.getDebug().log(DebugContext.DETAILED_LEVEL, "success vectorizing %s", safepoint);

            return new VectorizationEffects() {
                @Override
                FixedWithNextNode apply(FixedWithNextNode insertionPoint, ArrayList<LowerableVectorConsumer> vectorizedOperations, boolean keepPostLoop, ValueNode vectorLength) {
                    VectorSafepointNode vectorSafepoint = new VectorSafepointNode(vectorLength, getLoopIV().direction(), vectorState, vectorPositions, stateVectors);
                    vectorizedOperations.add(vectorSafepoint);
                    graph.addAfterFixed(insertionPoint, graph.add(vectorSafepoint));
                    if (keepPostLoop) {
                        // Nothing to do, do not eliminate the safepoint from the post loop.
                    } else {
                        graph.removeFixed(safepoint);
                    }
                    return vectorSafepoint;
                }
            };
        }

        private static ValueNode getVectorLength(StructuredGraph graph, Loop loop) {
            CountedLoopInfo counted = loop.counted();
            ValueNode length = counted.maxTripCountNode(false);
            if (counted.counterNeverOverflows()) {
                return length;
            }
            return graph.unique(new GuardedValueNode(length, counted.getOverFlowGuard()));
        }

        private VectorNode vectorizeValue(ValueNode v, NodeBitMap vectorizedNodes) {
            ValueNode value = v;
            if (v instanceof VirtualConditionalNode) {
                value = ((VirtualConditionalNode) v).conditional();
            }
            /*
             * The consumer direction matters for the vectorization of sequences. It must always
             * match the direction of memory accesses, therefore every consumer must have a defined
             * direction.
             */
            final Direction consumerDirection = currentInductionVariable.direction();
            Pair<ValueNode, Direction> cacheKey = Pair.create(value, consumerDirection);
            if (vectorizedValues.containsKey(cacheKey)) {
                return vectorizedValues.get(cacheKey);
            }
            Stamp valueStamp = value.stamp(NodeView.DEFAULT);
            VectorNode result = null;
            if (loop.isOutsideLoop(value)) {
                if (value instanceof LogicNode) {
                    if (value instanceof IntegerExactOverflowNode) {
                        /*
                         * Exact overflow nodes only allow a restricted set of node types as usages.
                         */
                        result = null;
                    } else {
                        result = graph.unique(new InvariantVectorLogicNode((LogicNode) value));
                    }
                } else {
                    result = graph.unique(new FillVectorNode(value));
                }
            } else if (value instanceof ConditionalNode && loop.isOutsideLoop(((ConditionalNode) value).condition())) {
                // Don't allow conditionals with loop-invariant conditions.
                result = null;
            } else {
                // inside loop
                VectorNode iv = vectorizeInductionVariable(value, consumerDirection);
                if (iv != null) {
                    result = iv;
                } else if (!(value instanceof LogicNode) && !arch.isVectorizable(valueStamp)) {
                    value.getDebug().log(DebugContext.DETAILED_LEVEL, "value %s has non-vectorizable stamp %s", value, valueStamp);
                    result = null;
                } else if (useInputVectors) {
                    if (value instanceof ArithmeticOperation) {
                        result = vectorizeArithmetic(value);
                    } else if (value instanceof ConditionalNode && VectorizeConditional.getValue(value.graph().getOptions())) {
                        result = vectorizeConditional((ConditionalNode) value);
                    } else if (value instanceof LogicNode) {
                        result = vectorizeLogic((LogicNode) value, vectorizedNodes);
                    } else if (value instanceof FloatingReadNode) {
                        result = vectorizeRead((FloatingReadNode) value, vectorizedNodes);
                    } else if (value instanceof ReadNode) {
                        result = vectorizeRead((ReadNode) value, vectorizedNodes);
                    } else if (value instanceof PiNode) {
                        result = vectorizeValue(((PiNode) value).getOriginalNode(), vectorizedNodes);
                    }
                }
            }
            if (result != null) {
                vectorizedValues.put(cacheKey, result);
                if (v instanceof VirtualConditionalNode) {
                    vectorizedValues.put(Pair.create(v, consumerDirection), result);
                }
            } else {
                value.getDebug().log(DebugContext.DETAILED_LEVEL, "failed to vectorize value %s", value);
            }
            return result;
        }

        private SequenceVectorNode vectorizeInductionVariable(ValueNode value, Direction consumerDirection) {
            if (VectorizeSequence.getValue(graph.getOptions())) {
                InductionVariable iv = loop.getInductionVariables().get(value);
                if (iv != null) {
                    InductionVariable checkIv = iv;
                    boolean extendSeen = false;
                    while (checkIv instanceof DerivedInductionVariable derivedIv) {
                        /*
                         * If an extend has been seen, check all parent ivs for not being negative.
                         * This ensures that vectorizing the computations as long sequence will
                         * produce the same results.
                         */
                        extendSeen |= derivedIv.valueNode() instanceof SignExtendNode || derivedIv.valueNode() instanceof ZeroExtendNode;
                        if (extendSeen) {
                            if (derivedIv.getBase().valueNode().stamp(NodeView.DEFAULT) instanceof IntegerStamp iStamp && iStamp.canBeNegative()) {
                                /*
                                 * An IV which can be negative but is later used for computing a
                                 * derived IV which contains a ZeroExtend or SignExtend. By
                                 * vectorizing the IV as a long sequence (without the Zero- or
                                 * SignExtend) we risk changing the semantics wrt overflowing the
                                 * int range.
                                 */
                                value.getDebug().log(DebugContext.DETAILED_LEVEL, "cannot vectorize sign or zero extending IV %s as a sequence because a parent iv can be negative", iv);
                                return null;
                            }
                        }
                        checkIv = derivedIv.getBase();
                    }
                    return graph.unique(new SequenceVectorNode(iv.initNode(), iv.strideNode(), consumerDirection));
                }
            }
            return null;
        }

        private <Read extends ValueNode & AddressableMemoryAccess & GuardedNode> VectorNode vectorizeRead(Read value, NodeBitMap vectorizedNodes) {
            DebugContext debug = value.getDebug();
            if (value.getAddress() instanceof OffsetAddressNode && value.getLastLocationAccess() != null) {
                OffsetAddressNode address = (OffsetAddressNode) value.getAddress();
                MemoryKill lastLocationAccess = value.getLastLocationAccess();
                /*
                 * Retain the read's existing guard. However, if it's guarded by the counted loop
                 * begin, rewrite that guard edge to an anchor before the loop. We either remove the
                 * original loop altogether, or it becomes a post loop. Either way, the new
                 * vectorized read cannot remain anchored there.
                 */
                GuardingNode guard = value.getGuard();
                if (guard == null || guard == loop.counted().getBody()) {
                    guard = AbstractBeginNode.prevBegin(loop.loopBegin().forwardEnd());
                } else if (guard instanceof MultiGuardNode multiGuard) {
                    guard = (GuardingNode) multiGuard.copyWithInputs(true);
                    guard.asNode().replaceAllInputs(loop.counted().getBody(), AbstractBeginNode.prevBegin(loop.loopBegin().forwardEnd()));
                }
                /* Now add any aliasing guards. */
                while (!loop.isOutsideLoop(lastLocationAccess.asNode())) {
                    // potential aliasing inside loop
                    if (lastLocationAccess instanceof OptimisticMemoryEdge) {
                        OptimisticMemoryEdge edge = (OptimisticMemoryEdge) lastLocationAccess;
                        guard = MultiGuardNode.addGuard(guard, edge.getGuard());
                        lastLocationAccess = edge.getOptimisticEdge();
                    } else {
                        debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize %s with potentially aliasing lastLocationAccess %s", value, lastLocationAccess);
                        return null;
                    }
                }

                InductionVariable iv = LoopVectorizationAnalysis.getInductionVariable(loop, address);
                if (hasOnlySupportedUsages(value, vectorizedNodes)) {
                    if (iv != null) {
                        if (LoopVectorizationAnalysis.canVectorizeIv(graph, iv) && isCompatibleWithCurrentIv(iv)) {
                            AddressNode startAddress = createStartAddress(iv, address);
                            VectorStamp stamp = new VectorStamp(value.stamp(NodeView.DEFAULT));
                            /*
                             * The vector read we build here is a floating placeholder. It will be
                             * replaced by a fixed version in a cleanup step. We use this two-stage
                             * process because we don't know yet where exactly the read should be
                             * inserted. We can only determine that once we know if the guard is
                             * replaced by a vectorized version.
                             */
                            return graph.unique(
                                            new FloatingVectorReadNode(startAddress, value.getLocationIdentity(), (int) iv.constantStride(), stamp, value.getBarrierType(), lastLocationAccess, guard));
                        } else {
                            debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize IV %s for %s", iv, value);
                            return null;
                        }
                    } else if (loop.isOutsideLoop(address.getBase()) && (address.getOffset().isConstant() || loop.isOutsideLoop(address.getOffset()))) {
                        ValueNode loopInvariantRead;
                        if (value instanceof FloatingReadNode) {
                            loopInvariantRead = FloatingReadNode.create(graph, address, value.getLocationIdentity(), lastLocationAccess, value.stamp(NodeView.DEFAULT), guard,
                                            value.getBarrierType());
                        } else {
                            ReadNode origRead = (ReadNode) value;
                            ReadNode fixedInvariantRead = graph.add(new ReadNode(address, value.getLocationIdentity(), value.stamp(NodeView.DEFAULT), value.getBarrierType(),
                                            origRead.getMemoryOrder()));
                            graph.addAfterFixed(AbstractBeginNode.prevBegin(loop.loopBegin().forwardEnd()), fixedInvariantRead);
                            loopInvariantRead = fixedInvariantRead;
                        }
                        return graph.unique(new FillVectorNode(loopInvariantRead));
                    } else if (VectorizeGather.getValue(value.graph().getOptions()) && LoopVectorizationAnalysis.isPotentialGatherAddress(address, loop, arch)) {
                        ValueNode base = address.getBase();
                        VectorNode offsets = vectorizeValue(address.getOffset(), vectorizedNodes);
                        if (!(offsets instanceof ShiftableVectorNode)) {
                            debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize address offset %s for gather %s: non-shiftable %s", address.getOffset(), value, offsets);
                            return null;
                        }
                        VectorStamp stamp = new VectorStamp(value.stamp(NodeView.DEFAULT));
                        VectorNode gather = graph.unique(new FloatingVectorGatherNode(base, offsets.asNode(), value.getLocationIdentity(), stamp, value.getBarrierType(), lastLocationAccess, guard));
                        return gather;
                    }
                }
            }

            debug.log(DebugContext.DETAILED_LEVEL, "can't vectorize read %s with address %s, lastLocationAccess %s", value, value.getAddress(), value.getLastLocationAccess());
            return null;
        }

        private boolean isCompatibleWithCurrentIv(InductionVariable iv) {
            assert iv != null;
            assert iv.isConstantStride() : "IV must be constant stride " + iv;
            GraalError.guarantee(currentInductionVariable != null, "a currentInductionVariable must always be set: %s", currentInductionVariable);

            // until GR-6038 is resolved, the sign of all induction variables must be the same
            assert currentInductionVariable.isConstantStride() : "IV must be constant stride " + currentInductionVariable;
            return Long.signum(currentInductionVariable.constantStride()) == Long.signum(iv.constantStride());
        }

        private InductionVariable getLoopIV() {
            return loop.counted().getLimitCheckedIV();
        }

        private VectorNode vectorizeArithmetic(ValueNode value) {
            ArrayList<ValueNode> vectorInputs = new ArrayList<>();
            ArrayList<ValueNode> scalarInputs = new ArrayList<>();
            StructuredGraph mapGraph = createInnerGraph(value, vectorInputs, scalarInputs, null);

            if (mapGraph != null) {
                return graph.unique(new MapVectorNode(mapGraph, vectorInputs, scalarInputs));
            } else {
                return null;
            }
        }

        private VectorNode vectorizeConditional(ConditionalNode conditional) {
            if (!LoopVectorizationAnalysis.isVectorizableAsInnerNode(conditional, loop, arch)) {
                return null;
            }
            return vectorizeArithmetic(conditional);
        }

        private VectorNode vectorizeLogic(LogicNode logicNode, NodeBitMap vectorizedNodes) {
            if (logicNode instanceof CompareNode) {
                return vectorizeCompare((CompareNode) logicNode, vectorizedNodes);
            } else if (logicNode instanceof IsNullNode) {
                return vectorizeIsNull((IsNullNode) logicNode, vectorizedNodes);
            }

            return null;
        }

        private VectorNode vectorizeCompare(CompareNode compare, NodeBitMap vectorizedNodes) {
            if (!LoopVectorizationAnalysis.isVectorizableCompare(compare, loop, arch)) {
                return null;
            }
            VectorNode x = vectorizeValue(compare.getX(), vectorizedNodes);
            if (x == null) {
                return null;
            }
            VectorNode y = vectorizeValue(compare.getY(), vectorizedNodes);
            if (y == null) {
                return null;
            }

            Stamp elementStamp = compare.getX().stamp(NodeView.DEFAULT);
            return graph.unique(CompareVectorNode.compare(compare.condition(), x.asNode(), y.asNode(), compare.unorderedIsTrue(), arch.maskStamp(elementStamp)));
        }

        private VectorNode vectorizeIsNull(IsNullNode isNullNode, NodeBitMap vectorizedNodes) {
            if (!LoopVectorizationAnalysis.isVectorizableIsNull(isNullNode, arch)) {
                return null;
            }
            VectorNode value = vectorizeValue(isNullNode.getValue(), vectorizedNodes);
            if (value == null) {
                return null;
            }
            Stamp elementStamp = isNullNode.getValue().stamp(NodeView.DEFAULT);
            return graph.unique(VectorIsNullNode.isNull(value.asNode(), isNullNode.nullConstant(), arch.maskStamp(elementStamp)));
        }

        private StructuredGraph createInnerGraph(ValueNode value, ArrayList<ValueNode> vectorInputs, ArrayList<ValueNode> scalarInputs, final PhiNode foldAccumulator) {
            final NodeBitMap innerNodes = new NodeBitMap(graph);
            collectInnerNodes(innerNodes, value, loop, arch, false);

            final StructuredGraph innerGraph = new StructuredGraph.Builder(graph.getOptions(), graph.getDebug(), graph.allowAssumptions()).compilationId(graph.compilationId()).trackNodeSourcePosition(
                            graph.trackNodeSourcePosition()).build();
            final List<ValueNode> inputNodes = new ArrayList<>();
            UnmodifiableEconomicMap<Node, Node> duplicates = innerGraph.addDuplicates(innerNodes, graph, innerNodes.count(), new DuplicationReplacement() {

                @Override
                public Node replacement(Node node) {
                    if (node instanceof ValuePhiNode) {
                        if (node.hasExactlyOneUsage() && node.usages().first() instanceof VirtualConditionalNode) {
                            // Don't represent this phi in the vector graph, the virtual conditional
                            // is enough.
                            return null;
                        }
                    }
                    if (innerNodes.contains(node)) {
                        return node;
                    } else if (node == foldAccumulator) {
                        return innerGraph.unique(new FoldVectorNode.AccumulatorNode(foldAccumulator.stamp(NodeView.DEFAULT)));
                    } else {
                        ValueNode input = (ValueNode) node;
                        int inputIdx = inputNodes.size();
                        inputNodes.add(input);
                        return innerGraph.unique(new ParameterNode(inputIdx, StampPair.createSingle(input.stamp(NodeView.DEFAULT))));
                    }
                }
            });

            for (Node innerNode : innerGraph.getNodes()) {
                if (innerNode instanceof VirtualConditionalNode) {
                    // Inside the vector graph, these virtual conditionals should really be
                    // conditionals.
                    ((VirtualConditionalNode) innerNode).commit();
                }
            }

            ValueNode originalValue = value;
            if (value instanceof VirtualConditionalNode) {
                originalValue = ((VirtualConditionalNode) value).conditional();
            }
            ValueNode dupValue = (ValueNode) duplicates.get(originalValue);
            ReturnNode ret = innerGraph.add(new ReturnNode(dupValue));
            innerGraph.addAfterFixed(innerGraph.start(), ret);

            vectorInputs.ensureCapacity(inputNodes.size());
            for (int i = 0; i < inputNodes.size(); i++) {
                ParameterNode param = innerGraph.getParameter(i);
                assert param.getUsageCount() == 1 : "Should only have one usage " + param;
                Node paramUsage = param.usages().first();
                ValueNode input = inputNodes.get(i);

                if (paramUsage instanceof ShiftNode<?> s && s.getY() == param && loop.isOutsideLoop(input)) {
                    // If the shift amount is a loop invariant, delay its expansion
                    int scalarIdx = scalarInputs.size() + SubGraphUtil.SCALAR_OFFSET;
                    scalarInputs.add(input);
                    ParameterNode scalarParam = innerGraph.unique(new ParameterNode(scalarIdx, StampPair.createSingle(param.stamp(NodeView.DEFAULT))));
                    param.replaceAtUsagesAndDelete(scalarParam);
                } else {
                    VectorNode vector = vectorizeValue(input, innerNodes);
                    if (vector == null) {
                        // we can't vectorize an input to this loop, so we can't vectorize the loop
                        return null;
                    }

                    int vectorIdx = vectorInputs.size();
                    if (vectorIdx >= SubGraphUtil.SCALAR_OFFSET) {
                        value.getDebug().log(DebugContext.DETAILED_LEVEL, "inner graph for %s would have too many vector inputs: %d", value, vectorIdx);
                        return null;
                    }

                    vectorInputs.add(vector.asNode());
                    if (vectorIdx != param.index()) {
                        ParameterNode vectorParam = innerGraph.unique(new ParameterNode(vectorIdx, StampPair.createSingle(param.stamp(NodeView.DEFAULT))));
                        param.replaceAtUsagesAndDelete(vectorParam);
                    }
                }
            }

            if (foldAccumulator != null) {
                // A fold without an accumulator is not a valid fold. As the accumulator may
                // disappear due to canonicalization of sign extend/narrow pairs, we must
                // simplify and check the operation right away to avoid simplifying to an invalid
                // fold later.
                new VectorSimplificationPhase.CachingSimplifier(graph, context, canonicalizer).canonicalize(innerGraph);
                if (!SubGraphUtil.containsValidAccumulator(innerGraph)) {
                    graph.getDebug().log(DebugContext.DETAILED_LEVEL, "no accumulator in subgraph but have fold accumulator %s", foldAccumulator);
                    return null;
                }
            }

            return innerGraph;
        }

        private boolean hasOnlySupportedUsages(Node readNode, NodeBitMap vectorizedNodes) {
            DebugContext debug = readNode.getDebug();
            /*
             * First, check if the read has *direct* usages both inside and outside the loop. We do
             * the same check for transitive usages below.
             */
            if (hasBothInsideAndOutsideUsages(readNode, readNode)) {
                debug.log(DebugContext.DETAILED_LEVEL, "unsupported direct inside/outside usages for %s", readNode);
                return false;
            }
            // we can't vectorize a read if it is used in a non-vectorizable part of the loop.
            ArrayDeque<Node> usagesToProcess = new ArrayDeque<>();
            NodeBitMap visited = new NodeBitMap(graph);
            visited.mark(readNode);
            enqueueUsages(readNode, usagesToProcess, visited);
            while (!usagesToProcess.isEmpty()) {
                Node usage = usagesToProcess.remove();
                assert !loop.isOutsideLoop(usage) : usage + " must be inside loop " + loop.inside();
                if (usage instanceof WriteNode) {
                    if (vectorizableWrites != null && !vectorizableWrites.containsKey((WriteNode) usage)) {
                        debug.log(DebugContext.DETAILED_LEVEL, "unsupported usage for %s: %s not marked as vectorizable", readNode, usage);
                        return false;
                    }
                    // Don't look at this write's usages, they aren't usages of the read's value.
                    continue;
                }
                if (usage instanceof IfNode && LoopVectorizationAnalysis.getDeoptExit((IfNode) usage) != null) {
                    if (!valuesToVectorize.isMarked(((IfNode) usage).condition())) {
                        debug.log(DebugContext.DETAILED_LEVEL, "unsupported usage for %s: %s not marked for vectorization", readNode, ((IfNode) usage).condition());
                        return false;
                    }
                    continue;
                }
                if (usage instanceof IfNode) {
                    // This must be an empty if that we will if-convert if vectorization succeeds.
                    continue;
                }
                if (usage instanceof FrameState) {
                    continue;
                }
                if (!acceptableReadUsage(readNode, usage, vectorizedNodes)) {
                    debug.log(DebugContext.DETAILED_LEVEL, "unsupported usage for %s: %s", readNode, usage);
                    return false;
                }
                enqueueUsages(usage, usagesToProcess, visited);
            }
            return true;
        }

        /**
         * Return {@code true} if the transitive usage {@code usage} of a read {@code readNode} is
         * "acceptable" for vectorization, i.e., there is no property specific to this usage that
         * would make vectorization impossible or illegal.
         */
        private boolean acceptableReadUsage(Node readNode, Node usage, NodeBitMap vectorizedNodes) {
            /*
             * First, check if the usage itself is marked for vectorization. This is required,
             * except for some special cases below.
             */
            if (currentNodeToVectorize == usage || valuesToVectorize.isMarked(usage) || (vectorizedNodes != null && vectorizedNodes.contains(usage))) {
                if (!(usage instanceof PhiNode)) {
                    if (hasBothInsideAndOutsideUsages(readNode, usage)) {
                        return false;
                    }
                }
                return true;
            }
            /*
             * Some special nodes that will disappear through vectorization and do not prevent it.
             */
            if (usage instanceof PhiNode || usage instanceof PiNode) {
                return true;
            }
            if (usage instanceof OffsetAddressNode && loop.isOutsideLoop(((OffsetAddressNode) usage).getBase()) && valuesToVectorize.isMarked(((OffsetAddressNode) usage).getOffset())) {
                // A vectorizable address computation based on a read.
                return true;
            }
            return false;
        }

        /**
         * Check if {@code readNode}'s transitive usage {@code usage} has usages both inside and
         * outside the loop. This can happen for inverted loops. For example:
         *
         * <pre>
         *     loop {
         *         read = array[i];
         *         usage = read + 1;
         *         someInsideUsage(usage);
         *         if (i >= 100) {
         *             break;
         *         }
         *     }
         *     someOutsideUsage(usage);
         * </pre>
         *
         * We cannot vectorize reads that have (transitive) usages both inside and outside the loop.
         * The usages inside the loop would be replaced by vector nodes fed by the vectorized read,
         * but the usages outside would still keep the original read alive. We aren't allowed to
         * duplicate reads in this way, and we wouldn't be able to enforce the correct ordering of
         * the original scalar read with regard to the vector loop.
         * <p/>
         *
         * If called with {@code readNode == usage}, this checks the read's direct usages. Otherwise
         * it checks transitive usages via {@code usage}.
         */
        private boolean hasBothInsideAndOutsideUsages(Node readNode, Node usage) {
            boolean seenOutsideUsage = false;
            boolean seenInsideUsage = false;
            for (Node transitiveUsage : usage.usages()) {
                boolean outside = loop.isOutsideLoop(transitiveUsage);
                seenOutsideUsage |= outside;
                seenInsideUsage |= !outside;
                if (seenOutsideUsage && seenInsideUsage) {
                    usage.getDebug().log(DebugContext.DETAILED_LEVEL,
                                    "read %s's usage %s has both inside and outside usages, can't vectorize read",
                                    readNode, usage, transitiveUsage);
                    return true;
                }
            }
            return false;
        }

        private void enqueueUsages(Node node, ArrayDeque<Node> usagesToProcess, NodeBitMap visited) {
            for (Node usage : node.usages()) {
                if (!visited.isMarked(usage) && !loop.isOutsideLoop(usage) && !(usage instanceof MemoryPhiNode)) {
                    usagesToProcess.add(usage);
                    visited.mark(node);
                }
            }
        }

        // Collect nodes that should be vectorized, starting at node. If collectReads is true,
        // include read nodes; if it's false, do not include them. We want to mark reads while
        // searching for all nodes to be vectorized, but not include them when building an inner
        // graph for a map or a fold.
        private static void collectInnerNodes(NodeBitMap innerNodes, Node node, Loop loop, VectorArchitecture arch, boolean collectReads) {
            if (innerNodes.contains(node)) {
                return;
            }
            if (!collectReads && (node instanceof FloatingReadNode || node instanceof ReadNode)) {
                return;
            }
            if (node instanceof VirtualConditionalNode virtualConditional) {
                if (loop.isOutsideLoop(virtualConditional.condition())) {
                    /*
                     * This input condition would have to become a parameter of the inner graph, but
                     * logic nodes can't be parameters. We can't handle this at the moment.
                     */
                    return;
                }
            }

            assert node instanceof FloatingNode || node instanceof ReadNode : node + " is not a floating node or a fixed read";
            innerNodes.mark(node);
            if (VectorizeGather.getValue(node.graph().getOptions()) && (node instanceof FloatingReadNode || node instanceof ReadNode)) {
                maybeCollectReadAddress(innerNodes, node, loop, arch, collectReads);
            }
            for (Node input : node.inputs()) {
                if (LoopVectorizationAnalysis.isVectorizableAsInnerNode(input, loop, arch)) {
                    if (!loop.isOutsideLoop(input) && loop.getInductionVariables().get(input) == null) {
                        collectInnerNodes(innerNodes, input, loop, arch, collectReads);
                    }
                } else if (collectReads && (input instanceof FloatingReadNode || input instanceof ReadNode)) {
                    collectInnerNodes(innerNodes, input, loop, arch, collectReads);
                }
            }
        }

        private static void maybeCollectReadAddress(NodeBitMap innerNodes, Node read, Loop loop, VectorArchitecture arch, boolean collectReads) {
            assert read instanceof FloatingReadNode || read instanceof ReadNode : read;
            OffsetAddressNode address = null;
            if (read instanceof FloatingReadNode && ((FloatingReadNode) read).getAddress() instanceof OffsetAddressNode) {
                address = (OffsetAddressNode) ((FloatingReadNode) read).getAddress();
            }
            if (read instanceof ReadNode && ((ReadNode) read).getAddress() instanceof OffsetAddressNode) {
                address = (OffsetAddressNode) ((ReadNode) read).getAddress();
            }
            if (address != null && LoopVectorizationAnalysis.isPotentialGatherAddress(address, loop, arch)) {
                ValueNode offset = address.getOffset();
                if (!loop.isOutsideLoop(offset) && loop.getInductionVariables().get(offset) == null) {
                    collectInnerNodes(innerNodes, offset, loop, arch, collectReads);
                }
            }
        }

        /**
         * Returns a bitmap marking all the values in the loop that must be vectorized in order to
         * vectorize the loop. This collects all the values that feed into writes (if
         * {@code mapShaped} is set) and into phis (if {@code foldShaped} is set).
         *
         * @param loop the loop to analyze
         * @param arch the target vector architecture
         * @param bodyNodes the nodes that make up the loop's body
         * @param writes the write nodes in the loop to analyze for map-shaped vectorization
         * @param deopts whether to try to vectorize deopts inside the loop
         * @param mapShaped whether to try to vectorize maps inside the loop
         * @param foldShaped whether to try to vectorize folds inside the loop
         * @return all the values to vectorize
         */
        public static LoopValues valuesToVectorize(Loop loop, VectorArchitecture arch, ArrayList<FixedNode> bodyNodes, EconomicMap<WriteNode, InductionVariable> writes, boolean deopts,
                        boolean mapShaped, boolean foldShaped, boolean reachabilityFences, boolean safepoints) {
            LoopValues values = new LoopValues(loop.loopBegin().graph());

            if (deopts || reachabilityFences) {
                for (FixedNode node : bodyNodes) {
                    if (deopts && node instanceof IfNode) {
                        IfNode ifNode = (IfNode) node;
                        values.rootValues.add(ifNode.condition());
                        values.deopts++;
                        collectInnerNodes(values.allValues, ifNode.condition(), loop, arch, true);
                    }
                    if (reachabilityFences && node instanceof ReachabilityFenceNode) {
                        ReachabilityFenceNode fence = (ReachabilityFenceNode) node;
                        values.rootValues.addAll(fence.getValues());
                        values.reachabilityFences++;
                        for (ValueNode value : fence.getValues()) {
                            if (!loop.isOutsideLoop(value)) {
                                collectInnerNodes(values.allValues, value, loop, arch, true);
                            }
                        }
                    }
                }
            }

            if (mapShaped) {
                for (WriteNode write : writes.getKeys()) {
                    ValueNode value = write.value();
                    if (LoopVectorizationAnalysis.isVectorizableComputationRoot(value, loop, arch)) {
                        values.rootValues.add(write.value());
                        values.maps++;
                        if (!loop.isOutsideLoop(value)) {
                            collectInnerNodes(values.allValues, write.value(), loop, arch, true);
                        }
                    } else {
                        loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "non-vectorizable value %s for write %s", value, write);
                        return null;
                    }
                }
            }

            if (foldShaped) {
                EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
                for (PhiNode phi : loop.loopBegin().phis().snapshot()) {
                    if (ivs.get(phi) != null) {
                        continue;
                    }
                    if (phi instanceof ValuePhiNode) {
                        ValueNode value = phi.singleBackValueOrThis();
                        if (LoopVectorizationAnalysis.isVectorizableComputationRoot(phi, loop, arch)) {
                            if (hasOutsideUsages(phi, loop)) {
                                if (RemoveEmptyLoopsPhase.isOptimizablePhi(phi, loop)) {
                                    /*
                                     * No need to vectorize such phis. But we must count them; this
                                     * is relevant for determining the deopt strategy, if there is a
                                     * deopt in the same loop.
                                     */
                                    values.complexPhis++;
                                    continue;
                                }
                                values.rootValues.add(value);
                                values.folds++;
                                collectInnerNodes(values.allValues, value, loop, arch, true);
                            } else {
                                loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "ignoring phi %s with back value %s, phi has no outside usages", phi, value);
                                if (loop.counted().isInverted()) {
                                    loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "(%s might be an inverted fold, GR-62006)", phi);
                                    if (!RemoveEmptyLoopsPhase.isOptimizablePhi(phi, loop)) {
                                        loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "not vectorizing due to non-optimizable inverted phi %s)", phi);
                                        return null;
                                    }
                                }
                            }
                        } else {
                            loop.loopBegin().getDebug().log(DebugContext.DETAILED_LEVEL, "non-vectorizable value %s for phi %s", value, phi);
                            return null;
                        }
                    }
                }
            }

            if (values.isEmpty()) {
                // We can "vectorize" empty loops by eliminating them completely.
                return values;
            }

            if (safepoints) {
                for (FixedNode node : bodyNodes) {
                    if (node instanceof SafepointNode) {
                        values.safepoints++;
                        assert values.safepoints == 1 : "loops with more than one safepoint not supported";
                    }
                }
            }

            return values;
        }
    }
}
