/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.vm.ci.meta.PlatformKind;

/**
 * Reinterpret the lower bits of a SIMD value as a narrower SIMD value, usually involving a change
 * of the underlying element type as well. For example, this node can reinterpret the lower 16 bytes
 * of
 *
 * <pre>
 * &lt;i8 [0], ..., i8 [0]&gt;  // 32 bytes
 * </pre>
 *
 * as
 *
 * <pre>
 * &lti64 [0], i64 [0]&gt;  // 16 bytes
 * </pre>
 *
 * to allow the use of the same register for both values. The bits themselves must make sense for
 * the target value, so the usefulness of this node is mostly limited to all-zeros or all-ones
 * constants.
 * </p>
 *
 * Compare {@link ReinterpretNode}, which requires the input to be the same size as the result, and
 * to {@link SimdCutNode}, which requires the SIMD element types to match.
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_0,
          cyclesRationale = "just a change of type, no computation is involved",
          size = SIZE_0,
          sizeRationale = "just a change of type, no computation is involved")
// @formatter:on
public class SimdNarrowingReinterpretNode extends UnaryNode implements VectorLIRLowerable {
    public static final NodeClass<SimdNarrowingReinterpretNode> TYPE = NodeClass.create(SimdNarrowingReinterpretNode.class);

    protected SimdNarrowingReinterpretNode(ValueNode value, SimdStamp stamp) {
        super(TYPE, stamp, value);
    }

    public static ValueNode create(ValueNode value, SimdStamp stamp) {
        if (value.stamp(NodeView.DEFAULT).equals(stamp)) {
            return value;
        } else {
            return new SimdNarrowingReinterpretNode(value, stamp);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRGeneratorTool lirTool = builder.getLIRGeneratorTool();
        LIRKind castKind = lirTool.getLIRKind(stamp(NodeView.DEFAULT));
        PlatformKind operandKind = lirTool.getLIRKind(getValue().stamp(NodeView.DEFAULT)).getPlatformKind();
        assert castKind.getPlatformKind().getSizeInBytes() <= operandKind.getSizeInBytes() : castKind.getPlatformKind() + " ! <= " + operandKind.getSizeInBytes();
        builder.setResult(this, new CastValue(castKind, lirTool.asAllocatable(builder.operand(getValue()))));
    }
}
