/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.model.MethodSpec;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.model.TemplateMethod;

public abstract class TemplateMethodParser<T extends Template, E extends TemplateMethod> {

    protected final T template;
    private final ProcessorContext context;
    private final MethodSpecParser parser;
    protected final TruffleTypes types;

    public TemplateMethodParser(ProcessorContext context, T template) {
        this.template = template;
        this.context = context;
        this.parser = new MethodSpecParser(template);
        this.types = context.getTypes();
    }

    protected final ProcessorContext getContext() {
        return context;
    }

    public abstract MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror);

    public abstract E create(TemplateMethod method, boolean invalid);

    public abstract boolean isParsable(ExecutableElement method);

    public DeclaredType getAnnotationType() {
        return null;
    }

    public final List<E> parse(List<? extends Element> elements) {
        List<ExecutableElement> methods = new ArrayList<>();
        methods.addAll(ElementFilter.methodsIn(elements));

        List<E> parsedMethods = new ArrayList<>();
        int naturalOrder = 0;
        for (ExecutableElement method : methods) {
            if (!isParsable(method)) {
                continue;
            }

            DeclaredType annotationType = getAnnotationType();
            AnnotationMirror mirror = null;
            if (annotationType != null) {
                mirror = ElementUtils.findAnnotationMirror(method, annotationType);
            }

            E parsedMethod = parse(naturalOrder, method, mirror);

            if (method.getModifiers().contains(Modifier.PRIVATE) && parser.isEmitErrors()) {
                parsedMethod.addError("Method annotated with @%s must not be private.", getAnnotationType().asElement().getSimpleName().toString());
                parsedMethods.add(parsedMethod);
                continue;
            }

            if (parsedMethod != null) {
                parsedMethods.add(parsedMethod);
            }
            naturalOrder++;
        }
        Collections.sort(parsedMethods);
        return parsedMethods;
    }

    private E parse(int naturalOrder, ExecutableElement method, AnnotationMirror annotation) {
        MethodSpec methodSpecification = createSpecification(method, annotation);
        if (methodSpecification == null) {
            return null;
        }

        TemplateMethod templateMethod = parser.parse(methodSpecification, method, annotation, naturalOrder);
        if (templateMethod != null) {
            return create(templateMethod, templateMethod.hasErrors());
        }
        return null;
    }

    public final E create(String id, int naturalOrder, ExecutableElement methodMetadata, AnnotationMirror mirror, TypeMirror returnType, List<VariableElement> parameterTypes) {
        TemplateMethod method = parser.parseImpl(createSpecification(methodMetadata, mirror), naturalOrder, id, methodMetadata, mirror, returnType, parameterTypes);
        if (method != null) {
            return create(method, method.hasErrors());
        }
        return null;
    }
}
