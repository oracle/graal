/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.dsl.processor.java.ElementUtils.getElementHierarchy;
import static java.util.Collections.reverse;

import java.io.PrintWriter;
import java.io.StringWriter;
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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.ExpectError;
import com.oracle.truffle.dsl.processor.ProcessorContext;
import com.oracle.truffle.dsl.processor.TruffleTypes;
import com.oracle.truffle.dsl.processor.java.ElementUtils;

@SupportedAnnotationTypes({
                TruffleTypes.CompilerDirectives_TruffleBoundary_Name,
                TruffleTypes.Node_Child_Name,
                TruffleTypes.Node_Children_Name})
public class VerifyTruffleProcessor extends AbstractProcessor {
    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    /**
     * Node class currently being processed.
     */
    private Element scope;

    public static boolean isEnclosedIn(Element e, Element scopeElement) {
        List<Element> elementHierarchy = getElementHierarchy(e);
        return elementHierarchy.contains(scopeElement);
    }

    void errorMessage(Element element, String format, Object... args) {
        message(Kind.ERROR, element, format, args);
    }

    void message(Kind kind, Element element, String format, Object... args) {
        if (scope != null && !isEnclosedIn(element, scope)) {
            // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=428357#c1
            List<Element> elementHierarchy = getElementHierarchy(element);
            reverse(elementHierarchy);

            StringBuilder str = new StringBuilder();
            for (Element e : elementHierarchy) {
                if (e.getKind() != ElementKind.PACKAGE) {
                    str.append(str.length() == 0 ? "" : ".");
                    str.append(e);
                }
            }
            processingEnv.getMessager().printMessage(kind, String.format(str + ": " + format, args), scope);
        } else {
            processingEnv.getMessager().printMessage(kind, String.format(format, args), element);
        }
    }

    /**
     * Bugs in an annotation processor can cause silent failure so try to report any exception
     * throws as errors.
     */
    private void reportException(Kind kind, Element element, Throwable t) {
        StringWriter buf = new StringWriter();
        t.printStackTrace(new PrintWriter(buf));
        message(kind, element, "Exception thrown during processing: %s", buf.toString());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        ProcessorContext context = ProcessorContext.enter(processingEnv);
        try {
            TruffleTypes types = context.getTypes();
            TypeElement virtualFrameType = ElementUtils.castTypeElement(types.VirtualFrame);

            for (Element element : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(types.CompilerDirectives_TruffleBoundary))) {
                scope = element;
                try {
                    if (element.getKind() != ElementKind.CONSTRUCTOR &&
                                    element.getKind() != ElementKind.METHOD) {
                        continue;
                    }
                    ExecutableElement method = (ExecutableElement) element;

                    for (VariableElement parameter : method.getParameters()) {
                        Element paramType = processingEnv.getTypeUtils().asElement(parameter.asType());
                        if (paramType != null && paramType.equals(virtualFrameType)) {
                            errorMessage(element, "Method %s cannot be annotated with @%s and have a parameter of type %s", method.getSimpleName(),
                                            types.CompilerDirectives_TruffleBoundary.asElement().getSimpleName().toString(),
                                            paramType.getSimpleName());
                        }
                    }
                } catch (Throwable t) {
                    reportException(isBug367599(t) ? Kind.NOTE : Kind.ERROR, element, t);
                } finally {
                    scope = null;
                }
            }

            TypeElement nodeType = ElementUtils.castTypeElement(types.Node);
            TypeElement nodeInterfaceType = ElementUtils.castTypeElement(types.NodeInterface);
            for (Element e : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(types.Node_Child))) {
                if (e.getModifiers().contains(Modifier.FINAL)) {
                    emitError("@Child field cannot be final", e);
                    continue;
                }
                if (!processingEnv.getTypeUtils().isSubtype(e.asType(), nodeInterfaceType.asType())) {
                    emitError("@Child field must implement NodeInterface", e);
                    continue;
                }
                if (!processingEnv.getTypeUtils().isSubtype(e.getEnclosingElement().asType(), nodeType.asType())) {
                    emitError("@Child field is allowed only in Node sub-class", e);
                    continue;
                }
                if (ElementUtils.findAnnotationMirror(e, types.Executed) == null) {
                    assertNoErrorExpected(e);
                }
            }
            for (Element annotatedField : roundEnv.getElementsAnnotatedWith(ElementUtils.castTypeElement(types.Node_Children))) {
                boolean reportError = false;
                TypeMirror annotatedFieldType = annotatedField.asType();
                if (annotatedFieldType.getKind() == TypeKind.ARRAY) {
                    TypeMirror compomentType = ((ArrayType) annotatedFieldType).getComponentType();
                    if (!processingEnv.getTypeUtils().isSubtype(compomentType, nodeInterfaceType.asType())) {
                        reportError = true;
                    }
                } else {
                    reportError = true;
                }
                if (reportError) {
                    emitError("@Children field must be an array of NodeInerface sub-types", annotatedField);
                    continue;
                }
                if (!processingEnv.getTypeUtils().isSubtype(annotatedField.getEnclosingElement().asType(), nodeType.asType())) {
                    emitError("@Children field is allowed only in Node sub-class", annotatedField);
                    continue;
                }
                if (ElementUtils.findAnnotationMirror(annotatedField, types.Executed) == null) {
                    assertNoErrorExpected(annotatedField);
                }
            }
            return false;
        } finally {
            ProcessorContext.leave();
        }
    }

    void assertNoErrorExpected(Element element) {
        ExpectError.assertNoErrorExpected(processingEnv, element);
    }

    void emitError(String message, Element element) {
        if (ExpectError.isExpectedError(processingEnv, element, message)) {
            return;
        }
        processingEnv.getMessager().printMessage(Kind.ERROR, message, element);
    }

    /**
     * Determines if a given exception is (most likely) caused by
     * <a href="https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599">Bug 367599</a>.
     */
    public static boolean isBug367599(Throwable t) {
        if (t instanceof FilerException) {
            for (StackTraceElement ste : t.getStackTrace()) {
                if (ste.toString().contains("org.eclipse.jdt.internal.apt.pluggable.core.filer.IdeFilerImpl.create")) {
                    // See: https://bugs.eclipse.org/bugs/show_bug.cgi?id=367599
                    return true;
                }
            }
        }
        if (t.getCause() != null) {
            return isBug367599(t.getCause());
        }
        return false;
    }
}
