/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.verify;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

@SupportedAnnotationTypes(TruffleTypes.CompilerDirectives_CompilationFinal_Name)
public class VerifyCompilationFinalProcessor extends AbstractProcessor {

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        ProcessorContext context = ProcessorContext.enter(processingEnv);
        try {
            TruffleTypes types = context.getTypes();
            for (Element element : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(types.CompilerDirectives_CompilationFinal))) {
                if (!element.getKind().isField()) {
                    emitError(element, String.format("Only fields can be annotated with %s.", types.CompilerDirectives_CompilationFinal.asElement().getSimpleName().toString()));
                    continue;
                }
                if (!checkDimensions((VariableElement) element)) {
                    continue;
                }
                assertNoErrorExpected(element);
            }
            return false;
        } finally {
            ProcessorContext.leave();
        }
    }

    private boolean checkDimensions(final VariableElement field) {
        TruffleTypes types = ProcessorContext.getInstance().getTypes();
        final AnnotationMirror compFin = ElementUtils.findAnnotationMirror(field, types.CompilerDirectives_CompilationFinal);
        if (compFin != null) {
            final int compFinDimensions = ElementUtils.getAnnotationValue(Integer.class, compFin, "dimensions");
            final int fieldDimensions = dimension(field.asType());
            if (compFinDimensions < -1) {
                emitError(field, "@CompilationFinal.dimensions cannot be negative.");
                return false;
            }
            if (compFinDimensions == -1 && fieldDimensions > 0) {
                final SuppressWarnings suppressWarnings = field.getAnnotation(SuppressWarnings.class);
                if (suppressWarnings != null) {
                    for (String warning : suppressWarnings.value()) {
                        if ("VerifyCompilationFinal".equals(warning)) {
                            return true;
                        }
                    }
                }
                emitWarning(field, "@CompilationFinal.dimensions should be given for an array type.");
                return false;
            }
            if (compFinDimensions > fieldDimensions) {
                if (fieldDimensions == 0) {
                    emitError(field, String.format("Positive @CompilationFinal.dimensions (%d) not allowed for non array type.", compFinDimensions));
                } else {
                    emitError(field, String.format("@CompilationFinal.dimensions (%d) cannot exceed the array's dimensions (%d).", compFinDimensions, fieldDimensions));
                }
                return false;
            }
        }
        return true;
    }

    void assertNoErrorExpected(final Element originatingElm) {
        ExpectError.assertNoErrorExpected(processingEnv, originatingElm);
    }

    private void emitError(final Element originatingElm, final String message) {
        if (ExpectError.isExpectedError(processingEnv, originatingElm, message)) {
            return;
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, originatingElm);
    }

    private void emitWarning(final Element originatingElm, final String message) {
        if (ExpectError.isExpectedError(processingEnv, originatingElm, message)) {
            return;
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, message, originatingElm);
    }

    private static int dimension(final TypeMirror type) {
        if (type.getKind() == TypeKind.ARRAY) {
            return 1 + dimension(((ArrayType) type).getComponentType());
        } else {
            return 0;
        }
    }
}
