/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.extended;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;

import java.nio.ByteOrder;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.StructuredGraph.StageFlag;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@NodeInfo(cycles = CYCLES_2, size = SIZE_1)
public abstract class UnsafeAccessNode extends FixedWithNextNode implements Canonicalizable {

    public static final NodeClass<UnsafeAccessNode> TYPE = NodeClass.create(UnsafeAccessNode.class);
    @Input ValueNode object;
    @Input ValueNode offset;
    protected final JavaKind accessKind;
    protected final LocationIdentity locationIdentity;
    protected final boolean forceLocation;

    public abstract boolean isVolatile();

    protected UnsafeAccessNode(NodeClass<? extends UnsafeAccessNode> c, Stamp stamp, ValueNode object, ValueNode offset, JavaKind accessKind, LocationIdentity locationIdentity,
                    boolean forceLocation) {
        super(c, stamp);
        this.forceLocation = forceLocation;
        assert accessKind != null;
        assert locationIdentity != null;
        this.object = object;
        this.offset = offset;
        this.accessKind = accessKind;
        this.locationIdentity = locationIdentity;
    }

    public LocationIdentity getLocationIdentity() {
        return locationIdentity;
    }

    public boolean isLocationForced() {
        return forceLocation;
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
    public Node canonical(CanonicalizerTool tool) {
        if (!isLocationForced()) {
            if (offset().isConstant()) {
                long constantOffset = offset().asJavaConstant().asLong();

                // Try to canonicalize to a field access.
                ResolvedJavaType receiverType = StampTool.typeOrNull(object());
                if (receiverType != null) {
                    ResolvedJavaField field = getStaticFieldUnsafeAccess(tool.getConstantReflection());
                    if (field == null) {
                        field = receiverType.findInstanceFieldWithOffset(constantOffset, accessKind());
                    }

                    // No need for checking that the receiver is non-null. The field access
                    // includes the null check and if a field is found, the offset is so small that
                    // this is never a valid access of an arbitrary address.
                    if ((field != null && field.getJavaKind() == this.accessKind() &&
                                    !field.isInternal() /* Ensure this is a true java field. */)) {
                        assert graph().isBeforeStage(StageFlag.FLOATING_READS) : "cannot add more precise memory location after floating read phase";
                        return cloneAsFieldAccess(graph().getAssumptions(), field, isVolatile());
                    }
                }
            }
            if (getLocationIdentity().isAny()) {
                // If we have a vague one, try to build a better location identity.
                ResolvedJavaType receiverType = StampTool.typeOrNull(object());
                if (receiverType != null && receiverType.isArray()) {
                    /*
                     * This code might assign a wrong location identity in case the offset is
                     * outside of the body of the array. This seems to be benign.
                     */
                    LocationIdentity identity = NamedLocationIdentity.getArrayLocation(receiverType.getComponentType().getJavaKind());
                    assert graph().isBeforeStage(StageFlag.FLOATING_READS) : "cannot add more precise memory location after floating read phase";
                    return cloneAsArrayAccess(offset(), identity, isVolatile());
                }
            }
        }

        return this;
    }

    protected ValueNode cloneAsFieldAccess(Assumptions assumptions, ResolvedJavaField field) {
        return cloneAsFieldAccess(assumptions, field, field.isVolatile());
    }

    protected abstract ValueNode cloneAsFieldAccess(Assumptions assumptions, ResolvedJavaField field, boolean volatileAccess);

    protected abstract ValueNode cloneAsArrayAccess(ValueNode location, LocationIdentity identity, boolean volatileAccess);

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

    private static ResolvedJavaField findStaticFieldWithOffset(ResolvedJavaType type, long offset, JavaKind expectedEntryKind) {
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
