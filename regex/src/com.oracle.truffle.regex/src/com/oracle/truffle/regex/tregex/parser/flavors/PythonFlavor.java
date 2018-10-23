/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * Furthermore, features not supported by TRegex in general are also not supported (e.g.
 * backreferences or variable-length lookbehind).
 * 
 * @see PythonREMode
 */
public final class PythonFlavor implements RegexFlavor {

    public static final PythonFlavor STR_INSTANCE = new PythonFlavor(PythonREMode.Str);
    public static final PythonFlavor BYTES_INSTANCE = new PythonFlavor(PythonREMode.Bytes);

    private final PythonREMode mode;

    private PythonFlavor(PythonREMode mode) {
        this.mode = mode;
    }

    @Override
    public RegexFlavorProcessor forRegex(RegexSource source) {
        return new PythonFlavorProcessor(source, mode);
    }

}
