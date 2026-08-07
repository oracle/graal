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
package jdk.graal.compiler.duplication.util;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static jdk.vm.ci.meta.TriState.UNKNOWN;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.duplication.phases.simulation.HighTierDuplicationSimulationPhase;
import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ValueNumberable;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.JavaWriteNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.extended.UnsafeMemoryStoreNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.MemoryEdgeProxy;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.vm.ci.meta.TriState;

public class DuplicationUtil {

    /**
     * Hard stop for the guard-phi normalization worklist so a malformed or unexpectedly cyclic
     * graph shape fails with a bailout instead of looping forever.
     */
    private static final int SPLIT_REGULAR_MERGE_GUARD_INPUTS_WORKLIST_LIMIT = 10_000;

    public static class Options {
        //@formatter:off
        @Option(help = "", type = OptionType.Debug)
        public static final OptionKey<Boolean> VerifyDuplicationOperations = new OptionKey<>(false);
        //@formatter:on
    }

    @NodeInfo
    static class DummyMergeNode extends AbstractMergeNode {
        public static final NodeClass<DummyMergeNode> TYPE = NodeClass.create(DummyMergeNode.class);
        protected final String name;

        protected DummyMergeNode(String name) {
            super(TYPE);
            this.name = name;
        }

        @Override
        public String toString(Verbosity verbosity) {
            return name;
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static class DummyNullValueNode extends FloatingNode implements GuardingNode {
        public static final NodeClass<DummyNullValueNode> TYPE = NodeClass.create(DummyNullValueNode.class);

        protected DummyNullValueNode(Stamp stamp) {
            super(TYPE, stamp);
        }
    }

    private static final AbstractMergeNode UNUSED = new DummyMergeNode("UNUSED");
    private static final AbstractMergeNode ABOVE_DUPLICATED = new DummyMergeNode("ABOVE_DUPLICATED");

    private final NodeBitMap aboveBound;
    private final NodeBitMap belowBound;
    private final Deque<Node> worklist;
    private final EconomicMap<AbstractMergeNode, AbstractMergeNode> classificationCache;
    private final EconomicSet<Node> duplicatedNodes;
    private final StructuredGraph graph;
    private boolean firstOperation;

    private final SimplifierTool tool;

    public DuplicationUtil(StructuredGraph graph, SimplifierTool tool) {
        this.aboveBound = graph.createNodeBitMap();
        this.belowBound = graph.createNodeBitMap();
        this.worklist = new ArrayDeque<>();
        this.classificationCache = EconomicMap.create(Equivalence.IDENTITY);
        this.duplicatedNodes = EconomicSet.create(Equivalence.IDENTITY);
        this.graph = graph;
        this.firstOperation = true;
        this.tool = tool;
    }

    public void duplicate(AbstractMergeNode merge, DuplicationRegion region, CanonicalizerPhase canonicalizer, CoreProviders context) {
        verifyDuplicationValid(merge.graph(), region);
        if (firstOperation) {
            firstOperation = false;
        } else {
            aboveBound.clearAll();
            belowBound.clearAll();
            classificationCache.clear();
            duplicatedNodes.clear();
            assert worklist.isEmpty() : "Worklist must be empty at the end but is " + worklist;
        }
        new DuplicationOperation(merge, region).duplicate(canonicalizer, context);
    }

    /**
     * Replaces guard-phi inputs with predecessor-specific replacement guard phis for
     * non-duplicatable regular-merge patterns before duplication starts.
     *
     * <p>
     * Duplication works on a fixed-node region. If a merge inside that region owns ordinary value
     * or memory phis, duplication repairs those phis as part of the normal region rewrite. Phis
     * whose owning merge stays outside the region normally stay outside the duplicated node set,
     * which is also fine for ordinary phis because they still describe values at their own merge.
     *
     * <p>
     * Guard phis are different. A guard phi can stay outside the duplicated region even though one
     * of its inputs is a regular merge from inside that region and guarded floating nodes from that
     * phi are still consumed by duplicated fixed nodes.
     *
     * <pre>
     * before duplication:
     *     loop_header(..., outer_guard(entry_guard, body_merge))
     *         ...
     *         body_merge
     *           /     \
     *      other    duplicated_path
     *                  guarded_value = op(..., floatingGuard = outer_guard)
     *                  store(guarded_value)
     *                  loop_end
     * </pre>
     *
     * <p>
     * If duplication sees that shape directly, the outer guard phi stays outside the duplicated
     * node set and duplication has to introduce the predecessor-specific split guard phi only
     * later, after control has already been split into predecessor-specific paths. To address that
     * duplication shortcoming, this transformation splits the guard phi before duplication so
     * duplication sees only guard-phi inputs it can handle directly.
     *
     * <pre>
     * before:
     *                     If
     *                    /  \
     *               Begin    Begin
     *                   |    |
     *                   A    SomeNodeWithGuardSemantics
     *                   |    |
     *                   B    Z
     *                   |    |
     *                 End    End
     *                  \      /
     *                   body_merge
     *                       |
     *               GuardPhi(loop_header, ..., body_merge)
     *                       |
     *               GuardedFloatingNode
     *
     * after rewrite, before duplication:
     *                     If
     *                    /  \
     *               Begin    Begin
     *                   |    |
     *                   A    SomeNodeWithGuardSemantics
     *                   |    |
     *                   B    Z
     *                   |    |
     *                 End    End
     *                  \      /
     *                   body_merge
     *                       |
     * split_guard_phi(Begin, SomeNodeWithGuardSemantics)
     *                    |
     *           GuardPhi(loop_header, ..., split_guard_phi)
     *                    |
     *           GuardedFloatingNode
     * </pre>
     *
     * <p>
     * This helper creates that split guard phi first by replacing the regular-merge input with an
     * explicit predecessor-specific guard phi, so duplication sees the guard dependency in the same
     * form that later scheduling and classification expect.
     *
     * <p>
     * The rewrite is intentionally narrow:
     * <ul>
     * <li>Only guard-phi inputs that are regular merges are rewritten.</li>
     * <li>The pass runs to a fixpoint because one predecessor begin can itself be another regular
     * merge that needs the same rewrite.</li>
     * </ul>
     *
     * @return the number of rewritten guard-phi inputs
     */
    public static int splitRegularMergeGuardInputs(StructuredGraph graph) {
        EconomicMap<MergeNode, GuardPhiNode> guardPhisByMerge = EconomicMap.create(Equivalence.IDENTITY);
        ArrayDeque<GuardPhiNode> worklist = new ArrayDeque<>();
        for (GuardPhiNode guardPhi : graph.getNodes().filter(GuardPhiNode.class)) {
            worklist.addLast(guardPhi);
        }
        int replacements = 0;
        int processedGuardPhis = 0;
        while (!worklist.isEmpty()) {
            processedGuardPhis++;
            if (processedGuardPhis > SPLIT_REGULAR_MERGE_GUARD_INPUTS_WORKLIST_LIMIT) {
                throw new PermanentBailoutException("splitRegularMergeGuardInputs exceeded worklist limit %d", SPLIT_REGULAR_MERGE_GUARD_INPUTS_WORKLIST_LIMIT);
            }
            GuardPhiNode guardPhi = worklist.removeFirst();
            for (int i = 0; i < guardPhi.valueCount(); i++) {
                MergeNode mergeInput = getRegularMergeGuardInput(guardPhi, i);
                if (mergeInput == null) {
                    continue;
                }
                GuardPhiNode replacementPhi = getOrCreateSplitGuardPhi(graph, mergeInput, guardPhisByMerge);
                if (guardPhi.valueAt(i) != replacementPhi) {
                    guardPhi.setValueAt(i, replacementPhi);
                    replacements++;
                }
                worklist.addLast(replacementPhi);
            }
        }
        return replacements;
    }

    /**
     * Returns the regular merge currently used as a guard-phi input when that input still needs
     * rewriting, or {@code null} when the input already names the guard phi's own merge or is not a
     * regular merge at all.
     */
    private static MergeNode getRegularMergeGuardInput(GuardPhiNode guardPhi, int inputIndex) {
        ValueNode input = guardPhi.valueAt(inputIndex);
        if (input instanceof MergeNode mergeInput && mergeInput != guardPhi.merge()) {
            return mergeInput;
        }
        return null;
    }

    /**
     * Creates or reuses the predecessor-specific guard phi that replaces one escaped regular merge
     * input.
     */
    private static GuardPhiNode getOrCreateSplitGuardPhi(StructuredGraph graph, MergeNode mergeInput, EconomicMap<MergeNode, GuardPhiNode> guardPhisByMerge) {
        GuardPhiNode replacementPhi = guardPhisByMerge.get(mergeInput);
        if (replacementPhi != null) {
            return replacementPhi;
        }
        replacementPhi = graph.addWithoutUnique(new GuardPhiNode(mergeInput));
        for (int predIndex = 0; predIndex < mergeInput.forwardEndCount(); predIndex++) {
            EndNode end = mergeInput.forwardEndAt(predIndex);
            AbstractBeginNode prevBegin = AbstractBeginNode.prevBegin(end);
            GraalError.guarantee(prevBegin != null, "expected a begin for merge input %s predecessor %s", mergeInput, end);
            replacementPhi.addInput(prevBegin);
        }
        guardPhisByMerge.put(mergeInput, replacementPhi);
        return replacementPhi;
    }

    public static LocationIdentity getLocationIdentity(Node node) {
        if (node instanceof MemoryPhiNode) {
            return ((MemoryPhiNode) node).getLocationIdentity();
        } else if (node instanceof MemoryAccess) {
            return ((MemoryAccess) node).getLocationIdentity();
        } else if (node instanceof MemoryEdgeProxy) {
            return ((MemoryEdgeProxy) node).getLocationIdentity();
        } else if (MemoryKill.isSingleMemoryKill(node)) {
            return ((SingleMemoryKill) node).getKilledLocationIdentity();
        } else if (MemoryKill.isMultiMemoryKill(node)) {
            return LocationIdentity.any();
        } else {
            throw GraalError.shouldNotReachHere("unexpected node as part of memory graph: " + node); // ExcludeFromJacocoGeneratedReport
        }
    }

    public abstract static class DuplicationRegion {

        private final EndNode duplicatedEnd;
        protected final ArrayList<FixedNode> fixedNodes = new ArrayList<>();
        protected FrameState stateAfter;

        protected DuplicationRegion(EndNode duplicatedEnd) {
            this.duplicatedEnd = duplicatedEnd;
        }

        public Collection<FixedNode> getFixedNodes() {
            return fixedNodes;
        }

        public FrameState getStateAfter() {
            return stateAfter;
        }

        public abstract void fixPhis(UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements);

        public abstract void canonicalize(SimplifierTool tool, boolean emptyDuplication, UnmodifiableEconomicMap<Node, Node> duplicates);

        public abstract void collectReplacements(EconomicMap<Node, Node> replacements);

        public abstract boolean isEndMerge(Node node);

        public abstract int getEndMergePhiIndex(AbstractMergeNode merge);

        public EndNode getDuplicationEnd() {
            return duplicatedEnd;
        }

        protected void fixPhisForMerge(AbstractMergeNode merge, UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements) {
            for (PhiNode phi : merge.phis()) {
                ValueNode originalValue = phi.valueAt(getEndMergePhiIndex(merge));
                ValueNode duplicate = (ValueNode) duplicates.get(originalValue);
                if (duplicate != null) {
                    phi.addInput(duplicate);
                } else if (replacements.containsKey(originalValue)) {
                    phi.addInput((ValueNode) replacements.get(originalValue));
                } else {
                    phi.addInput(originalValue);
                }
                phi.inferStamp();
            }
        }

        /**
         * Do any additional rewiring required.
         */
        @SuppressWarnings("unused")
        protected void rewire(UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements) {
        }

        private static String classString(Class<? extends DuplicationRegion> c) {
            return c.getSimpleName();
        }

        protected Node duplicationTarget() {
            return fixedNodes.get(fixedNodes.size() - 1);
        }

        @Override
        public String toString() {
            Node target = duplicationTarget();
            String targetString = target == null ? "" : target.toString();
            return String.format("%s region from %s -> %s", classString(this.getClass()), duplicatedEnd.merge(), targetString);
        }

    }

    public static TriState queryDominatingDecision(LogicNode condition, FixedNode fixed) {
        FixedNode current = fixed;
        do {
            Node predecessor = current.predecessor();
            if (predecessor instanceof IfNode) {
                if (((IfNode) predecessor).condition() == condition) {
                    return TriState.get(current == ((IfNode) predecessor).trueSuccessor());
                }
            }
            current = (FixedNode) predecessor;
        } while (current != null);
        return TriState.UNKNOWN;
    }

    public static void traverseLinear(FixedNode regionStart, FixedNode regionEnd, Consumer<? super FixedNode> consumer) {
        traverseLinear(regionStart, regionEnd, node -> {
            consumer.accept(node);
            return true;
        }, consumer);
    }

    public static boolean traverseLinear(FixedNode regionStart, FixedNode regionEnd, Predicate<? super FixedNode> predicate, Consumer<? super FixedNode> sideConsumer) {
        FixedNode fixed = regionStart;
        while (true) { // TERMINATION ARGUMENT: processing next nodes with a given exit criteria
            CompilationAlarm.checkProgress(regionStart.graph());
            assert fixed != null;
            if (!predicate.test(fixed)) {
                return false;
            }
            if (fixed == regionEnd) {
                break;
            }
            if (fixed instanceof FixedWithNextNode) {
                fixed = ((FixedWithNextNode) fixed).next();
            } else if (fixed instanceof ControlSplitNode) {
                FixedNode next = null;
                for (Node successor : fixed.successors()) {
                    AbstractBeginNode begin = (AbstractBeginNode) successor;
                    if (begin.next() instanceof ControlSinkNode) {
                        if (sideConsumer != null) {
                            sideConsumer.accept(begin);
                            sideConsumer.accept(begin.next());
                        }
                    } else {
                        assert next == null;
                        next = begin;
                    }
                }
                fixed = next;
            }
        }
        return true;
    }

    public static FrameState findLastFrameState(FixedNode regionEnd) {
        for (FixedNode fixed : GraphUtil.predecessorIterable(regionEnd)) {
            if (fixed instanceof StateSplit) {
                StateSplit stateSplit = (StateSplit) fixed;
                if (stateSplit.stateAfter() != null) {
                    return stateSplit.stateAfter();
                }
            }
        }
        return null;
    }

    public static class LinearRegion extends DuplicationRegion {

        protected final AbstractMergeNode mergeAfter;
        protected final EndNode endAfter;
        protected final Deque<AbstractMergeNode> queue;

        public LinearRegion(AbstractMergeNode regionStart, FixedWithNextNode regionEnd, Deque<AbstractMergeNode> queue, EndNode duplicatedEnd) {
            super(duplicatedEnd);
            this.queue = queue;
            StructuredGraph graph = regionStart.graph();

            stateAfter = findLastFrameState(regionEnd);
            traverseLinear(regionStart.next(), regionEnd.next(), node -> fixedNodes.add(node));
            fixedNodes.remove(fixedNodes.size() - 1);

            endAfter = graph.add(new EndNode());
            mergeAfter = graph.add(new MergeNode());
            if (stateAfter != null) {
                if (stateAfter == regionStart.stateAfter() && regionStart.forwardEndCount() == 2) {
                    mergeAfter.setStateAfter(stateAfter);
                    regionStart.setStateAfter(null);
                } else {
                    mergeAfter.setStateAfter((FrameState) stateAfter.copyWithInputs());
                }
            }
            FixedNode next = regionEnd.next();
            regionEnd.setNext(endAfter);
            mergeAfter.setNext(next);
            mergeAfter.addForwardEnd(endAfter);

            mergeAfter.addForwardEnd(graph.add(new EndNode()));

            assert mergeAfter.forwardEndIndex(endAfter) == 0 : mergeAfter + " " + endAfter;
        }

        @Override
        public void collectReplacements(EconomicMap<Node, Node> replacements) {
            replacements.put(mergeAfter.forwardEndAt(0), mergeAfter.forwardEndAt(1));
        }

        @Override
        public void fixPhis(UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements) {
            fixPhisForMerge(mergeAfter, duplicates, replacements);
        }

        @Override
        public void canonicalize(SimplifierTool tool, boolean emptyDuplication, UnmodifiableEconomicMap<Node, Node> duplicates) {
            AbstractMergeNode successorMerge = mergeAfter.next() instanceof AbstractMergeNode ? (AbstractMergeNode) mergeAfter.next() : null;
            mergeAfter.simplify(tool);
            if (queue != null) {
                if (successorMerge != null && !mergeAfter.isAlive()) {
                    queue.addFirst(successorMerge);
                }
                if (mergeAfter.isAlive() && !emptyDuplication) {
                    queue.addFirst(mergeAfter);
                }
            }
        }

        @Override
        public boolean isEndMerge(Node node) {
            return node == mergeAfter;
        }

        @Override
        public int getEndMergePhiIndex(AbstractMergeNode merge) {
            assert isEndMerge(merge) : "Must be end merge " + merge + " but merge after =" + mergeAfter;
            return 0;
        }

    }

    public static class SplitRegion extends DuplicationRegion {

        private final AbstractMergeNode[] mergesAfter;
        private final Deque<AbstractMergeNode> queue;
        private final FixedNode regionEnd;

        public SplitRegion(AbstractMergeNode regionStart, FixedNode regionEnd, Deque<AbstractMergeNode> queue, EndNode duplicatedEnd) {
            super(duplicatedEnd);
            this.regionEnd = regionEnd;
            this.queue = queue;
            assert regionEnd instanceof ControlSplitNode && !(regionEnd instanceof WithExceptionNode) : regionEnd;

            stateAfter = DuplicationUtil.findLastFrameState(regionEnd);
            DuplicationUtil.traverseLinear(regionStart.next(), regionEnd, node -> fixedNodes.add(node));

            boolean reuseStateAfter = stateAfter == regionStart.stateAfter() && regionStart.forwardEndCount() == 2;

            StructuredGraph graph = regionStart.graph();
            int i = 0;
            List<Node> successors = regionEnd.successors().snapshot();
            mergesAfter = new AbstractMergeNode[successors.size()];
            for (Node successor : successors) {
                FixedNode mergeSuccessor;
                FixedWithNextNode mergePredecessor;
                // patch in a BeginNode before LoopExitNodes -> fewer special cases later on
                if (successor instanceof LoopExitNode) {
                    AbstractBeginNode newBegin = graph.add(new BeginNode());
                    newBegin.setNodeSourcePosition(successor.getNodeSourcePosition());
                    successor.replaceAtPredecessor(newBegin);
                    newBegin.setNext((FixedNode) successor);
                    mergePredecessor = newBegin;
                } else {
                    mergePredecessor = (FixedWithNextNode) successor;
                }
                fixedNodes.add(mergePredecessor);
                mergeSuccessor = mergePredecessor.next();

                AbstractMergeNode mergeAfter = graph.add(new MergeNode());
                EndNode endAfter = graph.add(new EndNode());

                if (stateAfter != null) {
                    if (reuseStateAfter) {
                        mergeAfter.setStateAfter(stateAfter);
                        regionStart.setStateAfter(null);
                        reuseStateAfter = false;
                    } else {
                        mergeAfter.setStateAfter((FrameState) stateAfter.copyWithInputs());
                    }
                }
                mergeSuccessor.predecessor().replaceFirstSuccessor(mergeSuccessor, endAfter);
                mergeAfter.setNext(mergeSuccessor);
                mergeAfter.addForwardEnd(endAfter);

                EndNode newEndAfter = graph.add(new EndNode());
                mergeAfter.addForwardEnd(newEndAfter);

                mergesAfter[i] = mergeAfter;
                assert mergeAfter.forwardEndIndex(endAfter) == 0 : mergeAfter + " " + endAfter;
                assert mergeAfter.forwardEndIndex(newEndAfter) == 1 : mergeAfter + " " + newEndAfter;
                i++;
            }
        }

        @Override
        public void collectReplacements(EconomicMap<Node, Node> replacements) {
            for (AbstractMergeNode merge : mergesAfter) {
                replacements.put(merge.forwardEndAt(0), merge.forwardEndAt(1));
            }
        }

        @Override
        public void fixPhis(UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements) {
            for (AbstractMergeNode mergeAfter : mergesAfter) {
                fixPhisForMerge(mergeAfter, duplicates, replacements);
            }
        }

        @Override
        public void canonicalize(SimplifierTool tool, boolean emptyDuplication, UnmodifiableEconomicMap<Node, Node> duplicates) {
            for (AbstractMergeNode mergeAfter : mergesAfter) {
                AbstractMergeNode successorMerge = mergeAfter.next() instanceof AbstractMergeNode ? (AbstractMergeNode) mergeAfter.next() : null;
                mergeAfter.simplify(tool);
                if (queue != null) {
                    if (successorMerge != null && !mergeAfter.isAlive()) {
                        queue.addFirst(successorMerge);
                    }
                    if (mergeAfter.isAlive() && !emptyDuplication) {
                        queue.addFirst(mergeAfter);
                    }
                }
            }
            /*
             * Perform a limited form of conditional elimination. In order to eliminate redundant
             * checks that have been identified before duplication we look for dominating checks
             * with the same conditions. In case we find one we set the duplicated check to have a
             * constant condition input. The canonicalizer will pick it up and eliminate it.
             * However, we have to handle guarded nodes on the checks's successor as they need to
             * point to the correct condition in order to be valid.
             */
            if (regionEnd instanceof IfNode) {
                IfNode ifNode = (IfNode) regionEnd;
                if (ifNode.condition().getUsageCount() > 1) {
                    TriState decision = queryDominatingDecision(ifNode.condition(), regionEnd);
                    if (decision != TriState.UNKNOWN) {
                        /*
                         * Update guarded nodes to point to a dominating control split with the same
                         * condition, the immediate dominating begin node might not have a
                         * predecessor with the correct condition.
                         */
                        rewireGuards(decision, ifNode);
                        ifNode.setCondition(LogicConstantNode.forBoolean(decision == TriState.TRUE, ifNode.graph()));
                    }
                }

                IfNode ifDuplicate = (IfNode) duplicates.get(regionEnd);
                if (ifDuplicate.condition().getUsageCount() > 1) {
                    TriState decision = queryDominatingDecision(ifDuplicate.condition(), ifDuplicate);
                    if (decision != TriState.UNKNOWN) {
                        /*
                         * Update guarded nodes to point to a dominating control split with the same
                         * condition, the immediate dominating begin node might not have a
                         * predecessor with the correct condition.
                         */
                        rewireGuards(decision, ifDuplicate);
                        ifDuplicate.setCondition(LogicConstantNode.forBoolean(decision == TriState.TRUE, ifDuplicate.graph()));
                    }
                }
            }
        }

        @Override
        public boolean isEndMerge(Node node) {
            for (AbstractMergeNode merge : mergesAfter) {
                if (node == merge) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getEndMergePhiIndex(AbstractMergeNode merge) {
            assert isEndMerge(merge) : "Merge " + merge + " must be one of " + Arrays.toString(mergesAfter);
            return 0;
        }
    }

    public static void rewireGuards(TriState decision, IfNode toBeDeleted) {
        assert decision.isKnown() : "Known!=" + decision;
        final LogicNode safeCondition = toBeDeleted.condition();
        AbstractBeginNode visitedSuccessor = null;
        ArrayList<LoopExitNode> loopExits = null;
        AbstractBeginNode survivingSuccessor = decision == TriState.TRUE ? toBeDeleted.trueSuccessor() : toBeDeleted.falseSuccessor();
        if (survivingSuccessor.hasUsages()) {
            final boolean proxyGuards = toBeDeleted.graph().isBeforeStage(StageFlag.VALUE_PROXY_REMOVAL);
            for (FixedNode fixed : GraphUtil.predecessorIterable(toBeDeleted)) {
                if (proxyGuards && fixed instanceof LoopExitNode) {
                    if (loopExits == null) {
                        loopExits = new ArrayList<>();
                    }
                    loopExits.add((LoopExitNode) fixed);
                }
                if (fixed instanceof AbstractBeginNode) {
                    visitedSuccessor = (AbstractBeginNode) fixed;
                }
                if (fixed instanceof IfNode && ((IfNode) fixed).condition() == safeCondition && fixed != toBeDeleted) {
                    assert visitedSuccessor != null : "Must have a valid begin";
                    GuardingNode newGuardingnode = visitedSuccessor;
                    if (loopExits != null) {
                        for (int i = loopExits.size() - 1; i >= 0; i--) {
                            LoopExitNode lex = loopExits.get(i);
                            if (lex != newGuardingnode) {
                                newGuardingnode = toBeDeleted.graph().unique(new GuardProxyNode(newGuardingnode, loopExits.get(i)));
                            }
                        }
                    }
                    survivingSuccessor.replaceAtUsages((Node) newGuardingnode, InputType.Guard);
                    return;
                }
            }
            GraalError.shouldNotReachHere(String.format("Must find correct dominating split for condition %s.", safeCondition)); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static class SinkRegion extends DuplicationRegion {

        public SinkRegion(AbstractMergeNode regionStart, FixedNode regionEnd, EndNode duplicatedEnd) {
            super(duplicatedEnd);
            assert regionEnd instanceof ControlSinkNode : regionEnd;

            stateAfter = DuplicationUtil.findLastFrameState(regionEnd);
            DuplicationUtil.traverseLinear(regionStart.next(), regionEnd, node -> fixedNodes.add(node));

            if (stateAfter != null && stateAfter == regionStart.stateAfter() && regionStart.forwardEndCount() == 2) {
                regionStart.setStateAfter(null);
                if (stateAfter.usages().isEmpty()) {
                    GraphUtil.killWithUnusedFloatingInputs(stateAfter);
                }
                stateAfter = null;
            }
        }

        @Override
        public void collectReplacements(EconomicMap<Node, Node> replacements) {
            // nothing to do
        }

        @Override
        public void fixPhis(UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements) {
            // nothing to do
        }

        @Override
        public void canonicalize(SimplifierTool tool, boolean emptyDuplication, UnmodifiableEconomicMap<Node, Node> duplicates) {
            // nothing to do
        }

        @Override
        public boolean isEndMerge(Node node) {
            throw GraalError.shouldNotReachHere("asking for " + node); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public int getEndMergePhiIndex(AbstractMergeNode merge) {
            throw GraalError.shouldNotReachHere("asking for " + merge); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static final TimerKey timeCreatePhi = DebugContext.timer("DuplicationUtil_Time_CreatePhi");
    private static final TimerKey timerProcessUsages = DebugContext.timer("DuplicationUtil_Time_ProcessUsages");
    private static final TimerKey timeClassifyFloating = DebugContext.timer("DuplicationUtil_Time_ClassifyFloating");
    private static final TimerKey timeClassifyFixed = DebugContext.timer("DuplicationUtil_Time_ClassifyFixed");
    private static final TimerKey timeClassifyVirtual = DebugContext.timer("DuplicationUtil_Time_ClassifyVirtual");

    /**
     * This class encapsulates one duplication operation on a specific {@link AbstractMergeNode}.
     */
    private class DuplicationOperation {

        private final AbstractMergeNode merge;

        private final int duplicatedEndIndex;
        private final DuplicationRegion region;

        private final EconomicMap<Node, Node> replacements = EconomicMap.create(Equivalence.IDENTITY);

        DuplicationOperation(AbstractMergeNode merge, DuplicationRegion region) {
            this.merge = merge;
            this.region = region;
            this.duplicatedEndIndex = merge.forwardEndIndex(region.getDuplicationEnd());
        }

        /**
         * Performs the actual duplication:
         * <ul>
         * <li>Creates a new {@link ValueAnchorNode} at the beginning of the duplicated area, and
         * transfers all dependencies from the merge to this anchor.</li>
         * <li>Determines the set of fixed nodes to be duplicated.</li>
         * <li>Creates the new merge at the bottom of the duplicated area.</li>
         * <li>Determines the complete set of duplicated nodes.</li>
         * <li>Performs the actual duplication.</li>
         * </ul>
         *
         * @param canonicalizer
         */
        @SuppressWarnings("try")
        private void duplicate(CanonicalizerPhase canonicalizer, CoreProviders context) {
            DebugContext debug = merge.getDebug();
            try (Indent indent = debug.logAndIndent(3, "duplication at merge %s / end %s", merge, region.getDuplicationEnd())) {

                region.collectReplacements(replacements);

                if (merge.stateAfter() != null && merge.stateAfter().getUsageCount() > 1) {
                    merge.setStateAfter((FrameState) merge.stateAfter().copyWithInputs());
                }

                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before expand at %s", merge);
                buildDuplicatedNodeSet();
                expandDuplicated();
                DummyNullValueNode nullValue = null;
                for (PhiNode phi : merge.phis()) {
                    ValueNode value = phi.valueAt(duplicatedEndIndex);
                    if (value == null && nullValue == null) {
                        nullValue = graph.addWithoutUnique(new DummyNullValueNode(phi.stamp(NodeView.DEFAULT)));
                    }
                    replacements.put(phi, value == null ? nullValue : value);
                }
                ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
                if (!(merge instanceof LoopBeginNode)) {
                    replacements.put(merge, anchor);
                }

                debug.log(DebugContext.VERY_DETAILED_LEVEL, "Duplicated node set: %s", duplicatedNodes);
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "Replacements: %s", replacements);
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before addDuplicates at %s", merge);
                UnmodifiableEconomicMap<Node, Node> duplicates = graph.addDuplicates(duplicatedNodes, graph, duplicatedNodes.size(), replacements);

                debug.log(DebugContext.VERY_DETAILED_LEVEL, "Duplicates: %s", duplicates);
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After addDuplicates at %s", merge);
                anchor.setNext((FixedNode) getDuplicateOrReplacement(merge.next(), duplicates));
                region.fixPhis(duplicates, replacements);
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After fixPhis");

                // move dependencies on the ValueAnchorNode to the previous BeginNode
                AbstractBeginNode prevBegin = AbstractBeginNode.prevBegin((FixedNode) region.getDuplicationEnd().predecessor());
                anchor.replaceAtUsages(prevBegin);
                // re-wire the duplicated ValueAnchorNode to the predecessor of the
                // corresponding
                // EndNode
                FixedNode next = anchor.next();
                anchor.setNext(null);
                ((FixedWithNextNode) region.getDuplicationEnd().predecessor()).setNext(next);
                anchor.safeDelete();

                if (nullValue != null) {
                    nullValue.replaceAtUsages(null);
                    nullValue.safeDelete();
                }

                region.rewire(duplicates, replacements);
                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After re-wire at %s", merge);

                GraphUtil.killCFG(region.getDuplicationEnd());

                debug.dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After killcfg at %s", merge);

                for (Node node : duplicatedNodes) {
                    if (node.isAlive()) {
                        Node duplicate = duplicates.get(node);
                        if (duplicate.isAlive() && duplicate.usages().isEmpty() && GraphUtil.isFloatingNode(duplicate)) {
                            GraphUtil.killWithUnusedFloatingInputs(duplicate);
                        }
                        if (node.isAlive() && node.usages().isEmpty() && GraphUtil.isFloatingNode(node)) {
                            GraphUtil.killWithUnusedFloatingInputs(node);
                        }
                    }
                }
                // verify a valid graph after the duplication
                OptionValues options = graph.getOptions();
                if (Options.VerifyDuplicationOperations.getValue(options)) {
                    verifyDuplication(graph);
                }
                region.canonicalize(tool, duplicatedNodes.isEmpty(), duplicates);
                // verify a valid graph after manual cleanups
                if (Options.VerifyDuplicationOperations.getValue(options)) {
                    verifyDuplication(graph);
                }
                if (canonicalizer != null) {
                    EconomicSet<Node> changedNodes = EconomicSet.create(Equivalence.IDENTITY);
                    changedNodes.addAll(duplicates.getValues());
                    changedNodes.addAll(duplicatedNodes);
                    for (PhiNode phiNode : merge.phis()) {
                        changedNodes.add(phiNode);
                    }
                    canonicalizer.applyIncremental(graph, context, changedNodes);
                }
                debug.dump(DebugContext.DETAILED_LEVEL, graph, "After duplication at %s of end %s", merge, region.duplicatedEnd);
            }
        }

        public ValueNode getDuplicateOrReplacement(ValueNode original, UnmodifiableEconomicMap<Node, Node> duplicates) {
            Node result = duplicates.get(original);
            if (result == null) {
                result = replacements.get(original);
            }
            return (ValueNode) result;
        }

        /**
         * Given a set of fixed nodes, this method determines the set of fixed and floating nodes
         * that needs to be duplicated, i.e., all nodes that due to data flow and other dependencies
         * needs to be duplicated.
         */
        private void buildDuplicatedNodeSet() {
            assert aboveBound.isEmpty() && belowBound.isEmpty() && worklist.isEmpty() && duplicatedNodes.isEmpty() : "All must be empty initially " + aboveBound + " " + belowBound + " " + worklist +
                            " " + duplicatedNodes;
            aboveBound.grow();
            belowBound.grow();

            List<Node> mergeUsages = merge.usages().snapshot();
            List<PhiNode> mergePhis = merge.phis().snapshot();

            /*
             * Build the set of nodes that have (transitive) usages within the duplicatedNodes. This
             * is achieved by iterating all nodes that are reachable via inputs from the fixed
             * nodes.
             */
            final Collection<FixedNode> fixedNodes = region.getFixedNodes();
            aboveBound.markAll(fixedNodes);
            worklist.addAll(fixedNodes);

            VirtualState.NodePositionClosure<Node> aboveClosure = new VirtualState.NodePositionClosure<>() {

                @Override
                public void apply(Node from, Position p) {
                    Node input = p.get(from);
                    if (input instanceof PhiNode && !fixedNodes.contains(((PhiNode) input).merge())) {
                        // stop iterating: phis belonging to outside merges are known to be outside.
                    } else if (input instanceof FixedNode) {
                        // stop iterating: fixed nodes within the given set are traversal roots
                        // anyway, and all other fixed nodes are known to be outside.
                    } else if (!aboveBound.isMarked(input)) {
                        worklist.add(input);
                        aboveBound.mark(input);
                    }
                }
            };

            /*
             * Everything referenced
             */
            // the phis at the original merge should always be duplicated
            worklist.addAll(mergePhis);
            aboveBound.markAll(mergePhis);

            // all inputs of the frame state need to be inside, so that the proper phis will be
            // generated
            if (region.getStateAfter() != null) {
                region.getStateAfter().applyToNonVirtual(aboveClosure);
            }
            while (!worklist.isEmpty()) {
                Node current = worklist.remove();
                for (Node input : current.inputs()) {
                    if (input instanceof PhiNode && !fixedNodes.contains(((PhiNode) input).merge())) {
                        // stop iterating: phis belonging to outside merges are known to be outside.
                    } else if (input instanceof FixedNode) {
                        // stop iterating: fixed nodes within the given set are traversal roots
                        // anyway, and all other fixed nodes are known to be outside.
                    } else if (!aboveBound.isMarked(input)) {
                        worklist.add(input);
                        aboveBound.mark(input);
                    }
                }
            }

            // Build the set of nodes that have (transitive) inputs within the duplicatedNodes.
            // This is achieved by iterating all nodes that are reachable via usages from the fixed
            // nodes.
            belowBound.markAll(fixedNodes);
            worklist.addAll(fixedNodes);

            // the phis at the original merge should always be duplicated
            worklist.addAll(mergeUsages);
            belowBound.markAll(mergeUsages);

            while (!worklist.isEmpty()) {
                Node current = worklist.remove();
                for (Node usage : current.usages()) {
                    if (usage instanceof PhiNode && !fixedNodes.contains(((PhiNode) usage).merge())) {
                        // stop iterating: phis belonging to outside merges are known to be outside.
                    } else if (usage instanceof FixedNode) {
                        // stop iterating: fixed nodes within the given set are traversal roots
                        // anyway, and all other
                        // fixed nodes are known to be outside.
                    } else if (!belowBound.isMarked(usage)) {
                        worklist.add(usage);
                        belowBound.mark(usage);
                    }
                }
            }

            // build the intersection
            belowBound.intersect(aboveBound);
            for (Node node : belowBound) {
                duplicatedNodes.add(node);
            }
            for (Node node : mergePhis) {
                duplicatedNodes.remove(node);
            }
        }

        public void expandDuplicated() {
            DebugContext debug = merge.getDebug();
            debug.log(DebugContext.VERY_DETAILED_LEVEL, "before expand: %s", duplicatedNodes);
            List<PhiNode> phis = merge.phis().snapshot();
            assert worklist.isEmpty() : "Worklist must be empty but is " + worklist;
            duplicatedNodes.add(merge);
            worklist.add(merge);
            for (Node n : duplicatedNodes) {
                worklist.add(n);
            }
            worklist.addAll(phis);
            if (nodeClassificationCache == null) {
                nodeClassificationCache = EconomicMap.create(Equivalence.IDENTITY);
            } else {
                nodeClassificationCache.clear();
            }

            /*
             * Run a set of input/usage processing first only on the fixed nods - do this for nodes
             * like proxies, etc to break ties regarding scheduling position of certain nodes (like
             * proxies) immediately and explicitly.
             */
            for (int i = region.fixedNodes.size() - 1; i >= 0; i--) {
                Node duplicated = region.fixedNodes.get(i);
                processDuplicatedNode(phis, duplicated);
            }

            while (!worklist.isEmpty()) {
                Node duplicated = worklist.removeLast();
                processDuplicatedNode(phis, duplicated);
            }
            duplicatedNodes.remove(merge);
        }

        private void processDuplicatedNode(List<PhiNode> phis, Node duplicated) {
            // check if this node has usages that lie outside and cannot be shared
            processUsages(duplicated, phis);
            // check if this node has an input that lies outside and cannot be shared
            processInputs(duplicated);
        }

        @SuppressWarnings({"fallthrough", "try"})
        private void processUsages(Node duplicated, List<PhiNode> phis) {
            try (DebugCloseable c = timerProcessUsages.start(duplicated.getDebug())) {
                DebugContext debug = merge.getDebug();
                EconomicSet<Node> unique = EconomicSet.create(Equivalence.IDENTITY);
                unique.addAll(duplicated.usages());

                // clear the current classification cache
                nodeClassificationCache.clear();
                EconomicMap<AbstractMergeNode, PhiNode> phiCache = null;

                for (Node usage : unique) {
                    EconomicMap<AbstractMergeNode, Node> newOutsideClones = null;
                    if (!duplicatedNodes.contains(usage)) {
                        Iterator<Position> iter = usage.inputPositions().iterator();
                        while (iter.hasNext()) {
                            Position pos = iter.next();
                            if (pos.get(usage) == duplicated) {
                                try (Indent indent = debug.logAndIndent(4, "Processing usage %s of node %s", usage, duplicated)) {
                                    InputType inputType = pos.getInputType();
                                    switch (inputType) {
                                        case Association:
                                            if (merge.isPhiAtMerge(usage)) {
                                                break;
                                            }
                                            // fallthrough
                                        case Extension:
                                        case Condition:
                                        case State: {
                                            if (duplicated instanceof FixedNode) {
                                                duplicatedNodes.add(usage);
                                                worklist.add(usage);
                                            } else {
                                                if (newOutsideClones == null) {
                                                    newOutsideClones = EconomicMap.create(Equivalence.IDENTITY);
                                                }
                                                // clone the offending node to the outside
                                                AbstractMergeNode classification = classify(usage, duplicated);
                                                Node newOutsideClone = newOutsideClones.get(classification);
                                                if (newOutsideClone == null) {
                                                    newOutsideClone = duplicated.copyWithInputs();
                                                    // this might cause other nodes to have
                                                    // outside
                                                    // usages
                                                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "added clone %s for %s (%s of %s)", newOutsideClone, duplicated, pos.getName(), usage);
                                                    for (Node input : newOutsideClone.inputs()) {
                                                        if (duplicatedNodes.contains(input) || phis.contains(input)) {
                                                            debug.log(DebugContext.VERY_DETAILED_LEVEL, "added %s to worklist", input);
                                                            worklist.add(input);
                                                        }
                                                    }
                                                    newOutsideClones.put(classification, newOutsideClone);
                                                }
                                                pos.set(usage, newOutsideClone);
                                            }
                                            break;
                                        }
                                        case Anchor: {
                                            // re-route dependencies to the merge
                                            AbstractMergeNode classification = classify(usage, duplicated);
                                            if (classification != ABOVE_DUPLICATED && classification != UNUSED) {
                                                pos.set(usage, classification);
                                            }
                                            break;
                                        }
                                        case Guard:
                                        case Memory:
                                        case Value: {
                                            if (usage instanceof ProxyNode && duplicatedNodes.contains(((ProxyNode) usage).proxyPoint())) {
                                                /*
                                                 * this node needs to be cloned - and this will be
                                                 * handled by the "Association" case above.
                                                 */
                                                break;
                                            }
                                            if (usage instanceof PhiNode) {
                                                LocationIdentity identity = inputType == InputType.Memory ? getLocationIdentity(usage) : null;
                                                PhiNode phiNode = (PhiNode) usage;
                                                AbstractMergeNode phiMerge = phiNode.merge();
                                                if (phiMerge == merge || region.isEndMerge(phiMerge)) {
                                                    // nothing to do, will be fixed after
                                                    // duplicating
                                                } else {
                                                    int valueIndex = pos.getSubIndex();
                                                    AbstractEndNode phiPredecessorAt = phiMerge.phiPredecessorAt(valueIndex);
                                                    AbstractMergeNode classification = classifyFixed(phiPredecessorAt);
                                                    assert classification != UNUSED : classification + " for " + phiPredecessorAt;
                                                    if (classification != ABOVE_DUPLICATED) {
                                                        if (phiCache == null) {
                                                            phiCache = EconomicMap.create(Equivalence.IDENTITY, PHI_CACHE_DEFAULT_SIZE);
                                                        } else {
                                                            phiCache.clear();
                                                        }
                                                        PhiNode phi = generatePhis(phiCache, classification, (ValueNode) duplicated, inputType, identity);
                                                        pos.set(usage, phi);
                                                    }
                                                }
                                            } else {
                                                LocationIdentity identity = inputType == InputType.Memory ? getLocationIdentity(usage) : null;
                                                // introduce a new phi
                                                AbstractMergeNode classification = classify(usage, duplicated);
                                                if (classification != ABOVE_DUPLICATED && classification != UNUSED) {
                                                    if (phiCache == null) {
                                                        phiCache = EconomicMap.create(Equivalence.IDENTITY, PHI_CACHE_DEFAULT_SIZE);
                                                    } else {
                                                        phiCache.clear();
                                                    }
                                                    PhiNode phi = generatePhis(phiCache, classification, (ValueNode) duplicated, inputType, identity);
                                                    pos.set(usage, phi);
                                                }
                                            }
                                            break;
                                        }
                                        default:
                                            throw GraalError.shouldNotReachHere("unexpected input type " + inputType + " at input " + pos.getName() + " of node " + usage); // ExcludeFromJacocoGeneratedReport
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * An additional classification cache for all nodes. Very large duplication regions might
         * require very expensive traversals of the fixed nodes to find classifications for all
         * nodes which we only want to do once.
         */
        private EconomicMap<Node, AbstractMergeNode> nodeClassificationCache;

        private static final int PHI_CACHE_DEFAULT_SIZE = 4;

        private void processInputs(Node duplicated) {
            if (duplicated == merge) {
                return;
            }
            // check if this node has an input that lies outside and cannot be shared
            Iterator<Position> iter = duplicated.inputPositions().iterator();
            while (iter.hasNext()) {
                Position pos = iter.next();
                Node input = pos.get(duplicated);
                if (input != null && !duplicatedNodes.contains(input)) {
                    InputType inputType = pos.getInputType();
                    switch (inputType) {
                        case Extension:
                            if (!(input instanceof ValueNumberable)) {
                                duplicatedNodes.add(input);
                                worklist.add(input);
                                duplicated.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "added %s to duplicated set (%s of %s)", input, pos.getName(), duplicated);
                            }
                            break;
                        case State:
                        case Condition:
                        case Association:
                        case Guard:
                        case Anchor:
                        case Value:
                        case Memory:
                        case Unchecked:
                            // no change needed
                            break;
                        default:
                            throw GraalError.shouldNotReachHere("unexpected input type " + inputType + " at input " + pos.getName() + " of node " + duplicated); // ExcludeFromJacocoGeneratedReport
                    }
                }
            }
        }

        @SuppressWarnings("try")
        private PhiNode generatePhis(EconomicMap<AbstractMergeNode, PhiNode> phiCache, AbstractMergeNode phiMerge, ValueNode input, InputType inputType, LocationIdentity identity) {
            /*
             * Note: caching the phi nodes per usage generally has little or no benefit at all if
             * there are below 5 merges between the classification of the usage and the duplication
             * region. However, if there are more merges the runtime complexity can explode very
             * fast thus we always cache already computed phis to avoid runtime problems.
             */
            PhiNode alreadyComputed = phiCache.get(phiMerge);
            if (alreadyComputed != null) {
                return alreadyComputed;
            }
            PhiNode phi = null;
            DebugContext debug = phiMerge.getDebug();
            try (DebugCloseable c = timeCreatePhi.start(debug); Indent indent = debug.logAndIndent(DebugContext.VERY_DETAILED_LEVEL, "generatePhis %s at %s{", input, phiMerge)) {
                if (region.isEndMerge(phiMerge)) {
                    for (PhiNode existingPhi : phiMerge.phis()) {
                        if (existingPhi.valueCount() == 0) {
                            throw new GraalError("!" + existingPhi);
                        }
                        if (existingPhi.valueAt(0) == input && existingPhi.isAllowedUsageType(inputType)) {
                            phi = existingPhi;
                            return phi;
                        }
                    }
                    phi = createPhi(phiMerge, input, inputType, identity);
                    phi.addInput(input);
                    phi.inferStamp();
                    return phi;
                } else {
                    phi = createPhi(phiMerge, input, inputType, identity);
                    for (int i = 0; i < phiMerge.phiPredecessorCount(); i++) {
                        AbstractMergeNode endClassification = classifyFixed(phiMerge.phiPredecessorAt(i));
                        assert endClassification != UNUSED && endClassification != ABOVE_DUPLICATED : endClassification + " for node " + input;
                        phi.addInput(generatePhis(phiCache, endClassification, input, inputType, identity));
                    }
                    // fix phi stamp before looking for duplicates, else we would miss cases
                    phi.inferStamp();
                    PhiNode duplicate = graph.findDuplicate(phi);
                    if (duplicate != null) {
                        phi.safeDelete();
                        phi = duplicate;
                        return duplicate;
                    }
                    return phi;
                }
            } finally {
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "}result: %s", phi);
                // cache the node for the current run
                phiCache.put(phiMerge, phi);
            }
        }

        private PhiNode createPhi(AbstractMergeNode phiMerge, ValueNode input, InputType inputType, LocationIdentity identity) {
            switch (inputType) {
                case Value:
                    return graph.addWithoutUnique(new ValuePhiNode(input.stamp(NodeView.DEFAULT).unrestricted(), phiMerge));
                case Memory:
                    return graph.addWithoutUnique(new MemoryPhiNode(phiMerge, identity));
                case Guard:
                    return graph.addWithoutUnique(new GuardPhiNode(phiMerge));
                default:
                    throw GraalError.shouldNotReachHereUnexpectedValue(inputType); // ExcludeFromJacocoGeneratedReport
            }
        }

        /**
         * Classification:
         *
         * A classification is always a merge that is a successor of both the original and the
         * duplicated section. There are potentially many such merges, and the specific one for a
         * given node is calculated as follows:
         *
         * For fixed nodes, the classification is a dominator of the node. Since there could be
         * multiple merges that fulfill both criteria, the classification is always the earliest one
         * - the one that is not dominated by any other merge that fulfills the criteria.
         *
         * For floating nodes, the classification is a common dominator of the classifications of
         * all (transitive) usages that are fixed nodes. Again, the earliest classification is
         * chosen.
         */
        private AbstractMergeNode classify(Node node, Node input) {
            AbstractMergeNode result = null;
            if (merge.stateAfter() == node) {
                result = ABOVE_DUPLICATED;
            } else if (node instanceof PhiNode) {
                result = classifyPhi((PhiNode) node, input);
            } else if (node instanceof FloatingNode) {
                result = classifyFloating((FloatingNode) node, input);
            } else if (node instanceof VirtualState) {
                result = classifyVirtual((VirtualState) node, input);
            } else if (node instanceof FixedNode) {
                result = classifyFixed((FixedNode) node);
            } else if (node instanceof CallTargetNode) {
                assert node.getUsageCount() == 1 : node;
                result = classifyFixed((FixedNode) node.usages().first());
            } else {
                throw GraalError.shouldNotReachHere("node: " + node); // ExcludeFromJacocoGeneratedReport
            }
            return result;
        }

        @SuppressWarnings("try")
        private AbstractMergeNode classifyFloating(FloatingNode node, Node input) {
            AbstractMergeNode classification = nodeClassificationCache.get(node);
            if (classification != null) {
                return classification;
            }
            assert !(node instanceof PhiNode) : "Must not be a phi " + node;
            AbstractMergeNode result = null;
            DebugContext debug = node.getDebug();
            try (DebugCloseable c = timeClassifyFloating.start(debug); Indent indent = debug.logAndIndent(4, "classifyFloating %s (input: %s)", node, input)) {
                if (duplicatedNodes.contains(node)) {
                    result = ABOVE_DUPLICATED;
                    return result;
                } else {
                    result = UNUSED;
                    for (Node usage : node.usages()) {
                        AbstractMergeNode usageClassification = classify(usage, node);
                        if (usageClassification != UNUSED) {
                            if (usageClassification == ABOVE_DUPLICATED || result != UNUSED && result != usageClassification) {
                                result = ABOVE_DUPLICATED;
                                break;
                            } else {
                                result = usageClassification;
                            }
                        }
                    }
                }
                if (result == ABOVE_DUPLICATED) {
                    /*
                     * there is no common dominator of all usages that is below the duplicated set
                     * -> pull the node into the duplicated set
                     */
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "pulling %s into duplicated set", node);
                    duplicatedNodes.add(node);
                    worklist.add(node);
                }
                return result;
            } finally {
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "result: %s", result);
                nodeClassificationCache.put(node, result);
            }
        }

        @SuppressWarnings("try")
        private AbstractMergeNode classifyPhi(PhiNode node, Node input) {
            DebugContext debug = node.getDebug();
            AbstractMergeNode result = null;
            try (Indent indent = debug.logAndIndent(4, "classifyPhi %s (input: %s)", node, input)) {
                AbstractMergeNode phiMerge = node.merge();
                if (region.isEndMerge(phiMerge)) {
                    result = phiMerge;
                    return result;
                }
                for (int i = 0; i < phiMerge.phiPredecessorCount(); i++) {
                    if (node.valueAt(i) == input) {
                        AbstractMergeNode endClassification = classifyFixed(phiMerge.phiPredecessorAt(i));
                        if (result == null) {
                            result = endClassification;
                        } else if (result != endClassification) {
                            result = ABOVE_DUPLICATED;
                            return result;
                        }
                    }
                }
                assert result != null && result != UNUSED : result + " for node " + node;
                return result;
            } finally {
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "result: %s", result);
            }
        }

        @SuppressWarnings("try")
        private AbstractMergeNode classifyVirtual(VirtualState node, Node input) {
            AbstractMergeNode result = null;
            DebugContext debug = node.getDebug();
            try (DebugCloseable c = timeClassifyVirtual.start(debug); Indent indent = debug.logAndIndent(4, "classifyVirtual %s (input: %s)", node, input)) {
                result = UNUSED;
                for (Node usage : node.usages()) {
                    AbstractMergeNode usageClassification;
                    if (usage instanceof VirtualState) {
                        // we pass along the original input instead of the current node
                        usageClassification = classifyVirtual((VirtualState) usage, input);
                    } else {
                        if (node == merge.stateAfter()) {
                            usageClassification = merge.forwardEndCount() > 2 ? ABOVE_DUPLICATED : UNUSED;
                        } else {
                            usageClassification = classifyFixed((FixedNode) usage);
                            if (usage instanceof AbstractMergeNode && !((AbstractMergeNode) usage).isPhiAtMerge(input) && usageClassification == usage) {
                                /*
                                 * Only Phi nodes can be scheduled at the merge that belongs to a
                                 * frame state, all other nodes need to go into a dominator.
                                 */
                                if (!duplicatedNodes.contains(input) && !merge.isPhiAtMerge(input)) {
                                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "not schedulable at merge: %s", input);
                                    usageClassification = ABOVE_DUPLICATED;
                                }
                            }
                        }
                    }
                    if (usageClassification != UNUSED) {
                        if (usageClassification == ABOVE_DUPLICATED || (result != UNUSED && result != usageClassification)) {
                            result = ABOVE_DUPLICATED;
                            break;
                        } else {
                            result = usageClassification;
                        }
                    }
                }
                if (result == ABOVE_DUPLICATED) {
                    /*
                     * there is no common dominator of all usages that is below the duplicated set
                     * -> pull the node into the duplicated set
                     */
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "pulling %s into duplicated set", node);
                    duplicatedNodes.add(node);
                    worklist.add(node);
                }
                return result;
            } finally {
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "result: %s", result);
            }
        }

        @SuppressWarnings("try")
        private AbstractMergeNode classifyFixed(FixedNode node) {
            AbstractMergeNode classification = nodeClassificationCache.get(node);
            if (classification != null) {
                return classification;
            }
            DebugContext debug = node.getDebug();
            try (DebugCloseable c = timeClassifyFixed.start(debug)) {
                AbstractMergeNode result = null;
                FixedNode current = node;
                try (Indent indent = debug.logAndIndent(4, "classifyFixed %s {", node)) {
                    // iterate upwards to the next merge
                    while (current.predecessor() != null) {
                        current = (FixedNode) current.predecessor();
                    }
                    // check if the merge is one of the newly introduced merges
                    if (region.isEndMerge(current)) {
                        result = (AbstractMergeNode) current;
                        return result;
                    }
                    assert current instanceof AbstractMergeNode : current;
                    // query the cache
                    AbstractMergeNode currentMerge = (AbstractMergeNode) current;
                    result = classificationCache.get(currentMerge);
                    if (result != null) {
                        return result;
                    }
                    /*
                     * If all predecessors have the same classification, then that's the
                     * classification of this merge. Otherwise,
                     */
                    for (AbstractEndNode currentEnd : currentMerge.forwardEnds()) {
                        AbstractMergeNode endClassification = classifyFixed(currentEnd);
                        if (result == null) {
                            result = endClassification;
                        } else if (result != endClassification) {
                            debug.log(DebugContext.VERY_DETAILED_LEVEL,
                                            "Classification for fixed node %s has different pred classifications on current merge %s,taking current merge as classification", node,
                                            currentMerge);
                            result = currentMerge;
                            break;
                        }
                    }
                    classificationCache.put(currentMerge, result);
                    return result;
                } finally {
                    // Parfait_ALLOW impossible-redundant-condition
                    assert result != null && result != UNUSED && result != ABOVE_DUPLICATED : result + " for " + node + " (" + current + ")";
                    debug.log(DebugContext.VERY_DETAILED_LEVEL, "} result: %s", result);
                    nodeClassificationCache.put(node, result);
                }
            }
        }

    }

    @NodeInfo(size = SIZE_IGNORED, cycles = CYCLES_IGNORED)
    private static class FixedValueConsumer extends FixedWithNextNode implements Canonicalizable {
        public static final NodeClass<FixedValueConsumer> TYPE = NodeClass.create(FixedValueConsumer.class);

        @Input ValueNode input;

        protected FixedValueConsumer(ValueNode input) {
            super(TYPE, input.stamp(NodeView.DEFAULT));
            this.input = input;
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            // delete this node, it is not needed after duplication
            return null;
        }

        public ValueNode value() {
            return input;
        }
    }

    public static class LoopEndRegion extends DuplicationRegion {
        private final LoopBeginNode loopBegin;
        private final LoopEndNode originalLoopEnd;
        private final FixedValueConsumer[] fixedValueConsumers;
        private final AbstractMergeNode mergeAfter;
        private final LoopEndNode newLoopEnd;
        private final int oldNrOfLoopEnds;

        @SuppressWarnings("try")
        protected LoopEndRegion(AbstractMergeNode regionStart, EndNode duplicatedEnd, LoopEndNode regionEnd) {
            super(duplicatedEnd);
            assert regionStart != null;
            assert duplicatedEnd != null;
            assert regionEnd != null;
            mergeAfter = regionStart;
            loopBegin = regionEnd.loopBegin();
            originalLoopEnd = regionEnd;
            stateAfter = findLastFrameState(regionEnd);
            StructuredGraph graph = regionStart.graph();
            oldNrOfLoopEnds = loopBegin.loopEnds().count() + 1/* forward end */;
            try (DebugCloseable s = originalLoopEnd.withNodeSourcePosition()) {
                newLoopEnd = graph.add(new LoopEndNode(loopBegin));
            }
            /*
             * Fake a set of fixed nodes predecessing the loop end to have usages of the values
             * flowing into the loop phi that will be properly duplicated.
             */
            FixedValueConsumer[] consumers = null;
            if (loopBegin.phis().isNotEmpty()) {
                consumers = new FixedValueConsumer[loopBegin.phis().count()];
                int index = 0;
                for (PhiNode phi : loopBegin.phis()) {
                    ValueNode oldInput = phi.valueAt(originalLoopEnd);
                    FixedValueConsumer consumer = graph.add(new FixedValueConsumer(oldInput));
                    graph.addBeforeFixed(originalLoopEnd, consumer);
                    consumers[index++] = consumer;
                    phi.setValueAt(loopBegin.phiPredecessorIndex(originalLoopEnd), null);
                }
            }
            fixedValueConsumers = consumers;
            DuplicationUtil.traverseLinear(regionStart.next(), regionEnd, node -> fixedNodes.add(node));
        }

        @Override
        protected void rewire(UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements) {
            if (fixedValueConsumers != null) {
                List<PhiNode> loopPhis = loopBegin.phis().snapshot();
                for (int i = 0; i < fixedValueConsumers.length; i++) {
                    FixedValueConsumer originalConsumer = fixedValueConsumers[i];
                    FixedValueConsumer duplicatedConsumer = (FixedValueConsumer) duplicates.get(originalConsumer);
                    int oldLoopEndIndex = loopBegin.phiPredecessorIndex(originalLoopEnd);
                    PhiNode phi = loopPhis.get(i);
                    while (phi.values().size() <= loopBegin.getLoopEndCount()) {
                        phi.values().add(null);
                    }
                    assert phi.valueAt(oldLoopEndIndex) == null;
                    phi.setValueAt(oldLoopEndIndex, originalConsumer.value());
                    assert phi.values().size() == oldNrOfLoopEnds + 1 : phi.values().snapshot();
                    phi.setValueAt(oldNrOfLoopEnds, duplicatedConsumer.value());
                }
            }
        }

        @Override
        public void fixPhis(UnmodifiableEconomicMap<Node, Node> duplicates, UnmodifiableEconomicMap<Node, Node> replacements) {
            // Nothing do to
        }

        @Override
        public void canonicalize(SimplifierTool tool, boolean emptyDuplication, UnmodifiableEconomicMap<Node, Node> duplicates) {
            // Nothing do to
        }

        @Override
        public void collectReplacements(EconomicMap<Node, Node> replacements) {
            replacements.put(originalLoopEnd, newLoopEnd);
        }

        @Override
        public boolean isEndMerge(Node node) {
            /*
             * We do not need to create new phis at the loop begin node. And we do not need to
             * create new phis at the duplication target merge. Thus we fake the loop begin being
             * the end merge.
             */
            return node == mergeAfter || node == loopBegin;
        }

        @Override
        public int getEndMergePhiIndex(AbstractMergeNode merge) {
            throw GraalError.shouldNotReachHere("asking for " + merge); // ExcludeFromJacocoGeneratedReport
        }

    }

    public static boolean verifyDuplication(StructuredGraph g) {
        if (g.getGuardsStage().areFrameStatesAtDeopts()) {
            assert GraphOrder.assertNonCyclicGraph(g);
            if (Assertions.detailedAssertionsEnabled(g.getOptions())) {
                // we still want to do a memory verification of the schedule even if we can
                // no longer use assertSchedulableGraph after the floating reads phase
                SchedulePhase.runWithoutContextOptimizations(g, SchedulePhase.SchedulingStrategy.LATEST_OUT_OF_LOOPS, true);
            }
        } else {
            assert GraphOrder.assertSchedulableGraph(g);
        }
        return true;
    }

    public static DuplicationRegion createRegion(final Deque<AbstractMergeNode> queue, AbstractMergeNode regionStart, FixedNode regionEnd, EndNode duplicatedEnd) {
        if (regionEnd instanceof LoopEndNode) {
            return new LoopEndRegion(regionStart, duplicatedEnd, (LoopEndNode) regionEnd);
        } else if (regionEnd instanceof WithExceptionNode || regionEnd instanceof AbstractEndNode) {
            return new DuplicationUtil.LinearRegion(regionStart, (FixedWithNextNode) regionEnd.predecessor(), queue, duplicatedEnd);
        } else if (regionEnd instanceof ControlSinkNode) {
            return new SinkRegion(regionStart, regionEnd, duplicatedEnd);
        } else if (regionEnd instanceof ControlSplitNode) {
            return new SplitRegion(regionStart, regionEnd, queue, duplicatedEnd);
        } else if (regionEnd instanceof FixedWithNextNode) {
            return new DuplicationUtil.LinearRegion(regionStart, (FixedWithNextNode) regionEnd, queue, duplicatedEnd);
        } else {
            throw GraalError.shouldNotReachHere("regionend " + regionEnd); // ExcludeFromJacocoGeneratedReport
        }
    }

    public static FixedNode findRegionEnd(AbstractMergeNode regionStart) {
        return findRegionEnd(regionStart, true, true);
    }

    public static int assertNotNegative(int d) {
        assert d >= 0 : "Must not be negative " + d;
        return d;
    }

    public static long assertNotNegative(long d) {
        assert d >= 0 : "Must not be negative " + d;
        return d;
    }

    public static double assertNotNegative(double d) {
        assert d >= 0 : "Must not be negative " + d;
        return d;
    }

    public static double assertPositive(double d) {
        assert d > 0 : "Must not be negative " + d;
        return d;
    }

    public static boolean conditionDominatesEnd(FixedNode regionEnd, EndNode end, MergeNode merge) {
        for (FixedNode f : GraphUtil.predecessorIterable(regionEnd)) {
            if (f == merge) {
                return false;
            }
            if (f instanceof IfNode) {
                IfNode ifNode = (IfNode) f;
                LogicNode condition = ifNode.condition();
                if (condition.getUsageCount() > 1) {
                    if (DuplicationUtil.queryDominatingDecision(condition, end) != UNKNOWN) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determines if the node potentialInput is input to the node n. If the potentialInput is a
     * {@linkplain PiNode} it checks whether the input node (recursively) is the input.
     */
    public static boolean isInputOrPiInput(Node n, Node potentialInput) {
        for (Node input : n.inputs()) {
            if (input == potentialInput || unPiCheckEquality(input, potentialInput)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the other node is equal to the current one or if it is a pi one of its inputs
     * is.
     */
    private static boolean unPiCheckEquality(Node n, Node other) {
        Node cur = n;
        while (cur instanceof PiNode) {
            if (cur == other) {
                return true;
            }
            cur = ((PiNode) cur).object();
        }
        return cur == other;
    }

    public static boolean graphQualifiesForDuplication(StructuredGraph graph) {
        for (MergeNode merge : graph.getNodes(MergeNode.TYPE)) {
            if (mergeQualifiesForDuplication(merge)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mergeQualifiesForDuplication(AbstractMergeNode merge) {
        return !(merge instanceof LoopBeginNode) && !(merge.next() instanceof EndNode);
    }

    public static FixedNode findRegionEnd(AbstractMergeNode regionStart, boolean split, boolean sink) {
        boolean allowsFloatingReads = regionStart.graph().getGraphState().allowsFloatingReads();
        boolean beforePEA = regionStart.graph().isBeforeStage(StageFlag.FINAL_PARTIAL_ESCAPE);
        EconomicSet<AllocatedObjectNode> allocatedObjects = EconomicSet.create();
        FixedNode fixed = regionStart;
        while (true) { // TERMINATION ARGUMENT: processing a known set of next nodes until an exit
                       // condition node is hit
            CompilationAlarm.checkProgress(regionStart.graph());
            if (fixed instanceof ControlFlowAnchored) {
                // prevent duplication of control flow anchor
                return fixed;
            } else if (fixed instanceof FixedWithNextNode) {
                if (beforePEA) {
                    /*
                     * Before PEA, do not duplicate stores that might cause a duplicated allocation
                     * to be materialized. PE read elimination is not powerful enough to propagate
                     * store information for materialized objects past merges. Therefore, keep track
                     * of allocations we duplicate and stop before any relevant store.
                     */
                    if (fixed instanceof CommitAllocationNode) {
                        fixed.usages().filter(AllocatedObjectNode.class).forEach(allocatedObjects::add);
                    } else if (isEscapingStoreOfDuplicatedAllocation(fixed, allocatedObjects)) {
                        return (FixedNode) fixed.predecessor();
                    }
                }

                FixedNode next = ((FixedWithNextNode) fixed).next();
                if (allowsFloatingReads && MemoryKill.isMemoryKill(next)) {
                    return fixed;
                }
                fixed = next;
            } else if (fixed instanceof ControlSplitNode) {
                FixedNode next = null;
                if (sink) {
                    for (Node successor : fixed.successors()) {
                        AbstractBeginNode begin = (AbstractBeginNode) successor;
                        if (!(begin.next() instanceof ControlSinkNode)) {
                            if (next != null) {
                                return fixed;
                            } else {
                                next = begin;
                            }
                        }
                    }
                } else {
                    if (split) {
                        return fixed;
                    } else {
                        return (FixedNode) fixed.predecessor();
                    }
                }
                if (next == null || (allowsFloatingReads && MemoryKill.isMemoryKill(next))) {
                    return fixed;
                }
                fixed = next;
            } else {
                return fixed;
            }
        }

    }

    /**
     * Determine if {@code fixed} is some sort of high tier store operation that writes a value from
     * {@code allocatedObjects} to memory. This would cause PEA to materialize that object.
     */
    private static boolean isEscapingStoreOfDuplicatedAllocation(FixedNode fixed, EconomicSet<AllocatedObjectNode> allocatedObjects) {
        ValueNode storedValue;
        if (fixed instanceof StoreFieldNode) {
            storedValue = ((StoreFieldNode) fixed).value();
        } else if (fixed instanceof StoreIndexedNode) {
            storedValue = ((StoreIndexedNode) fixed).value();
        } else if (fixed instanceof RawStoreNode) {
            storedValue = ((RawStoreNode) fixed).value();
        } else if (fixed instanceof UnsafeMemoryStoreNode) {
            storedValue = ((UnsafeMemoryStoreNode) fixed).getValue();
        } else if (fixed instanceof JavaWriteNode) {
            storedValue = ((JavaWriteNode) fixed).value();
        } else {
            return false;
        }
        if (storedValue instanceof AllocatedObjectNode) {
            return allocatedObjects.contains((AllocatedObjectNode) storedValue);
        }
        if (storedValue instanceof ValuePhiNode) {
            ValuePhiNode phi = (ValuePhiNode) storedValue;
            // If all phi input values are allocated objects (presumably from an earlier round of
            // duplication), don't duplicate a materializing store either.
            return phi.values().filter(AllocatedObjectNode.class).count() == phi.valueCount();
        }
        return false;
    }

    private static final int MAX_SIM_DEPTH = 25;

    /**
     * Computes the depth in number of basic blocks that should be used for simulation during
     * {@link HighTierDuplicationSimulationPhase}. Takes into account the graph shapes that are
     * supported by duplication.
     *
     * {@link #duplicate(AbstractMergeNode, DuplicationRegion, CanonicalizerPhase, CoreProviders)}
     * has certain requirements with respect to graph shape. Duplication supports some basic shapes
     * of code after a {@link MergeNode}: straight line code {@link LinearRegion}, control flow
     * split regions {@link SplitRegion}, loop end regions {@link LoopEndRegion} and control flow
     * sinking regions {@link SinkRegion}.
     *
     * Sink regions are special in that they can cover multiple control flow sinks as long as they
     * are duplicated along.
     *
     * Consider the following piece of code
     *
     * <pre>
     * public static int snippetEarlyExitBlockDepth(A a) {
     *     A p;
     *     if (a == null) {
     *         p = new A();
     *     } else {
     *         p = a;
     *     } // merge
     *     if (S1 == 1) {
     *         return 0;
     *     }
     *     if (S2 == 1) {
     *         return 1;
     *     }
     *     if (S3 == 2) {
     *         return 2;
     *     }
     *     if (S4 == 3) {
     *         return 3;
     *     }
     *     // region end
     *     return p.x;
     * }
     * </pre>
     *
     * The merge dominates a set of linearly connected {@code if(cond) {sink;}} patterns of code.
     * Duplication can include those as they are exits of the graph and never merge back. In that
     * sense a sink region is just like linear code with these early exit patterns.
     *
     * Since duplication simulation tries to reduce compile time it will only ever traverse a fixed
     * set of blocks in terms of successor count == block depth. Thus this method computes the depth
     * in basic blocks that should be inspected during duplication simulation while respecting the
     * duplication capabilities of {@link DuplicationUtil}.
     */
    public static int blockDepthIgnoringEarlyExits(AbstractMergeNode regionStart) {
        boolean allowsFloatingReads = regionStart.graph().getGraphState().allowsFloatingReads();
        FixedNode fixed = regionStart;
        int depth = 1;
        while (true) { // TERMINATION ARGUMENT: processing next nodes with a fixed depth
            CompilationAlarm.checkProgress(regionStart.graph());
            if (depth > MAX_SIM_DEPTH) {
                return 1;
            }
            if (fixed instanceof ControlFlowAnchored) {
                // prevent duplication of control flow anchor
                return depth;
            } else if (fixed instanceof FixedWithNextNode) {
                if (fixed instanceof LoopExitNode) {
                    /*
                     * High-level loop data structure splits basic blocks on loop exit boundaries,
                     * just simulate over them they are still just sequential nodes
                     */
                    depth++;
                }
                FixedNode next = ((FixedWithNextNode) fixed).next();
                if (allowsFloatingReads && MemoryKill.isMemoryKill(next)) {
                    return depth;
                }
                fixed = next;
            } else if (fixed instanceof ControlSplitNode) {
                FixedNode next = null;
                for (Node successor : fixed.successors()) {
                    AbstractBeginNode begin = (AbstractBeginNode) successor;
                    if (!(begin.next() instanceof ControlSinkNode)) {
                        if (next != null) {
                            return depth;
                        } else {
                            next = begin;
                        }
                    }
                }
                if (next == null || (allowsFloatingReads && MemoryKill.isMemoryKill(next))) {
                    return depth;
                }
                fixed = next;
                depth++;
            } else {
                return depth;
            }
        }

    }

    public static boolean blockHasOneSuccessor(HIRBlock b) {
        return b.getSuccessorCount() == 1;
    }

    public static boolean uniqueNonSinkingSplit(ControlSplitNode split) {
        FixedNode uniqueNonSinkingSuccessor = null;
        for (Node successor : split.successors()) {
            AbstractBeginNode begin = (AbstractBeginNode) successor;
            if (!(begin.next() instanceof ControlSinkNode)) {
                if (uniqueNonSinkingSuccessor != null) {
                    return false;
                } else {
                    uniqueNonSinkingSuccessor = begin;
                }
            }
        }
        return uniqueNonSinkingSuccessor != null;
    }

    public static boolean isRealMergeBlock(HIRBlock b) {
        if (b.getPredecessorCount() > 1 && b.getBeginNode() instanceof MergeNode) {
            MergeNode merge = (MergeNode) b.getBeginNode();
            return !(merge.next() instanceof EndNode) || !(((EndNode) merge.next()).merge() instanceof LoopBeginNode);
        }
        return false;
    }

    private static double log(double d) {
        return Math.log(d + 1);
    }

    /**
     * Minimal fraction of basic blocks in a CFG to trust the profiles and make a profile drive.
     */
    private static final double minBlocksTrusted = 0.33D;

    public static class CFGFrequencyInfo {

        private final double maxFrequency;

        private final boolean trusted;

        public CFGFrequencyInfo(ControlFlowGraph cfg) {
            int nrOfBlocks = cfg.getBlocks().length;
            int trustedBlocks = 0;
            double max = 0;
            for (HIRBlock block : cfg.getBlocks()) {
                block.getFrequencySource();
                if (ProfileSource.isTrusted(block.getFrequencySource())) {
                    trustedBlocks++;
                }
                double p = block.getRelativeFrequency();
                if (p > max) {
                    max = p;
                }
            }

            final double trustedFraction = ((double) trustedBlocks / (double) nrOfBlocks);
            cfg.graph.getDebug().log(DebugContext.VERY_DETAILED_LEVEL, "Graph %s has %d trusted and %d untrusted blocks (fraction %f)", cfg.graph, trustedBlocks, (nrOfBlocks - trustedBlocks),
                            trustedFraction);
            trusted = trustedFraction >= minBlocksTrusted;
            maxFrequency = log(max);
        }

        public boolean isTrusted() {
            return trusted;
        }

        public double getFrequencyNormalizedToMaxFrequency(HIRBlock b) {
            double blockFrequency = log(b.getRelativeFrequency());
            final double normalized = blockFrequency / maxFrequency;
            assert normalized <= 1.0D : "Normalized = " + normalized;
            return normalized;
        }
    }

    /**
     * Determines if a block should be unconditionally visited during duplication simulation. This
     * is for patterns where simplifying a low probability duplicated path can result in
     * optimization opportunities for the remaining high probability paths.
     *
     * Example:
     *
     * <pre>
     *.    Object a;
     *      if (condition1) {
     *          // branch1: very slow path
     *          lotsOfSideEffectFreeWork();
     *          a = null;
     *      } else {
     *          if (condition2) {
     *              // branch2: fast path
     *              a = unknownValue1;
     *          } else {
     *              // branch3: fast path
     *              a = unknownValue2;
     *          }
     *      }
     *      if (a == null) {
     *          // deopt
     *      }
     *      consume(a);
     * </pre>
     *
     * By duplicating branches 1, 2 and 3 at the merge, branch1 can be reduced to a guarded deopt
     * which in turn can allow further optimizations on the remaining code. After duplication the
     * code looks like this:
     *
     * <pre>
     * Object a;
     * if (condition1) {
     *     // deopt;
     * }
     * if (condition2) {
     *     // branch2: fast path
     *     a = unknownValue1;
     * } else {
     *     // branch3: fast path
     *     a = unknownValue2;
     * }
     * if (a == null) {
     *     // deopt
     * }
     * consume(a);
     * </pre>
     *
     * This may allow further optimizations like e.g. conditional move optimization *
     *
     * <pre>
     * if (condition1) {
     *     // deopt;
     * }
     * tmp = conditionalMove(condition2, unkownValue1, unknownValue2);
     * if (tmp == null) {
     *     // deopt
     * }
     * consume(tmp);
     * </pre>
     *
     * The existence of the low probable path in the merge as a predecessor can prohibit further
     * optimizations, thus this method tries to find such patterns ahead and allow the inspection
     * during duplication simulation.
     */
    public static boolean alwaysEnterBlockFilter(HIRBlock b) {
        if (blockHasOneSuccessor(b) && isRealMergeBlock(b.getFirstSuccessor())) {
            MergeNode merge = (MergeNode) ((EndNode) b.getEndNode()).merge();
            FixedNode lastNode = b.getFirstSuccessor().getEndNode();
            if (lastNode instanceof IfNode) {
                IfNode ifNode = (IfNode) lastNode;
                LogicNode l = ifNode.condition();
                for (Node input : l.inputs()) {
                    if (merge.isPhiAtMerge(input)) {
                        // found a logic node consuming the phi which is constant at the given merge
                        // predecessor block
                        ValuePhiNode phi = (ValuePhiNode) input;
                        if (phi.valueAt((EndNode) b.getEndNode()).isConstant()) {
                            return true;
                        } else if (l instanceof IsNullNode) {
                            IsNullNode isNull = (IsNullNode) l;
                            if (isNull.tryFold(phi.valueAt((EndNode) b.getEndNode()).stamp(NodeView.DEFAULT)).isKnown()) {
                                return true;
                            }
                        }
                    }
                }
            } else if (lastNode instanceof SwitchNode) {
                SwitchNode switchNode = (SwitchNode) lastNode;
                ValueNode switchVal = switchNode.value();
                if (merge.isPhiAtMerge(switchVal)) {
                    for (ValueNode n : ((PhiNode) switchVal).values()) {
                        if (n.isConstant()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Code size in {@linkp NodeSize} we allow during merge - before - return duplication during
     * inlining.
     */
    private static final int MergeBeforeReturnDuplicateBudget = 32;

    /**
     * Considers duplicating merged paths in an inlinee that end in a return node in order to remove
     * the merge frame state.
     */
    public static class EEInliningReturnAction extends InliningUtil.InlineeReturnAction {

        private final CoreProviders providers;

        public EEInliningReturnAction(CoreProviders providers) {
            this.providers = providers;
        }

        @Override
        public List<ReturnNode> processInlineeReturns(List<ReturnNode> returns) {
            if (returns.isEmpty()) {
                return returns;
            }
            StructuredGraph callerGraph = returns.get(0).graph();
            List<ReturnNode> finalReturns = new ArrayList<>(returns.size());
            Graph.Mark before = callerGraph.getMark();
            for (ReturnNode ret : returns) {
                // will be merged and the returns removed afterwards, thus their code size must not
                // be counted
                int size = -ReturnNode.TYPE.size().value;
                for (FixedNode f : GraphUtil.predecessorIterable(ret)) {
                    if (f instanceof ControlFlowAnchored) {
                        break;
                    }
                    if (f instanceof ControlSplitNode) {
                        break;
                    }
                    if (f instanceof MergeNode) {
                        if (size < MergeBeforeReturnDuplicateBudget) {
                            final MergeNode merge = (MergeNode) f;
                            FixedNode regionEnd = DuplicationUtil.findRegionEnd(merge);
                            if (regionEnd == ret) {
                                SimplifierTool simplifierTool = GraphUtil.getDefaultSimplifier(providers, true, callerGraph.getAssumptions(), callerGraph.getOptions());
                                DuplicationUtil util = new DuplicationUtil(callerGraph, simplifierTool);
                                for (EndNode end : merge.forwardEnds().snapshot()) {
                                    if (end.isAlive()) {
                                        SinkRegion s = new SinkRegion(merge, ret, end);
                                        callerGraph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, callerGraph, "Before duplicating end %s merge %s %s", end, merge, s);
                                        util.duplicate(merge, s, null, null);
                                        callerGraph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, callerGraph, "After duplicating end %s merge %s %s", end, merge, s);
                                    }
                                }
                            }
                            GraalError.guarantee(!merge.isAlive(), "Merge must be dead");
                        } else {
                            break;
                        }
                    }
                    if (f instanceof StateSplit) {
                        break;
                    }
                    size += f.estimatedNodeSize().value;
                }
                if (ret.isAlive()) {
                    finalReturns.add(ret);
                }
            }
            for (Node n : callerGraph.getNewNodes(before)) {
                if (n instanceof ReturnNode) {
                    finalReturns.add((ReturnNode) n);
                }
            }
            return finalReturns;
        }
    }

    /**
     * Version of {@link GraphUtil#mayRemoveSplit(IfNode)} for arbitrary {@link ControlSplitNode}.
     */
    public static boolean mayRemoveSplit(ControlSplitNode split) {
        for (Node successor : split.successors()) {
            if (!GraphUtil.checkFrameState((FixedNode) successor, GraphUtil.MAX_FRAMESTATE_SEARCH_DEPTH)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Verify the {@link StructuredGraph} is in a valid state (phase plan step) to use duplication
     * util for the duplication of the specific duplication region. Graal IR does not model memory
     * anti dependencies (https://en.wikipedia.org/wiki/Data_dependency) explicitly in the graph as
     * edges but implicitly during {@link SchedulePhase}. Thus, {@linkplain DuplicationUtil} must
     * not be used for duplication purposes during {@link StageFlag#FLOATING_READS}.
     *
     * This can be verified with the following snippet:
     *
     * <pre>
     * public static int snippet01(int p0, int p1, O[] o) {
     *     if (o == null) {
     *         return 0;
     *     }
     *     if (o[0] == null) {
     *         return 0;
     *     }
     *     if (o[1] == null) {
     *         return 0;
     *     }
     *     int x;
     *     int index;
     *     if (p1 > 12) {
     *         GraalDirectives.sideEffect();
     *         x = p0;
     *         index = 0;
     *     } else {
     *         GraalDirectives.sideEffect();
     *         x = 1;
     *         index = 1;
     *     }// merge
     *     int tmp = o[index].field;
     *     if (p0 > 13) {
     *         o[index].field = 12 * x;
     *     } else {
     *         o[index].field = 13;
     *     }
     *
     *     GraalDirectives.sideEffect(98);
     *     GraalDirectives.sideEffect(99);
     *     // piece of code over which we must not duplicate
     *     GraalDirectives.controlFlowAnchor();
     *
     *     return tmp;
     * }
     * </pre>
     */
    public static boolean verifyDuplicationValid(StructuredGraph graph, DuplicationRegion region) {
        if (region instanceof SplitRegion) {
            GraalError.guarantee(graph.isBeforeStage(StageFlag.FLOATING_READS) || graph.isAfterStage(StageFlag.FIXED_READS),
                            "Split region duplication must not be used during floating reads.");
        }
        return true;
    }
}
