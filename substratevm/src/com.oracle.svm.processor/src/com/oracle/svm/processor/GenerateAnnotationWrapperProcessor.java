/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.processor;

// Checkstyle: allow Class.getSimpleName

import static javax.tools.Diagnostic.Kind.ERROR;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import jdk.graal.compiler.processor.AbstractProcessor;

/**
 * Generates immutable guest-side representations of annotations declared by
 * {@code GenerateAnnotationWrapper}.
 */
@SupportedAnnotationTypes(GenerateAnnotationWrapperProcessor.ANNOTATION_CLASS_NAME)
public class GenerateAnnotationWrapperProcessor extends AbstractProcessor {

    static final String ANNOTATION_CLASS_NAME = "com.oracle.svm.common.annotation.GenerateAnnotationWrapper";
    private static final String ANNOTATION_VALUE_CLASS_NAME = "jdk.graal.compiler.annotation.AnnotationValue";
    private static final String ANNOTATED_CLASS_NAME = "jdk.vm.ci.meta.annotation.Annotated";
    private static final String GUEST_ANNOTATION_ACCESS_CLASS_NAME = "com.oracle.svm.util.GuestAnnotationAccess";
    private static final String LIST_CLASS_NAME = "java.util.List";
    private static final String PLATFORM_CLASS_NAME = "org.graalvm.nativeimage.Platform";
    private static final String PLATFORMS_CLASS_NAME = "org.graalvm.nativeimage.Platforms";
    private static final String RESOLVED_JAVA_TYPE_CLASS_NAME = "jdk.vm.ci.meta.ResolvedJavaType";

    private final Set<Element> processed = new HashSet<>();

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        TypeElement annotationType = getTypeElement(ANNOTATION_CLASS_NAME);
        for (var element : roundEnv.getElementsAnnotatedWith(annotationType)) {
            if (processed.add(element)) {
                AnnotationMirror annotationMirror = getAnnotation(element, annotationType.asType());
                processAnnotation(element, annotationMirror);
            }
        }

        return true;
    }

    private record AnnotationWrapperMember(String name, String type, String initializer, Set<String> requiredImports) {
    }

    /**
     * Generates wrappers for the annotation types listed by one package-level marker.
     */
    @SuppressWarnings("unchecked")
    private void processAnnotation(Element annotatedElement, AnnotationMirror annotationMirror) {
        TypeMirror annotationTypeMirror = processingEnv.getElementUtils()
                        .getTypeElement("java.lang.annotation.Annotation").asType();
        assert annotatedElement.getKind() == ElementKind.PACKAGE : "Only packages are supported: " + annotatedElement;
        PackageElement packageElement = (PackageElement) annotatedElement;
        List<? extends AnnotationValue> value = getAnnotationValue(annotationMirror, "value", List.class);
        for (AnnotationValue annotationValue : value) {
            if (annotationValue.getValue() instanceof TypeMirror typeMirror) {
                generateWrapper(typeMirror, annotationTypeMirror, packageElement);
            } else {
                throw new IllegalArgumentException("Expected an annotation type but got " + annotationValue.getValue().getClass().getName());
            }
        }
    }

    /**
     * Collects the members of an annotation type and writes its wrapper to the target package.
     */
    private void generateWrapper(TypeMirror typeMirror, TypeMirror annotationTypeMirror, PackageElement targetPackage) {
        assert processingEnv.getTypeUtils().isSubtype(typeMirror, annotationTypeMirror) : "Not an annotation: " + typeMirror;

        TypeElement annotationType = (TypeElement) processingEnv.getTypeUtils().asElement(typeMirror);
        List<AnnotationWrapperMember> members = annotationType.getEnclosedElements().stream()
                        .filter(element -> element.getKind() == ElementKind.METHOD)
                        .map(ExecutableElement.class::cast)
                        .sorted(Comparator.comparing((ExecutableElement member) -> member.getDefaultValue() != null)
                                        .thenComparing(member -> member.getSimpleName().toString()))
                        .map(this::createMember)
                        .toList();
        String packageName = targetPackage.getQualifiedName().toString();
        String generatedClassName = annotationType.getSimpleName() + "GuestValue";
        String content = renderSource(packageName, generatedClassName, annotationType, targetPackage, members);
        writeSource(packageName, generatedClassName, annotationType, targetPackage, content);
    }

    /**
     * Renders a wrapper record and its factory method.
     */
    private String renderSource(String packageName, String generatedClassName, TypeElement annotationType, PackageElement targetPackage, List<AnnotationWrapperMember> members) {
        String recordMembers = members.stream().map(m -> m.type + " " + m.name).collect(Collectors.joining(", "));
        String memberInitializers = members.stream().map(AnnotationWrapperMember::initializer).collect(Collectors.joining(",\n                "));
        String imports = Stream.concat(
                        Stream.of(ANNOTATION_VALUE_CLASS_NAME, ANNOTATED_CLASS_NAME, GUEST_ANNOTATION_ACCESS_CLASS_NAME, PLATFORM_CLASS_NAME, PLATFORMS_CLASS_NAME),
                        members.stream().flatMap(member -> member.requiredImports().stream()))
                        .distinct()
                        .sorted()
                        .map("import %s;"::formatted)
                        .collect(Collectors.joining("\n"));
        return """
                        // CheckStyle: stop header check
                        // CheckStyle: stop line length check
                        package %3$s;

                        // GENERATED CONTENT - DO NOT EDIT
                        // Annotation processor: %4$s
                        // Source File: %7$s
                        // Annotated Element: %8$s

                        // imports
                        %5$s

                        public record %1$s(%2$s) {

                            @Platforms(Platform.HOSTED_ONLY.class)
                            public static %1$s get(Annotated element) {
                                return GuestAnnotationAccess.getAnnotationValue(element, %9$s.class, %1$s::from);
                            }

                            public static %1$s from(AnnotationValue annotationValue) {
                                if (annotationValue == null) {
                                    return null;
                                }
                                return new %1$s(
                                        %6$s);
                            }
                        }""".formatted(
                        generatedClassName,
                        recordMembers,
                        packageName,
                        getClass().getName(),
                        imports,
                        memberInitializers,
                        annotationType.getQualifiedName(),
                        targetPackage.getQualifiedName(),
                        annotationType.getQualifiedName());
    }

    /**
     * Writes a generated wrapper and associates it with its originating elements.
     */
    private void writeSource(String packageName, String generatedClassName, TypeElement annotationType, PackageElement targetPackage, String content) {
        try {
            JavaFileObject file = processingEnv.getFiler().createSourceFile(packageName + "." + generatedClassName, targetPackage, annotationType);
            try (OutputStreamWriter out = new OutputStreamWriter(file.openOutputStream(), StandardCharsets.UTF_8)) {
                out.write(content);
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(isBug367599(e) ? Diagnostic.Kind.NOTE : ERROR, e.getMessage(), annotationType);
        }
    }

    /**
     * Maps an annotation member to a record component and its value initializer.
     */
    private AnnotationWrapperMember createMember(ExecutableElement member) {
        String name = member.getSimpleName().toString();
        TypeMirror returnType = member.getReturnType();
        if (returnType.getKind().isPrimitive()) {
            String type = returnType.toString();
            String getter = "get" + Character.toUpperCase(type.charAt(0)) + type.substring(1);
            return member(name, type, getter);
        }
        return switch (returnType.getKind()) {
            case DECLARED -> createDeclaredMember(name, returnType);
            case ARRAY -> createArrayMember(name, (ArrayType) returnType);
            default -> throw unsupportedType(returnType);
        };
    }

    /**
     * Maps a supported declared annotation member type (Enum, String, Class).
     */
    private AnnotationWrapperMember createDeclaredMember(String name, TypeMirror returnType) {
        TypeElement returnTypeElement = (TypeElement) processingEnv.getTypeUtils().asElement(returnType);
        if (returnTypeElement.getKind() == ElementKind.ENUM) {
            // Preserve the enum type so the generated record remains strongly typed.
            String type = returnType.toString();
            return member(name, type, "getEnum", type + ".class");
        }
        return switch (returnTypeElement.getQualifiedName().toString()) {
            case "java.lang.String" -> member(name, returnType.toString(), "getString");
            case "java.lang.Class" -> member(name, "ResolvedJavaType", "getType", Set.of(RESOLVED_JAVA_TYPE_CLASS_NAME));
            default -> throw unsupportedType(returnType);
        };
    }

    /**
     * Maps an annotation array to the list representation used by the generated wrapper.
     */
    private AnnotationWrapperMember createArrayMember(String name, ArrayType returnType) {
        TypeMirror componentType = returnType.getComponentType();
        if (isType("java.lang.String", componentType)) {
            return member(name, "List<String>", "getList", Set.of(LIST_CLASS_NAME), "String.class");
        }
        if (isType("java.lang.Class", componentType)) {
            return member(name, "List<ResolvedJavaType>", "getList", Set.of(LIST_CLASS_NAME, RESOLVED_JAVA_TYPE_CLASS_NAME), "ResolvedJavaType.class");
        }
        // Keep other component types wildcarded until a generated wrapper needs stronger typing.
        return member(name, "List<?>", "getList", Set.of(LIST_CLASS_NAME), "Object.class");
    }

    /**
     * Creates a member whose initializer invokes the matching guest annotation value accessor.
     */
    private static AnnotationWrapperMember member(String name, String type, String getter, String... additionalArgs) {
        return member(name, type, getter, Set.of(), additionalArgs);
    }

    /**
     * Creates a member with the imports required by its generated type and initializer.
     */
    private static AnnotationWrapperMember member(String name, String type, String getter, Set<String> requiredImports, String... additionalArgs) {
        String arguments = Stream.concat(Stream.of("\"" + name + "\""), Arrays.stream(additionalArgs)).collect(Collectors.joining(", "));
        String initializer = "annotationValue.%s(%s)".formatted(getter, arguments);
        return new AnnotationWrapperMember(name, type, initializer, requiredImports);
    }

    /**
     * Tests type equality after erasure, allowing parameterized types such as {@code Class<?>}.
     */
    private boolean isType(String expectedType, TypeMirror type) {
        TypeMirror expected = processingEnv.getElementUtils().getTypeElement(expectedType).asType();
        return processingEnv.getTypeUtils().isSameType(processingEnv.getTypeUtils().erasure(type), processingEnv.getTypeUtils().erasure(expected));
    }

    /**
     * Creates a consistent error for unsupported annotation member types.
     */
    private static IllegalArgumentException unsupportedType(TypeMirror type) {
        return new IllegalArgumentException("Unsupported annotation member type: " + type);
    }
}
