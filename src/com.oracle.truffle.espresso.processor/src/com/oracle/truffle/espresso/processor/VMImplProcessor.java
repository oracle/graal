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

public class VMImplProcessor extends IntrinsicsProcessor {
    // Annotations

    // @VmImpl
    private TypeElement vmImpl;
    // @JniImpl
    private TypeElement jniImpl;

    // Processor-specific constants

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.vm";

    private static final String VM_IMPL = SUBSTITUTION_PACKAGE + "." + "VmImpl";
    private static final String JNI_IMPL = JNI_PACKAGE + "." + "JniImpl";

    private static final String VM = "VM";
    private static final String VM_NAME = "env";
    private static final String IMPORT_VM = "import " + SUBSTITUTION_PACKAGE + "." + VM + ";\n";

    private static final String SUBSTITUTOR = "VMSubstitutor";
    private static final String COLLECTOR = "VMCollector";
    private static final String COLLECTOR_INSTANCE_NAME = "vmCollector";

    private static final String INVOKE = "invoke(" + VM + " " + VM_NAME + ", Object[] " + ARGS_NAME + ") {\n";

    public VMImplProcessor() {
        super(VM_NAME, SUBSTITUTION_PACKAGE, SUBSTITUTOR, COLLECTOR, COLLECTOR_INSTANCE_NAME);
    }

    static class VMHelper extends SubstitutionHelper {
        final String jniNativeSignature;
        final List<Boolean> referenceTypes;
        final String returnType;
        final boolean isStatic;
        final boolean isJni;

        public VMHelper(String jniNativeSignature, List<Boolean> referenceTypes, String returnType, boolean isStatic, boolean isJni) {
            this.jniNativeSignature = jniNativeSignature;
            this.referenceTypes = referenceTypes;
            this.returnType = returnType;
            this.isStatic = isStatic;
            this.isJni = isJni;
        }
    }

    private void processElement(Element method) {
        assert method.getKind() == ElementKind.METHOD;
        ExecutableElement VMmethod = (ExecutableElement) method;
        assert VMmethod.getEnclosingElement().getKind() == ElementKind.CLASS;
        TypeElement declaringClass = (TypeElement) VMmethod.getEnclosingElement();
        if (declaringClass.getQualifiedName().toString().equals(SUBSTITUTION_PACKAGE + "." + VM)) {
            String className = VM;
            // Extract the class name.
            // Obtain the name of the method to be substituted in.
            String targetMethodName = VMmethod.getSimpleName().toString();
            // Obtain the host types of the parameters
            List<String> espressoTypes = new ArrayList<>();
            List<Boolean> referenceTypes = new ArrayList<>();
            getEspressoTypes(VMmethod, espressoTypes, referenceTypes);
            List<String> guestCalls = getGuestCalls(VMmethod);
            // Spawn the name of the Substitutor we will create.
            String substitutorName = getSubstitutorClassName(className, targetMethodName, espressoTypes);
            if (!classes.contains(substitutorName)) {
                // Obtain the fully qualified guest return type of the method.
                String returnType = extractReturnType(VMmethod);
                // Check if this VM method has the @JniImpl annotation
                boolean isJni = getAnnotation(VMmethod, jniImpl) != null;
                // Obtain the jniNativeSignature
                String jniNativeSignature = jniNativeSignature(VMmethod, returnType, isJni);
                // Check if we need to call an instance method
                boolean isStatic = VMmethod.getModifiers().contains(Modifier.STATIC);
                // Spawn helper
                VMHelper h = new VMHelper(jniNativeSignature, referenceTypes, returnType, isStatic, isJni);
                // Create the contents of the source file
                String classFile = spawnSubstitutor(
                                className,
                                targetMethodName,
                                espressoTypes,
                                guestCalls,
                                hasMetaInjection(VMmethod),
                                h);
                commitSubstitution(VMmethod, substitutorName, classFile);
            }
        }
    }

    @Override
    void processImpl(RoundEnvironment env) {
        // Set up the different annotations, along with their values, that we will need.
        this.vmImpl = processingEnv.getElementUtils().getTypeElement(VM_IMPL);
        this.jniImpl = processingEnv.getElementUtils().getTypeElement(JNI_IMPL);
        initNfiType();
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
    String generateImports(String className, String targetMethodName, List<String> parameterTypeName, List<String> guestCalls, boolean hasMetaInjection, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        VMHelper h = (VMHelper) helper;
        str.append(IMPORT_VM);
        if (h.referenceTypes.contains(true)) {
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
    String generateFactoryConstructorBody(String className, String targetMethodName, List<String> parameterTypeName, List<String> guestCalls, boolean hasMetaInjection, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        VMHelper h = (VMHelper) helper;
        str.append(TAB_3).append("super(\n");
        str.append(TAB_4).append(generateString(targetMethodName)).append(",\n");
        str.append(TAB_4).append(generateString(h.jniNativeSignature)).append(",\n");
        str.append(TAB_4).append(parameterTypeName.size()).append(",\n");
        str.append(TAB_4).append(generateString(h.returnType)).append(",\n");
        str.append(TAB_4).append(h.isJni).append("\n");
        str.append(TAB_3).append(");\n");
        str.append(TAB_2).append("}\n");
        return str.toString();
    }

    @Override
    String generateInvoke(String className, String targetMethodName, List<String> parameterTypeName, List<String> guestCalls, SubstitutionHelper helper, boolean hasMetaInjection) {
        StringBuilder str = new StringBuilder();
        VMHelper h = (VMHelper) helper;
        str.append(TAB_1).append(PUBLIC_FINAL_OBJECT).append(INVOKE);
        int argIndex = 0;
        for (String type : parameterTypeName) {
            boolean isNonPrimitive = h.referenceTypes.get(argIndex);
            str.append(extractArg(argIndex++, type, isNonPrimitive, h.isJni ? 1 : 0, TAB_2));
        }
        switch (h.returnType) {
            case "char":
                str.append(TAB_2).append("return ").append("(short) ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, guestCalls, hasMetaInjection));
                break;
            case "boolean":
                str.append(TAB_2).append("boolean b = ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, guestCalls, hasMetaInjection));
                str.append(TAB_2).append("return b ? (byte) 1 : (byte) 0;\n");
                break;
            case "void":
                str.append(TAB_2).append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, guestCalls, hasMetaInjection));
                str.append(TAB_2).append("return ").append(STATIC_OBJECT_NULL).append(";\n");
                break;
            default:
                str.append(TAB_2).append("return ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, guestCalls, hasMetaInjection));
        }
        str.append(TAB_1).append("}\n");
        str.append("}");
        return str.toString();
    }
}