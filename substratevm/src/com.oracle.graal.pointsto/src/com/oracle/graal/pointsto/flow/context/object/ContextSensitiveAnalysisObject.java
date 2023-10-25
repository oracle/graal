/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.flow.context.object;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.TypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;

import jdk.vm.ci.code.BytecodePosition;

/**
 * This class models analysis objects that retain some information about their allocation. So here
 * context doesn't refer to calling context, but rather to the allocation context of this object
 * (which could contain information about the calling context of its allocator). More importantly,
 * this object abstraction is responsible four correct routing of field and array read and write
 * flows.
 */
public class ContextSensitiveAnalysisObject extends AnalysisObject {

    public ContextSensitiveAnalysisObject(AnalysisUniverse universe, AnalysisType type, AnalysisObjectKind kind) {
        super(universe, type, kind);
        assert universe.analysisPolicy().allocationSiteSensitiveHeap() : "policy mismatch";
    }

    /** The object has been in contact with an context insensitive object in an union operation. */
    @Override
    public void noteMerge(PointsToAnalysis bb) {
        assert bb.analysisPolicy().isMergingEnabled() : "policy mismatch";

        if (!merged) {
            super.noteMerge(bb);

            if (this.type.isArray()) {
                if (this.isObjectArray()) {
                    mergeArrayElementsFlow(bb);
                }
            } else {
                mergeInstanceFieldsFlows(bb);
            }
        }
    }

    private void mergeArrayElementsFlow(PointsToAnalysis bb) {
        assert this.isObjectArray() : this;

        ArrayElementsTypeFlow contextInsensitiveWriteArrayElementsFlow = type.getContextInsensitiveAnalysisObject().getArrayElementsFlow(bb, true);
        contextInsensitiveWriteArrayElementsFlow.addUse(bb, this.arrayElementsTypeStore.writeFlow());

        ArrayElementsTypeFlow contextInsensitiveReadArrayElementsFlow = type.getContextInsensitiveAnalysisObject().getArrayElementsFlow(bb, false);
        this.arrayElementsTypeStore.readFlow().addUse(bb, contextInsensitiveReadArrayElementsFlow);
    }

    private void mergeInstanceFieldsFlows(PointsToAnalysis bb) {
        mergeInstanceFieldsFlows(bb, type.getContextInsensitiveAnalysisObject());
    }

    public void mergeInstanceFieldsFlows(PointsToAnalysis bb, AnalysisObject object) {
        if (instanceFieldsTypeStore != null) {
            for (int i = 0; i < instanceFieldsTypeStore.length(); i++) {
                FieldTypeStore fieldTypeStore = instanceFieldsTypeStore.get(i);
                if (fieldTypeStore != null) {
                    mergeInstanceFieldFlow(bb, fieldTypeStore, object);
                }
            }
        }
    }

    /**
     * Merge the read and write flows of the fieldTypeStore with those of the context insensitive
     * object.
     */
    protected static void mergeInstanceFieldFlow(PointsToAnalysis bb, FieldTypeStore fieldTypeStore, AnalysisObject object) {
        AnalysisField field = fieldTypeStore.field();

        FieldTypeFlow readFieldFlow = fieldTypeStore.readFlow();
        FieldTypeFlow writeFieldFlow = fieldTypeStore.writeFlow();

        FieldTypeFlow parentWriteFieldFlow = object.getInstanceFieldFlow(bb, field, true);

        parentWriteFieldFlow.addUse(bb, writeFieldFlow);
        /*
         * Route the values from the field flow to the context-sensitive parent write field flow.
         * This will effectively merge this values with their context-insensitive version.
         */
        readFieldFlow.addUse(bb, parentWriteFieldFlow);
    }

    @Override
    public ArrayElementsTypeFlow getArrayElementsFlow(PointsToAnalysis bb, boolean isStore) {
        assert type.isArray() : type;
        assert bb.analysisPolicy().allocationSiteSensitiveHeap() : "policy mismatch";

        return isStore ? arrayElementsTypeStore.writeFlow() : arrayElementsTypeStore.readFlow();
    }

    /** Returns the filter field flow corresponding to an unsafe accessed field. */
    @Override
    public FieldFilterTypeFlow getInstanceFieldFilterFlow(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers()) && field.isUnsafeAccessed() && bb.analysisPolicy().allocationSiteSensitiveHeap() : field;

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, objectFlow, context, field);

        /*
         * If this object has already been merged all the other fields have been merged as well.
         * Merge the type flows for the newly accessed field too.
         */
        for (AnalysisObject mergedWith : getAllObjectsMergedWith()) {
            mergeInstanceFieldFlow(bb, fieldTypeStore, mergedWith);
        }

        return fieldTypeStore.writeFlow().filterFlow(bb);
    }

    @Override
    public FieldTypeFlow getInstanceFieldFlow(PointsToAnalysis bb, TypeFlow<?> objectFlow, BytecodePosition context, AnalysisField field, boolean isStore) {
        assert !Modifier.isStatic(field.getModifiers()) && bb.analysisPolicy().allocationSiteSensitiveHeap() : field;

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, objectFlow, context, field);

        /*
         * If this object has already been merged all the other fields have been merged as well.
         * Merge the type flows for the newly accessed field too.
         */
        for (AnalysisObject mergedWith : getAllObjectsMergedWith()) {
            mergeInstanceFieldFlow(bb, fieldTypeStore, mergedWith);
        }

        return isStore ? fieldTypeStore.writeFlow() : fieldTypeStore.readFlow();
    }

    @Override
    protected void linkFieldFlows(PointsToAnalysis bb, AnalysisField field, FieldTypeStore fieldStore) {
        // link the initial instance field flow to the field write flow
        field.getInitialInstanceFieldFlow().addUse(bb, fieldStore.writeFlow());
        // link the field read flow to the instance field flow
        fieldStore.readFlow().addUse(bb, field.getInstanceFieldFlow());
        // Also link the field read flow the field flow on the context insensitive object.
        // This ensures that the all values flowing into a context-sensitive field flow
        // are also visible from the context-insensitive field flow.
        // Note that the context-insensitive field flow strips down all context from incoming state,
        // so there is no risk that the context-sensitive objects will get flagged as merged.
        FieldTypeFlow parentReadFieldFlow = type.getContextInsensitiveAnalysisObject().getInstanceFieldFlow(bb, field, false);
        fieldStore.readFlow().addUse(bb, parentReadFieldFlow);
    }

    /**
     * This returns all the objects this object was ever merged with.
     */
    protected List<AnalysisObject> getAllObjectsMergedWith() {
        return merged ? Collections.singletonList(type().getContextInsensitiveAnalysisObject()) : Collections.emptyList();
    }
}
