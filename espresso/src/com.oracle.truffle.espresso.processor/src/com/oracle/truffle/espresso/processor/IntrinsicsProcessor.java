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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ReferenceType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

public abstract class IntrinsicsProcessor extends EspressoProcessor {
    private static final String JNI_PACKAGE = "com.oracle.truffle.espresso.jni";
    protected static final String FFI_PACKAGE = "com.oracle.truffle.espresso.ffi";
    private static final String POINTER = FFI_PACKAGE + "." + "Pointer";
    private static final String HANDLE = JNI_PACKAGE + "." + "Handle";
    private static final String JNI_IMPL = JNI_PACKAGE + "." + "JniImpl";

    private static final String SUBSTITUTOR_PACKAGE = "com.oracle.truffle.espresso.substitutions";
    private static final String SUBSTITUTOR = "IntrinsicSubstitutor";

    private static final String ENV_ARG_NAME = "env";

    protected static final String IMPORT_NATIVE_SIGNATURE = "import " + FFI_PACKAGE + "." + "NativeSignature" + ";\n";
    protected static final String IMPORT_NATIVE_TYPE = "import " + FFI_PACKAGE + "." + "NativeType" + ";\n";

    // @Pointer
    private TypeElement pointerAnnotation;

    // @Handle
    private TypeElement handleAnnotation;

    private final String ENV_NAME;
    private final String ENV_PACKAGE;
    private final String ENV_CLASSNAME;
    private final String SUPPORTED_ANNOTATION;

    private final String INVOKE;
    private final String IMPORT;

    // @JniImpl
    private TypeElement jniImpl;
    // The annotation for this processor.
    private TypeElement intrinsicAnnotation;

    public static final class IntrinsincsHelper extends SubstitutionHelper {
        final NativeType[] jniNativeSignature;
        final List<Boolean> referenceTypes;
        final boolean isStatic;
        final boolean isJni;
        final boolean needsHandlify;

        public IntrinsincsHelper(EspressoProcessor processor,
                        ExecutableElement method,
                        NativeType[] jniNativeSignature,
                        List<Boolean> referenceTypes,
                        boolean isStatic,
                        boolean isJni,
                        boolean needsHandlify) {
            super(processor, method);
            this.jniNativeSignature = jniNativeSignature;
            this.referenceTypes = referenceTypes;
            this.isStatic = isStatic;
            this.isJni = isJni;
            this.needsHandlify = needsHandlify;
        }
    }

    public IntrinsicsProcessor(String ENV_NAME, String ENV_CLASSNAME, String SUPPORTED_ANNOTATION, String ENV_PACKAGE, String COLLECTOR, String COLLECTOR_INSTANCE_NAME) {
        super(SUBSTITUTOR_PACKAGE, SUBSTITUTOR, COLLECTOR, COLLECTOR_INSTANCE_NAME);
        this.ENV_NAME = ENV_NAME;
        this.ENV_PACKAGE = ENV_PACKAGE;
        this.ENV_CLASSNAME = ENV_CLASSNAME;
        this.SUPPORTED_ANNOTATION = SUPPORTED_ANNOTATION;
        this.INVOKE = "invoke(Object " + ENV_ARG_NAME + ", Object[] " + ARGS_NAME + ") {\n";
        this.IMPORT = "import " + ENV_PACKAGE + "." + ENV_CLASSNAME + ";\n";
    }

    protected void initNfiType() {
        this.pointerAnnotation = processingEnv.getElementUtils().getTypeElement(POINTER);
        this.handleAnnotation = processingEnv.getElementUtils().getTypeElement(HANDLE);
    }

    @Override
    void processImpl(RoundEnvironment env) {
        // Set up the different annotations, along with their values, that we will need.
        this.jniImpl = processingEnv.getElementUtils().getTypeElement(JNI_IMPL);
        this.intrinsicAnnotation = processingEnv.getElementUtils().getTypeElement(SUPPORTED_ANNOTATION);
        initNfiType();
        for (Element e : env.getElementsAnnotatedWith(intrinsicAnnotation)) {
            processElement(e);
        }
    }

    final void processElement(Element element) {
        assert element.getKind() == ElementKind.METHOD;
        ExecutableElement method = (ExecutableElement) element;
        assert method.getEnclosingElement().getKind() == ElementKind.CLASS;
        TypeElement declaringClass = (TypeElement) method.getEnclosingElement();
        if (declaringClass.getQualifiedName().toString().equals(ENV_PACKAGE + "." + ENV_CLASSNAME)) {
            String className = ENV_CLASSNAME;
            // Extract the class name.
            // Obtain the name of the method to be substituted in.
            String targetMethodName = method.getSimpleName().toString();
            // Obtain the host types of the parameters
            List<String> espressoTypes = new ArrayList<>();
            List<Boolean> referenceTypes = new ArrayList<>();
            boolean needsHandlify = getEspressoTypes(method, espressoTypes, referenceTypes);
            // Spawn the name of the Substitutor we will create.
            String substitutorName = getSubstitutorClassName(className, targetMethodName, espressoTypes);
            if (!classes.contains(substitutorName)) {
                // Check if this method has the @JniImpl annotation
                boolean isJni = isJni(method);
                // Obtain the jniNativeSignature
                NativeType[] jniNativeSignature = jniNativeSignature(method, isJni);
                // Check if we need to call an instance method
                boolean isStatic = method.getModifiers().contains(Modifier.STATIC);
                // Spawn helper
                IntrinsincsHelper h = new IntrinsincsHelper(this, method, jniNativeSignature, referenceTypes, isStatic, isJni, needsHandlify);
                // Create the contents of the source file
                String classFile = spawnSubstitutor(
                                className,
                                targetMethodName,
                                espressoTypes, h);
                commitSubstitution(method, substitutorName, classFile);
            }
        }
    }

    boolean isJni(ExecutableElement VMmethod) {
        // Check if this VM method has the @JniImpl annotation
        return getAnnotation(VMmethod, jniImpl) != null;
    }

    boolean getEspressoTypes(ExecutableElement inner, List<String> parameterTypeNames, List<Boolean> referenceTypes) {
        boolean hasStaticObject = false;
        for (VariableElement parameter : inner.getParameters()) {
            if (isActualParameter(parameter)) {
                String arg = parameter.asType().toString();
                String result = extractSimpleType(arg);
                boolean isRef = parameter.asType() instanceof ReferenceType;
                if (isRef && result.equals("StaticObject")) {
                    hasStaticObject = true;
                }
                parameterTypeNames.add(result);
                referenceTypes.add(isRef);
            }
        }
        return hasStaticObject;
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

    String extractArg(int index, String clazz, boolean isNonPrimitive, int startAt, String tabulation) {
        String decl = tabulation + clazz + " " + ARG_NAME + index + " = ";
        String obj = ARGS_NAME + "[" + (index + startAt) + "]";
        if (isNonPrimitive) {
            if (!clazz.equals("StaticObject")) {
                return decl + castTo(obj, clazz) + ";\n";
            }
            return decl + ENV_NAME + ".getHandles().get(Math.toIntExact((long) " + obj + "))" + ";\n";
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

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(SUPPORTED_ANNOTATION);
        return annotations;
    }

    @Override
    String generateImports(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        IntrinsincsHelper h = (IntrinsincsHelper) helper;
        str.append(IMPORT);
        str.append(IMPORT_NATIVE_SIGNATURE);
        str.append(IMPORT_NATIVE_TYPE);
        if (parameterTypeName.contains("String")) {
            str.append(IMPORT_INTEROP_LIBRARY);
        }
        if (parameterTypeName.contains("StaticObject") || h.jniNativeSignature[0].equals(NativeType.VOID)) {
            str.append(IMPORT_STATIC_OBJECT);
        }
        if (parameterTypeName.contains("TruffleObject")) {
            str.append(IMPORT_TRUFFLE_OBJECT);
        }
        str.append("\n");
        return str.toString();
    }

    @Override
    String generateFactoryConstructorAndBody(String className, String targetMethodName, List<String> parameterTypeName, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        IntrinsincsHelper h = (IntrinsincsHelper) helper;
        str.append(TAB_3).append("super(\n");
        str.append(TAB_4).append(generateString(targetMethodName)).append(",\n");
        str.append(TAB_4).append(generateNativeSignature(h.jniNativeSignature)).append(",\n");
        str.append(TAB_4).append(parameterTypeName.size()).append(",\n");
        str.append(TAB_4).append(h.isJni).append("\n");
        str.append(TAB_3).append(");\n");
        str.append(TAB_2).append("}\n");
        return str.toString();
    }

    @Override
    String generateInvoke(String className, String targetMethodName, List<String> parameterTypes, SubstitutionHelper helper) {
        StringBuilder str = new StringBuilder();
        IntrinsincsHelper h = (IntrinsincsHelper) helper;
        str.append(TAB_1).append(PUBLIC_FINAL_OBJECT).append(INVOKE);
        if (h.needsHandlify || !h.isStatic) {
            str.append(TAB_2).append(ENV_CLASSNAME).append(" ").append(ENV_NAME).append(" = ").append("(").append(ENV_CLASSNAME).append(") " + ENV_ARG_NAME + ";\n");
        }
        int argIndex = 0;
        for (String type : parameterTypes) {
            boolean isNonPrimitive = h.referenceTypes.get(argIndex);
            str.append(extractArg(argIndex++, type, isNonPrimitive, h.isJni ? 1 : 0, TAB_2));
        }
        switch (h.jniNativeSignature[0]) {
            case VOID:
                str.append(TAB_2).append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper)).append(";\n");
                str.append(TAB_2).append("return ").append(STATIC_OBJECT_NULL).append(";\n");
                break;
            case OBJECT:
                str.append(TAB_2).append("return ").append(
                                "(long) " + ENV_NAME + ".getHandles().createLocal(" + extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper) + ")").append(";\n");
                break;
            default:
                str.append(TAB_2).append("return ").append(extractInvocation(className, targetMethodName, argIndex, h.isStatic, helper)).append(";\n");
        }
        str.append(TAB_1).append("}\n");
        str.append("}");
        return str.toString();
    }
}
