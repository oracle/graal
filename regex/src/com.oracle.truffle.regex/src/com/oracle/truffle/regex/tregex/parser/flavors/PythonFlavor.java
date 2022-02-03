/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.RegexSource;

/**
 * An implementation of the Python regex flavor. Technically, this class provides an implementation
 * for two regex flavors: 'str' regexes, which result from compiling string patterns, and 'bytes'
 * patterns, which result from compiling binary (byte buffer) patterns.
 *
 * This implementation supports translating all Python regular expressions to ECMAScript regular
 * expressions with the exception of the following features:
 * <ul>
 * <li>case insensitive backreferences: Python regular expressions use a different definition of
 * case folding and they also allow mixing case sensitive and case insensitive backreferences in the
 * same regular expression.</li>
 * <li>locale-sensitive case folding, word boundary assertions and character classes: When a regular
 * expression is compiled with the {@code re.LOCALE} flag, some of its elements should depend on the
 * locale set during matching time. This is not compatible with compiling regular expressions
 * ahead-of-time into automata.</li>
 * <li>conditional backreferences, i.e. {@code (?(groupId)ifPart|elsePart)}: These do not have a
 * direct counterpart in ECMAScript. It should be theoretically feasible to translate Python regular
 * expressions using these into ECMAScript regular expressions, however the translation would have
 * to use much more complex global rewriting rules than the current approach.</li>
 * </ul>
 *
 * @see PythonREMode
 */
public final class PythonFlavor extends RegexFlavor {

    public static final PythonFlavor INSTANCE = new PythonFlavor(PythonREMode.None);

    public static final PythonFlavor STR_INSTANCE = new PythonFlavor(PythonREMode.Str);
    public static final PythonFlavor BYTES_INSTANCE = new PythonFlavor(PythonREMode.Bytes);

    private final PythonREMode mode;

    private PythonFlavor(PythonREMode mode) {
        super(BACKREFERENCES_TO_UNMATCHED_GROUPS_FAIL | NESTED_CAPTURE_GROUPS_KEPT_ON_LOOP_REENTRY | FAILING_EMPTY_CHECKS_DONT_BACKTRACK | USES_LAST_GROUP_RESULT_FIELD |
                        LOOKBEHINDS_RUN_LEFT_TO_RIGHT);
        this.mode = mode;
    }

    @Override
    public RegexFlavorProcessor forRegex(RegexSource source) {
        return new PythonFlavorProcessor(source, mode);
    }

}
