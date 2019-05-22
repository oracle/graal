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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.logging.Level;

import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_BAILOUT_MESSAGES;
import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_COMPILER_FALLBACK;
import static com.oracle.truffle.regex.tregex.util.DebugUtil.LOG_TOTAL_COMPILATION_TIME;

public class RegexCompilerWithFallback implements RegexCompiler {

    private final RegexCompiler mainCompiler;
    private final RegexCompiler fallbackCompiler;

    public RegexCompilerWithFallback(RegexCompiler mainCompiler, TruffleObject fallbackCompiler) {
        this.mainCompiler = mainCompiler;
        this.fallbackCompiler = ForeignRegexCompiler.importRegexCompiler(fallbackCompiler);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public Object compile(RegexSource regexSource) throws RegexSyntaxException, UnsupportedRegexException {
        Object regex;
        long elapsedTimeMain = 0;
        long elapsedTimeFallback = 0;
        DebugUtil.Timer timer = null;
        final boolean shouldLog = shouldLogCompilationTime();
        if (shouldLog) {
            timer = new DebugUtil.Timer();
        }
        try {
            if (shouldLog) {
                timer.start();
            }
            regex = mainCompiler.compile(regexSource);
            if (shouldLog) {
                elapsedTimeMain = timer.getElapsed();
            }
            LOG_COMPILER_FALLBACK.finer(() -> "Primary compiler used: " + regexSource);
        } catch (UnsupportedRegexException mainBailout) {
            LOG_BAILOUT_MESSAGES.fine(() -> mainBailout.getReason() + ": " + regexSource);
            try {
                if (shouldLog) {
                    timer.start();
                }
                regex = fallbackCompiler.compile(regexSource);
                if (shouldLog) {
                    elapsedTimeFallback = timer.getElapsed();
                }
                LOG_COMPILER_FALLBACK.fine(() -> String.format("Secondary compiler used (primary bailout due to '%s'): %s", mainBailout.getReason(), regexSource));
            } catch (UnsupportedRegexException fallbackBailout) {
                LOG_COMPILER_FALLBACK.fine(() -> String.format("No compiler handled following regex (primary bailout: '%s'; secondary bailout: '%s'): %s", mainBailout.getReason(),
                                fallbackBailout.getReason(), regexSource));
                String bailoutReasons = String.format("%s; %s", mainBailout.getReason(), fallbackBailout.getReason());
                throw new UnsupportedRegexException(bailoutReasons, regexSource);
            }
        }
        if (shouldLog) {
            logCompilationTime(regexSource, elapsedTimeMain, elapsedTimeFallback);
        }
        return regex;
    }

    private static boolean shouldLogCompilationTime() {
        return LOG_TOTAL_COMPILATION_TIME.isLoggable(Level.FINE);
    }

    private static void logCompilationTime(RegexSource regexSource, long elapsedTimeMain, long elapsedTimeFallback) {
        LOG_TOTAL_COMPILATION_TIME.log(Level.FINE, "{0}, {1}, {2}, {3}", new Object[]{
                        DebugUtil.Timer.elapsedToString(elapsedTimeMain + elapsedTimeFallback),
                        DebugUtil.Timer.elapsedToString(elapsedTimeMain),
                        DebugUtil.Timer.elapsedToString(elapsedTimeFallback),
                        DebugUtil.jsStringEscape(regexSource.toString())
        });
    }
}
