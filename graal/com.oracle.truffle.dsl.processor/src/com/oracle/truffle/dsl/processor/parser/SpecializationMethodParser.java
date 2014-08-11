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
package com.oracle.truffle.dsl.processor.parser;

import java.lang.annotation.*;
import java.util.*;

import javax.lang.model.element.*;
import javax.lang.model.type.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.*;
import com.oracle.truffle.dsl.processor.model.SpecializationData.SpecializationKind;

public class SpecializationMethodParser extends NodeMethodParser<SpecializationData> {

    public SpecializationMethodParser(ProcessorContext context, NodeData operation) {
        super(context, operation);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        return createDefaultMethodSpec(method, mirror, true, null);
    }

    @Override
    public SpecializationData create(TemplateMethod method, boolean invalid) {
        return parseSpecialization(method);
    }

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Specialization.class;
    }

    private SpecializationData parseSpecialization(TemplateMethod method) {
        AnnotationValue rewriteValue = ElementUtils.getAnnotationValue(method.getMarkerAnnotation(), "rewriteOn");
        List<TypeMirror> exceptionTypes = ElementUtils.getAnnotationValueList(TypeMirror.class, method.getMarkerAnnotation(), "rewriteOn");
        List<SpecializationThrowsData> exceptionData = new ArrayList<>();
        for (TypeMirror exceptionType : exceptionTypes) {
            SpecializationThrowsData throwsData = new SpecializationThrowsData(method.getMarkerAnnotation(), rewriteValue, exceptionType);
            if (!ElementUtils.canThrowType(method.getMethod().getThrownTypes(), exceptionType)) {
                throwsData.addError("Method must specify a throws clause with the exception type '%s'.", ElementUtils.getQualifiedName(exceptionType));
            }
            exceptionData.add(throwsData);
        }

        Collections.sort(exceptionData, new Comparator<SpecializationThrowsData>() {

            @Override
            public int compare(SpecializationThrowsData o1, SpecializationThrowsData o2) {
                return ElementUtils.compareByTypeHierarchy(o1.getJavaClass(), o2.getJavaClass());
            }
        });
        SpecializationData specialization = new SpecializationData(getNode(), method, SpecializationKind.SPECIALIZED, exceptionData);

        String insertBeforeName = ElementUtils.getAnnotationValue(String.class, method.getMarkerAnnotation(), "insertBefore");
        if (!insertBeforeName.equals("")) {
            specialization.setInsertBeforeName(insertBeforeName);
        }

        List<String> guardDefs = ElementUtils.getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "guards");
        List<GuardExpression> guardExpressions = new ArrayList<>();
        for (String guardDef : guardDefs) {
            guardExpressions.add(new GuardExpression(guardDef));
        }
        specialization.setGuards(guardExpressions);

        List<String> containsDefs = ElementUtils.getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "contains");
        Set<String> containsNames = specialization.getContainsNames();
        containsNames.clear();
        if (containsDefs != null) {
            for (String include : containsDefs) {
                if (!containsNames.contains(include)) {
                    specialization.getContainsNames().add(include);
                } else {
                    AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "contains");
                    specialization.addError(value, "Duplicate contains declaration '%s'.", include);
                }
            }

        }

        List<String> assumptionDefs = ElementUtils.getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "assumptions");
        specialization.setAssumptions(assumptionDefs);

        for (String assumption : assumptionDefs) {
            if (!getNode().getAssumptions().contains(assumption)) {
                specialization.addError("Undeclared assumption '%s' used. Use @NodeAssumptions to declare them.", assumption);
            }
        }

        return specialization;
    }
}
