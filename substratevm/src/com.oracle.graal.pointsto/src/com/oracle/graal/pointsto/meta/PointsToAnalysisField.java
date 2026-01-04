/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.ContextInsensitiveFieldTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow;
import com.oracle.graal.pointsto.flow.StoreFieldTypeFlow.StoreInstanceFieldTypeFlow;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.util.AtomicUtils;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ResolvedJavaField;

public class PointsToAnalysisField extends AnalysisField {

    /**
     * Initial field type flow, i.e., as specified by the analysis client. It can be used to inject
     * specific types into a field that the analysis would not see on its own, and to inject the
     * null value into a field.
     */
    protected FieldTypeFlow initialFlow;
    /**
     * Field type flow that reflects all the types flowing in this field on its declaring type and
     * all the sub-types. It does not track any context-sensitive information.
     */
    protected FieldTypeFlow sinkFlow;

    /**
     * Unique, per field, context insensitive store. The context insensitive store has as a receiver
     * the field declaring class. Therefore, this store will link with all possible field flows, in
     * all assignable subtypes of the field declaring type.
     */
    private final AtomicReference<StoreInstanceFieldTypeFlow> contextInsensitiveStore = new AtomicReference<>();

    PointsToAnalysisField(AnalysisUniverse universe, ResolvedJavaField wrapped) {
        super(universe, wrapped);
        initialFlow = new FieldTypeFlow(this, getType());
        if (this.isStatic()) {
            /* There is never any context-sensitivity for static fields. */
            sinkFlow = initialFlow;
        } else {
            /*
             * Regardless of the context-sensitivity policy, there is always this single type flow
             * that accumulates all types.
             */
            sinkFlow = new ContextInsensitiveFieldTypeFlow(this, getType());
        }
    }

    public StoreFieldTypeFlow initAndGetContextInsensitiveStore(PointsToAnalysis bb, BytecodePosition originalLocation) {
        return AtomicUtils.produceAndSetValue(contextInsensitiveStore,
                        () -> createContextInsensitiveStore(bb, originalLocation), (t) -> initContextInsensitiveStore(bb, t));
    }

    /** Create an unique, per field, context insensitive store. */
    private StoreInstanceFieldTypeFlow createContextInsensitiveStore(PointsToAnalysis bb, BytecodePosition originalLocation) {
        /* The receiver object flow is the field declaring type flow. */
        var objectFlow = declaringClass.getTypeFlow(bb, false);
        /*
         * The context insensitive store doesn't have a value flow, it will instead be linked with
         * the value flows at all the locations where it is swapped in.
         */
        StoreInstanceFieldTypeFlow store = new StoreInstanceFieldTypeFlow(originalLocation, this, objectFlow);
        store.markAsContextInsensitive();
        store.enableFlow(bb);
        return store;
    }

    /**
     * Register the context insensitive store as an observer of its receiver type, i.e., the
     * declaring class of the field. This also triggers an update and links all field flows.
     */
    private static void initContextInsensitiveStore(PointsToAnalysis bb, StoreInstanceFieldTypeFlow store) {
        store.receiver().addObserver(bb, store);
    }

    public FieldTypeFlow getInitialFlow() {
        return initialFlow;
    }

    public FieldTypeFlow getSinkFlow() {
        return sinkFlow;
    }

    public FieldTypeFlow getStaticFieldFlow() {
        assert Modifier.isStatic(this.getModifiers()) : this;
        return sinkFlow;
    }

    @Override
    public void injectDeclaredType() {
        var bb = getUniverse().getBigbang();
        if (!bb.isSupportedJavaKind(getStorageKind())) {
            /*
             * If this JavaKind is not tracked by the analysis, there is no point in inserting any
             * state into the field.
             */
            return;
        }
        if (getStorageKind().isObject()) {
            bb.injectFieldTypes(this, List.of(this.getType()), true);
        } else {
            this.saturatePrimitiveField();
        }
    }

    @Override
    public void cleanupAfterAnalysis() {
        super.cleanupAfterAnalysis();
        contextInsensitiveStore.set(null);
        initialFlow = null;
        sinkFlow = null;
    }

    @Override
    public boolean registerAsUnsafeAccessed(Object reason) {
        if (super.registerAsUnsafeAccessed(reason)) {
            if (fieldType.getStorageKind().isPrimitive()) {
                /*
                 * Primitive type states are not propagated through unsafe loads/stores. Instead,
                 * both primitive fields that are unsafe written and all unsafe loads for primitives
                 * are pre-saturated.
                 */
                saturatePrimitiveField();
            }
            ((PointsToAnalysis) getUniverse().getBigbang()).forceUnsafeUpdate();
            return true;
        }
        return false;
    }

    private void saturatePrimitiveField() {
        assert fieldType.isPrimitive() || fieldType.isWordType() : this;
        var bb = ((PointsToAnalysis) getUniverse().getBigbang());
        initialFlow.addState(bb, TypeState.anyPrimitiveState());
        sinkFlow.addState(bb, TypeState.anyPrimitiveState());
    }
}
