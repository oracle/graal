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

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.codegen.*;
import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.template.*;
import com.oracle.truffle.codegen.processor.template.ParameterSpec.Kind;
import com.oracle.truffle.codegen.processor.typesystem.*;


public class SpecializationParser extends OperationMethodParser<SpecializationData> {

    private final MethodSpec specification;

    public SpecializationParser(ProcessorContext context, OperationData operation) {
        super(context, operation);
        this.specification = createDefaultMethodSpec(null);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        return specification;
    }
    public MethodSpec getSpecification() {
        return specification;
    }

    @Override
    public SpecializationData create(TemplateMethod method) {
        return parseSpecialization(method);
    }

    @Override
    public Class< ? extends Annotation> getAnnotationType() {
        return Specialization.class;
    }

    private SpecializationData parseSpecialization(TemplateMethod template) {
        int order = Utils.getAnnotationValueInt(template.getMarkerAnnotation(), "order");
        if (order < 0 && order != Specialization.DEFAULT_ORDER) {
            getContext().getLog().error(template.getMethod(), template.getMarkerAnnotation(), "Invalid order attribute %d. The value must be >= 0 or the default value.");
            return null;
        }

        List<AnnotationMirror> exceptionDefs = Utils.collectAnnotations(getContext(), template.getMarkerAnnotation(), "exceptions", template.getMethod(), SpecializationThrows.class);
        SpecializationThrowsData[] exceptionData = new SpecializationThrowsData[exceptionDefs.size()];
        for (int i = 0; i < exceptionData.length; i++) {
            AnnotationMirror mirror = exceptionDefs.get(i);
            TypeMirror javaClass = Utils.getAnnotationValueType(mirror, "javaClass");
            String transitionTo = Utils.getAnnotationValueString(mirror, "transitionTo");
            exceptionData[i] = new SpecializationThrowsData(mirror, javaClass, transitionTo);

            if (!Utils.canThrowType(template.getMethod().getThrownTypes(), javaClass)) {
                getContext().getLog().error(template.getMethod(), "Method must specify a throws clause with the exception type '%s'.", Utils.getQualifiedName(javaClass));
                return null;
            }
        }

        Arrays.sort(exceptionData, new Comparator<SpecializationThrowsData>() {
            @Override
            public int compare(SpecializationThrowsData o1, SpecializationThrowsData o2) {
                return Utils.compareByTypeHierarchy(o1.getJavaClass(), o2.getJavaClass());
            }
        });
        SpecializationData specialization = new SpecializationData(template, order, exceptionData);

        boolean valid = true;
        List<AnnotationMirror> guardDefs = Utils.collectAnnotations(getContext(), template.getMarkerAnnotation(), "guards", template.getMethod(), SpecializationGuard.class);
        SpecializationGuardData[] guardData = new SpecializationGuardData[guardDefs.size()];
        for (int i = 0; i < guardData.length; i++) {
            AnnotationMirror guardMirror = guardDefs.get(i);
            String guardMethod = Utils.getAnnotationValueString(guardMirror, "methodName");
            boolean onSpecialization = Utils.getAnnotationValueBoolean(guardMirror, "onSpecialization");
            boolean onExecution = Utils.getAnnotationValueBoolean(guardMirror, "onExecution");

            if (!onSpecialization && !onExecution) {
                String message = "Either onSpecialization, onExecution or both must be enabled.";
                getContext().getLog().error(template.getMethod(), guardMirror, message);
                valid = false;
                continue;
            }

            guardData[i] = new SpecializationGuardData(guardMethod, onSpecialization, onExecution);

            GuardData compatibleGuard = matchSpecializationGuard(guardMirror, specialization, guardData[i]);
            if (compatibleGuard != null) {
                guardData[i].setGuardDeclaration(compatibleGuard);
            } else {
                valid = false;
            }
        }

        if (!valid) {
            return null;
        }

        specialization.setGuards(guardData);

        return specialization;
    }

    private GuardData matchSpecializationGuard(AnnotationMirror mirror, SpecializationData specialization, SpecializationGuardData specializationGuard)  {
        List<GuardData> foundGuards = getOperation().findGuards(specializationGuard.getGuardMethod());
        GuardData compatibleGuard = null;
        for (GuardData guardData : foundGuards) {
            if (isGuardCompatible(specialization, guardData)) {
                compatibleGuard = guardData;
                break;
            }
        }

        if (compatibleGuard == null) {
            ParameterSpec returnTypeSpec = new ParameterSpec("returnValue", getContext().getType(boolean.class), Kind.ATTRIBUTE, false);
            List<ParameterSpec> expectedParameterSpecs = new ArrayList<>();

            for (ActualParameter param : filterGuardParameters(specialization)) {
                ParameterSpec spec = param.getSpecification();
                expectedParameterSpecs.add(new ParameterSpec(spec.getName(), param.getActualType(), Kind.ATTRIBUTE, false));
            }
            String expectedSignature = TemplateMethodParser.createExpectedSignature(specializationGuard.getGuardMethod(), returnTypeSpec, expectedParameterSpecs);
            AnnotationValue value = Utils.getAnnotationValue(mirror, "methodName");
            getContext().getLog().error(specialization.getMethod(), mirror, value, "No guard with signature '%s' found in type system.", expectedSignature);
            return null;
        }

        return compatibleGuard;
    }

    private static boolean isGuardCompatible(SpecializationData specialization, GuardData guard) {
        Iterator<ActualParameter> guardParameters = Arrays.asList(guard.getParameters()).iterator();
        for (ActualParameter param : filterGuardParameters(specialization)) {
            if (!guardParameters.hasNext()) {
                return false;
            }
            ActualParameter guardParam = guardParameters.next();
            if (!Utils.typeEquals(guardParam.getActualType(), param.getActualType())) {
                return false;
            }
        }
        if (guardParameters.hasNext()) {
            return false;
        }
        return true;
    }

    private static List<ActualParameter> filterGuardParameters(SpecializationData specialization) {
        List<ActualParameter> parameters = new ArrayList<>();
        for (ActualParameter param : specialization.getParameters()) {
            if (param.getSpecification().getKind() != Kind.EXECUTE
                            && param.getSpecification().getKind() != Kind.SHORT_CIRCUIT) {
                continue;
            }
            parameters.add(param);
        }
        return parameters;
    }


}
