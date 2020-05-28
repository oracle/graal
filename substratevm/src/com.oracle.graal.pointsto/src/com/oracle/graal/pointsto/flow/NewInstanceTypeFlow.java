/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

public class NewInstanceTypeFlow extends SourceTypeFlowBase {

    @SuppressWarnings("rawtypes") //
    private static final AtomicReferenceFieldUpdater<NewInstanceTypeFlow, ConcurrentMap> HEAP_OBJECTS_CACHE_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(NewInstanceTypeFlow.class, ConcurrentMap.class, "heapObjectsCache");

    /**
     * The original type flow keeps track of the heap objects created for the clones to avoid
     * duplication of heap object abstractions. The allocation context is derived from the allocator
     * context, i.e., the context of the method clone that holds this flow. There is only one
     * NewInstanceTypeFlow per context of a method, however, depending of the analysis policy,
     * multiple NewInstanceTypeFlows can generate objects with the same allocation context.
     */
    protected volatile ConcurrentMap<AnalysisContext, AnalysisObject> heapObjectsCache;

    /** Source flow has an immutable type state. */
    protected final AnalysisType type;
    protected final BytecodeLocation allocationSite;

    public NewInstanceTypeFlow(ValueNode node, AnalysisType type, BytecodeLocation allocationLabel) {
        this(node, type, allocationLabel, TypeState.forNull());
    }

    protected NewInstanceTypeFlow(ValueNode node, AnalysisType type, BytecodeLocation allocationLabel, TypeState typeState) {
        super(node, type, typeState);
        this.type = type;
        this.allocationSite = allocationLabel;
    }

    protected NewInstanceTypeFlow(BigBang bb, NewInstanceTypeFlow original, MethodFlowsGraph methodFlows) {
        super(bb, original, methodFlows, original.cloneSourceState(bb, methodFlows));

        this.type = original.type;
        this.allocationSite = original.allocationSite;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(BigBang bb, MethodFlowsGraph methodFlows) {
        return new NewInstanceTypeFlow(bb, this, methodFlows);
    }

    /** Create the type state for a clone. */
    protected TypeState cloneSourceState(BigBang bb, MethodFlowsGraph methodFlows) {
        assert !this.isClone();

        AnalysisContext allocatorContext = methodFlows.context();
        AnalysisContext allocationContext = bb.contextPolicy().allocationContext(allocatorContext, PointstoOptions.MaxHeapContextDepth.getValue(bb.getOptions()));

        if (bb.analysisPolicy().isContextSensitiveAllocation(bb, type, allocationContext)) {
            /*
             * If the analysis is context sensitive create a new heap object for the new context, or
             * return an existing one. The original NewInstanceTypeFlow is the one that stores the
             * (Context->HeapObject) mapping.
             */
            AnalysisObject newHeapObject = createHeapObject(bb, allocationContext);
            return TypeState.forNonNullObject(bb, newHeapObject);
        } else {
            /*
             * In the heap insensitive case instead of more precise heap abstractions (i.e.
             * allocation site) we use just the type of the object wrapped into the AbstractObject
             * base class. There is no cloning in this case.
             */
            return TypeState.forExactType(bb, type, false);
        }

    }

    private AnalysisObject createHeapObject(BigBang bb, AnalysisContext objContext) {
        assert !this.isClone();

        if (heapObjectsCache == null) {
            /* Lazily initialize the cache. */
            HEAP_OBJECTS_CACHE_UPDATER.compareAndSet(this, null, new ConcurrentHashMap<>());
        }

        AnalysisObject result = heapObjectsCache.get(objContext);
        if (result == null) {
            AnalysisObject newValue = bb.analysisPolicy().createHeapObject(bb, type, allocationSite, objContext);
            AnalysisObject oldValue = heapObjectsCache.putIfAbsent(objContext, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        return result;
    }

    public BytecodeLocation allocationSite() {
        return allocationSite;
    }

    public AnalysisType type() {
        return type;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("NewInstanceFlow<").append(getState()).append(">");
        return str.toString();
    }
}
