/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
import javax.lang.model.util.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.*;
import com.oracle.truffle.dsl.processor.model.*;

public abstract class TemplateMethodParser<T extends Template, E extends TemplateMethod> {

    protected final T template;
    private final ProcessorContext context;
    private final MethodSpecParser parser;

    private boolean parseNullOnError;

    public TemplateMethodParser(ProcessorContext context, T template) {
        this.template = template;
        this.context = context;
        this.parser = new MethodSpecParser(template);
    }

    public void setParseNullOnError(boolean parseNullOnError) {
        this.parseNullOnError = parseNullOnError;
    }

    public boolean isParseNullOnError() {
        return parseNullOnError;
    }

    public MethodSpecParser getParser() {
        return parser;
    }

    public ProcessorContext getContext() {
        return context;
    }

    public TypeSystemData getTypeSystem() {
        return template.getTypeSystem();
    }

    public abstract MethodSpec createSpecification(ExecutableElement method, AnnotationMirror mirror);

    public abstract E create(TemplateMethod method, boolean invalid);

    public abstract boolean isParsable(ExecutableElement method);

    public Class<? extends Annotation> getAnnotationType() {
        return null;
    }

    public final List<E> parse(List<? extends Element> elements) {
        List<ExecutableElement> methods = new ArrayList<>();
        methods.addAll(ElementFilter.methodsIn(elements));

        List<E> parsedMethods = new ArrayList<>();
        boolean valid = true;
        int naturalOrder = 0;
        for (ExecutableElement method : methods) {
            if (!isParsable(method)) {
                continue;
            }

            Class<? extends Annotation> annotationType = getAnnotationType();
            AnnotationMirror mirror = null;
            if (annotationType != null) {
                mirror = ElementUtils.findAnnotationMirror(getContext().getEnvironment(), method, annotationType);
            }

            E parsedMethod = parse(naturalOrder, method, mirror);

            if (method.getModifiers().contains(Modifier.PRIVATE) && parser.isEmitErrors()) {
                parsedMethod.addError("Method annotated with @%s must not be private.", getAnnotationType().getSimpleName());
                parsedMethods.add(parsedMethod);
                valid = false;
                continue;
            }

            if (parsedMethod != null) {
                parsedMethods.add(parsedMethod);
            } else {
                valid = false;
            }
            naturalOrder++;
        }
        Collections.sort(parsedMethods);

        if (!valid && isParseNullOnError()) {
            return null;
        }
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

    public final E create(String id, int naturalOrder, ExecutableElement methodMetadata, AnnotationMirror mirror, TypeMirror returnType, List<TypeMirror> parameterTypes) {
        TemplateMethod method = parser.parseImpl(createSpecification(methodMetadata, mirror), naturalOrder, id, methodMetadata, mirror, returnType, parameterTypes);
        if (method != null) {
            return create(method, method.hasErrors());
        }
        return null;
    }
}
