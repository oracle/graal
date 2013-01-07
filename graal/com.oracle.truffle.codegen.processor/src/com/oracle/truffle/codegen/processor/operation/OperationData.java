/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.codegen.processor.operation;

import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class OperationData extends Template {

    private final TypeSystemData typeSystem;
    private final String[] values;
    private final String[] shortCircuitValues;
    private final OperationFieldData[] operationFields;
    private final OperationFieldData[] constructorFields;
    private final OperationFieldData[] superFields;
    private final TypeMirror nodeType;

    private MethodSpec specification;
    private SpecializationData genericSpecialization;
    private SpecializationData[] specializations;
    private TemplateMethod[] specializationListeners;
    private GuardData[] guards;

    boolean generateFactory = true;

    public OperationData(TypeElement templateType, AnnotationMirror templateTypeAnnotation,
                    TypeSystemData typeSystem, TypeMirror nodeType,
                    String[] values, String[] shortCircuitValues,
                    OperationFieldData[] operationFields,
                    OperationFieldData[] constructorFields,
                    OperationFieldData[] superFields) {
        super(templateType, templateTypeAnnotation);
        this.nodeType = nodeType;
        this.typeSystem = typeSystem;
        this.values = values;
        this.shortCircuitValues = shortCircuitValues;
        this.operationFields = operationFields;
        this.constructorFields = constructorFields;
        this.superFields = superFields;
    }

    public boolean isUseSingleton() {
        return constructorFields.length == 0;
    }

    public boolean hasExtensions() {
        return !getExtensionElements().isEmpty();
    }

    public List<GuardData> findGuards(String name) {
        List<GuardData> foundGuards = new ArrayList<>();
        for (GuardData guardData : guards) {
            if (guardData.getMethodName().equals(name)) {
                foundGuards.add(guardData);
            }
        }
        for (GuardData guardData : getTypeSystem().getGuards()) {
            if (guardData.getMethodName().equals(name)) {
                foundGuards.add(guardData);
            }
        }
        return foundGuards;
    }


    void setGuards(GuardData[] guards) {
        this.guards = guards;
    }

    void setSpecification(MethodSpec specification) {
        this.specification = specification;
    }

    void setGenericSpecialization(SpecializationData genericSpecialization) {
        this.genericSpecialization = genericSpecialization;
    }

    void setSpecializations(SpecializationData[] specializations) {
        this.specializations = specializations;
        for (SpecializationData specialization : specializations) {
            specialization.setOperation(this);
        }
    }

    void setSpecializationListeners(TemplateMethod[] specializationListeners) {
        this.specializationListeners = specializationListeners;
    }

    public String[] getValues() {
        return values;
    }

    public OperationFieldData[] getOperationFields() {
        return operationFields;
    }

    public String[] getShortCircuitValues() {
        return shortCircuitValues;
    }

    public TypeSystemData getTypeSystem() {
        return typeSystem;
    }

    public TypeMirror getNodeType() {
        return nodeType;
    }

    public SpecializationData[] getSpecializations() {
        return specializations;
    }

    public SpecializationData getGenericSpecialization() {
        return genericSpecialization;
    }

    public OperationFieldData[] getConstructorFields() {
        return constructorFields;
    }

    public OperationFieldData[] getSuperFields() {
        return superFields;
    }

    public MethodSpec getSpecification() {
        return specification;
    }

    public SpecializationData[] getAllMethods() {
        return specializations;
    }

    public boolean needsRewrites() {
        boolean needsRewrites = getValues().length > 0 || getShortCircuitValues().length > 0;
        needsRewrites &= specializations.length >= 2;
        return needsRewrites;
    }

    public TemplateMethod[] getSpecializationListeners() {
        return specializationListeners;
    }

    public GuardData[] getGuards() {
        return guards;
    }

    public SpecializationData findUniqueSpecialization(TypeData type) {
        SpecializationData result = null;
        for (SpecializationData specialization : specializations) {
            if (specialization.getReturnType().getActualTypeData(getTypeSystem()) == type) {
                if (result != null) {
                    // Result not unique;
                    return null;
                }
                result = specialization;
            }
        }
        return result;
    }
}
