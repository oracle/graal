/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.ReinterpretUtils;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.ReinterpretOp;
import jdk.graal.compiler.core.common.type.ArithmeticStamp;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ArithmeticOperation;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SerializableConstant;

/**
 * The {@code ReinterpretNode} class represents a reinterpreting conversion that changes the stamp
 * of a primitive value to some other incompatible stamp. The new stamp must have the same width as
 * the old stamp.
 */
@NodeInfo(cycles = CYCLES_1)
public final class ReinterpretNode extends UnaryNode implements ArithmeticOperation, ArithmeticLIRLowerable {

    public static final NodeClass<ReinterpretNode> TYPE = NodeClass.create(ReinterpretNode.class);

    public ReinterpretNode(JavaKind to, ValueNode value) {
        this(StampFactory.forKind(to), value);
    }

    protected ReinterpretNode(Stamp to, ValueNode value) {
        super(TYPE, getReinterpretStamp(to, value.stamp(NodeView.DEFAULT)), value);
        assert to instanceof ArithmeticStamp : Assertions.errorMessageContext("to", to, "value", value);
    }

    public static ValueNode create(JavaKind to, ValueNode value, NodeView view) {
        return create(StampFactory.forKind(to), value, view);
    }

    public static ValueNode create(Stamp to, ValueNode value, NodeView view) {
        return canonical(null, to, value, view);
    }

    private static Constant evalConst(Stamp forStamp, SerializableConstant c) {
        return ArithmeticOpTable.forStamp(forStamp).getReinterpret().foldConstant(forStamp, c);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        NodeView view = NodeView.from(tool);
        return canonical(this, this.stamp(view), forValue, view);
    }

    public static ValueNode canonical(ReinterpretNode node, Stamp forStamp, ValueNode forValue, NodeView view) {
        if (forValue.isConstant()) {
            return ConstantNode.forConstant(forStamp, evalConst(forStamp, (SerializableConstant) forValue.asConstant()), null);
        }
        if (forStamp.isCompatible(forValue.stamp(view))) {
            return forValue;
        }
        if (forValue instanceof ReinterpretNode) {
            ReinterpretNode reinterpret = (ReinterpretNode) forValue;
            if (forStamp.isCompatible(reinterpret.value.stamp(view))) {
                return reinterpret.value;
            }
        }
        return node != null ? node : new ReinterpretNode(forStamp, forValue);
    }

    private static Stamp getReinterpretStamp(Stamp toStamp, Stamp fromStamp) {
        if (toStamp instanceof IntegerStamp && fromStamp instanceof FloatStamp) {
            return ReinterpretUtils.floatToInt((FloatStamp) fromStamp);
        } else if (toStamp instanceof FloatStamp && fromStamp instanceof IntegerStamp) {
            return ReinterpretUtils.intToFloat((IntegerStamp) fromStamp);
        } else {
            return toStamp;
        }
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        assert newStamp.isCompatible(getValue().stamp(NodeView.DEFAULT));
        return getOp(getValue()).foldStamp(stamp(NodeView.DEFAULT), newStamp);
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool gen) {
        LIRKind kind = builder.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        builder.setResult(this, gen.emitReinterpret(kind, builder.operand(getValue())));
    }

    public static ValueNode reinterpret(JavaKind toKind, ValueNode value) {
        return value.graph().unique(new ReinterpretNode(toKind, value));
    }

    @Override
    public ReinterpretOp getArithmeticOp() {
        return getOp(getValue());
    }

    private static ReinterpretOp getOp(ValueNode forValue) {
        return BinaryArithmeticNode.getArithmeticOpTable(forValue).getReinterpret();
    }
}
