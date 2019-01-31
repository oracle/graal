/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

import org.graalvm.compiler.processor.AbstractProcessor;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id;
import org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpotRepeated;

/**
 * Processor for the {@value #SVM_TO_HOTSPOT_CLASS_NAME} annotation that generates code to push JNI
 * arguments to the stack and make a JNI call corresponding to a
 * {@link org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot.Id}. This helps mitigate
 * bugs where incorrect arguments are pushed for a JNI call. Given the low level nature of
 * {@code org.graalvm.nativeimage.StackValue}, it's very hard to use runtime assertion checking.
 */
@SupportedAnnotationTypes({
                "org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpot",
                "org.graalvm.compiler.truffle.common.hotspot.libgraal.SVMToHotSpotRepeated"})
public class SVMToHotSpotProcessor extends AbstractProcessor {

    private static final String SVM_TO_HOTSPOT_CLASS_NAME = SVMToHotSpot.class.getName();
    private static final String SVM_TO_HOTSPOT_REPEATED_CLASS_NAME = SVMToHotSpotRepeated.class.getName();

    @Override
    public SourceVersion getSupportedSourceVersion() {
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
        final List<Id> ids = new ArrayList<>();
        final Set<Element> originatingElements = new HashSet<>();

        CallsInfo(Element topDeclaringType) {
            this.topDeclaringType = topDeclaringType;
        }
    }

    private static Element topDeclaringType(Element element) {
        Element enclosing = element.getEnclosingElement();
        if (enclosing == null || enclosing.getKind() == ElementKind.PACKAGE) {
            assert element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.INTERFACE;
            return element;
        }
        return topDeclaringType(enclosing);
    }

    private void processElement(ExecutableElement hsCall, Map<Element, CallsInfo> calls) {
        if (processed.contains(hsCall)) {
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
        AnnotationMirror annotation = getAnnotation(hsCall, getType(SVM_TO_HOTSPOT_REPEATED_CLASS_NAME));
        if (annotation != null) {
            annotations = getAnnotationValueList(annotation, "value", AnnotationMirror.class);
        } else {
            annotation = getAnnotation(hsCall, getType(SVM_TO_HOTSPOT_CLASS_NAME));
            if (annotation != null) {
                annotations = Collections.singletonList(annotation);
            } else {
                return;
            }
        }
        for (AnnotationMirror a : annotations) {
            VariableElement annotationValue = getAnnotationValue(a, "value", VariableElement.class);
            String idName = annotationValue.getSimpleName().toString();
            SVMToHotSpot.Id id = Id.valueOf(idName);
            info.ids.add(id);
        }
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
            Set<String> importedCalls = new HashSet<>();
            boolean usesJObject = false;
            for (Id id : info.ids) {
                out.printf("import static %s.%s;%n", id.getClass().getName().replace('$', '.'), id.name());
                Class<?> returnType = id.getReturnType();
                if (!returnType.isPrimitive()) {
                    usesJObject = true;
                }
                for (Class<?> t : id.getParameterTypes()) {
                    if (!t.isPrimitive()) {
                        usesJObject = true;
                    }
                }
                String call = "call" + toJNIType(returnType, true);
                if (importedCalls.add(call)) {
                    out.printf("import static org.graalvm.compiler.truffle.compiler.hotspot.libgraal.SVMToHotSpotUtil.%s;%n", call);
                }
            }
            out.println("");
            out.println("import org.graalvm.nativeimage.StackValue;");
            out.println("import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JNIEnv;");
            out.println("import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JValue;");
            if (usesJObject) {
                out.println("import org.graalvm.compiler.truffle.compiler.hotspot.libgraal.JNI.JObject;");
            }
            out.println("");
            out.printf("final class %s {%n", genClassName);
            for (Id id : info.ids) {
                int p = 0;
                Class<?> rt = id.getReturnType();
                out.println("");
                if (!rt.isPrimitive()) {
                    out.println("    @SuppressWarnings(\"unchecked\")");
                    out.printf("    static <T extends JObject> T call%s(JNIEnv env", id.name());
                } else {
                    out.printf("    static %s call%s(JNIEnv env", toJNIType(rt, false), id.name());
                }
                for (Class<?> t : id.getParameterTypes()) {
                    out.printf(", %s p%d", (t.isPrimitive() ? t.getName() : "JObject"), p);
                    p++;
                }
                out.println(") {");
                out.printf("        JValue args = StackValue.get(%d, JValue.class);%n", id.getParameterTypes().length);
                p = 0;
                for (Class<?> t : id.getParameterTypes()) {
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
                out.printf("        %scall%s(env, %s, args);%n", returnPrefix, toJNIType(rt, true), id.name());
                out.println("    }");
            }
            out.println("}");
        }
    }

    protected PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
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

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        Map<Element, CallsInfo> calls = new HashMap<>();
        for (Element element : roundEnv.getElementsAnnotatedWith(getTypeElement(SVM_TO_HOTSPOT_CLASS_NAME))) {
            processElement((ExecutableElement) element, calls);
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(getTypeElement(SVM_TO_HOTSPOT_REPEATED_CLASS_NAME))) {
            processElement((ExecutableElement) element, calls);
        }

        for (CallsInfo info : calls.values()) {
            createFiles(info);
        }
        return true;
    }
}
