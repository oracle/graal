/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import java.util.BitSet;

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractStaticInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.CloneTypeFlow;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.flow.MethodTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.graal.pointsto.typestate.MultiTypeState;
import com.oracle.graal.pointsto.typestate.SingleTypeState;
import com.oracle.graal.pointsto.typestate.TypeState;
import com.oracle.graal.pointsto.typestate.TypeStateUtils;
import com.oracle.graal.pointsto.typestore.ArrayElementsTypeStore;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;

import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;

public abstract class AnalysisPolicy {

    protected final OptionValues options;

    protected final boolean aliasArrayTypeFlows;
    protected final boolean relaxTypeFlowConstraints;
    protected final boolean removeSaturatedTypeFlows;
    protected final int typeFlowSaturationCutoff;

    public AnalysisPolicy(OptionValues options) {
        this.options = options;

        aliasArrayTypeFlows = PointstoOptions.AliasArrayTypeFlows.getValue(options);
        relaxTypeFlowConstraints = PointstoOptions.RelaxTypeFlowStateConstraints.getValue(options);
        removeSaturatedTypeFlows = PointstoOptions.RemoveSaturatedTypeFlows.getValue(options);
        typeFlowSaturationCutoff = PointstoOptions.TypeFlowSaturationCutoff.getValue(options);
    }

    public abstract boolean isContextSensitiveAnalysis();

    public boolean aliasArrayTypeFlows() {
        return aliasArrayTypeFlows;
    }

    public boolean relaxTypeFlowConstraints() {
        return relaxTypeFlowConstraints;
    }

    public boolean removeSaturatedTypeFlows() {
        return removeSaturatedTypeFlows;
    }

    public int typeFlowSaturationCutoff() {
        return typeFlowSaturationCutoff;
    }

    public abstract MethodTypeFlow createMethodTypeFlow(PointsToAnalysisMethod method);

    /**
     * Specifies if this policy models constants objects context sensitively, i.e., by creating a
     * different abstraction for each JavaConstant of the same type, and thus needs a constants
     * cache.
     */
    public abstract boolean needsConstantCache();

    /** In some analysis policies some objects can summarize others. */
    public abstract boolean isSummaryObject(AnalysisObject object);

    /** Check if merging is enabled. Used for assertions. */
    public abstract boolean isMergingEnabled();

    /** Note type state merge. */
    public abstract void noteMerge(PointsToAnalysis bb, TypeState t);

    /** Note analysis object state merge. */
    public abstract void noteMerge(PointsToAnalysis bb, AnalysisObject... a);

    /** Note analysis object state merge. */
    public abstract void noteMerge(PointsToAnalysis bb, AnalysisObject o);

    /** Specifies if an allocation site should be modeled context sensitively. */
    public abstract boolean isContextSensitiveAllocation(PointsToAnalysis bb, AnalysisType type, AnalysisContext allocationContext);

    /** Create a heap allocated object abstraction. */
    public abstract AnalysisObject createHeapObject(PointsToAnalysis bb, AnalysisType objectType, BytecodePosition allocationSite, AnalysisContext allocationContext);

    /** Create a constant object abstraction. */
    public abstract AnalysisObject createConstantObject(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType);

    /** Wrap a constant into a type state abstraction. */
    public abstract TypeState constantTypeState(PointsToAnalysis bb, JavaConstant constant, AnalysisType exactType);

    /** Create type state for dynamic new instance. */
    public abstract TypeState dynamicNewInstanceState(PointsToAnalysis bb, TypeState currentState, TypeState newState, BytecodePosition allocationSite, AnalysisContext allocationContext);

    /** Create type state for clone. */
    public abstract TypeState cloneState(PointsToAnalysis bb, TypeState currentState, TypeState inputState, BytecodePosition cloneSite, AnalysisContext allocationContext);

    /**
     * Link the elements of the cloned objects (array flows or field flows) to the elements of the
     * source objects.
     */
    public abstract void linkClonedObjects(PointsToAnalysis bb, TypeFlow<?> inputFlow, CloneTypeFlow cloneFlow, BytecodePosition source);

    public abstract FieldTypeStore createFieldTypeStore(PointsToAnalysis bb, AnalysisObject object, AnalysisField field, AnalysisUniverse universe);

    public abstract ArrayElementsTypeStore createArrayElementsTypeStore(AnalysisObject object, AnalysisUniverse universe);

    /** Provides implementation for the virtual invoke type flow. */
    public abstract AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn);

    /** Provides implementation for the special invoke type flow. */
    public abstract AbstractSpecialInvokeTypeFlow createSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn);

    /** Provides implementation for the static invoke type flow. */
    public abstract AbstractStaticInvokeTypeFlow createStaticInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, PointsToAnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn);

    public abstract MethodFlowsGraph staticRootMethodGraph(PointsToAnalysis bb, PointsToAnalysisMethod pointsToMethod);

    public abstract AnalysisContext allocationContext(PointsToAnalysis bb, MethodFlowsGraph callerGraph);

    public abstract TypeFlow<?> proxy(BytecodePosition source, TypeFlow<?> input);

    public abstract boolean addOriginalUse(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> use);

    public abstract boolean addOriginalObserver(PointsToAnalysis bb, TypeFlow<?> flow, TypeFlow<?> observer);

    public abstract void linkActualReturn(PointsToAnalysis bb, boolean isStatic, InvokeTypeFlow invoke);

    public abstract void registerAsImplementationInvoked(InvokeTypeFlow invoke, MethodFlowsGraph calleeFlows);

    @SuppressWarnings("unused")
    public int makeProperties(BigBang bb, AnalysisObject... objects) {
        /* The default analysis policy doesn't use properties. */
        return 0;
    }

    @SuppressWarnings("unused")
    public int makePropertiesForUnion(TypeState s1, TypeState s2) {
        /* The default analysis policy doesn't use properties. */
        return 0;
    }

    /**
     * Simplifies a type state by replacing all context sensitive objects with context insensitive
     * objects.
     */
    public abstract TypeState forContextInsensitiveTypeState(PointsToAnalysis bb, TypeState state);

    public abstract SingleTypeState singleTypeState(PointsToAnalysis bb, boolean canBeNull, int properties, AnalysisType type, AnalysisObject... objects);

    public abstract MultiTypeState multiTypeState(PointsToAnalysis bb, boolean canBeNull, int properties, BitSet typesBitSet, AnalysisObject... objects);

    public abstract TypeState doUnion(PointsToAnalysis bb, SingleTypeState s1, SingleTypeState s2);

    public abstract TypeState doUnion(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2);

    public abstract TypeState doUnion(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2);

    @SuppressWarnings("static-method")
    public final TypeState doIntersection(PointsToAnalysis bb, SingleTypeState s1, SingleTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";
        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();
        if (s1.exactType().equals(s2.exactType())) {
            /* The inputs have the same type, the result will be s1. */
            return s1.forCanBeNull(bb, resultCanBeNull);
        } else {
            /* The inputs have different types then the result is empty or null. */
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }
    }

    @SuppressWarnings("static-method")
    public final TypeState doIntersection(PointsToAnalysis bb, SingleTypeState s1, MultiTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";
        boolean resultCanBeNull = s1.canBeNull() && s2.canBeNull();
        if (s2.containsType(s1.exactType())) {
            return s1.forCanBeNull(bb, resultCanBeNull);
        } else {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        }
    }

    public abstract TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2);

    public abstract TypeState doIntersection(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2);

    @SuppressWarnings("static-method")
    public final TypeState doSubtraction(PointsToAnalysis bb, SingleTypeState s1, SingleTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s1.exactType().equals(s2.exactType())) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    @SuppressWarnings("static-method")
    public final TypeState doSubtraction(PointsToAnalysis bb, SingleTypeState s1, MultiTypeState s2) {
        assert !bb.extendedAsserts() || TypeStateUtils.isContextInsensitiveTypeState(bb, s2) : "Current implementation limitation.";
        boolean resultCanBeNull = s1.canBeNull() && !s2.canBeNull();
        if (s2.containsType(s1.exactType())) {
            return TypeState.forEmpty().forCanBeNull(bb, resultCanBeNull);
        } else {
            return s1.forCanBeNull(bb, resultCanBeNull);
        }
    }

    public abstract TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, SingleTypeState s2);

    public abstract TypeState doSubtraction(PointsToAnalysis bb, MultiTypeState s1, MultiTypeState s2);
}
