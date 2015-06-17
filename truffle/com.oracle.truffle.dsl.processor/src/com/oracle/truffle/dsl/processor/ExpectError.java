/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.tools.Diagnostic.*;

public class ExpectError {

    public static void assertNoErrorExpected(ProcessingEnvironment processingEnv, Element element) {
        TypeElement eee = processingEnv.getElementUtils().getTypeElement(TruffleTypes.EXPECT_ERROR_CLASS_NAME);
        if (eee != null) {
            for (AnnotationMirror am : element.getAnnotationMirrors()) {
                if (am.getAnnotationType().asElement().equals(eee)) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Expected an error, but none found!", element);
                }
            }
        }
    }

    public static boolean isExpectedError(ProcessingEnvironment processingEnv, Element element, String message) {
        TypeElement eee = processingEnv.getElementUtils().getTypeElement(TruffleTypes.EXPECT_ERROR_CLASS_NAME);
        if (eee != null) {
            for (AnnotationMirror am : element.getAnnotationMirrors()) {
                if (am.getAnnotationType().asElement().equals(eee)) {
                    Map<? extends ExecutableElement, ? extends AnnotationValue> vals = am.getElementValues();
                    if (vals.size() == 1) {
                        AnnotationValue av = vals.values().iterator().next();
                        if (av.getValue() instanceof List) {
                            List<?> arr = (List<?>) av.getValue();
                            for (Object o : arr) {
                                if (o instanceof AnnotationValue) {
                                    AnnotationValue ov = (AnnotationValue) o;
                                    if (message.equals(ov.getValue())) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

}
