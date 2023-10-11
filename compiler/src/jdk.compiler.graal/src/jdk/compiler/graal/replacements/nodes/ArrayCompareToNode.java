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
package jdk.compiler.graal.replacements.nodes;

import static jdk.compiler.graal.nodeinfo.NodeCycles.CYCLES_1024;
import static jdk.compiler.graal.nodeinfo.NodeSize.SIZE_16;

import java.util.EnumSet;

import jdk.compiler.graal.core.common.Stride;
import jdk.compiler.graal.core.common.spi.ForeignCallDescriptor;
import jdk.compiler.graal.core.common.type.StampFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.graph.NodeClass;
import jdk.compiler.graal.lir.GenerateStub;
import jdk.compiler.graal.nodeinfo.NodeInfo;
import jdk.compiler.graal.nodes.ConstantNode;
import jdk.compiler.graal.nodes.NamedLocationIdentity;
import jdk.compiler.graal.nodes.ValueNode;
import jdk.compiler.graal.nodes.spi.Canonicalizable;
import jdk.compiler.graal.nodes.spi.CanonicalizerTool;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.nodes.spi.Virtualizable;
import jdk.compiler.graal.nodes.spi.VirtualizerTool;
import jdk.compiler.graal.nodes.util.GraphUtil;
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
