/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.dsl.processor.LanguageRegistrationProcessor.SortedProperties;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

@SupportedAnnotationTypes("com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration")
public final class InstrumentRegistrationProcessor extends AbstractProcessor {
    private final List<TypeElement> registrations = new ArrayList<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    private static final int NUMBER_OF_PROPERTIES_PER_ENTRY = 4;

    private void generateFile(List<TypeElement> instruments) {
        String filename = "META-INF/truffle/instrument";
        Properties p = new SortedProperties();
        int numInstruments = loadIfFileAlreadyExists(filename, p);

        for (TypeElement l : instruments) {
            Registration annotation = l.getAnnotation(Registration.class);
            if (annotation == null) {
                continue;
            }

            int instNum = findInstrument(annotation.id(), p);
            if (instNum == 0) { // not found
                numInstruments += 1;
                instNum = numInstruments;
            }

            String prefix = "instrument" + instNum + ".";
            String className = processingEnv.getElementUtils().getBinaryName(l).toString();

            p.setProperty(prefix + "id", annotation.id());
            p.setProperty(prefix + "name", annotation.name());
            p.setProperty(prefix + "version", annotation.version());
            p.setProperty(prefix + "className", className);
            p.setProperty(prefix + "internal", Boolean.toString(annotation.internal()));

            int serviceCounter = 0;
            for (AnnotationMirror anno : l.getAnnotationMirrors()) {
                final String annoName = anno.getAnnotationType().asElement().toString();
                if (Registration.class.getCanonicalName().equals(annoName)) {
                    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : anno.getElementValues().entrySet()) {
                        final Name attrName = entry.getKey().getSimpleName();
                        if (attrName.contentEquals("services")) {
                            AnnotationValue attrValue = entry.getValue();
                            List<?> classes = (List<?>) attrValue.getValue();
                            for (Object clazz : classes) {
                                AnnotationValue clazzValue = (AnnotationValue) clazz;
                                p.setProperty(prefix + "service" + serviceCounter++, clazzValue.getValue().toString());
                            }
                        }
                    }
                }
            }
        }
        if (numInstruments > 0) {
            try {
                FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename);
                try (OutputStream os = file.openOutputStream()) {
                    p.store(os, "Generated by " + InstrumentRegistrationProcessor.class.getName());
                }
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), instruments.get(0));
            }
        }
    }

    private static int findInstrument(String id, Properties p) {
        int cnt = 1;
        String val;
        while ((val = p.getProperty("instrument" + cnt + ".id")) != null) {
            if (id.equals(val)) {
                return cnt;
            }
            cnt += 1;
        }
        return 0;
    }

    private int loadIfFileAlreadyExists(String filename, Properties p) {
        try {
            FileObject file = processingEnv.getFiler().getResource(
                            StandardLocation.CLASS_OUTPUT, "", filename);
            p.load(file.openInputStream());

            return p.keySet().size() / NUMBER_OF_PROPERTIES_PER_ENTRY;
        } catch (IOException e) {
            // Ignore error. It is ok if the file does not exist
            return 0;
        }
    }

    static void loadExistingTypes(ProcessingEnvironment env, List<TypeElement> instruments, String filename, String pre) {
        Set<String> typeNames = new HashSet<>();
        for (TypeElement type : instruments) {
            typeNames.add(ElementUtils.getQualifiedName(type));
        }

        Properties current = new Properties();
        try {
            FileObject object = env.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", filename);
            current.load(object.openInputStream());
        } catch (IOException e1) {
            env.getMessager().printMessage(Kind.NOTE, filename + e1.getMessage(), null);
            // does not exist yet.
            // better way to detect this?
        }

        for (int cnt = 1;; cnt++) {
            String prefix = pre + cnt + ".";
            String className = current.getProperty(prefix + "className");
            if (className == null) {
                break;
            }
            env.getMessager().printMessage(Kind.NOTE, filename + className, null);
            TypeElement foundType = ElementUtils.getTypeElement(env, className);
            if (foundType != null && !typeNames.contains(ElementUtils.getQualifiedName(foundType))) {
                instruments.add(foundType);
            }
        }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            generateFile(registrations);
            registrations.clear();
            return true;
        }
        for (Element e : roundEnv.getElementsAnnotatedWith(Registration.class)) {
            Registration annotation = e.getAnnotation(Registration.class);
            if (annotation != null && e.getKind() == ElementKind.CLASS) {
                if (!e.getModifiers().contains(Modifier.PUBLIC)) {
                    emitError("Registered instrument class must be public", e);
                    continue;
                }
                if (e.getEnclosingElement().getKind() != ElementKind.PACKAGE && !e.getModifiers().contains(Modifier.STATIC)) {
                    emitError("Registered instrument inner-class must be static", e);
                    continue;
                }
                TypeMirror truffleLang = processingEnv.getTypeUtils().erasure(ElementUtils.getTypeElement(processingEnv, TruffleInstrument.class.getName()).asType());
                if (!processingEnv.getTypeUtils().isAssignable(e.asType(), truffleLang)) {
                    emitError("Registered instrument class must subclass TruffleInstrument", e);
                    continue;
                }
                assertNoErrorExpected(e);
                registrations.add((TypeElement) e);
            }
        }

        return true;
    }

    void assertNoErrorExpected(Element e) {
        ExpectError.assertNoErrorExpected(processingEnv, e);
    }

    void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e);
    }

}
