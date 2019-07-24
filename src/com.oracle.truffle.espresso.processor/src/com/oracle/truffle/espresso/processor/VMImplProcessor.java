/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.PrimitiveType;

public class VMImplProcessor extends EspressoProcessor {
    // Annotations

    // @VmImpl
    private TypeElement vmImpl;
    // @JniImpl
    private TypeElement jniImpl;
    // @NFIType
    private TypeElement nfiType;
    // @NFIType.value()
    private ExecutableElement nfiTypeValueElement;

    // Processor-specific constants

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.vm";
    private static final String JNI_PACKAGE = "com.oracle.truffle.espresso.jni";

    private static final String VM_IMPL = SUBSTITUTION_PACKAGE + "." + "VmImpl";
    private static final String JNI_IMPL = JNI_PACKAGE + "." + "JniImpl";
    private static final String NFI_TYPE = JNI_PACKAGE + "." + "NFIType";

    private static final String VM = "VM";
    private static final String VM_NAME = "env";
    private static final String IMPORT_VM = "import " + SUBSTITUTION_PACKAGE + "." + VM + ";\n";

    private static final String SUBSTITUTOR = "VMSubstitutor";
    private static final String COLLECTOR = "VMCollector";
    private static final String COLLECTOR_INSTANCE_NAME = "vmCollector";

    private static final String INVOKE = "invoke(" + VM + " " + VM_NAME + ", Object[] " + ARGS_NAME + ") {\n";

    public VMImplProcessor() {
        super(SUBSTITUTION_PACKAGE, SUBSTITUTOR, COLLECTOR, COLLECTOR_INSTANCE_NAME);
    }

    static class VMHelper extends SubstitutionHelper {
        final String jniNativeSignature;
        final List<Boolean> nonPrimitives;
        final String returnType;
        final boolean isStatic;
        final boolean isJni;

        public VMHelper(String jniNativeSignature, List<Boolean> nonPrimitives, String returnType, boolean isStatic, boolean isJni) {
            this.jniNativeSignature = jniNativeSignature;
            this.nonPrimitives = nonPrimitives;
            this.returnType = returnType;
            this.isStatic = isStatic;
            this.isJni = isJni;
        }
    }

    private void processElement(Element method) {
        assert method.getKind() == ElementKind.METHOD;
        ExecutableElement jniMethod = (ExecutableElement) method;
        assert jniMethod.getEnclosingElement().getKind() == ElementKind.CLASS;
        TypeElement declaringClass = (TypeElement) jniMethod.getEnclosingElement();
        if (declaringClass.getQualifiedName().toString().equals(SUBSTITUTION_PACKAGE + "." + VM)) {
            String className = VM;
            // Extract the class name.
            // Obtain the name of the method to be substituted in.
            String targetMethodName = jniMethod.getSimpleName().toString();
            // Obtain the host types of the parameters
            List<String> espressoTypes = new ArrayList<>();
            List<Boolean> nonPrimitives = new ArrayList<>();
            getEspressoTypes(jniMethod, espressoTypes, nonPrimitives);
            // Spawn the name of the Substitutor we will create.
            String substitutorName = getSubstitutorClassName(className, targetMethodName, espressoTypes);
            if (!classes.contains(substitutorName)) {
                // Obtain the fully qualified guest return type of the method.
                String returnType = extractReturnType(jniMethod);
                // Check if this VM method has the @JniImpl annotation
                boolean isJni = getAnnotation(jniMethod, jniImpl) != null;
                // Obtain the jniNativeSignature
                String jniNativeSignature = jniNativeSignature(jniMethod, returnType, isJni);
                // Check if we need to call an instance method
                boolean isStatic = jniMethod.getModifiers().contains(Modifier.STATIC);
                // Spawn helper
                VMHelper h = new VMHelper(jniNativeSignature, nonPrimitives, returnType, isStatic, isJni);
                // Create the contents of the source file
                String classFile = spawnSubstitutor(className, targetMethodName, espressoTypes, h);
                commitSubstitution(jniMethod, substitutorName, classFile);
            }
        }
    }

    private static String extractArg(int index, String clazz, boolean isNonPrimitive, int startAt, String tabulation) {
        String decl = tabulation + clazz + " " + ARG_NAME + index + " = ";
        String obj = ARGS_NAME + "[" + (index + startAt) + "]";
        if (isNonPrimitive) {
            return decl + genIsNull(obj) + " ? " + (clazz.equals("StaticObject") ? STATIC_OBJECT_NULL : "null") + " : " + castTo(obj, clazz) + ";\n";
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

    private static String extractInvocation(String className, String methodName, int nParameters, boolean isStatic) {
        StringBuilder str = new StringBuilder();
        if (isStatic) {
            str.append(className).append(".").append(methodName).append("(");
        } else {
            str.append(VM_NAME).append(".").append(methodName).append("(");
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
        str.append(");\n");
        return str.toString();
    }

    private static void getEspressoTypes(ExecutableElement inner, List<String> parameterTypeNames, List<Boolean> nonPrimitives) {
        for (VariableElement parameter : inner.getParameters()) {
            String arg = parameter.asType().toString();
            String result = extractSimpleType(arg);
            parameterTypeNames.add(result);
            nonPrimitives.add(!(parameter.asType() instanceof PrimitiveType));
        }
    }

    public String jniNativeSignature(ExecutableElement method, String returnType, boolean isJni) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        boolean first = true;
        if (isJni) {
            sb.append(NativeSimpleType.SINT64);
            first = false;
        }
        for (VariableElement param : method.getParameters()) {
            if (!first) {
                sb.append(", ");
            } else {
                first = false;
            }

            // Override NFI type.
            AnnotationMirror nfi = getAnnotation(param.asType(), nfiType);
            if (nfi != null) {
                AnnotationValue value = nfi.getElementValues().get(nfiTypeValueElement);
                if (value != null) {
                    sb.append(NativeSimpleType.valueOf(((String) value.getValue()).toUpperCase()));
                } else {
                    sb.append(classToType(param.asType().toString(), false));
                }
            } else {
                sb.append(classToType(param.asType().toString(), false));
            }
        }
        sb.append("): ").append(classToType(returnType, true));
        return sb.toString();
    }

    @Override
    void processImpl(RoundEnvironment env) {
        // Set up the different annotations, along with their values, that we will need.
        this.vmImpl = processingEnv.getElementUtils().getTypeElement(VM_IMPL);
        this.jniImpl = processingEnv.getElementUtils().getTypeElement(JNI_IMPL);
        this.nfiType = processingEnv.getElementUtils().getTypeElement(NFI_TYPE);
        for (Element e : nfiType.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("value")) {
                    this.nfiTypeValueElement = (ExecutableElement) e;
                }
            }
        }
        // Actual work
        for (Element e : env.getElementsAnnotatedWith(vmImpl)) {
            processElement(e);
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(VM_IMPL);
        return annotations;
    }

    @Override
    String generateImports(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        VMHelper h = (VMHelper) helper;
        str.append(IMPORT_VM);
        if (h.nonPrimitives.contains(true)) {
            str.append(IMPORT_INTEROP_LIBRARY);
        }
        if (parameterTypeName.contains("StaticObject") || h.returnType.equals("void")) {
            str.append(IMPORT_STATIC_OBJECT);
        }
        if (parameterTypeName.contains("TruffleObject")) {
            str.append(IMPORT_TRUFFLE_OBJECT);
        }
        str.append("\n");
        return str.toString();
    }

    @Override
    String generateConstructor(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        VMHelper h = (VMHelper) helper;
        str.append(TAB_1).append("private ").append(className).append("() {\n");
        str.append(TAB_2).append("super(\n");
        str.append(TAB_3).append(generateString(targetMethodName)).append(",\n");
        str.append(TAB_3).append(generateString(h.jniNativeSignature)).append(",\n");
        str.append(TAB_3).append(parameterTypeName.size()).append(",\n");
        str.append(TAB_3).append(generateString(h.returnType)).append(",\n");
        str.append(TAB_3).append(h.isJni).append("\n");
        str.append(TAB_2).append(");\n");
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    @Override
    String generateInvoke(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        VMHelper h = (VMHelper) helper;
        str.append(TAB_1).append(PUBLIC_FINAL_OBJECT).append(INVOKE);
        int argIndex = 0;
        for (String type : parameterTypeName) {
            boolean isNonPrimitive = h.nonPrimitives.get(argIndex);
            str.append(extractArg(argIndex++, type, isNonPrimitive, h.isJni ? 1 : 0, TAB_2));
        }
        switch (h.returnType) {
            case "char":
                str.append(TAB_2).append("return ").append("(short) ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic));
                break;
            case "boolean":
                str.append(TAB_2).append("boolean b = ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic));
                str.append(TAB_2).append("return b ? (byte) 1 : (byte) 0;\n");
                break;
            case "void":
                str.append(TAB_2).append(extractInvocation(className, targetMethodName, argIndex, h.isStatic));
                str.append(TAB_2).append("return ").append(STATIC_OBJECT_NULL).append(";\n");
                break;
            default:
                str.append(TAB_2).append("return ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic));
        }
        str.append(TAB_1).append("}\n");
        str.append("}");
        return str.toString();
    }
}