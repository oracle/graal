/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.HashSet;
import java.util.Set;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import com.oracle.truffle.dsl.processor.generator.CodeTypeElementFactory;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.java.transform.FixWarningsVisitor;
import com.oracle.truffle.dsl.processor.java.transform.GenerateOverrideVisitor;
import com.oracle.truffle.dsl.processor.model.Template;
import com.oracle.truffle.dsl.processor.parser.AbstractParser;

/**
 * THIS IS NOT PUBLIC API.
 */
class AnnotationProcessor<M extends Template> {

    private final AbstractParser<M> parser;
    private final CodeTypeElementFactory<M> factory;

    private final Set<String> processedElements = new HashSet<>();

    AnnotationProcessor(AbstractParser<M> parser, CodeTypeElementFactory<M> factory) {
        this.parser = parser;
        this.factory = factory;
    }

    public AbstractParser<M> getParser() {
        return parser;
    }

    @SuppressWarnings({"unchecked"})
    public void process(Element element, boolean callback) {
        if (!(element instanceof TypeElement)) {
            return;
        }
        // since it is not guaranteed to be called only once by the compiler
        // we check for already processed elements to avoid errors when writing files.
        if (!callback) {
            String qualifiedName = ElementUtils.getQualifiedName((TypeElement) element);
            if (processedElements.contains(qualifiedName)) {
                return;
            }
            processedElements.add(qualifiedName);
        }

        ProcessorContext context = ProcessorContext.getInstance();
        TypeElement type = (TypeElement) element;

        M model = (M) context.getTemplate(type.asType(), false);
        boolean firstRun = !context.containsTemplate(type);

        if (firstRun || !callback) {
            context.registerTemplate(type, null);
            model = parser.parse(element);
            context.registerTemplate(type, model);

            if (model != null) {
                CodeTypeElement unit;
                try {
                    unit = factory.create(ProcessorContext.getInstance(), model);
                } catch (Throwable e) {
                    throw new RuntimeException(String.format("Failed to write code for %s. Parserdump:%s.", ElementUtils.getQualifiedName(type), ""), e);
                }
                if (unit == null) {
                    return;
                }
                unit.setGeneratorAnnotationMirror(model.getTemplateTypeAnnotation());
                unit.setGeneratorElement(model.getTemplateType());

                DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
                DeclaredType unusedType = (DeclaredType) context.getType(SuppressWarnings.class);
                unit.accept(new GenerateOverrideVisitor(overrideType), null);
                unit.accept(new FixWarningsVisitor(context.getEnvironment(), unusedType, overrideType), null);

                if (!callback) {
                    unit.accept(new CodeWriter(context.getEnvironment(), element), null);
                }
            }
        }
    }
}
