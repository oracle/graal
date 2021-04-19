/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;

import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValueNodeUtil;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.MemoryKill;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class ArrayIndexOfNode extends FixedWithNextNode implements LIRLowerable, MemoryAccess {

    public static final NodeClass<ArrayIndexOfNode> TYPE = NodeClass.create(ArrayIndexOfNode.class);

    protected final JavaKind arrayKind;
    protected final JavaKind valueKind;
    protected final boolean findTwoConsecutive;

    @Input protected ValueNode arrayPointer;
    @Input protected ValueNode arrayLength;
    @Input protected ValueNode fromIndex;
    @Input protected NodeInputList<ValueNode> searchValues;

    @OptionalInput(InputType.Memory) protected MemoryKill lastLocationAccess;

    public ArrayIndexOfNode(NodeClass<? extends ArrayIndexOfNode> type, @ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive, ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        super(type, StampFactory.forKind(JavaKind.Int));
        this.arrayKind = arrayKind;
        this.valueKind = valueKind;
        this.findTwoConsecutive = findTwoConsecutive;
        this.arrayPointer = arrayPointer;
        this.arrayLength = arrayLength;
        this.fromIndex = fromIndex;
        this.searchValues = new NodeInputList<>(this, searchValues);
    }

    public ArrayIndexOfNode(@ConstantNodeParameter JavaKind arrayKind, @ConstantNodeParameter JavaKind valueKind, @ConstantNodeParameter boolean findTwoConsecutive,
                    ValueNode arrayPointer, ValueNode arrayLength, ValueNode fromIndex, ValueNode... searchValues) {
        this(TYPE, arrayKind, valueKind, findTwoConsecutive, arrayPointer, arrayLength, fromIndex, searchValues);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(arrayKind);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] searchValueOperands = new Value[searchValues.size()];
        for (int i = 0; i < searchValues.size(); i++) {
            searchValueOperands[i] = gen.operand(searchValues.get(i));
        }
        int arrayBaseOffset = getArrayBaseOffset(gen.getLIRGeneratorTool().getMetaAccess(), arrayPointer, arrayKind);
        Value result = gen.getLIRGeneratorTool().emitArrayIndexOf(arrayBaseOffset, valueKind, findTwoConsecutive, gen.operand(arrayPointer), gen.operand(arrayLength), gen.operand(fromIndex),
                        searchValueOperands);
        gen.setResult(this, result);
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    protected int getArrayBaseOffset(MetaAccessProvider metaAccessProvider, @SuppressWarnings("unused") ValueNode array, JavaKind kind) {
        return metaAccessProvider.getArrayBaseOffset(kind);
    }

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, byte v1, byte v2, byte v3, byte v4);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, char v1, char v2, char v3, char v4);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(
                    @ConstantNodeParameter JavaKind arrayKind,
                    @ConstantNodeParameter JavaKind valueKind,
                    @ConstantNodeParameter boolean findTwoConsecutive,
                    Object array, int arrayLength, int fromIndex, int searchValue);
}
