/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.util.Map;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;

/**
 * Allows the parsing and translating of a foreign regular expression into an ECMAScript regular
 * expression.
 * <p>
 * After either {@link #validate} or {@link #toECMAScriptRegex} was called,
 * {@link #getNamedCaptureGroups}, {@link #getFlags} and {@link #isUnicodePattern} can be called to
 * extract extra information obtained during the parse.
 */
public interface RegexFlavorProcessor {

    /**
     * Runs the parser without trying to find an equivalent ECMAScript regex. Useful for early error
     * detection.
     * 
     * @throws RegexSyntaxException when the pattern or the flags are not well-formed
     */
    void validate() throws RegexSyntaxException;

    /**
     * Runs the parser and emits an equivalent ECMAScript regex.
     * 
     * @return an ECMAScript {@link RegexSource} compatible to the input one
     * @throws RegexSyntaxException when the pattern or the flags are not well-formed
     * @throws UnsupportedRegexException when the pattern cannot be translated to an equivalent
     *             ECMAScript pattern
     */
    RegexSource toECMAScriptRegex() throws RegexSyntaxException, UnsupportedRegexException;

    /**
     * Returns the number of capture groups contained in the expression, including capture group 0.
     */
    int getNumberOfCaptureGroups();

    /**
     * Returns a map from the names of capture groups to their indices. If the regular expression
     * had no named capture groups, returns null.
     */
    Map<String, Integer> getNamedCaptureGroups();

    /**
     * Returns a {@link TruffleObject} representing the compilation flags which were set for the
     * regular expression. The returned object responds to 'READ' messages on names which correspond
     * to the names of the flags as used in the language from which the flavor originates.
     */
    TruffleObject getFlags();

    /**
     * Returns {@code true} if the generated ECMAScript pattern is a Unicode pattern (matches
     * Unicode code points instead of UTF-16 code units).
     */
    boolean isUnicodePattern();
}
