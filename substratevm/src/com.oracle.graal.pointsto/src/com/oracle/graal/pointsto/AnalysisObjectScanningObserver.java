/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.pointsto.ObjectScanner.ScanReason;
import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.meta.JavaConstant;

public class AnalysisObjectScanningObserver implements ObjectScanningObserver {

    private final BigBang bb;

    public AnalysisObjectScanningObserver(BigBang bb) {
        this.bb = bb;
    }

    @Override
    public boolean forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        if (!field.isWritten()) {
            return field.registerAsWritten(null);
        }
        return false;
    }

    @Override
    public boolean forNullFieldValue(JavaConstant receiver, AnalysisField field, ScanReason reason) {
        FieldTypeFlow fieldTypeFlow = getFieldTypeFlow(field, receiver);
        if (!fieldTypeFlow.getState().canBeNull()) {
            /* Signal that the field can contain null. */
            return fieldTypeFlow.addState(getAnalysis(), TypeState.forNull());
        }
        return false;
    }

    @Override
    public boolean forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue, ScanReason reason) {
        PointsToAnalysis analysis = getAnalysis();
        AnalysisType fieldType = analysis.getMetaAccess().lookupJavaType(analysis.getSnippetReflectionProvider().asObject(Object.class, fieldValue).getClass());

        /* Add the constant value object to the field's type flow. */
        FieldTypeFlow fieldTypeFlow = getFieldTypeFlow(field, receiver);
        AnalysisObject constantObject = bb.analysisPolicy().createConstantObject(analysis, fieldValue, fieldType);
        if (!fieldTypeFlow.getState().containsObject(constantObject)) {
            /* Add the new constant to the field's flow state. */
            TypeState constantTypeState = TypeState.forNonNullObject(analysis, constantObject);
            return fieldTypeFlow.addState(analysis, constantTypeState);
        }
        return false;
    }

    /**
     * Get the field type flow give a receiver.
     */
    private FieldTypeFlow getFieldTypeFlow(AnalysisField field, JavaConstant receiver) {
        /* The field type flow is used to track the constant field value. */
        if (field.isStatic()) {
            /* If the field is static it comes from the originalRoots. */
            return field.getStaticFieldFlow();
        } else {
            /*
             * The field comes from a constant scan, thus it's type flow is mapped to the unique
             * constant object.
             */
            PointsToAnalysis analysis = getAnalysis();
            AnalysisType receiverType = analysis.getMetaAccess().lookupJavaType(analysis.getSnippetReflectionProvider().asObject(Object.class, receiver).getClass());
            AnalysisObject constantReceiverObj = analysis.analysisPolicy().createConstantObject(analysis, receiver, receiverType);
            return constantReceiverObj.getInstanceFieldFlow(analysis, field, true);
        }
    }

    @Override
    public boolean forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex, ScanReason reason) {
        ArrayElementsTypeFlow arrayObjElementsFlow = getArrayElementsFlow(array, arrayType);
        if (!arrayObjElementsFlow.getState().canBeNull()) {
            /* Signal that the constant array can contain null. */
            return arrayObjElementsFlow.addState(getAnalysis(), TypeState.forNull());
        }
        return false;
    }

    @Override
    public boolean forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int elementIndex, ScanReason reason) {
        ArrayElementsTypeFlow arrayObjElementsFlow = getArrayElementsFlow(array, arrayType);
        PointsToAnalysis analysis = getAnalysis();
        AnalysisObject constantObject = bb.analysisPolicy().createConstantObject(analysis, elementConstant, elementType);
        if (!arrayObjElementsFlow.getState().containsObject(constantObject)) {
            /* Add the constant element to the constant's array type flow. */
            TypeState elementTypeState = TypeState.forNonNullObject(analysis, constantObject);
            return arrayObjElementsFlow.addState(analysis, elementTypeState);
        }
        return false;
    }

    /**
     * Get the array elements flow given its type and the array constant.
     */
    private ArrayElementsTypeFlow getArrayElementsFlow(JavaConstant array, AnalysisType arrayType) {
        PointsToAnalysis analysis = getAnalysis();
        AnalysisObject arrayObjConstant = analysis.analysisPolicy().createConstantObject(analysis, array, arrayType);
        return arrayObjConstant.getArrayElementsFlow(analysis, true);
    }

    @Override
    public void forScannedConstant(JavaConstant value, ScanReason reason) {
        PointsToAnalysis analysis = getAnalysis();
        Object valueObj = analysis.getSnippetReflectionProvider().asObject(Object.class, value);
        AnalysisType type = bb.getMetaAccess().lookupJavaType(valueObj.getClass());

        type.registerAsInHeap();
    }

    private PointsToAnalysis getAnalysis() {
        return ((PointsToAnalysis) bb);
    }
}
