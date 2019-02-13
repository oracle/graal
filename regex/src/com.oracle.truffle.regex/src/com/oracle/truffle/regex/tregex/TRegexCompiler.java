/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

/*
 * current status:
 *
 * truffle.regex.tregex can handle quantifiers, character classes, alternations,
 * positive look-aheads, and positive look-behinds of fixed length.
 * Counted repetitions are implemented by transforming them to alternations
 * (e.g. a{2,4} => aa|aaa|aaaa).
 *
 * basic structure of truffle.regex.tregex:
 *
 * tregex parses a regular expression using the custom RegexParser and transforms it to a non-deterministic finite
 * automaton (NFA) using the data structures found in tregex.nfa.
 *
 * The NFA is compiled to a DFA (deterministic finite automaton) during pattern matching. Each DFA stateSet is a
 * set of NFA states, which is stored as a BitSet where each bit corresponds to a slot in the NFA array.
 *
 */

import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.RegexCompiler;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.tregex.nodes.TRegexDFAExecutorNode;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class TRegexCompiler extends RegexCompiler {

    private final RegexLanguage language;
    private final RegexOptions options;

    public TRegexCompiler(RegexLanguage language, RegexOptions options) {
        this.language = language;
        this.options = options;
    }

    public RegexLanguage getLanguage() {
        return language;
    }

    public RegexOptions getOptions() {
        return options;
    }

    @TruffleBoundary
    @Override
    public TruffleObject compile(RegexSource source) throws RegexSyntaxException {
        return new TRegexCompilationRequest(this, source).compile();
    }

    @TruffleBoundary
    public TRegexDFAExecutorNode compileEagerDFAExecutor(RegexSource source) {
        return new TRegexCompilationRequest(this, source).compileEagerDFAExecutor();
    }
}
