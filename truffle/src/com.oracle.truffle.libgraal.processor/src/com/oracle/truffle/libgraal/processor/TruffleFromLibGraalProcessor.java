/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.libgraal.processor;

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
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

/**
 * Processor for the {@code jdk.graal.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal}
 * annotation that generates code to push JNI arguments to the stack and make a JNI call
 * corresponding to a
 * {@code jdk.graal.compiler.truffle.common.hotspot.libgraal.TruffleFromLibGraal.Id}. This helps
 * mitigate bugs where incorrect arguments are pushed for a JNI call. Given the low level nature of
 * {@code org.graalvm.nativeimage.StackValue}, it's very hard to use runtime assertion checking.
 */
@SupportedAnnotationTypes({
                "com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal",
                "com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraalRepeated"})
public class TruffleFromLibGraalProcessor extends BaseProcessor {

    /**
     * Captures the info defined by a
     * {@code com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id} enum constant.
     * That is, a method name, return type and parameter types.
     */
    static class Id {

        final String name;
        final List<TypeMirror> parameterTypes;
        final TypeMirror returnType;

        Id(String name, List<TypeMirror> signatureTypes) {
            this.name = name;
            returnType = signatureTypes.get(0);
            parameterTypes = signatureTypes.subList(1, signatureTypes.size());
        }
    }

    /**
     * Allows subclasses to filter out annotated elements to generate calls for.
     */
    protected boolean accept(@SuppressWarnings("unused") ExecutableElement annotatedElement) {
        return true;
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

        // Do not process Truffle runtime sources. The annotations in these
        // classes exist solely to simplify IDE navigation between the end points
        // of libgraal to Truffle runtime calls.
        if (topDeclaringType.asType().toString().contains("truffle.runtime")) {
            return;
        }

        CallsInfo info = calls.get(topDeclaringType);
        if (info == null) {
            info = new CallsInfo(topDeclaringType);
            calls.put(topDeclaringType, info);
        }

        processed.add(hsCall);
        info.originatingElements.add(hsCall);

        List<AnnotationMirror> annotations;
        AnnotationMirror annotation = getAnnotation(hsCall, annotationType);
        if (isRepeatedAnnotation(annotationType)) {
            annotations = getAnnotationValueList(annotation, "value", AnnotationMirror.class);
        } else {
            annotations = Collections.singletonList(annotation);
        }

        TypeMirror signatureAnnotationType = getType("com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Signature");
        for (AnnotationMirror a : annotations) {
            VariableElement annotationValue = getAnnotationValue(a, "value", VariableElement.class);
            String idName = annotationValue.getSimpleName().toString();
            AnnotationMirror signatureAnnotation = getAnnotation(annotationValue, signatureAnnotationType);
            List<TypeMirror> signature = getAnnotationValueList(signatureAnnotation, "value", TypeMirror.class);
            Id id = new Id(idName, signature);
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

    private static String toJNIType(TypeMirror t, boolean uppercasePrimitive) {
        if (isPrimitiveOrVoid(t)) {
            if (!uppercasePrimitive) {
                return t.toString();
            }
            return uppercaseFirst(t.toString());
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
            for (Id id : info.ids) {
                out.printf("import static com.oracle.truffle.compiler.hotspot.libgraal.TruffleFromLibGraal.Id.%s;%n", id.name);
                TypeMirror returnType = id.returnType;
                if (!isPrimitiveOrVoid(returnType)) {
                    usesJObject = true;
                }
                for (TypeMirror t : id.parameterTypes) {
                    if (!isPrimitiveOrVoid(t)) {
                        usesJObject = true;
                    }
                }
            }
            out.println("");
            out.println("import org.graalvm.nativeimage.StackValue;");
            out.println("import org.graalvm.jniutils.JNI.JNIEnv;");
            out.println("import org.graalvm.jniutils.JNI.JValue;");
            if (usesJObject) {
                out.println("import org.graalvm.jniutils.JNI.JObject;");
            }
            out.println("");
            out.printf("final class %s {%n", genClassName);
            for (Id id : info.ids) {
                int p = 0;
                String idName = id.name;
                TypeMirror rt = id.returnType;
                out.println("");
                if (!isPrimitiveOrVoid(rt)) {
                    out.println("    @SuppressWarnings(\"unchecked\")");
                    out.printf("    static <T extends JObject> T call%s(TruffleFromLibGraalCalls calls, JNIEnv env", idName);
                } else {
                    out.printf("    static %s call%s(TruffleFromLibGraalCalls calls, JNIEnv env", toJNIType(rt, false), idName);
                }
                List<TypeMirror> parameterTypes = id.parameterTypes;
                for (TypeMirror t : parameterTypes) {
                    out.printf(", %s p%d", (isPrimitiveOrVoid(t) ? t.toString() : "JObject"), p);
                    p++;
                }
                out.println(") {");
                out.printf("        JValue args = StackValue.get(%d, JValue.class);%n", parameterTypes.size());
                p = 0;
                for (TypeMirror t : parameterTypes) {
                    out.printf("        args.addressOf(%d).set%s(p%d);%n", p, toJNIType(t, true), p);
                    p++;
                }
                String returnPrefix;
                if (!isPrimitiveOrVoid(rt)) {
                    returnPrefix = "return (T) ";
                } else if (rt.getKind() == TypeKind.VOID) {
                    returnPrefix = "";
                } else {
                    returnPrefix = "return ";
                }
                out.printf("        %scalls.call%s(env, %s, args);%n", returnPrefix, toJNIType(rt, true), idName);
                out.println("    }");
            }
            out.println("}");
        }
    }

    private static boolean isPrimitiveOrVoid(TypeMirror returnType) {
        return returnType.getKind() == TypeKind.VOID || returnType instanceof PrimitiveType;
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
