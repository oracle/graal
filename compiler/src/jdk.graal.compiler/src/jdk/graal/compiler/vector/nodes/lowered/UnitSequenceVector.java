/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.lowered;

import jdk.graal.compiler.vector.nodes.AbstractVectorNode;
import jdk.graal.compiler.vector.nodes.SimdifyableVectorProducer;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.graal.compiler.vector.nodes.type.VectorStamp;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.BinaryOp.Add;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.loop.InductionVariable.Direction;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

/**
 * The constant vector [0,1,2,...].
 *
 * This will typically be simdified to a constant {@code <0, 1, ..., N-1>}. However, when consumed
 * by a write with a negative stride, it will be simdified to {@code <N-1, N-2, ..., 1, 0>}. In the
 * following code:
 *
 * <pre>
 * for (int i = 3; i >= 0; i--) {
 *     array[i] = i;
 * }
 * </pre>
 *
 * The array should be filled with the values {@code 3, 2, 1, 0} (read from index 0 to 3). If we
 * used the standard unit sequence and took a 4-element chunk, we would be writing the values
 * {@code 0, 1, 2, 3} (read from index 0 to 3) instead. In this case we must therefore simdify the
 * sequence to the SIMD constant {@code <3, 2, 1, 0>}, which when written will put the correct
 * elements in the correct places.
 */
@NodeInfo
public final class UnitSequenceVector extends AbstractVectorNode implements SimdifyableVectorProducer {
    public static final NodeClass<UnitSequenceVector> TYPE = NodeClass.create(UnitSequenceVector.class);

    private final Direction direction;

    public UnitSequenceVector(VectorStamp stamp, Direction direction) {
        super(TYPE, stamp);
        this.direction = direction;
    }

    @Override
    public ValueNode simdify(int length, Direction consumerDirection) {
        IntegerStamp elementStamp = (IntegerStamp) getVectorStamp().getElementStamp();

        boolean negativeStride = consumerDirection == Direction.Down;
        GraalError.guarantee(negativeStride == (direction == Direction.Down), "%s: UnitSequenceVector direction must be consistent with the direction of its consumer", this);
        int initValue = negativeStride ? length - 1 : 0;
        JavaConstant init = IntegerStamp.create(elementStamp.getBits(), initValue, initValue).asConstant();

        if (length == 1) {
            return ConstantNode.forPrimitive(elementStamp, init, graph());
        } else {
            BinaryOp<Add> add = ArithmeticOpTable.forStamp(elementStamp).getAdd();
            int stepValue = negativeStride ? -1 : 1;
            JavaConstant step = IntegerStamp.create(elementStamp.getBits(), stepValue, stepValue).asConstant();

            if (!negativeStride) {
                assert add.isNeutral(init) && ArithmeticOpTable.forStamp(elementStamp).getMul().isNeutral(step) : "ill defined semantics of UnitSequenceVector for type " + elementStamp;
            }

            Constant[] unitValues = new Constant[length];
            unitValues[0] = init;
            for (int i = 1; i < length; i++) {
                unitValues[i] = add.foldConstant(unitValues[i - 1], step);
            }

            SimdConstant ret = new SimdConstant(unitValues);
            return graph().unique(new ConstantNode(ret, getVectorStamp().toSimd(length).constant(ret, null)));
        }
    }
}
