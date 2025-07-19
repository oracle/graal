/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.gc.shenandoah;

import static jdk.graal.compiler.nodes.NamedLocationIdentity.OFF_HEAP_LOCATION;

import jdk.graal.compiler.core.common.type.AbstractObjectStamp;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.extended.ArrayRangeWrite;
import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.type.NarrowOopStamp;
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
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Shenandoah barrier set implementation.
 *
 * This generates 3 kinds of barriers:
 *
 * <ul>
 * <li>Load-reference barriers after reference-loads. The purpose is to canonicalize references
 * during concurrent collection, where we might otherwise see both from- and to-space references to
 * the same object.</li>
 *
 * <li>SATB barriers before reference writes. Those support concurrent marking, similar to how this
 * is done in G1. Reference.get-barriers are a special form of this, to support (weak,soft,phantom-)
 * references.</li>
 *
 * <li>Card barriers, only needed for generational Shenandoah. Those are inserted after
 * reference-writes and dirty cards. Similar to their counterparts in Serial and Parallel GC.</li>
 * </ul>
 */
public class ShenandoahBarrierSet implements BarrierSet {

    private final ResolvedJavaType objectArrayType;
    private final ResolvedJavaField referentField;
    protected boolean useLoadRefBarrier;
    protected boolean useSATBBarrier;
    protected boolean useCASBarrier;
    protected boolean useCardBarrier;

    public ShenandoahBarrierSet(ResolvedJavaType objectArrayType, ResolvedJavaField referentField) {
        this.referentField = referentField;
        this.objectArrayType = objectArrayType;
        this.useLoadRefBarrier = true;
        this.useSATBBarrier = true;
        this.useCASBarrier = true;
        this.useCardBarrier = true;
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
            GraalError.guarantee(!loadStamp.isObjectStamp(), "off-heap location not expected to be object: %s", location);
            return BarrierType.NONE;
        }

        if (loadStamp.isObjectStamp()) {
            if (address.stamp(NodeView.DEFAULT).isObjectStamp()) {
                // A read of an Object from an Object requires a barrier
                return BarrierType.READ;
            }

            if (address instanceof AddressNode addr) {
                if (addr.getBase().stamp(NodeView.DEFAULT).isObjectStamp()) {
                    // A read of an Object from an Object requires a barrier
                    return BarrierType.READ;
                }
            }
            // Objects aren't expected to be read from non-heap locations.
            throw GraalError.shouldNotReachHere("Unexpected location type " + loadStamp);
        }

        GraalError.guarantee(!(location instanceof FieldLocationIdentity fieldLocationIdentity) || fieldLocationIdentity.getField().getJavaKind() != JavaKind.Object,
                        "must not be a reference location: %s", address);
        return BarrierType.NONE;
    }

    @Override
    public BarrierType writeBarrierType(RawStoreNode store) {
        if (store.object().isNullConstant()) {
            return BarrierType.NONE;
        }
        return store.needsBarrier() ? readWriteBarrier(store.object(), store.value()) : BarrierType.NONE;
    }

    @Override
    public BarrierType fieldReadBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        if (field.getJavaKind() == JavaKind.Object && field.equals(referentField)) {
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
        switch (n) {
            case ReadNode readNode -> addReadNodeBarriers(readNode);
            case WriteNode write -> addWriteBarriers(write, write.value(), null);
            case LoweredAtomicReadAndWriteNode atomic -> {
                if (useCASBarrier) {
                    addWriteBarriers(atomic, atomic.getNewValue(), null);
                    addReadNodeBarriers(atomic);
                }
            }
            case AbstractCompareAndSwapNode cmpSwap -> {
                if (useCASBarrier) {
                    addWriteBarriers(cmpSwap, cmpSwap.getNewValue(), cmpSwap.getExpectedValue());
                    if (cmpSwap instanceof ValueCompareAndSwapNode) {
                        addReadNodeBarriers(cmpSwap);
                    }
                }
            }
            case ArrayRangeWrite ignored -> GraalError.unimplemented("ArrayRangeWrite is not used");
            case null, default ->
                GraalError.guarantee(n.getBarrierType() == BarrierType.NONE, "missed a node that requires a GC barrier: %s", n.getClass());
        }
    }

    private void addWriteBarriers(FixedAccessNode node, ValueNode writtenValue, ValueNode expectedValue) {
        BarrierType barrierType = node.getBarrierType();
        switch (barrierType) {
            case NONE:
                // nothing to do
                break;
            case FIELD:
            case ARRAY:
            case UNKNOWN:
            case POST_INIT_WRITE:
            case AS_NO_KEEPALIVE_WRITE:
                if (isObjectValue(writtenValue)) {
                    StructuredGraph graph = node.graph();
                    boolean init = node.getLocationIdentity().isInit();
                    if (!init && barrierType != BarrierType.AS_NO_KEEPALIVE_WRITE && useSATBBarrier) {
                        // The pre barrier does nothing if the value being read is null, so it can
                        // be explicitly skipped when this is an initializing store.
                        // No keep-alive means no need for the pre-barrier.
                        addShenandoahSATBBarrier(node, node.getAddress(), expectedValue, graph);
                    }
                    if (!init && useCardBarrier && !StampTool.isPointerAlwaysNull(writtenValue)) {
                        graph.addAfterFixed(node, graph.add(new ShenandoahCardBarrierNode(node.getAddress())));
                    }
                }
                break;
            default:
                throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    private void addLoadReferenceBarrier(FixedAccessNode node, AddressNode address, BarrierType barrierType) {
        GraalError.guarantee(node != null, "input value must not be null");
        StructuredGraph graph = node.graph();
        boolean narrow = node.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp;
        ValueNode uncompressed = maybeUncompressReference(node, narrow);
        ShenandoahLoadRefBarrierNode lrb = graph.add(new ShenandoahLoadRefBarrierNode(uncompressed, address, barrierType, narrow));
        ValueNode compValue = maybeCompressReference(lrb, narrow);
        ValueNode newUsage = uncompressed != node ? uncompressed : lrb;
        node.replaceAtUsages(compValue, usage -> usage != newUsage);
    }

    private void addReadNodeBarriers(FixedAccessNode node) {

        BarrierType barrierType = node.getBarrierType();
        StructuredGraph graph = node.graph();
        switch (barrierType) {
            case NONE -> {
                // No barriers required.
            }
            case REFERENCE_GET -> {
                if (useLoadRefBarrier) {
                    addLoadReferenceBarrier(node, node.getAddress(), barrierType);
                }
                if (useSATBBarrier) {
                    boolean narrow = node.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp;
                    ShenandoahReferentFieldReadBarrierNode barrier = graph.add(new ShenandoahReferentFieldReadBarrierNode(node.getAddress(), maybeUncompressReference(node, narrow)));
                    graph.addAfterFixed(node, barrier);
                }
            }
            case WEAK_REFERS_TO, PHANTOM_REFERS_TO, READ, ARRAY, FIELD, UNKNOWN -> {
                if (useLoadRefBarrier) {
                    addLoadReferenceBarrier(node, node.getAddress(), barrierType);
                }
            }
            default -> throw new GraalError("unexpected barrier type: " + barrierType);
        }
    }

    protected ValueNode maybeUncompressReference(ValueNode value, @SuppressWarnings("unused") boolean narrow) {
        return value;
    }

    protected ValueNode maybeCompressReference(ValueNode value, @SuppressWarnings("unused") boolean narrow) {
        return value;
    }

    private void addShenandoahSATBBarrier(FixedAccessNode node, AddressNode address, ValueNode value, StructuredGraph graph) {
        boolean narrow = value != null && value.stamp(NodeView.DEFAULT) instanceof NarrowOopStamp;
        ShenandoahSATBBarrierNode preBarrier = graph.add(new ShenandoahSATBBarrierNode(address, maybeUncompressReference(value, narrow)));
        GraalError.guarantee(!node.getUsedAsNullCheck(), "trapping null checks are inserted after write barrier insertion: ", node);
        node.setStateBefore(null);
        graph.addBeforeFixed(node, preBarrier);
    }

    private static boolean isObjectValue(ValueNode value) {
        return value.stamp(NodeView.DEFAULT) instanceof AbstractObjectStamp;
    }

    @Override
    public boolean mayNeedPreWriteBarrier(JavaKind storageKind) {
        return false;
    }

    @Override
    public void verifyBarriers(StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof WriteNode write) {
                Stamp stamp = write.getAccessStamp(NodeView.DEFAULT);
                if (!stamp.isObjectStamp()) {
                    GraalError.guarantee(write.getBarrierType() == BarrierType.NONE, "no barriers for primitive writes: %s", write);
                }
            } else if (node instanceof ReadNode ||
                            node instanceof AbstractCompareAndSwapNode ||
                            node instanceof LoweredAtomicReadAndWriteNode) {
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
            } else if (node instanceof AddressableMemoryAccess access) {
                if (access.getBarrierType() != BarrierType.NONE) {
                    throw new GraalError("Unexpected memory access with barrier : " + node);
                }
            }
        }
    }

    protected BarrierType barrierForLocation(BarrierType currentBarrier, LocationIdentity location, JavaKind storageKind) {
        if (location instanceof FieldLocationIdentity fieldLocationIdentity) {
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

    @Override
    public boolean shouldAddBarriersInStage(GraphState.StageFlag stage) {
        return stage == GraphState.StageFlag.LOW_TIER_BARRIER_ADDITION;
    }
}
