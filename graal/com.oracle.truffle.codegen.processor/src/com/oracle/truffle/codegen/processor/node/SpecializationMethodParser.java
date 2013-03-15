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
package com.oracle.truffle.codegen.processor.node;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class SpecializationMethodParser extends MethodParser<SpecializationData> {

    public SpecializationMethodParser(ProcessorContext context, NodeData operation) {
        super(context, operation);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        return createDefaultMethodSpec(method, mirror, null);
    }

    @Override
    public SpecializationData create(TemplateMethod method) {
        return parseSpecialization(method);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Specialization.class;
    }

    private SpecializationData parseSpecialization(TemplateMethod method) {
        int order = Utils.getAnnotationValue(Integer.class, method.getMarkerAnnotation(), "order");
        if (order < 0 && order != Specialization.DEFAULT_ORDER) {
            method.addError("Invalid order attribute %d. The value must be >= 0 or the default value.");
            return null;
        }

        AnnotationValue rewriteValue = Utils.getAnnotationValue(method.getMarkerAnnotation(), "rewriteOn");
        List<TypeMirror> exceptionTypes = Utils.getAnnotationValueList(TypeMirror.class, method.getMarkerAnnotation(), "rewriteOn");
        List<SpecializationThrowsData> exceptionData = new ArrayList<>();
        for (TypeMirror exceptionType : exceptionTypes) {
            SpecializationThrowsData throwsData = new SpecializationThrowsData(method.getMarkerAnnotation(), rewriteValue, exceptionType);
            if (!Utils.canThrowType(method.getMethod().getThrownTypes(), exceptionType)) {
                throwsData.addError("Method must specify a throws clause with the exception type '%s'.", Utils.getQualifiedName(exceptionType));
            }
            exceptionData.add(throwsData);
        }

        Collections.sort(exceptionData, new Comparator<SpecializationThrowsData>() {

            @Override
            public int compare(SpecializationThrowsData o1, SpecializationThrowsData o2) {
                return Utils.compareByTypeHierarchy(o1.getJavaClass(), o2.getJavaClass());
            }
        });
        SpecializationData specialization = new SpecializationData(method, order, exceptionData);
        AnnotationValue guardsValue = Utils.getAnnotationValue(method.getMarkerAnnotation(), "guards");
        List<String> guardDefs = Utils.getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "guards");
        List<SpecializationGuardData> guardData = new ArrayList<>(guardDefs.size());
        for (int i = 0; i < guardDefs.size(); i++) {
            String guardMethod = guardDefs.get(i);

            SpecializationGuardData assignedGuard = new SpecializationGuardData(specialization, guardsValue, guardMethod, true, true);

            guardData.add(assignedGuard);

            GuardData compatibleGuard = matchSpecializationGuard(specialization, assignedGuard);
            if (compatibleGuard != null) {
                assignedGuard.setGuardDeclaration(compatibleGuard);
            }
        }

        specialization.setGuards(guardData);

        return specialization;
    }

    private GuardData matchSpecializationGuard(SpecializationData specialization, SpecializationGuardData specializationGuard) {
        List<GuardData> foundGuards = getNode().findGuards(specializationGuard.getGuardMethod());

        GuardData compatibleGuard = null;
        for (GuardData guardData : foundGuards) {
            if (isGuardCompatible(specialization, guardData)) {
                compatibleGuard = guardData;
                break;
            }
        }

        if (compatibleGuard == null) {
            ParameterSpec returnTypeSpec = new ParameterSpec("returnValue", getContext().getType(boolean.class), false);
            List<ParameterSpec> expectedParameterSpecs = new ArrayList<>();

            for (ActualParameter param : specialization.getParameters()) {
                ParameterSpec spec = param.getSpecification();
                expectedParameterSpecs.add(new ParameterSpec(spec.getName(), param.getActualType(), false));
            }
            List<TypeDef> typeDefs = createTypeDefinitions(returnTypeSpec, expectedParameterSpecs);
            String expectedSignature = TemplateMethodParser.createExpectedSignature(specializationGuard.getGuardMethod(), returnTypeSpec, expectedParameterSpecs, typeDefs);
            specializationGuard.addError("No guard with signature '%s' found in type system.", expectedSignature);
        }

        return compatibleGuard;
    }

    private static boolean isGuardCompatible(SpecializationData specialization, GuardData guard) {
        Iterator<ActualParameter> guardParameters = guard.getParameters().iterator();
        for (ActualParameter param : specialization.getParameters()) {
            if (param.getSpecification().isOptional()) {
                continue;
            }
            if (!guardParameters.hasNext()) {
                return false;
            }
            ActualParameter guardParam = guardParameters.next();
            if (!Utils.typeEquals(guardParam.getActualType(), param.getActualType()) && !guardParam.getSpecification().isOptional()) {
                return false;
            }
        }
        while (guardParameters.hasNext()) {
            ActualParameter param = guardParameters.next();
            if (!param.getSpecification().isOptional()) {
                return false;
            }
        }

        return true;
    }

}
