/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.util.concurrent.atomic.AtomicReference;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.StoreIndexedTypeFlow;
import com.oracle.graal.pointsto.flow.OffsetStoreTypeFlow.UnsafeStoreTypeFlow;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AtomicUtils;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class PointsToAnalysisType extends AnalysisType {

    /**
     * Unique, per type, context insensitive unsafe field store. This store has as a receiver the
     * flow of this type, i.e., it will link with all possible unsafe accessed field flows, in all
     * assignable subtypes.
     */
    private final AtomicReference<UnsafeStoreTypeFlow> contextInsensitiveUnsafeStore = new AtomicReference<>();
    /**
     * Unique, per type, context insensitive indexed store that can write to all array flows of this
     * type and its subtypes.
     */
    private final AtomicReference<StoreIndexedTypeFlow> contextInsensitiveIndexedStore = new AtomicReference<>();

    PointsToAnalysisType(AnalysisUniverse universe, ResolvedJavaType javaType, JavaKind storageKind, AnalysisType objectType, AnalysisType cloneableType) {
        super(universe, javaType, storageKind, objectType, cloneableType);
    }

    @Override
    public boolean registerAsUnsafeAllocated(Object reason) {
        boolean result = super.registerAsUnsafeAllocated(reason);
        if (result) {
            var bb = (PointsToAnalysis) universe.getBigbang();
            for (var f : getInstanceFields(true)) {
                var field = (AnalysisField) f;
                field.getInitialFlow().addState(bb, TypeState.defaultValueForKind(field.getStorageKind()));
            }
        }
        return result;
    }

    /**
     * @see AnalysisType#registerAsAssignable(BigBang)
     */
    @Override
    public void registerAsAssignable(BigBang bb) {
        TypeState typeState = TypeState.forType(((PointsToAnalysis) bb), this, true);
        /*
         * Register the assignable type with its super types. Skip this type, it can lead to a
         * deadlock when this is called when the type is created.
         */
        forAllSuperTypes(t -> t.addAssignableType(bb, typeState), false);
        /* Register the type as assignable to itself. */
        this.addAssignableType(bb, typeState);
    }

    public UnsafeStoreTypeFlow initAndGetContextInsensitiveUnsafeStore(PointsToAnalysis bb, BytecodePosition originalLocation) {
        return AtomicUtils.produceAndSetValue(contextInsensitiveUnsafeStore,
                        () -> createContextInsensitiveUnsafeStore(bb, originalLocation), (t) -> initContextInsensitiveUnsafeStore(bb, t));
    }

    /**
     * Create a unique, per type, context insensitive unsafe store that can store to all unsafe
     * accessed fields of this type, in the default partition.
     */
    private UnsafeStoreTypeFlow createContextInsensitiveUnsafeStore(PointsToAnalysis bb, BytecodePosition originalLocation) {
        /* The receiver object flow is the flow corresponding to this type. */
        var objectFlow = this.getTypeFlow(bb, false);
        /* Use the Object type as a conservative type for the values loaded. */
        AnalysisType componentType = bb.getObjectType();
        /*
         * The context insensitive store doesn't have a value flow, it will instead be linked with
         * the value flows at all the locations where it is swapped in.
         */
        UnsafeStoreTypeFlow store = new UnsafeStoreTypeFlow(originalLocation, this, componentType, objectFlow, null);
        store.markAsContextInsensitive();
        return store;
    }

    /**
     * Register the context insensitive unsafe store as an observer of its receiver type. This also
     * triggers an update of the context insensitive store.
     */
    private static void initContextInsensitiveUnsafeStore(PointsToAnalysis bb, UnsafeStoreTypeFlow store) {
        store.receiver().addObserver(bb, store);
    }

    public StoreIndexedTypeFlow initAndGetContextInsensitiveIndexedStore(PointsToAnalysis bb, BytecodePosition originalLocation) {
        return AtomicUtils.produceAndSetValue(contextInsensitiveIndexedStore,
                        () -> createContextInsensitiveIndexedStore(bb, originalLocation), (t) -> initContextInsensitiveIndexedStore(bb, t));
    }

    /**
     * Create a unique, per type, context insensitive indexed store that can write to all array
     * flows of this type and its subtypes.
     */
    private StoreIndexedTypeFlow createContextInsensitiveIndexedStore(PointsToAnalysis bb, BytecodePosition originalLocation) {
        assert this.isArray() : this;
        /* The receiver object flow is the flow corresponding to this type. */
        var objectFlow = this.getTypeFlow(bb, false);
        /*
         * The context insensitive store doesn't have a value flow, it will instead be linked with
         * the value flows at all the locations where it is swapped in.
         */
        StoreIndexedTypeFlow store = new StoreIndexedTypeFlow(originalLocation, this, objectFlow, null);
        store.markAsContextInsensitive();
        return store;
    }

    /**
     * Register the context insensitive indexed store as an observer of its receiver type. This also
     * triggers an update of the context insensitive store.
     */
    private static void initContextInsensitiveIndexedStore(PointsToAnalysis bb, StoreIndexedTypeFlow store) {
        store.receiver().addObserver(bb, store);
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        contextInsensitiveUnsafeStore.set(null);
        contextInsensitiveIndexedStore.set(null);
    }
}
