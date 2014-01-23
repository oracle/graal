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

import java.io.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.*;

import com.oracle.truffle.dsl.processor.ast.*;
import com.oracle.truffle.dsl.processor.codewriter.*;
import com.oracle.truffle.dsl.processor.compiler.*;
import com.oracle.truffle.dsl.processor.template.*;

/**
 * THIS IS NOT PUBLIC API.
 */
class AnnotationProcessor<M extends Template> {

    private final AbstractParser<M> parser;
    private final CompilationUnitFactory<M> factory;
    private final ProcessorContext context;

    private final Set<String> processedElements = new HashSet<>();

    public AnnotationProcessor(ProcessorContext context, AbstractParser<M> parser, CompilationUnitFactory<M> factory) {
        this.context = context;
        this.parser = parser;
        this.factory = factory;
    }

    public AbstractParser<M> getParser() {
        return parser;
    }

    public ProcessorContext getContext() {
        return context;
    }

    @SuppressWarnings({"unchecked"})
    public void process(RoundEnvironment env, Element element, boolean callback) {

        // since it is not guaranteed to be called only once by the compiler
        // we check for already processed elements to avoid errors when writing files.
        if (!callback && element instanceof TypeElement) {
            String qualifiedName = Utils.getQualifiedName((TypeElement) element);
            if (processedElements.contains(qualifiedName)) {
                return;
            }
            processedElements.add(qualifiedName);
        }

        TypeElement type = (TypeElement) element;

        M model = (M) context.getTemplate(type.asType(), false);
        boolean firstRun = !context.containsTemplate(type);

        if (firstRun || !callback) {
            context.registerTemplate(type, null);
            model = parser.parse(env, element);
            context.registerTemplate(type, model);

            if (model != null) {
                CodeCompilationUnit unit = factory.process(null, model);
                unit.setGeneratorAnnotationMirror(model.getTemplateTypeAnnotation());
                unit.setGeneratorElement(model.getTemplateType());

                DeclaredType overrideType = (DeclaredType) context.getType(Override.class);
                DeclaredType unusedType = (DeclaredType) context.getType(SuppressWarnings.class);
                unit.accept(new GenerateOverrideVisitor(overrideType), null);
                unit.accept(new FixWarningsVisitor(context, unusedType, overrideType), null);

                if (!callback) {
                    unit.accept(new CodeWriter(context.getEnvironment(), element), null);
                }
            }
        }
    }

    private static class CodeWriter extends AbstractCodeWriter {

        private final Element originalElement;
        private final ProcessingEnvironment env;

        public CodeWriter(ProcessingEnvironment env, Element originalElement) {
            this.env = env;
            this.originalElement = originalElement;
        }

        @Override
        protected Writer createWriter(CodeTypeElement clazz) throws IOException {
            JavaFileObject jfo = env.getFiler().createSourceFile(clazz.getQualifiedName(), originalElement);
            return new BufferedWriter(jfo.openWriter());
        }

        @Override
        protected void writeHeader() {
            if (env == null) {
                return;
            }
            String comment = CompilerFactory.getCompiler(originalElement).getHeaderComment(env, originalElement);
            if (comment != null) {
                writeLn(comment);
            }
        }

    }

}
