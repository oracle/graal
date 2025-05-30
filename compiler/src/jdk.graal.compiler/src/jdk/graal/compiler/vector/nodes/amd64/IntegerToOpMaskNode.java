/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.amd64;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.graal.compiler.vector.nodes.simd.SimdStamp;
import jdk.vm.ci.amd64.AMD64Kind;

/**
 * Node for converting an integer value to a vector opmask. This node generates a move from a
 * general purpose register to an AVX512 k-register.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class IntegerToOpMaskNode extends UnaryNode implements VectorLIRLowerable {
    public static final NodeClass<IntegerToOpMaskNode> TYPE = NodeClass.create(IntegerToOpMaskNode.class);

    public IntegerToOpMaskNode(ValueNode value, Stamp stamp) {
        super(TYPE, stamp, value);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind resultKind = switch (((SimdStamp) this.stamp(NodeView.DEFAULT)).getVectorLength()) {
            case 1, 2, 4, 8 -> LIRKind.value(AMD64Kind.MASK8);
            case 16 -> LIRKind.value(AMD64Kind.MASK16);
            case 32 -> LIRKind.value(AMD64Kind.MASK32);
            case 64 -> LIRKind.value(AMD64Kind.MASK64);
            default -> throw GraalError.shouldNotReachHere("invalid vector length");
        };
        builder.setResult(this, gen.emitMoveIntegerToOpMask(resultKind, builder.operand(getValue())));
    }
}
