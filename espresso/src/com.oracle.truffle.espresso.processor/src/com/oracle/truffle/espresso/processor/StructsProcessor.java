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

import static com.oracle.truffle.espresso.processor.EspressoProcessor.IMPORT_INTEROP_LIBRARY;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.IMPORT_STATIC_OBJECT;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.IMPORT_TRUFFLE_OBJECT;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.SUPPRESS_WARNINGS;
import static com.oracle.truffle.espresso.processor.EspressoProcessor.UNUSED;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.argument;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.assignment;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.call;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.decapitalize;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.removeUnderscores;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.stringify;
import static com.oracle.truffle.espresso.processor.ProcessorUtils.toMemberName;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import com.oracle.truffle.espresso.processor.builders.AnnotationBuilder;
import com.oracle.truffle.espresso.processor.builders.ClassBuilder;
import com.oracle.truffle.espresso.processor.builders.ClassFileBuilder;
import com.oracle.truffle.espresso.processor.builders.FieldBuilder;
import com.oracle.truffle.espresso.processor.builders.MethodBuilder;
import com.oracle.truffle.espresso.processor.builders.ModifierBuilder;

public class StructsProcessor extends AbstractProcessor {

    private static final String[] EMPTY_ARGS = new String[0];
    private static final String WRAPPER = "Wrapper";

    private static final String STRUCTS_PACKAGE = "com.oracle.truffle.espresso.vm.structs";

    // Annotations
    private static final String GENERATE_STRUCTS = STRUCTS_PACKAGE + ".GenerateStructs";
    private static final String KNOWN_STRUCT = GENERATE_STRUCTS + ".KnownStruct";

    // Imports
    private static final String IMPORT_BYTEBUFFER = "java.nio.ByteBuffer";
    private static final String IMPORT_JNI_ENV = "com.oracle.truffle.espresso.jni.JniEnv";
    private static final String IMPORT_RAW_POINTER = "com.oracle.truffle.espresso.ffi.RawPointer";

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
        commit(STRUCTS_CLASS, source);

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
        commit(className, source);
        return className;
    }

    void commit(String className, String classFile) {
        try {
            // Create the file
            JavaFileObject file = processingEnv.getFiler().createSourceFile(STRUCTS_PACKAGE + "." + className);
            Writer wr = file.openWriter();
            wr.write(classFile);
            wr.close();
        } catch (IOException ex) {
            /* nop */
        }
    }

    private static String generateStruct(String strName, List<String> members, List<NativeType> typesList, int length, String className) {
        ClassFileBuilder structFile = new ClassFileBuilder() //
                        .withCopyright() //
                        .inPackage(STRUCTS_PACKAGE) //
                        .withImportGroup(Collections.singletonList(IMPORT_BYTEBUFFER)) //
                        .withImportGroup(Collections.singletonList(IMPORT_TRUFFLE_OBJECT)) //
                        .withImportGroup(Arrays.asList(IMPORT_STATIC_OBJECT, IMPORT_JNI_ENV, IMPORT_RAW_POINTER));

        String wrapperName = className + WRAPPER;

        // Java struct declaration
        ClassBuilder struct = new ClassBuilder(className) //
                        .withAnnotation(new AnnotationBuilder(SUPPRESS_WARNINGS).withValue("value", UNUSED)) //
                        .withQualifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withSuperClass(STRUCT_STORAGE_CLASS + "<" + className + "." + wrapperName + ">");

        // Generate the fields:
        // - One to store the struct size
        // - One per member to store their offsets
        generateFields(struct, members);

        // Generate the constructor.
        // Use passed MemberOffsetGetter.
        generateConstructor(struct, strName, members, className);

        // Generate the wrapper structure that will access the native struct java-like.
        // It simply wraps a byte buffer around the pointer, and uses offsets in parent class to
        // perform accesses.
        generateWrapper(struct, members, typesList, length, wrapperName);

        generateWrapMethod(struct, wrapperName);

        structFile.withClass(struct);
        return structFile.build();
    }

    private static void generateFields(ClassBuilder struct, List<String> members) {
        for (String member : members) {
            struct.withField(new FieldBuilder("int", member).withQualifiers(new ModifierBuilder().asFinal()));
        }
    }

    private static void generateConstructor(ClassBuilder struct, String strName, List<String> members, String className) {
        MethodBuilder constructor = new MethodBuilder(className) //
                        .withParams(argument(MEMBER_OFFSET_GETTER_CLASS, MEMBER_OFFSET_GETTER_ARG)) //
                        .asConstructor();

        constructor.addBodyLine("super(", call(MEMBER_OFFSET_GETTER_ARG, GET_INFO, new String[]{stringify(strName)}), ");");
        for (String member : members) {
            constructor.addBodyLine(assignment(member, "(int) " + call(MEMBER_OFFSET_GETTER_ARG, GET_OFFSET, new String[]{stringify(strName), stringify(member)})));
        }

        struct.withMethod(constructor);
    }

    private static void generateWrapper(ClassBuilder struct, List<String> members, List<NativeType> typesList, int length, String wrapperName) {
        ClassBuilder wrapper = new ClassBuilder(wrapperName) //
                        .withQualifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withSuperClass(STRUCT_WRAPPER_CLASS);

        MethodBuilder wrapperConstructor = new MethodBuilder(wrapperName) //
                        .asConstructor() //
                        .withParams(argument(JNI_ENV_CLASS, JNI_ENV_ARG), argument(TRUFFLE_OBJECT, PTR)) //
                        .addBodyLine(call(null, "super", new String[]{JNI_ENV_ARG, PTR, STRUCT_SIZE}), ';');
        wrapper.withMethod(wrapperConstructor);

        for (int i = 0; i < length; i++) {
            String member = members.get(i);
            NativeType type = typesList.get(i);

            String callSuffix = nativeTypeToMethodSuffix(type);
            String argType = nativeTypeToArgType(type);
            String methodName = toMemberName(removeUnderscores(member));

            MethodBuilder getter = new MethodBuilder(methodName) //
                            .withModifiers(new ModifierBuilder().asPublic()) //
                            .withReturnType(argType) //
                            .addBodyLine("return ", call(null, GET + callSuffix, new String[]{member}), ';');
            MethodBuilder setter = new MethodBuilder(methodName) //
                            .withModifiers(new ModifierBuilder().asPublic()) //
                            .withParams(argument(argType, VALUE)) //
                            .addBodyLine(call(null, PUT + callSuffix, new String[]{member, VALUE}), ';');
            wrapper.withMethod(getter);
            wrapper.withMethod(setter);
        }

        struct.withInnerClass(wrapper);
    }

    private static void generateWrapMethod(ClassBuilder struct, String wrapperClass) {
        MethodBuilder wrapMethod = new MethodBuilder("wrap") //
                        .withOverrideAnnotation() //
                        .withModifiers(new ModifierBuilder().asPublic()) //
                        .withReturnType(wrapperClass) //
                        .withParams(argument(JNI_ENV_CLASS, JNI_ENV_ARG), argument(TRUFFLE_OBJECT, PTR)) //
                        .addBodyLine("return new ", call(null, wrapperClass, new String[]{JNI_ENV_ARG, PTR}), ';');
        struct.withMethod(wrapMethod);
    }

    private static String generateStructCollector(List<String> structs) {
        ClassFileBuilder structCollectorFile = new ClassFileBuilder() //
                        .withCopyright() //
                        .inPackage(STRUCTS_PACKAGE) //
                        .withImportGroup(Arrays.asList(IMPORT_INTEROP_LIBRARY, IMPORT_TRUFFLE_OBJECT)) //
                        .withImportGroup(Collections.singletonList(IMPORT_JNI_ENV));

        ClassBuilder structCollector = new ClassBuilder(STRUCTS_CLASS) //
                        .withQualifiers(new ModifierBuilder().asPublic().asFinal());

        generateCollectorFieldDeclaration(structCollector, structs);
        generateColectorConstructor(structCollector, structs);

        structCollectorFile.withClass(structCollector);
        return structCollectorFile.build();
    }

    private static void generateCollectorFieldDeclaration(ClassBuilder collector, List<String> structs) {
        for (String struct : structs) {
            collector.withField(new FieldBuilder(struct, decapitalize(struct)).withQualifiers(new ModifierBuilder().asPublic().asFinal()));
        }
    }

    private static void generateColectorConstructor(ClassBuilder collector, List<String> structs) {
        MethodBuilder constructor = new MethodBuilder(STRUCTS_CLASS) //
                        .asConstructor() //
                        .withModifiers(new ModifierBuilder().asPublic()) //
                        .withParams(argument(JNI_ENV_CLASS, JNI_ENV_ARG), argument(TRUFFLE_OBJECT, MEMBER_INFO_PTR), argument(TRUFFLE_OBJECT, LOOKUP_MEMBER_OFFSET)) //
                        .addBodyLine(INTEROP_LIBRARY, ' ', assignment(LIBRARY, call(INTEROP_LIBRARY, GET_UNCACHED, EMPTY_ARGS))) //
                        .addBodyLine(MEMBER_OFFSET_GETTER_CLASS, ' ', assignment(MEMBER_OFFSET_GETTER_ARG,
                                        "new " + call(null, NATIVE_MEMBER_OFFSET_GETTER_CLASS, new String[]{LIBRARY, MEMBER_INFO_PTR, LOOKUP_MEMBER_OFFSET})));

        generateOptionalMemberInfo(constructor, structs);

        for (String struct : structs) {
            constructor.addBodyLine(assignment(decapitalize(struct), "new " + call(null, struct, new String[]{MEMBER_OFFSET_GETTER_ARG})));
        }

        collector.withMethod(constructor);
    }

    private static void generateOptionalMemberInfo(MethodBuilder constructor, List<String> structs) {
        if (structs.contains(MEMBER_INFO)) {
            constructor.addBodyLine(assignment(decapitalize(MEMBER_INFO), "new " + call(null, MEMBER_INFO, new String[]{MEMBER_OFFSET_GETTER_ARG})));
            structs.remove(MEMBER_INFO);
            constructor.addBodyLine(assignment(MEMBER_OFFSET_GETTER_ARG, "new " + call(null, JAVA_MEMBER_OFFSET_GETTER_CLASS,
                            new String[]{JNI_ENV_ARG, MEMBER_INFO_PTR, "this"})));
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
