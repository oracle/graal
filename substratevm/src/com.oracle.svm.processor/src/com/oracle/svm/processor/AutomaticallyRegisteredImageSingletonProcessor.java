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

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import jdk.graal.compiler.processor.AbstractProcessor;

// Checkstyle: allow Class.getSimpleName

/**
 * Annotation processor for the @AutomaticallyRegisteredImageSingleton annotation.
 */
@SupportedAnnotationTypes(AutomaticallyRegisteredImageSingletonProcessor.ANNOTATION_CLASS_NAME)
public class AutomaticallyRegisteredImageSingletonProcessor extends AbstractProcessor {

    static final String ANNOTATION_CLASS_NAME = "com.oracle.svm.shared.singletons.AutomaticallyRegisteredImageSingleton";
    static final String SERVICE_REGISTRATION_INTERFACE_NAME = "com.oracle.svm.core.singleton.AutomaticallyRegisteredImageSingletonServiceRegistration";

    private final Set<Element> processed = new HashSet<>(); // noEconomicSet(dependency)

    private void processElement(TypeElement annotatedType) {
        String serviceRegistrationImplClassName = getTypeNameWithEnclosingClasses(annotatedType, "_ServiceRegistration");
        String packageName = getPackage(annotatedType).getQualifiedName().toString();
        String annotatedTypeClassName = processingEnv.getElementUtils().getBinaryName(annotatedType).toString();

        try (PrintWriter out = createSourceFile(packageName, serviceRegistrationImplClassName, processingEnv.getFiler(), annotatedType)) {
            out.println("// CheckStyle: stop header check");
            out.println("// CheckStyle: stop line length check");
            out.println("package " + packageName + ";");
            out.println("");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Annotated type: " + annotatedType);
            out.println("// Annotation: " + ANNOTATION_CLASS_NAME);
            out.println("// Annotation processor: " + getClass().getName());
            out.println("");
            out.println("import " + SERVICE_REGISTRATION_INTERFACE_NAME + ";");
            out.println("import org.graalvm.nativeimage.Platform;");
            out.println("import org.graalvm.nativeimage.Platforms;");
            out.println("");
            out.println("@Platforms(Platform.HOSTED_ONLY.class)");
            out.println("public final class " + serviceRegistrationImplClassName + " implements " + getSimpleName(SERVICE_REGISTRATION_INTERFACE_NAME) + " {");
            out.println("    @Override");
            out.println("    public String getClassName() {");
            out.println("        return \"" + annotatedTypeClassName + "\";");
            out.println("    }");
            out.println("}");
        }

        createProviderFile(packageName + "." + serviceRegistrationImplClassName, SERVICE_REGISTRATION_INTERFACE_NAME, annotatedType);
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
