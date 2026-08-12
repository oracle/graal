/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import jdk.graal.compiler.processor.AbstractProcessor;

// Checkstyle: allow Class.getSimpleName

/**
 * Shared support for generating feature service registrations.
 */
final class AutomaticallyRegisteredFeatureSupport {

    private static final String SERVICE_REGISTRATION_INTERFACE_NAME = "com.oracle.svm.core.feature.AutomaticallyRegisteredFeatureServiceRegistration";

    private AutomaticallyRegisteredFeatureSupport() {
    }

    static void generateRegistration(AbstractProcessor processor, TypeElement annotatedType) {
        String packageName = AbstractProcessor.getPackage(annotatedType).getQualifiedName().toString();
        String featureClassName = annotatedType.getSimpleName().toString();
        generateRegistration(processor, packageName, featureClassName, annotatedType.toString(), annotatedType);
    }

    static void generateRegistration(AbstractProcessor processor, String packageName, String featureClassName, Element originatingElement) {
        generateRegistration(processor, packageName, featureClassName, packageName + "." + featureClassName, originatingElement);
    }

    private static void generateRegistration(AbstractProcessor processor, String packageName, String featureClassName, String annotatedType, Element originatingElement) {
        String serviceRegistrationImplClassName = featureClassName + "_ServiceRegistration";
        String featureImplementationClassName = packageName + "." + featureClassName;

        /*
         * Generate the "service registration" class. This class is public and can therefore
         * registered as standard Java service. Its only purpose is to return the
         * featureImplementationClassName.
         */
        try (PrintWriter out = AbstractProcessor.createSourceFile(packageName, serviceRegistrationImplClassName, processor.env().getFiler(), originatingElement)) {
            out.println("// CheckStyle: stop header check");
            out.println("// CheckStyle: stop line length check");
            out.println("package " + packageName + ";");
            out.println("");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.println("// Annotated type: " + annotatedType);
            out.println("// Annotation: " + AutomaticallyRegisteredFeatureProcessor.ANNOTATION_CLASS_NAME);
            out.println("// Annotation processor: " + processor.getClass().getName());
            out.println("");
            out.println("import " + SERVICE_REGISTRATION_INTERFACE_NAME + ";");
            out.println("import org.graalvm.nativeimage.Platform;");
            out.println("import org.graalvm.nativeimage.Platforms;");
            out.println("");
            out.println("@Platforms(Platform.HOSTED_ONLY.class)");
            out.println("public final class " + serviceRegistrationImplClassName + " implements " + AbstractProcessor.getSimpleName(SERVICE_REGISTRATION_INTERFACE_NAME) + " {");
            out.println("    @Override");
            out.println("    public String getClassName() {");
            out.println("        return \"" + featureImplementationClassName + "\";");
            out.println("    }");
            out.println("}");
        }

        /* Register the "service registration" class as a service provider. */
        processor.createProviderFile(packageName + "." + serviceRegistrationImplClassName, SERVICE_REGISTRATION_INTERFACE_NAME, originatingElement);
    }
}
