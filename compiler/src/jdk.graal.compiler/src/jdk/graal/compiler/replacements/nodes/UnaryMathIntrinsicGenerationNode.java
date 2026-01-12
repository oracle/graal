/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1024;
import static jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

@Node.NodeIntrinsicFactory
@NodeInfo(nameTemplate = "MathIntrinsic#{p#operation/s}", cycles = CYCLES_64, size = SIZE_1024)
public final class UnaryMathIntrinsicGenerationNode extends FixedWithNextNode implements LIRLowerable {
    public static final NodeClass<UnaryMathIntrinsicGenerationNode> TYPE = NodeClass.create(UnaryMathIntrinsicGenerationNode.class);
    private final UnaryOperation op;
    @Input private ValueNode value;

    protected UnaryMathIntrinsicGenerationNode(ValueNode value, UnaryOperation op) {
        super(TYPE, UnaryOperation.computeStamp(op, value.stamp(NodeView.DEFAULT)));
        this.op = op;
        this.value = value;
    }

    public static ValueNode create(ValueNode value, UnaryOperation op) {
        return new UnaryMathIntrinsicGenerationNode(value, op);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool lirTool = gen.getLIRGeneratorTool();
        // We can only reach here in the math stubs
        Value input = gen.operand(value);
        Value result = switch (op) {
            case LOG -> lirTool.getArithmetic().emitMathLog(input, false);
            case LOG10 -> lirTool.getArithmetic().emitMathLog(input, true);
            case EXP -> lirTool.getArithmetic().emitMathExp(input);
            case SIN -> lirTool.getArithmetic().emitMathSin(input);
            case COS -> lirTool.getArithmetic().emitMathCos(input);
            case TAN -> lirTool.getArithmetic().emitMathTan(input);
            case TANH -> lirTool.getArithmetic().emitMathTanh(input);
            case CBRT -> lirTool.getArithmetic().emitMathCbrt(input);
        };
        gen.setResult(this, result);
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode value, UnaryOperation op) {
        b.addPush(JavaKind.Double, new UnaryMathIntrinsicGenerationNode(value, op));
        return true;
    }

    public UnaryOperation getOperation() {
        return op;
    }

    public ValueNode getValue() {
        return value;
    }

    @NodeIntrinsic
    public static native double compute(double value, @ConstantNodeParameter UnaryOperation op);
}
