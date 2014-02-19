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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.api.meta.LocationIdentity.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.runtime.*;

public final class ArrayCopyCallNode extends ArrayRangeWriteNode implements Lowerable, MemoryCheckpoint.Single {

    @Input private ValueNode src;
    @Input private ValueNode srcPos;
    @Input private ValueNode dest;
    @Input private ValueNode destPos;
    @Input private ValueNode length;

    private Kind elementKind;
    private boolean aligned;
    private boolean disjoint;

    private ArrayCopyCallNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind, boolean aligned, boolean disjoint) {
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
    }

    private ArrayCopyCallNode(ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos, ValueNode length, Kind elementKind) {
        this(src, srcPos, dest, destPos, length, elementKind, false, false);
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

    @Override
    public ValueNode getArray() {
        return dest;
    }

    @Override
    public ValueNode getIndex() {
        return destPos;
    }

    @Override
    public ValueNode getLength() {
        return length;
    }

    @Override
    public boolean isObjectArray() {
        return elementKind == Kind.Object;
    }

    @Override
    public boolean isInitialization() {
        return false;
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
        FixedWithNextNode basePtr = graph().add(new GetObjectAddressNode(base));
        graph().addBeforeFixed(this, basePtr);
        ValueNode loc = IndexedLocationNode.create(getLocationIdentity(), elementKind, arrayBaseOffset(elementKind), pos, graph(), arrayIndexScale(elementKind));
        return graph().unique(new ComputeAddressNode(basePtr, loc, StampFactory.forKind(Kind.Long)));
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage() == StructuredGraph.GuardsStage.AFTER_FSA) {
            updateAlignedDisjoint();
            ForeignCallDescriptor desc = HotSpotHostForeignCallsProvider.lookupArraycopyDescriptor(elementKind, isAligned(), isDisjoint());
            StructuredGraph graph = graph();
            ValueNode srcAddr = computeBase(getSource(), getSourcePosition());
            ValueNode destAddr = computeBase(getDestination(), getDestinationPosition());
            ValueNode len = getLength();
            if (len.kind() != Kind.Long) {
                len = graph().unique(new ConvertNode(len.kind(), Kind.Long, len));
            }
            ForeignCallNode call = graph.add(new ForeignCallNode(Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getForeignCalls(), desc, srcAddr, destAddr, len));
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
    public static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind);

    @NodeIntrinsic
    public static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter Kind elementKind, @ConstantNodeParameter boolean aligned,
                    @ConstantNodeParameter boolean disjoint);

    public boolean isAligned() {
        return aligned;
    }

    public boolean isDisjoint() {
        return disjoint;
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
