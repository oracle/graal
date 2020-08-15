/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

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

    private final boolean ignoreUnexpectedResult;

    public SpecializationMethodParser(ProcessorContext context, NodeData operation, boolean ignoreUnexpectedResult) {
        super(context, operation);
        this.ignoreUnexpectedResult = ignoreUnexpectedResult;
    }

    @Override
    public MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror) {
        MethodSpec spec = createDefaultMethodSpec(method, mirror, true, null);
        spec.getAnnotations().add(new CachedParameterSpec(types.Cached));
        spec.getAnnotations().add(new CachedParameterSpec(types.CachedLibrary));
        spec.getAnnotations().add(new CachedParameterSpec(types.CachedContext));
        spec.getAnnotations().add(new CachedParameterSpec(types.CachedLanguage));
        spec.getAnnotations().add(new CachedParameterSpec(types.Bind));
        return spec;
    }

    @Override
    public SpecializationData create(TemplateMethod method, boolean invalid) {
        return parseSpecialization(method);
    }

    @Override
    public DeclaredType getAnnotationType() {
        return types.Specialization;
    }

    private SpecializationData parseSpecialization(TemplateMethod method) {
        List<SpecializationThrowsData> exceptionData = new ArrayList<>();
        boolean unexpectedResultRewrite = false;
        boolean annotated = false;
        if (method.getMethod() != null) {
            AnnotationValue rewriteValue = ElementUtils.getAnnotationValue(method.getMarkerAnnotation(), "rewriteOn");
            List<TypeMirror> exceptionTypes = ElementUtils.getAnnotationValueList(TypeMirror.class, method.getMarkerAnnotation(), "rewriteOn");
            List<TypeMirror> rewriteOnTypes = new ArrayList<>();

            for (TypeMirror exceptionType : exceptionTypes) {
                SpecializationThrowsData throwsData = new SpecializationThrowsData(method.getMessageElement(), method.getMarkerAnnotation(), rewriteValue, exceptionType);
                if (!ElementUtils.canThrowType(method.getMethod().getThrownTypes(), exceptionType)) {
                    method.addError("A rewriteOn checked exception was specified but not thrown in the method's throws clause. The @%s method must specify a throws clause with the exception type '%s'.",
                                    types.Specialization.asElement().getSimpleName().toString(), ElementUtils.getQualifiedName(exceptionType));
                }
                if (!ignoreUnexpectedResult && ElementUtils.typeEquals(exceptionType, types.UnexpectedResultException)) {
                    if (ElementUtils.typeEquals(method.getMethod().getReturnType(), getContext().getType(Object.class))) {
                        method.addError("A specialization with return type 'Object' cannot throw UnexpectedResultException.");
                    }
                    unexpectedResultRewrite = true;
                }
                rewriteOnTypes.add(throwsData.getJavaClass());
                exceptionData.add(throwsData);
            }

            Collections.sort(exceptionData, new Comparator<SpecializationThrowsData>() {

                @Override
                public int compare(SpecializationThrowsData o1, SpecializationThrowsData o2) {
                    return ElementUtils.compareByTypeHierarchy(o1.getJavaClass(), o2.getJavaClass());
                }
            });
            annotated = isAnnotatedWithReportPolymorphismExclude(method);
        }
        SpecializationData specialization = new SpecializationData(getNode(), method, SpecializationKind.SPECIALIZED, exceptionData, unexpectedResultRewrite, !annotated);

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

    private boolean isAnnotatedWithReportPolymorphismExclude(TemplateMethod method) {
        assert method.getMethod() != null;
        return ElementUtils.findAnnotationMirror(method.getMethod(), types.ReportPolymorphism_Exclude) != null;
    }
}
