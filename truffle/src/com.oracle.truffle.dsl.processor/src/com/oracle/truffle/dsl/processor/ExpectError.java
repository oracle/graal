/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

public class ExpectError {

    private static final String[] EXPECT_ERROR_TYPES = new String[]{TruffleTypes.EXPECT_ERROR_CLASS_NAME1, TruffleTypes.EXPECT_ERROR_CLASS_NAME2};

    public static void assertNoErrorExpected(ProcessingEnvironment processingEnv, Element element) {
        for (String errorType : EXPECT_ERROR_TYPES) {
            assertNoErrorExpectedImpl(processingEnv, element, ElementUtils.getTypeElement(processingEnv, errorType));
        }
    }

    private static void assertNoErrorExpectedImpl(ProcessingEnvironment processingEnv, Element element, TypeElement eee) {
        if (eee != null) {
            for (AnnotationMirror am : element.getAnnotationMirrors()) {
                if (am.getAnnotationType().asElement().equals(eee)) {
                    processingEnv.getMessager().printMessage(Kind.ERROR, "Expected an error, but none found!", element);
                }
            }
        }
    }

    public static boolean isExpectedError(ProcessingEnvironment processingEnv, Element element, String actualText) {
        List<String> expectedErrors = getExpectedErrors(processingEnv, element);
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

    public static List<String> getExpectedErrors(ProcessingEnvironment processingEnv, Element element) {
        if (element == null) {
            return Collections.emptyList();
        }
        List<String> expectedErrors = new ArrayList<>();
        for (String errorType : EXPECT_ERROR_TYPES) {
            collectExpectedErrors(expectedErrors, element, processingEnv.getElementUtils().getTypeElement(errorType));
        }
        return expectedErrors;
    }

    private static void collectExpectedErrors(List<String> expectedErrors, Element element, TypeElement eee) {
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
                                    expectedErrors.add((String) ov.getValue());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
