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
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.memory.FixedAccessNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;

public interface BarrierSet {

    boolean hasWriteBarrier();

    boolean hasReadBarrier();

    /**
     * Checks whether writing to {@link LocationIdentity#INIT_LOCATION} can be performed with an
     * intervening allocation.
     */
    default BarrierType postAllocationInitBarrier(BarrierType original) {
        return original;
    }

    void addBarriers(FixedAccessNode n, CoreProviders context);

    BarrierType fieldReadBarrierType(ResolvedJavaField field, JavaKind storageKind);

    BarrierType fieldWriteBarrierType(ResolvedJavaField field, JavaKind storageKind);

    BarrierType readBarrierType(LocationIdentity location, ValueNode address, Stamp loadStamp);

    /**
     * @param location
     */
    default BarrierType writeBarrierType(LocationIdentity location) {
        return BarrierType.NONE;
    }

    BarrierType writeBarrierType(RawStoreNode store);

    BarrierType arrayWriteBarrierType(JavaKind storageKind);

    BarrierType readWriteBarrier(ValueNode object, ValueNode value);

    /**
     * Determine whether writes of the given {@code storageKind} may ever need a pre-write barrier.
     *
     * @return {@code false} if no writes of {@code storageKind} ever need a pre-write barrier;
     *         {@code true} if writes of {@code storageKind} may need a pre-write barrier at least
     *         under certain circumstances.
     */
    boolean mayNeedPreWriteBarrier(JavaKind storageKind);

    /**
     * Perform verification of inserted or missing barriers.
     *
     * @param graph the grraph to verify.
     */
    default void verifyBarriers(StructuredGraph graph) {
    }

    default boolean shouldAddBarriersInStage(GraphState.StageFlag stage) {
        /*
         * Most barrier sets should be added in mid-tier, some might also wish to add in low-tier
         * (e.g. Shenandoah GC).
         */
        return stage == GraphState.StageFlag.MID_TIER_BARRIER_ADDITION;
    }

    /**
     * For initializing writes, the last allocation executed by the JVM is guaranteed to be
     * automatically card marked so it's safe to skip the card mark in the emitted code.
     */
    default boolean isWriteToNewObject(FixedAccessNode node) {
        if (!node.getLocationIdentity().isInit()) {
            return false;
        }
        // This is only allowed for the last allocation in sequence
        return isWriteToNewObject(node, node.getAddress().getBase());
    }

    default boolean isWriteToNewObject(FixedWithNextNode node, ValueNode base) {
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
