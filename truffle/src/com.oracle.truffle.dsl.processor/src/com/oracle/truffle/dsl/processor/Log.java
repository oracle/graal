/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

    public Log(ProcessingEnvironment env) {
        this.processingEnv = env;
    }

    public void debug(String message, Object... args) {
        message(Kind.ERROR, null, null, null, message, args);
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
        processingEnv.getMessager().printMessage(kind, message, usedElement, usedMirror, usedValue);
    }

}
