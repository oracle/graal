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
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.tools.Diagnostic.*;

import com.oracle.truffle.dsl.processor.ProcessorContext.*;
import com.oracle.truffle.dsl.processor.node.*;
import com.oracle.truffle.dsl.processor.typesystem.*;

/**
 * THIS IS NOT PUBLIC API.
 */
// @SupportedAnnotationTypes({"com.oracle.truffle.codegen.Operation",
// "com.oracle.truffle.codegen.TypeLattice"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class TruffleProcessor extends AbstractProcessor implements ProcessCallback {

    private ProcessorContext context;
    private List<AnnotationProcessor<?>> generators;

    private RoundEnvironment round;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            processImpl(roundEnv);
        }
        return false;
    }

    private void processImpl(RoundEnvironment env) {
        this.round = env;
        // TODO run verifications that other annotations are not processed out of scope of the
        // operation or typelattice.
        try {
            for (AnnotationProcessor generator : getGenerators()) {
                AbstractParser<?> parser = generator.getParser();
                if (parser.getAnnotationType() != null) {
                    for (Element e : env.getElementsAnnotatedWith(parser.getAnnotationType())) {
                        processElement(env, generator, e, false);
                    }
                }

                for (Class<? extends Annotation> annotationType : parser.getTypeDelegatedAnnotationTypes()) {
                    for (Element e : env.getElementsAnnotatedWith(annotationType)) {
                        TypeElement processedType;
                        if (parser.isDelegateToRootDeclaredType()) {
                            processedType = Utils.findRootEnclosingType(e);
                        } else {
                            processedType = Utils.findNearestEnclosingType(e);
                        }
                        processElement(env, generator, processedType, false);
                    }
                }

            }
        } finally {
            this.round = null;
        }
    }

    private static void processElement(RoundEnvironment env, AnnotationProcessor generator, Element e, boolean callback) {
        try {
            generator.process(env, e, callback);
        } catch (Throwable e1) {
            handleThrowable(generator, e1, e);
        }
    }

    private static void handleThrowable(AnnotationProcessor generator, Throwable t, Element e) {
        String message = "Uncaught error in " + generator.getClass().getSimpleName() + " while processing " + e;
        generator.getContext().getEnvironment().getMessager().printMessage(Kind.ERROR, message + ": " + Utils.printException(t), e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void callback(TypeElement template) {
        for (AnnotationProcessor generator : generators) {
            Class annotationType = generator.getParser().getAnnotationType();
            if (annotationType != null) {
                Annotation annotation = template.getAnnotation(annotationType);
                if (annotation != null) {
                    processElement(round, generator, template, true);
                }
            }
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        List<Class<? extends Annotation>> annotationsTypes = new ArrayList<>();
        annotationsTypes.addAll(NodeParser.ANNOTATIONS);
        annotationsTypes.addAll(TypeSystemParser.ANNOTATIONS);
        for (Class<? extends Annotation> type : annotationsTypes) {
            annotations.add(type.getCanonicalName());
        }
        return annotations;
    }

    private List<AnnotationProcessor<?>> getGenerators() {
        if (generators == null && processingEnv != null) {
            generators = new ArrayList<>();
            generators.add(new AnnotationProcessor<>(getContext(), new TypeSystemParser(getContext()), new TypeSystemCodeGenerator(getContext())));
            generators.add(new AnnotationProcessor<>(getContext(), new NodeParser(getContext()), new NodeCodeGenerator(getContext())));
        }
        return generators;
    }

    private ProcessorContext getContext() {
        if (context == null) {
            context = new ProcessorContext(processingEnv, this);
        }
        return context;
    }

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        this.processingEnv = env;
        super.init(env);
    }

}
