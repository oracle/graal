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

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.code.BytecodePosition;

/**
 * Models a flow that introduces an instantiated type in the type flow graph. The type can originate
 * from a new-instance, new-array, new-multi-array, box, arrays-copy-of, etc.
 */
public class NewInstanceTypeFlow extends TypeFlow<BytecodePosition> {

    @SuppressWarnings("rawtypes") //
    private static final AtomicReferenceFieldUpdater<NewInstanceTypeFlow, ConcurrentMap> HEAP_OBJECTS_CACHE_UPDATER = //
                    AtomicReferenceFieldUpdater.newUpdater(NewInstanceTypeFlow.class, ConcurrentMap.class, "heapObjectsCache");

    /**
     * True iff the flow should insert default values into the fields of the instantiated type. Both
     * NewInstanceNode and CommitAllocationNode are represented as a NewInstanceTypeFlow, but we
     * want to insert the default values only for NewInstanceNode.
     */
    private final boolean insertDefaultFieldValues;

    /**
     * The original type flow keeps track of the heap objects created for the clones to avoid
     * duplication of heap object abstractions. The allocation context is derived from the allocator
     * context, i.e., the context of the method clone that holds this flow. There is only one
     * NewInstanceTypeFlow per context of a method, however, depending of the analysis policy,
     * multiple NewInstanceTypeFlows can generate objects with the same allocation context.
     */
    volatile ConcurrentMap<AnalysisContext, AnalysisObject> heapObjectsCache;

    public NewInstanceTypeFlow(BytecodePosition position, AnalysisType type, boolean insertDefaultFieldValues) {
        /* The actual type state is set lazily in initFlow(). */
        super(position, type, TypeState.forEmpty());
        this.insertDefaultFieldValues = insertDefaultFieldValues;
        assert source != null;
    }

    @Override
    protected void onFlowEnabled(PointsToAnalysis bb) {
        super.onFlowEnabled(bb);
        declaredType.registerAsInstantiated(source);
        if (insertDefaultFieldValues) {
            for (var f : declaredType.getInstanceFields(true)) {
                var field = (AnalysisField) f;
                field.getInitialFlow().addState(bb, TypeState.defaultValueForKind(bb, field.getStorageKind()));
            }
        }
    }

    @Override
    public void initFlow(PointsToAnalysis bb) {
        if (!isClone()) {
            /*
             * Inject state into graphs lazily, only after the type flow graph is pruned. When
             * context sensitivity is enabled the default graph is kept clean and used as a template
             * for clones. For clones the state is provided by createCloneState(), on creation.
             */
            addState(bb, TypeState.forExactType(bb, declaredType, false));
        }
    }

    @Override
    public boolean needsInitialization() {
        return true;
    }

    NewInstanceTypeFlow(PointsToAnalysis bb, NewInstanceTypeFlow original, MethodFlowsGraph methodFlows) {
        super(original, methodFlows, original.createCloneState(bb, methodFlows));
        this.insertDefaultFieldValues = original.insertDefaultFieldValues;
    }

    @Override
    public TypeFlow<BytecodePosition> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        return new NewInstanceTypeFlow(bb, this, methodFlows);
    }

    /** Create the type state for a clone. */
    TypeState createCloneState(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        AnalysisContext allocationContext = bb.analysisPolicy().allocationContext(bb, methodFlows);

        if (bb.analysisPolicy().isContextSensitiveAllocation(bb, declaredType, allocationContext)) {
            /*
             * If the analysis is context-sensitive create a new heap object for the new context, or
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
            return TypeState.forExactType(bb, declaredType, false);
        }

    }

    private AnalysisObject createHeapObject(PointsToAnalysis bb, AnalysisContext objContext) {
        if (heapObjectsCache == null) {
            /* Lazily initialize the cache. */
            HEAP_OBJECTS_CACHE_UPDATER.compareAndSet(this, null, new ConcurrentHashMap<>());
        }

        AnalysisObject result = heapObjectsCache.get(objContext);
        if (result == null) {
            AnalysisObject newValue = bb.analysisPolicy().createHeapObject(bb, declaredType, source, objContext);
            AnalysisObject oldValue = heapObjectsCache.putIfAbsent(objContext, newValue);
            result = oldValue != null ? oldValue : newValue;
        }
        return result;
    }

    @Override
    public String toString() {
        return "NewInstanceFlow<" + getStateDescription() + ">";
    }
}
