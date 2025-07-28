/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.phases;

import java.util.List;

import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.codegen.lowerer.CommitAllocationLowerer;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.LoweringPhase;
import jdk.vm.ci.meta.JavaKind;

/**
 * Replaces {@link CommitAllocationNode}s and {@link AllocatedObjectNode}s with a set of
 * {@link NewInstanceNode}s, {@link NewArrayNode}s, {@link StoreFieldNode}s, and
 * {@link StoreIndexedNode}s.
 * <p>
 * First, all virtual objects are materialized as a set of allocations. After that, we set field and
 * array values on those objects from {@link CommitAllocationNode#getValues()}.
 * <p>
 * Has to be a phase instead of running as part of {@link LoweringPhase} because we replace the
 * {@link CommitAllocationNode} with other {@link MemoryAccess} nodes that have non-overlapping
 * {@link LocationIdentity LocationIdentities}, which fails the {@link LoweringPhase}'s post
 * lowering verification.
 *
 * @see jdk.graal.compiler.replacements.DefaultJavaLoweringProvider
 *      DefaultJavaLoweringProvider#lowerCommitAllocation
 * @see CommitAllocationLowerer
 */
public class MaterializeAllocationsPhase extends BasePhase<CoreProviders> {
    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        for (CommitAllocationNode commitAllocation : graph.getNodes().filter(CommitAllocationNode.class)) {
            lowerCommitAllocationNode(commitAllocation, context);
        }
    }

    protected void lowerCommitAllocationNode(CommitAllocationNode commit, CoreProviders context) {
        AbstractNewObjectNode[] allocations = materializeAllocations(commit);
        storeValues(commit, allocations, context);
        FixedValueAnchorNode[] valueAnchors = anchorAllocations(commit, allocations);
        replaceUsagesWithAnchors(commit, valueAnchors);

        /*
         * At this point the CommitAllocation has been materialized, should no longer have any
         * usages, and can be removed.
         */
        commit.graph().removeFixed(commit);
    }

    /**
     * Materializes each virtual object in the given {@link CommitAllocationNode} with an object
     * allocation. The allocations are inserted before the commit node.
     */
    @SuppressWarnings("try")
    protected AbstractNewObjectNode[] materializeAllocations(CommitAllocationNode commit) {
        StructuredGraph graph = commit.graph();

        List<VirtualObjectNode> virtualObjects = commit.getVirtualObjects();
        int numObjects = virtualObjects.size();

        // Stores the new allocation node for each virtual object
        AbstractNewObjectNode[] allocations = new AbstractNewObjectNode[numObjects];

        // Create an allocation for each virtual object
        for (int objIndex = 0; objIndex < numObjects; objIndex++) {
            VirtualObjectNode virtual = virtualObjects.get(objIndex);
            try (DebugCloseable ignored = graph.withNodeSourcePosition(virtual)) {
                AbstractNewObjectNode newObject = graph.addOrUniqueWithInputs(switch (virtual) {
                    case VirtualInstanceNode ignored1 -> new NewInstanceNode(virtual.type(), true);
                    case VirtualArrayNode virtualArrayNode -> new NewArrayNode(virtualArrayNode.componentType(), ConstantNode.forInt(virtual.entryCount()), true);
                    default -> throw VMError.shouldNotReachHere("Unexpected VirtualNode: " + virtual);
                });

                graph.addBeforeFixed(commit, newObject);
                allocations[objIndex] = newObject;
            }
        }

        return allocations;
    }

    /**
     * Stores all values from {@link CommitAllocationNode#getValues()} into the allocated objects.
     * <p>
     * Stores of default values can be omitted since the allocated objects are filled with default
     * values.
     */
    @SuppressWarnings("try")
    protected void storeValues(CommitAllocationNode commit, AbstractNewObjectNode[] allocations, CoreProviders context) {
        StructuredGraph graph = commit.graph();
        FrameState frameState = GraphUtil.findLastFrameState(commit).duplicate();

        List<VirtualObjectNode> virtualObjects = commit.getVirtualObjects();
        int numObjects = virtualObjects.size();

        /*-
         * Record starting position of field values in CommitAllocations#values for each object.
         *
         * For object at objIndex the values at indices
         * [valuePositions[objIndex], valuePositions[objIndex] + virtualObjects[objIndex].entryCount)
         * are the field values for that object.
         */
        int[] valuePositions = new int[numObjects];
        for (int objIndex = 0, valuePos = 0; objIndex < numObjects; objIndex++) {
            valuePositions[objIndex] = valuePos;
            valuePos += virtualObjects.get(objIndex).entryCount();
        }

        // Populate all the fields with values from the CommitAllocationNode
        for (int objIndex = 0; objIndex < numObjects; objIndex++) {
            VirtualObjectNode virtual = virtualObjects.get(objIndex);

            try (DebugCloseable ignored = graph.withNodeSourcePosition(virtual)) {
                AbstractNewObjectNode newObject = allocations[objIndex];
                // Base index for values for this object
                int baseValuePos = valuePositions[objIndex];
                for (int i = 0; i < virtual.entryCount(); i++) {
                    ValueNode value = commit.getValues().get(baseValuePos + i);

                    /*
                     * If the value is another virtual object, that object is materialized in the
                     * same CommitAllocation and we can directly use the allocation node as the
                     * value.
                     */
                    if (value instanceof VirtualObjectNode) {
                        assert virtualObjects.contains(value) : "Virtual object " + value + " does not belong to " + commit;
                        value = allocations[virtualObjects.indexOf(value)];
                    }

                    /*
                     * Default constants don't need an explicit store because the object was
                     * allocated with default values
                     */
                    if (!value.isDefaultConstant()) {
                        JavaKind storageKind = virtual.entryKind(context.getMetaAccessExtensionProvider(), i);

                        var storeNode = graph.addOrUniqueWithInputs(switch (virtual) {
                            case VirtualInstanceNode virtualInstance -> new StoreFieldNode(newObject, virtualInstance.field(i), value);
                            case VirtualArrayNode ignored1 -> new StoreIndexedNode(newObject, ConstantNode.forInt(i), null, null, storageKind, value);
                            default -> throw VMError.shouldNotReachHere("Unexpected VirtualNode: " + virtual);
                        });
                        storeNode.setStateAfter(frameState);
                        graph.addBeforeFixed(commit, storeNode);
                    }
                }
            }
        }
    }

    /**
     * Anchors all allocations after {@code anchorPoint}, which should come after all the values are
     * stored in the allocated objects.
     * <p>
     * Usages of the allocated objects have to reference those anchors instead of the allocation
     * node itself, because only after all stores are done are the objects in the state guaranteed
     * by the commit allocation.
     */
    protected FixedValueAnchorNode[] anchorAllocations(FixedWithNextNode anchorPoint, AbstractNewObjectNode[] allocations) {
        StructuredGraph graph = anchorPoint.graph();
        FixedWithNextNode insertionPoint = anchorPoint;
        FixedValueAnchorNode[] valueAnchors = new FixedValueAnchorNode[allocations.length];

        for (int objIndex = 0; objIndex < allocations.length; objIndex++) {
            AbstractNewObjectNode allocation = allocations[objIndex];
            FixedValueAnchorNode anchor = graph.add(new FixedValueAnchorNode(allocation));
            valueAnchors[objIndex] = anchor;
            graph.addAfterFixed(insertionPoint, anchor);
            insertionPoint = anchor;
        }

        return valueAnchors;
    }

    /**
     * Replaces usages of the {@link CommitAllocationNode}.
     * <p>
     * The only usages should be {@link AllocatedObjectNode}. These are replaced with the
     * corresponding anchor.
     */
    protected void replaceUsagesWithAnchors(CommitAllocationNode commit, FixedValueAnchorNode[] anchors) {
        List<VirtualObjectNode> virtualObjects = commit.getVirtualObjects();

        for (Node usage : commit.usages().snapshot()) {
            if (usage instanceof AllocatedObjectNode addObject) {
                int index = virtualObjects.indexOf(addObject.getVirtualObject());
                addObject.replaceAtUsagesAndDelete(anchors[index]);
            } else {
                throw VMError.shouldNotReachHere("Found CommitAllocationNode with unexpected usage: " + usage);
            }
        }

        assert commit.hasNoUsages() : commit.usages().snapshot();
    }
}
