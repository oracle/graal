/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;
import java.lang.annotation.Annotation;
import java.util.Collections;

@SupportedAnnotationTypes("com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration")
public final class InstrumentRegistrationProcessor extends AbstractRegistrationProcessor {

    private static final int NUMBER_OF_PROPERTIES_PER_ENTRY = 4;

    @Override
    boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror, Annotation registration) {
        if (!annotatedElement.getModifiers().contains(Modifier.PUBLIC)) {
            emitError("Registered instrument class must be public", annotatedElement);
            return false;
        }
        if (annotatedElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !annotatedElement.getModifiers().contains(Modifier.STATIC)) {
            emitError("Registered instrument inner-class must be static", annotatedElement);
            return false;
        }
        TypeMirror truffleInstrument = ProcessorContext.getInstance().getType(TruffleInstrument.class);
        TypeMirror truffleInstrumentProvider = ProcessorContext.getInstance().getType(TruffleInstrument.Provider.class);
        boolean processingTruffleInstrument;
        if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleInstrument)) {
            processingTruffleInstrument = true;
        } else if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleInstrumentProvider)) {
            processingTruffleInstrument = false;
        } else {
            emitError("Registered instrument class must subclass TruffleInstrument", annotatedElement);
            return false;
        }
        assertNoErrorExpected(annotatedElement);
        return processingTruffleInstrument;
    }

    @Override
    Class<?> getProviderClass() {
        return TruffleInstrument.Provider.class;
    }

    @Override
    Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement) {
        return Collections.singleton(ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), ProcessorContext.getInstance().getType(TruffleInstrument.Registration.class)));
    }

    @Override
    void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement) {
        CodeTreeBuilder builder = methodToImplement.createBuilder();
        switch (methodToImplement.getSimpleName().toString()) {
            case "create":
                builder.startReturn().startNew(annotatedElement.asType()).end().end();
                break;
            case "getInstrumentClassName":
                ProcessorContext context = ProcessorContext.getInstance();
                Elements elements = context.getEnvironment().getElementUtils();
                builder.startReturn().doubleQuote(elements.getBinaryName(annotatedElement).toString()).end();
                break;
            default:
                throw new IllegalStateException("Unsupported method: " + methodToImplement.getSimpleName());
        }
    }

    @Override
    String getRegistrationFileName() {
        return "META-INF/truffle/instrument";
    }

    @Override
    void storeRegistrations(Properties into, Iterable<? extends TypeElement> instruments) {
        int numInstruments = loadIfFileAlreadyExists(getRegistrationFileName(), into);
        for (TypeElement l : instruments) {
            Registration annotation = l.getAnnotation(Registration.class);
            if (annotation == null) {
                continue;
            }

            int instNum = findInstrument(annotation.id(), into);
            if (instNum == 0) { // not found
                numInstruments += 1;
                instNum = numInstruments;
            }

            String prefix = "instrument" + instNum + ".";
            String className = processingEnv.getElementUtils().getBinaryName(l).toString();

            into.setProperty(prefix + "id", annotation.id());
            into.setProperty(prefix + "name", annotation.name());
            into.setProperty(prefix + "version", annotation.version());
            into.setProperty(prefix + "className", className);
            into.setProperty(prefix + "internal", Boolean.toString(annotation.internal()));

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
                                into.setProperty(prefix + "service" + serviceCounter++, clazzValue.getValue().toString());
                            }
                        }
                    }
                }
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
}
