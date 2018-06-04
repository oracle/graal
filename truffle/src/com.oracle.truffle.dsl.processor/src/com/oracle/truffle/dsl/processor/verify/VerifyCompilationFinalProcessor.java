/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.verify;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.dsl.processor.ExpectError;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

@SupportedAnnotationTypes("com.oracle.truffle.api.CompilerDirectives.CompilationFinal")
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
        for (Element element : roundEnv.getElementsAnnotatedWith(CompilerDirectives.CompilationFinal.class)) {
            if (!element.getKind().isField()) {
                emitError(element, String.format("Only fields can be annotated with %s.", CompilerDirectives.CompilationFinal.class.getSimpleName()));
                continue;
            }
            if (!checkDimensions((VariableElement) element)) {
                continue;
            }
            assertNoErrorExpected(element);
        }
        return false;
    }

    private boolean checkDimensions(final VariableElement field) {
        final CompilerDirectives.CompilationFinal compFin = field.getAnnotation(CompilerDirectives.CompilationFinal.class);
        if (compFin != null) {
            final int compFinDimensions = compFin.dimensions();
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
