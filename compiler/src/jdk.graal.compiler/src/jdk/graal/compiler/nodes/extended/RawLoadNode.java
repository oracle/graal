/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.extended;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.Virtualizable;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.replacements.DefaultJavaLoweringProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Load of a value from a location specified as an offset relative to an object. No null check is
 * performed before the load.
 */
@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public class RawLoadNode extends UnsafeAccessNode implements Lowerable, Virtualizable, Canonicalizable, SingleMemoryKill {
    public static final NodeClass<RawLoadNode> TYPE = NodeClass.create(RawLoadNode.class);

    /**
     * This constructor exists for node intrinsics that need a stamp based on {@code accessKind}.
     */
    public RawLoadNode(ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity) {
        this(object, offset, accessKind, locationIdentity, false, MemoryOrderMode.PLAIN);
    }

    public RawLoadNode(ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity, MemoryOrderMode memoryOrder) {
        this(object, offset, accessKind, locationIdentity, false, memoryOrder);
    }

    public RawLoadNode(ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity, boolean forceLocation, MemoryOrderMode memoryOrder) {
        super(TYPE, StampFactory.forKind(accessKind.getStackKind()), object, offset, accessKind, locationIdentity, forceLocation, memoryOrder);
    }

    /**
     * This constructor exists for node intrinsics that need a stamp based on the return type of the
     * {@link NodeIntrinsic} annotated method.
     */
    public RawLoadNode(@InjectedNodeParameter Stamp stamp, ValueNode object, ValueNode offset, LocationIdentity locationIdentity, JavaKind accessKind) {
        super(TYPE, stamp, object, offset, accessKind, locationIdentity, false, MemoryOrderMode.PLAIN);
    }

    static Stamp computeStampForArrayAccess(ValueNode object, JavaKind accessKind, Stamp oldStamp) {
        TypeReference type = StampTool.typeReferenceOrNull(object);
        // Loads from instances will generally be raised into a LoadFieldNode and end up with a
        // precise stamp but array accesses will not, so manually compute a better stamp from
        // the underlying object.
        if (accessKind.isObject() && type != null && type.getType().isArray() && type.getType().getComponentType().getJavaKind().isObject()) {
            TypeReference componentType = TypeReference.create(object.graph().getAssumptions(), type.getType().getComponentType());
            Stamp newStamp = StampFactory.object(componentType);
            // Don't allow the type to get worse
            return oldStamp == null ? newStamp : oldStamp.improveWith(newStamp);
        }
        if (oldStamp != null) {
            return oldStamp;
        } else {
            return StampFactory.forKind(accessKind);
        }
    }

    protected RawLoadNode(NodeClass<? extends RawLoadNode> c, ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity) {
        this(c, object, offset, accessKind, locationIdentity, false, MemoryOrderMode.PLAIN);
    }

    protected RawLoadNode(NodeClass<? extends RawLoadNode> c, ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity, boolean forceLocation) {
        this(c, object, offset, accessKind, locationIdentity, forceLocation, MemoryOrderMode.PLAIN);
    }

    protected RawLoadNode(NodeClass<? extends RawLoadNode> c, ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity, boolean forceLocation,
                    MemoryOrderMode memoryOrder) {
        super(c, computeStampForArrayAccess(object, accessKind, null), object, offset, accessKind, locationIdentity, forceLocation, memoryOrder);
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        if (ordersMemoryAccesses()) {
            return LocationIdentity.any();
        }
        return MemoryKill.NO_LOCATION;
    }

    @Override
    public boolean inferStamp() {
        // Primitive stamps can't get any better
        if (accessKind.isObject()) {
            return updateStamp(computeStampForArrayAccess(object, accessKind, stamp));
        }
        return false;
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            VirtualObjectNode virtual = (VirtualObjectNode) alias;
            ValueNode offsetValue = tool.getAlias(offset());
            if (offsetValue.isConstant()) {
                long off = offsetValue.asJavaConstant().asLong();
                int entryIndex = virtual.entryIndexForOffset(tool.getMetaAccess(), off, accessKind());

                if (entryIndex != -1) {
                    ValueNode entry = tool.getEntry(virtual, entryIndex);
                    JavaKind entryKind = virtual.entryKind(tool.getMetaAccessExtensionProvider(), entryIndex);

                    if (virtual.isVirtualByteArrayAccess(tool.getMetaAccessExtensionProvider(), accessKind())) {
                        if (virtual.canVirtualizeLargeByteArrayUnsafeRead(entry, entryIndex, accessKind(), tool)) {
                            tool.replaceWith(VirtualArrayNode.virtualizeByteArrayRead(entry, accessKind(), stamp));
                        }
                    } else if (entry.getStackKind() == getStackKind() || entryKind == accessKind()) {

                        if (!(entry.stamp(NodeView.DEFAULT).isCompatible(stamp(NodeView.DEFAULT)))) {
                            if (entry.stamp(NodeView.DEFAULT) instanceof PrimitiveStamp && stamp instanceof PrimitiveStamp) {
                                PrimitiveStamp p1 = (PrimitiveStamp) stamp;
                                PrimitiveStamp p2 = (PrimitiveStamp) entry.stamp(NodeView.DEFAULT);
                                int width1 = p1.getBits();
                                int width2 = p2.getBits();
                                if (width1 == width2) {
                                    Node replacement = ReinterpretNode.create(p2, entry, NodeView.DEFAULT);
                                    tool.replaceWith((ValueNode) replacement);
                                    return;
                                } else {
                                    // different bit width
                                    return;
                                }
                            } else {
                                // cannot reinterpret for arbitrary objects
                                return;
                            }
                        }
                        JavaKind kind = accessKind();
                        ValueNode replacement = tool.getAlias(entry);

                        if (kind.getStackKind() == JavaKind.Int) {
                            /*
                             * Get the value as it would be actually stored in memory or in other
                             * words only the bytes we are interested in. The type is defined by the
                             * access kind, e.g. for the access kind byte and the value 0xF0F0F0F0
                             * we actually want to have 0xF0.
                             */
                            ValueNode narrowed = DefaultJavaLoweringProvider.implicitPrimitiveStoreConvert(kind, replacement);
                            /*
                             * Expand the value to 32 bits again and perform boolean coercion if
                             * necessary.
                             */
                            replacement = DefaultJavaLoweringProvider.implicitUnsafePrimitiveLoadConvert(kind, narrowed);
                        }

                        tool.ensureAdded(replacement);
                        tool.replaceWith(replacement);
                    }
                }
            }
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        Node canonical = super.canonical(tool);
        if (canonical != this) {
            return canonical;
        }
        if (!isLocationForced()) {
            return ReadNode.canonicalizeRead(this, tool, accessKind, object, offset, locationIdentity);
        }
        return this;
    }

    @Override
    public ValueNode cloneAsFieldAccess(ResolvedJavaField field) {
        assert field.getJavaKind() == accessKind() : Assertions.errorMessageContext("field", field, "accessKind", accessKind);
        assert !field.isInternal() : field;
        assert graph().isBeforeStage(GraphState.StageFlag.FLOATING_READS) : "cannot add more precise memory location after floating read phase";
        return LoadFieldNode.create(graph().getAssumptions(), field.isStatic() ? null : object(), field, getMemoryOrder());
    }

    @Override
    protected ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity, MemoryOrderMode memoryOrder) {
        return new RawLoadNode(object(), location, accessKind(), identity, memoryOrder);
    }

    @NodeIntrinsic
    public static native Object load(Object object, long offset, @ConstantNodeParameter JavaKind kind, @ConstantNodeParameter LocationIdentity locationIdentity);

    @NodeIntrinsic
    public static native int loadInt(Object object, long offset, @ConstantNodeParameter JavaKind kind, @ConstantNodeParameter LocationIdentity locationIdentity);
}
