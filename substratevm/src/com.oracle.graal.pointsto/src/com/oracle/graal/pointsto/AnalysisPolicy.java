/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.AbstractSpecialInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.AbstractVirtualInvokeTypeFlow;
import com.oracle.graal.pointsto.flow.ActualReturnTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.flow.context.AnalysisContext;
import com.oracle.graal.pointsto.flow.context.AnalysisContextPolicy;
import com.oracle.graal.pointsto.flow.context.BytecodeLocation;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.typestate.TypeState;
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

    /** Provide an analysis context policy. */
    protected abstract AnalysisContextPolicy<? extends AnalysisContext> contextPolicy();

    @SuppressWarnings("unchecked")
    public AnalysisContextPolicy<AnalysisContext> getContextPolicy() {
        return (AnalysisContextPolicy<AnalysisContext>) contextPolicy();
    }

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
    public abstract void noteMerge(BigBang bb, TypeState t);

    /** Note analysis object state merge. */
    public abstract void noteMerge(BigBang bb, AnalysisObject... a);

    /** Note analysis object state merge. */
    public abstract void noteMerge(BigBang bb, AnalysisObject o);

    /** Specifies if an allocation site should be modeled context sensitively. */
    public abstract boolean isContextSensitiveAllocation(BigBang bb, AnalysisType type, AnalysisContext allocationContext);

    /** Create a heap allocated object abstraction. */
    public abstract AnalysisObject createHeapObject(BigBang bb, AnalysisType objectType, BytecodeLocation allocationSite, AnalysisContext allocationContext);

    /** Create a constant object abstraction. */
    public abstract AnalysisObject createConstantObject(BigBang bb, JavaConstant constant, AnalysisType exactType);

    /** Create an allocation site given the BCI and method. */
    public abstract BytecodeLocation createAllocationSite(BigBang bb, int bci, AnalysisMethod method);

    /**
     * Create the allocation site given a unique key and method. The BCI might be duplicated due to
     * Graal method substitutions and inlining. Then we use a unique object key.
     */
    public BytecodeLocation createAllocationSite(BigBang bb, Object key, AnalysisMethod method) {
        return createAllocationSite(bb, BytecodeLocation.keyToBci(key), method);
    }

    public abstract FieldTypeStore createFieldTypeStore(AnalysisObject object, AnalysisField field, AnalysisUniverse universe);

    public abstract ArrayElementsTypeStore createArrayElementsTypeStore(AnalysisObject object, AnalysisUniverse universe);

    /** Provides implementation for the virtual invoke type flow. */
    public abstract AbstractVirtualInvokeTypeFlow createVirtualInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location);

    /** Provides implementation for the virtual invoke type flow. */
    public abstract AbstractSpecialInvokeTypeFlow createSpecialInvokeTypeFlow(BytecodePosition invokeLocation, AnalysisType receiverType, AnalysisMethod targetMethod,
                    TypeFlow<?>[] actualParameters, ActualReturnTypeFlow actualReturn, BytecodeLocation location);

    @SuppressWarnings("unused")
    public int makePoperties(BigBang bb, AnalysisObject... objects) {
        /* The default analysis policy doesn't use properties. */
        return 0;
    }

    @SuppressWarnings("unused")
    public int makePopertiesForUnion(TypeState s1, TypeState s2) {
        /* The default analysis policy doesn't use properties. */
        return 0;
    }
}
