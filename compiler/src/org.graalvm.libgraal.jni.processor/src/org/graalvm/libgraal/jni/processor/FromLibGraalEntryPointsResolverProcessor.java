/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.libgraal.jni.processor;

import static org.graalvm.libgraal.jni.processor.AbstractFromLibGraalProcessor.topDeclaringType;
import static org.graalvm.libgraal.jni.processor.AbstractFromLibGraalProcessor.createSourceFile;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import org.graalvm.compiler.processor.AbstractProcessor;
import org.graalvm.libgraal.jni.annotation.FromLibGraalEntryPointsResolver;
import org.graalvm.libgraal.jni.annotation.FromLibGraalId;

/**
 * Processor for the {@link FromLibGraalEntryPointsResolver} annotation that generates
 * {@code FromLibGraalCalls} subclass for given id type.
 */
@SupportedAnnotationTypes("org.graalvm.libgraal.jni.annotation.FromLibGraalEntryPointsResolver")
public final class FromLibGraalEntryPointsResolverProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        TypeElement resolverElement = getTypeElement(FromLibGraalEntryPointsResolver.class.getName());
        for (Element element : roundEnv.getElementsAnnotatedWith(resolverElement)) {
            try {
                processElement(element, (DeclaredType) resolverElement.asType());
            } catch (ProcessingException e) {
                processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), e.element);
            }
        }
        return true;
    }

    private void processElement(Element annotatedElement, DeclaredType annotationType) throws ProcessingException {
        AnnotationMirror annotation = getAnnotation(annotatedElement, annotationType);
        String entryPointsClassName = getAnnotationValue(annotation, "entryPointsClassName", String.class);
        TypeMirror idType = getAnnotationValue(annotation, "value", TypeMirror.class);
        TypeMirror fromLibGraalId = getTypeElement(FromLibGraalId.class.getName()).asType();
        if (!processingEnv.getTypeUtils().isSubtype(idType, fromLibGraalId)) {
            throw new ProcessingException(annotatedElement, "The %s must implement %s.", ((DeclaredType) idType).asElement().getSimpleName(),
                            ((DeclaredType) fromLibGraalId).asElement().getSimpleName());
        }
        boolean method = annotatedElement.getKind() == ElementKind.METHOD;
        boolean requiresJNIEnv = false;
        if (method) {
            if (!entryPointsClassName.isEmpty()) {
                throw new ProcessingException(annotatedElement, "The FromLibGraalEntryPointsResolver on method cannot have entryPointsClassName.");
            }
            ExecutableElement methodElement = (ExecutableElement) annotatedElement;
            if (!methodElement.getModifiers().contains(Modifier.STATIC)) {
                throw new ProcessingException(annotatedElement, "Method %s must be static.", methodElement.getSimpleName());
            }
            if (methodElement.getModifiers().contains(Modifier.PRIVATE)) {
                throw new ProcessingException(annotatedElement, "Method %s cannot be private.", methodElement.getSimpleName());
            }
            if (!verifyParameters(methodElement)) {
                throw new ProcessingException(annotatedElement, "Method %s can have either a single JNIEnv parameter or no parameters.", methodElement.getSimpleName());
            }
            TypeMirror jclass = getTypeElement("org.graalvm.libgraal.jni.JNI.JClass").asType();
            if (!processingEnv.getTypeUtils().isSameType(methodElement.getReturnType(), jclass)) {
                throw new ProcessingException(annotatedElement, "Method %s must return JClass.", methodElement.getSimpleName());
            }
            requiresJNIEnv = !methodElement.getParameters().isEmpty();
        } else {
            if (entryPointsClassName.isEmpty()) {
                throw new ProcessingException(annotatedElement, "The FromLibGraalEntryPointsResolver on class or package must have entryPointsClassName.");
            }
        }
        TypeElement topEnclosingElement = topDeclaringType(annotatedElement);
        String pkg = ((PackageElement) topEnclosingElement.getEnclosingElement()).getQualifiedName().toString();
        TypeElement idElement = (TypeElement) ((DeclaredType) idType).asElement();
        TypeElement idTopEncloingElement = topDeclaringType(idElement);
        String simpleName = String.format("%sCalls", idTopEncloingElement.getSimpleName());
        try (PrintWriter out = createSourceFile(pkg, simpleName, processingEnv.getFiler(), annotatedElement)) {
            out.println("// CheckStyle: stop header check");
            out.println("// CheckStyle: stop line length check");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.printf("// Source: %s.java%n", topEnclosingElement.getQualifiedName());
            out.printf("// Generated-by: %s%n", getClass().getName());
            out.println("package " + pkg + ";");
            out.println("");
            if (!pkg.equals("org.graalvm.libgraal.jni")) {
                out.println("import org.graalvm.libgraal.jni.FromLibGraalCalls;");
            }
            out.println("import org.graalvm.libgraal.jni.JNI.JClass;");
            out.println("import org.graalvm.libgraal.jni.JNI.JNIEnv;");
            out.printf("import %s;%n", idElement.getQualifiedName());
            out.println("");
            out.printf("class %s extends FromLibGraalCalls<%s> {%n", simpleName, idElement.getSimpleName());
            out.println("");
            out.printf("    static final %s INSTANCE = new %s();%n", simpleName, simpleName);
            out.println("");
            out.printf("    private %s() {%n", simpleName);
            out.printf("        super(%s.class);%n", idElement.getSimpleName());
            out.println("    }");
            out.println("");
            out.println("    @Override");
            out.println("    protected JClass resolvePeer(JNIEnv env) {");
            if (method) {
                out.printf("        return %s.%s(%s);%n",
                                annotatedElement.getEnclosingElement().getSimpleName(),
                                annotatedElement.getSimpleName(),
                                requiresJNIEnv ? "env" : "");
            } else {
                out.printf("        return getJNIClass(env, \"%s\");%n", entryPointsClassName);
            }
            out.println("    }");
            out.println("}");
        }
    }

    private boolean verifyParameters(ExecutableElement method) {
        List<? extends VariableElement> params = method.getParameters();
        if (params.isEmpty()) {
            return true;
        }
        if (params.size() == 1) {
            VariableElement param = params.get(0);
            TypeMirror expectedType = processingEnv.getElementUtils().getTypeElement("org.graalvm.libgraal.jni.JNI.JNIEnv").asType();
            TypeMirror actualType = param.asType();
            return processingEnv.getTypeUtils().isSameType(expectedType, actualType);
        }
        return false;
    }

    @SuppressWarnings("serial")
    private static final class ProcessingException extends Exception {
        final Element element;

        ProcessingException(Element element, String message, Object... params) {
            super(String.format(message, params));
            this.element = element;
        }
    }
}
