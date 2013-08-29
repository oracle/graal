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
package com.oracle.truffle.dsl.processor;

import java.lang.annotation.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.template.*;

/**
 * THIS IS NOT PUBLIC API.
 */
public abstract class AbstractParser<M extends Template> {

    protected final ProcessorContext context;
    protected final ProcessingEnvironment processingEnv;
    protected RoundEnvironment roundEnv;

    protected final Log log;

    public AbstractParser(ProcessorContext c) {
        this.context = c;
        this.processingEnv = c.getEnvironment();
        this.log = c.getLog();
    }

    public final M parse(RoundEnvironment env, Element element) {
        this.roundEnv = env;
        M model = null;
        try {
            AnnotationMirror mirror = null;
            if (getAnnotationType() != null) {
                mirror = Utils.findAnnotationMirror(processingEnv, element.getAnnotationMirrors(), getAnnotationType());
            }

            if (!context.getTruffleTypes().verify(context, element, mirror)) {
                return null;
            }
            model = parse(element, mirror);
            if (model == null) {
                return null;
            }

            model.emitMessages(context, (TypeElement) element, log);
            return filterErrorElements(model);
        } catch (CompileErrorException e) {
            log.message(Kind.WARNING, element, null, null, "The truffle processor could not parse class due to error: %s", e.getMessage());
            return null;
        } finally {
            this.roundEnv = null;
        }
    }

    protected M filterErrorElements(M model) {
        return model.hasErrors() ? null : model;
    }

    protected abstract M parse(Element element, AnnotationMirror mirror);

    public abstract Class<? extends Annotation> getAnnotationType();

    public boolean isDelegateToRootDeclaredType() {
        return false;
    }

    public List<Class<? extends Annotation>> getAllAnnotationTypes() {
        List<Class<? extends Annotation>> list = new ArrayList<>();
        if (getAnnotationType() != null) {
            list.add(getAnnotationType());
        }
        list.addAll(getTypeDelegatedAnnotationTypes());
        return list;
    }

    public List<Class<? extends Annotation>> getTypeDelegatedAnnotationTypes() {
        return Collections.emptyList();
    }

}
