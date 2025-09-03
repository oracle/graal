/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

    static void assertNoErrorExpected(Iterable<? extends AbstractBridgeGenerator> generators) {
        DeclaredType expectErrorAnnotation = generators.iterator().next().getTypeCache().expectError;
        if (expectErrorAnnotation != null) {
            for (AbstractBridgeGenerator generator : generators) {
                AbstractBridgeParser parser = generator.getParser();
                for (Element element : generator.getDefinition().getVerifiedElements()) {
                    for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                        if (parser.types.isSameType(mirror.getAnnotationType(), expectErrorAnnotation)) {
                            parser.processor.env().getMessager().printMessage(Diagnostic.Kind.ERROR, "Expected an error, but none found!", element);
                        }
                    }
                }
            }
        }
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
        if (parser.getTypeCache().expectError != null) {
            for (AnnotationMirror am : element.getAnnotationMirrors()) {
                if (parser.types.isSameType(am.getAnnotationType(), parser.getTypeCache().expectError)) {
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
