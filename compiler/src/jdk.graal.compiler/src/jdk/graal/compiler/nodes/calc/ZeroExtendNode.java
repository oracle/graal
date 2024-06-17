/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.Narrow;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.ZeroExtend;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.CodeUtil;

/**
 * The {@code ZeroExtendNode} converts an integer to a wider integer using zero extension.
 *
 * On all supported architectures, sub-word (&lt;32 bit) operations generally do not yield
 * performance improvements. They can even be slower than 32 bit operations. Thus, nodes extending
 * &lt;32 bit values to 32 bit or more should usually not be removed.
 */
@NodeInfo(cycles = CYCLES_1)
public final class ZeroExtendNode extends IntegerConvertNode<ZeroExtend> {

    public static final NodeClass<ZeroExtendNode> TYPE = NodeClass.create(ZeroExtendNode.class);

    public ZeroExtendNode(ValueNode input, int resultBits) {
        this(input, PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)), resultBits);
        assert 0 < PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) && PrimitiveStamp.getBits(input.stamp(NodeView.DEFAULT)) <= resultBits : Assertions.errorMessageContext("input", input,
                        "resultBits", resultBits);
    }

    public ZeroExtendNode(ValueNode input, int inputBits, int resultBits) {
        super(TYPE, BinaryArithmeticNode.getArithmeticOpTable(input).getZeroExtend(), inputBits, resultBits, input);
    }

    public static ValueNode create(ValueNode input, int resultBits, NodeView view) {
        return create(input, PrimitiveStamp.getBits(input.stamp(view)), resultBits, view);
    }

    public static ValueNode create(ValueNode input, int inputBits, int resultBits, NodeView view) {
        IntegerConvertOp<ZeroExtend> signExtend = ArithmeticOpTable.forStamp(input.stamp(view)).getZeroExtend();
        ValueNode synonym = findSynonym(signExtend, input, inputBits, resultBits, signExtend.foldStamp(inputBits, resultBits, input.stamp(view)));
        if (synonym != null) {
            return synonym;
        }
        return canonical(null, input, inputBits, resultBits, view);
    }

    @Override
    protected IntegerConvertOp<ZeroExtend> getOp(ArithmeticOpTable table) {
        return table.getZeroExtend();
    }

    @Override
    protected IntegerConvertOp<Narrow> getReverseOp(ArithmeticOpTable table) {
        return table.getNarrow();
    }

    @Override
    public boolean isLossless() {
        return true;
    }

    @Override
    public boolean preservesOrder(CanonicalCondition cond) {
        switch (cond) {
            case LT:
                return false;
            default:
                return true;
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        NodeView view = NodeView.from(tool);
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        return canonical(this, forValue, getInputBits(), getResultBits(), view);
    }

    private static ValueNode canonical(ZeroExtendNode zeroExtendNode, ValueNode forValue, int inputBits, int resultBits, NodeView view) {
        ZeroExtendNode self = zeroExtendNode;
        if (forValue instanceof ZeroExtendNode) {
            // xxxx -(zero-extend)-> 0000 xxxx -(zero-extend)-> 00000000 0000xxxx
            // ==> xxxx -(zero-extend)-> 00000000 0000xxxx
            ZeroExtendNode other = (ZeroExtendNode) forValue;
            return new ZeroExtendNode(other.getValue(), other.getInputBits(), resultBits);
        }
        if (forValue instanceof NarrowNode) {
            NarrowNode narrow = (NarrowNode) forValue;
            Stamp inputStamp = narrow.getValue().stamp(view);
            if (inputStamp instanceof IntegerStamp) {
                IntegerStamp istamp = (IntegerStamp) inputStamp;
                long mask = CodeUtil.mask(PrimitiveStamp.getBits(narrow.stamp(view)));

                if ((istamp.mayBeSet() & ~mask) == 0) {
                    // The original value cannot change because of the narrow and zero extend.

                    if (istamp.getBits() < resultBits) {
                        // Need to keep the zero extend, skip the narrow.
                        return create(narrow.getValue(), resultBits, view);
                    } else if (istamp.getBits() > resultBits) {
                        // Need to keep the narrow, skip the zero extend.
                        return NarrowNode.create(narrow.getValue(), resultBits, view);
                    } else {
                        assert istamp.getBits() == resultBits : Assertions.errorMessageContext("zeroExtend", zeroExtendNode, "forVal", forValue, "inputBits", inputBits, "resultBits", resultBits);
                        // Just return the original value.
                        return narrow.getValue();
                    }
                }
            }
        }

        if (self == null) {
            self = new ZeroExtendNode(forValue, inputBits, resultBits);
        }
        return self;
    }

    /**
     * @return true if the {@code usage} may depend on the LIRKinds of its inputs. Many arithmetic
     *         operations often take the LIRKind of the first input as its own LIRKind.
     */
    private static boolean mayBeLIRKindDependentOp(Node usage) {
        return !(usage instanceof AddressNode);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        // If the value originates from caller, we have no control on the upper bits.
        boolean requiresExplicitZeroExtend = getValue() instanceof ParameterNode || getValue() instanceof NarrowNode;
        boolean requiresLIRKindChange = usages().filter(ZeroExtendNode::mayBeLIRKindDependentOp).isNotEmpty();

        nodeValueMap.setResult(this, gen.emitZeroExtend(nodeValueMap.operand(getValue()), getInputBits(), getResultBits(),
                        requiresExplicitZeroExtend, requiresLIRKindChange));
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return true;
    }
}
