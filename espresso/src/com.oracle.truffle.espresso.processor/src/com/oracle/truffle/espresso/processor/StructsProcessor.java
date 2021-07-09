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

import static com.oracle.truffle.espresso.processor.EspressoProcessor.COPYRIGHT;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.IMPORT_INTEROP_LIBRARY;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.IMPORT_STATIC_OBJECT;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.IMPORT_TRUFFLE_OBJECT;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.OVERRIDE;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.PUBLIC_FINAL;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.PUBLIC_FINAL_CLASS;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.SUPPRESS_UNUSED;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.TAB_1;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.TAB_2;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.TAB_3;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.argument;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.assignment;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.call;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.decapitalize;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.fieldDeclaration;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.imports;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.methodDeclaration;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.removeUnderscores;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.stringify;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.toMemberName;

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

    private static final String[] EMPTY_ARGS = new String[0];
    private static final String WRAPPER = "Wrapper";

    private static final String STRUCTS_PACKAGE = "com.oracle.truffle.espresso.vm.structs";

    // Annotations
    private static final String GENERATE_STRUCTS = STRUCTS_PACKAGE + ".GenerateStructs";
    private static final String KNOWN_STRUCT = GENERATE_STRUCTS + ".KnownStruct";

    // Imports
    private static final String IMPORTED_BYTEBUFFER = imports("java.nio.ByteBuffer");
    private static final String IMPORTED_JNI_ENV = imports("com.oracle.truffle.espresso.jni.JniEnv");
    private static final String IMPORTED_RAW_POINTER = imports("com.oracle.truffle.espresso.ffi.RawPointer");

    // Classes
    private static final String TRUFFLE_OBJECT = "TruffleObject";
    private static final String INTEROP_LIBRARY = "InteropLibrary";
    private static final String JNI_ENV_CLASS = "JniEnv";
    private static final String MEMBER_OFFSET_GETTER_CLASS = "MemberOffsetGetter";
    private static final String NATIVE_MEMBER_OFFSET_GETTER_CLASS = "NativeMemberOffsetGetter";
    private static final String JAVA_MEMBER_OFFSET_GETTER_CLASS = "JavaMemberOffsetGetter";
    private static final String STRUCT_WRAPPER_CLASS = "StructWrapper";
    private static final String STRUCT_STORAGE_CLASS = "StructStorage";
    private static final String STRUCTS_CLASS = "Structs";

    // Methods
    private static final String GET_INFO = "getInfo";
    private static final String GET_OFFSET = "getOffset";
    private static final String GET = "get";
    private static final String PUT = "put";
    private static final String GET_UNCACHED = "getUncached";

    // Members
    private static final String STRUCT_SIZE = "structSize";

    // Arguments
    private static final String VALUE = "valueToPut";
    private static final String JNI_ENV_ARG = "jni";
    private static final String PTR = "pointer";
    private static final String MEMBER_OFFSET_GETTER_ARG = "offsetGetter";
    private static final String MEMBER_INFO_PTR = "memberInfoPtr";
    private static final String LOOKUP_MEMBER_OFFSET = "lookupMemberOffset";
    private static final String LIBRARY = "library";

    // Modifiers
    private static final String PUBLIC = "public";
    private static final String FINAL = "final";

    // Known structs
    private static final String MEMBER_INFO = "MemberInfo";

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

        List<String> structs = new ArrayList<>();

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
                String className = processStruct(strName, memberNamesList, typesList);
                structs.add(className);
            }
        }
        String source = generateStructCollector(structs);
        commit(null, STRUCTS_CLASS, source);

        done = true;

        return false;
    }

    private String processStruct(String strName, List<?> memberNamesList, List<?> typesList) {
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

        String className = ProcessorUtils.removeUnderscores(strName); // Replace '_j' with 'J'
        // Generate code
        String source = generateStruct(strName, members, nativeTypes, length, className);
        // Commit and write to files.
        commit(null, className, source);
        return className;
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
        generateStructHeader(builder);

        String wrapperName = className + WRAPPER;

        // Java struct declaration
        builder.append(SUPPRESS_UNUSED).append("\n");
        builder.append(PUBLIC_FINAL_CLASS).append(className).append(" extends ").append(STRUCT_STORAGE_CLASS) //
                        .append("<").append(className).append(".").append(wrapperName).append(">") //
                        .append(" {\n");

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
        generateWrapper(members, typesList, length, builder, wrapperName);
        builder.append("\n");

        generateWrapMethod(builder, wrapperName);

        builder.append("}\n");

        return builder.toString();
    }

    private static void generateWrapMethod(StringBuilder builder, String wrapperClass) {
        builder.append(TAB_1).append(OVERRIDE).append("\n");
        builder.append(TAB_1).append(methodDeclaration(PUBLIC, wrapperClass, "wrap",
                        new String[]{argument(JNI_ENV_CLASS, JNI_ENV_ARG), argument(TRUFFLE_OBJECT, PTR)})).append(" {\n");
        builder.append(TAB_2).append("return ").append("new ").append(call(null, wrapperClass, new String[]{JNI_ENV_ARG, PTR})).append(";\n");
        builder.append(TAB_1).append("}\n");
    }

    private static void generateWrapper(List<String> members, List<NativeType> typesList, int length, StringBuilder builder, String wrapperName) {
        builder.append(TAB_1).append(PUBLIC_FINAL_CLASS).append(wrapperName).append(" extends ").append(STRUCT_WRAPPER_CLASS).append(" {\n");
        generateWrapperConstructor(builder, wrapperName);
        for (int i = 0; i < length; i++) {
            String member = members.get(i);
            NativeType type = typesList.get(i);
            generateGetterSetter(builder, member, type);
        }
        builder.append(TAB_1).append("}\n");
    }

    private static void generateWrapperConstructor(StringBuilder builder, String wrapperName) {
        builder.append(TAB_2).append(methodDeclaration(null, null, wrapperName,
                        new String[]{argument(JNI_ENV_CLASS, JNI_ENV_ARG), argument(TRUFFLE_OBJECT, PTR)})).append("{\n");
        builder.append(TAB_3).append(call(null, "super", new String[]{JNI_ENV_ARG, PTR, STRUCT_SIZE})).append(";\n");
        builder.append(TAB_2).append("}\n\n");
    }

    private static void generateGetterSetter(StringBuilder builder, String member, NativeType type) {
        String callSuffix = nativeTypeToMethodSuffix(type);
        String argType = nativeTypeToArgType(type);
        String methodName = toMemberName(removeUnderscores(member));
        builder.append(TAB_2).append(methodDeclaration(PUBLIC, argType, methodName, new String[]{})).append(" {\n");
        builder.append(TAB_3).append("return ").append(call(null, GET + callSuffix, new String[]{member})).append(";\n");
        builder.append(TAB_2).append("}\n\n");

        builder.append(TAB_2).append(methodDeclaration(PUBLIC, "void", methodName, new String[]{argument(argType, VALUE)})).append(" {\n");
        builder.append(TAB_3).append(call(null, PUT + callSuffix, new String[]{member, VALUE})).append(";\n");
        builder.append(TAB_2).append("}\n\n");
    }

    private static void generateStructHeader(StringBuilder builder) {
        builder.append(COPYRIGHT);
        builder.append("package ").append(STRUCTS_PACKAGE).append(";\n\n");
        builder.append('\n');
        builder.append(IMPORTED_BYTEBUFFER);
        builder.append('\n');
        builder.append(imports(IMPORT_TRUFFLE_OBJECT));
        builder.append('\n');
        builder.append(imports(IMPORT_STATIC_OBJECT));
        builder.append(IMPORTED_JNI_ENV);
        builder.append(IMPORTED_RAW_POINTER);
        builder.append('\n');
    }

    private static void generateFields(List<String> members, StringBuilder builder) {
        // builder.append(TAB_1).append(fieldDeclaration(FINAL, "long", STRUCT_SIZE,
        // null)).append("\n\n");
        for (String member : members) {
            builder.append(TAB_1).append(fieldDeclaration(FINAL, "int", member, null)).append("\n");
        }
        builder.append('\n');
    }

    private static void generateConstructor(String strName, List<String> members, StringBuilder builder, String className) {
        builder.append(TAB_1).append(methodDeclaration(null, null, className, new String[]{argument(MEMBER_OFFSET_GETTER_CLASS, MEMBER_OFFSET_GETTER_ARG)}));
        builder.append(" {\n");
        builder.append(TAB_2).append(call(null, "super", new String[]{call(MEMBER_OFFSET_GETTER_ARG, GET_INFO, new String[]{stringify(strName)})}));
        builder.append(";\n");
        for (String member : members) {
            builder.append(TAB_2).append(assignment(member,
                            "(int) " + call(MEMBER_OFFSET_GETTER_ARG, GET_OFFSET, new String[]{stringify(strName), stringify(member)}))).append("\n");
        }

        builder.append(TAB_1).append("}\n\n");
    }

    private static String generateStructCollector(List<String> structs) {
        StringBuilder builder = new StringBuilder();
        builder.append(generateCollectorHeader());
        builder.append(PUBLIC_FINAL_CLASS).append(STRUCTS_CLASS).append(" {\n");
        generateCollectorFieldDeclaration(structs, builder);
        builder.append("\n");
        generateColectorConstructor(structs, builder);
        builder.append("}\n");
        return builder.toString();
    }

    private static String generateCollectorHeader() {
        StringBuilder builder = new StringBuilder();
        builder.append(COPYRIGHT);
        builder.append("package ").append(STRUCTS_PACKAGE).append(";\n\n");
        builder.append('\n');
        builder.append(imports(IMPORT_INTEROP_LIBRARY));
        builder.append(imports(IMPORT_TRUFFLE_OBJECT));
        builder.append('\n');
        builder.append(IMPORTED_JNI_ENV);
        builder.append('\n');
        return builder.toString();
    }

    private static void generateCollectorFieldDeclaration(List<String> structs, StringBuilder builder) {
        for (String struct : structs) {
            builder.append(TAB_1).append(fieldDeclaration(PUBLIC_FINAL, struct, decapitalize(struct), null));
            builder.append("\n");
        }
    }

    private static void generateColectorConstructor(List<String> structs, StringBuilder builder) {
        builder.append(TAB_1).append(methodDeclaration(PUBLIC, null, STRUCTS_CLASS,
                        new String[]{argument(JNI_ENV_CLASS, JNI_ENV_ARG), argument(TRUFFLE_OBJECT, MEMBER_INFO_PTR), argument(TRUFFLE_OBJECT, LOOKUP_MEMBER_OFFSET)})).append(" {\n");
        builder.append(TAB_2).append(INTEROP_LIBRARY).append(" ").append(assignment(LIBRARY, call(INTEROP_LIBRARY, GET_UNCACHED, EMPTY_ARGS))).append("\n");
        builder.append(TAB_2).append(MEMBER_OFFSET_GETTER_CLASS).append(" ").append(assignment(MEMBER_OFFSET_GETTER_ARG,
                        "new " + call(null, NATIVE_MEMBER_OFFSET_GETTER_CLASS, new String[]{LIBRARY, MEMBER_INFO_PTR, LOOKUP_MEMBER_OFFSET}))).append("\n");

        optionalMemberInfo(structs, builder);

        for (String struct : structs) {
            builder.append(TAB_2).append(assignment(decapitalize(struct), "new " + call(null, struct, new String[]{MEMBER_OFFSET_GETTER_ARG})));
            builder.append("\n");
        }
        builder.append(TAB_1).append("}\n");
    }

    private static void optionalMemberInfo(List<String> structs, StringBuilder builder) {
        if (structs.contains(MEMBER_INFO)) {
            builder.append(TAB_2).append(assignment(decapitalize(MEMBER_INFO), "new " + call(null, MEMBER_INFO, new String[]{MEMBER_OFFSET_GETTER_ARG})));
            builder.append("\n");
            structs.remove(MEMBER_INFO);
            builder.append(TAB_2).append(assignment(MEMBER_OFFSET_GETTER_ARG, "new " + call(null, JAVA_MEMBER_OFFSET_GETTER_CLASS,
                            new String[]{JNI_ENV_ARG, MEMBER_INFO_PTR, "this"}))).append("\n");
        }
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
