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
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

public class JniImplProcessor extends IntrinsicsProcessor {
    // @JniImpl
    private TypeElement jniImpl;

    // region Various String constants.

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.jni";

    private static final String JNI_IMPL = SUBSTITUTION_PACKAGE + "." + "JniImpl";
    private static final String NFI_TYPE = SUBSTITUTION_PACKAGE + "." + "NFIType";

    private static final String JNI_ENV = "JniEnv";
    private static final String ENV_NAME = "env";
    private static final String IMPORT_JNI_ENV = "import " + SUBSTITUTION_PACKAGE + "." + JNI_ENV + ";\n";

    private static final String SUBSTITUTOR = "JniSubstitutor";
    private static final String COLLECTOR = "JniCollector";
    private static final String COLLECTOR_INSTANCE_NAME = "jniCollector";

    private static final String INVOKE = "invoke(" + JNI_ENV + " " + ENV_NAME + ", Object[] " + ARGS_NAME + ") {\n";

    public JniImplProcessor() {
        super(ENV_NAME, SUBSTITUTION_PACKAGE, SUBSTITUTOR, COLLECTOR, COLLECTOR_INSTANCE_NAME);
    }

    static class JniHelper extends SubstitutionHelper {
        final String jniNativeSignature;
        final List<Boolean> referenceTypes;
        final String returnType;
        final boolean isStatic;

        public JniHelper(EspressoProcessor processor, ExecutableElement method, String jniNativeSignature, List<Boolean> referenceTypes, String returnType, boolean isStatic) {
            super(processor, method);
            this.jniNativeSignature = jniNativeSignature;
            this.referenceTypes = referenceTypes;
            this.returnType = returnType;
            this.isStatic = isStatic;
        }
    }

    private void processElement(Element method) {
        assert method.getKind() == ElementKind.METHOD;
        ExecutableElement jniMethod = (ExecutableElement) method;
        assert jniMethod.getEnclosingElement().getKind() == ElementKind.CLASS;
        TypeElement declaringClass = (TypeElement) jniMethod.getEnclosingElement();
        if (declaringClass.getQualifiedName().toString().equals(SUBSTITUTION_PACKAGE + "." + JNI_ENV)) {
            String className = JNI_ENV;
            // Extract the class name.
            // Obtain the name of the method to be substituted in.
            String targetMethodName = jniMethod.getSimpleName().toString();
            // Obtain the host types of the parameters
            List<String> espressoTypes = new ArrayList<>();
            List<Boolean> referenceTypes = new ArrayList<>();
            getEspressoTypes(jniMethod, espressoTypes, referenceTypes);
            // Spawn the name of the Substitutor we will create.
            String substitutorName = getSubstitutorClassName(className, targetMethodName, espressoTypes);
            if (!classes.contains(substitutorName)) {
                // Obtain the fully qualified guest return type of the method.
                String returnType = extractReturnType(jniMethod);
                // Obtain the jniNativeSignature
                String jniNativeSignature = jniNativeSignature(jniMethod, returnType, true);
                // Check if we need to call an instance method
                boolean isStatic = jniMethod.getModifiers().contains(Modifier.STATIC);
                // Spawn helper
                JniHelper helper = new JniHelper(this, (ExecutableElement) method, jniNativeSignature, referenceTypes, returnType, isStatic);
                // Create the contents of the source file
                String classFile = spawnSubstitutor(
                                className,
                                targetMethodName,
                                espressoTypes,
                                helper);
                commitSubstitution(jniMethod, substitutorName, classFile);
            }
        }
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(JNI_IMPL);
        return annotations;
    }

    @Override
    void processImpl(RoundEnvironment env) {
        // Set up the different annotations, along with their values, that we will need.
        this.jniImpl = processingEnv.getElementUtils().getTypeElement(JNI_IMPL);
        this.nfiType = processingEnv.getElementUtils().getTypeElement(NFI_TYPE);
        for (Element e : nfiType.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals("value")) {
                    this.nfiTypeValueElement = (ExecutableElement) e;
                }
            }
        }
        for (Element e : env.getElementsAnnotatedWith(jniImpl)) {
            processElement(e);
        }
    }

    @Override
    String generateImports(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        JniHelper h = (JniHelper) helper;
        str.append(IMPORT_JNI_ENV);
        if (parameterTypeName.contains("String")) {
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
    String generateFactoryConstructorBody(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        JniHelper h = (JniHelper) helper;
        str.append(TAB_3).append("super(\n");
        str.append(TAB_4).append(generateString(targetMethodName)).append(",\n");
        str.append(TAB_4).append(generateString(h.jniNativeSignature)).append(",\n");
        str.append(TAB_4).append(parameterTypeName.size()).append(",\n");
        str.append(TAB_4).append(generateString(h.returnType)).append("\n");
        str.append(TAB_3).append(");\n");
        str.append(TAB_2).append("}\n");
        return str.toString();
    }

    @Override
    String generateInvoke(String className, String targetMethodName, List<String> parameterTypes, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        JniHelper h = (JniHelper) helper;
        str.append(TAB_1).append(PUBLIC_FINAL_OBJECT).append(INVOKE);
        int argIndex = 0;
        for (String type : parameterTypes) {
            boolean isNonPrimitive = h.referenceTypes.get(argIndex);
            str.append(extractArg(argIndex++, type, isNonPrimitive, 1, TAB_2));
        }
        switch (h.returnType) {
            case "char":
                str.append(TAB_2).append("return ").append("(short) ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper)).append(";\n");
                break;
            case "boolean":
                str.append(TAB_2).append("boolean b = ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper)).append(";\n");
                str.append(TAB_2).append("return b ? (byte) 1 : (byte) 0;\n");
                break;
            case "void":
                str.append(TAB_2).append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper)).append(";\n");
                str.append(TAB_2).append("return ").append(STATIC_OBJECT_NULL).append(";\n");
                break;
            case "StaticObject":
                str.append(TAB_2).append("return ").append(
                                "(long) env.getHandles().createLocal(" + extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper) + ")").append(";\n");
                break;
            default:
                str.append(TAB_2).append("return ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper)).append(";\n");
        }
        str.append(TAB_1).append("}\n");
        str.append("}");
        return str.toString();
    }
}
