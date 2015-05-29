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

import com.oracle.jvmci.meta.NamedLocationIdentity;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.PrimitiveConstant;
import com.oracle.jvmci.meta.LocationIdentity;
import com.oracle.jvmci.meta.ForeignCallDescriptor;
import com.oracle.jvmci.meta.Kind;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.runtime.*;
import com.oracle.jvmci.hotspot.*;

@NodeInfo(allowedUsageTypes = {InputType.Memory})
public final class ArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single, MemoryAccess, Canonicalizable {

    public static final NodeClass<ArrayCopyCallNode> TYPE = NodeClass.create(ArrayCopyCallNode.class);
    @Input protected ValueNode src;
    @Input protected ValueNode srcPos;
    @Input protected ValueNode dest;
    @Input protected ValueNode destPos;
    @Input protected ValueNode length;

    @OptionalInput(InputType.Memory) MemoryNode lastLocationAccess;

    protected final Kind elementKind;
    protected final LocationIdentity locationIdentity;

    /**
     * Aligned means that the offset of the copy is heap word aligned.
     */
    protected boolean aligned;
    protected boolean disjoint;
    protected boolean uninitialized;

    protected final HotSpotGraalRuntimeProvider runtime;

    public ArrayCopyCallNode(@InjectedNodeParameter HotSpotGraalRuntimeProvider runtime, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind,
                    boolean aligned, boolean disjoint, boolean uninitialized) {
        this(runtime, src, srcPos, dest, destPos, length, elementKind, null, aligned, disjoint, uninitialized);
    }

    public ArrayCopyCallNode(@InjectedNodeParameter HotSpotGraalRuntimeProvider runtime, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind,
                    boolean disjoint) {
        this(runtime, src, srcPos, dest, destPos, length, elementKind, null, false, disjoint, false);
    }

    protected ArrayCopyCallNode(@InjectedNodeParameter HotSpotGraalRuntimeProvider runtime, ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind,
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

    public Kind getElementKind() {
        return elementKind;
    }

    private ValueNode computeBase(ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = graph().add(new GetObjectAddressNode(base));
        graph().addBeforeFixed(this, basePtr);
        ValueNode loc = graph().unique(
                        new IndexedLocationNode(getLocationIdentity(), runtime.getJVMCIRuntime().getArrayBaseOffset(elementKind), pos, runtime.getJVMCIRuntime().getArrayIndexScale(elementKind)));
        return graph().unique(new ComputeAddressNode(basePtr, loc, StampFactory.forKind(Kind.Long)));
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
            if (len.stamp().getStackKind() != Kind.Long) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(Kind.Long), graph());
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
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind, @ConstantNodeParameter boolean aligned,
                    @ConstantNodeParameter boolean disjoint, @ConstantNodeParameter boolean uninitialized);

    @NodeIntrinsic
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind,
                    @ConstantNodeParameter LocationIdentity locationIdentity, @ConstantNodeParameter boolean aligned, @ConstantNodeParameter boolean disjoint,
                    @ConstantNodeParameter boolean uninitialized);

    public static void arraycopyObjectKillsAny(Object src, int srcPos, Object dest, int destPos, int length) {
        arraycopy(src, srcPos, dest, destPos, length, Kind.Object, LocationIdentity.any(), false, false, false);
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, false, false);
    }

    public static void disjointArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, true, false);
    }

    public static void disjointUninitializedArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind) {
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

    boolean isHeapWordAligned(JavaConstant value, Kind kind) {
        HotSpotJVMCIRuntimeProvider jvmciRuntime = runtime.getJVMCIRuntime();
        return (jvmciRuntime.getArrayBaseOffset(kind) + (long) value.asInt() * jvmciRuntime.getArrayIndexScale(kind)) % runtime.getConfig().heapWordSize == 0;
    }

    public void updateAlignedDisjoint() {
        Kind componentKind = elementKind;
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
