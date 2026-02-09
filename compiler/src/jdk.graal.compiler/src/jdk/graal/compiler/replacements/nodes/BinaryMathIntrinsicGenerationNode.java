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

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1024;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;
import static jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode.BinaryOperation;

@Node.NodeIntrinsicFactory
@NodeInfo(nameTemplate = "MathIntrinsic#{p#operation/s}", cycles = CYCLES_1024, cyclesRationale = "stub based math intrinsics all have roughly the same high cycle count", size = SIZE_1)
public final class BinaryMathIntrinsicGenerationNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<BinaryMathIntrinsicGenerationNode> TYPE = NodeClass.create(BinaryMathIntrinsicGenerationNode.class);
    private final BinaryOperation op;
    @Input private ValueNode x;
    @Input private ValueNode y;

    protected BinaryMathIntrinsicGenerationNode(ValueNode x, ValueNode y, BinaryOperation op) {
        super(TYPE, StampFactory.forKind(JavaKind.Double));
        this.x = x;
        this.y = y;
        this.op = op;
    }

    @NodeIntrinsic
    public static native double compute(double x, double y, @ConstantNodeParameter BinaryOperation op);

    public static ValueNode create(ValueNode forX, ValueNode forY, BinaryOperation op) {
        return new BinaryMathIntrinsicGenerationNode(forX, forY, op);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool lirTool = gen.getLIRGeneratorTool();
        Value xValue = gen.operand(x);
        Value yValue = gen.operand(y);
        Value result;
        switch (op) {
            case POW:
                result = lirTool.getArithmetic().emitMathPow(xValue, yValue);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(op); // ExcludeFromJacocoGeneratedReport
        }
        gen.setResult(this, result);
    }

    public static boolean intrinsify(GraphBuilderContext b, ValueNode forX, ValueNode forY, BinaryOperation op) {
        b.addPush(JavaKind.Double, new BinaryMathIntrinsicGenerationNode(forX, forY, op));
        return true;
    }

    public BinaryOperation getOperation() {
        return op;
    }

    public ValueNode getX() {
        return x;
    }

    public ValueNode getY() {
        return y;
    }
}
