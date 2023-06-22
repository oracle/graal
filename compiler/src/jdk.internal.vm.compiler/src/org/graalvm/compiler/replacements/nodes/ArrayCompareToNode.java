/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_1024;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_16;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.Stride;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Compares two arrays lexicographically.
 */
@NodeInfo(cycles = CYCLES_1024, size = SIZE_16)
public class ArrayCompareToNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, Virtualizable {

    public static final NodeClass<ArrayCompareToNode> TYPE = NodeClass.create(ArrayCompareToNode.class);

    /** {@link Stride} of one array to compare. */
    protected final Stride strideA;

    /** {@link Stride} of the other array to compare. */
    protected final Stride strideB;

    /** One array to be tested for equality. */
    @Input protected ValueNode arrayA;

    /** Length of array A. */
    @Input protected ValueNode lengthA;

    /** The other array to be tested for equality. */
    @Input protected ValueNode arrayB;

    /** Length of array B. */
    @Input protected ValueNode lengthB;

    public ArrayCompareToNode(ValueNode arrayA, ValueNode lengthA, ValueNode arrayB, ValueNode lengthB,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB) {
        this(TYPE, arrayA, lengthA, arrayB, lengthB, strideA, strideB);
    }

    public ArrayCompareToNode(ValueNode arrayA, ValueNode lengthA, ValueNode arrayB, ValueNode lengthB,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, lengthA, arrayB, lengthB, strideA, strideB, runtimeCheckedCPUFeatures);
    }

    protected ArrayCompareToNode(NodeClass<? extends ArrayCompareToNode> c, ValueNode arrayA, ValueNode lengthA, ValueNode arrayB, ValueNode lengthB,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB) {
        this(c, arrayA, lengthA, arrayB, lengthB, strideA, strideB, null);
    }

    protected ArrayCompareToNode(NodeClass<? extends ArrayCompareToNode> c, ValueNode arrayA, ValueNode lengthA, ValueNode arrayB, ValueNode lengthB,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        super(c, StampFactory.forKind(JavaKind.Int), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(JavaKind.Byte));
        this.strideA = strideA;
        this.strideB = strideB;
        this.arrayA = arrayA;
        this.lengthA = lengthA;
        this.arrayB = arrayB;
        this.lengthB = lengthB;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            return null;
        }
        ValueNode a1 = GraphUtil.unproxify(arrayA);
        ValueNode a2 = GraphUtil.unproxify(arrayB);
        if (a1 == a2) {
            return ConstantNode.forInt(0);
        }
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias1 = tool.getAlias(arrayA);
        ValueNode alias2 = tool.getAlias(arrayB);
        if (alias1 == alias2) {
            // the same virtual objects will always have the same contents
            tool.replaceWithValue(ConstantNode.forInt(0, graph()));
        }
    }

    @NodeIntrinsic
    @GenerateStub(name = "byteArrayCompareToByteArray", parameters = {"S1", "S1"})
    @GenerateStub(name = "byteArrayCompareToCharArray", parameters = {"S1", "S2"})
    @GenerateStub(name = "charArrayCompareToByteArray", parameters = {"S2", "S1"})
    @GenerateStub(name = "charArrayCompareToCharArray", parameters = {"S2", "S2"})
    public static native int compareTo(Pointer arrayA, int lengthA, Pointer arrayB, int lengthB,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB);

    @NodeIntrinsic
    public static native int compareTo(Pointer arrayA, int lengthA, Pointer arrayB, int lengthB,
                    @ConstantNodeParameter Stride strideA,
                    @ConstantNodeParameter Stride strideB,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    public Stride getStrideA() {
        return strideA;
    }

    public Stride getStrideB() {
        return strideB;
    }

    public ValueNode getArrayA() {
        return arrayA;
    }

    public ValueNode getLengthA() {
        return lengthA;
    }

    public ValueNode getArrayB() {
        return arrayB;
    }

    public ValueNode getLengthB() {
        return lengthB;
    }

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayCompareToForeignCalls.getStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{arrayA, lengthA, arrayB, lengthB};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitArrayCompareTo(
                        strideA,
                        strideB,
                        getRuntimeCheckedCPUFeatures(),
                        gen.operand(arrayA),
                        gen.operand(lengthA),
                        gen.operand(arrayB),
                        gen.operand(lengthB)));
    }
}
