/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.OrderedMemoryAccess;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.TrackedUnsafeAccess;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public abstract class UnsafeAccessNode extends FixedWithNextNode implements Canonicalizable, OrderedMemoryAccess, MemoryAccess, TrackedUnsafeAccess {

    public static final NodeClass<UnsafeAccessNode> TYPE = NodeClass.create(UnsafeAccessNode.class);
    @Input ValueNode object;
    @Input ValueNode offset;
    protected final JavaKind accessKind;
    protected final LocationIdentity locationIdentity;
    /** Whether the location identity of this node must not change. */
    protected final boolean forceLocation;
    private final MemoryOrderMode memoryOrder;

    protected UnsafeAccessNode(NodeClass<? extends UnsafeAccessNode> c, Stamp stamp, ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity,
                    boolean forceLocation, MemoryOrderMode memoryOrder) {
        super(c, stamp);
        this.forceLocation = forceLocation;
        assert accessKind != null;
        assert locationIdentity != null;
        this.object = object;
        this.offset = offset;
        this.accessKind = accessKind;
        this.locationIdentity = locationIdentity;
        this.memoryOrder = memoryOrder;
    }

    @Override
    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public boolean isLocationForced() {
        return forceLocation;
    }

    public boolean isCanonicalizable() {
        /*
         * When a node's location is forced, it cannot be improved.
         */
        return !isLocationForced();
    }

    public ValueNode object() {
        return object;
    }

    public ValueNode offset() {
        return offset;
    }

    public JavaKind accessKind() {
        return accessKind;
    }

    @Override
    public MemoryOrderMode getMemoryOrder() {
        return memoryOrder;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (isCanonicalizable()) {
            // Try to canonicalize to a field access.
            ResolvedJavaType receiverType = StampTool.typeOrNull(object(), tool.getMetaAccess());
            if (receiverType != null) {
                ResolvedJavaField field = null;
                if (offset().isConstant()) {
                    field = getStaticFieldUnsafeAccess(tool.getConstantReflection());
                    if (field == null) {
                        long constantOffset = offset().asJavaConstant().asLong();
                        field = receiverType.findInstanceFieldWithOffset(constantOffset, accessKind());
                    }
                } else if (offset() instanceof FieldOffsetProvider fieldOffsetProvider) {
                    field = fieldOffsetProvider.getField();
                }

                // No need for checking that the receiver is non-null. The field access
                // includes the null check and if a field is found, the offset is so small that
                // this is never a valid access of an arbitrary address.
                if ((field != null && field.getJavaKind() == this.accessKind() &&
                                !field.isInternal() /* Ensure this is a true java field. */)) {
                    return cloneAsFieldAccess(field);
                }
            }

            if (getLocationIdentity().isAny()) {
                // If we have a vague one, try to build a better location identity.
                if (receiverType != null && receiverType.isArray()) {
                    /*
                     * This code might assign a wrong location identity in case the offset is
                     * outside of the body of the array. This seems to be benign.
                     */
                    LocationIdentity identity = NamedLocationIdentity.getArrayLocation(receiverType.getComponentType().getJavaKind());
                    assert graph().isBeforeStage(StageFlag.FLOATING_READS) : "cannot add more precise memory location after floating read phase";
                    return cloneAsArrayAccess(offset(), identity, getMemoryOrder());
                }
            }
        }

        return this;
    }

    public abstract ValueNode cloneAsFieldAccess(ResolvedJavaField field);

    protected abstract ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity, MemoryOrderMode memOrder);

    /**
     * In this method we check if the unsafe access is to a static field. This is the case when
     * {@code object} is a constant of type {@link Class} (static field's declaring class) and
     * {@code offset} is a constant (HotSpot-specific field offset from the declaring class).
     *
     * @return the static field, if any, that this node is reading
     */
    private ResolvedJavaField getStaticFieldUnsafeAccess(ConstantReflectionProvider constantReflection) {
        if (!object().isJavaConstant() || !offset().isJavaConstant() ||
                        object().isNullConstant() || offset().isNullConstant()) {
            return null;
        }
        JavaConstant objectConstant = object().asJavaConstant();
        JavaConstant offsetConstant = offset().asJavaConstant();
        assert objectConstant != null && offsetConstant != null : "Verified by the check at the beginning.";
        ResolvedJavaType staticReceiverType = constantReflection.asJavaType(objectConstant);
        if (staticReceiverType == null) {
            // object is not of type Class so it is not a static field
            return null;
        }
        return findStaticFieldWithOffset(staticReceiverType, offsetConstant.asLong(), accessKind);
    }

    public static ResolvedJavaField findStaticFieldWithOffset(ResolvedJavaType type, long offset, JavaKind expectedEntryKind) {
        try {
            ResolvedJavaField[] declaredFields = type.getStaticFields();
            return findFieldWithOffset(offset, expectedEntryKind, declaredFields);
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * NOTE GR-18873: this is a copy-paste implementation derived from
     * {@code jdk.vm.ci.hotspot.HotSpotResolvedObjectTypeImpl#findStaticFieldWithOffset}.
     */
    private static ResolvedJavaField findFieldWithOffset(long offset, JavaKind expectedEntryKind, ResolvedJavaField[] declaredFields) {
        for (ResolvedJavaField field : declaredFields) {
            long resolvedFieldOffset = field.getOffset();
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN &&
                            expectedEntryKind.isPrimitive() &&
                            !expectedEntryKind.equals(JavaKind.Void) &&
                            field.getJavaKind().isPrimitive()) {
                resolvedFieldOffset += field.getJavaKind().getByteCount() -
                                Math.min(field.getJavaKind().getByteCount(), 4 + expectedEntryKind.getByteCount());
            }
            if (resolvedFieldOffset == offset) {
                return field;
            }
        }
        return null;
    }

}
