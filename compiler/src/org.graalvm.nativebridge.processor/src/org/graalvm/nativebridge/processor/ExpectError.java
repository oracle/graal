/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ExpectError {

    private ExpectError() {
    }

    static boolean assertNoErrorExpected(AbstractBridgeParser parser, Element element) {
        DeclaredType expectErrorAnnotation = parser.typeCache.expectError;
        if (expectErrorAnnotation != null) {
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                if (parser.types.isSameType(mirror.getAnnotationType(), expectErrorAnnotation)) {
                    parser.processor.env().getMessager().printMessage(Diagnostic.Kind.ERROR, "Expected an error, but none found!", element);
                    return false;
                }
            }
        }
        return true;
    }

    static boolean isExpectedError(AbstractBridgeParser parser, Element element, String actualText) {
        List<String> expectedErrors = getExpectedErrors(parser, element);
        for (String expectedText : expectedErrors) {
            String newExpectedText = expectedText.replaceAll("%n", System.lineSeparator());
            if (newExpectedText.endsWith("%") && actualText.startsWith(newExpectedText.substring(0, newExpectedText.length() - 1))) {
                return true;
            } else if (actualText.equals(newExpectedText)) {
                return true;
            }
        }
        return false;
    }

    static List<String> getExpectedErrors(AbstractBridgeParser parser, Element element) {
        if (element == null) {
            return Collections.emptyList();
        }
        List<String> expectedErrors = new ArrayList<>();
        if (parser.typeCache.expectError != null) {
            for (AnnotationMirror am : element.getAnnotationMirrors()) {
                if (parser.types.isSameType(am.getAnnotationType(), parser.typeCache.expectError)) {
                    List<?> values = (List<?>) AbstractBridgeParser.getAnnotationValue(am, "value");
                    for (Object value : values) {
                        if (value instanceof AnnotationValue) {
                            expectedErrors.add((String) ((AnnotationValue) value).getValue());
                        }
                    }
                }
            }
        }
        return expectedErrors;
    }
}
