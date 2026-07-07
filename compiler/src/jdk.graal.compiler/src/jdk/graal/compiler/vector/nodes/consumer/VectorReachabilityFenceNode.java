/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorReachabilityFenceIterator;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.java.ReachabilityFenceNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.vm.ci.code.TargetDescription;

/**
 * Vectorized version of {@link ReachabilityFenceNode}.
 */
//@formatter:off
@NodeInfo(allowedUsageTypes = {Association},
          cycles = CYCLES_IGNORED,
          cyclesRationale = "No code is generated for this node.",
          size = SIZE_IGNORED,
          sizeRationale = "No code is generated for this node.")
//@formatter:on
public class VectorReachabilityFenceNode extends FixedWithNextNode implements LowerableVectorConsumer, SimdifyableVectorOperation {

    public static final NodeClass<VectorReachabilityFenceNode> TYPE = NodeClass.create(VectorReachabilityFenceNode.class);

    @Input ValueNode vectorLength;
    @Input NodeInputList<ValueNode> objectVectors;
    /** @see LowerableVectorConsumer#vectorLoopMarker() */
    @OptionalInput(InputType.Association) VectorLoopMarkerNode vectorLoopMarker;

    private final Direction direction;
    private AnchoringNode loopAnchor;
    private double trustedBodyIterations;

    @SuppressWarnings("this-escape")
    public VectorReachabilityFenceNode(ValueNode vectorLength, Direction direction, ValueNode[] objectVectors) {
        super(TYPE, StampFactory.forVoid());
        this.vectorLength = vectorLength;
        this.direction = direction;
        this.objectVectors = new NodeInputList<>(this, objectVectors);
        this.trustedBodyIterations = -1;
    }

    @Override
    public ValueNode getLength() {
        return vectorLength;
    }

    @Override
    public Direction direction() {
        return direction;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch) {
        int maxLength = arch.getMaxVectorLength();
        for (ValueNode object : objectVectors) {
            VectorStamp objectStamp = (VectorStamp) object.stamp(NodeView.DEFAULT);
            maxLength = Math.min(maxLength, arch.getMaxVectorLength(objectStamp.getElementStamp()));
        }
        return maxLength;
    }

    @Override
    public void simplifyTree(VectorSimplifier simplifier) {
        /*
         * If all inputs are FillVectors, replace this node by a scalar ReachabilityFence. The
         * scalar fence is placed after this vector node or its vector loop. This has the same
         * effect as if we had sunk the original fence after the original loop.
         */
        boolean allFills = true;
        for (int i = 0; i < objectVectors.size(); i++) {
            VectorNode input = (VectorNode) objectVectors.get(i);
            VectorNode simplified = simplifier.simplifyLengthHint(input, vectorLength);
            if (simplified != input) {
                objectVectors.set(i, simplified.asNode());
            }

            allFills &= simplified instanceof FillVectorNode;
        }
        if (allFills) {
            ValueNode[] scalars = new ValueNode[objectVectors.size()];
            for (int i = 0; i < objectVectors.size(); i++) {
                scalars[i] = ((FillVectorNode) objectVectors.get(i)).getElement();
            }
            ReachabilityFenceNode scalarFence = graph().add(ReachabilityFenceNode.create(scalars));
            VectorLoopNode loop = this.vectorLoop();
            if (loop != null) {
                graph().addAfterFixed(loop, scalarFence);
                loop.removeConsumer(this);
            } else {
                graph().addAfterFixed(this, scalarFence);
            }
            graph().removeFixed(this);
        }
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        return objectVectors;
    }

    public ArrayList<VectorNode> getObjectVectors() {
        ArrayList<VectorNode> ret = new ArrayList<>();
        for (ValueNode objectVector : objectVectors) {
            ret.add((VectorNode) objectVector);
        }
        return ret;
    }

    @Override
    @SuppressWarnings("try")
    public ValueNode simdify(VectorArchitecture arch, ValueNode... inputs) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            ReachabilityFenceNode simdFence = graph().add(ReachabilityFenceNode.create(inputs));
            graph().replaceFixedWithFixed(this, simdFence);
            return simdFence;
        }
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator createInitialIterator(TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            ValueNode index = ConstantNode.forIntegerKind(target.wordJavaKind, 0, graph());
            AnchoringNode anchor = getAnchor();
            ArrayList<VectorIterator> objectVectorIterators = VectorReachabilityFenceIterator.createInitialIterators(getObjectVectors(), anchor, target);
            return new VectorReachabilityFenceIterator(index, objectVectorIterators);
        }
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator createPhiIterator(int minInputStepLength, int maxInputStepLength, PhiNode phi, TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            AbstractMergeNode merge = phi.merge();
            ValueNode index = graph().addOrUnique(new ValuePhiNode(StampFactory.forKind(target.wordJavaKind), merge));
            AnchoringNode anchor = getAnchor();
            ArrayList<VectorIterator> objectVectorIterators = VectorReachabilityFenceIterator.createPhiIterators(getObjectVectors(), merge, anchor, target);
            return new VectorReachabilityFenceIterator(index, objectVectorIterators);
        }
    }

    @Override
    public void lower(LowTierContext context) {
        ValueNode wordLength = IntegerConvertNode.convertUnsigned(vectorLength, StampFactory.forUnsignedInteger(context.getTarget().wordSize * 8), graph(), NodeView.DEFAULT);
        setVectorLength(wordLength);
    }

    private void setVectorLength(ValueNode newLength) {
        updateUsages(vectorLength, newLength);
        vectorLength = newLength;
    }

    @Override
    public boolean getSupportsAlignment() {
        return true;
    }

    @Override
    public VectorLoopMarkerNode vectorLoopMarker() {
        return vectorLoopMarker;
    }

    @Override
    public void setVectorLoopMarker(VectorLoopMarkerNode vectorLoopMarker) {
        updateUsages(this.vectorLoopMarker, vectorLoopMarker);
        this.vectorLoopMarker = vectorLoopMarker;
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
