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
package jdk.compiler.graal.hotspot.amd64;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_1;

import jdk.compiler.graal.core.common.LIRKind;
import jdk.compiler.graal.core.common.type.FloatStamp;
import jdk.compiler.graal.core.common.type.PrimitiveStamp;
import jdk.compiler.graal.core.common.type.Stamp;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.Variable;
import jdk.compiler.graal.lir.VirtualStackSlot;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NodeView;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.calc.UnaryNode;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.LIRLowerable;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.Value;

@NodeInfo(nameTemplate = "X87MathIntrinsic#{p#operation/s}", cycles = CYCLES_64, size = SIZE_1)
public final class AMD64X87MathIntrinsicNode extends UnaryNode implements LIRLowerable {

    public static final NodeClass<AMD64X87MathIntrinsicNode> TYPE = NodeClass.create(AMD64X87MathIntrinsicNode.class);
    protected final UnaryOperation operation;

    protected AMD64X87MathIntrinsicNode(ValueNode value, UnaryOperation op) {
        super(TYPE, op.computeStamp(value.stamp(NodeView.DEFAULT)), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == 64;
        this.operation = op;
    }

    @Override
    public Stamp foldStamp(Stamp valueStamp) {
        return operation.computeStamp(valueStamp);
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
            return ConstantNode.forDouble(operation.compute(forValue.asJavaConstant().asDouble()));
        }
        return this;
    }

    @NodeIntrinsic
    public static native double compute(double value, @ConstantNodeParameter UnaryOperation op);

}
