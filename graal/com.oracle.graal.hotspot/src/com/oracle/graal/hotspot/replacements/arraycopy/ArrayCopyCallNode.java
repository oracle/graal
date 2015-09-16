/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
//JaCoCo Exclude
package com.oracle.graal.hotspot.replacements.arraycopy;

import static jdk.internal.jvmci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayBaseOffset;
import static jdk.internal.jvmci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;
import static jdk.internal.jvmci.hotspot.HotSpotVMConfig.config;
import jdk.internal.jvmci.code.CodeUtil;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LocationIdentity;
import jdk.internal.jvmci.meta.PrimitiveConstant;

import com.oracle.graal.api.runtime.Graal;
import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.hotspot.HotSpotGraalRuntimeProvider;
import com.oracle.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import com.oracle.graal.hotspot.nodes.GetObjectAddressNode;
import com.oracle.graal.nodeinfo.InputType;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.NamedLocationIdentity;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.AddNode;
import com.oracle.graal.nodes.calc.IntegerConvertNode;
import com.oracle.graal.nodes.calc.LeftShiftNode;
import com.oracle.graal.nodes.extended.ForeignCallNode;
import com.oracle.graal.nodes.memory.AbstractMemoryCheckpoint;
import com.oracle.graal.nodes.memory.MemoryAccess;
import com.oracle.graal.nodes.memory.MemoryCheckpoint;
import com.oracle.graal.nodes.memory.MemoryNode;
import com.oracle.graal.nodes.memory.address.OffsetAddressNode;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.runtime.RuntimeProvider;

@NodeInfo(allowedUsageTypes = {InputType.Memory})
public final class ArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single, MemoryAccess, Canonicalizable {

    public static final NodeClass<ArrayCopyCallNode> TYPE = NodeClass.create(ArrayCopyCallNode.class);
    @Input protected ValueNode src;
    @Input protected ValueNode srcPos;
    @Input protected ValueNode dest;
    @Input protected ValueNode destPos;
    @Input protected ValueNode length;

    @OptionalInput(InputType.Memory) MemoryNode lastLocationAccess;

    protected final JavaKind elementKind;
    protected final LocationIdentity locationIdentity;

    /**
     * Aligned means that the offset of the copy is heap word aligned.
     */
    protected boolean aligned;
    protected boolean disjoint;
    protected boolean uninitialized;

    protected final HotSpotGraalRuntimeProvider runtime;

    public ArrayCopyCallNode(@InjectedNodeParameter HotSpotGraalRuntimeProvider runtime, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind,
                    boolean aligned, boolean disjoint, boolean uninitialized) {
        this(runtime, src, srcPos, dest, destPos, length, elementKind, null, aligned, disjoint, uninitialized);
    }

    public ArrayCopyCallNode(@InjectedNodeParameter HotSpotGraalRuntimeProvider runtime, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind,
                    boolean disjoint) {
        this(runtime, src, srcPos, dest, destPos, length, elementKind, null, false, disjoint, false);
    }

    protected ArrayCopyCallNode(@InjectedNodeParameter HotSpotGraalRuntimeProvider runtime, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, JavaKind elementKind,
                    LocationIdentity locationIdentity, boolean aligned, boolean disjoint, boolean uninitialized) {
        super(TYPE, StampFactory.forVoid());
        assert elementKind != null;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.elementKind = elementKind;
        this.locationIdentity = (locationIdentity != null ? locationIdentity : NamedLocationIdentity.getArrayLocation(elementKind));
        this.aligned = aligned;
        this.disjoint = disjoint;
        this.uninitialized = uninitialized;
        this.runtime = runtime;
    }

    public ValueNode getSource() {
        return src;
    }

    public ValueNode getSourcePosition() {
        return srcPos;
    }

    public ValueNode getDestination() {
        return dest;
    }

    public ValueNode getDestinationPosition() {
        return destPos;
    }

    public ValueNode getLength() {
        return length;
    }

    public JavaKind getElementKind() {
        return elementKind;
    }

    private ValueNode computeBase(ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = graph().add(new GetObjectAddressNode(base));
        graph().addBeforeFixed(this, basePtr);
        Stamp wordStamp = StampFactory.forKind(runtime.getTarget().wordJavaKind);
        ValueNode wordPos = IntegerConvertNode.convert(pos, wordStamp, graph());
        int shift = CodeUtil.log2(getArrayIndexScale(elementKind));
        ValueNode scaledIndex = graph().unique(new LeftShiftNode(wordPos, ConstantNode.forInt(shift, graph())));
        ValueNode offset = graph().unique(new AddNode(scaledIndex, ConstantNode.forIntegerStamp(wordStamp, getArrayBaseOffset(elementKind), graph())));
        return graph().unique(new OffsetAddressNode(basePtr, offset));
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            updateAlignedDisjoint();
            ForeignCallDescriptor desc = HotSpotHostForeignCallsProvider.lookupArraycopyDescriptor(elementKind, isAligned(), isDisjoint(), isUninitialized(),
                            locationIdentity.equals(LocationIdentity.any()));
            StructuredGraph graph = graph();
            ValueNode srcAddr = computeBase(getSource(), getSourcePosition());
            ValueNode destAddr = computeBase(getDestination(), getDestinationPosition());
            ValueNode len = getLength();
            if (len.stamp().getStackKind() != JavaKind.Long) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(JavaKind.Long), graph());
            }
            ForeignCallNode call = graph.add(new ForeignCallNode(Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getForeignCalls(), desc, srcAddr, destAddr, len));
            call.setStateAfter(stateAfter());
            graph.replaceFixedWithFixed(this, call);
        }
    }

    public MemoryNode getLastLocationAccess() {
        return lastLocationAccess;
    }

    public void setLastLocationAccess(MemoryNode lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @NodeIntrinsic
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter boolean aligned,
                    @ConstantNodeParameter boolean disjoint, @ConstantNodeParameter boolean uninitialized);

    @NodeIntrinsic
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter LocationIdentity locationIdentity, @ConstantNodeParameter boolean aligned, @ConstantNodeParameter boolean disjoint,
                    @ConstantNodeParameter boolean uninitialized);

    public static void arraycopyObjectKillsAny(Object src, int srcPos, Object dest, int destPos, int length) {
        arraycopy(src, srcPos, dest, destPos, length, JavaKind.Object, LocationIdentity.any(), false, false, false);
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, false, false);
    }

    public static void disjointArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, true, false);
    }

    public static void disjointUninitializedArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, true, true);
    }

    public boolean isAligned() {
        return aligned;
    }

    public boolean isDisjoint() {
        return disjoint;
    }

    public boolean isUninitialized() {
        return uninitialized;
    }

    static boolean isHeapWordAligned(JavaConstant value, JavaKind kind) {
        return (getArrayBaseOffset(kind) + (long) value.asInt() * getArrayIndexScale(kind)) % config().heapWordSize == 0;
    }

    public void updateAlignedDisjoint() {
        JavaKind componentKind = elementKind;
        if (srcPos == destPos) {
            // Can treat as disjoint
            disjoint = true;
        }
        PrimitiveConstant constantSrc = (PrimitiveConstant) srcPos.stamp().asConstant();
        PrimitiveConstant constantDst = (PrimitiveConstant) destPos.stamp().asConstant();
        if (constantSrc != null && constantDst != null) {
            if (!aligned) {
                aligned = isHeapWordAligned(constantSrc, componentKind) && isHeapWordAligned(constantDst, componentKind);
            }
            if (constantSrc.asInt() >= constantDst.asInt()) {
                // low to high copy so treat as disjoint
                disjoint = true;
            }
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getLength().isConstant() && getLength().asConstant().isDefaultForKind()) {
            if (lastLocationAccess != null) {
                replaceAtUsages(InputType.Memory, lastLocationAccess.asNode());
            }
            return null;
        }
        return this;
    }
}
