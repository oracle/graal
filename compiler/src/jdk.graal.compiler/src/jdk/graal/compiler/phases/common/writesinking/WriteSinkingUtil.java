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

package jdk.graal.compiler.phases.common.writesinking;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.nodes.util.GraphUtil;

import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheData;
import jdk.graal.compiler.phases.common.writesinking.data.MovableWriteState.CacheEntry;
import jdk.graal.compiler.phases.common.writesinking.WriteSinkingDiagnostics.WriteRejection;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import org.graalvm.word.LocationIdentity;

import java.lang.reflect.Modifier;

/**
 * Shared legality checks for the write-sinking analysis and graph-rewrite phases.
 */
public final class WriteSinkingUtil {
    private WriteSinkingUtil() {
    }

    /**
     * Returns {@code true} when {@code write} can replace the movable write represented by
     * {@code data} without changing barrier semantics or value type compatibility.
     */
    public static boolean canOverrideOldData(WriteNode write, CacheData data) {
        return data.barrier == write.getBarrierType() && areValuesReplaceable(write.value(), data.value);
    }

    /**
     * Returns {@code true} when {@code write} has an address and field/array shape that write
     * sinking can track exactly.
     */
    public static boolean canDeferWrite(ValueNode base, ValueNode offset, WriteNode write, MetaAccessProvider metaAccess) {
        return getWriteRejectionReason(base, offset, write, metaAccess) == null;
    }

    /**
     * Returns the reason {@code write} is not structurally eligible for write sinking, or
     * {@code null} when the write can be tracked exactly.
     */
    public static WriteRejection getWriteRejectionReason(ValueNode base, ValueNode offset, WriteNode write, MetaAccessProvider metaAccess) {
        /*-
         * This method checks that a given write is absolutely safe to sink, and we are extremely
         * conservative with respect to this.
         * There are plans in the future to extend what is considered safe to sink.
         *
         * Notably, This excludes:
         * - Arbitrary writes to non-field locations (includes raw pointers)
         * - Writes to field locations with wrong access kind
         */
        ResolvedJavaType type = getBaseType(base, metaAccess);
        DebugContext debug = base.getDebug();
        if (type == null) {
            debug.log(DebugContext.VERBOSE_LEVEL, "Write \"%s\" is not deferrable because the base has no Java type!", write);
            return WriteRejection.UNKNOWN_JAVA_TYPE;
        }
        if (!offset.isConstant()) {
            debug.log(DebugContext.VERBOSE_LEVEL, "Write \"%s\" is not deferrable due to variable offset!", write);
            return WriteRejection.NON_CONSTANT_OFFSET;
        }
        long constantOffset = offset.asJavaConstant().asLong();
        LocationIdentity locationIdentity = write.getLocationIdentity();
        if (requiresWriteBarrier(write)) {
            debug.log(DebugContext.VERBOSE_LEVEL, "Write \"%s\" is not deferrable because it requires a write barrier!", write);
            return WriteRejection.REQUIRES_WRITE_BARRIER;
        }
        if (type.isArray()) {
            return null;
        }

        ResolvedJavaField field;
        if (locationIdentity instanceof FieldLocationIdentity fieldLocationIdentity && Modifier.isStatic(fieldLocationIdentity.getField().getModifiers())) {
            // Lookup static fields directly via write location
            field = fieldLocationIdentity.getField();
        } else {
            // Instance fields use indirect approach based on the base type
            field = type.findInstanceFieldWithOffset(constantOffset, null);
        }
        if (field == null) {
            debug.log(DebugContext.VERBOSE_LEVEL, "Write \"%s\" is not deferrable because no field resolves at offset %d!", write, constantOffset);
            if (locationIdentity instanceof FieldLocationIdentity) {
                return WriteRejection.FIELD_IDENTITY_OFFSET_MISMATCH;
            } else if (locationIdentity.isInit()) {
                return WriteRejection.INIT_LOCATION_WITHOUT_FIELD;
            } else if (locationIdentity instanceof NamedLocationIdentity) {
                return WriteRejection.NAMED_LOCATION_WITHOUT_FIELD;
            }
            return WriteRejection.RAW_OBJECT_OFFSET_WITHOUT_FIELD;
        }

        if (Modifier.isVolatile(field.getModifiers())) {
            debug.log(DebugContext.VERBOSE_LEVEL, "Write \"%s\" is not deferrable due to being a write to a volatile field!", write);
            return WriteRejection.VOLATILE;
        }

        /*
         * Field exists, and we write the same kind. Note that this prevents sinking a double
         * over two ints.
         */
        if (field.getJavaKind().getStackKind() != write.value().getStackKind()) {
            debug.log(DebugContext.VERBOSE_LEVEL, "Write \"%s\" is not deferrable due to field kind mismatch!", write);
            return WriteRejection.FIELD_KIND_MISMATCH;
        }
        return null;
    }

    /**
     * Returns {@code true} when deferred writes must be materialized before {@code node}.
     */
    public static boolean isForcedCommitPoint(Node node) {
        return node instanceof ControlSinkNode ||
                        // We do not support deferred writes in frame states yet (GR-20478).
                        (node instanceof NodeWithState nodeWithState && nodeWithState.states().isNotEmpty());
    }

    /**
     * Builds the write-sinking cache key for a fixed write.
     */
    public static CacheEntry getEntryFromWrite(WriteNode write) {
        return new MovableWriteState.CacheEntry((AddressNode) GraphUtil.unproxify(write.getAddress()), write.getLocationIdentity());
    }

    /**
     * Returns {@code true} when two values can be merged as alternatives for the same sunk write.
     */
    public static boolean canMergeValues(ValueNode value, ValueNode otherValue) {
        return value.stamp(NodeView.DEFAULT).isCompatible(otherValue.stamp(NodeView.DEFAULT));
    }

    private static boolean areValuesReplaceable(ValueNode originalValue, ValueNode replacementValue) {
        return originalValue.stamp(NodeView.DEFAULT).isCompatible(replacementValue.stamp(NodeView.DEFAULT));
    }

    /**
     * Returns whether moving {@code write} would also have to preserve barriered store behavior.
     * Write sinking only handles writes whose lowered write metadata already says that no write
     * barrier is required.
     */
    private static boolean requiresWriteBarrier(WriteNode write) {
        return write.getBarrierType() != BarrierType.NONE;
    }

    private static ResolvedJavaType getBaseType(ValueNode base, MetaAccessProvider metaAccess) {
        ResolvedJavaType type;
        try {
            type = base.stamp(NodeView.DEFAULT).javaType(metaAccess);
        } catch (GraalError e) {
            if (e.getMessage().contains("no Java type")) {
                // Static storage or raw pointer.
                type = null;
            } else {
                throw e;
            }
        }
        return type;
    }

    /**
     * Returns {@code true} when {@code identifier} is excluded by the debug field filter.
     */
    public static boolean isFieldExcluded(MethodFilter excludeFieldsFilter, LocationIdentity identifier) {
        if (excludeFieldsFilter == null) {
            return false;
        }

        if (identifier instanceof FieldLocationIdentity fieldLocationIdentity) {
            ResolvedJavaField field = fieldLocationIdentity.getField();
            String declaringClass = field.getDeclaringClass().toJavaName();
            return excludeFieldsFilter.matches(declaringClass, field.getName(), null) ||
                            excludeFieldsFilter.matches(declaringClass.substring(declaringClass.lastIndexOf('.') + 1), field.getName(), null);
        }

        String locationString = identifier.toString();
        int fieldSeparator = locationString.lastIndexOf('.');
        if (fieldSeparator > 0 && fieldSeparator < locationString.length() - 1) {
            return excludeFieldsFilter.matches(locationString.substring(0, fieldSeparator), locationString.substring(fieldSeparator + 1), null);
        }

        return false;
    }
}
