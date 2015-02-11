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
        MethodSpec spec = createDefaultMethodSpec(method, mirror, true, null);
        spec.getAnnotations().add(new AnnotatedParameterSpec(getContext().getDeclaredType(Cached.class)));
        return spec;
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
        List<TypeMirror> rewriteOnTypes = new ArrayList<>();
        for (TypeMirror exceptionType : exceptionTypes) {
            SpecializationThrowsData throwsData = new SpecializationThrowsData(method.getMarkerAnnotation(), rewriteValue, exceptionType);
            if (!ElementUtils.canThrowType(method.getMethod().getThrownTypes(), exceptionType)) {
                method.addError("A rewriteOn checked exception was specified but not thrown in the method's throws clause. The @%s method must specify a throws clause with the exception type '%s'.",
                                Specialization.class.getSimpleName(), ElementUtils.getQualifiedName(exceptionType));
            }
            rewriteOnTypes.add(throwsData.getJavaClass());
            exceptionData.add(throwsData);
        }

        for (TypeMirror typeMirror : method.getMethod().getThrownTypes()) {
            if (!ElementUtils.canThrowType(rewriteOnTypes, typeMirror)) {
                method.addError(rewriteValue,
                                "A checked exception '%s' is thrown but is not specified using the rewriteOn property. Checked exceptions that are not used for rewriting are not handled by the DSL. Use RuntimeExceptions for this purpose instead.",
                                ElementUtils.getQualifiedName(typeMirror));
            }
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

        return specialization;
    }
}
