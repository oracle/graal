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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;

import org.graalvm.compiler.processor.AbstractProcessor;
import org.graalvm.libgraal.jni.annotation.FromLibGraalId;

/**
 * Base class for an annotation processor that generates code to push JNI arguments to the stack and
 * make a JNI call corresponding to a {@link FromLibGraalId}. This helps mitigate bugs where
 * incorrect arguments are pushed for a JNI call. Given the low level nature of
 * {@code org.graalvm.nativeimage.StackValue}, it's very hard to use runtime assertion checking.
 */
public abstract class AbstractFromLibGraalProcessor<T extends Enum<T> & FromLibGraalId> extends AbstractProcessor {

    private final Class<T> idClass;

    protected AbstractFromLibGraalProcessor(Class<T> idClass) {
        Objects.requireNonNull(idClass);
        this.idClass = idClass;
    }

    /**
     * Allows subclasses to filter out annotated elements to generate calls for.
     */
    protected boolean accept(@SuppressWarnings("unused") ExecutableElement annotatedElement) {
        return true;
    }

    @Override
    public final SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private final Set<ExecutableElement> processed = new HashSet<>();

    /**
     * The calls made in a single Java source file.
     */
    static class CallsInfo {

        /**
         * The top level type declared in the source file.
         */
        final Element topDeclaringType;

        /**
         * The identifiers for the calls made in the source file.
         */
        final List<FromLibGraalId> ids = new ArrayList<>();
        final Set<Element> originatingElements = new HashSet<>();

        CallsInfo(Element topDeclaringType) {
            this.topDeclaringType = topDeclaringType;
        }
    }

    static TypeElement topDeclaringType(Element element) {
        Element enclosing = element.getEnclosingElement();
        if (enclosing == null || enclosing.getKind() == ElementKind.PACKAGE) {
            assert element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE;
            return (TypeElement) element;
        }
        return topDeclaringType(enclosing);
    }

    private void processElement(ExecutableElement hsCall, DeclaredType annotationType, Map<Element, CallsInfo> calls) {
        if (processed.contains(hsCall) || !accept(hsCall)) {
            return;
        }

        Element topDeclaringType = topDeclaringType(hsCall);
        CallsInfo info = calls.get(topDeclaringType);
        if (info == null) {
            info = new CallsInfo(topDeclaringType);
            calls.put(topDeclaringType, info);
        }

        processed.add(hsCall);

        List<AnnotationMirror> annotations;
        AnnotationMirror annotation = getAnnotation(hsCall, annotationType);
        if (isRepeatedAnnotation(annotationType)) {
            annotations = getAnnotationValueList(annotation, "value", AnnotationMirror.class);
        } else {
            annotations = Collections.singletonList(annotation);
        }

        for (AnnotationMirror a : annotations) {
            VariableElement annotationValue = getAnnotationValue(a, "value", VariableElement.class);
            String idName = annotationValue.getSimpleName().toString();
            FromLibGraalId id = Enum.valueOf(idClass, idName);
            info.ids.add(id);
        }
    }

    private boolean isRepeatedAnnotation(DeclaredType annotationType) {
        Name valueName = processingEnv.getElementUtils().getName("value");
        for (ExecutableElement method : ElementFilter.methodsIn(annotationType.asElement().getEnclosedElements())) {
            if (valueName.equals(method.getSimpleName())) {
                return method.getReturnType().getKind() == TypeKind.ARRAY;
            }
        }
        return false;
    }

    private void createFiles(CallsInfo info) {
        String pkg = ((PackageElement) info.topDeclaringType.getEnclosingElement()).getQualifiedName().toString();
        Name topDeclaringClass = info.topDeclaringType.getSimpleName();
        Element[] originatingElements = info.originatingElements.toArray(new Element[info.originatingElements.size()]);

        createGenSource(info, pkg, topDeclaringClass, originatingElements);
    }

    private static String uppercaseFirst(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static String toJNIType(Class<?> t, boolean uppercasePrimitive) {
        if (t.isPrimitive()) {
            if (!uppercasePrimitive) {
                return t.getName();
            }
            return uppercaseFirst(t.getName());
        }
        return "JObject";
    }

    private void createGenSource(CallsInfo info, String pkg, Name topDeclaringClass, Element[] originatingElements) {
        String genClassName = topDeclaringClass + "Gen";

        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(pkg, genClassName, filer, originatingElements)) {

            out.println("// CheckStyle: stop header check");
            out.println("// CheckStyle: stop line length check");
            out.println("// GENERATED CONTENT - DO NOT EDIT");
            out.printf("// Source: %s.java%n", topDeclaringClass);
            out.printf("// Generated-by: %s%n", getClass().getName());
            out.println("package " + pkg + ";");
            out.println("");
            boolean usesJObject = false;
            for (FromLibGraalId id : info.ids) {
                out.printf("import static %s.%s;%n", id.getClass().getName().replace('$', '.'), id.getName());
                Class<?> returnType = id.getReturnType();
                if (!returnType.isPrimitive()) {
                    usesJObject = true;
                }
                for (Class<?> t : id.getParameterTypes()) {
                    if (!t.isPrimitive()) {
                        usesJObject = true;
                    }
                }
            }
            out.println("");
            out.println("import org.graalvm.nativeimage.StackValue;");
            out.println("import org.graalvm.libgraal.jni.JNI.JNIEnv;");
            out.println("import org.graalvm.libgraal.jni.JNI.JValue;");
            if (usesJObject) {
                out.println("import org.graalvm.libgraal.jni.JNI.JObject;");
            }
            out.println("");
            out.printf("final class %s {%n", genClassName);
            for (FromLibGraalId id : info.ids) {
                int p = 0;
                String idName = id.getName();
                Class<?> rt = id.getReturnType();
                out.println("");
                if (!rt.isPrimitive()) {
                    out.println("    @SuppressWarnings(\"unchecked\")");
                    out.printf("    static <T extends JObject> T call%s(JNIEnv env", idName);
                } else {
                    out.printf("    static %s call%s(JNIEnv env", toJNIType(rt, false), idName);
                }
                Class<?>[] parameterTypes = id.getParameterTypes();
                for (Class<?> t : parameterTypes) {
                    out.printf(", %s p%d", (t.isPrimitive() ? t.getName() : "JObject"), p);
                    p++;
                }
                out.println(") {");
                out.printf("        JValue args = StackValue.get(%d, JValue.class);%n", parameterTypes.length);
                p = 0;
                for (Class<?> t : parameterTypes) {
                    out.printf("        args.addressOf(%d).set%s(p%d);%n", p, toJNIType(t, true), p);
                    p++;
                }
                String returnPrefix;
                if (!rt.isPrimitive()) {
                    returnPrefix = "return (T) ";
                } else if (rt == void.class) {
                    returnPrefix = "";
                } else {
                    returnPrefix = "return ";
                }
                out.printf("        %s%s.INSTANCE.call%s(env, %s, args);%n", returnPrefix, getAccessorClassSimpleName(), toJNIType(rt, true), idName);
                out.println("    }");
            }
            out.println("}");
        }
    }

    static PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            // Ensure Unix line endings to comply with code style guide checked by Checkstyle
            JavaFileObject sourceFile = filer.createSourceFile(pkg + "." + relativeName, originatingElements);
            return new PrintWriter(sourceFile.openWriter()) {
                @Override
                public void println() {
                    print("\n");
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getAccessorClassSimpleName() {
        Class<?> topLevel = null;
        for (Class<?> current = idClass; current != null; current = current.getEnclosingClass()) {
            topLevel = current;
        }
        return String.format("%sCalls", topLevel.getSimpleName());
    }

    @Override
    public final boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        Map<Element, CallsInfo> calls = new HashMap<>();
        for (TypeElement supportedAnnotationElement : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(supportedAnnotationElement)) {
                processElement((ExecutableElement) element, (DeclaredType) supportedAnnotationElement.asType(), calls);
            }
        }

        for (CallsInfo info : calls.values()) {
            createFiles(info);
        }
        return true;
    }
}
