/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import org.graalvm.compiler.processor.AbstractProcessor;

// Checkstyle: allow Class.getSimpleName

/**
 * Annotation processor for the @AutomaticallyRegisteredImageSingleton annotation.
 */
@SupportedAnnotationTypes(AutomaticallyRegisteredImageSingletonProcessor.ANNOTATION_CLASS_NAME)
public class AutomaticallyRegisteredImageSingletonProcessor extends AbstractProcessor {

    static final String ANNOTATION_CLASS_NAME = "com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton";

    private final Set<Element> processed = new HashSet<>();

    private void processElement(TypeElement annotatedType) {
        String featureClassName = getTypeNameWithEnclosingClasses(annotatedType, "Feature");
        String packageName = getPackage(annotatedType).getQualifiedName().toString();

        AnnotationMirror singletonAnnotation = getAnnotation(annotatedType, getType(ANNOTATION_CLASS_NAME));
        AnnotationMirror platformsAnnotation = getAnnotation(annotatedType, getType("org.graalvm.nativeimage.Platforms"));

        try (PrintWriter out = createSourceFile(packageName, featureClassName, processingEnv.getFiler(), annotatedType)) {
            out.println("// CheckStyle: stop header check");
            out.println("// CheckStyle: stop line length check");
            out.println("package " + packageName + ";");
            out.println("");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Annotated type: " + annotatedType);
            out.println("// Annotation: " + ANNOTATION_CLASS_NAME);
            out.println("// Annotation processor: " + getClass().getName());
            out.println("");
            out.println("import org.graalvm.nativeimage.ImageSingletons;");
            out.println("import " + AutomaticallyRegisteredFeatureProcessor.ANNOTATION_CLASS_NAME + ";");
            out.println("import " + AutomaticallyRegisteredFeatureProcessor.FEATURE_INTERFACE_CLASS_NAME + ";");
            if (platformsAnnotation != null) {
                out.println("import org.graalvm.nativeimage.Platforms;");
            }
            out.println("");

            if (platformsAnnotation != null) {
                String platforms = getAnnotationValueList(platformsAnnotation, "value", TypeMirror.class).stream().map(type -> type.toString() + ".class").collect(Collectors.joining(", "));
                out.println("@Platforms({" + platforms + "})");
            }
            out.println("@" + getSimpleName(AutomaticallyRegisteredFeatureProcessor.ANNOTATION_CLASS_NAME));
            out.println("public final class " + featureClassName + " implements " + getSimpleName(AutomaticallyRegisteredFeatureProcessor.FEATURE_INTERFACE_CLASS_NAME) + " {");
            out.println("    @Override");
            out.println("    public void afterRegistration(AfterRegistrationAccess access) {");

            List<TypeMirror> onlyWithList = getAnnotationValueList(singletonAnnotation, "onlyWith", TypeMirror.class);
            if (!onlyWithList.isEmpty()) {
                for (var onlyWith : onlyWithList) {
                    out.println("        if (!new " + onlyWith + "().getAsBoolean()) {");
                    out.println("            return;");
                    out.println("        }");
                }
            }

            out.println("        var singleton = new " + annotatedType + "();");
            List<TypeMirror> keysFromAnnotation = getAnnotationValueList(singletonAnnotation, "value", TypeMirror.class);
            if (keysFromAnnotation.isEmpty()) {
                out.println("        ImageSingletons.add(" + annotatedType + ".class, singleton);");
            } else {
                for (var keyFromAnnotation : keysFromAnnotation) {
                    out.println("        ImageSingletons.add(" + keyFromAnnotation.toString() + ".class, singleton);");
                }
            }
            out.println("    }");
            out.println("}");
        }
    }

    /**
     * We allow inner classes to be annotated. To make the generated service name unique, we need to
     * concatenate the simple names of all outer classes.
     */
    static String getTypeNameWithEnclosingClasses(TypeElement annotatedType, String suffix) {
        String result = suffix;
        for (Element cur = annotatedType; cur instanceof TypeElement; cur = cur.getEnclosingElement()) {
            result = cur.getSimpleName().toString() + result;
        }
        return result;
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(getTypeElement(ANNOTATION_CLASS_NAME))) {
            assert element.getKind().isClass();
            if (processed.add(element)) {
                processElement((TypeElement) element);
            }
        }
        return true;
    }
}
