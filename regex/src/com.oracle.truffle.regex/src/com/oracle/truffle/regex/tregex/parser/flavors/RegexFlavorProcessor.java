/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;

import java.util.Map;

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
