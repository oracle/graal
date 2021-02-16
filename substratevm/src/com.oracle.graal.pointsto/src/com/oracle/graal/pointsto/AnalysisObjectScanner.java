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

import com.oracle.graal.pointsto.flow.ArrayElementsTypeFlow;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.typestate.TypeState;

import jdk.vm.ci.meta.JavaConstant;

public class AnalysisObjectScanner extends ObjectScanner {

    public AnalysisObjectScanner(BigBang bigbang, ReusableSet scannedObjects) {
        super(bigbang, scannedObjects);
    }

    @Override
    public void forRelocatedPointerFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
        if (!field.isWritten()) {
            field.registerAsWritten(null);
        }
    }

    @Override
    public void forNullFieldValue(JavaConstant receiver, AnalysisField field) {
        FieldTypeFlow fieldTypeFlow = getFieldTypeFlow(field, receiver);
        if (!fieldTypeFlow.getState().canBeNull()) {
            /* Signal that the field can contain null. */
            fieldTypeFlow.addState(bb, TypeState.forNull());
        }
    }

    @Override
    public void forNonNullFieldValue(JavaConstant receiver, AnalysisField field, JavaConstant fieldValue) {
        AnalysisType fieldType = bb.getMetaAccess().lookupJavaType(bb.getSnippetReflectionProvider().asObject(Object.class, fieldValue).getClass());
        assert fieldType.isInstantiated() : fieldType;

        /*
         * *ALL* constants are scanned after each analysis iteration, thus the fieldType will
         * eventually be added to the AllInstantiatedTypeFlow and the field type flow will
         * eventually be updated.
         */

        if (bb.getAllInstantiatedTypeFlow().getState().containsType(fieldType)) {
            /* Add the constant value object to the field's type flow. */
            FieldTypeFlow fieldTypeFlow = getFieldTypeFlow(field, receiver);
            AnalysisObject constantObject = bb.analysisPolicy().createConstantObject(bb, fieldValue, fieldType);
            if (!fieldTypeFlow.getState().isUnknown() && !fieldTypeFlow.getState().containsObject(constantObject)) {
                /* Add the new constant to the field's flow state. */
                TypeState constantTypeState = TypeState.forNonNullObject(bb, constantObject);
                fieldTypeFlow.addState(bb, constantTypeState);
            }
        }
    }

    /** Get the field type flow give a receiver. */
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
            AnalysisType receiverType = bb.getMetaAccess().lookupJavaType(bb.getSnippetReflectionProvider().asObject(Object.class, receiver).getClass());
            AnalysisObject constantReceiverObj = bb.analysisPolicy().createConstantObject(bb, receiver, receiverType);
            return constantReceiverObj.getInstanceFieldFlow(bb, field, true);
        }
    }

    @Override
    public void forNullArrayElement(JavaConstant array, AnalysisType arrayType, int elementIndex) {
        ArrayElementsTypeFlow arrayObjElementsFlow = getArrayElementsFlow(array, arrayType);
        if (!arrayObjElementsFlow.getState().canBeNull()) {
            /* Signal that the constant array can contain null. */
            arrayObjElementsFlow.addState(bb, TypeState.forNull());
        }
    }

    @Override
    public void forNonNullArrayElement(JavaConstant array, AnalysisType arrayType, JavaConstant elementConstant, AnalysisType elementType, int elementIndex) {
        /*
         * *ALL* constants are scanned after each analysis iteration, thus the elementType will
         * eventually be added to the AllInstantiatedTypeFlow and the array elements flow will
         * eventually be updated.
         */
        if (bb.getAllInstantiatedTypeFlow().getState().containsType(elementType)) {
            ArrayElementsTypeFlow arrayObjElementsFlow = getArrayElementsFlow(array, arrayType);
            AnalysisObject constantObject = bb.analysisPolicy().createConstantObject(bb, elementConstant, elementType);
            if (!arrayObjElementsFlow.getState().isUnknown() && !arrayObjElementsFlow.getState().containsObject(constantObject)) {
                /* Add the constant element to the constant's array type flow. */
                TypeState elementTypeState = TypeState.forNonNullObject(bb, constantObject);
                arrayObjElementsFlow.addState(bb, elementTypeState);
            }
        }
    }

    /** Get the array elements flow given its type and the array constant. */
    private ArrayElementsTypeFlow getArrayElementsFlow(JavaConstant array, AnalysisType arrayType) {
        AnalysisObject arrayObjConstant = bb.analysisPolicy().createConstantObject(bb, array, arrayType);
        return arrayObjConstant.getArrayElementsFlow(bb, true);
    }

    @Override
    protected void forScannedConstant(JavaConstant value, ScanReason reason) {
        Object valueObj = bb.getSnippetReflectionProvider().asObject(Object.class, value);
        AnalysisType type = bb.getMetaAccess().lookupJavaType(valueObj.getClass());

        type.registerAsInHeap();
    }
}
