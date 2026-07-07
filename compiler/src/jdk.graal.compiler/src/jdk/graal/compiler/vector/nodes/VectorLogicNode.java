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
package jdk.graal.compiler.vector.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.vector.nodes.op.VectorTransformation;
import jdk.graal.compiler.vector.nodes.producer.FillVectorNode;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;
import jdk.graal.compiler.vector.phases.SimdifyVectorPhase;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.vector.architecture.VectorArchitecture;
import jdk.graal.compiler.vector.nodes.simd.SimdCutNode;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;

/**
 * Base class for vector nodes that produce vectors of truth values.
 *
 * @implNote This class is a {@link NodeWithIdentity} to prevent sharing in the graph. When
 *           {@link SimdifyVectorPhase} builds SIMD code for shared nodes, it only builds a single
 *           SIMD version and uses {@link SimdCutNode} to adjust the vector length for each usage.
 *           But vectors of truth values cannot be cut, therefore we must prevent such sharing.
 */
//@formatter:off
@NodeInfo(cycles = CYCLES_UNKNOWN,
   cyclesRationale = "We cannot argue about vector nodes statically.",
   size = SIZE_UNKNOWN,
   sizeRationale = "We cannot argue about vector nodes statically.")
//@formatter:on
public abstract class VectorLogicNode extends AbstractVectorNode implements VectorTransformation, NodeWithIdentity {
    public static final NodeClass<VectorLogicNode> TYPE = NodeClass.create(VectorLogicNode.class);

    protected VectorLogicNode(NodeClass<? extends VectorLogicNode> c, Stamp stamp) {
        super(c, stamp);
    }

    /**
     * Return a scalar version of this operation, i.e., the operation to be executed for a vector
     * length of 1.
     *
     * @return the scalar logic operation
     */
    public abstract LogicNode asScalar();

    /**
     * Return a scalar version of this operation applied to the {@code offset}-th components of its
     * input vectors.
     */
    public abstract LogicNode cutToScalar(int offset);

    /**
     * Build a vector or SIMD stamp for the logic operation's result, computed from the input stamp.
     * If the input stamp is a {@link VectorStamp} or {@link SimdStamp}, returns a corresponding
     * {@link VectorStamp} or {@link SimdStamp} of integer stamps of the same size as the input
     * stamp's elements. Otherwise, returns a {@code void} stamp appropriate for scalar logic nodes.
     *
     * @param input the logic operation's input
     * @param maskStamp the base stamp for each element of the vector or SIMD mask
     * @return a stamp for the result of the logic operation
     */
    protected static Stamp buildVectorStamp(ValueNode input, Stamp maskStamp) {
        Stamp inputStamp = input.stamp(NodeView.DEFAULT);
        if (inputStamp instanceof VectorStamp) {
            return new VectorStamp(maskStamp);
        } else if (inputStamp instanceof SimdStamp) {
            SimdStamp inputSimdStamp = (SimdStamp) inputStamp;
            return SimdStamp.broadcast(maskStamp, inputSimdStamp.getVectorLength());
        } else {
            return StampFactory.forVoid();
        }
    }

    /**
     * Get the maximum natively supported length for this logic operation.
     */
    public abstract int getMaxVectorLength(VectorArchitecture arch, int upperBound);

    /**
     * Return an equivalent scalar condition, if one exists. This can be the case if all inputs to
     * the vector logic operation are {@link FillVectorNode}s, in which case we can just evaluate
     * the scalar version of the condition on the vectors' invariant elements. Return {@code null}
     * if the condition is not invariant in this sense.
     */
    public abstract LogicNode invariantCondition();

    public Stamp getElementStamp() {
        Stamp thisStamp = stamp(NodeView.DEFAULT);
        if (thisStamp instanceof VectorStamp) {
            return ((VectorStamp) thisStamp).getElementStamp();
        } else if (thisStamp instanceof SimdStamp) {
            return ((SimdStamp) thisStamp).getComponent(0);
        } else {
            throw GraalError.shouldNotReachHere("expected vector or SIMD stamp: " + thisStamp); // ExcludeFromJacocoGeneratedReport
        }
    }

    /**
     * Creates a copy of this vector logic operation with new inputs. Fixed nodes created by the
     * copying are inserted at positions determined by the implementation. Use
     * {@link #createCopy(FixedNode, ValueNode...)} to control the insertion position.
     */
    public VectorLogicNode createCopyDefaultInsertionPosition(ValueNode... inputs) {
        return (VectorLogicNode) createCopy(null, inputs);
    }
}
