/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.HAS_SIDE_EFFECT;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Fills in an array with a given constant value.
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN, allowedUsageTypes = InputType.Memory)
public class ArrayFillNode extends MemoryKillStubIntrinsicNode
                implements StateSplit, MemoryAccess {

    public static final NodeClass<ArrayFillNode> TYPE = NodeClass.create(ArrayFillNode.class);
    private static final ForeignCallDescriptor BYTE_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Byte);
    private static final ForeignCallDescriptor SHORT_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Short);
    private static final ForeignCallDescriptor INT_ARRAY_FILL_STUB = createForeignCallDescriptor(JavaKind.Int);

    public static final ForeignCallDescriptor[] STUBS = {
                    BYTE_ARRAY_FILL_STUB,
                    SHORT_ARRAY_FILL_STUB,
                    INT_ARRAY_FILL_STUB,
    };

    protected JavaKind elementKind;

    @Input protected ValueNode arrayBase;
    @Input protected ValueNode offsetToFirstElement;
    @Input protected ValueNode arrayLength;
    @Input protected ValueNode valueToFillWith;

    public ArrayFillNode(ValueNode arrayBase, ValueNode offsetToFirstElement, ValueNode arrayLength, ValueNode valueToFillWith, @ConstantNodeParameter JavaKind elementKind) {
        super(TYPE, StampFactory.forVoid(), null, LocationIdentity.any());
        this.arrayBase = arrayBase;
        this.offsetToFirstElement = offsetToFirstElement;
        this.arrayLength = arrayLength;
        this.valueToFillWith = valueToFillWith;
        this.elementKind = elementKind;
    }

    public ArrayFillNode(ValueNode arrayBase, ValueNode offsetToFirstElement, ValueNode arrayLength, ValueNode valueToFillWith, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.arrayBase = arrayBase;
        this.offsetToFirstElement = offsetToFirstElement;
        this.arrayLength = arrayLength;
        this.valueToFillWith = valueToFillWith;
        this.elementKind = elementKind;
    }

    @NodeIntrinsic
    @GenerateStub(name = "byteArrayFill", parameters = {"Byte"})
    @GenerateStub(name = "shortArrayFill", parameters = {"Short"})
    @GenerateStub(name = "intArrayFill", parameters = {"Int"})
    public static native void fill(Pointer array, long offset, int length, int value,
                    @ConstantNodeParameter JavaKind kind);

    @NodeIntrinsic
    public static native void fill(Pointer array, long offset, int length, int value,
                    @ConstantNodeParameter JavaKind kind,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures);

    @Override
    public ForeignCallDescriptor getForeignCallDescriptor() {
        switch (this.elementKind) {
            case Byte:
                return BYTE_ARRAY_FILL_STUB;
            case Short:
                return SHORT_ARRAY_FILL_STUB;
            case Int:
                return INT_ARRAY_FILL_STUB;
            default:
                return null;
        }
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{arrayBase, offsetToFirstElement, arrayLength, valueToFillWith};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitArrayFill(elementKind, getRuntimeCheckedCPUFeatures(), gen.operand(arrayBase), gen.operand(offsetToFirstElement), gen.operand(arrayLength),
                        gen.operand(valueToFillWith));
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        return new LocationIdentity[]{NamedLocationIdentity.getArrayLocation(this.elementKind)};
    }

    @Override
    public boolean canBeEmitted(Architecture arch) {
        return (arch instanceof AArch64);
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    @Override
    public void setStateAfter(FrameState x) {
        updateUsages(this.stateAfter, x);
        this.stateAfter = x;
    }

    private static ForeignCallDescriptor createForeignCallDescriptor(JavaKind kind) {
        LocationIdentity[] locs = new LocationIdentity[]{NamedLocationIdentity.getArrayLocation(kind)};
        Class<?>[] argTypes = new Class<?>[]{Pointer.class, long.class, int.class, int.class};
        String name = kind.getJavaName() + "ArrayFill";

        return new ForeignCallDescriptor(name, void.class, argTypes, HAS_SIDE_EFFECT, locs, false, false);
    }
}
