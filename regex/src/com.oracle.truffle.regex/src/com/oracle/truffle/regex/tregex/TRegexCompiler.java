/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex;

import java.util.logging.Level;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.RegexExecNode;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecNode.LazyCaptureGroupRegexSearchNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.TRegexDFAExecutorNode;
import com.oracle.truffle.regex.tregex.nodes.nfa.TRegexBacktrackingNFAExecutorNode;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.Loggers;

public final class TRegexCompiler {

    /**
     * Try and compile the regular expression described in {@code source}.
     *
     * @throws RegexSyntaxException if the engine discovers a syntax error in the regular expression
     * @throws UnsupportedRegexException if the regular expression is not supported by the engine
     */
    @TruffleBoundary
    public static RegexObject compile(RegexLanguage language, RegexSource source) throws RegexSyntaxException {
        DebugUtil.Timer timer = shouldLogCompilationTime() ? new DebugUtil.Timer() : null;
        if (timer != null) {
            timer.start();
        }
        try {
            RegexObject regex = doCompile(language, source);
            logCompilationTime(source, timer, regex);
            Loggers.LOG_COMPILER_FALLBACK.finer(() -> "TRegex compiled: " + source);
            return regex;
        } catch (UnsupportedRegexException bailout) {
            logCompilationTime(source, timer, null);
            Loggers.LOG_BAILOUT_MESSAGES.fine(() -> bailout.getReason() + ": " + source);
            throw bailout;
        }
    }

    @TruffleBoundary
    private static RegexObject doCompile(RegexLanguage language, RegexSource source) throws RegexSyntaxException {
        TRegexCompilationRequest compReq = new TRegexCompilationRequest(language, source);
        RegexExecNode execNode = compReq.compile();
        return new RegexObject(execNode, source, compReq.getAst().getFlavorSpecificFlags(), compReq.getAst().getNumberOfCaptureGroups(), compReq.getNamedCaptureGroups());
    }

    @TruffleBoundary
    public static TRegexDFAExecutorNode compileEagerDFAExecutor(RegexLanguage language, RegexSource source) {
        TRegexDFAExecutorNode executor = new TRegexCompilationRequest(language, source).compileEagerDFAExecutor();
        if (executor.getCGTrackingCost() > TRegexOptions.TRegexMaxEagerCGDFACost) {
            throw new UnsupportedRegexException("Too much additional capture group tracking overhead");
        }
        return executor;
    }

    @TruffleBoundary
    public static LazyCaptureGroupRegexSearchNode compileLazyDFAExecutor(RegexLanguage language, NFA nfa, TRegexExecNode rootNode, boolean allowSimpleCG) {
        return new TRegexCompilationRequest(language, nfa).compileLazyDFAExecutor(rootNode, allowSimpleCG);
    }

    @TruffleBoundary
    public static TRegexBacktrackingNFAExecutorNode compileBacktrackingExecutor(RegexLanguage language, NFA nfa) {
        return new TRegexCompilationRequest(language, nfa).compileBacktrackingExecutor();
    }

    @TruffleBoundary
    private static boolean shouldLogCompilationTime() {
        return Loggers.LOG_TOTAL_COMPILATION_TIME.isLoggable(Level.FINE);
    }

    @TruffleBoundary
    private static void logCompilationTime(RegexSource regexSource, DebugUtil.Timer timer, RegexObject regex) {
        if (timer != null) {
            Loggers.LOG_TOTAL_COMPILATION_TIME.log(Level.FINE, "Total compilation time: {0}, matcher: {1}, regex: {2}", new Object[]{
                            timer.elapsedToString(),
                            regex == null ? "bailout" : regex.getLabel(),
                            DebugUtil.jsStringEscape(regexSource.toString())
            });
        }
    }
}
