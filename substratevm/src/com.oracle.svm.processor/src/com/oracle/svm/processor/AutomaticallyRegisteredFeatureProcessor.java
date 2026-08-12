/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.processor;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import jdk.graal.compiler.processor.AbstractProcessor;

// Checkstyle: allow Class.getSimpleName

/**
 * Annotation processor for the @AutomaticallyRegisteredFeature annotation. We need to generate some
 * textual listing of all annotated feature classes that can be easily loaded in the image builder.
 * Standard Java ServiceLoader descriptors are the easiest, because mx already has the support to
 * aggregate service descriptors for multiple projects that end up in the same module.
 *
 * But ServiceLoader has one big downside: the implementation classes that are loaded must be
 * public. We have many feature classes that are small and just "at the end" of other source files
 * and therefore cannot be public. But there is an easy workaround: we do not register the feature
 * classes as services, but automatically generate a "service registration" class in this annotation
 * processor too. Generated feature classes can opt out when another processor owns registration.
 */
@SupportedAnnotationTypes(AutomaticallyRegisteredFeatureProcessor.ANNOTATION_CLASS_NAME)
public class AutomaticallyRegisteredFeatureProcessor extends AbstractProcessor {

    static final String ANNOTATION_CLASS_NAME = "com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature";
    static final String FEATURE_INTERFACE_CLASS_NAME = "com.oracle.svm.core.feature.InternalFeature";

    private final Set<Element> processed = new HashSet<>(); // noEconomicSet(dependencies)

    private void processElement(TypeElement annotatedType, AnnotationMirror annotationMirror) {
        if (!processingEnv.getTypeUtils().isSubtype(annotatedType.asType(), getType(FEATURE_INTERFACE_CLASS_NAME))) {
            String msg = String.format("Class %s annotated with %s must implement interface %s", annotatedType.getSimpleName(), ANNOTATION_CLASS_NAME, FEATURE_INTERFACE_CLASS_NAME);
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, annotatedType);
            return;
        }
        if (annotatedType.getNestingKind().isNested()) {
            /*
             * This is a simplifying constraint that means we do not have to process the qualified
             * name to insert '$' characters at the relevant positions.
             */
            String msg = String.format("Class %s annotated with %s must be a top level class", annotatedType.getSimpleName(), ANNOTATION_CLASS_NAME);
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, annotatedType);
            return;
        }

        if (getAnnotationValue(annotationMirror, "generateRegistration", Boolean.class)) {
            AutomaticallyRegisteredFeatureSupport.generateRegistration(this, annotatedType);
        }
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        TypeElement annotationType = getTypeElement(ANNOTATION_CLASS_NAME);
        for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
            assert element.getKind().isClass();
            if (processed.add(element)) {
                processElement((TypeElement) element, getAnnotation(element, annotationType.asType()));
            }
        }
        return true;
    }
}
