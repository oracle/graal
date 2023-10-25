/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AbstractCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.FieldLocationIdentity;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import org.graalvm.word.LocationIdentity;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Base class for the barrier set for ZGC. This provides the basica requirement that ever reads of
 * an object from the heap should have an associated read barrier. Read barriers for non-heap
 * locations like handles can be handled by subclasses.
 */
public class ZBarrierSet implements BarrierSet {

    private final ResolvedJavaField referentField;

    public ZBarrierSet(ResolvedJavaField referentField) {
        this.referentField = referentField;
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
        return BarrierType.NONE;
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
        return BarrierType.NONE;
    }

    @Override
    public BarrierType arrayWriteBarrierType(JavaKind storageKind) {
        return BarrierType.NONE;
    }

    @Override
    public BarrierType guessReadWriteBarrier(ValueNode object, ValueNode value) {
        if (value.stamp(NodeView.DEFAULT).isObjectStamp()) {
            return BarrierType.READ;
        }
        return BarrierType.NONE;
    }

    @Override
    public boolean hasWriteBarrier() {
        return false;
    }

    @Override
    public boolean hasReadBarrier() {
        return true;
    }

    @Override
    public void addBarriers(FixedAccessNode n) {
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
                GraalError.guarantee(write.getBarrierType() == BarrierType.NONE, "There are no write barriers with ZGC: %s", node);
            } else if (node instanceof ReadNode || node instanceof FloatingReadNode ||
                            node instanceof AbstractCompareAndSwapNode || node instanceof LoweredAtomicReadAndWriteNode) {
                LIRLowerableAccess read = (LIRLowerableAccess) node;
                Stamp stamp = read.getAccessStamp(NodeView.DEFAULT);
                if (!stamp.isObjectStamp()) {
                    GraalError.guarantee(read.getBarrierType() == BarrierType.NONE, "no barriers for primitive reads: %s", read);
                    continue;
                }

                BarrierType expectedBarrier = barrierForLocation(read.getBarrierType(), read.getLocationIdentity(), JavaKind.Object);
                if (expectedBarrier != null) {
                    GraalError.guarantee(expectedBarrier == read.getBarrierType(), "expected %s but found %s in %s", expectedBarrier, read.getBarrierType(), read);
                    continue;
                }

                ValueNode base = read.getAddress().getBase();
                if (!base.stamp(NodeView.DEFAULT).isObjectStamp()) {
                    GraalError.guarantee(read.getBarrierType() == BarrierType.NONE, "no barrier for non-heap read: %s", read);
                } else {
                    GraalError.guarantee(read.getBarrierType() == BarrierType.READ, "missing barriers for heap read: %s", read);
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
