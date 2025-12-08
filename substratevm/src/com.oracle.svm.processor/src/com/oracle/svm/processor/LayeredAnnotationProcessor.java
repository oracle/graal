/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintWriter;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import jdk.graal.compiler.processor.AbstractProcessor;

/**
 * During layered builds, methods with LayeredCompilationBehaviors set to PINNED_TO_INITIAL_LAYER
 * are required to be explicitly registered during setup to ensure the method is compiled in the
 * initial layer. This annotation processor scans the LayeredCompilationBehavior annotations, and
 * for each method with the annotation set to PINNED_TO_INITIAL_LAYER generates a new feature class
 * which will ensure the method is properly registered.
 */
@SupportedAnnotationTypes(LayeredAnnotationProcessor.ANNOTATION_CLASS_NAME)
public class LayeredAnnotationProcessor extends AbstractProcessor {
    static final String ANNOTATION_CLASS_NAME = "com.oracle.svm.common.layeredimage.LayeredCompilationBehavior";
    static final String PINNED_TO_INITIAL_LAYER = "PINNED_TO_INITIAL_LAYER";
    static final String METHOD_NAME_COMPONENT_SEPARATOR = "__";
    static final char OBJECT_PATH_SEPARATOR = '_';
    static final String ARRAY_IDENTIFIER = "ARRAY_";
    static final String CONSTRUCTOR_NAME = "<init>";
    static final String CLASS_INITIALIZER_NAME = "<clinit>";

    private void processElement(ExecutableElement annotatedExecutable, AnnotationMirror compilationBehavior) {
        String featureClassName = computeFileName(annotatedExecutable);
        String packageName = getPackage(annotatedExecutable).getQualifiedName().toString();
        String methodName = computeMethodName(annotatedExecutable);
        boolean isConstructor = methodName.equals(CONSTRUCTOR_NAME);

        if (methodName.equals(CLASS_INITIALIZER_NAME)) {
            /* Class initializers cannot have a special compilation behavior. */
            return;
        }

        var foobar = getAnnotationValue(compilationBehavior, "value", VariableElement.class);
        assert foobar.getKind() == ElementKind.ENUM_CONSTANT;
        var name = foobar.getSimpleName().toString();
        if (!name.equals(PINNED_TO_INITIAL_LAYER)) {
            /*
             * Only annotations set to PINNED_TO_INITIAL_LAYER require a new feature to be
             * generated.
             */
            return;
        }

        try (PrintWriter out = createSourceFile(packageName, featureClassName, processingEnv.getFiler(), annotatedExecutable)) {
            String classContents = """
                            // CheckStyle: stop header check
                            // CheckStyle: stop line length check
                            package %1$s;

                            // GENERATED CONTENT - DO NOT EDIT
                            // Annotated method: %2$s
                            // Annotation: %3$s
                            // Annotation processor: com.oracle.svm.processor.LayeredAnnotationProcessor

                            import com.oracle.svm.common.hosted.layeredimage.LayeredCompilationSupport;
                            import com.oracle.svm.common.layeredimage.LayeredCompilationBehavior;
                            import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
                            import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
                            import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
                            import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
                            import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
                            import com.oracle.svm.core.traits.SingletonTraits;
                            import com.oracle.svm.util.ReflectionUtil;

                            @AutomaticallyRegisteredFeature
                            @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = Independent.class)
                            public class %4$s implements com.oracle.svm.core.feature.InternalFeature {
                                @Override
                                public boolean isInConfiguration(IsInConfigurationAccess access) {
                                    return ImageLayerBuildingSupport.buildingInitialLayer();
                                }

                                @Override
                                public void duringSetup(DuringSetupAccess access) {
                                    var method = ReflectionUtil.lookup%5$s(%6$s%7$s%8$s);
                                    LayeredCompilationSupport.singleton().registerCompilationBehavior(method, LayeredCompilationBehavior.Behavior.PINNED_TO_INITIAL_LAYER);
                                }
                            }
                            """.formatted(
                            packageName,
                            computeAnnotatedMethodName(annotatedExecutable),
                            ANNOTATION_CLASS_NAME,
                            featureClassName,
                            isConstructor ? "Constructor" : "Method",
                            computeDeclaringClass(annotatedExecutable),
                            isConstructor ? "" : ", \"%s\"".formatted(methodName),
                            computeParams(annotatedExecutable));
            out.print(classContents);
        }
    }

    private String computeFileName(ExecutableElement e) {
        TypeElement enclosingElement = (TypeElement) e.getEnclosingElement();
        StringBuilder fileName = new StringBuilder(AutomaticallyRegisteredImageSingletonProcessor.getTypeNameWithEnclosingClasses(enclosingElement, ""));

        String methodName = e.getSimpleName().toString();
        if (CONSTRUCTOR_NAME.equals(methodName)) {
            methodName = enclosingElement.getSimpleName().toString();
        }
        fileName.append(METHOD_NAME_COMPONENT_SEPARATOR).append(methodName).append(METHOD_NAME_COMPONENT_SEPARATOR).append(getDescriptorForClass(e.getReturnType()));

        var parameters = e.getParameters();
        if (!parameters.isEmpty()) {
            fileName.append(METHOD_NAME_COMPONENT_SEPARATOR);
            for (var parameter : parameters) {
                fileName.append(getDescriptorForClass(parameter.asType()));
            }
        }

        return fileName.toString();
    }

    private static String computeAnnotatedMethodName(ExecutableElement e) {
        TypeElement enclosingElement = (TypeElement) e.getEnclosingElement();
        return enclosingElement.getQualifiedName().toString() + "#" + e.getSimpleName().toString();
    }

    private static String computeDeclaringClass(ExecutableElement e) {
        TypeElement enclosingElement = (TypeElement) e.getEnclosingElement();
        return enclosingElement.getQualifiedName().toString() + ".class";
    }

    private static String computeMethodName(ExecutableElement e) {
        return e.getSimpleName().toString();
    }

    private String computeParams(ExecutableElement e) {
        String params = String.join(", ", e.getParameters().stream().map(p -> lookupClass(getQualifiedName(p.asType()), p.asType())).toArray(String[]::new));
        if (!params.isEmpty()) {
            return ", " + params;
        }
        return "";
    }

    private String getDescriptorForClass(TypeMirror c) {
        return switch (c.getKind()) {
            case BOOLEAN -> "Z";
            case BYTE -> "B";
            case CHAR -> "C";
            case SHORT -> "S";
            case INT -> "I";
            case LONG -> "J";
            case FLOAT -> "F";
            case DOUBLE -> "D";
            case VOID -> "V";
            case TYPEVAR -> "Ljava_lang_Object";
            case ARRAY -> ARRAY_IDENTIFIER + getDescriptorForClass(((ArrayType) c).getComponentType());
            case DECLARED -> "L" + getQualifiedName(c).replace('.', OBJECT_PATH_SEPARATOR).replace('$', OBJECT_PATH_SEPARATOR);
            default -> throw new RuntimeException("Unexpected null type: " + c);
        };
    }

    private String getQualifiedName(TypeMirror c) {
        return switch (c.getKind()) {
            case DECLARED -> getQualifiedName((DeclaredType) c);
            case ARRAY -> getQualifiedName(((ArrayType) c).getComponentType()) + "[]";
            default -> c.toString();
        };
    }

    private String getQualifiedName(DeclaredType c) {
        return processingEnv.getElementUtils().getBinaryName((TypeElement) c.asElement()).toString();
    }

    private static String lookupClass(String clazz, TypeMirror c) {
        return switch (c.getKind()) {
            case DECLARED -> "ReflectionUtil.lookupClass(\"%s\")".formatted(clazz);
            case ARRAY -> "%s.arrayType()".formatted(lookupClass(clazz.substring(0, clazz.length() - 2), ((ArrayType) c).getComponentType()));
            default -> clazz + ".class";
        };
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(getTypeElement(ANNOTATION_CLASS_NAME))) {
            assert element.getKind().isExecutable();
            AnnotationMirror compilationBehavior = getAnnotation(element, getType(ANNOTATION_CLASS_NAME));
            processElement((ExecutableElement) element, compilationBehavior);
        }
        return true;
    }
}
