/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.TRegexCompiler;

/**
 * {@link RegexEngineBuilder} is the entry point into using {@link RegexLanguage}. It is an
 * executable {@link TruffleObject} that configures and returns a {@link RegexEngine}. When
 * executing a {@link RegexEngineBuilder}, it accepts the following arguments:
 * <ol>
 * <li>{@link String} {@code options} (optional): a comma-separated list of options for the engine,
 * the currently supported options include:</li>
 * <ul>
 * <li>{@code U180EWhitespace}: the U+180E Unicode character (MONGOLIAN VOWEL SEPARATOR) is to be
 * treated as whitespace (Unicode versions before 6.3.0)</li>
 * <li>{@code RegressionTestMode}: all compilation is done eagerly, so as to detect errors early
 * during testing</li>
 * </ul>
 * <li>{@link RegexCompiler} {@code fallbackCompiler} (optional): an optional {@link RegexCompiler}
 * to be used when compilation by {@link TRegexCompiler}, the native compiler of
 * {@link RegexLanguage}, fails with an {@link UnsupportedRegexException}; {@code fallbackCompiler}
 * does not have to be an instance of {@link RegexCompiler}, it can also be a {@link TruffleObject}
 * with the same interop semantics as {@link RegexCompiler}</li>
 * </ol>
 */
public class RegexEngineBuilder implements RegexLanguageObject {

    private final RegexLanguage language;

    public RegexEngineBuilder(RegexLanguage language) {
        this.language = language;
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof RegexCompiler;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return RegexEngineBuilderMessageResolutionForeign.ACCESS;
    }

    @MessageResolution(receiverType = RegexEngineBuilder.class)
    static class RegexEngineBuilderMessageResolution {

        @Resolve(message = "EXECUTE")
        abstract static class RegexEngineBuilderExecuteNode extends Node {

            @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();

            public Object access(RegexEngineBuilder receiver, Object[] args) {
                if (args.length > 2) {
                    throw ArityException.raise(2, args.length);
                }
                RegexOptions options = RegexOptions.DEFAULT;
                if (args.length >= 1) {
                    if (!(args[0] instanceof String)) {
                        throw UnsupportedTypeException.raise(args);
                    }
                    options = RegexOptions.parse((String) args[0]);
                }
                TruffleObject fallbackCompiler = null;
                if (args.length >= 2) {
                    if (!(args[1] instanceof TruffleObject && ForeignAccess.sendIsExecutable(isExecutableNode, (TruffleObject) args[1]))) {
                        throw UnsupportedTypeException.raise(args);
                    }
                    fallbackCompiler = (TruffleObject) args[1];
                }
                return createRegexEngine(receiver.language, options, fallbackCompiler);
            }

            @TruffleBoundary
            private static RegexEngine createRegexEngine(RegexLanguage regexLanguage, RegexOptions options, TruffleObject fallbackCompiler) {
                if (fallbackCompiler != null) {
                    return new CachingRegexEngine(new RegexCompilerWithFallback(new TRegexCompiler(regexLanguage, options), fallbackCompiler),
                                    options.isRegressionTestMode());
                } else {
                    return new CachingRegexEngine(new TRegexCompiler(regexLanguage, options), options.isRegressionTestMode());
                }
            }
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class RegexEngineBuilderIsExecutableNode extends Node {

            @SuppressWarnings("unused")
            public boolean access(RegexEngineBuilder receiver) {
                return true;
            }
        }
    }
}
