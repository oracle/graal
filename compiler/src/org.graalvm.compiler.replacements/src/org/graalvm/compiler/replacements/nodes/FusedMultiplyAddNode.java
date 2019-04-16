/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.TernaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public final class FusedMultiplyAddNode extends TernaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<FusedMultiplyAddNode> TYPE = NodeClass.create(FusedMultiplyAddNode.class);

    public FusedMultiplyAddNode(ValueNode a, ValueNode b, ValueNode c) {
        super(TYPE, computeStamp(a, b, c), a, b, c);
        assert a.getStackKind() == JavaKind.Float || a.getStackKind() == JavaKind.Double;
        assert b.getStackKind() == JavaKind.Float || b.getStackKind() == JavaKind.Double;
        assert c.getStackKind() == JavaKind.Float || c.getStackKind() == JavaKind.Double;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        return computeStamp(getX(), getY(), getZ());
    }

    static Stamp computeStamp(ValueNode a, ValueNode b, ValueNode c) {
        FloatStamp fstampX = (FloatStamp) a.stamp(NodeView.DEFAULT);
        FloatStamp fstampY = (FloatStamp) b.stamp(NodeView.DEFAULT);
        FloatStamp fstampZ = (FloatStamp) c.stamp(NodeView.DEFAULT);

        double lower = Math.min(fstampX.lowerBound(), fstampY.lowerBound());
        lower = Math.min(lower, fstampZ.lowerBound());

        double upper = Math.max(fstampX.upperBound(), fstampY.upperBound());
        upper = Math.min(lower, fstampZ.upperBound());

        boolean isNonNan = fstampX.isNonNaN() && fstampY.isNonNaN() && fstampZ.isNonNaN();

        return StampFactory.forFloat(a.getStackKind(), lower, upper, isNonNan);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode a, ValueNode b, ValueNode c) {
        if (a.isConstant() && b.isConstant() && c.isConstant()) {
            JavaConstant ca = a.asJavaConstant();
            JavaConstant cb = b.asJavaConstant();
            JavaConstant cc = c.asJavaConstant();

            ValueNode res;
            if (a.getStackKind() == JavaKind.Float) {
                res = ConstantNode.forFloat(Math.fma(ca.asFloat(), cb.asFloat(), cc.asFloat()));
            } else {
                res = ConstantNode.forDouble(Math.fma(ca.asDouble(), cb.asDouble(), cc.asDouble()));
            }
            return res;
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        builder.setResult(this, gen.emitFusedMultiplyAdd(builder.operand(getX()), builder.operand(getY()), builder.operand(getZ())));
    }
}
