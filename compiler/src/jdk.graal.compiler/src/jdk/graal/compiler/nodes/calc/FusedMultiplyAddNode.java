/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.calc;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;
import static jdk.graal.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.TernaryOp.FMA;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

/**
 * This node represents the operation x * y + z.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class FusedMultiplyAddNode extends TernaryArithmeticNode<FMA> {

    public static final NodeClass<FusedMultiplyAddNode> TYPE = NodeClass.create(FusedMultiplyAddNode.class);

    public FusedMultiplyAddNode(ValueNode x, ValueNode y, ValueNode z) {
        super(TYPE, getArithmeticOpTable(x).getFMA(), x, y, z);
        assert x.stamp(NodeView.DEFAULT).isFloatStamp();
        assert y.stamp(NodeView.DEFAULT).isFloatStamp();
        assert z.stamp(NodeView.DEFAULT).isFloatStamp();
    }

    public static ValueNode create(ValueNode x, ValueNode y, ValueNode z, NodeView view) {
        ArithmeticOpTable.TernaryOp<ArithmeticOpTable.TernaryOp.FMA> op = ArithmeticOpTable.forStamp(x.stamp(view)).getFMA();
        Stamp stamp = op.foldStamp(x.stamp(view), y.stamp(view), z.stamp(view));
        ConstantNode tryConstantFold = tryConstantFold(op, x, y, z, stamp);
        if (tryConstantFold != null) {
            return tryConstantFold;
        }

        return new FusedMultiplyAddNode(x, y, z);
    }

    @Override
    protected ArithmeticOpTable.TernaryOp<FMA> getOp(ArithmeticOpTable table) {
        return table.getFMA();
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

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        return switch (arch) {
            case AMD64 amd64 -> amd64.getFeatures().contains(AMD64.CPUFeature.FMA);
            case AArch64 aarch64 -> true;
            default -> false;
        };
    }
}
