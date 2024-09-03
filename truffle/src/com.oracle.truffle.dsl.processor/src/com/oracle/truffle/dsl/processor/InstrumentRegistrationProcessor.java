/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeExecutableElement;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

@SupportedAnnotationTypes(TruffleTypes.TruffleInstrument_Registration_Name)
public final class InstrumentRegistrationProcessor extends AbstractRegistrationProcessor {

    private static final Set<String> IGNORED_ATTRIBUTES = Set.of("services");

    @Override
    boolean validateRegistration(Element annotatedElement, AnnotationMirror registrationMirror) {
        if (annotatedElement.getModifiers().contains(Modifier.PRIVATE)) {
            emitError("Registered instrument class must be at least package protected.", annotatedElement);
            return false;
        }
        if (annotatedElement.getEnclosingElement().getKind() != ElementKind.PACKAGE && !annotatedElement.getModifiers().contains(Modifier.STATIC)) {
            emitError("Registered instrument inner-class must be static.", annotatedElement);
            return false;
        }
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();
        TypeMirror truffleInstrument = types.TruffleInstrument;
        TypeMirror truffleInstrumentProvider = types.TruffleInstrumentProvider;
        boolean processingTruffleInstrument;
        if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleInstrument)) {
            processingTruffleInstrument = true;
        } else if (processingEnv.getTypeUtils().isAssignable(annotatedElement.asType(), truffleInstrumentProvider)) {
            processingTruffleInstrument = false;
        } else {
            emitError("Registered instrument class must subclass TruffleInstrument.", annotatedElement);
            return false;
        }
        if (!validateInternalResources(annotatedElement, registrationMirror, context)) {
            return false;
        }
        assertNoErrorExpected(annotatedElement);
        return processingTruffleInstrument;
    }

    @Override
    DeclaredType getProviderClass() {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        return types.TruffleInstrumentProvider;
    }

    @Override
    Iterable<AnnotationMirror> getProviderAnnotations(TypeElement annotatedElement) {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        DeclaredType registrationType = types.TruffleInstrument_Registration;
        AnnotationMirror registration = copyAnnotations(ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(), registrationType),
                        (t) -> !IGNORED_ATTRIBUTES.contains(t.getSimpleName().toString()));
        return Collections.singleton(registration);
    }

    @Override
    void implementMethod(TypeElement annotatedElement, CodeExecutableElement methodToImplement) {
        CodeTreeBuilder builder = methodToImplement.createBuilder();
        ProcessorContext context = ProcessorContext.getInstance();
        TruffleTypes types = context.getTypes();
        switch (methodToImplement.getSimpleName().toString()) {
            case "create":
                builder.startReturn().startNew(annotatedElement.asType()).end().end();
                break;
            case "getInstrumentClassName": {
                Elements elements = context.getEnvironment().getElementUtils();
                builder.startReturn().doubleQuote(elements.getBinaryName(annotatedElement).toString()).end();
                break;
            }
            case "getServicesClassNames": {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(),
                                types.TruffleInstrument_Registration);
                generateGetServicesClassNames(registration, builder, context);
                break;
            }
            case "getInternalResourceIds": {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(),
                                types.TruffleInstrument_Registration);
                generateGetInternalResourceIds(registration, builder, context);
                break;
            }
            case "createInternalResource": {
                AnnotationMirror registration = ElementUtils.findAnnotationMirror(annotatedElement.getAnnotationMirrors(),
                                types.TruffleInstrument_Registration);
                generateCreateInternalResource(registration, methodToImplement.getParameters().get(0), builder, context);
                break;
            }
            default:
                throw new IllegalStateException("Unsupported method: " + methodToImplement.getSimpleName());
        }
    }
}
