/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.memory;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;
import static org.graalvm.compiler.nodes.NamedLocationIdentity.ARRAY_LENGTH_LOCATION;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.PrimitiveStamp;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.CanonicalizableLocation;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.ZeroExtendNode;
import org.graalvm.compiler.nodes.extended.GuardingNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.spi.Virtualizable;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Reads an {@linkplain FixedAccessNode accessed} value.
 */
@NodeInfo(nameTemplate = "Read#{p#location/s}", cycles = CYCLES_2, size = SIZE_1)
public class ReadNode extends FloatableAccessNode implements LIRLowerableAccess, Canonicalizable, Virtualizable, GuardingNode, OrderedMemoryAccess {

    public static final NodeClass<ReadNode> TYPE = NodeClass.create(ReadNode.class);

    public ReadNode(AddressNode address, LocationIdentity location, Stamp stamp, BarrierType barrierType) {
        this(TYPE, address, location, stamp, null, barrierType, false, null);
    }

    protected ReadNode(NodeClass<? extends ReadNode> c, AddressNode address, LocationIdentity location, Stamp stamp, GuardingNode guard, BarrierType barrierType, boolean nullCheck,
                    FrameState stateBefore) {
        super(c, address, location, stamp, guard, barrierType, nullCheck, stateBefore);
        this.lastLocationAccess = null;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        LIRKind readKind = gen.getLIRGeneratorTool().getLIRKind(getAccessStamp(NodeView.DEFAULT));
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitLoad(readKind, gen.operand(address), gen.state(this)));
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (tool.allUsagesAvailable() && hasNoUsages()) {
            // Read without usages or guard can be safely removed.
            return null;
        }
        if (!getUsedAsNullCheck()) {
            return canonicalizeRead(this, getAddress(), getLocationIdentity(), tool);
        } else {
            // if this read is a null check, then replacing it with the value is incorrect for
            // guard-type usages
            return this;
        }
    }

    @SuppressWarnings("try")
    @Override
    public FloatingAccessNode asFloatingNode() {
        try (DebugCloseable position = withNodeSourcePosition()) {
            return graph().unique(new FloatingReadNode(getAddress(), getLocationIdentity(), lastLocationAccess, stamp(NodeView.DEFAULT), getGuard(), getBarrierType()));
        }
    }

    @Override
    public boolean isAllowedUsageType(InputType type) {
        return (getUsedAsNullCheck() && type == InputType.Guard) ? true : super.isAllowedUsageType(type);
    }

    public static ValueNode canonicalizeRead(ValueNode read, AddressNode address, LocationIdentity locationIdentity, CanonicalizerTool tool) {
        if (!tool.canonicalizeReads()) {
            return read;
        }
        return canonicalizeRead(read, address, locationIdentity, tool, NodeView.from(tool));
    }

    public static ValueNode canonicalizeRead(ValueNode read, AddressNode address, LocationIdentity locationIdentity, CoreProviders tool, NodeView view) {
        if (address instanceof OffsetAddressNode) {
            OffsetAddressNode objAddress = (OffsetAddressNode) address;
            return canonicalizeRead(read, read.stamp(view), objAddress.getBase(), objAddress.getOffset(), locationIdentity, tool, view);
        }
        return read;
    }

    private static ValueNode canonicalizeRead(ValueNode read, Stamp accessStamp, ValueNode object, ValueNode offset, LocationIdentity locationIdentity, CoreProviders tool, NodeView view) {
        MetaAccessProvider metaAccess = tool.getMetaAccess();
        ConstantReflectionProvider constantReflection = tool.getConstantReflection();
        Stamp resultStamp = read.stamp(view);

        // Note: readConstant cannot be used to read the array length, so in order to avoid an
        // unnecessary CompilerToVM.readFieldValue call ending in an IllegalArgumentException,
        // check if we are reading the array length location first.
        if (locationIdentity.equals(ARRAY_LENGTH_LOCATION)) {
            ValueNode length = GraphUtil.arrayLength(object, ArrayLengthProvider.FindLengthMode.CANONICALIZE_READ, constantReflection);
            if (length != null) {
                assert length.stamp(view).isCompatible(accessStamp);
                return length;
            }
        } else {
            if (metaAccess != null && object.isConstant() && !object.isNullConstant() && offset.isConstant()) {
                long displacement = offset.asJavaConstant().asLong();
                int stableDimension = ((ConstantNode) object).getStableDimension();
                if (locationIdentity.isImmutable() || stableDimension > 0) {
                    Constant constant = resultStamp.readConstant(constantReflection.getMemoryAccessProvider(), object.asConstant(), displacement, accessStamp);
                    boolean isDefaultStable = locationIdentity.isImmutable() || ((ConstantNode) object).isDefaultStable();
                    if (constant != null && (isDefaultStable || !constant.isDefaultForKind())) {
                        return ConstantNode.forConstant(resultStamp, constant, Math.max(stableDimension - 1, 0), isDefaultStable, metaAccess);
                    }
                }
            }
        }
        if (locationIdentity instanceof CanonicalizableLocation) {
            CanonicalizableLocation canonicalize = (CanonicalizableLocation) locationIdentity;
            ValueNode result = canonicalize.canonicalizeRead(read, object, offset, tool);
            assert result != null && result.stamp(view).isCompatible(read.stamp(view));
            return result;
        }
        return read;
    }

    public static ValueNode canonicalizeRead(ValueNode read, CanonicalizerTool tool, JavaKind accessKind, ValueNode object, ValueNode offset, LocationIdentity locationIdentity) {
        if (!tool.canonicalizeReads()) {
            return read;
        }
        NodeView view = NodeView.from(tool);
        Stamp resultStamp = read.stamp(view);
        if (!resultStamp.isCompatible(StampFactory.forKind(accessKind))) {
            return read;
        }
        Stamp accessStamp = resultStamp;
        switch (accessKind) {
            case Boolean:
            case Byte:
                accessStamp = IntegerStamp.OPS.getNarrow().foldStamp(32, 8, accessStamp);
                break;
            case Char:
            case Short:
                accessStamp = IntegerStamp.OPS.getNarrow().foldStamp(32, 16, accessStamp);
                break;
        }
        ValueNode result = ReadNode.canonicalizeRead(read, accessStamp, object, offset, locationIdentity, tool, view);
        if (result.isJavaConstant() && accessKind == JavaKind.Char) {
            PrimitiveStamp primitiveStamp = (PrimitiveStamp) result.stamp(NodeView.DEFAULT);
            result = NarrowNode.create(result, primitiveStamp.getBits(), accessKind.getBitCount(), view);
            return ZeroExtendNode.create(result, primitiveStamp.getBits(), NodeView.DEFAULT);
        }
        return result;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        throw GraalError.shouldNotReachHere("unexpected ReadNode before PEA");
    }

    @Override
    public boolean canNullCheck() {
        return true;
    }

    @Override
    public Stamp getAccessStamp(NodeView view) {
        return stamp(view);
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return MemoryOrderMode.PLAIN;
    }
}
