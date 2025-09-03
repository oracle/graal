/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.gc;

import static jdk.graal.compiler.nodes.NamedLocationIdentity.OFF_HEAP_LOCATION;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AbstractCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Base class for the barrier set for generational ZGC. This provides the basic every that every
 * read or write of an object from the heap should have an associated read barrier. Barriers for
 * non-heap locations like handles can be handled by subclasses.
 */
public class ZBarrierSet implements BarrierSet {

    private final ResolvedJavaType objectArrayType;
    private final ResolvedJavaField referentField;

    public ZBarrierSet(ResolvedJavaType objectArrayType, ResolvedJavaField referentField) {
        this.referentField = referentField;
        this.objectArrayType = objectArrayType;
    }

    @Override
    public BarrierType postAllocationInitBarrier(BarrierType original) {
        assert original == BarrierType.FIELD || original == BarrierType.ARRAY : "only for write barriers: " + original;
        return BarrierType.POST_INIT_WRITE;
    }

    @Override
    public BarrierType readBarrierType(LocationIdentity location, ValueNode address, Stamp loadStamp) {
        if (location.equals(OFF_HEAP_LOCATION)) {
            // Off heap locations are never expected to contain objects
            assert !loadStamp.isObjectStamp() : location;
            return BarrierType.NONE;
        }

        if (loadStamp.isObjectStamp()) {
            if (address.stamp(NodeView.DEFAULT).isObjectStamp()) {
                // A read of an Object from an Object requires a barrier
                return BarrierType.READ;
            }

            if (address instanceof AddressNode) {
                AddressNode addr = (AddressNode) address;
                if (addr.getBase().stamp(NodeView.DEFAULT).isObjectStamp()) {
                    // A read of an Object from an Object requires a barrier
                    return BarrierType.READ;
                }
            }
            // Objects aren't expected to be read from non-heap locations.
            throw GraalError.shouldNotReachHere("Unexpected location type " + loadStamp);
        }

        boolean mustBeObject = false;
        if (location instanceof FieldLocationIdentity) {
            FieldLocationIdentity fieldLocationIdentity = (FieldLocationIdentity) location;
            mustBeObject = fieldLocationIdentity.getField().getJavaKind() == JavaKind.Object;
        }
        assert !mustBeObject : address;
        return BarrierType.NONE;
    }

    @Override
    public BarrierType writeBarrierType(RawStoreNode store) {
        return store.needsBarrier() ? readWriteBarrier(store.object(), store.value()) : BarrierType.NONE;
    }

    @Override
    public BarrierType fieldReadBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        if (storageKind == JavaKind.Object && field.equals(referentField)) {
            return BarrierType.REFERENCE_GET;
        }
        if (storageKind.isObject()) {
            return BarrierType.READ;
        }
        return BarrierType.NONE;
    }

    @Override
    public BarrierType fieldWriteBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.FIELD : BarrierType.NONE;
    }

    @Override
    public BarrierType arrayWriteBarrierType(JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.ARRAY : BarrierType.NONE;
    }

    @Override
    public BarrierType readWriteBarrier(ValueNode object, ValueNode value) {
        if (value.stamp(NodeView.DEFAULT).isObjectStamp()) {
            ResolvedJavaType type = StampTool.typeOrNull(object);
            if (type != null && type.isArray()) {
                return BarrierType.ARRAY;
            } else if (type == null || type.isAssignableFrom(objectArrayType)) {
                // Treat as an array
                return BarrierType.ARRAY;
            } else {
                return BarrierType.FIELD;
            }
        }
        return BarrierType.NONE;
    }

    @Override
    public boolean hasWriteBarrier() {
        return true;
    }

    @Override
    public boolean hasReadBarrier() {
        return true;
    }

    @Override
    public void addBarriers(FixedAccessNode n, CoreProviders context) {
    }

    @Override
    public boolean mayNeedPreWriteBarrier(JavaKind storageKind) {
        return false;
    }

    @Override
    public void verifyBarriers(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof WriteNode) {
                WriteNode write = (WriteNode) node;
                Stamp stamp = write.getAccessStamp(NodeView.DEFAULT);
                if (!stamp.isObjectStamp()) {
                    GraalError.guarantee(write.getBarrierType() == BarrierType.NONE, "no barriers for primitive writes: %s", write);
                }
            } else if (node instanceof ReadNode ||
                            node instanceof FloatingReadNode ||
                            node instanceof AbstractCompareAndSwapNode ||
                            node instanceof LoweredAtomicReadAndWriteNode) {
                LIRLowerableAccess read = (LIRLowerableAccess) node;
                Stamp stamp = read.getAccessStamp(NodeView.DEFAULT);
                BarrierType barrierType = read.getBarrierType();
                if (!stamp.isObjectStamp()) {
                    GraalError.guarantee(barrierType == BarrierType.NONE, "no barriers for primitive reads: %s", read);
                    continue;
                }

                BarrierType expectedBarrier = barrierForLocation(barrierType, read.getLocationIdentity(), JavaKind.Object);
                if (expectedBarrier != null) {
                    GraalError.guarantee(expectedBarrier == barrierType, "expected %s but found %s in %s", expectedBarrier, barrierType, read);
                    continue;
                }

                ValueNode base = read.getAddress().getBase();
                if (!base.stamp(NodeView.DEFAULT).isObjectStamp()) {
                    GraalError.guarantee(barrierType == BarrierType.NONE, "no barrier for non-heap read: %s", read);
                } else if (node instanceof AbstractCompareAndSwapNode || node instanceof LoweredAtomicReadAndWriteNode) {
                    GraalError.guarantee(barrierType == BarrierType.FIELD || barrierType == BarrierType.ARRAY, "missing barriers for heap read: %s", read);
                } else {
                    GraalError.guarantee(barrierType == BarrierType.READ, "missing barriers for heap read: %s", read);
                }
            } else if (node instanceof AddressableMemoryAccess) {
                AddressableMemoryAccess access = (AddressableMemoryAccess) node;
                if (access.getBarrierType() != BarrierType.NONE) {
                    throw new GraalError("Unexpected memory access with barrier : " + node);
                }
            }
        }
    }

    protected BarrierType barrierForLocation(BarrierType currentBarrier, LocationIdentity location, JavaKind storageKind) {
        if (location instanceof FieldLocationIdentity) {
            FieldLocationIdentity fieldLocationIdentity = (FieldLocationIdentity) location;
            BarrierType barrierType = fieldReadBarrierType(fieldLocationIdentity.getField(), storageKind);
            if (barrierType != currentBarrier && barrierType == BarrierType.REFERENCE_GET) {
                if (currentBarrier == BarrierType.WEAK_REFERS_TO || currentBarrier == BarrierType.PHANTOM_REFERS_TO) {
                    return currentBarrier;
                }
            }
            return barrierType;
        }
        if (location.equals(NamedLocationIdentity.getArrayLocation(JavaKind.Object))) {
            return BarrierType.READ;
        }
        return null;
    }

}
