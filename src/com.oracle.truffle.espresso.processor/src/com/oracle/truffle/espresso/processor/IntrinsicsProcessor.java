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

import java.util.List;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ReferenceType;

public abstract class IntrinsicsProcessor extends EspressoProcessor {
    static final String JNI_PACKAGE = "com.oracle.truffle.espresso.jni";
    // private static final String NFI_TYPE = JNI_PACKAGE + "." + "NFIType";
    private static final String POINTER = JNI_PACKAGE + "." + "Pointer";
    private static final String HANDLE = JNI_PACKAGE + "." + "Handle";

    // @Pointer
    TypeElement pointer;

    // @Handle
    TypeElement handle;

    private final String ENV_NAME;

    public IntrinsicsProcessor(String ENV_NAME, String SUBSTITUTION_PACKAGE, String SUBSTITUTOR, String COLLECTOR, String COLLECTOR_INSTANCE_NAME) {
        super(SUBSTITUTION_PACKAGE, SUBSTITUTOR, COLLECTOR, COLLECTOR_INSTANCE_NAME);
        this.ENV_NAME = ENV_NAME;
    }

    protected void initNfiType() {
        this.pointer = processingEnv.getElementUtils().getTypeElement(POINTER);
        this.handle = processingEnv.getElementUtils().getTypeElement(HANDLE);
    }

    static void getEspressoTypes(ExecutableElement inner, List<String> parameterTypeNames, List<Boolean> referenceTypes) {
        for (VariableElement parameter : inner.getParameters()) {
            String arg = parameter.asType().toString();
            String result = extractSimpleType(arg);
            parameterTypeNames.add(result);
            referenceTypes.add((parameter.asType() instanceof ReferenceType));
        }
    }

    String jniNativeSignature(ExecutableElement method, String returnType, boolean isJni) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        boolean first = true;
        if (isJni) {
            sb.append(NativeSimpleType.POINTER);
            first = false;
        }
        for (VariableElement param : method.getParameters()) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            // Override NFI type.
            AnnotationMirror ptr = getAnnotation(param.asType(), pointer);
            AnnotationMirror hdl = getAnnotation(param.asType(), handle);
            if (ptr != null) {
                sb.append(NativeSimpleType.POINTER);
            } else if (hdl != null) {
                sb.append(NativeSimpleType.SINT64);
            } else {
                sb.append(classToType(param.asType().toString()));
            }
        }

        sb.append("): ");

        AnnotationMirror ptr = getAnnotation(method.getReturnType(), pointer);
        AnnotationMirror hdl = getAnnotation(method.getReturnType(), handle);
        if (ptr != null) {
            sb.append(NativeSimpleType.POINTER);
        } else if (hdl != null) {
            sb.append(NativeSimpleType.SINT64);
        } else {
            sb.append(classToType(returnType));
        }
        return sb.toString();
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
        switch (clazz) {
            case "boolean":
                return decl + "(" + castTo(obj, "byte") + ") != 0;\n";
            case "char":
                return decl + castTo(castTo(obj, "short"), "char") + ";\n";
            default:
                return decl + castTo(obj, clazz) + ";\n";
        }
    }

    String extractInvocation(String className, String methodName, int nParameters, boolean isStatic) {
        StringBuilder str = new StringBuilder();
        if (isStatic) {
            str.append(className).append(".").append(methodName).append("(");
        } else {
            str.append(ENV_NAME).append(".").append(methodName).append("(");
        }
        boolean notFirst = false;
        for (int i = 0; i < nParameters; i++) {
            if (notFirst) {
                str.append(", ");
            } else {
                notFirst = true;
            }
            str.append(ARG_NAME).append(i);
        }
        str.append(")"); // ;\n");
        return str.toString();
    }

}
