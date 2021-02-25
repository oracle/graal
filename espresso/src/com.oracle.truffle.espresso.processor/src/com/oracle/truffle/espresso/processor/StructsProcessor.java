/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.processor.EspressoProcessor.IMPORT_STATIC_OBJECT;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.PUBLIC_FINAL_CLASS;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.SUPPRESS_UNUSED;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.TAB_1;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.TAB_2;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.TAB_3;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

public class StructsProcessor extends AbstractProcessor {

    private static final String STRUCTS_PACKAGE = "com.oracle.truffle.espresso.jvmti.structs";

    // Annotations
    private static final String GENERATE_STRUCTS = STRUCTS_PACKAGE + ".GenerateStructs";
    private static final String KNOWN_STRUCT = GENERATE_STRUCTS + ".KnownStruct";

    // Imports
    private static final String IMPORT_BYTEBUFFER = "import java.nio.ByteBuffer;\n";
    private static final String IMPORT_JNI_ENV = "import com.oracle.truffle.espresso.jni.JniEnv;\n";
    private static final String IMPORT_RAW_POINTER = "import com.oracle.truffle.espresso.ffi.RawPointer;\n";

    // Classes
    private static final String TRUFFLE_OBJECT = "TruffleObject";
    private static final String JNI_ENV_CLASS = "JniEnv";
    private static final String MEMBER_OFFSET_GETTER_CLASS = "MemberOffsetGetter";
    private static final String STRUCT_WRAPPER_CLASS = "StructWrapper";

    // Methods
    private static final String GET_INFO = "getInfo";
    private static final String GET_OFFSET = "getOffset";
    private static final String GET = "get";
    private static final String PUT = "put";

    // Members
    private static final String MEMBER_OFFSET_GETTER_ARG = "offset";
    private static final String STRUCT_SIZE = "structSize";

    // Arguments
    private static final String VALUE = "valueToPut";
    private static final String JNI_ENV_ARG = "jni";
    private static final String PTR = "pointer";

    // Modifiers
    private static final String PUBLIC = "public";
    private static final String FINAL = "final";

    // @GenerateStructs
    TypeElement generateStructs;
    // @GenerateStructs.value()
    ExecutableElement genStructValue;

    // @GenerateStructs.KnownStruct
    TypeElement knownStruct;
    // @GenerateStructs.structName()
    ExecutableElement structName;
    // @GenerateStructs.memberNames()
    ExecutableElement memberNames;
    // @GenerateStructs.types()
    ExecutableElement types;

    private boolean done = false;

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new HashSet<>();
        annotations.add(GENERATE_STRUCTS);
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
    }

    ExecutableElement findAttribute(String name, TypeElement annotation) {
        for (Element e : annotation.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                if (e.getSimpleName().contentEquals(name)) {
                    return (ExecutableElement) e;
                }
            }
        }
        return null;
    }

    TypeElement findAnnotation(String annotation) {
        return processingEnv.getElementUtils().getTypeElement(annotation);
    }

    AnnotationValue getAttribute(AnnotationMirror annotation, Element attribute) {
        Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = processingEnv.getElementUtils().getElementValuesWithDefaults(annotation);
        return elementValues.get(attribute);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (done) {
            return false;
        }

        // Initialize annotations
        generateStructs = findAnnotation(GENERATE_STRUCTS);
        knownStruct = findAnnotation(KNOWN_STRUCT);
        genStructValue = findAttribute("value", generateStructs);
        structName = findAttribute("structName", knownStruct);
        memberNames = findAttribute("memberNames", knownStruct);
        types = findAttribute("types", knownStruct);

        // Look at the @GenerateStructs annotation
        for (Element e : roundEnv.getElementsAnnotatedWith(generateStructs)) {
            assert e.getKind() == ElementKind.CLASS;
            AnnotationMirror generateStructAnnotation = EspressoProcessor.getAnnotation(e, generateStructs);
            // Obtain the value() of @GenerateStructs (The only interesting thing)
            AnnotationValue v = getAttribute(generateStructAnnotation, genStructValue);
            assert v.getValue() instanceof List<?>;
            List<?> list = (List<?>) v.getValue();

            for (Object known : list) {
                // Each entry in the annotation value is a struct to generate.
                assert known instanceof AnnotationValue;
                assert ((AnnotationValue) known).getValue() instanceof AnnotationMirror;
                AnnotationMirror knownStr = ((AnnotationMirror) ((AnnotationValue) known).getValue());

                // C struct name.
                String strName = (String) getAttribute(knownStr, structName).getValue();
                // C struct members.
                List<?> memberNamesList = (List<?>) getAttribute(knownStr, memberNames).getValue();
                // native to java types.
                List<?> typesList = (List<?>) getAttribute(knownStr, types).getValue();
                processStruct(strName, memberNamesList, typesList);
            }
        }
        return false;
    }

    private void processStruct(String strName, List<?> memberNamesList, List<?> typesList) {
        assert memberNamesList.size() == typesList.size();
        List<String> members = new ArrayList<>();
        List<NativeType> nativeTypes = new ArrayList<>();
        int length = memberNamesList.size();

        // Preprocess the members
        for (int i = 0; i < length; i++) {
            members.add((String) ((AnnotationValue) memberNamesList.get(i)).getValue());
            VariableElement type = (VariableElement) ((AnnotationValue) typesList.get(i)).getValue();
            nativeTypes.add(NativeType.valueOf(type.getSimpleName().toString()));
        }

        String className = "J" + strName.substring(2); // Replace '_j' with 'J'
        // Generate code
        String source = generateStruct(strName, members, nativeTypes, length, className);
        // Commit and write to files.
        commit(null, className, source);
    }

    void commit(Element method, String className, String classFile) {
        try {
            // Create the file
            JavaFileObject file = processingEnv.getFiler().createSourceFile(STRUCTS_PACKAGE + "." + className, method);
            Writer wr = file.openWriter();
            wr.write(classFile);
            wr.close();
        } catch (IOException ex) {
            /* nop */
        }
    }

    private static String generateStruct(String strName, List<String> members, List<NativeType> typesList, int length, String className) {
        StringBuilder builder = new StringBuilder();
        // Copyright + package + imports
        generateHeader(builder);

        // Java struct declaration
        builder.append(SUPPRESS_UNUSED).append("\n");
        builder.append(PUBLIC_FINAL_CLASS).append(className).append(" {\n");

        // Generate the fields:
        // - One to store the struct size
        // - One per member to store their offsets
        generateFields(members, builder);

        // Generate the constructor.
        // Use passed MemberOffsetGetter.
        generateConstructor(strName, members, builder, className);

        // Generate the wrapper structure that will access the native struct java-like.
        // It simply wraps a byte buffer around the pointer, and uses offsets in parent class to
        // perform accesses.
        generateWrapper(members, typesList, length, className, builder);

        builder.append(TAB_1).append("}\n");

        builder.append("}\n");

        return builder.toString();
    }

    private static void generateWrapper(List<String> members, List<NativeType> typesList, int length, String className, StringBuilder builder) {
        String wrapperName = className + "Wrapper";
        builder.append(TAB_1).append(PUBLIC_FINAL_CLASS).append(wrapperName).append(" extends ").append(STRUCT_WRAPPER_CLASS).append(" {\n");
        generateWrapperConstructor(builder, wrapperName);
        for (int i = 0; i < length; i++) {
            String member = members.get(i);
            NativeType type = typesList.get(i);
            generateGetterSetter(builder, member, type);
        }
    }

    private static void generateWrapperConstructor(StringBuilder builder, String wrapperName) {
        builder.append(TAB_2).append(ProcessorUtils.methodDeclaration(null, null, wrapperName,
                        new String[]{ProcessorUtils.argument(JNI_ENV_CLASS, JNI_ENV_ARG), ProcessorUtils.argument(TRUFFLE_OBJECT, PTR)})).append("{\n");
        builder.append(TAB_3).append(ProcessorUtils.call(null, "super", new String[]{JNI_ENV_ARG, PTR, STRUCT_SIZE})).append(";\n");
        builder.append(TAB_2).append("}\n\n");
    }

    private static void generateGetterSetter(StringBuilder builder, String member, NativeType type) {
        String callSuffix = nativeTypeToMethodSuffix(type);
        String argType = nativeTypeToArgType(type);
        builder.append(TAB_2).append(ProcessorUtils.methodDeclaration(PUBLIC, argType, member, new String[]{})).append(" {\n");
        builder.append(TAB_3).append("return ").append(ProcessorUtils.call(null, GET + callSuffix, new String[]{member})).append(";\n");
        builder.append(TAB_2).append("}\n\n");

        builder.append(TAB_2).append(ProcessorUtils.methodDeclaration(PUBLIC, "void", member, new String[]{ProcessorUtils.argument(argType, VALUE)})).append(" {\n");
        builder.append(TAB_3).append(ProcessorUtils.call(null, PUT + callSuffix, new String[]{member, VALUE})).append(";\n");
        builder.append(TAB_2).append("}\n\n");
    }

    private static void generateHeader(StringBuilder builder) {
        builder.append(EspressoProcessor.COPYRIGHT);
        builder.append("package ").append(STRUCTS_PACKAGE).append(";\n\n");
        builder.append('\n');
        builder.append(IMPORT_BYTEBUFFER);
        builder.append('\n');
        builder.append(EspressoProcessor.IMPORT_TRUFFLE_OBJECT);
        builder.append('\n');
        builder.append(IMPORT_STATIC_OBJECT);
        builder.append(IMPORT_JNI_ENV);
        builder.append(IMPORT_RAW_POINTER);
        builder.append('\n');
    }

    private static void generateFields(List<String> members, StringBuilder builder) {
        builder.append(TAB_1).append(ProcessorUtils.fieldDeclaration(FINAL, "long", STRUCT_SIZE, null)).append("\n\n");
        for (String member : members) {
            builder.append(TAB_1).append(ProcessorUtils.fieldDeclaration(FINAL, "int", member, null)).append("\n");
        }
        builder.append('\n');
    }

    private static void generateConstructor(String strName, List<String> members, StringBuilder builder, String className) {
        builder.append(TAB_1).append(ProcessorUtils.methodDeclaration(null, null, className, new String[]{ProcessorUtils.argument(MEMBER_OFFSET_GETTER_CLASS, MEMBER_OFFSET_GETTER_ARG)}));
        builder.append(" {\n");

        builder.append(TAB_2).append(ProcessorUtils.assignment(STRUCT_SIZE, ProcessorUtils.call(MEMBER_OFFSET_GETTER_ARG, GET_INFO, new String[]{ProcessorUtils.stringify(strName)}))).append("\n");
        for (String member : members) {
            builder.append(TAB_2).append(ProcessorUtils.assignment(member,
                            " (int) " + ProcessorUtils.call(MEMBER_OFFSET_GETTER_ARG, GET_OFFSET, new String[]{ProcessorUtils.stringify(strName), ProcessorUtils.stringify(member)}))).append("\n");
        }

        builder.append(TAB_1).append("}\n\n");
    }

    private static String nativeTypeToMethodSuffix(NativeType type) {
        switch (type) {
            case BOOLEAN:
                return "Boolean";
            case BYTE:
                return "Byte";
            case CHAR:
                return "Char";
            case SHORT:
                return "Short";
            case INT:
                return "Int";
            case LONG:
                return "Long";
            case FLOAT:
                return "Float";
            case DOUBLE:
                return "Double";
            case OBJECT:
                return "Object";
            case POINTER:
                return "Pointer";
            default:
                return "";
        }
    }

    private static String nativeTypeToArgType(NativeType type) {
        switch (type) {
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case CHAR:
                return "char";
            case SHORT:
                return "short";
            case INT:
                return "int";
            case LONG:
                return "long";
            case FLOAT:
                return "float";
            case DOUBLE:
                return "double";
            case OBJECT:
                return "StaticObject";
            case POINTER:
                return "TruffleObject";
            default:
                return "";
        }
    }

}
