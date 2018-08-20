/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.substitutions.amd64;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
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
import org.graalvm.compiler.nodes.memory.MemoryNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_512;

@NodeInfo(size = SIZE_512, cycles = NodeCycles.CYCLES_UNKNOWN)
public class AMD64ArrayIndexOfNCharsNode extends FixedWithNextNode implements LIRLowerable, MemoryAccess {

    public static final NodeClass<AMD64ArrayIndexOfNCharsNode> TYPE = NodeClass.create(AMD64ArrayIndexOfNCharsNode.class);

    private final JavaKind kind;

    @Input private ValueNode arrayPointer;
    @Input private ValueNode arrayLength;
    @Input private NodeInputList<ValueNode> searchValues;

    @OptionalInput(InputType.Memory) private MemoryNode lastLocationAccess;

    public AMD64ArrayIndexOfNCharsNode(@ConstantNodeParameter JavaKind kind, ValueNode arrayPointer, ValueNode arrayLength, ValueNode... searchValues) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.kind = kind;
        this.arrayPointer = arrayPointer;
        this.arrayLength = arrayLength;
        this.searchValues = new NodeInputList<>(this, searchValues);
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return NamedLocationIdentity.getArrayLocation(kind);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value[] searchValueOperands = new Value[searchValues.size()];
        for (int i = 0; i < searchValues.size(); i++) {
            searchValueOperands[i] = gen.operand(searchValues.get(i));
        }
        Value result = gen.getLIRGeneratorTool().emitArrayIndexOf(kind, false, gen.operand(arrayPointer), gen.operand(arrayLength), searchValueOperands);
        gen.setResult(this, result);
    }

    @Override
    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryNode lla) {
        updateUsages(ValueNodeUtil.asNode(lastLocationAccess), ValueNodeUtil.asNode(lla));
        lastLocationAccess = lla;
    }

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, char c1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, char c1, char c2);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, char c1, char c2, char c3);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, char c1, char c2, char c3, char c4);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, byte c1);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, byte c1, byte c2);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, byte c1, byte c2, byte c3);

    @NodeIntrinsic
    public static native int optimizedArrayIndexOf(@ConstantNodeParameter JavaKind kind, Pointer arrayPointer, int arrayLength, byte c1, byte c2, byte c3, byte c4);
}
