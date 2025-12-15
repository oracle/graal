/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
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

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

/**
 * Abstract base class for emitting garbage collection barriers.
 * <p>
 * A {@code BarrierSet} defines methods to determine when and what kind of read, write, or
 * read-write barriers should be inserted during memory accesses in the generated code. This
 * includes barriers for field and array accesses, raw memory operations, and object allocations.
 * <p>
 * Subclasses are expected to provide barrier logic specific to the garbage collector being
 * supported, such as adding necessary barriers to nodes, determining the required type of barrier
 * for various memory operations, and verifying correctness of barrier placement within compilation
 * graph structures.
 * <p>
 * This abstraction supports integration with GC strategies that require barriers at different
 * compilation stages (mid-tier or low-tier), deferred barriers for specific allocation scenarios,
 * and customization for pre-write and post-allocation initialization write behaviors.
 * <p>
 *
 * @see jdk.graal.compiler.core.common.memory.BarrierType
 * @see jdk.graal.compiler.nodes.memory.FixedAccessNode
 * @see jdk.graal.compiler.nodes.java.AbstractNewObjectNode
 * @see jdk.graal.compiler.nodes.extended.ArrayRangeWrite
 */

public abstract class BarrierSet {

    /**
     * The stage at which barriers should be inserted. May be {@code null} if no barriers are
     * required, {@link jdk.graal.compiler.nodes.GraphState.StageFlag#MID_TIER_BARRIER_ADDITION} or
     * {@link jdk.graal.compiler.nodes.GraphState.StageFlag#LOW_TIER_BARRIER_ADDITION}.
     *
     * @see jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase
     */
    private final GraphState.StageFlag barrierStage;

    protected final boolean hasDeferredInitBarriers;

    protected BarrierSet(GraphState.StageFlag barrierStage, boolean hasDeferredInitBarriers) {
        assert barrierStage == null || barrierStage == GraphState.StageFlag.MID_TIER_BARRIER_ADDITION || barrierStage == GraphState.StageFlag.LOW_TIER_BARRIER_ADDITION : barrierStage;
        this.barrierStage = barrierStage;
        this.hasDeferredInitBarriers = hasDeferredInitBarriers;
    }

    /**
     * Return {@code true} if the last allocated object can elide all barriers.
     */
    public boolean hasDeferredInitBarriers() {
        return hasDeferredInitBarriers;
    }

    /**
     * Returns the barrier type to use when writing to {@link LocationIdentity#INIT_LOCATION} after
     * an intervening allocation.
     */
    public BarrierType postAllocationInitBarrier(BarrierType original) {
        return original;
    }

    /**
     * Adds necessary barriers to the given {@link FixedAccessNode} based on the type of memory
     * access it represents. The barriers are added according to the specific requirements of the
     * barrier set implementation.
     *
     * @param n the {@link FixedAccessNode} to which barriers should be added
     * @param context the {@link CoreProviders} instance providing necessary information and
     *            services for adding barriers
     */
    public abstract void addBarriers(FixedAccessNode n, CoreProviders context);

    /**
     * Determines the type of read barrier required for a field read operation.
     *
     * @param field the field being read from
     * @param storageKind the kind of value being loaded from the field
     * @return the type of read barrier required, or {@link BarrierType#NONE} if no barrier is
     *         needed.
     */
    public BarrierType fieldReadBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        return BarrierType.NONE;
    }

    /**
     * Determines the type of write barrier required for a field write operation.
     *
     * @param field the field being written to
     * @param storageKind the kind of value being stored in the field
     * @return the type of write barrier required, or {@link BarrierType#NONE} if no barrier is
     *         needed.
     */
    public BarrierType fieldWriteBarrierType(ResolvedJavaField field, JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.FIELD : BarrierType.NONE;
    }

    /**
     * Determines the type of read barrier required for a memory access.
     *
     * @param location the location identity of the memory access
     * @param address the node representing the address being accessed
     * @param loadStamp the stamp of the value being loaded
     * @return the type of read barrier required, or {@link BarrierType#NONE} if no barrier is
     *         needed
     */
    public BarrierType readBarrierType(LocationIdentity location, ValueNode address, Stamp loadStamp) {
        return BarrierType.NONE;
    }

    /**
     * Determines the type of write barrier required for a memory access based on the location
     * identity.
     *
     * @param location the location identity of the memory access
     * @return the type of write barrier required, or {@link BarrierType#NONE} if no barrier is
     *         needed
     */
    public BarrierType writeBarrierType(LocationIdentity location) {
        return BarrierType.NONE;
    }

    /**
     * Determines the type of write barrier required for a raw store operation represented by the
     * given {@link RawStoreNode}.
     *
     * @param store the {@link RawStoreNode} representing the raw store operation
     * @return the type of write barrier required, or {@link BarrierType#NONE} if no barrier is
     *         needed
     */
    public abstract BarrierType writeBarrierType(RawStoreNode store);

    /**
     * Determines the type of write barrier required for an array write operation based on the kind
     * of value being stored in the array.
     *
     * @param storageKind the kind of value being stored in the array
     * @return the type of write barrier required, or {@link BarrierType#NONE} if no barrier is
     *         needed. If the {@code storageKind} is {@link JavaKind#Object}, returns
     *         {@link BarrierType#ARRAY}; otherwise, returns {@link BarrierType#NONE}.
     */
    public BarrierType arrayWriteBarrierType(JavaKind storageKind) {
        return storageKind == JavaKind.Object ? BarrierType.ARRAY : BarrierType.NONE;
    }

    /**
     * Determines the type of read-write barrier required for a given memory access operation.
     *
     * @param object the node representing the object being accessed
     * @param value the node representing the value being written or read
     * @return the type of read-write barrier required, or {@link BarrierType#NONE} if no barrier is
     *         needed
     */
    public abstract BarrierType readWriteBarrier(ValueNode object, ValueNode value);

    /**
     * Determine whether writes of the given {@code storageKind} may ever need a pre-write barrier.
     *
     * @param storageKind the kind of value being stored
     * @return {@code false} if no writes of {@code storageKind} ever need a pre-write barrier;
     *         {@code true} if writes of {@code storageKind} may need a pre-write barrier at least
     *         under certain circumstances.
     */
    public boolean mayNeedPreWriteBarrier(JavaKind storageKind) {
        return false;
    }

    /**
     * Perform verification of inserted or missing barriers.
     *
     * @param graph the graph to verify.
     */
    public void verifyBarriers(StructuredGraph graph) {
    }

    /**
     * Determines whether barriers should be added during the specified compilation stage.
     *
     * @param stage the compilation stage to check
     * @return {@code true} if barriers should be added during the specified stage, {@code false}
     *         otherwise
     */
    public final boolean shouldAddBarriersInStage(GraphState.StageFlag stage) {
        assert stage == GraphState.StageFlag.MID_TIER_BARRIER_ADDITION || stage == GraphState.StageFlag.LOW_TIER_BARRIER_ADDITION : stage;
        return stage == barrierStage;
    }

    /**
     * This is a helper for GCs that support automatic marking for the last allocated object. For
     * initializing writes, the last allocation executed by the Java Virtual Machine is guaranteed
     * to be automatically handled so it's safe to skip the barrier in the emitted code.
     */
    protected boolean isWriteToNewObject(FixedAccessNode node) {
        if (!node.getLocationIdentity().isInit()) {
            return false;
        }
        // This is only allowed for the last allocation in sequence
        ValueNode base = node.getAddress().getBase();
        if (base instanceof AbstractNewObjectNode) {
            Node pred = node.predecessor();
            while (pred != null) {
                if (pred == base) {
                    node.getDebug().log(DebugContext.INFO_LEVEL, "Deferred barrier for %s with base %s", node, base);
                    return true;
                }
                if (pred instanceof AbstractNewObjectNode) {
                    node.getDebug().log(DebugContext.INFO_LEVEL, "Disallowed deferred barrier for %s because %s was last allocation instead of %s", node, pred, base);
                    return false;
                }
                pred = pred.predecessor();
            }
        }
        node.getDebug().log(DebugContext.INFO_LEVEL, "Unable to find allocation for deferred barrier for %s with base %s", node, base);
        return false;
    }
}
