/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor;

import com.oracle.truffle.api.TruffleFileTypeDetector;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("com.oracle.truffle.api.TruffleFileTypeDetector.Registration")
public class TruffleFileTypeDetectorRegistrationProcessor extends AbstractProcessor {

    private final List<TypeElement> registrations = new ArrayList<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        ProcessorContext.setThreadLocalInstance(new ProcessorContext(processingEnv, null));
        try {
            if (roundEnv.processingOver()) {
                generateFile(registrations);
                registrations.clear();
            } else {
                TypeMirror registration = ProcessorContext.getInstance().getType(TruffleFileTypeDetector.Registration.class);
                for (Element element : roundEnv.getElementsAnnotatedWith(ElementUtils.fromTypeMirror(registration))) {
                    if (element.getKind() == ElementKind.CLASS) {
                        if (!element.getModifiers().contains(Modifier.PUBLIC)) {
                            emitError("Registered TruffleFileTypeDetector class must be public.", element);
                            continue;
                        }
                        if (element.getEnclosingElement().getKind() != ElementKind.PACKAGE && !element.getModifiers().contains(Modifier.STATIC)) {
                            emitError("Registered TruffleFileTypeDetector inner-class must be static.", element);
                            continue;
                        }
                        TypeMirror truffleFileTypeDetector = ProcessorContext.getInstance().getType(TruffleFileTypeDetector.class);
                        if (!processingEnv.getTypeUtils().isAssignable(element.asType(), truffleFileTypeDetector)) {
                            emitError("Registered TruffleFileTypeDetector class must subclass TruffleFileTypeDetector.", element);
                            continue;
                        }
                        boolean foundConstructor = false;
                        for (ExecutableElement constructor : ElementFilter.constructorsIn(element.getEnclosedElements())) {
                            if (!constructor.getModifiers().contains(Modifier.PUBLIC)) {
                                continue;
                            }
                            if (!constructor.getParameters().isEmpty()) {
                                continue;
                            }
                            foundConstructor = true;
                            break;
                        }
                        if (!foundConstructor) {
                            emitError("A TruffleFileTypeDetector subclass must have a public no argument constructor.", element);
                            continue;
                        }
                        registrations.add((TypeElement) element);
                    }
                }
            }
            return true;
        } finally {
            ProcessorContext.setThreadLocalInstance(null);
        }
    }

    void emitError(String msg, Element e) {
        if (ExpectError.isExpectedError(processingEnv, e, msg)) {
            return;
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, e);
    }

    private void generateFile(List<TypeElement> detectors) {
        if (detectors.isEmpty()) {
            return;
        }
        String filename = "META-INF/services/" + TruffleFileTypeDetector.class.getName();
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, detectors.toArray(new Element[detectors.size()]));
            try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"))) {
                for (TypeElement detector : detectors) {
                    writer.println(processingEnv.getElementUtils().getBinaryName(detector));
                }
            }
        } catch (IOException e) {
            if (e instanceof FilerException) {
                if (e.getMessage().startsWith("Source file already created")) {
                    // ignore source file already created errors
                    return;
                }
            }
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), detectors.get(0));
        }
    }
}
