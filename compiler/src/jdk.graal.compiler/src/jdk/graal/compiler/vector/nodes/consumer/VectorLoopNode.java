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
import static jdk.graal.compiler.nodeinfo.InputType.Value;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.ArrayList;
import java.util.List;

import jdk.graal.compiler.vector.nodes.SimplifiableVectorNode.VectorSimplifier;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;
import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorLoopIterator;
import jdk.graal.compiler.vector.nodes.op.ConcatVectorNode;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.vm.ci.code.TargetDescription;

/**
 * A group of lowerable vector consumers that were generated from the same input loop and should be
 * vectorized together. We use this node even if the "group" contains exactly one vector consumer.
 * </p>
 *
 * Vector consumers that do not derive from loops in the input source (for example, consumers
 * generated from intrinsified methods) don't necessarily have such a vector loop node.
 * </p>
 *
 * Example: The following loop is vectorized with two consumers (a vector guard and a vector write)
 * that we group together with a {@code VectorLoopNode}:
 *
 * <pre>
 *     for (int i = 0; i < in.length; i++) {
 *         double v = in[i];
 *         if (isNaN(v)) { deopt; }
 *         out[i] = v + 1.0;
 *     }
 * </pre>
 *
 * Loop vectorization builds the following representation:
 *
 * <pre>
 * v = VectorRead(in);
 * g = VectorGuard(!isNaN(v));
 * w = VectorWrite(out, v + VectorFill(1.0));
 * VectorLoop(g, w);
 * </pre>
 *
 * Viewed as a sequence of fixed nodes, this is misleading, since it could be interpreted as first
 * doing the entire work of the guard (i.e., checking every element of the input array) and only
 * then moving on to the entire work of the vector write (i.e., mapping every element of the input
 * array and writing the results to the output array). But the actual expansion after vector
 * lowering interleaves the work in chunks of the size of the target's SIMD registers:
 *
 * <pre>
 *     for (int i = 0; i + 3 < in.length; i += 4) {
 *         v = in[i:i+3];
 *         if (isNaN(v)) { deopt; }
 *         out[i:i+3] = v + <1.0, 1.0, 1.0, 1.0>;
 *     }
 *     // ... tail processing...
 * </pre>
 *
 * For this reason, we enforce an invariant that consumers in the same vector loop must be adjacent
 * in the graph. No control flow or other work must be inserted between them, since it's not clear
 * how that would interleave with the chunked execution of the vector code. The only nodes permitted
 * between vector consumers in one vector loop are vector reads from the same loop, since they chunk
 * in the same way.
 * </p>
 *
 * We do allow control flow before the first consumer in a group and any vector reads that precede
 * it. This situation arises in VectorGuardTest#deoptOnInvariantCondition:
 *
 * <pre>
 *     v = VectorRead(array);
 *     if (someCondition) { deopt; }
 *     VectorWrite(v);
 * </pre>
 *
 * We get this shape when {@link VectorGuardNode#simplifyTree} turns a vector guard with a
 * loop-invariant condition into a scalar guard. Conceptually, the vector read can be done "in full"
 * before this guard because it has no side effects and cannot deopt. Lowering the remaining vector
 * code will properly place the entire vector loop, including the read, below the guard.
 */
//@formatter:off
@NodeInfo(allowedUsageTypes = {Association, Value},
          cycles = CYCLES_UNKNOWN,
          cyclesRationale = "We cannot argue about vector nodes statically.",
          size = SIZE_UNKNOWN,
          sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public class VectorLoopNode extends FixedWithNextNode implements LowerableVectorConsumer {
    public static final NodeClass<VectorLoopNode> TYPE = NodeClass.create(VectorLoopNode.class);

    /**
     * The number of iterations of the original scalar loop's body. The vector loop will execute up
     * to this number of iterations. If it {@linkplain #hasPostLoop() has a post loop}, it may
     * execute fewer iterations and leave some work for the post loop.
     */
    private @Input ValueNode length;
    private @Input(Association) NodeInputList<ValueNode> consumers;

    private final Direction direction;
    private boolean allFoldsAreAssociativeAndCommutative;
    private double trustedBodyIterations;
    /**
     * Records whether this vector loop is followed in the graph by a scalar post-loop that can
     * handle the work left after all SIMD iterations.
     */
    private boolean hasPostLoop;

    @SuppressWarnings("this-escape")
    public VectorLoopNode(ValueNode length, Direction direction, List<? extends LowerableVectorConsumer> consumers, Stamp stamp, boolean hasPostLoop) {
        super(TYPE, stamp);
        this.length = length;
        this.direction = direction;
        assert !consumers.isEmpty() : consumers + " " + length;
        this.consumers = new NodeInputList<>(this, consumers.toArray(ValueNode.EMPTY_ARRAY));
        recomputeFoldAssociativity();
        this.trustedBodyIterations = consumers.get(0).trustedBodyIterations();
        this.hasPostLoop = hasPostLoop;
    }

    @Override
    public VectorLoopMarkerNode vectorLoopMarker() {
        throw GraalError.shouldNotReachHere("vector loops should never need markers"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public void setVectorLoopMarker(VectorLoopMarkerNode vectorLoop) {
        GraalError.shouldNotReachHere("vector loops should never need markers"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public List<? extends ValueNode> getVectorInputs() {
        ArrayList<ValueNode> ret = new ArrayList<>();
        for (ValueNode consumer : consumers) {
            ret.addAll(((VectorConsumer) consumer).getVectorInputs());
        }
        return ret;
    }

    @Override
    public ValueNode getLength() {
        for (ValueNode consumer : consumers) {
            assert length.dataFlowEquals(((VectorConsumer) consumer).getLength()) : "inconsistent lengths within a vector loop";
        }
        return length;
    }

    private void setLength(ValueNode length) {
        updateUsages(this.length, length);
        this.length = length;
    }

    @Override
    public Direction direction() {
        return direction;
    }

    @Override
    public int getMaxVectorLength(VectorArchitecture arch) {
        assert !consumers.isEmpty() : consumers;
        int max = Integer.MAX_VALUE;
        // We vectorize all members of the loops with the same vector length. As we require vector
        // lengths to be powers of two, this common vector length is just the minimum of the
        // members' maximal lengths.
        for (ValueNode consumer : consumers) {
            max = Integer.min(max, ((VectorConsumer) consumer).getMaxVectorLength(arch));
        }
        return max;
    }

    @Override
    public void simplifyTree(VectorSimplifier simplifier) {
        // The simplification phase runs on each vector consumer, so the consumers in this loop
        // will be visited individually.
    }

    @Override
    public VectorConsumerIterator createInitialIterator(TargetDescription target) {
        ArrayList<VectorConsumerIterator> iterators = new ArrayList<>(consumers.size());
        AnchoringNode anchor = getAnchor();
        for (ValueNode consumer : consumers) {
            ((LowerableVectorConsumer) consumer).setLoopAnchor(anchor);
            iterators.add(((LowerableVectorConsumer) consumer).createInitialIterator(target));
        }
        return new VectorLoopIterator(iterators);
    }

    @Override
    public VectorConsumerIterator createPhiIterator(int minInputStepLength, int maxInputStepLength, PhiNode phi, TargetDescription target) {
        ArrayList<VectorConsumerIterator> iterators = new ArrayList<>(consumers.size());
        AnchoringNode anchor = getAnchor();
        for (ValueNode consumer : consumers) {
            ((LowerableVectorConsumer) consumer).setLoopAnchor(anchor);
            iterators.add(((LowerableVectorConsumer) consumer).createPhiIterator(minInputStepLength, maxInputStepLength, phi, target));
        }
        return new VectorLoopIterator(iterators);
    }

    @Override
    public void lower(LowTierContext context) {
        ValueNode wordLength = IntegerConvertNode.convertUnsigned(length, StampFactory.forUnsignedInteger(context.getTarget().wordSize * 8), graph(), NodeView.DEFAULT);
        setLength(wordLength);
        for (ValueNode consumer : consumers.snapshot()) {
            ((LowerableVectorConsumer) consumer).lower(context);
        }
    }

    @Override
    public boolean getSupportsAlignment() {
        return false;
    }

    public boolean removeConsumer(ValueNode consumer) {
        boolean removed = consumers.remove(consumer.asNode());
        if (removed && consumers.isEmpty()) {
            if (hasPostLoop()) {
                /*
                 * The post loop will have to do all the work, i.e., this vector loop does 0
                 * iterations.
                 */
                ValueNode zero = ConstantNode.forIntegerStamp(stamp(NodeView.DEFAULT), 0, graph());
                replaceAtUsages(zero, Value);
                replaceAtUsages(null, Association);
            }
            graph().removeFixed(this);
        }
        return removed;
    }

    public List<ValueNode> getConsumers() {
        return consumers.snapshot();
    }

    public boolean allFoldsAreAssociativeAndCommutative() {
        return allFoldsAreAssociativeAndCommutative;
    }

    /**
     * Visit all folds in this loop to check if they are all associative and commutative. Note that
     * previously non-associative folds can become associative after they are constructed, if we
     * recognize some pattern like a hashCode computation.
     */
    public void recomputeFoldAssociativity() {
        this.allFoldsAreAssociativeAndCommutative = CollectionsUtil.allMatch(consumers, consumer -> !(consumer instanceof FoldVectorNode) || ((FoldVectorNode) consumer).isAssociativeAndCommutative());
    }

    @Override
    public void setLoopAnchor(AnchoringNode anchor) {
        GraalError.shouldNotReachHere("vector loops cannot be nested"); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public AnchoringNode getLoopAnchor() {
        return null;
    }

    /**
     * Writes of concats are lowered to writes of the individual concat components, with differing
     * lengths. We cannot lower these correctly as part of a loop: It's not clear which component
     * represents the "original" write that was part of the vectorized loop. We must split such
     * writes out of their loops. Also split off any write preceding such a concat write to make
     * sure that we preserve the relative ordering of writes.
     */
    public void prepareLoopForLowering() {
        ValueNode lastWriteOfConcat = null;
        for (ValueNode member : consumers) {
            if (member instanceof VectorWriteNode && ((VectorWriteNode) member).getVector() instanceof ConcatVectorNode) {
                lastWriteOfConcat = member;
            }
        }
        if (lastWriteOfConcat != null) {
            ValueNode toBeRemoved = null;
            do {
                toBeRemoved = consumers.first();
                consumers.remove(toBeRemoved);
            } while (toBeRemoved != lastWriteOfConcat);
            if (consumers.isEmpty()) {
                if (hasPostLoop()) {
                    /*
                     * All elements are handled by the split writes, nothing to do for the post
                     * loop.
                     */
                    replaceAtUsages(getLength(), Value);
                    replaceAtUsages(null, Association);
                }
                graph().removeFixed(this);
            }
        }
    }

    @Override
    public boolean allowUnrolling() {
        return CollectionsUtil.allMatch(consumers, consumer -> ((VectorConsumer) consumer).allowUnrolling());
    }

    /**
     * Determine whether it's legal to split the loop for lowering.
     * {@link #prepareLoopForLowering()} removes writes with concats from the consumer loop. This
     * transformation is not valid if the loop contains a vector node with a frame state that we
     * need to be able to reconstruct. Further, the splitting is not possible if the vector loop has
     * a post loop.
     */
    public boolean mayRemoveConcatsFromLoop() {
        return !hasPostLoop() && !CollectionsUtil.anyMatch(consumers, consumer -> consumer instanceof VectorGuardNode || consumer instanceof VectorSafepointNode);
    }

    @Override
    public void finishLowering(LowTierContext context) {
        for (ValueNode consumer : consumers) {
            ((LowerableVectorConsumer) consumer).finishLowering(context);
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

    @Override
    public boolean verifyNode() {
        VectorLoopMarkerNode commonMarker = null;
        for (ValueNode consumer : consumers) {
            if (!(consumer instanceof LowerableVectorConsumer)) {
                /*
                 * During vector materialization this can temporarily be a placeholder, that's OK.
                 */
                continue;
            }
            VectorLoopMarkerNode marker = ((LowerableVectorConsumer) consumer).vectorLoopMarker();
            assertTrue(marker != null, "consumer %s in loop %s must have a vector loop marker", consumer, this);
            if (commonMarker == null) {
                commonMarker = marker;
            } else {
                assertTrue(commonMarker == marker, "consumer %s in loop %s should have marker %s but has %s", consumer, this, commonMarker, marker);
            }
        }
        return super.verifyNode();
    }

    public boolean hasPostLoop() {
        return hasPostLoop;
    }
}
