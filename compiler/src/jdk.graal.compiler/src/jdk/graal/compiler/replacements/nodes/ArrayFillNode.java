/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Fills in an array with a given value
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_16)
public class ArrayFillNode extends PureFunctionStubIntrinsicNode implements Canonicalizable, Virtualizable {

    public static final NodeClass<ArrayFillNode> TYPE = NodeClass.create(ArrayFillNode.class);

    /** {@link JavaKind} of the array to fill. */
    protected final JavaKind kind;

    /** One array to be tested for equality. */
    @Input protected ValueNode array;

    /** Length of the array. */
    @Input protected ValueNode length;

    /** Value to fill the array with. */
    @Input protected ValueNode value;

    public ArrayFillNode(ValueNode array1, ValueNode length, ValueNode value, @ConstantNodeParameter JavaKind kind) {
        this(TYPE, array1, length, value, kind, null);
    }

    public ArrayFillNode(ValueNode array1, ValueNode length, ValueNode value, @ConstantNodeParameter JavaKind kind, @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        this(TYPE, array1, length, value, kind, runtimeCheckedCPUFeatures);
    }

    protected ArrayFillNode(NodeClass<? extends ArrayFillNode> c, ValueNode array, ValueNode length, ValueNode value,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        super(c, StampFactory.forVoid(), runtimeCheckedCPUFeatures, NamedLocationIdentity.getArrayLocation(kind));
        this.kind = kind;
        this.array = array;
        this.length = length;
        this.value = value;
    }

    public JavaKind getKind() {
        return this.kind;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        return this;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
    }

    @NodeIntrinsic
    @GenerateStub(name = "byteArrayFill", parameters = {"Byte"})
    @GenerateStub(name = "intArrayFill", parameters = {"Int"})
    public static native void fill(Pointer array, int length, int value,
                    @ConstantNodeParameter JavaKind kind);

    @NodeIntrinsic
    public static native void fill(Pointer array, int length, int value,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayFillForeignCalls.getArrayFillStub(this);
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{array, length, value};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitArrayFill(kind, getRuntimeCheckedCPUFeatures(), gen.operand(array), gen.operand(length), gen.operand(value));
    }

}
