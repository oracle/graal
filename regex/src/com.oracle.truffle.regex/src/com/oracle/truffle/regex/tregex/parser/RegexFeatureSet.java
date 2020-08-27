/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser;

import java.util.Optional;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.UnsupportedRegexException;

/**
 * A predicate that describes the set of regular expressions supported by the regex compilers being
 * used.
 */
@FunctionalInterface
public interface RegexFeatureSet {

    /**
     * Tests whether or not a regular expression is supported. Returns a descriptive error message
     * if it is not.
     *
     * @param source the regular expression whose features are in question
     * @param features a record of features detected in the expression by the parser that validated
     *            its well-formedness
     * @return
     *         <ul>
     *         <li>{@code Optional.empty()} if the regex is supported</li>
     *         <li>{@code Optional.of(err)} if the regex is not supported; {@code err} is the error
     *         message</li>
     *         </ul>
     */
    Optional<String> testSupport(RegexSource source, RegexFeatures features);

    @TruffleBoundary
    default void checkSupport(RegexSource source, RegexFeatures features) throws UnsupportedRegexException {
        Optional<String> maybeError = testSupport(source, features);
        if (maybeError.isPresent()) {
            throw new UnsupportedRegexException(maybeError.get(), source);
        }
    }

    RegexFeatureSet DEFAULT = (RegexSource source, RegexFeatures features) -> Optional.empty();
}
