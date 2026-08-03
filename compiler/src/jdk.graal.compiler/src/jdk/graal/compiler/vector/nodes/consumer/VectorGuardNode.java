/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.consumer;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.InputType.Condition;
import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.VectorLogicNode;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorInitialIteratorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorGuardIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;
import jdk.graal.compiler.vector.nodes.producer.InvariantVectorLogicNode;
import jdk.graal.compiler.vector.nodes.producer.SequenceVectorNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.DeoptimizingFixedWithNextNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdMaskLogicNode;
import jdk.graal.compiler.vector.nodes.simd.SimdPrimitiveCompareNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.SpeculationLog.Speculation;

/**
 * A node that models conditional deoptimization based on a condition expressed as a vector.
 */
// @formatter:off
@NodeInfo(allowedUsageTypes = {Association, Guard},
          cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot argue about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot argue about vector nodes statically.",
          shortName = "VectorGuard", nameTemplate = "VectorGuard ({p#deoptBranch/s}) {p#reason/s}")
// @formatter:on
public final class VectorGuardNode extends DeoptimizingFixedWithNextNode implements LowerableVectorConsumer, SimdifyableVectorOperation, GuardingNode {

    public static final NodeClass<VectorGuardNode> TYPE = NodeClass.create(VectorGuardNode.class);
    @Input(Condition) ValueNode condition;
    @Input ValueNode conditionLength;
    @Input NodeInputList<ValueNode> stateVectors;
    /** @see LowerableVectorConsumer#vectorLoopMarker() */
    @OptionalInput(Association) VectorLoopMarkerNode vectorLoopMarker;

    private final Direction direction;
    protected DeoptBranch deoptBranch;
    protected double deoptProbability;
    protected DeoptimizationAction action;
    protected DeoptimizationReason reason;
    protected int debugId;
    protected final Speculation speculation;

    private AnchoringNode loopAnchor;
    private final ArrayList<ArrayList<Position>> vectorPositions;
    private double trustedBodyIterations;

    public enum DeoptBranch {
        TRUE_DEOPT_BRANCH,
        FALSE_DEOPT_BRANCH
    }

    public VectorGuardNode(VectorLogicNode condition, ValueNode conditionLength, Direction direction, DeoptBranch deoptBranch, double deoptProbability, DeoptimizeNode deopt,
                    ArrayList<ArrayList<Position>> vectorPositions, ArrayList<VectorNode> stateVectors) {
        this(condition, conditionLength, direction, deoptBranch, deoptProbability, deopt.getAction(), deopt.getReason(), DeoptimizeNode.DEFAULT_DEBUG_ID, deopt.getSpeculation(), deopt.stateBefore(),
                        vectorPositions, stateVectors);
    }

    public VectorGuardNode(VectorLogicNode condition, ValueNode conditionLength, Direction direction, DeoptBranch deoptBranch, double deoptProbability, DeoptimizationAction action,
                    DeoptimizationReason reason, int debugId, Speculation speculation, FrameState stateBefore, ArrayList<ArrayList<Position>> vectorPositions, ArrayList<VectorNode> stateVectors) {
        super(TYPE, StampFactory.forVoid(), stateBefore);
        this.condition = condition;
        this.conditionLength = conditionLength;
        this.direction = direction;
        this.deoptBranch = deoptBranch;
        this.deoptProbability = deoptProbability;
        this.action = action;
        this.reason = reason;
        this.debugId = debugId;
        this.speculation = speculation;
        if (Assertions.assertionsEnabled()) {
            int totalVectorPositions = 0;
            for (ArrayList<Position> positions : vectorPositions) {
                totalVectorPositions += positions.size();
            }
            assert totalVectorPositions == stateVectors.size() : "need exactly one vector per vector position";
        }
        this.vectorPositions = vectorPositions;
        this.stateVectors = new NodeInputList<>(this, CollectionsUtil.mapToArray(stateVectors, vector -> vector.asNode(), n -> new ValueNode[n]));
        this.trustedBodyIterations = -1;
    }

    public VectorLogicNode getCondition() {
        return (VectorLogicNode) condition;
    }

    @Override
    public ValueNode getLength() {
        return conditionLength;
    }

    @Override
    public Direction direction() {
        return direction;
    }

    public DeoptBranch getDeoptBranch() {
        return deoptBranch;
    }

    public double getDeoptProbability() {
        return deoptProbability;
    }

    public DeoptimizationAction getAction() {
        return action;
    }

    public DeoptimizationReason getReason() {
        return reason;
    }

    public int getDebugId() {
        return debugId;
    }

    public Speculation getSpeculation() {
        return speculation;
    }

    @Override
    public VectorLoopMarkerNode vectorLoopMarker() {
        return vectorLoopMarker;
    }

    @Override
    public void setVectorLoopMarker(VectorLoopMarkerNode vectorLoopMarker) {
        GraalError.guarantee(this.vectorLoopMarker == null, "vectorLoopMarker may only be set once");
        updateUsages(this.vectorLoopMarker, vectorLoopMarker);
        this.vectorLoopMarker = vectorLoopMarker;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch) {
        if (condition instanceof InvariantVectorLogicNode) {
            // No restriction on the vector length, this will be expanded to scalar code.
            return arch.getMaxVectorLength();
        }
        return getCondition().getMaxVectorLength(arch, arch.getMaxLogicVectorLength(getCondition().getElementStamp()));
    }

    @Override
    @SuppressWarnings("try")
    public void simplifyTree(VectorSimplifier simplifier) {
        VectorNode newCondition = simplifier.simplify(getCondition());
        if (condition != newCondition) {
            updateUsages(condition, newCondition.asNode());
            condition = newCondition.asNode();
        }

        try (DebugCloseable position = withNodeSourcePosition()) {
            LogicNode invariantCondition = ((VectorLogicNode) condition).invariantCondition();
            /*
             * Replace vector guards with loop invariant conditions by a simple scalar if. If the
             * guard is part of a vector loop, we can only do this if it is the first member of the
             * loop: A loop's consumers must be adjacent in the graph because we need to replace
             * them all together by an expanded vector loop. Simplifying any but the first guard
             * would introduce control flow between these consumers, breaking the adjacency
             * invariant.
             *
             * To not interfere with decisions from SpeculativeGuardMovement, this optimization is
             * only performed if the loop body is known to be executed more than once (i.e., there
             * is a benefit from moving the guard to before the loop) and if there is a proper
             * speculation reason attached.
             */
            if (invariantCondition != null && (!isPartOfALoop() || this == vectorLoop().getConsumers().get(0)) && hasMultipleIterations() &&
                            !(speculation.getReason() instanceof SpeculationLog.NoSpeculationReason)) {
                List<? extends ValueNode> vectorInputs = getVectorInputs();
                boolean allStateVectorsSimplifiable = true;
                for (ValueNode stateVector : stateVectors) {
                    if (!(stateVector instanceof SequenceVectorNode)) {
                        allStateVectorsSimplifiable = false;
                        break;
                    }
                }
                if (allStateVectorsSimplifiable) {
                    ValueNode[] inputs = vectorInputs.toArray(new ValueNode[vectorInputs.size()]);
                    replaceByIf(invariantCondition, inputs);
                    return;
                }
            }

            splitOnConcatInput(simplifier);
        }
    }

    /**
     * Returns true if the loop body is executed more than once. Otherwise, moving an invariant
     * guard out of the loop would not be beneficial.
     */
    private boolean hasMultipleIterations() {
        return trustedBodyIterations > 1;
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        ArrayList<ValueNode> inputs = new ArrayList<>(1 + stateVectors.size());
        inputs.add(condition.asNode());
        inputs.addAll(stateVectors);
        return inputs;
    }

    @Override
    @SuppressWarnings("try")
    public ValueNode simdify(VectorArchitecture arch, ValueNode... inputs) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            assert inputs.length == 1 + stateVectors.size() : inputs + " " + stateVectors;
            assert getLength().isJavaConstant() && getLength().asJavaConstant().getJavaKind() == JavaKind.Int : getLength();

            ValueNode vectorCondition = inputs[0];
            LogicNode scalarCondition;
            if (vectorCondition instanceof VectorLogicNode vectorLogic) {
                scalarCondition = vectorLogic instanceof InvariantVectorLogicNode ? vectorLogic.asScalar() : computeScalarCondition(vectorCondition);
            } else if (vectorCondition instanceof SimdPrimitiveCompareNode simdCompare) {
                scalarCondition = computeScalarCondition(simdCompare);
            } else if (vectorCondition instanceof LogicNode scalarLogic) {
                scalarCondition = scalarLogic;
            } else {
                throw GraalError.shouldNotReachHere("unexpected condition: " + vectorCondition);
            }
            IfNode ifNode = replaceByIf(scalarCondition, inputs);

            return ifNode;
        }
    }

    protected static void fixUpFrameState(FrameState state, Direction consumerDirection, ValueNode[] inputs, int inputOffset, NodeInputList<ValueNode> stateVectors,
                    ArrayList<ArrayList<Position>> vectorPositions) {
        // Fix up the deopt's frame state: Iterate over our saved state vectors. These should
        // correspond to positions in the state where vector nodes are stored. Replace those by
        // their simdified versions.
        assert inputs.length == inputOffset + stateVectors.size() : inputs + " " + stateVectors;
        FrameState currentState = state;
        int stateIndex = 0;
        for (ArrayList<Position> currentVectorPositions : vectorPositions) {
            for (Position inputPosition : currentVectorPositions) {
                ValueNode simdifiedStateVector = inputs[inputOffset + stateIndex];
                Node stateInput = currentState.getInput(inputPosition);
                if (stateInput instanceof SequenceVectorNode) {
                    ValueNode scalarState = firstComponentOfSequence(simdifiedStateVector, consumerDirection);
                    inputPosition.set(currentState, scalarState);
                } else if (stateInput instanceof VectorNode) {
                    GraalError.shouldNotReachHere("unexpected vector node in frame state: " + stateInput); // ExcludeFromJacocoGeneratedReport
                }
                stateIndex++;
            }
            currentState = currentState.outerFrameState();
        }
    }

    private LogicNode computeScalarCondition(ValueNode vectorCondition) {
        int vectorLength = getLength().asJavaConstant().asInt();
        LogicNode scalarCondition;
        if (vectorLength == 1 && vectorCondition instanceof VectorLogicNode vectorLogic) {
            scalarCondition = vectorLogic.asScalar();
        } else if (vectorLength == 1 && vectorCondition instanceof SimdPrimitiveCompareNode simdLogic) {
            scalarCondition = simdLogic.asScalar();
        } else {
            SimdMaskLogicNode.Condition maskCondition;
            boolean negate;
            if (deoptBranch == DeoptBranch.TRUE_DEOPT_BRANCH) {
                // Deopt if any of the conditions in the vector are true, i.e., if the mask is not
                // equal to zero.
                maskCondition = SimdMaskLogicNode.Condition.ALL_ZEROS;
                negate = true;
            } else {
                assert deoptBranch == DeoptBranch.FALSE_DEOPT_BRANCH : deoptBranch + " " + vectorCondition;
                // Deopt if any of the conditions in the vector are false, i.e., if the mask is
                // not all ones.
                maskCondition = SimdMaskLogicNode.Condition.ALL_ONES;
                negate = false;
            }
            scalarCondition = new SimdMaskLogicNode(vectorCondition, maskCondition);
            if (negate) {
                scalarCondition = LogicNegationNode.create(scalarCondition);
            }
            scalarCondition = graph().addOrUniqueWithInputs(scalarCondition);
        }
        return scalarCondition;
    }

    private IfNode replaceByIf(LogicNode scalarCondition, ValueNode... inputs) {
        DeoptimizeNode deopt = cloneDeoptAndState(graph());
        if (inputs.length > 0) {
            FrameState state = deopt.stateBefore();
            fixUpFrameState(state, direction(), inputs, 1, stateVectors, vectorPositions);
        }

        FixedWithNextNode pred = (FixedWithNextNode) this.predecessor();
        FixedNode succ = this.next();
        VectorLoopNode loop = this.vectorLoop();
        if (loop != null) {
            assert this == loop.getConsumers().get(0) : this + " " + loop.getConsumers();
            if (loop.getConsumers().size() == 1) {
                succ = loop.next();
            }
            loop.removeConsumer(this);
        }
        if (hasUsages()) {
            BeginNode guardAnchor = graph().add(new BeginNode());
            graph().addBeforeFixed(succ, guardAnchor);
            this.replaceAtUsages(guardAnchor);
            succ = guardAnchor;
        }
        graph().removeFixed(this);

        FixedNode trueBranch = (deoptBranch == DeoptBranch.TRUE_DEOPT_BRANCH ? deopt : succ);
        FixedNode falseBranch = (deoptBranch == DeoptBranch.FALSE_DEOPT_BRANCH ? deopt : succ);
        double trueProbability = (deoptBranch == DeoptBranch.TRUE_DEOPT_BRANCH ? deoptProbability : 1 - deoptProbability);
        pred.setNext(null);
        IfNode ifNode = graph().add(new IfNode(scalarCondition, trueBranch, falseBranch, BranchProbabilityData.injected(trueProbability)));
        pred.setNext(ifNode);

        return ifNode;
    }

    /**
     * Extracts the first component of the sequence (vectorized IV) computation given by
     * {@code simdifiedValue}. Here, "first" refers to the value that is seen on the earliest loop
     * iteration in the scalar version of the code. This is the <em>last</em> element of a SIMD
     * value if the {@code consumerDirection} is {@link Direction#Down}.
     */
    private static ValueNode firstComponentOfSequence(ValueNode simdifiedValue, Direction consumerDirection) {
        if (simdifiedValue.stamp(NodeView.DEFAULT) instanceof SimdStamp simdStamp) {
            if (consumerDirection == Direction.Up) {
                return simdifiedValue.graph().addOrUnique(new SimdCutNode(simdifiedValue, 1));
            } else {
                return simdifiedValue.graph().addOrUnique(new SimdCutNode(simdifiedValue, simdStamp.getVectorLength() - 1, 1));
            }
        } else if (simdifiedValue.stamp(NodeView.DEFAULT) instanceof VectorStamp) {
            return ((SequenceVectorNode) simdifiedValue).getInitial();
        } else {
            // Already a scalar.
            return simdifiedValue;
        }
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator createInitialIterator(TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            ValueNode index = ConstantNode.forIntegerKind(target.wordJavaKind, 0, graph());
            AnchoringNode anchor = getAnchor();
            VectorIterator conditionIterator = VectorInitialIteratorNode.createInitialIterator(getCondition(), anchor, target);
            ArrayList<VectorIterator> stateVectorIterators = VectorGuardIterator.createInitialIterators(getStateVectors(), anchor, target);
            return new VectorGuardIterator(index, conditionIterator, stateVectorIterators);
        }
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator createPhiIterator(int minInputStepLength, int maxInputStepLength, PhiNode phi, TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            AbstractMergeNode merge = phi.merge();
            ValueNode index = graph().addOrUnique(new ValuePhiNode(StampFactory.forKind(target.wordJavaKind), merge));
            AnchoringNode anchor = getAnchor();
            VectorIterator conditionIterator = VectorInitialIteratorNode.createPhiIterator(merge, getCondition(), anchor, target);
            ArrayList<VectorIterator> stateVectorIterators = VectorGuardIterator.createPhiIterators(getStateVectors(), merge, anchor, target);
            return new VectorGuardIterator(index, conditionIterator, stateVectorIterators);
        }
    }

    @Override
    public void lower(LowTierContext context) {
        ValueNode wordLength = IntegerConvertNode.convertUnsigned(conditionLength, StampFactory.forUnsignedInteger(context.getTarget().wordSize * 8), graph(), NodeView.DEFAULT);
        setConditionLength(wordLength);
    }

    @Override
    public boolean getSupportsAlignment() {
        return false;
    }

    // Build a new deopt that is just like the original one.
    private DeoptimizeNode cloneDeopt() {
        return new DeoptimizeNode(action, reason, debugId, speculation, stateBefore);
    }

    // Build a new deopt that is just like the original one, but with the frame state cloned as
    // well.
    private DeoptimizeNode cloneDeoptAndState() {
        return new DeoptimizeNode(action, reason, debugId, speculation, stateBefore.duplicateWithVirtualState());
    }

    private DeoptimizeNode cloneDeoptAndState(StructuredGraph graph) {
        return graph.addWithoutUnique(cloneDeoptAndState());
    }

    @Override
    public void setLoopAnchor(AnchoringNode anchor) {
        loopAnchor = anchor;
    }

    @Override
    public AnchoringNode getLoopAnchor() {
        return loopAnchor;
    }

    public ArrayList<ArrayList<Position>> getVectorPositions() {
        return vectorPositions;
    }

    public ArrayList<VectorNode> getStateVectors() {
        ArrayList<VectorNode> ret = new ArrayList<>(stateVectors.size());
        for (ValueNode stateVector : stateVectors) {
            ret.add((VectorNode) stateVector);
        }
        return ret;
    }

    private void setConditionLength(ValueNode newLength) {
        updateUsages(conditionLength, newLength);
        conditionLength = newLength;
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    /**
     * This method handles the case that a {@link VectorGuardNode}'s condition has at least one
     * {@link ConcatVectorNode} as one of its inputs. In such a case, we need to do the following
     * transformation:
     *
     * <p>
     * before:
     *
     * <pre>
     * VectorGuardNode(condition(ConcatVectorNode(A, B)), originalConditionLength)
     * </pre>
     *
     * after:
     *
     * <pre>
     * remainingLength = originalConditionLength
     * conditionALength = min(A.length, remainingLength)
     * VectorGuardNode(condition(A), conditionALength)
     *
     * remainingLength = remainingLength - conditionALength
     * conditionBLength = min(B.length, remainingLength)
     * VectorGuardNode(condition(B), conditionBLength)
     * </pre>
     *
     * If a {@link FoldVectorNode} has N > 1 {@link ConcatVectorNode} inputs, we need to generate
     * all 2 ^ N possible combinations in the same way as for {@link FoldVectorNode} (see there for
     * an example). Combinatorial explosions are avoided by limiting the number of concats in vector
     * materialization.
     */
    private void splitOnConcatInput(VectorSimplifier simplifier) {
        SplitConcatUtil.CombinationTable combinationTable = new SplitConcatUtil.CombinationTable();
        List<ValueNode> vectorInputs = new ArrayList<>();
        for (Node input : getCondition().inputs()) {
            if (input instanceof VectorNode) {
                vectorInputs.add(((VectorNode) input).asNode());
                if (input instanceof ConcatVectorNode) {
                    combinationTable.add((ConcatVectorNode) input);
                }
            }
        }

        // create guards for all possible input combinations
        ArrayList<ArrayList<SplitConcatUtil.VectorEntry>> combinations = combinationTable.rows;
        if (!combinations.isEmpty()) {
            assert !this.isPartOfALoop() : "cannot handle vector concats propagated into vector loops";
            List<ValueNode> currentVectorInputs = vectorInputs;
            ArrayList<VectorGuardNode> createdGuardNodes = new ArrayList<>();
            ConstantReflectionProvider constantReflection = simplifier.getConstantReflection();
            ValueNode remainingLength = this.conditionLength;
            for (int i = 0; i < combinations.size(); i++) {
                ArrayList<SplitConcatUtil.VectorEntry> concatInputs = combinations.get(i);
                List<ValueNode> vectorGuardInputs = SplitConcatUtil.getVectorInputsForNextOperation(currentVectorInputs, concatInputs);
                ValueNode nextLength = SplitConcatUtil.minLength(graph(), constantReflection, concatInputs, remainingLength);
                VectorLogicNode nextCondition = getCondition().createCopyDefaultInsertionPosition(vectorGuardInputs.toArray(new ValueNode[vectorGuardInputs.size()]));
                VectorGuardNode nextGuard = graph().add(new VectorGuardNode(nextCondition, nextLength, direction(), deoptBranch, deoptProbability, cloneDeopt(), getVectorPositions(),
                                getStateVectors()));
                graph().addBeforeFixed(this, nextGuard);
                createdGuardNodes.add(nextGuard);

                SplitConcatUtil.shiftInputs(graph(), constantReflection, currentVectorInputs, concatInputs, nextLength);
                remainingLength = BinaryArithmeticNode.sub(graph(), remainingLength, nextLength, NodeView.DEFAULT);
            }

            // remove the existing guard
            graph().removeFixed(this);

            // trigger a recursive simplification of the newly generated guard nodes
            for (VectorGuardNode createdGuardNode : createdGuardNodes) {
                createdGuardNode.simplifyTree(simplifier);
            }
        }
    }

    @Override
    public double trustedBodyIterations() {
        return trustedBodyIterations;
    }

    @Override
    public void setTrustedBodyIterations(double trustedBodyIterations) {
        this.trustedBodyIterations = trustedBodyIterations;
    }
}
