/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

@SupportedAnnotationTypes("com.oracle.truffle.api.TruffleLanguage.Registration")
public final class LanguageRegistrationProcessor extends AbstractProcessor {
    private final List<TypeElement> registrations = new ArrayList<>();

    // also update list in PolyglotEngineImpl#RESERVED_IDS
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList("host", "graal", "truffle", "engine", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm", "log"));

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @SuppressWarnings("deprecation")
    private void generateFile(List<TypeElement> languages) {
        String filename = "META-INF/truffle/language";
        // sorted properties
        Properties p = new SortedProperties();
        int cnt = 0;
        for (TypeElement l : languages) {
            Registration annotation = l.getAnnotation(Registration.class);
            if (annotation == null) {
                continue;
            }
            String prefix = "language" + ++cnt + ".";
            String className = processingEnv.getElementUtils().getBinaryName(l).toString();
            String id = annotation.id();
            if (id != null && !id.isEmpty()) {
                p.setProperty(prefix + "id", id);
            }
            p.setProperty(prefix + "name", annotation.name());
            p.setProperty(prefix + "implementationName", annotation.implementationName());
            p.setProperty(prefix + "version", annotation.version());
            p.setProperty(prefix + "className", className);

            if (!annotation.defaultMimeType().equals("")) {
                p.setProperty(prefix + "defaultMimeType", annotation.defaultMimeType());
            }

            String[] mimes = annotation.mimeType();
            for (int i = 0; i < mimes.length; i++) {
                p.setProperty(prefix + "mimeType." + i, mimes[i]);
            }
            String[] charMimes = annotation.characterMimeTypes();
            Arrays.sort(charMimes);
            for (int i = 0; i < charMimes.length; i++) {
                p.setProperty(prefix + "characterMimeType." + i, charMimes[i]);
            }
            String[] byteMimes = annotation.byteMimeTypes();
            Arrays.sort(byteMimes);
            for (int i = 0; i < byteMimes.length; i++) {
                p.setProperty(prefix + "byteMimeType." + i, byteMimes[i]);
            }

            String[] dependencies = annotation.dependentLanguages();
            Arrays.sort(dependencies);
            for (int i = 0; i < dependencies.length; i++) {
                p.setProperty(prefix + "dependentLanguage." + i, dependencies[i]);
            }
            p.setProperty(prefix + "interactive", Boolean.toString(annotation.interactive()));
            p.setProperty(prefix + "internal", Boolean.toString(annotation.internal()));
        }
        if (cnt > 0) {
            try {
                FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, languages.toArray(new Element[0]));
                try (OutputStream os = file.openOutputStream()) {
                    p.store(os, "Generated by " + LanguageRegistrationProcessor.class.getName());
                }
            } catch (IOException e) {
                if (e instanceof FilerException) {
                    if (e.getMessage().startsWith("Source file already created")) {
                        // ignore source file already created errors
                        return;
                    }
                }
                processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), languages.get(0));
            }
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessorContext.setThreadLocalInstance(new ProcessorContext(processingEnv, null));
        try {
            if (roundEnv.processingOver()) {
                generateFile(registrations);
                registrations.clear();
                return true;
            }

            TypeMirror registration = ProcessorContext.getInstance().getType(Registration.class);
            for (Element e : roundEnv.getElementsAnnotatedWith(Registration.class)) {
                AnnotationMirror mirror = ElementUtils.findAnnotationMirror(e.getAnnotationMirrors(), registration);
                Registration annotation = e.getAnnotation(Registration.class);
                if (annotation != null && e.getKind() == ElementKind.CLASS) {
                    if (!e.getModifiers().contains(Modifier.PUBLIC)) {
                        emitError("Registered language class must be public", e);
                        continue;
                    }
                    if (e.getEnclosingElement().getKind() != ElementKind.PACKAGE && !e.getModifiers().contains(Modifier.STATIC)) {
                        emitError("Registered language inner-class must be static", e);
                        continue;
                    }
                    TypeMirror truffleLang = processingEnv.getTypeUtils().erasure(ElementUtils.getTypeElement(processingEnv, TruffleLanguage.class.getName()).asType());
                    if (!processingEnv.getTypeUtils().isAssignable(e.asType(), truffleLang)) {
                        emitError("Registered language class must subclass TruffleLanguage", e);
                        continue;
                    }
                    boolean foundConstructor = false;
                    for (ExecutableElement constructor : ElementFilter.constructorsIn(e.getEnclosedElements())) {
                        if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                            continue;
                        }
                        if (!constructor.getParameters().isEmpty()) {
                            continue;
                        }
                        foundConstructor = true;
                        break;
                    }

                    Element singletonElement = null;
                    for (Element mem : e.getEnclosedElements()) {
                        if (!mem.getModifiers().contains(Modifier.PUBLIC)) {
                            continue;
                        }
                        if (mem.getKind() != ElementKind.FIELD) {
                            continue;
                        }
                        if (!mem.getModifiers().contains(Modifier.FINAL)) {
                            continue;
                        }
                        if (!"INSTANCE".equals(mem.getSimpleName().toString())) {
                            continue;
                        }
                        if (processingEnv.getTypeUtils().isAssignable(mem.asType(), truffleLang)) {
                            singletonElement = mem;
                            break;
                        }
                    }
                    boolean valid = true;

                    if (singletonElement != null) {
                        emitWarning("Using a singleton field is deprecated. Please provide a public no-argument constructor instead.", singletonElement);
                        valid = false;
                    } else {
                        if (!foundConstructor) {
                            emitError("A TruffleLanguage subclass must have a public no argument constructor.", e);
                            continue;
                        }
                    }

                    Set<String> mimeTypes = new HashSet<>();
                    if (!validateMimeTypes(mimeTypes, e, mirror, ElementUtils.getAnnotationValue(mirror, "characterMimeTypes"), annotation.characterMimeTypes())) {
                        continue;
                    }
                    if (!validateMimeTypes(mimeTypes, e, mirror, ElementUtils.getAnnotationValue(mirror, "byteMimeTypes"), annotation.byteMimeTypes())) {
                        continue;
                    }

                    String defaultMimeType = annotation.defaultMimeType();
                    if (mimeTypes.size() > 1 && (defaultMimeType == null || defaultMimeType.equals(""))) {
                        emitError("No defaultMimeType attribute specified. " +
                                        "The defaultMimeType attribute needs to be specified if more than one MIME type was specified.", e,
                                        mirror, ElementUtils.getAnnotationValue(mirror, "defaultMimeType"));
                        continue;
                    }

                    if (defaultMimeType != null && !defaultMimeType.equals("") && !mimeTypes.contains(defaultMimeType)) {
                        emitError("The defaultMimeType is not contained in the list of supported characterMimeTypes or byteMimeTypes. Add the specified default MIME type to" +
                                        " character or byte MIME types to resolve this.", e,
                                        mirror, ElementUtils.getAnnotationValue(mirror, "defaultMimeType"));
                        continue;
                    }

                    if (annotation.mimeType().length == 0) {
                        String id = annotation.id();
                        if (id.isEmpty()) {
                            emitError("The attribute id is mandatory.", e, mirror, null);
                            continue;
                        }
                        if (RESERVED_IDS.contains(id)) {
                            emitError(String.format("Id '%s' is reserved for other use and must not be used as id.", id), e, mirror, ElementUtils.getAnnotationValue(mirror, "id"));
                            continue;
                        }
                    }

                    if (valid) {
                        assertNoErrorExpected(e);
                    }
                    registrations.add((TypeElement) e);
                }
            }

            return true;
        } finally {
            ProcessorContext.setThreadLocalInstance(null);
        }
    }

    private boolean validateMimeTypes(Set<String> collectedMimeTypes, Element e, AnnotationMirror mirror, AnnotationValue value, String[] loadedMimeTypes) {
        for (String mimeType : loadedMimeTypes) {
            if (!validateMimeType(e, mirror, value, mimeType)) {
                return false;
            }
            if (collectedMimeTypes.contains(mimeType)) {
                emitError(String.format("Duplicate MIME type specified '%s'. MIME types must be unique.", mimeType), e, mirror,
                                value);
                return false;
            }
            collectedMimeTypes.add(mimeType);
        }
        return true;
    }

    private boolean validateMimeType(Element type, AnnotationMirror mirror, AnnotationValue value, String mimeType) {
        int index = mimeType.indexOf('/');
        if (index == -1 || index == 0 || index == mimeType.length() - 1) {
            emitError(String.format("Invalid MIME type '%s' provided. MIME types consist of a type and a subtype separated by '/'.", mimeType), type, mirror, value);
            return false;
        }
        if (mimeType.indexOf('/', index + 1) != -1) {
            emitError(String.format("Invalid MIME type '%s' provided. MIME types consist of a type and a subtype separated by '/'.", mimeType), type, mirror, value);
            return false;
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

    void emitError(String msg, Element e, AnnotationMirror mirror, AnnotationValue value) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, msg, e, mirror, value);
    }

    void emitWarning(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.WARNING, msg, e);
    }

    void emitWarning(String msg, Element e, AnnotationMirror mirror, AnnotationValue value) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.WARNING, msg, e, mirror, value);
    }

    @SuppressWarnings("serial")
    static class SortedProperties extends Properties {
        @Override
        public synchronized Enumeration<Object> keys() {
            return Collections.enumeration(new TreeSet<>(super.keySet()));
        }
    }

}
