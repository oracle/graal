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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

public class JniImplProcessor extends AbstractProcessor {
    // @JniImpl
    private TypeElement jniImpl;
    // @NFIType
    private TypeElement nfiType;

    // @Host.value()
    private ExecutableElement nfiTypeValueElement;

    private HashSet<String> classes = new HashSet<>();
    private StringBuilder collector = null;

    static final Map<String, NativeSimpleType> classToNative = buildClassToNative();

    static Map<String, NativeSimpleType> buildClassToNative() {
        Map<String, NativeSimpleType> map = new HashMap<>();
        map.put("boolean", NativeSimpleType.SINT8);
        map.put("byte", NativeSimpleType.SINT8);
        map.put("short", NativeSimpleType.SINT16);
        map.put("char", NativeSimpleType.SINT16);
        map.put("int", NativeSimpleType.SINT32);
        map.put("float", NativeSimpleType.FLOAT);
        map.put("long", NativeSimpleType.SINT64);
        map.put("double", NativeSimpleType.DOUBLE);
        map.put("void", NativeSimpleType.VOID);
        map.put("java.lang.String", NativeSimpleType.STRING);
        return Collections.unmodifiableMap(map);
    }

    public static NativeSimpleType classToType(String clazz, boolean javaToNative) {
        return classToNative.getOrDefault(clazz, javaToNative ? NativeSimpleType.NULLABLE : NativeSimpleType.OBJECT);
    }

    private boolean done = false;

    // region Various String constants.

    private static final String SUBSTITUTION_PACKAGE = "com.oracle.truffle.espresso.jni";

    private static final String JNI_IMPL = SUBSTITUTION_PACKAGE + "." + "JniImpl";
    private static final String NFI_TYPE = SUBSTITUTION_PACKAGE + "." + "NFIType";

    private static final String INSTANCE_NAME = "theInstance";
    private static final String GETTER = "getInstance";

    private static final String COPYRIGHT = "/* Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.\n" +
                    " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
                    " *\n" +
                    " * This code is free software; you can redistribute it and/or modify it\n" +
                    " * under the terms of the GNU General Public License version 2 only, as\n" +
 * published by the Free Software Foundation.
                    " *\n" +
                    " * This code is distributed in the hope that it will be useful, but WITHOUT\n" +
                    " * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n" +
                    " * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n" +
                    " * version 2 for more details (a copy is included in the LICENSE file that\n" +
                    " * accompanied this code).\n" +
                    " *\n" +
                    " * You should have received a copy of the GNU General Public License version\n" +
                    " * 2 along with this work; if not, write to the Free Software Foundation,\n" +
                    " * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n" +
                    " *\n" +
                    " * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n" +
                    " * or visit www.oracle.com if you need additional information or have any\n" +
                    " * questions.\n" +
                    " */\n\n";

    private static final String GENERATED_BY = "// Generated by: ";
    private static final String PACKAGE = "package " + SUBSTITUTION_PACKAGE + ";\n" +
                    "\n";
    private static final String IMPORT_INTEROP_LIBRARY = "import com.oracle.truffle.api.interop.InteropLibrary;\n";
    private static final String IMPORT_STATIC_OBJECT = "import com.oracle.truffle.espresso.runtime.StaticObject;\n";
    private static final String IMPORT_TRUFFLE_OBJECT = "import com.oracle.truffle.api.interop.TruffleObject;\n";

    private static final String STATIC_OBJECT_NULL = "StaticObject.NULL";

    private static final String FACTORY_IS_NULL = "InteropLibrary.getFactory().getUncached().isNull";

    private static final String IMPORTS_COLLECTOR = PACKAGE +
                    "\n" +
                    "import java.util.ArrayList;\n" +
                    "import java.util.List;\n";

    private static final String JNI_ENV = "JniEnv";
    private static final String IMPORT_JNI_ENV = "import " + SUBSTITUTION_PACKAGE + "." + JNI_ENV + ";\n";

    private static final String PRIVATE_STATIC_FINAL = "private static final";

    private static final String PUBLIC_STATIC_FINAL = "public static final";
    private static final String PUBLIC_FINAL_OBJECT = "public final Object ";
    private static final String PUBLIC_FINAL_CLASS = "\n" + "public final class ";
    private static final String SUBSTITUTOR = "JniSubstitutor";
    private static final String COLLECTOR = "JniCollector";
    private static final String COLLECTOR_INSTANCE_NAME = "jniCollector";
    private static final String EXTENSION = " extends " + SUBSTITUTOR + " {\n";
    private static final String OVERRIDE = "@Override";
    private static final String ARGS_NAME = "args";
    private static final String ENV_NAME = "env";
    private static final String INVOKE = "invoke(" + JNI_ENV + " " + ENV_NAME + ", Object[] " + ARGS_NAME + ") {\n";
    private static final String ARG_NAME = "arg";
    private static final String TAB_1 = "    ";
    private static final String TAB_2 = TAB_1 + TAB_1;
    private static final String TAB_3 = TAB_2 + TAB_1;

    // end region Various String constants.

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        // Initialize the collector.
        initCollector();
    }

    private void initCollector() {
        this.collector = new StringBuilder();
        collector.append(COPYRIGHT);
        collector.append(IMPORTS_COLLECTOR).append("\n");
        collector.append(GENERATED_BY).append(JniImplProcessor.class.getSimpleName());
        collector.append(PUBLIC_FINAL_CLASS).append(COLLECTOR).append(" {\n");
        collector.append(generateInstance("ArrayList<>", COLLECTOR_INSTANCE_NAME, "List<" + SUBSTITUTOR + ">"));
        collector.append(TAB_1).append("private ").append(COLLECTOR).append("() {\n").append(TAB_1).append("}\n");
        collector.append(generateGetter(COLLECTOR_INSTANCE_NAME, "List<" + SUBSTITUTOR + ">", GETTER)).append("\n");
        collector.append(TAB_1).append("static {\n");
    }

    private static final String getSubstitutorQualifiedName(String substitutorName) {
        return SUBSTITUTION_PACKAGE + "." + substitutorName;
    }

    private static StringBuilder signatureSuffixBuilder(List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append("_").append(parameterTypes.size());
        return str;
    }

    private static final String getSubstitutorClassName(String className, String methodName, List<String> parameterTypes) {
        StringBuilder str = new StringBuilder();
        str.append(className).append("_").append(methodName).append(signatureSuffixBuilder(parameterTypes));
        return str.toString();
    }

    private static void addSubstitutor(StringBuilder str, String substitutorName) {
        str.append(TAB_2).append(COLLECTOR_INSTANCE_NAME).append(".add(").append(substitutorName).append(".").append(GETTER).append("()").append(");\n");
    }

    private static String castTo(String obj, String clazz) {
        if (clazz.equals("Object")) {
            return obj;
        }
        return "(" + clazz + ") " + obj;
    }

    private static String genIsNull(String obj) {
        return FACTORY_IS_NULL + "(" + obj + ")";
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following indented code snippet:
     * 
     * <pre>{@code
     * clazz arg\index\ = (clazz) args[index];
     * }</pre>
     * 
     * If clazz is Object, the cast is removed.
     */
    // Checkstyle: resume
    // @formatter:on
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

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>{@code
     * className.methodName(arg0, arg1, ..., argN);
     * }</pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String extractInvocation(String className, String methodName, int nParameters, boolean isStatic) {
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
        str.append(");\n");
        return str.toString();
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>{@code
     * private static final substitutorName instanceName = new instanceClass();
     * }</pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String generateInstance(String substitutorName, String instanceName, String instanceClass) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append(PRIVATE_STATIC_FINAL).append(" ").append(instanceClass).append(" ").append(instanceName);
        str.append(" = new ").append(substitutorName).append("();\n");
        return str.toString();
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>
     * private substitutorName() {
     *     super(
     *         "jniNativeSignature",
     *     );
     * }
     * </pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String generateConstructor(String substitutorName, String targetMethodName, String jniNativeSignature, int parameterCount, String returnType) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append("private ").append(substitutorName).append("() {\n");
        str.append(TAB_2).append("super(\n");
        str.append(TAB_3).append(generateString(targetMethodName)).append(",\n");
        str.append(TAB_3).append(generateString(jniNativeSignature)).append(",\n");
        str.append(TAB_3).append(parameterCount).append(",\n");
        str.append(TAB_3).append(generateString(returnType)).append("\n");
        str.append(TAB_2).append(");\n");
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    private static String generateString(String str) {
        return "\"" + str + "\"";
    }

    // @formatter:off
    // Checkstyle: stop
    /**
     * creates the following code snippet:
     * 
     * <pre>
     * public static final className getterName() {
     *     return instanceName;
     * }
     * </pre>
     */
    // Checkstyle: resume
    // @formatter:on
    private static String generateGetter(String instanceName, String className, String getterName) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append(PUBLIC_STATIC_FINAL).append(" ").append(className).append(" ").append(getterName).append("() {\n");
        str.append(TAB_2).append("return ").append(instanceName).append(";\n");
        str.append(TAB_1).append("}\n");
        return str.toString();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(JNI_IMPL);
        return annotations;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (done) {
            return false;
        }
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

        processImpl(roundEnv);
        // We are done, push the collector.
        commitFiles();
        done = true;
        return false;
    }

    private void processImpl(RoundEnvironment env) {
        for (Element e : env.getElementsAnnotatedWith(jniImpl)) {
            processElement(e);
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
            List<Boolean> nonPrimitives = new ArrayList<>();
            getEspressoTypes(jniMethod, espressoTypes, nonPrimitives);
            // Spawn the name of the Substitutor we will create.
            String substitutorName = getSubstitutorClassName(className, targetMethodName, espressoTypes);
            if (!classes.contains(substitutorName)) {
                // Obtain the fully qualified guest return type of the method.
                String returnType = extractReturnType(jniMethod);
                // Obtain the jniNativeSignature
                String jniNativeSignature = jniNativeSignature(jniMethod, returnType);
                // Check if we need to call an instance method
                boolean isStatic = jniMethod.getModifiers().contains(Modifier.STATIC);
                // Create the contents of the source file
                String classFile = spawnSubstitutor(
                                className,
                                targetMethodName,
                                jniNativeSignature,
                                espressoTypes,
                                nonPrimitives,
                                returnType,
                                isStatic);
                commitSubstitution(jniMethod, substitutorName, classFile);
            }
        }
    }

    private void commitSubstitution(Element method, String substitutorName, String classFile) {
        try {
            // Create the file
            JavaFileObject file = processingEnv.getFiler().createSourceFile(getSubstitutorQualifiedName(substitutorName), method);
            Writer wr = file.openWriter();
            wr.write(classFile);
            wr.close();
            classes.add(substitutorName);
            addSubstitutor(collector, substitutorName);
        } catch (IOException ex) {
            /* nop */
        }
    }

    private static String extractReturnType(ExecutableElement method) {
        return extractSimpleType(method.getReturnType().toString());
    }

    // Commits the collector.
    private void commitFiles() {
        try {
            collector.append(TAB_1).append("}\n}");
            JavaFileObject file = processingEnv.getFiler().createSourceFile(getSubstitutorQualifiedName(COLLECTOR));
            Writer wr = file.openWriter();
            wr.write(collector.toString());
            wr.close();
        } catch (IOException ex) {
            /* nop */
        }
    }

    private static void getEspressoTypes(ExecutableElement inner, List<String> parameterTypeNames, List<Boolean> nonPrimitives) {
        for (VariableElement parameter : inner.getParameters()) {
            String arg = parameter.asType().toString();
            String result = extractSimpleType(arg);
            parameterTypeNames.add(result);
            nonPrimitives.add(!(parameter.asType() instanceof PrimitiveType));
        }
    }

    public String jniNativeSignature(ExecutableElement method, String returnType) {
        StringBuilder sb = new StringBuilder("(");
        // Prepend JNIEnv* . The raw pointer will be substituted by the proper `this` reference.
        sb.append(NativeSimpleType.SINT64);
        for (VariableElement param : method.getParameters()) {
            sb.append(", ");

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

    private static AnnotationMirror getAnnotation(TypeMirror e, TypeElement type) {
        for (AnnotationMirror annotationMirror : e.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType().asElement().equals(type)) {
                return annotationMirror;
            }
        }
        return null;
    }

    private static String extractSimpleType(String arg) {
        int beginIndex = arg.lastIndexOf('.');
        String result;
        if (beginIndex >= 0) {
            if (arg.charAt(arg.length() - 1) == ')') {
                result = arg.substring(beginIndex + 1, arg.length() - 1);
            } else {
                result = arg.substring(beginIndex + 1);
            }
        } else {
            result = arg;
        }
        return result;
    }

    private static String generateInvoke(String className, String targetMethodName, List<String> parameterTypes, List<Boolean> nonPrimitives, String returnType, boolean isStatic) {
        StringBuilder str = new StringBuilder();
        str.append(TAB_1).append(PUBLIC_FINAL_OBJECT).append(INVOKE);
        int argIndex = 0;
        for (String type : parameterTypes) {
            boolean isNonPrimitive = nonPrimitives.get(argIndex);
            str.append(extractArg(argIndex++, type, isNonPrimitive, 1, TAB_2));
        }
        switch (returnType) {
            case "char":
                str.append(TAB_2).append("return ").append("(short) ").append(extractInvocation(className, targetMethodName, argIndex, isStatic));
                break;
            case "boolean":
                str.append(TAB_2).append("boolean b = ").append(extractInvocation(className, targetMethodName, argIndex, isStatic));
                str.append(TAB_2).append("return b ? (byte) 1 : (byte) 0;\n");
                break;
            case "void":
                str.append(TAB_2).append(extractInvocation(className, targetMethodName, argIndex, isStatic));
                str.append(TAB_2).append("return ").append(STATIC_OBJECT_NULL).append(";\n");
                break;
            default:
                str.append(TAB_2).append("return ").append(extractInvocation(className, targetMethodName, argIndex, isStatic));
        }
        str.append(TAB_1).append("}\n");
        str.append("}");
        return str.toString();
    }

    private static String spawnSubstitutor(String className, String targetMethodName, String jniNativeSignature, List<String> parameterTypeName, List<Boolean> nonPrimitives, String returnType,
                    boolean isStatic) {
        String substitutorName = getSubstitutorClassName(className, targetMethodName, parameterTypeName);
        StringBuilder classFile = new StringBuilder();
        // Header
        classFile.append(COPYRIGHT);
        classFile.append(PACKAGE);

        classFile.append(IMPORT_JNI_ENV);
        if (nonPrimitives.contains(true)) {
            classFile.append(IMPORT_INTEROP_LIBRARY);
        }
        if (parameterTypeName.contains("StaticObject") || returnType.equals("void")) {
            classFile.append(IMPORT_STATIC_OBJECT);
        }
        if (parameterTypeName.contains("TruffleObject")) {
            classFile.append(IMPORT_TRUFFLE_OBJECT);
        }
        classFile.append("\n");

        // Class
        classFile.append(GENERATED_BY).append(className);
        classFile.append(PUBLIC_FINAL_CLASS).append(substitutorName).append(EXTENSION);
        classFile.append(generateInstance(substitutorName, INSTANCE_NAME, SUBSTITUTOR)).append("\n");
        classFile.append(generateConstructor(substitutorName, targetMethodName, jniNativeSignature, parameterTypeName.size(), returnType)).append("\n");
        classFile.append(generateGetter(INSTANCE_NAME, SUBSTITUTOR, GETTER)).append("\n");
        classFile.append(TAB_1).append(OVERRIDE).append("\n");

        // Substitution
        classFile.append(generateInvoke(className, targetMethodName, parameterTypeName, nonPrimitives, returnType, isStatic));

        // End
        return classFile.toString();
    }

}

enum NativeSimpleType {
    VOID,
    UINT8,
    SINT8,
    UINT16,
    SINT16,
    UINT32,
    SINT32,
    UINT64,
    SINT64,
    FLOAT,
    DOUBLE,
    POINTER,
    STRING,
    OBJECT,
    NULLABLE;
}
