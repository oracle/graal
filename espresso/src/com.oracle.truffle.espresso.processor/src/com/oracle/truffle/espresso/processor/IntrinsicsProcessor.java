/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.processor;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ReferenceType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public abstract class IntrinsicsProcessor extends EspressoProcessor {
    static final String JNI_PACKAGE = "com.oracle.truffle.espresso.jni";
    protected static final String FFI_PACKAGE = "com.oracle.truffle.espresso.ffi";
    private static final String POINTER = FFI_PACKAGE + "." + "Pointer";
    private static final String HANDLE = JNI_PACKAGE + "." + "Handle";

    protected static final String IMPORT_NATIVE_SIGNATURE = "import " + FFI_PACKAGE + "." + "NativeSignature" + ";\n";
    protected static final String IMPORT_NATIVE_TYPE = "import " + FFI_PACKAGE + "." + "NativeType" + ";\n";

    // @Pointer
    TypeElement pointerAnnotation;

    // @Handle
    TypeElement handleAnnotation;

    private final String ENV_NAME;

    public IntrinsicsProcessor(String ENV_NAME, String SUBSTITUTION_PACKAGE, String SUBSTITUTOR, String COLLECTOR, String COLLECTOR_INSTANCE_NAME) {
        super(SUBSTITUTION_PACKAGE, SUBSTITUTOR, COLLECTOR, COLLECTOR_INSTANCE_NAME);
        this.ENV_NAME = ENV_NAME;
    }

    protected void initNfiType() {
        this.pointerAnnotation = processingEnv.getElementUtils().getTypeElement(POINTER);
        this.handleAnnotation = processingEnv.getElementUtils().getTypeElement(HANDLE);
    }

    void getEspressoTypes(ExecutableElement inner, List<String> parameterTypeNames, List<Boolean> referenceTypes) {
        for (VariableElement parameter : inner.getParameters()) {
            if (isActualParameter(parameter)) {
                String arg = parameter.asType().toString();
                String result = extractSimpleType(arg);
                parameterTypeNames.add(result);
                referenceTypes.add((parameter.asType() instanceof ReferenceType));
            }
        }
    }

    /**
     * Converts a parameter/return type into Espresso's NativeType, taking into account @Pointer
     * and @Handle annotations.
     *
     * @param typeMirror type to convert
     * @param element used to report proper error locations
     */
    private NativeType extractNativeType(TypeMirror typeMirror, Element element) {
        AnnotationMirror pointer = getAnnotation(typeMirror, pointerAnnotation);
        AnnotationMirror handle = getAnnotation(typeMirror, handleAnnotation);
        if (pointer != null && handle != null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, String.format("Parameter cannot be be annotated with both %s and %s", pointer, handle), element);
        }
        if (pointer != null) {
            if (typeMirror.getKind().isPrimitive()) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@Pointer annotation must be used only with 'TruffleObject' parameters/return types.", element);
            }
            return NativeType.POINTER;
        } else if (handle != null) {
            if (typeMirror.getKind() != TypeKind.LONG) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "@Handle annotation must be used only with 'long' parameters/return types.", element);
            }
            return NativeType.LONG; // word size
        } else {
            return classToType(typeMirror.getKind());
        }
    }

    NativeType[] jniNativeSignature(ExecutableElement method, boolean prependJniEnv) {
        List<NativeType> signature = new ArrayList<>(16);

        // Return type is always first.
        signature.add(extractNativeType(method.getReturnType(), method));

        // Arguments...

        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        if (prependJniEnv) {
            signature.add(NativeType.POINTER);
        }

        for (VariableElement param : method.getParameters()) {
            if (isActualParameter(param)) {
                signature.add(extractNativeType(param.asType(), param));
            }
        }

        return signature.toArray(new NativeType[0]);
    }

    static String extractArg(int index, String clazz, boolean isNonPrimitive, int startAt, String tabulation) {
        String decl = tabulation + clazz + " " + ARG_NAME + index + " = ";
        String obj = ARGS_NAME + "[" + (index + startAt) + "]";
        if (isNonPrimitive) {
            if (!clazz.equals("StaticObject")) {
                return decl + castTo(obj, clazz) + ";\n";
            }
            return decl + "env.getHandles().get(Math.toIntExact((long) " + obj + "))" + ";\n";
        }
        return decl + castTo(obj, clazz) + ";\n";
    }

    String extractInvocation(String className, String methodName, int nParameters, boolean isStatic, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        if (isStatic) {
            str.append(className).append(".").append(methodName).append("(");
        } else {
            str.append(ENV_NAME).append(".").append(methodName).append("(");
        }
        boolean first = true;
        for (int i = 0; i < nParameters; i++) {
            first = checkFirst(str, first);
            str.append(ARG_NAME).append(i);
        }
        first = appendInvocationMetaInformation(str, first, helper);
        str.append(")"); // ;\n");
        return str.toString();
    }
}
