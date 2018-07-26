/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.parser;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.CachedParameterSpec;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.model.SpecializationData;
import com.oracle.truffle.dsl.processor.model.SpecializationData.SpecializationKind;
import com.oracle.truffle.dsl.processor.model.SpecializationThrowsData;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;

public class SpecializationMethodParser extends NodeMethodParser<SpecializationData> {

    public SpecializationMethodParser(ProcessorContext context, NodeData operation) {
        super(context, operation);
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        MethodSpec spec = createDefaultMethodSpec(method, mirror, true, null);
        spec.getAnnotations().add(new CachedParameterSpec(getContext().getDeclaredType(Cached.class)));
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
        List<SpecializationThrowsData> exceptionData = new ArrayList<>();
        boolean unexpectedResultRewrite = false;
        if (method.getMethod() != null) {
            AnnotationValue rewriteValue = ElementUtils.getAnnotationValue(method.getMarkerAnnotation(), "rewriteOn");
            List<TypeMirror> exceptionTypes = ElementUtils.getAnnotationValueList(TypeMirror.class, method.getMarkerAnnotation(), "rewriteOn");
            List<TypeMirror> rewriteOnTypes = new ArrayList<>();

            for (TypeMirror exceptionType : exceptionTypes) {
                SpecializationThrowsData throwsData = new SpecializationThrowsData(method.getMarkerAnnotation(), rewriteValue, exceptionType);
                if (!ElementUtils.canThrowType(method.getMethod().getThrownTypes(), exceptionType)) {
                    method.addError("A rewriteOn checked exception was specified but not thrown in the method's throws clause. The @%s method must specify a throws clause with the exception type '%s'.",
                                    Specialization.class.getSimpleName(), ElementUtils.getQualifiedName(exceptionType));
                }
                if (ElementUtils.typeEquals(exceptionType, getContext().getType(UnexpectedResultException.class))) {
                    if (ElementUtils.typeEquals(method.getMethod().getReturnType(), getContext().getType(Object.class))) {
                        method.addError("A specialization with return type 'Object' cannot throw UnexpectedResultException.");
                    }
                    unexpectedResultRewrite = true;
                }
                rewriteOnTypes.add(throwsData.getJavaClass());
                exceptionData.add(throwsData);
            }

            for (TypeMirror typeMirror : method.getMethod().getThrownTypes()) {
                if (!ElementUtils.canThrowType(rewriteOnTypes, typeMirror)) {
                    method.addError(rewriteValue, "A checked exception '%s' is thrown but is not specified using the rewriteOn property. " +
                                    "Checked exceptions that are not used for rewriting are not handled by the DSL. Use RuntimeExceptions for this purpose instead.",
                                    ElementUtils.getQualifiedName(typeMirror));
                }
            }

            Collections.sort(exceptionData, new Comparator<SpecializationThrowsData>() {

                @Override
                public int compare(SpecializationThrowsData o1, SpecializationThrowsData o2) {
                    return ElementUtils.compareByTypeHierarchy(o1.getJavaClass(), o2.getJavaClass());
                }
            });
        }
        SpecializationData specialization = new SpecializationData(getNode(), method, SpecializationKind.SPECIALIZED, exceptionData, unexpectedResultRewrite);

        if (method.getMethod() != null) {
            String insertBeforeName = ElementUtils.getAnnotationValue(String.class, method.getMarkerAnnotation(), "insertBefore");
            if (!insertBeforeName.equals("")) {
                specialization.setInsertBeforeName(insertBeforeName);
            }

            List<String> replacesDefs = new ArrayList<>();
            replacesDefs.addAll(ElementUtils.getAnnotationValueList(String.class, specialization.getMarkerAnnotation(), "replaces"));

            Set<String> containsNames = specialization.getReplacesNames();
            containsNames.clear();
            if (replacesDefs != null) {
                for (String include : replacesDefs) {
                    if (!containsNames.contains(include)) {
                        specialization.getReplacesNames().add(include);
                    } else {
                        AnnotationValue value = ElementUtils.getAnnotationValue(specialization.getMarkerAnnotation(), "replaces");
                        specialization.addError(value, "Duplicate replace declaration '%s'.", include);
                    }
                }

            }
        }

        return specialization;
    }
}
