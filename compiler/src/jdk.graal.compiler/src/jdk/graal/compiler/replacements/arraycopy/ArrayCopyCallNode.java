/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
//JaCoCo Exclude
package jdk.graal.compiler.replacements.arraycopy;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import java.util.Arrays;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GetObjectAddressNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.IntegerConvertNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.memory.AbstractMemoryCheckpoint;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Implements {@link System#arraycopy} via a {@linkplain ForeignCallNode call} to specialized stubs
 * based on the element type and memory properties.
 *
 * The target of the call is queried via {@link ArrayCopyLookup#lookupArraycopyDescriptor}.
 * <p>
 * <strong>Note:</strong> this node does not have an exception edge. Only use in situations where
 * the exception edge is not needed.
 */
@NodeInfo(allowedUsageTypes = {Memory}, cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class ArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, SingleMemoryKill, MemoryAccess, Canonicalizable {

    public static final NodeClass<ArrayCopyCallNode> TYPE = NodeClass.create(ArrayCopyCallNode.class);
    @Input protected ValueNode src;
    @Input protected ValueNode srcPos;
    @Input protected ValueNode dest;
    @Input protected ValueNode destPos;
    @Input protected ValueNode length;

    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    private final JavaKind elementKind;
    private final LocationIdentity locationIdentity;
    private final LocationIdentity killedLocationIdentity;
    private final ArrayCopyForeignCalls foreignCalls;
    private final JavaKind wordJavaKind;
    private final int heapWordSize;

    /**
     * Aligned means that the offset of the copy is heap word aligned.
     */
    private boolean aligned;
    private boolean disjoint;
    private boolean uninitialized;

    public ArrayCopyCallNode(@InjectedNodeParameter ArrayCopyForeignCalls foreignCalls, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos,
                    ValueNode length, JavaKind elementKind, boolean aligned, boolean disjoint, boolean uninitialized, int heapWordSize) {
        this(foreignCalls, wordTypes, src, srcPos, dest, destPos, length, elementKind, (LocationIdentity) null, null, aligned, disjoint, uninitialized, heapWordSize);
    }

    public ArrayCopyCallNode(@InjectedNodeParameter ArrayCopyForeignCalls foreignCalls, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode src, ValueNode srcPos, ValueNode dest, ValueNode destPos,
                    ValueNode length, JavaKind copyKind, JavaKind srcKind, JavaKind destKind, boolean aligned, boolean disjoint, boolean uninitialized, int heapWordSize) {
        this(foreignCalls, wordTypes, src, srcPos, dest, destPos, length, copyKind, NamedLocationIdentity.getArrayLocation(srcKind), NamedLocationIdentity.getArrayLocation(destKind), aligned,
                        disjoint, uninitialized, heapWordSize);
    }

    public ArrayCopyCallNode(@InjectedNodeParameter ArrayCopyForeignCalls foreignCalls, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode src, ValueNode srcPos, ValueNode dest,
                    ValueNode destPos, ValueNode length, JavaKind elementKind,
                    LocationIdentity killedLocationIdentity, boolean aligned, boolean disjoint, boolean uninitialized, int heapWordSize) {
        this(foreignCalls, wordTypes, src, srcPos, dest, destPos, length, elementKind, null, killedLocationIdentity, aligned, disjoint, uninitialized, heapWordSize);
    }

    public ArrayCopyCallNode(@InjectedNodeParameter ArrayCopyForeignCalls foreignCalls, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode src, ValueNode srcPos, ValueNode dest,
                    ValueNode destPos, ValueNode length, JavaKind elementKind,
                    LocationIdentity locationIdentity, LocationIdentity killedLocationIdentity, boolean aligned, boolean disjoint, boolean uninitialized, int heapWordSize) {
        super(TYPE, StampFactory.forVoid());
        assert elementKind != null;
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.elementKind = elementKind;
        this.locationIdentity = (locationIdentity != null ? locationIdentity : NamedLocationIdentity.getArrayLocation(elementKind));
        this.killedLocationIdentity = (killedLocationIdentity != null ? killedLocationIdentity : this.locationIdentity);
        this.aligned = aligned;
        this.disjoint = disjoint;
        this.uninitialized = uninitialized;
        this.foreignCalls = foreignCalls;
        this.wordJavaKind = wordTypes.getWordKind();
        this.heapWordSize = heapWordSize;

        assert !getKilledLocationIdentity().equals(LocationIdentity.any()) || this.elementKind.isObject();
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

    private ValueNode computeBase(LoweringTool tool, ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = graph().add(new GetObjectAddressNode(base));
        graph().addBeforeFixed(this, basePtr);
        Stamp wordStamp = StampFactory.forKind(wordJavaKind);
        ValueNode wordPos = IntegerConvertNode.convert(pos, wordStamp, graph(), NodeView.DEFAULT);
        int shift = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(elementKind));
        ValueNode scaledIndex = graph().unique(new LeftShiftNode(wordPos, ConstantNode.forInt(shift, graph())));
        ValueNode offset = graph().unique(new AddNode(scaledIndex, ConstantNode.forIntegerStamp(wordStamp, tool.getMetaAccess().getArrayBaseOffset(elementKind), graph())));
        return graph().unique(new OffsetAddressNode(basePtr, offset));
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            updateAlignedDisjoint(tool.getMetaAccess());
            ForeignCallDescriptor desc = foreignCalls.lookupArraycopyDescriptor(elementKind, isAligned(), isDisjoint(), isUninitialized(), killedLocationIdentity);
            assert desc != null : "no descriptor for arraycopy " + elementKind + ", aligned " + isAligned() + ", disjoint " + isDisjoint() + ", uninit " + isUninitialized() + ", killing " +
                            killedLocationIdentity;
            StructuredGraph graph = graph();
            ValueNode srcAddr = computeBase(tool, getSource(), getSourcePosition());
            ValueNode destAddr = computeBase(tool, getDestination(), getDestinationPosition());
            ValueNode len = getLength();
            if (len.stamp(NodeView.DEFAULT).getStackKind() != JavaKind.Long) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(JavaKind.Long), graph(), NodeView.DEFAULT);
            }
            ForeignCallNode call = graph.add(new ForeignCallNode(desc, srcAddr, destAddr, len));
            LocationIdentity[] callKills = call.getKilledLocationIdentities();
            assert callKills.length == 1 && callKills[0].equals(getKilledLocationIdentity()) : String.format("%s: copy of %s from %s should kill %s, unexpected kills: %s", call, elementKind,
                            getLocationIdentity(), getKilledLocationIdentity(), Arrays.toString(callKills));
            graph.replaceFixedWithFixed(this, call);
        }
    }

    @Override
    public MemoryKill getLastLocationAccess() {
        return lastLocationAccess;
    }

    @Override
    public void setLastLocationAccess(MemoryKill lla) {
        updateUsagesInterface(lastLocationAccess, lla);
        lastLocationAccess = lla;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return killedLocationIdentity;
    }

    @Override
    public boolean hasSideEffect() {
        return !killedLocationIdentity.isInit();
    }

    @NodeIntrinsic(hasSideEffect = true)
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter boolean aligned,
                    @ConstantNodeParameter boolean disjoint, @ConstantNodeParameter boolean uninitialized, @ConstantNodeParameter int heapWordSize);

    @NodeIntrinsic(hasSideEffect = true)
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind copyKind, @ConstantNodeParameter JavaKind srcKind,
                    @ConstantNodeParameter JavaKind destKind, @ConstantNodeParameter boolean aligned, @ConstantNodeParameter boolean disjoint, @ConstantNodeParameter boolean uninitialized,
                    @ConstantNodeParameter int heapWordSize);

    @NodeIntrinsic(hasSideEffect = true)
    private static native void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter LocationIdentity killedLocationIdentity, @ConstantNodeParameter boolean aligned,
                    @ConstantNodeParameter boolean disjoint, @ConstantNodeParameter boolean uninitialized, @ConstantNodeParameter int heapWordSize);

    public static void arraycopyObjectKillsAny(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, JavaKind.Object, LocationIdentity.any(), false, false, false, heapWordSize);
    }

    public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter LocationIdentity killedLocationIdentity,
                    @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, killedLocationIdentity, false, false, false, heapWordSize);
    }

    public static void disjointArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, true, false, heapWordSize);
    }

    public static void disjointArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter LocationIdentity killedLocationIdentity, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, killedLocationIdentity, false, true, false, heapWordSize);
    }

    /**
     * Type punned copy of {@code length} elements of kind {@code copyKind} from an array with
     * {@code srcKind} elements to an array with {@code destKind} elements.
     */
    public static void disjointArraycopyDifferentKinds(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind copyKind, @ConstantNodeParameter JavaKind srcKind,
                    @ConstantNodeParameter JavaKind destKind, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, copyKind, srcKind, destKind, false, true, false, heapWordSize);
    }

    public static void disjointArraycopyKillsInit(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind, @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, LocationIdentity.init(), false, true, false, heapWordSize);
    }

    public static void disjointUninitializedArraycopy(Object src, int srcPos, Object dest, int destPos, int length, @ConstantNodeParameter JavaKind elementKind,
                    @ConstantNodeParameter int heapWordSize) {
        arraycopy(src, srcPos, dest, destPos, length, elementKind, false, true, true, heapWordSize);
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

    boolean isHeapWordAligned(MetaAccessProvider metaAccess, JavaConstant value, JavaKind kind) {
        return (metaAccess.getArrayBaseOffset(kind) + (long) value.asInt() * metaAccess.getArrayIndexScale(kind)) % heapWordSize == 0;
    }

    public void updateAlignedDisjoint(MetaAccessProvider metaAccess) {
        JavaKind componentKind = elementKind;
        if (srcPos == destPos) {
            // Can treat as disjoint
            disjoint = true;
        }
        PrimitiveConstant constantSrc = (PrimitiveConstant) srcPos.stamp(NodeView.DEFAULT).asConstant();
        PrimitiveConstant constantDst = (PrimitiveConstant) destPos.stamp(NodeView.DEFAULT).asConstant();
        if (constantSrc != null && constantDst != null) {
            if (!aligned) {
                aligned = isHeapWordAligned(metaAccess, constantSrc, componentKind) && isHeapWordAligned(metaAccess, constantDst, componentKind);
            }
            if (constantSrc.asInt() >= constantDst.asInt()) {
                // low to high copy (copying forwards) so treat as disjoint
                disjoint = true;
            }
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getLength().isConstant() && getLength().asConstant().isDefaultForKind()) {
            if (lastLocationAccess != null) {
                replaceAtUsages(lastLocationAccess.asNode(), InputType.Memory);
            }
            return null;
        }
        return this;
    }
}
