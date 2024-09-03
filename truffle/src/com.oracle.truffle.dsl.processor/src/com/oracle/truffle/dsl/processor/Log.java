/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.dsl.processor.java.model.GeneratedElement;

/**
 * THIS IS NOT PUBLIC API.
 */
public class Log {

    public static boolean isDebug() {
        return false;
    }

    private final ProcessingEnvironment processingEnv;
    private final boolean emitWarnings;
    private final Set<String> suppressWarnings;

    public Log(ProcessingEnvironment env, boolean emitWarnings, String[] suppressWarnings) {
        this.processingEnv = env;
        this.emitWarnings = emitWarnings;
        this.suppressWarnings = suppressWarnings != null ? Set.of(suppressWarnings) : null;
    }

    public void message(Kind kind, Element element, AnnotationMirror mirror, AnnotationValue value, String format, Object... args) {
        AnnotationMirror usedMirror = mirror;
        Element usedElement = element;
        AnnotationValue usedValue = value;
        String message = String.format(format, args);

        if (element instanceof GeneratedElement) {
            usedMirror = ((GeneratedElement) element).getGeneratorAnnotationMirror();
            usedElement = ((GeneratedElement) element).getGeneratorElement();
            usedValue = null;
            if (usedElement != null) {
                message = String.format("Element %s: %s", element, message);
            }
        }
        if (isSuppressed(kind, null, usedElement)) {
            return;
        }

        if (kind != Kind.WARNING || emitWarnings) {
            processingEnv.getMessager().printMessage(kind, message, usedElement, usedMirror, usedValue);
        }
    }

    public boolean isSuppressed(Kind kind, String suppressionKey, Element usedElement, boolean useOptions) {
        if (kind == Kind.WARNING) {
            if (!emitWarnings && useOptions) {
                return true;
            }
            if (suppressionKey != null) {
                if (TruffleSuppressedWarnings.isSuppressed(usedElement, suppressionKey)) {
                    return true;
                }
                if (suppressWarnings != null && useOptions && suppressWarnings.contains(suppressionKey)) {
                    return true;
                }
            } else {
                if (TruffleSuppressedWarnings.isSuppressed(usedElement)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isSuppressed(Kind kind, String suppressionKey, Element usedElement) {
        return isSuppressed(kind, suppressionKey, usedElement, true);
    }

}
