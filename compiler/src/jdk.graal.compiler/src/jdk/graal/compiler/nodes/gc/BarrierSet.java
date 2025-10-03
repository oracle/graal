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

import static jdk.graal.compiler.nodes.GraphState.StageFlag.LOW_TIER_BARRIER_ADDITION;
import static jdk.graal.compiler.nodes.GraphState.StageFlag.MID_TIER_BARRIER_ADDITION;

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

public abstract class BarrierSet {

    /**
     * Checks if this barrier set has a write barrier. All barrier sets that do any work include a
     * write barrier. Only the special {@link NoBarrierSet} returns false since it performs no
     * barrier work at all.
     */
    public boolean hasWriteBarrier() {
        return true;
    }

    /**
     * Checks whether writing to {@link LocationIdentity#INIT_LOCATION} can be performed with an
     * intervening allocation.
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
    public boolean shouldAddBarriersInStage(GraphState.StageFlag stage) {
        assert stage == MID_TIER_BARRIER_ADDITION || stage == LOW_TIER_BARRIER_ADDITION : stage;
        /*
         * Most barrier sets should be added in mid-tier, some might also wish to add in low-tier
         * (e.g. Shenandoah GC).
         */
        return stage == MID_TIER_BARRIER_ADDITION;
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
