/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.aarch64;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.UnsignedMath;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AcrossVectorOp;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

@NodeInfo(cycles = NodeCycles.CYCLES_2, size = NodeSize.SIZE_4)
public class AArch64AcrossVectorNode extends UnaryNode implements LIRLowerable {
    public static final NodeClass<AArch64AcrossVectorNode> TYPE = NodeClass.create(AArch64AcrossVectorNode.class);

    public enum Operation {
        UNSIGNED_MAX
    }

    private Operation operation;
    private int elementSize;

    public AArch64AcrossVectorNode(Operation op, int elementSize, ValueNode input) {
        super(TYPE, IntegerStamp.create(elementSize), input);
        assert elementSize == 8 || elementSize == 16 || elementSize == 32 || elementSize == 64 : elementSize;
        this.operation = op;
        this.elementSize = elementSize;
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.asConstant() instanceof SimdConstant) {
            /*
             * Note: as more enums are added, this method will need to be adjusted to have different
             * initial values.
             */
            SimdConstant simdConstant = (SimdConstant) forValue.asConstant();
            assert !simdConstant.getValues().isEmpty() : simdConstant.getValues();

            long result = Long.MIN_VALUE;
            for (Constant constant : simdConstant.getValues()) {
                if (!(constant instanceof JavaConstant)) {
                    return this;
                }

                long element = ((JavaConstant) constant).asLong() & NumUtil.getNbitNumberLong(elementSize);
                switch (operation) {
                    case UNSIGNED_MAX:
                        result = UnsignedMath.aboveOrEqual(result, element) ? result : element;
                        break;
                    default:
                        throw GraalError.shouldNotReachHereUnexpectedValue(operation); // ExcludeFromJacocoGeneratedReport
                }
            }

            if (elementSize == 64) {
                return ConstantNode.forIntegerKind(JavaKind.Long, result);
            } else {
                return ConstantNode.forIntegerKind(JavaKind.Int, result);
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        Value input = gen.operand(value);
        /* Result will be in a floating point register. */
        ValueKind<?> resultKind = input.getValueKind().changeType(elementSize == 64 ? AArch64Kind.DOUBLE : AArch64Kind.SINGLE);
        Variable result = tool.newVariable(resultKind);
        switch (operation) {
            case UNSIGNED_MAX:
                tool.append(new AArch64AcrossVectorOp.ASIMDOp(AArch64AcrossVectorOp.UMAX, result, tool.asAllocatable(input)));
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(operation); // ExcludeFromJacocoGeneratedReport
        }
        gen.setResult(this, result);

    }
}
