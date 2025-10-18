/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import jdk.graal.compiler.processor.AbstractProcessor;

// Checkstyle: allow Class.getSimpleName

/**
 * Annotation processor for the @AutomaticallyRegisteredImageSingleton annotation.
 */
@SupportedAnnotationTypes(AutomaticallyRegisteredImageSingletonProcessor.ANNOTATION_CLASS_NAME)
public class AutomaticallyRegisteredImageSingletonProcessor extends AbstractProcessor {

    static final String ANNOTATION_CLASS_NAME = "com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton";
    static final String LAYERED_SINGLETON_INFO = "com.oracle.svm.core.layeredimagesingleton.LoadedLayeredImageSingletonInfo";

    private final Set<Element> processed = new HashSet<>();

    private void processElement(TypeElement annotatedType) {
        String featureClassName = getTypeNameWithEnclosingClasses(annotatedType, "Feature");
        String packageName = getPackage(annotatedType).getQualifiedName().toString();

        try (PrintWriter out = createSourceFile(packageName, featureClassName, processingEnv.getFiler(), annotatedType)) {
            AnnotationMirror singletonAnnotation = getAnnotation(annotatedType, getType(ANNOTATION_CLASS_NAME));
            AnnotationMirror platformsAnnotation = getAnnotation(annotatedType, getType("org.graalvm.nativeimage.Platforms"));

            String classPlatformsAnnotation = "";
            String classPlatformsImport = "";
            if (platformsAnnotation != null) {
                String platforms = getAnnotationValueList(platformsAnnotation, "value", TypeMirror.class).stream().map(type -> type.toString() + ".class").collect(Collectors.joining(", "));
                classPlatformsAnnotation = System.lineSeparator() + "@Platforms({" + platforms + "})";
                classPlatformsImport = System.lineSeparator() + "import org.graalvm.nativeimage.Platforms;";
            }

            List<TypeElement> singletonSuperclasses = getSingletonSuperclasses(annotatedType);
            String supertypes = singletonSuperclasses.isEmpty()
                            ? "implements " + AutomaticallyRegisteredFeatureProcessor.FEATURE_INTERFACE_CLASS_NAME
                            : "extends " + getPackage(singletonSuperclasses.get(0)).getQualifiedName().toString() + "." + getTypeNameWithEnclosingClasses(singletonSuperclasses.get(0), "Feature");

            StringBuilder afterRegistrationBody = new StringBuilder();
            List<TypeMirror> onlyWithList = getAnnotationValueList(singletonAnnotation, "onlyWith", TypeMirror.class);
            if (!onlyWithList.isEmpty()) {
                for (var onlyWith : onlyWithList) {
                    String onlyWithPredicate = """
                                    if (!new %1$s().getAsBoolean()) {
                                        %2$sreturn;
                                    }
                                    """
                                    .formatted(onlyWith, singletonSuperclasses.isEmpty() ? "" : "super.afterRegistration(access);" + System.lineSeparator());
                    afterRegistrationBody.append(onlyWithPredicate.indent(8));
                }
            }

            List<TypeMirror> keysFromAnnotation = getAnnotationValueList(singletonAnnotation, "value", TypeMirror.class);
            for (var superclass : singletonSuperclasses) {
                AnnotationMirror superclassAnnotation = getAnnotation(superclass, getType(ANNOTATION_CLASS_NAME));
                keysFromAnnotation.addAll(getAnnotationValueList(superclassAnnotation, "value", TypeMirror.class));
            }

            String mainBody;
            if (keysFromAnnotation.isEmpty()) {
                String keyname = annotatedType + ".class";
                mainBody = """
                                if (ImageSingletons.lookup(%1$s.class).handledDuringLoading(%2$s)) {
                                    return;
                                }
                                var singleton = new %3$s();
                                ImageSingletons.add(%2$s, singleton);"""
                                .formatted(LAYERED_SINGLETON_INFO, keyname, annotatedType);
            } else {

                String handledDuringLoadingTemplate = "unhandled = unhandled || !ImageSingletons.lookup(%1$s.class).handledDuringLoading(%2$s);";
                String installSingletonTemplate = """
                                if (!ImageSingletons.lookup(%1$s.class).handledDuringLoading(%2$s)) {
                                    ImageSingletons.add(%2$s, singleton);
                                }
                                """;
                StringBuilder handledDuringLoadingContent = new StringBuilder();
                StringBuilder installSingletonContent = new StringBuilder();
                for (var keyFromAnnotation : keysFromAnnotation) {
                    String keyname = keyFromAnnotation + ".class";
                    handledDuringLoadingContent.append(handledDuringLoadingTemplate.formatted(LAYERED_SINGLETON_INFO, keyname)).append(System.lineSeparator());
                    installSingletonContent.append(installSingletonTemplate.formatted(LAYERED_SINGLETON_INFO, keyname));
                }
                mainBody = """
                                // checks for if the singleton was not already handled during loading
                                boolean unhandled = false;
                                %1$sif (!unhandled) {
                                    return;
                                }

                                var singleton = new %2$s();

                                // adding the singleton to all keys not handled during loading
                                %3$s"""
                                .formatted(handledDuringLoadingContent.toString(), annotatedType, installSingletonContent.toString());
            }
            afterRegistrationBody.append(mainBody.indent(8));

            String javaClass = """
                            // CheckStyle: stop header check
                            // CheckStyle: stop line length check
                            package %1$s;

                            // GENERATED CONTENT - DO NOT EDIT
                            // Annotated type: %2$s
                            // Annotation: com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton
                            // Annotation processor: com.oracle.svm.processor.AutomaticallyRegisteredImageSingletonProcessor

                            import org.graalvm.nativeimage.ImageSingletons;

                            import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
                            import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
                            import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
                            import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
                            import com.oracle.svm.core.traits.SingletonTraits;%3$s

                            @AutomaticallyRegisteredFeature%4$s
                            @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
                            public class %5$s %6$s {
                                @Override
                                public void afterRegistration(AfterRegistrationAccess access) {
                            %7$s    }
                            }"""
                            .formatted(
                                            packageName,
                                            annotatedType,
                                            classPlatformsImport,
                                            classPlatformsAnnotation,
                                            featureClassName,
                                            supertypes,
                                            afterRegistrationBody);
            out.print(javaClass);
        }
    }

    /**
     * Get the inheritance chain from {@code annotatedType} up to (excluding) the first
     * non-{@code @AutomaticallyRegisteredImageSingleton} type.
     *
     * @return a list ordered from the most specific to the least specific, empty if not even the
     *         direct superclass is annotated
     */
    private List<TypeElement> getSingletonSuperclasses(TypeElement annotatedType) {
        List<TypeElement> list = new ArrayList<>();
        for (TypeElement curr = annotatedType;;) {
            TypeMirror next = curr.getSuperclass();
            if (next.getKind() != TypeKind.DECLARED) {
                break;
            }
            curr = (TypeElement) ((DeclaredType) next).asElement();
            if (getAnnotation(curr, getType(ANNOTATION_CLASS_NAME)) == null) {
                break;
            }
            list.add(curr);
        }
        return list;
    }

    /**
     * We allow inner classes to be annotated. To make the generated service name unique, we need to
     * concatenate the simple names of all outer classes.
     */
    static String getTypeNameWithEnclosingClasses(TypeElement annotatedType, String suffix) {
        String result = suffix;
        for (Element cur = annotatedType; cur instanceof TypeElement; cur = cur.getEnclosingElement()) {
            result = cur.getSimpleName().toString() + result;
        }
        return result;
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(getTypeElement(ANNOTATION_CLASS_NAME))) {
            assert element.getKind().isClass();
            if (processed.add(element)) {
                processElement((TypeElement) element);
            }
        }
        return true;
    }
}
