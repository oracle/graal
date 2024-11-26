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

import static jdk.graal.compiler.nodeinfo.InputType.State;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.GenerateStub;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.DeoptBciSupplier;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;

// JaCoCo Exclude

/**
 * Fills in an array with a given value
 */
@NodeInfo(cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_UNKNOWN, allowedUsageTypes = InputType.Memory)
public class ArrayFillNode extends MemoryKillStubIntrinsicNode
                implements StateSplit, MemoryAccess, DeoptimizingNode, DeoptimizingNode.DeoptDuring, DeoptimizingNode.DeoptBefore, DeoptimizingNode.DeoptAfter, DeoptBciSupplier {

    public static final NodeClass<ArrayFillNode> TYPE = NodeClass.create(ArrayFillNode.class);

    public static final LocationIdentity[] KILLED_LOCATIONS = {NamedLocationIdentity.getArrayLocation(JavaKind.Byte), NamedLocationIdentity.OFF_HEAP_LOCATION};

    /** One array to be tested for equality. */
    @Input protected ValueNode array;

    /** Length of the array. */
    @Input protected ValueNode length;

    /** Value to fill the array with. */
    @Input protected ValueNode value;

    /** {@link JavaKind} of the array to fill. */
    protected JavaKind elementKind;

    @OptionalInput(State) FrameState stateBefore;
    @OptionalInput(State) FrameState stateDuring;
    @OptionalInput(State) FrameState stateAfter;

    protected int bci;

    public ArrayFillNode(ValueNode array, ValueNode length, ValueNode value, @ConstantNodeParameter JavaKind elementKind, int bci) {
        super(TYPE, StampFactory.forVoid(), null, LocationIdentity.any());
        this.bci = bci;
        this.array = array;
        this.length = length;
        this.value = value;
        this.elementKind = elementKind != JavaKind.Illegal ? elementKind : null;
    }

    public ArrayFillNode(ValueNode array, ValueNode length, ValueNode value, @ConstantNodeParameter JavaKind elementKind, int bci,
                    @ConstantNodeParameter EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forVoid(), runtimeCheckedCPUFeatures, LocationIdentity.any());
        this.bci = bci;
        this.array = array;
        this.length = length;
        this.value = value;
        this.elementKind = elementKind != JavaKind.Illegal ? elementKind : null;
    }

    @NodeIntrinsic
    @GenerateStub(name = "byteArrayFill", parameters = {"Byte"})
    @GenerateStub(name = "intArrayFill", parameters = {"Int"})
    public static native void fill(Pointer array, int length, int value,
                    @ConstantNodeParameter JavaKind kind, int bci);

    @NodeIntrinsic
    public static native void fill(Pointer array, int length, int value,
                    @ConstantNodeParameter JavaKind kind, int bci,
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
        gen.getLIRGeneratorTool().emitArrayFill(elementKind, getRuntimeCheckedCPUFeatures(), gen.operand(array), gen.operand(length), gen.operand(value));
    }

    @Override
    public int bci() {
        return bci;
    }

    @Override
    public void setBci(int bci) {
        this.bci = bci;
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
        return KILLED_LOCATIONS;
    }

    public JavaKind getElementKind() {
        return this.elementKind;
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean isSupported(Architecture arch) {
        // if (arch instanceof AMD64) {
        // return ((AMD64) arch).getFeatures().containsAll(minFeaturesAMD64());
        // } else if (arch instanceof AArch64) {
        // return ((AArch64) arch).getFeatures().containsAll(minFeaturesAARCH64());
        // }
        return (arch instanceof AArch64);
    }

    @Override
    public boolean canBeEmitted(Architecture arch) {
        return isSupported(arch);
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public FrameState stateBefore() {
        return stateBefore;
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

    @Override
    public void setStateBefore(FrameState x) {
        updateUsages(this.stateBefore, x);
        this.stateBefore = x;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public void computeStateDuring(FrameState stateAfter1) {
        FrameState newStateDuring = stateAfter1.duplicateModifiedDuringCall(bci, asNode().getStackKind());
        setStateDuring(newStateDuring);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }
}
