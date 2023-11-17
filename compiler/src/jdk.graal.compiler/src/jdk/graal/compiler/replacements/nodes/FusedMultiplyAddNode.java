/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.TernaryNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class FusedMultiplyAddNode extends TernaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<FusedMultiplyAddNode> TYPE = NodeClass.create(FusedMultiplyAddNode.class);

    public FusedMultiplyAddNode(ValueNode x, ValueNode y, ValueNode z) {
        super(TYPE, computeStamp(x.stamp(NodeView.DEFAULT), y.stamp(NodeView.DEFAULT), z.stamp(NodeView.DEFAULT)), x, y, z);
        assert x.getStackKind().isNumericFloat();
        assert y.getStackKind().isNumericFloat();
        assert z.getStackKind().isNumericFloat();
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        return computeStamp(stampX, stampY, stampZ);
    }

    private static Stamp computeStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        if (stampX.isEmpty()) {
            return stampX;
        }
        if (stampY.isEmpty()) {
            return stampY;
        }
        if (stampZ.isEmpty()) {
            return stampZ;
        }
        JavaConstant constantX = ((FloatStamp) stampX).asConstant();
        JavaConstant constantY = ((FloatStamp) stampY).asConstant();
        JavaConstant constantZ = ((FloatStamp) stampZ).asConstant();
        if (constantX != null && constantY != null && constantZ != null) {
            if (stampX.getStackKind() == JavaKind.Float) {
                float result = GraalServices.fma(constantX.asFloat(), constantY.asFloat(), constantZ.asFloat());
                if (Float.isNaN(result)) {
                    return StampFactory.forFloat(JavaKind.Float, Double.NaN, Double.NaN, false);
                } else {
                    return StampFactory.forFloat(JavaKind.Float, result, result, true);
                }
            } else {
                double result = GraalServices.fma(constantX.asDouble(), constantY.asDouble(), constantZ.asDouble());
                assert stampX.getStackKind() == JavaKind.Double : Assertions.errorMessage(stampX, stampY, stampZ);
                if (Double.isNaN(result)) {
                    return StampFactory.forFloat(JavaKind.Double, Double.NaN, Double.NaN, false);
                } else {
                    return StampFactory.forFloat(JavaKind.Double, result, result, true);
                }
            }
        }

        return stampX.unrestricted();
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY, ValueNode forZ) {
        if (forX.isConstant() && forY.isConstant() && forZ.isConstant()) {
            JavaConstant constantX = forX.asJavaConstant();
            JavaConstant constantY = forY.asJavaConstant();
            JavaConstant constantZ = forZ.asJavaConstant();
            if (forX.getStackKind() == JavaKind.Float) {
                return ConstantNode.forFloat(GraalServices.fma(constantX.asFloat(), constantY.asFloat(), constantZ.asFloat()));
            } else {
                assert forX.getStackKind() == JavaKind.Double : Assertions.errorMessage(forX, forY, forZ);
                return ConstantNode.forDouble(GraalServices.fma(constantX.asDouble(), constantY.asDouble(), constantZ.asDouble()));
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitFusedMultiplyAdd(builder.operand(getX()), builder.operand(getY()), builder.operand(getZ())));
    }
}
