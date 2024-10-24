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

import static jdk.vm.ci.common.JVMCIError.shouldNotReachHere;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

public class FieldTypeFlow extends TypeFlow<AnalysisField> implements GlobalFlow {

    private static final AtomicReferenceFieldUpdater<FieldTypeFlow, FieldFilterTypeFlow> FILTER_FLOW_UPDATER = AtomicReferenceFieldUpdater.newUpdater(FieldTypeFlow.class, FieldFilterTypeFlow.class,
                    "filterFlow");

    /** The holder of the field flow (null for static fields). */
    private final AnalysisObject object;

    /** A filter flow used for unsafe writes. */
    private volatile FieldFilterTypeFlow filterFlow;

    public FieldTypeFlow(AnalysisField field, AnalysisType type) {
        this(field, type, null);
    }

    public FieldTypeFlow(AnalysisField field, AnalysisType type, AnalysisObject object) {
        super(field, filterUncheckedInterface(type), TypeState.forEmpty());
        this.object = object;
    }

    public AnalysisObject object() {
        return object;
    }

    @Override
    public TypeFlow<AnalysisField> copy(PointsToAnalysis bb, MethodFlowsGraph methodFlows) {
        // return this field flow
        throw shouldNotReachHere("The field flow should not be cloned. Use Load/StoreFieldTypeFlow.");
    }

    @Override
    public boolean canSaturate(PointsToAnalysis bb) {
        /* Fields declared with a closed type don't saturate, they track all input types. */
        return !bb.isClosed(declaredType);
    }

    @Override
    protected void onInputSaturated(PointsToAnalysis bb, TypeFlow<?> input) {
        if (bb.isClosed(declaredType)) {
            /*
             * When a field store is saturated conservatively assume that the field state can
             * contain any subtype of its declared type or any primitive value for primitive fields.
             */
            declaredType.getTypeFlow(bb, true).addUse(bb, this);
        } else {
            /* Propagate saturation stamp through the field flow. */
            super.onInputSaturated(bb, input);
        }
    }

    /** The filter flow is used for unsafe writes and initialized on demand. */
    public FieldFilterTypeFlow filterFlow(PointsToAnalysis bb) {
        assert source.isUnsafeAccessed() : "Filter flow requested for non unsafe accessed field.";

        if (filterFlow == null) {
            if (FILTER_FLOW_UPDATER.compareAndSet(this, null, new FieldFilterTypeFlow(source))) {
                /*
                 * The newly installed FieldFilterTypeFlow can be used by other threads before
                 * addUse() is called / done. This is not a problem. The filterFlow stores its own
                 * state and after the use is actually linked the state, if any, is transfered to
                 * the use.
                 */
                filterFlow.addUse(bb, this);
            }
        }
        return filterFlow;
    }

    @Override
    public String toString() {
        return "FieldFlow<" + source.format("%h.%n") + System.lineSeparator() + getStateDescription() + ">";
    }

}
