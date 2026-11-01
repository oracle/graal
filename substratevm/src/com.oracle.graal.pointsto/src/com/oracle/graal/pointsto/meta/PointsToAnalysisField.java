/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.pointsto.flow.TypeFlow;
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
    public void injectConservativeFieldValues() {
        if (getStorageKind().isObject()) {
            injectConservativeFieldValues(List.of(initialFlow.getDeclaredType()), true);
        } else {
            injectFieldValueBound();
        }
    }

    @Override
    public void injectConservativeFieldValues(List<AnalysisType> customBounds, boolean canBeNull) {
        assert getStorageKind().isObject();
        var bb = (PointsToAnalysis) getUniverse().getBigbang();
        var effectiveBound = initialFlow.getDeclaredType();
        if (canBeNull) {
            initialFlow.addState(bb, TypeState.forNull());
        }
        for (AnalysisType valueBound : customBounds) {
            assert !valueBound.isPrimitive() && !valueBound.isWordType() : valueBound;
            assert effectiveBound.isAssignableFrom(valueBound) : "Custom bound must be assignable to the effective field bound: field: " + this +
                            " | effective bound: " + effectiveBound + " | custom bound: " + valueBound;
            injectBoundValues(bb, valueBound, initialFlow, false);
            injectArrayContents(bb, valueBound);
        }
    }

    @Override
    public void injectFieldValueBound() {
        var bb = (PointsToAnalysis) getUniverse().getBigbang();
        if (!bb.isSupportedJavaKind(getStorageKind())) {
            /* There is no point in inserting state for a kind not tracked by the analysis. */
            return;
        }
        if (getStorageKind().isObject()) {
            injectBoundValues(bb, initialFlow.getDeclaredType(), initialFlow, true);
        } else {
            this.saturatePrimitiveField();
        }
    }

    private static void injectBoundValues(PointsToAnalysis bb, AnalysisType valueBound, TypeFlow<?> flow, boolean canBeNull) {
        if (bb.getExtensionPolicy().isTypeClosed(valueBound)) {
            // A closed bound has a complete assignable-type hierarchy.
            valueBound.getTypeFlow(bb, canBeNull).addUse(bb, flow);
        } else {
            // An open bound must include values whose runtime types are not yet known.
            bb.getPreSaturatedSourceTypeFlow().addUse(bb, flow);
        }
    }

    /**
     * Summarizes reference-array contents along the bound's declared dimensions, using Object[] for
     * a supertype such as Object, Cloneable, or Serializable. When array flow aliasing is disabled,
     * concrete arrays admitted by generic or covariant bounds require separate coverage (GR-80697).
     */
    private static void injectArrayContents(PointsToAnalysis bb, AnalysisType valueBound) {
        AnalysisType arrayType = valueBound.isArray() ? valueBound : bb.getObjectType().getArrayClass();
        if (!valueBound.isAssignableFrom(arrayType)) {
            return;
        }
        do {
            AnalysisType componentType = arrayType.getComponentType();
            if (componentType.isPrimitive() || componentType.isWordType()) {
                break;
            }
            var elements = arrayType.getContextInsensitiveAnalysisObject().getArrayElementsFlow(bb, true);
            injectBoundValues(bb, componentType, elements, true);
            // Each nested array dimension needs its component values.
            arrayType = componentType;
        } while (arrayType.isArray());
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
            var bb = (PointsToAnalysis) getUniverse().getBigbang();
            if (bb.analysisPolicy().useConservativeUnsafeAccess()) {
                /*
                 * Conservative unsafe stores have no value flow. An unsafe store in analyzed code
                 * can write an extension value into a field that extensions cannot write directly.
                 * An open value bound therefore requires saturation, independently of the field
                 * classification and the saturation cutoff, so ordinary reads account for unknown
                 * runtime types.
                 */
                injectConservativeFieldValues();
            }
            if (fieldType.getStorageKind().isPrimitive()) {
                /*
                 * Primitive type states are not propagated through unsafe loads/stores. Instead,
                 * both primitive fields that are unsafe written and all unsafe loads for primitives
                 * are pre-saturated.
                 */
                saturatePrimitiveField();
            }
            bb.forceUnsafeUpdate();
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
