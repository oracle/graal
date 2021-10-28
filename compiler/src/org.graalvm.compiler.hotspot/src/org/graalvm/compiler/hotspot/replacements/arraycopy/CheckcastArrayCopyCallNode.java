/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.replacements.arraycopy;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.GetObjectAddressNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.IntegerConvertNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.memory.AbstractMemoryCheckpoint;
import org.graalvm.compiler.nodes.memory.SingleMemoryKill;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyCallNode;
import org.graalvm.compiler.replacements.arraycopy.ArrayCopyForeignCalls;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordTypes;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;

/**
 * Implements {@link System#arraycopy} via a {@linkplain ForeignCallNode stub call} that performs a
 * fast {@code CHECKCAST} check.
 *
 * The target of the call is queried via
 * {@link HotSpotHostForeignCallsProvider#lookupCheckcastArraycopyDescriptor}.
 *
 * Instead of throwing an {@link ArrayStoreException}, the stub is expected to return the number of
 * copied elements xor'd with {@code -1}. Users of this node are responsible for converting that
 * into the expected exception. A return value of {@code 0} indicates that the operation was
 * successful.
 *
 * @see GenericArrayCopyCallNode A generic {@link System#arraycopy} stub call node.
 * @see ArrayCopyCallNode A {@link System#arraycopy} stub call node that calls specialzied stubs
 *      based element type and memory properties.
 */
@NodeInfo(allowedUsageTypes = {InputType.Memory, InputType.Value}, cycles = CYCLES_UNKNOWN, sizeRationale = "depends on length", size = SIZE_UNKNOWN, cyclesRationale = "depends on length")
public final class CheckcastArrayCopyCallNode extends AbstractMemoryCheckpoint implements Lowerable, SingleMemoryKill {

    public static final NodeClass<CheckcastArrayCopyCallNode> TYPE = NodeClass.create(CheckcastArrayCopyCallNode.class);

    private final HotSpotHostForeignCallsProvider foreignCalls;
    private final JavaKind wordKind;

    @Input ValueNode src;
    @Input ValueNode srcPos;
    @Input ValueNode dest;
    @Input ValueNode destPos;
    @Input ValueNode length;
    @Input ValueNode destElemKlass;
    @Input ValueNode superCheckOffset;

    protected final boolean uninit;

    protected CheckcastArrayCopyCallNode(@InjectedNodeParameter ArrayCopyForeignCalls foreignCalls, @InjectedNodeParameter WordTypes wordTypes,
                    ValueNode src, ValueNode srcPos, ValueNode dest,
                    ValueNode destPos, ValueNode length,
                    ValueNode superCheckOffset, ValueNode destElemKlass, boolean uninit) {
        super(TYPE, StampFactory.forKind(JavaKind.Int));
        this.foreignCalls = (HotSpotHostForeignCallsProvider) foreignCalls;
        this.wordKind = wordTypes.getWordKind();
        this.src = src;
        this.srcPos = srcPos;
        this.dest = dest;
        this.destPos = destPos;
        this.length = length;
        this.superCheckOffset = superCheckOffset;
        this.destElemKlass = destElemKlass;
        this.uninit = uninit;
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

    public boolean isUninit() {
        return uninit;
    }

    private ValueNode computeBase(LoweringTool tool, ValueNode base, ValueNode pos) {
        FixedWithNextNode basePtr = graph().add(new GetObjectAddressNode(base));
        graph().addBeforeFixed(this, basePtr);

        int shift = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(JavaKind.Object));
        ValueNode extendedPos = IntegerConvertNode.convert(pos, StampFactory.forKind(wordKind), graph(), NodeView.DEFAULT);
        ValueNode scaledIndex = graph().unique(new LeftShiftNode(extendedPos, ConstantNode.forInt(shift, graph())));
        ValueNode offset = graph().unique(
                        new AddNode(scaledIndex,
                                        ConstantNode.forIntegerBits(PrimitiveStamp.getBits(scaledIndex.stamp(NodeView.DEFAULT)), tool.getMetaAccess().getArrayBaseOffset(JavaKind.Object), graph())));
        return graph().unique(new OffsetAddressNode(basePtr, offset));
    }

    @Override
    public void lower(LoweringTool tool) {
        if (graph().getGuardsStage().areFrameStatesAtDeopts()) {
            ForeignCallDescriptor desc = foreignCalls.lookupCheckcastArraycopyDescriptor(isUninit());
            StructuredGraph graph = graph();
            ValueNode srcAddr = computeBase(tool, getSource(), getSourcePosition());
            ValueNode destAddr = computeBase(tool, getDestination(), getDestinationPosition());
            ValueNode len = getLength();
            if (len.stamp(NodeView.DEFAULT).getStackKind() != wordKind) {
                len = IntegerConvertNode.convert(len, StampFactory.forKind(wordKind), graph(), NodeView.DEFAULT);
            }
            ForeignCallNode call = graph.add(new ForeignCallNode(desc, srcAddr, destAddr, len, superCheckOffset, destElemKlass));
            call.setStateAfter(stateAfter());
            graph.replaceFixedWithFixed(this, call);
        }
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        /*
         * Because of restrictions that the memory graph of snippets matches the original node,
         * pretend that we kill any.
         */
        return LocationIdentity.any();
    }

    @NodeIntrinsic
    public static native int checkcastArraycopy(Object src, int srcPos, Object dest, int destPos, int length, Word superCheckOffset, Object destElemKlass, @ConstantNodeParameter boolean uninit);
}
