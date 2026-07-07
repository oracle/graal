/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorSafepointIterator;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizingFixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.code.TargetDescription;

/**
 * A node that models a safepoint in a vectorized loop. It is turned back into a normal
 * {@link SafepointNode} during simdification. Vector values in the frame state are managed by
 * remembering their positions and simidifying them and fixing up the frame state as in
 * {@link VectorGuardNode}.
 */
//@formatter:off
@NodeInfo(allowedUsageTypes = {Association},
       cycles = CYCLES_UNKNOWN,
       cyclesRationale = "We cannot argue about vector nodes statically.",
       size = SIZE_UNKNOWN,
       sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public class VectorSafepointNode extends DeoptimizingFixedWithNextNode implements LowerableVectorConsumer, SimdifyableVectorOperation {

    public static final NodeClass<VectorSafepointNode> TYPE = NodeClass.create(VectorSafepointNode.class);

    @Input ValueNode length;
    @Input NodeInputList<ValueNode> stateVectors;
    /** @see LowerableVectorConsumer#vectorLoopMarker() */
    @OptionalInput(Association) VectorLoopMarkerNode vectorLoopMarker;

    private final Direction direction;
    private AnchoringNode loopAnchor;
    private final ArrayList<ArrayList<Position>> vectorPositions;
    private double trustedBodyIterations;

    @SuppressWarnings("this-escape")
    public VectorSafepointNode(ValueNode length, Direction direction, FrameState stateBefore, ArrayList<ArrayList<Position>> vectorPositions, ArrayList<VectorNode> stateVectors) {
        super(TYPE, StampFactory.forVoid(), stateBefore);
        this.length = length;
        this.direction = direction;
        this.vectorPositions = vectorPositions;
        this.stateVectors = new NodeInputList<>(this, CollectionsUtil.mapToArray(stateVectors, vector -> vector.asNode(), n -> new ValueNode[n]));
        this.trustedBodyIterations = -1;
    }

    @Override
    public ValueNode getLength() {
        return length;
    }

    @Override
    public Direction direction() {
        return direction;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch) {
        // This node should not restrict simdification in any way, so use the architecture's largest
        // possible vector length.
        return arch.getMaxVectorLength();
    }

    @Override
    public void simplifyTree(VectorSimplifier simplifier) {
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        ArrayList<ValueNode> inputs = new ArrayList<>();
        inputs.addAll(stateVectors);
        return inputs;
    }

    public ArrayList<VectorNode> getStateVectors() {
        ArrayList<VectorNode> ret = new ArrayList<>();
        for (ValueNode stateVector : stateVectors) {
            ret.add((VectorNode) stateVector);
        }
        return ret;
    }

    public ArrayList<ArrayList<Position>> getVectorPositions() {
        return vectorPositions;
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
    public boolean canDeoptimize() {
        return true;
    }

    @Override
    @SuppressWarnings("try")
    public ValueNode simdify(VectorArchitecture arch, ValueNode... inputs) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            FrameState newState = stateBefore.duplicateWithVirtualState();
            // VectorGuardNode captures the first element in an SIMD vector while we capture the
            // last, simply pass direction().opposite() would achieve the desired effect
            VectorGuardNode.fixUpFrameState(newState, direction().opposite(), inputs, 0, stateVectors, vectorPositions);
            SafepointNode safepoint = graph().add(new SafepointNode());

            safepoint.setStateBefore(newState);
            graph().replaceFixedWithFixed(this, safepoint);

            return safepoint;
        }
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator createInitialIterator(TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            ValueNode index = ConstantNode.forIntegerKind(target.wordJavaKind, 0, graph());
            AnchoringNode anchor = getAnchor();
            ArrayList<VectorIterator> stateVectorIterators = VectorSafepointIterator.createInitialIterators(getStateVectors(), anchor, target);
            return new VectorSafepointIterator(index, stateVectorIterators);
        }
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator createPhiIterator(int minInputStepLength, int maxInputStepLength, PhiNode phi, TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            AbstractMergeNode merge = phi.merge();
            ValueNode index = graph().addOrUnique(new ValuePhiNode(StampFactory.forKind(target.wordJavaKind), merge));
            AnchoringNode anchor = getAnchor();
            ArrayList<VectorIterator> stateVectorIterators = VectorSafepointIterator.createPhiIterators(getStateVectors(), merge, anchor, target);
            return new VectorSafepointIterator(index, stateVectorIterators);
        }
    }

    @Override
    public void lower(LowTierContext context) {
        Stamp wordStamp = IntegerStamp.create(context.getTarget().wordSize * 8);
        ValueNode totalLength = IntegerConvertNode.convertUnsigned(length, wordStamp, graph(), NodeView.DEFAULT);
        setLength(totalLength);
    }

    private void setLength(ValueNode newLength) {
        updateUsages(length, newLength);
        length = newLength;
    }

    @Override
    public boolean getSupportsAlignment() {
        return true;
    }

    @Override
    public void setLoopAnchor(AnchoringNode anchor) {
        loopAnchor = anchor;
    }

    @Override
    public AnchoringNode getLoopAnchor() {
        return loopAnchor;
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
