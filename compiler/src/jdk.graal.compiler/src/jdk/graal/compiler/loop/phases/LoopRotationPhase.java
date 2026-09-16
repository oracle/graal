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
package jdk.graal.compiler.loop.phases;

import static jdk.graal.compiler.debug.DebugContext.VERY_DETAILED_LEVEL;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.nodes.loop.Loop.IfPosition;

import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.duplication.util.DuplicationUtil;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopFragment;
import jdk.graal.compiler.nodes.loop.LoopFragmentInside;
import jdk.graal.compiler.nodes.loop.LoopFragmentWhole;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.MaterializedObjectState;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.util.EconomicSetNodeEventListener;
import jdk.graal.compiler.phases.common.util.LoopUtility;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerAddExactNode;
import jdk.graal.compiler.replacements.nodes.arithmetic.IntegerSubExactNode;

/**
 * Performs loop rotation on a {@link StructuredGraph}. Loop rotation is a special form of the loop
 * peeling optimization. It is used to make loops with complex code (in Graal IR mostly
 * {@linkplain FixedWithNextNode}) between the loop begin and the head counted exit check counted.
 *
 * Example:
 *
 * <pre>
 * iv = start;
 * while (true) {
 *     toxicPreBodyNodes();
 *     if (iv < end) {
 *         body();
 *         iv += stride;
 *         continue;
 *     }
 *     break;
 * }
 * </pre>
 *
 * is not a counted loop because there are nodes executed between the loop begin and the actual
 * index check. Such loops can be transformed to be proper head counted loops via a limited amount
 * of code duplication.
 *
 * <pre>
 * iv = start;
 * toxicPreBodyNodes(); // replacing iv with start in the duplicated portion
 * while (true) {
 *     if (iv < end) {
 *         body();
 *         iv += stride;
 *         toxicBodyNodes();
 *         continue;
 *     }
 *     break;
 * }
 * </pre>
 *
 * See {@link LoopRotationPhase#rotate(StructuredGraph, CoreProviders)} for details about the
 * transformation.
 *
 * In order to enable as many counted loops as possible, rotation supports various complex code
 * patterns in the toxic node set: See
 * {@link LoopRotationPhase#traverseLinearWithDiamonds(LoopBeginNode, FixedNode, Predicate, Consumer)}
 * for details.
 *
 * Loop rotation should improve loops that are neither head-counted nor tail-counted loops. That
 * includes loop patterns that have multiple loop exits of different condition types. For example
 * consider the following loop from {@link LinkedList#toArray()} (as of jdk 17):
 *
 * <pre>
 * public Object[] toArray() {
 *     Object[] result = new Object[size];
 *     int i = 0;
 *     for (Node<E> x = first; x != null; x = x.next) // first condition x!= null
 *         result[i++] = x.item;                      // second "condition" if(i>result.length) ->
 *                                                    // deoptimize()
 *     return result;
 * }
 * </pre>
 *
 * This loop has two loop exit conditions: the first one {@code x != null} does not contain an
 * induction variable check (since it does not check a mathematical comparison with a strictly
 * monotonically increasing / decreasing induction variable). The second one is an implicit one not
 * visible in code. Graal IR will create an explicit bounds check for the array access
 * {@code if(i >= result.length) deopt} in which case the interpreter can throw an
 * {@link IndexOutOfBoundsException}. So in reality the code compiled looks like:
 *
 * <pre>
 * public Object[] toArray() {
 *     Object[] result = new Object[size];
 *     int i = 0;
 *     Node<E> x = first;
 *     while (true) {
 *         if (x == null)
 *             break;
 *         if (i >= result.length)
 *             deoptimize();
 *         result[i] = x.item;
 *         i++;
 *         x = x.next;
 *     }
 *     return result;
 * }
 * </pre>
 *
 * However, the condition {@code if(i >= result.length)} is a perfect counted loop condition since
 * {@code i} is an induction variable. Sadly we cannot detect this loop as a tail counted loop since
 * there is still the write to the array in the loop body. Thus, this loop is not strictly head nor
 * tail counted. The compiler needs a mathematical comparison of an induction variable with a loop
 * invariant value ({@code i} in this case) to have a counted loop condition. Loop rotation can
 * duplicate the first non-counted condition before the loop and at the loop end (rotate it around
 * the loop header) to produce the following code:
 *
 * <pre>
 * public Object[] toArray() {
 *     Object[] result = new Object[size];
 *     int i = 0;
 *     Node<E> x = first;
 *     if (x != null) {
 *         while (true) {
 *             if (i >= result.length)
 *                 deoptimize();
 *             result[i] = x.item;
 *             i++;
 *             x = x.next;
 *             if (x == null)
 *                 break;
 *         }
 *     }
 *     return result;
 * }
 * </pre>
 *
 * Now, the comparison with the induction variable {@code i} dominates the loop body and the loop
 * resembles a properly head counted loop that is subject to further optimizations later.
 *
 * Performance impacts (see LoopRotationBenchmark) for the {@link LinkedList#toArray()} measured in
 * 11/2021 can be seen below:
 *
 * <pre>
 * Benchmark                                              Mode  Cnt     Score   Error   Units
 * LoopRotationBenchmark.benchWithRotationLinkedList     thrpt    2   501.821          ops/ms
 * LoopRotationBenchmark.benchWithoutRotationLinkedList  thrpt    2   410.160          ops/ms
 * </pre>
 *
 * Performance improvements can range from a few percent to multiple X speedups since rotation
 * enables counted loop detection making loops amendable to unrolling, vectorization, guard
 * optimizations, safepoint elimination, etc.
 */
public class LoopRotationPhase<Context extends CoreProviders> extends PhaseSuite<Context> {

    public static class Options {
        //@formatter:off
        @Option(help = "Enables loop rotation to let the compiler detect more loops as counted.", type = OptionType.Expert)
        public static final OptionKey<Boolean> LoopRotation = new OptionKey<>(true);

        @Option(help = "Enables loop rotation in the high tier.", type = OptionType.Debug)
        public static final OptionKey<Boolean> HighTierLoopRotation = new OptionKey<>(true);

        @Option(help = "Enables loop rotation early in the mid tier.", type = OptionType.Debug)
        public static final OptionKey<Boolean> EarlyMidTierLoopRotation = new OptionKey<>(false);

        @Option(help = "Enables loop rotation late in the mid tier.", type = OptionType.Debug)
        public static final OptionKey<Boolean> LateMidTierLoopRotation = new OptionKey<>(true);

        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> LoopRotationAssertCountedAfter = new OptionKey<>(false);

        @Option(help = "Maximum size in NodeSize of the code to be duplicated during rotation.", type = OptionType.Debug)
        public static final OptionKey<Integer> LoopRotationToxicNodeSetMaxNodecost = new OptionKey<>(512);

        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> RotateNonLeafLoops = new OptionKey<>(false);

        @Option(help = "Minimal relative frequency for a loop to be considered for rotation.", type = OptionType.Debug)
        public static final OptionKey<Double> RotationMinRelativeFrequency = new OptionKey<>(1D);

        @Option(help = "Minimal loop frequency for a loop to be considered for rotation.", type = OptionType.Debug)
        public static final OptionKey<Double> RotationMinLocalFrequency = new OptionKey<>(2D);
        //@formatter:on
    }

    private static final CounterKey RotatedPhisReparedAfter = DebugContext.counter("LoopRotation_PhisReparedAfter");
    private static final CounterKey RotatedCountedAfter = DebugContext.counter("LoopRotation_Rotated_CountedAfter");
    private static final CounterKey RotatedDiamond = DebugContext.counter("LoopRotation_Rotated_Diamond");
    private static final CounterKey NotRotated = DebugContext.counter("LoopRotation_NotRotated");
    private static final CounterKey NotRotatedSize = DebugContext.counter("LoopRotation_NotRotated_Size");
    private static final CounterKey NotCountedCondition = DebugContext.counter("LoopRotation_NotRotated_NonCountedCondition");
    private static final CounterKey NotCountedNonLeaf = DebugContext.counter("LoopRotation_NotRotated_NonLeafLoop");
    private static final CounterKey NotRotatedNoToxicNodes = DebugContext.counter("LoopRotation_NotRotatedNoToxicNodes");
    private static final CounterKey NotRotatedCannotDuplicate = DebugContext.counter("LoopRotation_NotRotatedCannotDuplicate");
    private static final CounterKey NotRotatedCannotDuplicateNodeWithState = DebugContext.counter("LoopRotation_NotRotatedCannotDuplicate_NodeWithState");

    private final CanonicalizerPhase canonicalizer;

    /**
     * This class only exists to allow us to reference a non-generic class in
     * {@code CEOptimization}.
     */
    public static final class LoopRotationPhaseWitness extends LoopRotationPhase<CoreProviders> {
        private LoopRotationPhaseWitness() {
            super(null);
            GraalError.shouldNotReachHere("This class is not meant to be instantiated.");
        }
    }

    public LoopRotationPhase(CanonicalizerPhase canonicalizer) {
        this(canonicalizer, null);
    }

    @SuppressWarnings("this-escape")
    public LoopRotationPhase(CanonicalizerPhase canonicalizer, PhaseSuite<Context> cleanupPhase) {
        this.canonicalizer = canonicalizer;
        if (cleanupPhase != null) {
            super.appendPhase(cleanupPhase);
        }
    }

    /**
     * Print rotation information to {@link TTY}.
     */
    private static final boolean PRINT_ROTATION = false;

    @Override
    public float codeSizeIncrease() {
        return 5.0F;
    }

    /**
     * Pre-process the to-be-rotated loop. This includes the following steps:
     * <ul>
     * <li>Merge {@link LoopEndNode} to have a single loop end where rotated toxic nodes can be
     * duplicated to.</li>
     * <li>Remove obsolete {@link ProxyNode} from the loop to avoid problems with proxied values
     * that should not need proxies.</li>
     * <li>Add a fixed {@link FixedRotationPlaceholderNode} after the {@link LoopBeginNode} of the
     * loop to have a regular {@link FixedWithNextNode} as the start of the loop body.</li>
     * <li>Create unique (not GVNed) {@link FrameState} inputs for each {@link StateSplit} inside
     * the toxic nodes. This is necessary to ensure there are no {@link InputType#State} usages from
     * the non-toxic region of the loop into the toxic region. This is necessary since GraalIR does
     * not support the notion of State-{@link PhiNode} nodes.</li>
     * </ul>
     */
    @SuppressWarnings("try")
    private static void preProcessLoop(boolean needsStates, StructuredGraph graph, ArrayList<FixedNode> toxicNodes, Loop elex) {
        if (needsStates) {
            for (Node n : toxicNodes) {
                if (n instanceof StateSplit) {
                    StateSplit s = (StateSplit) n;
                    FrameState stateAfter = s.stateAfter();
                    if (stateAfter != null) {
                        // ensure every state split has its own state so we
                        // do not need to bother duplicating state edges
                        s.setStateAfter(stateAfter.duplicateWithVirtualState());
                        if (stateAfter.hasNoUsages()) {
                            stateAfter.safeDelete();
                        }
                    }
                }
            }
            elex.loopBegin().setStateAfter(elex.loopBegin().stateAfter().duplicateWithVirtualState());
        }
        if (elex.loopBegin().getLoopEndCount() != 1) {
            // can only rotate loops with a single loop end
            LoopUtility.mergeLoopEnds(elex.loopBegin());
            elex.invalidateFragmentsAndIVs();
        }
        for (PhiNode phi : elex.loopBegin().phis().snapshot()) {
            if (phi.hasNoUsages()) {
                phi.safeDelete();
            }
        }
        LoopUtility.removeObsoleteProxiesForLoop(elex);
        graph.addAfterFixed(elex.loopBegin(), graph.add(new FixedRotationPlaceholderNode()));
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After preprocessing loop %s", elex);
    }

    /**
     * Placeholder node to ensure the body of the to-be-rotated loop starts with a regular
     * {@link FixedWithNextNode}. This is important if {@link IfNode} are rotated.
     */
    @NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
    private static class FixedRotationPlaceholderNode extends FixedWithNextNode implements Canonicalizable {
        public static final NodeClass<FixedRotationPlaceholderNode> TYPE = NodeClass.create(FixedRotationPlaceholderNode.class);

        protected FixedRotationPlaceholderNode() {
            super(TYPE, StampFactory.forVoid());
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            // delete this node, it is not needed after duplication
            return null;
        }

    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FSA, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.VALUE_PROXY_REMOVAL, graphState),
                        canonicalizer.notApplicableTo(graphState));
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops();
    }

    @Override
    protected void run(StructuredGraph graph, Context context) {
        if (graph.hasLoops()) {
            verifyRotation(false, graph, "Loop Rotation assertion prologue.");
            if (rotate(graph, context)) {
                super.run(graph, context);
                verifyRotation(false, graph, "Loop Rotation assertion epilogue.");
            }
        }
    }

    /**
     * Try to rotate {@linkplain Loop loops} inside this compilation unit. See
     * {@link LoopRotationPhase#rotateToxicNodeSet(StructuredGraph, Loop, ArrayList, boolean)}
     * for details on the transformation.
     */
    @SuppressWarnings("try")
    private boolean rotate(StructuredGraph graph, CoreProviders context) {
        final boolean needsStates = graph.getGuardsStage().areFrameStatesAtSideEffects();
        EconomicSet<LoopBeginNode> candidateLoops = null;

        LoopsData ld = context.getLoopsDataProvider().getLoopsData(graph);
        for (Loop lex : ld.loops()) {
            Loop elex = lex;
            if (!lex.detectCounted()) {
                if (!elex.canDuplicateLoop()) {
                    // not allowed to duplicate the body of the loop
                    continue;
                }
                if (loopQualifiesForRotation(elex)) {
                    ArrayList<FixedNode> toxicNodes = isCountedAfterRotation(elex);
                    if (toxicNodes != null) {
                        if (candidateLoops == null) {
                            candidateLoops = EconomicSet.create();
                        }
                        candidateLoops.add(elex.loopBegin());
                    }
                }
            }
        }
        boolean rotated = false;
        if (candidateLoops != null) {
            for (Loop lex : context.getLoopsDataProvider().getLoopsData(graph).loops()) {
                if (candidateLoops.contains(lex.loopBegin())) {
                    Loop elex = lex;
                    preProcessLoop(needsStates, graph, isCountedAfterRotation(elex), elex);
                }
            }
            boolean progress = false;
            process: while (true) { // TERMINATION ARGUMENT: processing a fixed set of candidate
                                    // loops
                CompilationAlarm.checkProgress(graph);
                ld = context.getLoopsDataProvider().getLoopsData(graph);
                for (Loop lex : ld.loops()) {
                    Loop elex = lex;
                    if (candidateLoops.contains(lex.loopBegin())) {
                        candidateLoops.remove(elex.loopBegin());
                        ArrayList<FixedNode> toxicNodes = isCountedAfterRotation(elex);
                        if (toxicNodes != null) {
                            EconomicSetNodeEventListener ec = new EconomicSetNodeEventListener();
                            try (NodeEventScope nes = graph.trackNodeEvents(ec)) {
                                if (rotateToxicNodeSet(graph, elex, toxicNodes, needsStates)) {
                                    graph.getOptimizationLog().report(getClass(), "LoopRotation", elex.loopBegin());
                                    elex.loopBegin().setRotated(true);
                                    progress = true;
                                    rotated = true;
                                } else {
                                    NotRotated.increment(graph.getDebug());
                                }
                            }
                            if (!ec.getNodes().isEmpty()) {
                                canonicalizer.applyIncremental(graph, context, ec.getNodes());
                                new DeadCodeEliminationPhase().apply(graph);
                                debugEpilogue(graph, context, elex);
                                verifyRotation(true, graph, "After rotating loop %s", lex);
                            }
                            if (progress) {
                                continue process;
                            }
                        } else {
                            NotRotatedNoToxicNodes.increment(graph.getDebug());
                        }
                    }
                }
                break;
            }
        }
        return rotated;
    }

    @SuppressWarnings("unused")
    private static void verifyRotation(boolean detailed, StructuredGraph graph, String msg, Object... args) {
        if (detailed) {
            if (!Assertions.detailedAssertionsEnabled(graph.getOptions())) {
                return;
            }
        }
        assert verifyRotation(graph);
    }

    private static boolean verifyRotation(StructuredGraph g) {
        assert GraphOrder.assertNonCyclicGraph(g);
        assert g.getGuardsStage().areFrameStatesAtDeopts() || GraphOrder.assertSchedulableGraph(g);
        if (g.getGuardsStage().areFrameStatesAtDeopts() && Assertions.detailedAssertionsEnabled(g.getOptions())) {
            // we still want to do a memory verification of the schedule even if we can
            // no longer use assertSchedulableGraph after the floating reads phase
            SchedulePhase.runWithoutContextOptimizations(g, SchedulingStrategy.EARLIEST);
        }
        return true;
    }

    private static void debugEpilogue(StructuredGraph graph, CoreProviders context, Loop elex) {
        if (graph.getDebug().areCountersEnabled() || Options.LoopRotationAssertCountedAfter.getValue(graph.getOptions())) {
            LoopsData ld1 = context.getLoopsDataProvider().getLoopsData(graph);
            for (Loop lex1 : ld1.loops()) {
                if (lex1.loopBegin() == elex.loopBegin()) {
                    if (lex1.detectCounted()) {
                        RotatedCountedAfter.increment(graph.getDebug());
                    }
                    if (Options.LoopRotationAssertCountedAfter.getValue(graph.getOptions())) {
                        assert lex1.detectCounted() : "Loop must be counted after rotation";
                    }
                }
            }
        }
    }

    /**
     * Determine for all usages of the toxic node set in the rest of the loop if we can create phi
     * nodes on the loop header after duplication without creating complex fix-point node sets like
     * {@link DuplicationUtil} does.
     */
    @SuppressWarnings("fallthrough")
    private static boolean canRotate(NodeFlood toxicFlood) {
        for (Node toxic : toxicFlood.getVisited()) {
            if (toxic instanceof NodeWithState && !(toxic instanceof StateSplit)) {
                if (!((NodeWithState) toxic).states().isEmpty()) {
                    // info point nodes for example and other nodes with complex framestate edges
                    // for which we do not want to duplicate frame state edges
                    DebugContext.counter(NotRotatedCannotDuplicateNodeWithState.getName() + toxic.getClass().getCanonicalName()).increment(toxic.getDebug());
                    return false;
                }
            }
            List<Node> afterRotationUsages = toxic.usages().snapshot();
            for (Node usage : afterRotationUsages) {
                boolean notToxic = (toxicFlood.isNew(usage) || !toxicFlood.isMarked(usage));
                if (notToxic) {
                    if (usage instanceof MaterializedObjectState) {
                        // cannot assign a phi to a materialized object state
                        DebugContext.counter(NotRotatedCannotDuplicateNodeWithState.getName() + toxic.getClass().getCanonicalName()).increment(toxic.getDebug());
                        return false;
                    }
                    for (Position p : usage.inputPositions()) {
                        Node input = p.get(usage);
                        if (input == toxic) {
                            switch (p.getInputType()) {
                                case Value:
                                case Memory:
                                case Guard:
                                    break;
                                default:
                                    toxic.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, toxic.graph(), "Cannot duplicate toxic node %s because of usage %s with edge type %s", toxic, usage,
                                                    p.getInputType());
                                    DebugContext.counter(NotRotatedCannotDuplicate.getName() + p.getInputType().toString()).increment(usage.getDebug());
                                    return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    /**
     * Rotate the set of toxic {@link Node} around the {@link LoopBeginNode}. This means they will
     * be duplicated once before the loop and once before the merged
     * ({@link LoopUtility#mergeLoopEnds(LoopBeginNode)}) {@link LoopEndNode}.
     *
     * Loop rotation uses {@link LoopFragment} API to perform the actual code duplication. The
     * general algorithm works as follows:
     * <ol>
     * <li>Based on the set of fixed nodes collect the entire set of fixed and floating nodes that
     * need to be duplicated in order to rotate this loop around a particular {@link IfNode}.</li>
     * <li>Decide for each usage from the non-toxic to the toxic node set if a {@link PhiNode} can
     * be created for it. If not, abort rotation.</li>
     * <li>Apply heuristics to decide if loop rotation may be beneficial for this loop, if not abort
     * rotation.</li>
     * <li>Duplicate the original loop, the toxic counterparts in the duplicated fragment will be
     * the toxic nodes at the loop end. For this redirect every input from a toxic node to a loop
     * phi to the backedge value of that phi.</li>
     * <li>Peel the original loop, delete every non-toxic counterpart in the peeled fragment.</li>
     * <li>In the original non-toxic body replace phi inputs with phi-before-peeling inputs, since
     * peeling updates IVs.</li>
     * <li>For each usage of a toxic node inside the body of the loop create a new phi that has the
     * peeled toxic as input(0) and the duplicated post dominating toxic as input(1).</li>
     * </ol>
     *
     * Note that the actual implementation is spread out in this method, the above list only covers
     * the conceptual steps.
     *
     * @return {@code true} if rotation was successful, {@code false} otherwise
     */
    private static boolean rotateToxicNodeSet(StructuredGraph graph, Loop elex, ArrayList<FixedNode> toxicNodes, boolean needsStates) {
        final IfNode countedIfAfter = (IfNode) ((FixedWithNextNode) toxicNodes.get(toxicNodes.size() - 1)).next();
        final LoopBeginNode lb = elex.loopBegin();

        EconomicMap<PhiNode, ValueNode> originalPhiFwdInputs = EconomicMap.create();
        for (PhiNode phi : lb.phis()) {
            originalPhiFwdInputs.put(phi, phi.firstValue());
        }

        /*
         * Determine which framestate to use as the loop header state: if there are diamonds inside
         * the toxic region we must use the last diamond's merge state
         */
        StateSplit lastToxicStatesplit = null;
        if (needsStates) {
            for (int i = toxicNodes.size() - 1; i >= 0; i--) {
                FixedNode toxicNode = toxicNodes.get(i);
                if (toxicNode instanceof StateSplit) {
                    // either a real side effect or a merge
                    if (((StateSplit) toxicNode).hasSideEffect() || toxicNode instanceof MergeNode) {
                        assert ((StateSplit) toxicNode).stateAfter() != null : "Node " + toxicNode + " needs state after because it has a side effect";
                        lastToxicStatesplit = (StateSplit) toxicNode;
                        break;
                    }
                }
            }
        }

        // collect set of fixed and floating nodes in this region from loop begin until
        // counted loop if
        NodeFlood toxicFlood = new NodeFlood(graph);

        // collect the entire node flood
        toxicFlood.addAll(toxicNodes);

        markToxicProxies(elex, toxicNodes, lb, toxicFlood);

        /*
         * Special case floating guards: all floating guards without usages that are part of the
         * loop that don't have any usages must also be duplicated
         */
        for (Node n : elex.whole().nodes()) {
            if (n instanceof GuardNode && n.hasNoUsages()) {
                GuardNode g = (GuardNode) n;
                // guard is not anchored inside the loop but still part of the loop, i.e., it will
                // be duplicated by the peeling so we must rewrite it to the backedge or give it iv
                // backedge values
                if (!elex.whole().contains(g.getAnchor().asNode())) {
                    toxicFlood.add(g);
                } else if (toxicFlood.isMarked(g.getAnchor().asNode())) {
                    toxicFlood.add(g);
                }
            }
        }

        for (Node flooded : toxicFlood) {
            for (Node input : flooded.inputs()) {
                if (!elex.loopBegin().isPhiAtMerge(input)) {
                    if (!elex.isOutsideLoop(input)) {
                        if (input instanceof VirtualObjectNode) {
                            // though virtual objects are considered to be "inside" a loop, there is
                            // no need to consider them being toxic nodes, since the duplicated
                            // portion will still operate on the same object
                            continue;
                        }
                        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Adding node %s to flood because of usage %s", input, flooded);
                        toxicFlood.add(input);
                    }
                }
            }
        }

        // this framestate can actually cause duplication to fail if the loop header state requires
        // complex state phis, thus duplicate it before calling canDuplicate()
        FrameState newState = null;
        if (lastToxicStatesplit != null) {
            newState = lastToxicStatesplit.stateAfter().duplicateWithVirtualState();
        }

        if (!canRotate(toxicFlood)) {
            return false;
        }

        if (!shouldRotate(graph, countedIfAfter, toxicFlood, elex)) {
            return false;
        }

        if (graph.getDebug().areCountersEnabled()) {
            for (Node flooded : toxicFlood.getVisited()) {
                if (flooded instanceof MergeNode) {
                    RotatedDiamond.increment(graph.getDebug());
                }
            }
        }

        if (PRINT_ROTATION) {
            TTY.printf("Rotated loop %s in %s w %s with node set %s\n", elex.loopBegin(), graph, graph.compilationId(), toxicFlood.getVisited());
        }

        final FixedWithNextNode toxicRegionStartInclusive = (FixedWithNextNode) elex.loopBegin().next();
        final IfNode toxicRegionEndExclusive = countedIfAfter;
        final FixedWithNextNode toxicRegionEndInclusive = (FixedWithNextNode) toxicRegionEndExclusive.predecessor();

        // duplicate the loop and only retain the toxic fraction
        // first duplicate to have a clean un-peeled version
        final LoopFragmentWhole duplicate = elex.whole().duplicate();
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After duplicating loop");

        final FixedWithNextNode duplicateToxicRegionStartInclusive = duplicate.getDuplicatedNode(toxicRegionStartInclusive);
        final FixedWithNextNode duplicateToxicRegionEndInclusive = duplicate.getDuplicatedNode(toxicRegionEndInclusive);
        final LoopBeginNode duplicatedLoopBegin = duplicate.getDuplicatedNode(lb);
        assert duplicateToxicRegionStartInclusive != null;
        assert duplicateToxicRegionEndInclusive != null;
        assert duplicatedLoopBegin != null;

        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Toxic=[%s]", toxicFlood.getVisited());

        // inside the post dominating (of the original loop body) toxic fraction phi usages must be
        // replaced with backedge values of the original loop, if the usages are toxic node
        // themselves, then they need to be phi nodes which will be handled when we process phi
        // nodes below
        for (PhiNode phi : duplicatedLoopBegin.phis()) {
            PhiNode reverseDuplicate = (PhiNode) duplicate.reverseDuplicationMap().get(phi);
            assert reverseDuplicate != null;
            assert reverseDuplicate.valueCount() == 2 : reverseDuplicate.values().snapshot();
            ValueNode backedgeVal = reverseDuplicate.valueAt(1);
            phi.replaceAtUsages(backedgeVal);
        }
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After replacing toxic duplicatee nodes with backedge values of original loop");

        // replace phi input values with toxic duplicate inputs
        for (PhiNode phi : lb.phis()) {
            ValueNode backedgeVal = phi.valueAt(1);
            if (toxicFlood.isMarked(phi)) {
                phi.setValueAt(1, duplicate.getDuplicatedNode(backedgeVal));
            }
        }
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After original backedge values with toxic duplicatees");

        // then peel
        LoopFragmentInside peeledFraction = LoopTransformations.peel(elex);
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After peeling");
        FixedWithNextNode toxicPeeledStart = peeledFraction.getDuplicatedNode(toxicRegionStartInclusive);
        FixedWithNextNode toxicPeeledEnd = peeledFraction.getDuplicatedNode(toxicRegionEndInclusive);
        assert toxicPeeledStart != null;
        assert toxicPeeledEnd != null;

        /*
         * The duplicated loop is split below: its toxic region is retained as the new loop body,
         * and the rest of its CFG is discarded. The duplicated loop exits in the retained region
         * must use the original loop header. Before discarding the rest, replace the value
         * currently referenced by each duplicated proxy with the corresponding node from before
         * duplication. This makes the discarded CFG removable. If the node currently referenced
         * by the proxy remains in the retained region, the proxy's value is restored below.
         */
        EconomicMap<ProxyNode, Node> duplicatedProxyValues = EconomicMap.create(Equivalence.IDENTITY);
        for (Node duplicateNode : duplicate.nodes()) {
            if (duplicateNode instanceof LoopExitNode loopExit && loopExit.loopBegin() == duplicatedLoopBegin) {
                loopExit.setLoopBegin(lb);
                for (ProxyNode proxy : loopExit.proxies().snapshot()) {
                    Node originalValue = duplicate.reverseDuplicationMap().get(proxy.value());
                    if (originalValue instanceof ValueNode && originalValue.isAlive() && !toxicFlood.isMarked(originalValue)) {
                        duplicatedProxyValues.put(proxy, proxy.value());
                        proxy.replaceFirstInput(proxy.value(), originalValue);
                    }
                }
            }
        }

        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After setting loop begin of loop exits in duplicated loop");

        // in the rest of the not peeled loop replace all phi inputs to non peeled toxic
        // nodes with the original inputs
        MapCursor<PhiNode, ValueNode> originalPhis = originalPhiFwdInputs.getEntries();
        while (originalPhis.advance()) {
            final PhiNode phi = originalPhis.getKey();
            PhiNode phiToSet = phi;
            PhiNode newPhi = peeledFraction.getOld2NewPhi().get(phi);
            if (newPhi != null) {
                phiToSet = newPhi;
            }
            phiToSet.setValueAt(0, originalPhiFwdInputs.get(phi));
        }
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After non toxic peeled fraction input to original loop phis");

        // for each usage of a toxic node into the body of the loop we need to create a
        // phi on the loop header
        EconomicMap<PhiKey, PhiNode> createdPhis = EconomicMap.create(Equivalence.DEFAULT);
        for (Node toxic : toxicFlood.getVisited()) {
            List<Node> toxicUsages = toxic.usages().snapshot();
            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Processing toxic node %s with usages %s", toxic, toxicUsages);
            for (Node usage : toxicUsages) {
                if (toxicFlood.isNew(usage) || !toxicFlood.isMarked(usage)) {
                    for (Position p : usage.inputPositions()) {
                        Node input = p.get(usage);
                        if (input == toxic) {
                            PhiKey key = new PhiKey(input, p.getInputType());
                            PhiNode phi = useOrCreatePhi(graph, input, lb, p, createdPhis, toxicFlood, key, peeledFraction, duplicate, usage);
                            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Before setting input %s of %s to %s", input, usage, phi);
                            p.set(usage, phi);
                            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Setting input %s of %s to %s", input, usage, phi);
                        }
                    }
                }
            }
        }
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After creating phis for toxic fraction");

        // cut it out
        // same as duplicatedLoopBegin
        FixedWithNextNode toKillPred = (FixedWithNextNode) duplicateToxicRegionStartInclusive.predecessor();
        FixedNode next = duplicateToxicRegionEndInclusive.next();
        duplicateToxicRegionEndInclusive.setNext(null);
        toKillPred.setNext(null);
        toKillPred.setNext(next);
        EndNode duplicateFwdEnd = duplicatedLoopBegin.forwardEndAt(0);
        GraphUtil.killCFG(duplicatedLoopBegin);
        GraphUtil.killCFG(duplicateFwdEnd);

        /*
         * Restore a live duplicated proxy's value to the node it referenced before rewiring when
         * that node also remains in the retained toxic region. If either node was deleted with
         * the rest of the CFG, no restoration is needed.
         */
        MapCursor<ProxyNode, Node> duplicatedValues = duplicatedProxyValues.getEntries();
        while (duplicatedValues.advance()) {
            ProxyNode proxy = duplicatedValues.getKey();
            Node duplicatedValue = duplicatedValues.getValue();
            if (proxy.isAlive() && duplicatedValue.isAlive()) {
                proxy.replaceFirstInput(proxy.value(), duplicatedValue);
            }
        }

        // cut toxic into original loop
        LoopEndNode len = lb.getSingleLoopEnd();
        FixedWithNextNode fwnOldLoop = (FixedWithNextNode) len.predecessor();
        fwnOldLoop.setNext(null);
        fwnOldLoop.setNext(duplicateToxicRegionStartInclusive);
        duplicateToxicRegionEndInclusive.setNext(len);
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After connecting old and new loop bodies");

        // then handle the toxic duplicated fraction via peeling
        FixedWithNextNode fwn = (FixedWithNextNode) lb.forwardEnd().predecessor();
        fwn.setNext(null);
        FixedNode killStartPeeled = toxicPeeledEnd.next();
        toxicPeeledEnd.setNext(null);

        // finally kill toxic fraction in original loop, the following operations must be done in a
        // particular order to avoid any problems with killCFG and loops.
        FixedWithNextNode pred = (FixedWithNextNode) toxicRegionStartInclusive.predecessor();
        toxicRegionEndInclusive.setNext(null);
        pred.setNext(null);
        pred.setNext(toxicRegionEndExclusive);
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After disconnecting original toxic fraction");
        GraphUtil.killCFG(toxicRegionStartInclusive);
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After cutting original toxic fraction");

        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After disconnecting non toxic peeled fraction");
        GraphUtil.killCFG(killStartPeeled);
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After kill cfg non toxic peeled fraction");
        toxicPeeledEnd.setNext(lb.forwardEnd());
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After connecting toxic peeled fraction to loop");

        // if a node is part of the toxic flood, it must be anchored at the loop end block
        for (Node toxic : toxicFlood.getVisited()) {
            Node d = duplicate.getDuplicatedNode(toxic);
            if (d.isAlive()) {
                for (Position p : d.inputPositions()) {
                    if (p.get(toxic) != null) {
                        if (p.getInputType() == InputType.Anchor || p.getInputType() == InputType.Guard) {
                            p.set(d, AbstractBeginNode.prevBegin(fwnOldLoop));
                        }
                    }
                }
            }
        }
        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After rewiring anchor edges with new anchor %s", AbstractBeginNode.prevBegin(fwnOldLoop));

        // delete guard nodes created in the fraction of the duplicate that should have been deleted
        // but are not deleted because we do not delete floating guards without usages
        for (Node duplicateNode : duplicate.nodes()) {
            if (duplicateNode.isAlive()) {
                Node original = duplicate.reverseDuplicationMap().get(duplicateNode);
                if (original != null && original.isAlive()) {
                    if (!toxicFlood.isMarked(original)) {
                        if (duplicateNode instanceof GuardNode && duplicateNode.hasNoUsages()) {
                            duplicateNode.safeDelete();
                        }
                    }
                }
            }
        }

        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After deleting old guards from the duplicate fraction that should not survive");

        // delete original guards part of the toxic fraction that should have been deleted before
        // but are not because we do not delete floating nodes without usages
        for (Node toxic : toxicFlood.getVisited()) {
            if (toxic.isAlive() && toxic instanceof GuardNode && toxic.hasNoUsages()) {
                toxic.safeDelete();
            }
        }

        graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After deleting old guards from the old toxic fraction of the loop that should not survive");

        if (newState != null) {
            lb.setStateAfter(newState);
            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After rewiring inputs for toxic fraction last state after for loop header");
        }

        // we would like to verify a schedule here but schedule verification requires dead code
        // elimination before thus we let it optimize a bit and fail later on
        postProcessLoopPhis(lb);

        return true;
    }

    /**
     * Mark proxies at loop exits from the toxic fraction.
     * </p>
     *
     * A value used only by a loop exit proxy is not reachable from the toxic fixed nodes. It must
     * move with the toxic region so the duplicated exit uses the value from the correct iteration.
     * Virtual object descriptors are not duplicated, as in {@link #rotateToxicNodeSet}. This must
     * be called before the marking of transitive inputs in the toxic fraction, since the proxies'
     * inputs must be marked too.
     * </p>
     *
     * Example:
     * <pre>
     *     while (true) {
     *         // -- start of toxic fraction --
     *         result = 9931 / (denominator + 152) ^ value;  // the value proxied at the loop exit below
     *         if (exit) {
     *             break;                                    // loop exit from the toxic fraction
     *         }
     *         // -- end of toxic fraction --
     *         if (index > 100) {                            // the counted exit we want to rotate to
     *             break;
     *         }
     *         ...
     *         value = ...;
     *         ...
     *         index++;
     *     }
     *     use(result);
     * </pre>
     */
    private static void markToxicProxies(Loop loop, ArrayList<FixedNode> toxicNodes, LoopBeginNode loopBegin, NodeFlood toxicFlood) {
        for (FixedNode toxicNode : toxicNodes) {
            if (toxicNode instanceof ControlSplitNode) {
                for (Node successor : toxicNode.successors()) {
                    if (successor instanceof LoopExitNode loopExit && loopExit.loopBegin() == loopBegin) {
                        for (ProxyNode proxy : loopExit.proxies()) {
                            ValueNode value = proxy.value();
                            if (!loopBegin.isPhiAtMerge(value) && !loop.isOutsideLoop(value) && !(value instanceof VirtualObjectNode)) {
                                toxicFlood.add(value);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Due to the way toxic phis are build during duplication it can happen that the code
     * duplication creates correct but sub-optimal phi patterns as the one below.
     *
     * <pre>
     * int phi0 = init;
     * int phi1 = init + stride;
     * while (condition) {
     * // header phi: phi1=phi(phi1,phi1+stride)
     * // header phi: phi0=phi(init,phi1)
     * }
     * </pre>
     *
     * We try to optimize them to the code below which is equivalent but simpler and faster.
     *
     * <pre>
     * int phi0 = init;
     * int phi1 = init + stride;
     * while (condition) {
     *     // header phi: phi1=phi(phi1,phi1+stride)
     *     // header phi: phi0=phi(init,phi0+stride)
     * }
     * </pre>
     */
    private static void postProcessLoopPhis(LoopBeginNode lb) {
        final StructuredGraph graph = lb.graph();
        EconomicMap<ValuePhiNode, ValuePhiNode> toReplace = null;
        for (ValuePhiNode phi1 : lb.valuePhis().snapshot()) {
            for (ValuePhiNode phi0 : lb.valuePhis().snapshot()) {
                if (phi1 != phi0) {
                    final ValueNode phi0Init = phi0.valueAt(0);
                    final ValueNode phi0Back = phi0.valueAt(1);
                    final ValueNode phi1Init = phi1.valueAt(0);
                    final ValueNode phi1Back = phi1.valueAt(1);
                    if (phi0Back == phi1) {
                        if (phi1Init instanceof AddNode || phi1Init instanceof SubNode) {
                            if (phi1Back instanceof AddNode || phi1Back instanceof SubNode) {
                                if (phi1Init.getNodeClass().equals(phi1Back.getNodeClass())) {
                                    final ValueNode phi1InitX = ((BinaryNode) phi1Init).getX();
                                    final ValueNode phi1InitY = ((BinaryNode) phi1Init).getY();
                                    final ValueNode phi1BackX = ((BinaryNode) phi1Back).getX();
                                    final ValueNode phi1BackY = ((BinaryNode) phi1Back).getY();

                                    // phi1InitY is the stride
                                    if (phi0Init == phi1InitX && phi1InitY.isConstant()) {
                                        if (phi1BackX == phi1 && phi1BackY.isConstant() && phi1BackY == phi1InitY) {
                                            /*
                                             * types need to agree, since we are post processing a
                                             * rotated loop if we look for this pattern we know the
                                             * types must be the same since the phi was created by
                                             * rotation
                                             */
                                            final NodeClass<?> phi1InitType = phi1Init.getNodeClass();
                                            final NodeClass<?> phi1BackType = phi1Back.getNodeClass();
                                            GraalError.guarantee(phi1InitType.equals(phi1BackType), "Node classes for phi post processing must match %s vs %s", phi1InitType, phi1BackType);

                                            final NodeClass<?> addSubType = phi1Init.getNodeClass();

                                            final ValueNode stride = phi1InitY;
                                            // stride is equal, also the node classes need to be the
                                            // same
                                            if (toReplace == null) {
                                                toReplace = EconomicMap.create();
                                            }
                                            ValuePhiNode newPhi = graph.addWithoutUnique(new ValuePhiNode(phi0.stamp(NodeView.DEFAULT), lb));
                                            newPhi.addInput(phi0Init);
                                            ValueNode backEdgeValue;
                                            if (addSubType == AddNode.TYPE) {
                                                backEdgeValue = new AddNode(newPhi, stride);
                                            } else if (addSubType == SubNode.TYPE) {
                                                backEdgeValue = new SubNode(newPhi, stride);
                                            } else if (addSubType == IntegerAddExactNode.TYPE) {
                                                GuardingNode guard = ((IntegerAddExactNode) phi1Init).getGuard();
                                                assert guard != null;
                                                backEdgeValue = new IntegerAddExactNode(newPhi, stride, guard);
                                            } else if (addSubType == IntegerSubExactNode.TYPE) {
                                                GuardingNode guard = ((IntegerSubExactNode) phi1Init).getGuard();
                                                assert guard != null;
                                                backEdgeValue = new IntegerSubExactNode(newPhi, stride, guard);
                                            } else {
                                                throw GraalError.shouldNotReachHere(String.format("Invalid add type %s %s", addSubType, phi1Init)); // ExcludeFromJacocoGeneratedReport
                                            }
                                            graph.addWithoutUnique(backEdgeValue);
                                            newPhi.addInput(backEdgeValue);
                                            toReplace.put(phi0, newPhi);
                                            RotatedPhisReparedAfter.increment(graph.getDebug());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (toReplace != null) {
            MapCursor<ValuePhiNode, ValuePhiNode> cursor = toReplace.getEntries();
            while (cursor.advance()) {
                final ValuePhiNode old = cursor.getKey();
                final ValuePhiNode newPhi = cursor.getValue();

                graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Before replacing phi %s with %s", old, newPhi);
                old.replaceAtUsages(newPhi);
                graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "After replacing phi %s with %s", old, newPhi);
            }
        }
    }

    /**
     * Determine if it may be beneficial to rotate this non-counted loop. The semantic of this
     * method is based on heuristics and empirical data.
     */
    private static boolean shouldRotate(StructuredGraph graph, IfNode rotationTest, NodeFlood toxicFlood, Loop elex) {
        int size = 0;
        for (Node n : toxicFlood.getVisited()) {
            size += n.estimatedNodeSize().value;
        }
        if (size > Options.LoopRotationToxicNodeSetMaxNodecost.getValue(graph.getOptions())) {
            // loop to big
            NotRotatedSize.increment(graph.getDebug());
            return false;
        }
        if (!Options.RotateNonLeafLoops.getValue(graph.getOptions())) {
            if (elex.getCFGLoop().getChildren().size() > 0) {
                // only rotate inner-most loops
                NotCountedNonLeaf.increment(graph.getDebug());
                return false;
            }
        }
        LogicNode l = rotationTest.condition();
        if (toxicFlood.getVisited().isMarked(l)) {
            // condition is toxic, can never be counted
            NotCountedCondition.increment(graph.getDebug());
            return false;
        }
        if (!(l instanceof CompareNode)) {
            // can never be counted
            return false;
        }
        /*
         * Determine if based on the shape and kind of the comparison this loop can ever be counted.
         */
        CompareNode compare = (CompareNode) l;
        Condition condition = null;
        InductionVariable iv = null;
        if (elex.isOutsideLoop(compare.getX())) {
            iv = elex.getInductionVariables().get(compare.getY());
            if (iv != null) {
                condition = compare.condition().asCondition().mirror();
            }
        } else if (elex.isOutsideLoop(compare.getY())) {
            iv = elex.getInductionVariables().get(compare.getX());
            if (iv != null) {
                condition = compare.condition().asCondition();
            }
        }
        if (condition == null) {
            return false;
        }
        return true;
    }

    /**
     * Use or lazily create a new {@link PhiNode} for the original input node before rotation.
     */
    private static PhiNode useOrCreatePhi(StructuredGraph graph, Node input, LoopBeginNode lb, Position p, EconomicMap<PhiKey, PhiNode> createdPhis, NodeFlood toxicFlood, PhiKey key,
                    LoopFragmentInside peeledFraction, LoopFragmentWhole duplicate, Node usage) {
        PhiNode phi = createdPhis.get(key);
        if (phi == null) {
            switch (p.getInputType()) {
                case Value:
                    phi = graph.addWithoutUnique(new ValuePhiNode(((ValueNode) input).stamp(NodeView.DEFAULT).unrestricted(), lb));
                    break;
                case Memory:
                    phi = graph.addWithoutUnique(new MemoryPhiNode(lb, DuplicationUtil.getLocationIdentity(input)));
                    break;
                case Guard:
                    phi = graph.addWithoutUnique(new GuardPhiNode(lb));
                    break;
                default:
                    throw GraalError.shouldNotReachHere(
                                    String.format("Unexpected edge type %s from %s to %s, [toxic nodes %s]", p.getInputType(), input, usage, toxicFlood.getVisited())); // ExcludeFromJacocoGeneratedReport
            }
            createdPhis.put(key, phi);
            Node peeledNode = peeledFraction.getDuplicatedNode(input);
            Node duplicatedNode = duplicate.getDuplicatedNode(input);
            phi.addInput((ValueNode) peeledNode);
            phi.addInput((ValueNode) duplicatedNode);
            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Created phi %s for original toxic node %s", phi, input);
        }
        return phi;
    }

    /**
     * Key object to be used inside {@link EconomicMap} to optimize phi node generation during
     * rotation.
     */
    private static class PhiKey {
        Node input;
        InputType type;

        PhiKey(Node input, InputType type) {
            super();
            this.input = input;
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PhiKey) {
                return input == ((PhiKey) obj).input && type == ((PhiKey) obj).type;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return input.hashCode() * type.hashCode();
        }
    }

    private static boolean loopQualifiesForRotation(Loop loop) {
        if (LoopUtility.excludeLoopFromOptimizer(loop)) {
            return false;
        }
        if (loop.localLoopFrequency() < Options.RotationMinLocalFrequency.getValue(loop.loopBegin().getOptions())) {
            return false;
        }
        if (loop.loopsData().getCFG().blockFor(loop.loopBegin()).getRelativeFrequency() < Options.RotationMinRelativeFrequency.getValue(loop.loopBegin().getOptions())) {
            return false;
        }
        return true;
    }

    /**
     * Determine if the {@link Loop} can be detected as {@link Loop#isCounted()} after
     * applying a loop rotation transformation. Supports code patterns listed in
     * {@link LoopRotationPhase#traverseLinearWithDiamonds(LoopBeginNode, FixedNode, Predicate, Consumer)}.
     */
    private static ArrayList<FixedNode> isCountedAfterRotation(Loop elex) {
        ArrayList<FixedNode> toxicNodes = new ArrayList<>();
        FixedNode loopStartNode = elex.loopBegin().next();
        FixedNode experiment = traverseLinearWithDiamonds(elex.loopBegin(), loopStartNode, x -> {
            if (x instanceof IfNode) {
                IfNode ifNode = (IfNode) x;
                LogicNode condition = ifNode.condition();
                if (condition instanceof CompareNode) {
                    if (elex.detectCountedLoopIf(ifNode, IfPosition.LoopStart, false) != null) {
                        return true;
                    }
                }
            }
            return false;
        }, x -> toxicNodes.add(x));
        if (experiment == null) {
            return null;
        }
        if (toxicNodes.size() == 0) {
            return null;
        }
        if (experiment instanceof IfNode) {
            if (elex.detectCountedLoopIf((IfNode) experiment, IfPosition.LoopStart, false) != null) {
                experiment.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, experiment.graph(), "Loop not head counted because of %s, subject to rotation",
                                Arrays.toString(toxicNodes.toArray()));
                return toxicNodes;
            }
        }
        // }
        return null;

    }

    /**
     * Visit and consume all nodes between {@code regionStart} and an end node that satisfy
     * {@code stop}. In between walk over
     * {@linkplain #collectSingleDiamond(ControlSplitNode, Consumer) diamonds}, sink and loop exit
     * patterns. Note that the {@code stop} node is not consumed by {@code consumer}.
     *
     *
     * Code patterns to walk over:
     * <ul>
     * <li>Diamonds:
     *
     * <pre>
     * if (condition) {
     *     fixedNodes();
     * } else {
     *     fixedNodes();
     * } // merge
     * </pre>
     *
     * </li>
     *
     * <li>If-Sink Patterns:
     *
     * <pre>
     * if(condition) {
     *   sink; // return / deopt / throw;
     * }
     * </pre>
     *
     * </li>
     *
     * <li>Loop Exit Patterns:
     *
     * <pre>
     * if (condition) {
     *     break;
     * }
     * </pre>
     *
     * *</li>
     *
     * </ul>
     *
     * @return a fixed node that satisfies {@code stop} or {@code null} otherwise
     */
    private static FixedNode traverseLinearWithDiamonds(LoopBeginNode lb, FixedNode regionStart, Predicate<? super FixedNode> stop,
                    Consumer<? super FixedNode> consumer) {
        FixedNode fixed = regionStart;
        while (true) { // TERMINATION ARGUMENT: processing next nodes until an exit criteria is hit
            CompilationAlarm.checkProgress(lb.graph());
            assert fixed != null;
            if (stop.test(fixed)) {
                return fixed;
            }
            if (fixed instanceof FixedWithNextNode) {
                consumer.accept(fixed);
                fixed = ((FixedWithNextNode) fixed).next();
            } else if (fixed instanceof IfNode) {
                consumer.accept(fixed);
                FixedNode next = null;
                for (Node successor : fixed.successors()) {
                    AbstractBeginNode begin = (AbstractBeginNode) successor;
                    if (!(begin instanceof LoopExitNode) && begin.next() instanceof ControlSinkNode) {
                        consumer.accept(begin);
                        consumer.accept(begin.next());
                    } else if (begin instanceof LoopExitNode && ((LoopExitNode) begin).loopBegin() == lb) {
                        // nothing to do, do not include lex node
                    } else {
                        // diamond or unstructured but not a valid <if()sink else sth> structure
                        if (next != null) {
                            next = null;
                            break;
                        }
                        next = begin;
                    }
                }
                if (next == null) {
                    MergeNode diamond = collectSingleDiamond((ControlSplitNode) fixed, x -> {
                        consumer.accept(x);
                    });
                    if (diamond == null) {
                        // unstructured diamond found, thus we could not collect the result
                        return null;
                    } else {
                        next = diamond.next();
                    }
                }
                fixed = next;
            } else {
                break;
            }
        }
        return null;
    }

    /**
     * Visit and process a single control flow diamond. A control flow diamond is code of the form:
     *
     * <pre>
     * if (condition) {
     *     fixedNodes();
     * } else {
     *     fixedNodes();
     * } // merge
     * </pre>
     */
    private static MergeNode collectSingleDiamond(ControlSplitNode split, Consumer<FixedNode> action) {
        if (split instanceof IfNode) {
            EconomicSet<FixedNode> fixedNodes = EconomicSet.create();
            fixedNodes.add(split);

            FixedNode trueSucc = ((IfNode) split).trueSuccessor();
            FixedNode falseSucc = ((IfNode) split).falseSuccessor();

            // iterate the true successor to the next merge
            while (trueSucc instanceof FixedWithNextNode) {
                fixedNodes.add(trueSucc);
                trueSucc = ((FixedWithNextNode) trueSucc).next();
            }
            if (!(trueSucc instanceof EndNode)) {
                // complex control flow -> abort
                return null;
            }
            fixedNodes.add(trueSucc);

            // iterate the false successor to the next merge
            while (falseSucc instanceof FixedWithNextNode) {
                fixedNodes.add(falseSucc);
                falseSucc = ((FixedWithNextNode) falseSucc).next();
            }
            if (!(falseSucc instanceof EndNode)) {
                // complex control flow -> abort
                return null;
            }
            fixedNodes.add(falseSucc);
            EndNode tEnd = (EndNode) trueSucc;
            EndNode fEnd = (EndNode) falseSucc;
            if (tEnd.merge() != fEnd.merge() || !(tEnd.merge() instanceof MergeNode)) {
                // different merges, abort
                return null;
            }
            fixedNodes.add(tEnd.merge());
            fixedNodes.forEach(action);
            return (MergeNode) tEnd.merge();
        }
        return null;
    }

}
