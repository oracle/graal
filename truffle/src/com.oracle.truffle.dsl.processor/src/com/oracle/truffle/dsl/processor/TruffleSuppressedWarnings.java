/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import com.oracle.truffle.dsl.processor.java.ElementUtils;

public final class TruffleSuppressedWarnings {

    private TruffleSuppressedWarnings() {
    }

    public static final String ALL = "all";
    public static final String TRUFFLE = "truffle";
    public static final String STATIC_METHOD = "truffle-static-method";
    public static final String LIMIT = "truffle-limit";
    public static final String UNUSED = "truffle-unused";
    public static final String NEVERDEFAULT = "truffle-neverdefault";
    public static final String INLINING_RECOMMENDATION = "truffle-inlining";
    public static final String SHARING_RECOMMENDATION = "truffle-sharing";
    public static final String ABSTRACT_LIBRARY_EXPORT = "truffle-abstract-export";
    public static final String ASSUMPTION = "truffle-assumption";
    public static final String GUARD = "truffle-guard";
    public static final String DEPRECATION = "deprecation";
    public static final String INTERPRETED_PERFORMANCE = "truffle-interpreted-performance";
    public static final List<String> ALL_KEYS = List.of(ALL, TRUFFLE, STATIC_METHOD, LIMIT, UNUSED, NEVERDEFAULT, INLINING_RECOMMENDATION, SHARING_RECOMMENDATION, ABSTRACT_LIBRARY_EXPORT,
                    DEPRECATION, INTERPRETED_PERFORMANCE);

    public static Set<String> getWarnings(Element element) {
        AnnotationMirror currentWarnings = ElementUtils.findAnnotationMirror(element, SuppressWarnings.class);
        Set<String> warnings = null;
        if (currentWarnings != null) {
            List<String> currentValues = ElementUtils.getAnnotationValueList(String.class, currentWarnings, "value");
            if (currentValues != null && !currentValues.isEmpty()) {
                if (warnings == null) {
                    warnings = new LinkedHashSet<>();
                }
                warnings.addAll(currentValues);
            }
        }
        if (element.getKind() == ElementKind.PACKAGE) {
            TruffleTypes types = ProcessorContext.getInstance().getTypes();
            AnnotationMirror packageWarnings = ElementUtils.findAnnotationMirror(element,
                            types.SuppressPackageWarnings);
            if (packageWarnings != null) {
                List<String> currentValues = ElementUtils.getAnnotationValueList(String.class, packageWarnings,
                                "value");
                if (currentValues != null && !currentValues.isEmpty()) {
                    if (warnings == null) {
                        warnings = new LinkedHashSet<>();
                    }
                    warnings.addAll(currentValues);
                }
            }
        }

        return warnings == null ? Collections.emptySet() : warnings;
    }

    public static boolean isSuppressed(Element element, String... warningKind) {
        Element e = element;
        do {
            Set<String> warnings = getWarnings(e);
            if (warnings.contains(ALL) || warnings.contains(TRUFFLE) || //
                            warnings.stream().anyMatch((s) -> (Arrays.asList(warningKind).contains(s)))) {
                return true;
            }
            e = e.getEnclosingElement();
        } while (e != null);
        return false;
    }

}
