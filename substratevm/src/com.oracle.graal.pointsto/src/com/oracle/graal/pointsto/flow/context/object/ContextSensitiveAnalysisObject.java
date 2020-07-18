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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.FieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.typestore.FieldTypeStore;
import com.oracle.graal.pointsto.typestore.UnifiedFieldTypeStore;

public class ContextSensitiveAnalysisObject extends AnalysisObject {

    private List<AnalysisObject> referencedObjects;

    public ContextSensitiveAnalysisObject(AnalysisUniverse universe, AnalysisType type, AnalysisObjectKind kind) {
        super(universe, type, kind);
        assert PointstoOptions.AllocationSiteSensitiveHeap.getValue(universe.hostVM().options());
    }

    /** The object has been in contact with an context insensitive object in an union operation. */
    @Override
    public void noteMerge(BigBang bb) {
        assert bb.analysisPolicy().isMergingEnabled();

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

    private void mergeArrayElementsFlow(BigBang bb) {
        assert this.isObjectArray();

        ArrayElementsTypeFlow contextInsensitiveWriteArrayElementsFlow = type.getContextInsensitiveAnalysisObject().getArrayElementsFlow(bb, true);
        contextInsensitiveWriteArrayElementsFlow.addUse(bb, this.arrayElementsTypeStore.writeFlow());

        ArrayElementsTypeFlow contextInsensitiveReadArrayElementsFlow = type.getContextInsensitiveAnalysisObject().getArrayElementsFlow(bb, false);
        this.arrayElementsTypeStore.readFlow().addUse(bb, contextInsensitiveReadArrayElementsFlow);
    }

    private void mergeInstanceFieldsFlows(BigBang bb) {
        mergeInstanceFieldsFlows(bb, type.getContextInsensitiveAnalysisObject());
    }

    public void mergeInstanceFieldsFlows(BigBang bb, AnalysisObject object) {
        if (instanceFieldsTypeStore != null) {
            for (int i = 0; i < instanceFieldsTypeStore.length(); i++) {
                FieldTypeStore fieldTypeStore = instanceFieldsTypeStore.get(i);
                if (fieldTypeStore != null) {
                    mergeInstanceFieldFlow(bb, fieldTypeStore, object);
                }
            }
        }
    }

    protected void mergeInstanceFieldFlow(BigBang bb, FieldTypeStore fieldTypeStore) {
        mergeInstanceFieldFlow(bb, fieldTypeStore, type.getContextInsensitiveAnalysisObject());
    }

    /**
     * Merge the read and write flows of the fieldTypeStore with those of the context insensitive
     * object.
     */
    protected static void mergeInstanceFieldFlow(BigBang bb, FieldTypeStore fieldTypeStore, AnalysisObject object) {
        AnalysisField field = fieldTypeStore.field();

        FieldTypeFlow readFieldFlow = fieldTypeStore.readFlow();
        FieldTypeFlow writeFieldFlow = fieldTypeStore.writeFlow();

        FieldTypeFlow parentWriteFieldFlow = object.getInstanceFieldFlow(bb, field, true);
        FieldTypeFlow parentReadFieldFlow = object.getInstanceFieldFlow(bb, field, false);

        parentWriteFieldFlow.addUse(bb, writeFieldFlow);
        readFieldFlow.addUse(bb, parentReadFieldFlow);
    }

    @Override
    public ArrayElementsTypeFlow getArrayElementsFlow(BigBang bb, boolean isStore) {
        assert type.isArray();
        assert PointstoOptions.AllocationSiteSensitiveHeap.getValue(bb.getOptions());

        return isStore ? arrayElementsTypeStore.writeFlow() : arrayElementsTypeStore.readFlow();
    }

    /** Returns the filter field flow corresponding to an unsafe accessed field. */
    @Override
    public FieldFilterTypeFlow getInstanceFieldFilterFlow(BigBang bb, AnalysisMethod context, AnalysisField field) {
        assert !Modifier.isStatic(field.getModifiers()) && field.isUnsafeAccessed() && PointstoOptions.AllocationSiteSensitiveHeap.getValue(bb.getOptions());

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, context, field);

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
    public FieldTypeFlow getInstanceFieldFlow(BigBang bb, AnalysisMethod context, AnalysisField field, boolean isStore) {
        assert !Modifier.isStatic(field.getModifiers()) && PointstoOptions.AllocationSiteSensitiveHeap.getValue(bb.getOptions());

        FieldTypeStore fieldTypeStore = getInstanceFieldTypeStore(bb, context, field);

        /*
         * If this object has already been merged all the other fields have been merged as well.
         * Merge the type flows for the newly accessed field too.
         */
        for (AnalysisObject mergedWith : getAllObjectsMergedWith()) {
            mergeInstanceFieldFlow(bb, fieldTypeStore, mergedWith);
        }

        return isStore ? fieldTypeStore.writeFlow() : fieldTypeStore.readFlow();
    }

    /**
     * This returns all the objects this object was ever merged with.
     */
    protected List<AnalysisObject> getAllObjectsMergedWith() {
        return merged ? Collections.singletonList(type().getContextInsensitiveAnalysisObject()) : Collections.emptyList();
    }

    /**
     * Returns the list of referenced objects, i.e., field objects or array elements discovered by
     * the static analysis.
     *
     * Since this list is not updated during the analysis, for complete results this should only be
     * called when the base analysis has finished.
     */
    public List<AnalysisObject> getReferencedObjects() {

        if (referencedObjects == null) {

            // TODO do we need to materialize the objects in a HashSet here, or could we just
            // iterate over them?

            HashSet<AnalysisObject> objectsSet = new HashSet<>();
            if (this.type().isArray()) {
                for (AnalysisObject object : arrayElementsTypeStore.readFlow().getState().objects()) {
                    objectsSet.add(object);
                }
            } else {
                if (instanceFieldsTypeStore != null) {
                    for (int i = 0; i < instanceFieldsTypeStore.length(); i++) {
                        FieldTypeStore fieldTypeStore = instanceFieldsTypeStore.get(i);
                        if (fieldTypeStore != null) {
                            FieldTypeFlow fieldFlow = ((UnifiedFieldTypeStore) fieldTypeStore).readWriteFlow();
                            if (!fieldFlow.getState().isUnknown()) {
                                /*
                                 * If the field state is unknown we don't process the state. Unknown
                                 * means that the state can contain any object of any type, but the
                                 * core analysis guarantees that there is no path on which the
                                 * objects of an unknown type state are converted to and used as
                                 * java objects; they are just used as data.
                                 */
                                for (AnalysisObject object : fieldFlow.getState().objects()) {
                                    objectsSet.add(object);
                                }
                            }
                        }
                    }
                }
            }

            referencedObjects = new ArrayList<>(objectsSet);
        }

        return referencedObjects;
    }
}
