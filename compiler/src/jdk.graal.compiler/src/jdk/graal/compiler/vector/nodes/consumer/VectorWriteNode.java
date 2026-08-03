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
package jdk.graal.compiler.vector.nodes.consumer;

import static jdk.graal.compiler.nodeinfo.InputType.Association;
import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.InputType.State;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;
import static org.graalvm.word.LocationIdentity.INIT_LOCATION;

import java.util.Collections;
import java.util.List;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;

import jdk.graal.compiler.nodes.extended.LoadAddressNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorOperation;
import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.VectorAccess;
import jdk.graal.compiler.vector.nodes.VectorNode;
import jdk.graal.compiler.vector.nodes.lowered.VectorInitialIteratorNode;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorWriteIterator;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.IterableNodeType;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNegationNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.ConditionalNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.ArrayRangeWrite;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode.Address;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.replacements.nodes.ZeroMemoryNode;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * Writes a vector value to memory.
 */
//@formatter:off
@NodeInfo(allowedUsageTypes = {Memory, Association},
          cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot argue about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public final class VectorWriteNode extends FixedAccessNode implements LowerableVectorConsumer, SimdifyableVectorOperation, IterableNodeType, SingleMemoryKill, MemoryAccess, VectorAccess,
                StateSplit, ArrayRangeWrite, GuardedNode {

    public static final NodeClass<VectorWriteNode> TYPE = NodeClass.create(VectorWriteNode.class);
    @Input ValueNode vector;
    @Input ValueNode length;
    @OptionalInput(State) FrameState stateAfter;
    /** @see LowerableVectorConsumer#vectorLoopMarker() */
    @OptionalInput(Association) VectorLoopMarkerNode vectorLoopMarker;

    private AnchoringNode loopAnchor;

    protected final int elementStride;
    protected final boolean isInitialization;
    private double trustedBodyIterations;
    private boolean shouldAlign = true;

    public VectorWriteNode(AddressNode address, LocationIdentity location, ValueNode vector, ValueNode length, int elementStride, boolean isInitialization, BarrierType barrierType) {
        this(address, location, vector, length, elementStride, isInitialization, null, barrierType);
    }

    public VectorWriteNode(AddressNode address, LocationIdentity location, ValueNode vector, ValueNode length, int elementStride, boolean isInitialization, GuardingNode guard,
                    BarrierType barrierType) {
        super(TYPE, address, isInitialization && !location.isInit() ? INIT_LOCATION : location, StampFactory.forVoid(), barrierType);
        this.vector = vector;
        this.length = length;
        this.elementStride = elementStride;
        this.isInitialization = isInitialization;
        this.guard = guard;
        this.loopAnchor = null;
        this.trustedBodyIterations = -1;
    }

    /**
     * An initializing write, repeating the primitive {@code initialValue} to fill the vector.
     */
    public VectorWriteNode(ValueNode address, ValueNode initialValue, ValueNode length) {
        this((AddressNode) address, LocationIdentity.init(), new FillVectorNode(initialValue), length, PrimitiveStamp.getBits(initialValue.stamp(NodeView.DEFAULT)) / 8, true, BarrierType.NONE);
        if (initialValue.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp) {
            GraalError.guarantee(((AbstractObjectStamp) initialValue.stamp(NodeView.DEFAULT)).alwaysNull(), "initializing vector write without a barrier must not write non-null references");
        }
    }

    @NodeIntrinsic
    public static native void initializingWrite(Address address, long initialValue, UnsignedWord length);

    public VectorNode getVector() {
        return (VectorNode) vector;
    }

    private void setLength(ValueNode newLength) {
        updateUsages(length, newLength);
        length = newLength;
    }

    @Override
    public ValueNode getLength() {
        return length;
    }

    @Override
    public Direction direction() {
        /*
         * Other vector consumers just need a direction field. Here in VectorWrite we also need a
         * precise stride for other reasons too, therefore we compute the direction from the stride
         * and don't track a separate field for it.
         */
        return elementStride < 0 ? Direction.Down : Direction.Up;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch) {
        return arch.getMaxVectorLength(getVector().getVectorStamp().getElementStamp());
    }

    public void setVector(VectorNode newVector) {
        updateUsages(vector, newVector.asNode());
        vector = newVector.asNode();
    }

    @Override
    public int getElementStride() {
        return elementStride;
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        return Collections.singletonList(vector);
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
    public boolean getSupportsAlignment() {
        return shouldAlign;
    }

    public void disableAlignment() {
        shouldAlign = false;
    }

    @Override
    public boolean isIdempotent() {
        /*
         * A write is idempotent if we can ensure that we can't read our own writes. This is always
         * true if we're writing to newly allocated memory, and also if the thing we're filling with
         * is a constant.
         */
        if (isInitialization) {
            return true;
        }
        if (vector instanceof FillVectorNode fillVector && fillVector.getElement().isConstant()) {
            return true;
        }
        return false;
    }

    private static ValueNode min(ValueNode x, ValueNode y, ConstantReflectionProvider constantReflection) {
        StructuredGraph graph = x.graph();
        LogicNode condition = CompareNode.createCompareNode(graph, CanonicalCondition.BT, x, y, constantReflection, NodeView.DEFAULT);
        ValueNode min = graph.addOrUniqueWithInputs(ConditionalNode.create(condition, x, y, NodeView.DEFAULT));
        return min;
    }

    @Override
    @SuppressWarnings("try")
    public void lower(LowTierContext context) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            if (getLastLocationAccess() == null) {
                /*
                 * This can happen for vector writes built in invocation plugins because we don't
                 * want to have memory edges in the high tier.
                 */
                MemoryAnchorNode anchor = graph().add(new MemoryAnchorNode());
                graph().addBeforeFixed(this, anchor);
                setLastLocationAccess(anchor);
            }

            Stamp wordStamp = IntegerStamp.create(context.getTarget().wordSize * 8);
            ValueNode totalWriteLength = IntegerConvertNode.convertUnsigned(length, wordStamp, graph(), NodeView.DEFAULT);

            if (vector instanceof ConcatVectorNode) {
                ConcatVectorNode concat = (ConcatVectorNode) vector;

                // Write min(xLength, totalWriteLength) elements as a prefix of X. This avoids
                // writing too much if xLength > totalWriteLength.
                ValueNode xLength = IntegerConvertNode.convert(concat.getXLength(), wordStamp, graph(), NodeView.DEFAULT);
                ValueNode xPrefixLength = min(xLength, totalWriteLength, context.getConstantReflection());
                VectorWriteNode writeX = graph().add(new VectorWriteNode(getAddress(), getLocationIdentity(), concat.x().asNode(), xPrefixLength, elementStride, isInitialization, guard, barrierType));

                // Write any elements of Y needed to get to the full write length. This only writes
                // anything if totalWriteLength > xLength, i.e., we have consumed all of X and need
                // to consume part of Y. In that case we have xLength == xPrefixLength. Continue to
                // use xLength here because it's a simpler expression and leads to better code in
                // some cases.
                assert CodeUtil.isPowerOf2(elementStride) : elementStride;
                ValueNode yScaledIndex = graph().unique(new LeftShiftNode(xLength, ConstantNode.forInt(CodeUtil.log2(elementStride), graph())));

                AddressNode yAddress = graph().unique(new OffsetAddressNode(getAddress(), yScaledIndex));
                ValueNode yLength = BinaryArithmeticNode.sub(graph(), totalWriteLength, xLength, NodeView.DEFAULT);
                VectorWriteNode writeY = graph().add(new VectorWriteNode(yAddress, getLocationIdentity(), concat.y().asNode(), yLength, elementStride, isInitialization, guard, barrierType));

                writeX.setLastLocationAccess(getLastLocationAccess());
                writeY.setLastLocationAccess(writeX);

                assert !this.isPartOfALoop() : "this write should have been split out of its vector loop at the beginning of vector lowering";
                graph().addBeforeFixed(this, writeX);
                graph().replaceFixed(this, writeY);
                return;
            } else if (this.isPartOfALoop()) {
                /**
                 * This write is zeroing an array, and it is part of a vector loop generated for a
                 * multi-write loop, something like:
                 *
                 * <pre>
                 * for (...) {
                 *     a[i] = 0;
                 *     b[i] = f(c[i]);
                 * }
                 * </pre>
                 *
                 * We cannot use bulk zeroing here because it would try to put a ZeroMemoryNode
                 * inside the vectorized multi-write loop, and eventually expand that node to a
                 * nested vector zeroing loop. Instead, fall through to normal vectorization, which
                 * will execute the zeroing operations in lockstep with the other vector writes in
                 * the loop.
                 */
            } else {
                if (isInitialization() && vector instanceof FillVectorNode fillVector && context.getLowerer().supportsBulkClearArray(fillVector.getElement().getStackKind())) {
                    ValueNode content = fillVector.getElement();
                    if (content.isConstant() && content.asJavaConstant().isDefaultForKind()) {
                        /*
                         * Use bulk zeroing if appropriate. There is a trade-off between vectorized
                         * zeroing code and bulk zeroing, which on AMD64 uses a rep stosb
                         * instruction. rep stosb is slower than AVX2 stores on buffer sizes up to
                         * about 2-4 KB, but it is faster on buffers above this threshold. The
                         * difference is often about 10% either way, so it pays off to have a
                         * dynamic check on the zeroing size to dispatch to best expected version.
                         */
                        assert CodeUtil.isPowerOf2(elementStride) : elementStride;
                        int minimalBulkZeroingSize = GraalOptions.MinimalBulkZeroingSize.getValue(graph().getOptions());
                        assert NumUtil.assertNonNegativeInt(minimalBulkZeroingSize);

                        ValueNode shift = ConstantNode.forInt(CodeUtil.log2(elementStride), graph());
                        ValueNode zeroingSize = graph().addOrUniqueWithInputs(LeftShiftNode.create(totalWriteLength, shift, NodeView.DEFAULT));

                        LogicNode lessThanThreshold = graph().addOrUniqueWithInputs(IntegerLessThanNode.create(zeroingSize, ConstantNode.forLong(minimalBulkZeroingSize), NodeView.DEFAULT));
                        LogicNode exceedsThreshold = graph().addOrUniqueWithInputs(LogicNegationNode.create(lessThanThreshold));

                        if (exceedsThreshold.isTautology()) {
                            ZeroMemoryNode zeroMemoryNode = graph().addOrUniqueWithInputs(new ZeroMemoryNode(getAddress(), zeroingSize, false, INIT_LOCATION, BarrierType.NONE));
                            graph().replaceFixedWithFixed(this, zeroMemoryNode);
                            return;
                        }
                        /*
                         * Filling memory only benefits from alignment for iteration counts bigger
                         * than the bulk zeroing size, so we can disable it for the non-bulk
                         * vectorized path.
                         */
                        disableAlignment();
                        if (exceedsThreshold.isContradiction()) {
                            // At this point, we know the zeroing size is less than the minimal
                            // bulk zeroing size. Fall through to normal vectorization.
                        } else {
                            FixedWithNextNode predecessor = (FixedWithNextNode) predecessor();
                            FixedNode successor = next();

                            predecessor.setNext(null);
                            setNext(null);

                            MergeNode afterInitialization = graph().add(new MergeNode());
                            afterInitialization.setNext(successor);

                            ZeroMemoryNode zeroMemoryNode = graph().addOrUniqueWithInputs(new ZeroMemoryNode(getAddress(), zeroingSize, false, INIT_LOCATION, BarrierType.NONE));
                            afterInitialization.addForwardEnd(end(zeroMemoryNode));
                            afterInitialization.addForwardEnd(end(this));

                            IfNode ifExceedsThreshold = graph().add(new IfNode(exceedsThreshold, BeginNode.begin(zeroMemoryNode), BeginNode.begin(this), BranchProbabilityNode.SLOW_PATH_PROFILE));
                            predecessor.setNext(ifExceedsThreshold);

                            MemoryPhiNode phi = graph().addWithoutUnique(new MemoryPhiNode(afterInitialization, getLocationIdentity(), new ValueNode[]{zeroMemoryNode, this}));
                            this.replaceAtUsages(phi, (Node usage) -> usage != phi);
                        }
                    }
                }
            }
            setLength(totalWriteLength);
        }
    }

    private static EndNode end(FixedWithNextNode fixedWithNextNode) {
        StructuredGraph graph = fixedWithNextNode.graph();
        EndNode endNode = graph.add(new EndNode());
        fixedWithNextNode.setNext(endNode);
        return endNode;
    }

    @Override
    public void simplifyTree(VectorSimplifier simplifier) {
        setVector(simplifier.simplifyLengthHint(getVector(), length));
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
    @SuppressWarnings("try")
    public VectorConsumerIterator createInitialIterator(TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            ValueNode index = ConstantNode.forIntegerKind(target.wordJavaKind, 0, graph());
            AnchoringNode anchor = getAnchor();
            VectorIterator value = VectorInitialIteratorNode.createInitialIterator(getVector(), anchor, target);
            LoadAddressNode address = graph().addOrUniqueWithInputs(LoadAddressNode.create(target.wordJavaKind, (OffsetAddressNode) getAddress(), anchor));
            if (getLastLocationAccess() == null && getLocationIdentity().isInit()) {
                setLastLocationAccess(graph().start());
            }
            return new VectorWriteIterator(index, value, address, getLocationIdentity(), getLastLocationAccess());
        }
    }

    @Override
    @SuppressWarnings("try")
    public VectorConsumerIterator createPhiIterator(int minInputStepLength, int maxInputStepLength, PhiNode phi, TargetDescription target) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            AbstractMergeNode merge = phi.merge();
            ValueNode index = graph().addOrUnique(new ValuePhiNode(StampFactory.forKind(target.wordJavaKind), merge));
            AnchoringNode anchor = getAnchor();
            VectorIterator value = VectorInitialIteratorNode.createPhiIterator(merge, getVector(), anchor, target);
            MemoryPhiNode memoryPhi = graph().addWithoutUnique(new MemoryPhiNode(merge, getLocationIdentity()));
            LoadAddressNode address = graph().addOrUniqueWithInputs(LoadAddressNode.create(target.wordJavaKind, (OffsetAddressNode) getAddress(), anchor));
            return new VectorWriteIterator(index, value, address, getLocationIdentity(), memoryPhi);
        }
    }

    @Override
    @SuppressWarnings("try")
    public ValueNode simdify(VectorArchitecture arch, ValueNode... inputs) {
        try (DebugCloseable position = this.withNodeSourcePosition()) {
            assert inputs.length == 1 : inputs + " " + this;
            WriteNode simdWrite = graph().add(new WriteNode(getAddress(), getLocationIdentity(), inputs[0], getBarrierType(), MemoryOrderMode.PLAIN));
            graph().replaceFixedWithFixed(this, simdWrite);
            return simdWrite;
        }
    }

    @Override
    public boolean isInitialization() {
        return isInitialization;
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        updateUsages(stateAfter, x);
        stateAfter = x;
    }

    @Override
    public boolean writesObjectArray() {
        VectorStamp vectorStamp = (VectorStamp) vector.stamp(NodeView.DEFAULT);
        return vectorStamp.getElementStamp() instanceof AbstractObjectStamp;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return getLocationIdentity();
    }

    @Override
    public FixedNode preBarrierInsertionPosition() {
        if (this.isPartOfALoop()) {
            return (FixedNode) this.vectorLoop().getConsumers().get(0);
        } else {
            return this;
        }
    }

    @Override
    public FixedWithNextNode postBarrierInsertionPosition() {
        if (this.isPartOfALoop()) {
            return this.vectorLoop();
        } else {
            return this;
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
