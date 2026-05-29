/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.InputType.Memory;

import java.util.EnumSet;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.memory.AbstractMemoryCheckpoint;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.replacements.NodeStrideUtil;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Single-location variant of {@link ArrayCopyWithConversionsNode}. This node exists so intrinsified
 * copies with a precise destination location, such as {@code ArrayUtils.arraycopy(int[], ...)}, can
 * be expanded in high tier: lowering the multi-kill variant to plain single-kill writes is rejected
 * by high-tier memory-kill verification.
 */
@NodeInfo(allowedUsageTypes = {Memory}, cycles = NodeCycles.CYCLES_UNKNOWN, size = NodeSize.SIZE_128)
public final class ArrayCopyWithConversionsSingleKillNode extends AbstractMemoryCheckpoint implements Lowerable, MemoryAccess, SingleMemoryKill,
                IntrinsicMethodNodeInterface {

    public static final NodeClass<ArrayCopyWithConversionsSingleKillNode> TYPE = NodeClass.create(ArrayCopyWithConversionsSingleKillNode.class);

    private final Stride strideSrc;
    private final Stride strideDst;
    private final LocationIdentity locationIdentity;
    private final EnumSet<?> runtimeCheckedCPUFeatures;

    @Input protected ValueNode arraySrc;
    @Input protected ValueNode offsetSrc;
    @Input protected ValueNode arrayDst;
    @Input protected ValueNode offsetDst;
    @Input protected ValueNode length;
    @OptionalInput(Memory) MemoryKill lastLocationAccess;

    public ArrayCopyWithConversionsSingleKillNode(ValueNode arraySrc, ValueNode offsetSrc, ValueNode arrayDst, ValueNode offsetDst, ValueNode length,
                    Stride strideSrc,
                    Stride strideDst,
                    LocationIdentity locationIdentity) {
        this(arraySrc, offsetSrc, arrayDst, offsetDst, length, strideSrc, strideDst, locationIdentity, null);
    }

    public ArrayCopyWithConversionsSingleKillNode(ValueNode arraySrc, ValueNode offsetSrc, ValueNode arrayDst, ValueNode offsetDst, ValueNode length,
                    Stride strideSrc,
                    Stride strideDst,
                    LocationIdentity locationIdentity,
                    EnumSet<?> runtimeCheckedCPUFeatures) {
        super(TYPE, StampFactory.forKind(JavaKind.Void));
        GraalError.guarantee(strideSrc != null && strideDst != null, "single-kill arraycopy requires fixed strides");
        GraalError.guarantee(!locationIdentity.isAny(), "single-kill arraycopy requires a precise location identity");
        this.arraySrc = arraySrc;
        this.offsetSrc = offsetSrc;
        this.arrayDst = arrayDst;
        this.offsetDst = offsetDst;
        this.length = length;
        this.strideSrc = strideSrc;
        this.strideDst = strideDst;
        this.locationIdentity = locationIdentity;
        this.runtimeCheckedCPUFeatures = runtimeCheckedCPUFeatures;
    }

    @Override
    public void lower(LoweringTool tool) {
        if (!length.isJavaConstant()) {
            return;
        }
        int constantLength = length.asJavaConstant().asInt();
        int maxVectorSizeBytes = ArrayCopyWithConversionsNode.maxVectorSizeBytes(tool);
        if (!ArrayCopyWithConversionsNode.canLowerConstantLengthCopy(maxVectorSizeBytes, strideSrc, strideDst, false, constantLength)) {
            return;
        }

        int byteLength = constantLength << strideDst.log2;
        int chunkSize = Integer.highestOneBit(Math.min(byteLength, maxVectorSizeBytes));

        ValueNode nonNullArraySrc = createNullCheckedValue(arraySrc, tool);
        ValueNode nonNullArrayDst = createNullCheckedValue(arrayDst, tool);
        WriteNode lastStore = ArrayCopyWithConversionsNode.lowerToReadWriteCopy(this, nonNullArraySrc, offsetSrc, nonNullArrayDst, offsetDst,
                        ArrayCopyWithConversionsNode.chunkOffsets(byteLength, chunkSize),
                        ArrayCopyWithConversionsNode.accessStamp(chunkSize), locationIdentity, locationIdentity, stateAfter());
        replaceAtUsages(lastStore, Memory);
        graph().removeFixed(this);
    }

    private ValueNode createNullCheckedValue(ValueNode object, LoweringTool tool) {
        if (StampTool.isPointerNonNull(object)) {
            return object;
        }
        GuardingNode nullCheck = tool.createGuard(this, graph().unique(IsNullNode.create(object)), DeoptimizationReason.NullCheckException,
                        DeoptimizationAction.InvalidateReprofile, SpeculationLog.NO_SPECULATION, true, null);
        return graph().addOrUnique(PiNode.create(object, StampFactory.objectNonNull(), (ValueNode) nullCheck));
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return locationIdentity;
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
    public ForeignCallDescriptor getForeignCallDescriptor() {
        return ArrayCopyWithConversionsForeignCalls.STUBS[NodeStrideUtil.getDirectStubCallIndex(null, strideSrc, strideDst)];
    }

    @Override
    public ValueNode[] getForeignCallArguments() {
        return new ValueNode[]{arraySrc, offsetSrc, arrayDst, offsetDst, length};
    }

    @Override
    public void emitIntrinsic(NodeLIRBuilderTool gen) {
        gen.getLIRGeneratorTool().emitArrayCopyWithConversion(
                        strideSrc,
                        strideDst,
                        runtimeCheckedCPUFeatures,
                        gen.operand(arraySrc),
                        gen.operand(offsetSrc),
                        gen.operand(arrayDst),
                        gen.operand(offsetDst),
                        gen.operand(length));
    }
}
