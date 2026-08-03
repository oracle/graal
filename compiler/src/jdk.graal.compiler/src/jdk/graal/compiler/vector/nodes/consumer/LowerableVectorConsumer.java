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

import jdk.graal.compiler.vector.nodes.lowered.iterator.VectorConsumerIterator;

import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.vm.ci.code.TargetDescription;

/**
 * A LowerableVectorConsumer is a vector consumer that can be lowered into a loop.
 */
public interface LowerableVectorConsumer extends VectorConsumer {

    VectorConsumerIterator createInitialIterator(TargetDescription target);

    VectorConsumerIterator createPhiIterator(int minInputStepLength, int maxInputStepLength, PhiNode phi, TargetDescription target);

    /**
     * The direction in which this consumer consumes vector elements. Consider consuming the
     * high-level vector {@code <0,1,2,3>} in SIMD chunks of two elements each. Consuming it in
     * {@link Direction#Up} means first consuming {@code <0,1>}, then {@code <2,3>}. Consuming it in
     * {@link Direction#Down} means consuming {@code <2,3>}, then {@code <0,1>}.
     */
    Direction direction();

    void lower(LowTierContext context);

    boolean getSupportsAlignment();

    /**
     * Whether it is safe to repeat part or all of the work done by this consumer.
     */
    default boolean isIdempotent() {
        return false;
    }

    /**
     * Gets this consumer's {@linkplain VectorLoopMarkerNode loop marker}, or {@code null} if none.
     */
    VectorLoopMarkerNode vectorLoopMarker();

    void setVectorLoopMarker(VectorLoopMarkerNode vectorLoop);

    default VectorLoopNode vectorLoop() {
        NodeIterable<VectorLoopNode> loops = this.asNode().usages().filter(VectorLoopNode.class);
        if (loops.isEmpty()) {
            return null;
        } else {
            assert loops.count() == 1 : loops;
            return loops.first();
        }
    }

    default boolean isPartOfALoop() {
        return this.vectorLoop() != null;
    }

    /**
     * Perform node-specific actions after the vector consumer loop structure has been constructed.
     */
    @SuppressWarnings("unused")
    default void finishLowering(LowTierContext context) {
    }

    /**
     * LowerableVectorConsumers need an anchor while lowering. They can create one themselves, but
     * consumers {@linkplain #isPartOfALoop() in a common vector loop} should share the same anchor,
     * which can be set with this method.
     *
     * @param anchor the anchor to use when lowering this node
     */
    void setLoopAnchor(AnchoringNode anchor);

    /**
     * Get an anchor set by {@link #setLoopAnchor(AnchoringNode)}. May return {@code null} if no
     * loop anchor has been set.
     */
    AnchoringNode getLoopAnchor();

    /**
     * Get or create an anchor for use during lowering. This uses an existing
     * {@linkplain #getLoopAnchor() loop anchor} if available, otherwise it creates one after the
     * current node.
     */
    default AnchoringNode getAnchor() {
        if (getLoopAnchor() != null) {
            return getLoopAnchor();
        } else if (asFixedWithNextNode().next() instanceof AnchoringNode) {
            return (AnchoringNode) asFixedWithNextNode().next();
        } else {
            ValueAnchorNode guarding = asFixedWithNextNode().graph().add(new ValueAnchorNode());
            asFixedWithNextNode().graph().addAfterFixed(this.asFixedWithNextNode(), guarding);
            return guarding;
        }
    }

    /**
     * The number of iterations of the original loop body from which this vector consumer was
     * derived, if it comes from a trusted source like an annotation, mature profiling info, or a
     * constant loop bound in the source code. This is a compile-time approximation of the
     * {@linkplain VectorConsumer#getLength() vector length}.
     *
     * Implementors should return -1 if no such trusted information is available.
     */
    double trustedBodyIterations();

    void setTrustedBodyIterations(double trustedBodyIterations);
}
