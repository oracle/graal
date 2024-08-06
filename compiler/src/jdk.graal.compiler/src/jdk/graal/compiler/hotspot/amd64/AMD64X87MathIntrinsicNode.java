/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.Value;

@NodeInfo(nameTemplate = "X87MathIntrinsic#{p#operation/s}", cycles = CYCLES_64, size = SIZE_1)
public final class AMD64X87MathIntrinsicNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<AMD64X87MathIntrinsicNode> TYPE = NodeClass.create(AMD64X87MathIntrinsicNode.class);
    protected final UnaryOperation operation;

    protected AMD64X87MathIntrinsicNode(ValueNode value, UnaryOperation op) {
        super(TYPE, UnaryOperation.computeStamp(op, value.stamp(NodeView.DEFAULT)), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == 64 : Assertions.errorMessage(value);
        this.operation = op;
    }

    @Override
    public Stamp foldStamp(Stamp valueStamp) {
        return UnaryOperation.computeStamp(operation, valueStamp);
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        LIRGeneratorTool gen = generator.getLIRGeneratorTool();
        Value input = generator.operand(getValue());
        Variable result = gen.newVariable(LIRKind.combine(input));
        VirtualStackSlot stack = gen.getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(AMD64Kind.DOUBLE));

        switch (operation) {
            case SIN:
            case COS:
            case TAN:
            case LOG:
            case LOG10:
                gen.append(new AMD64HotSpotMathIntrinsicOp(operation, result, gen.asAllocatable(input), stack));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(operation); // ExcludeFromJacocoGeneratedReport
        }
        generator.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            return ConstantNode.forDouble(UnaryOperation.compute(operation, forValue.asJavaConstant().asDouble()));
        }
        return this;
    }

    @NodeIntrinsic
    public static native double compute(double value, @ConstantNodeParameter UnaryOperation op);

}
