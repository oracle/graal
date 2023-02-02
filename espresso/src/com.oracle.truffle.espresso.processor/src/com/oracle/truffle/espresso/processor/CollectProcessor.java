/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.processor.builders.ClassBuilder;
import com.oracle.truffle.espresso.processor.builders.ClassFileBuilder;
import com.oracle.truffle.espresso.processor.builders.JavadocBuilder;
import com.oracle.truffle.espresso.processor.builders.MethodBuilder;
import com.oracle.truffle.espresso.processor.builders.ModifierBuilder;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;

/**
 * Processes classes annotated with {@code Collect}. For and class {@code C}, this processor
 * generates a class {@code CCollector} in the same package as {@code C} containing a list or all
 * annotated classes.
 */
@SupportedAnnotationTypes("com.oracle.truffle.espresso.substitutions.Collect")
public class CollectProcessor extends BaseProcessor {

    private static final String COLLECT = "com.oracle.truffle.espresso.substitutions.Collect";
    private final Set<TypeElement> processedClasses = new HashSet<>();
    private final Set<TypeElement> processedAnchors = new HashSet<>();
    private final Map<TypeElement, Set<TypeElement>> collectedClasses = new HashMap<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private boolean verifyAnnotation(TypeMirror anchorClass, TypeElement collected) {
        TypeElement anchorElement = asTypeElement(anchorClass);
        if (anchorElement.getNestingKind().isNested()) {
            String msg = String.format("@Collect anchor class %s must be a top level class", anchorElement.getSimpleName());
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, collected);
        }
        // Access check.
        Elements elementUtils = processingEnv.getElementUtils();
        if (!collected.getModifiers().contains(Modifier.PUBLIC) &&
                        !elementUtils.getPackageOf(anchorElement).equals(elementUtils.getPackageOf(collected))) {
            String msg = String.format("Class %s cannot be accessed by the generated collector from package %s", collected.getSimpleName(), elementUtils.getPackageOf(anchorElement));
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, collected);
        }
        return true;
    }

    private void processElement(TypeElement collected) {
        if (processedClasses.contains(collected)) {
            return;
        }

        processedClasses.add(collected);
        AnnotationMirror annotation = getAnnotation(collected, getType(COLLECT));
        if (annotation != null) {
            List<TypeMirror> anchorClasses = getAnnotationValueList(annotation, "value", TypeMirror.class);
            for (TypeMirror anchorClass : anchorClasses) {
                if (verifyAnnotation(anchorClass, collected)) {
                    this.collectedClasses.computeIfAbsent(asTypeElement(anchorClass), typeElement -> new HashSet<>()).add(collected);
                }
            }
        }
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        TypeElement collectTypeElement = getTypeElement(COLLECT);
        Set<? extends Element> elementsToProcess = roundEnv.getElementsAnnotatedWith(collectTypeElement);
        if (elementsToProcess.isEmpty()) {
            return true;
        }

        for (Element element : elementsToProcess) {
            assert element.getKind().isClass() || element.getKind().isInterface();
            processElement((TypeElement) element);
        }

        for (Entry<TypeElement, Set<TypeElement>> e : collectedClasses.entrySet()) {
            TypeElement anchorClass = e.getKey();
            if (processedAnchors.contains(anchorClass)) {
                env().getMessager().printMessage(Diagnostic.Kind.ERROR, getClass().getName() + " already generated a collector for anchor class: " + anchorClass.getQualifiedName());
            }
            Set<TypeElement> classes = e.getValue();
            createCollector(anchorClass, classes.toArray(new TypeElement[0]));
        }
        collectedClasses.clear();

        return true;
    }

    private String generateCollector(TypeElement anchorClass, TypeElement... classes) {
        String pkg = processingEnv.getElementUtils().getPackageOf(anchorClass).getQualifiedName().toString();

        MethodBuilder getInstancesMethod = new MethodBuilder("getInstances") //
                        .withModifiers(new ModifierBuilder().asPublic().asStatic()) //
                        .withReturnType("List<T>") //
                        .withTemplateParams("T") //
                        .withParams("Class<? extends T> componentClass") //
                        .addBodyLine("List<T> classes = new ArrayList<>(", classes.length, ");");
        for (TypeElement clazz : classes) {
            getInstancesMethod.addBodyLine("classes.add(componentClass.cast(new ", clazz.getQualifiedName(), "()));");
        }
        getInstancesMethod.addBodyLine("return classes;");

        JavadocBuilder javaDocLink = new JavadocBuilder().addLine(
                        "Generated by classes annotated with ",
                        JavadocBuilder.link(COLLECT + " &#064;Collect"),
                        "(@",
                        JavadocBuilder.link(anchorClass.getQualifiedName() + " " + anchorClass.getSimpleName().toString()),
                        ")");

        ClassBuilder collectorClass = new ClassBuilder(anchorClass.getSimpleName().toString() + "Collector") //
                        .withJavaDoc(javaDocLink) //
                        .withQualifiers(new ModifierBuilder().asPublic().asFinal()) //
                        .withMethod(getInstancesMethod);

        ClassFileBuilder collectorFile = new ClassFileBuilder() //
                        .inPackage(pkg) //
                        .withImportGroup(Arrays.asList("java.util.ArrayList", "java.util.List")) //
                        .withClass(collectorClass);

        return collectorFile.build();
    }

    /**
     * Creates a '&lt;anchorClass>Collector.java&gt;' class in the same package as
     * &lt;anchorClass&gt; allowing to list classes annotated with
     * &#064;Collect(&lt;anchorClass&gt;.class).
     */
    public void createCollector(TypeElement anchorClass, TypeElement... classes) {
        assert classes.length > 0;
        processedAnchors.add(anchorClass);
        String name = String.format("%sCollector", anchorClass.getQualifiedName());
        env().getMessager().printMessage(Diagnostic.Kind.NOTE, "Generating collector " + name);
        try {
            FileObject file = processingEnv.getFiler().createSourceFile(name, classes);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
            writer.print(generateCollector(anchorClass, classes));
            writer.close();
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), classes[0]);
        }
    }
}
