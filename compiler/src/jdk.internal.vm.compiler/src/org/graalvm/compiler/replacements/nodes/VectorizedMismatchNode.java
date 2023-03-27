/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.GenerateStub;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Returns the index of the first non-equal elements in two memory regions, or -1 if they are equal.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public class VectorizedMismatchNode extends PureFunctionStubIntrinsicNode {

    public static final NodeClass<VectorizedMismatchNode> TYPE = NodeClass.create(VectorizedMismatchNode.class);

    /**
     * Direct pointer to memory region A.
     */
    @Input protected ValueNode arrayA;

    /**
     * Direct pointer to memory region B.
     */
    @Input protected ValueNode arrayB;

    /**
     * Length of the memory region (as number of array elements). The caller is responsible for
     * ensuring that the region, starting at the respective offset, is within array bounds.
     */
    @Input protected ValueNode length;

    /**
     * Element size in log2 format (0: byte, 1: char, 2: int, 3: long).
     */
    @Input protected ValueNode stride;

    public VectorizedMismatchNode(ValueNode arrayA, ValueNode arrayB, ValueNode length, ValueNode stride) {
        this(TYPE, arrayA, arrayB, length, stride, null);
    }

    public VectorizedMismatchNode(ValueNode arrayA, ValueNode arrayB, ValueNode length, ValueNode stride, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, arrayA, arrayB, length, stride, runtimeCheckedCPUFeatures);
    }

    protected VectorizedMismatchNode(NodeClass<? extends VectorizedMismatchNode> c, ValueNode arrayA, ValueNode arrayB, ValueNode length, ValueNode stride, EnumSet<?> runtimeCheckedCPUFeatures) {
        super(c, StampFactory.forKind(JavaKind.Int), runtimeCheckedCPUFeatures, LocationIdentity.ANY_LOCATION);
        this.arrayA = arrayA;
        this.arrayB = arrayB;
        this.length = length;
        this.stride = stride;
    }

    @NodeIntrinsic
    @GenerateStub
    public static native int vectorizedMismatch(Pointer arrayA, Pointer arrayB, int length, int stride);

    @NodeIntrinsic
    public static native int vectorizedMismatch(Pointer arrayA, Pointer arrayB, int length, int stride, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return VectorizedMismatchForeignCalls.STUB;
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{arrayA, arrayB, length, stride};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().emitVectorizedMismatch(getRuntimeCheckedCPUFeatures(), gen.operand(arrayA), gen.operand(arrayB), gen.operand(length), gen.operand(stride)));
    }
}
