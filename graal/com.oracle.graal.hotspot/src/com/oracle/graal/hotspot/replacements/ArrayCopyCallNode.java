/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.runtime.*;

@NodeInfo(allowedUsageTypes = {InputType.Memory})
public class ArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryCheckpoint.Single {

    @Input private ValueNode src;
    @Input private ValueNode srcPos;
    @Input private ValueNode dest;
    @Input private ValueNode destPos;
    @Input private ValueNode length;

    private Kind elementKind;

    /**
     * Aligned means that the offset of the copy is heap word aligned.
     */
    private boolean aligned;
    private boolean disjoint;
    private boolean uninitialized;

    public static ArrayCopyCallNode create(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind, boolean aligned, boolean disjoint,
                    boolean uninitialized) {
        return new ArrayCopyCallNodeGen(src, srcPos, dest, destPos, length, elementKind, aligned, disjoint, uninitialized);
    }

    ArrayCopyCallNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind, boolean aligned, boolean disjoint, boolean uninitialized) {
        super(StampFactory.forVoid());
        assert elementKind != null;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.elementKind = elementKind;
        this.aligned = aligned;
        this.disjoint = disjoint;
        this.uninitialized = uninitialized;
    }

    public static ArrayCopyCallNode create(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind, boolean disjoint) {
        return new ArrayCopyCallNodeGen(src, srcPos, dest, destPos, length, elementKind, disjoint);
    }

    ArrayCopyCallNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind, boolean disjoint) {
        this(src, srcPos, dest, destPos, length, elementKind, false, disjoint, false);
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

    public void addSnippetArguments(Arguments args) {
        args.add("src", src);
        args.add("srcPos", srcPos);
        args.add("dest", dest);
        args.add("destPos", destPos);
        args.add("length", length);
    }

    public Kind getElementKind() {
        return elementKind;
    }

    private boolean shouldUnroll() {
        return getLength().isConstant() && getLength().asConstant().asInt() <= GraalOptions.MaximumEscapeAnalysisArrayLength.getValue();
    }

    private ValueNode computeBase(ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = graph().add(GetObjectAddressNode.create(base));
        graph().addBeforeFixed(this, basePtr);
        ValueNode loc = IndexedLocationNode.create(getLocationIdentity(), elementKind, arrayBaseOffset(elementKind), pos, graph(), arrayIndexScale(elementKind));
        return graph().unique(ComputeAddressNode.create(basePtr, loc, StampFactory.forKind(Kind.Long)));
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
            updateAlignedDisjoint();
            ForeignCallDescriptor desc = HotSpotHostForeignCallsProvider.lookupArraycopyDescriptor(elementKind, isAligned(), isDisjoint(), isUninitialized());
            StructuredGraph graph = graph();
            ValueNode srcAddr = computeBase(getSource(), getSourcePosition());
            ValueNode destAddr = computeBase(getDestination(), getDestinationPosition());
            ValueNode len = getLength();
            if (len.stamp().getStackKind() != Kind.Long) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(Kind.Long), graph());
            }
            ForeignCallNode call = graph.add(ForeignCallNode.create(Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getForeignCalls(), desc, srcAddr, destAddr, len));
            call.setStateAfter(stateAfter());
            graph.replaceFixedWithFixed(this, call);

        }
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        if (elementKind != null) {
            return NamedLocationIdentity.getArrayLocation(elementKind);
        }
        return ANY_LOCATION;
    }

    @NodeIntrinsic
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind, @ConstantNodeParameter boolean aligned,
                    @ConstantNodeParameter boolean disjoint, @ConstantNodeParameter boolean uninitialized);

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind, boolean aligned, boolean disjoint) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, aligned, disjoint, false);
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

    public void updateAlignedDisjoint() {
        Kind componentKind = elementKind;
        if (srcPos == destPos) {
            // Can treat as disjoint
            disjoint = true;
        }
        Constant constantSrc = srcPos.stamp().asConstant();
        Constant constantDst = destPos.stamp().asConstant();
        if (constantSrc != null && constantDst != null) {
            if (!aligned) {
                aligned = ArrayCopyNode.isHeapWordAligned(constantSrc, componentKind) && ArrayCopyNode.isHeapWordAligned(constantDst, componentKind);
            }
            if (constantSrc.asInt() >= constantDst.asInt()) {
                // low to high copy so treat as disjoint
                disjoint = true;
            }
        }
    }
}
