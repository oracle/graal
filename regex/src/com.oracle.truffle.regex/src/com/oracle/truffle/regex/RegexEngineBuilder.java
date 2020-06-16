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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.regex.runtime.nodes.ToStringNode;
import com.oracle.truffle.regex.tregex.TRegexCompiler;

/**
 * {@link RegexEngineBuilder} is the entry point into using {@link RegexLanguage}. It is an
 * executable {@link TruffleObject} that configures and returns a {@link RegexEngine}. When
 * executing a {@link RegexEngineBuilder}, it accepts the following arguments:
 * <ol>
 * <li>{@link String} {@code options} (optional): a comma-separated list of options for the engine,
 * the currently supported options include:</li>
 * <ul>
 * <li>{@code Flavor}: the flavor of regular expressions to support
 * <ul>
 * <li>{@code ECMAScript} (default): regular expressions as provided by RegExp objects in ECMAScript
 * </li>
 * <li>{@code PythonStr}: regular expressions as provided by the {@code re} module in Python when
 * compiling {@code str}-based regular expressions</li>
 * <li>{@code PythonBytes}: regular expressions as provided by the {@code re} module in Python when
 * compiling {@code bytes}-based regular expressions</li>
 * </ul>
 * </li>
 * <li>{@code U180EWhitespace}: the U+180E Unicode character (MONGOLIAN VOWEL SEPARATOR) is to be
 * treated as whitespace (Unicode versions before 6.3.0)</li>
 * <li>{@code RegressionTestMode}: all compilation is done eagerly, so as to detect errors early
 * during testing</li>
 * <li>{@code DumpAutomata}: ASTs and automata are dumped in JSON, DOT (GraphViz) and LaTeX formats
 * </li>
 * <li>{@code StepExecution}: the execution of automata is traced and logged in JSON files</li>
 * <li>{@code AlwaysEager}: capture groups are always eagerly matched</li>
 * </ul>
 * <li>{@link RegexCompiler} {@code fallbackCompiler} (optional): an optional {@link RegexCompiler}
 * to be used when compilation by {@link TRegexCompiler}, the native compiler of
 * {@link RegexLanguage}, fails with an {@link UnsupportedRegexException}; {@code fallbackCompiler}
 * does not have to be an instance of {@link RegexCompiler}, it can also be a {@link TruffleObject}
 * with the same interop semantics as {@link RegexCompiler}</li>
 * </ol>
 */
@ExportLibrary(InteropLibrary.class)
public final class RegexEngineBuilder extends AbstractRegexObject {

    private final RegexLanguage language;

    public RegexEngineBuilder(RegexLanguage language) {
        this.language = language;
    }

    public static boolean isInstance(TruffleObject object) {
        return object instanceof RegexEngineBuilder;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    Object execute(Object[] args,
                    @Cached ToStringNode expectOptionsNode,
                    @CachedLibrary(limit = "1") InteropLibrary fallbackCompilers) throws ArityException, UnsupportedTypeException {
        if (args.length > 2) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw ArityException.create(2, args.length);
        }
        RegexOptions options = RegexOptions.DEFAULT;
        if (args.length >= 1) {
            options = RegexOptions.parse(expectOptionsNode.execute(args[0]));
        }
        TruffleObject fallbackCompiler = null;
        if (args.length >= 2) {
            if (!(fallbackCompilers.isExecutable(args[1]))) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(args);
            }
            fallbackCompiler = (TruffleObject) args[1];
        }
        return createRegexEngine(language, options, fallbackCompiler);
    }

    @TruffleBoundary
    private static RegexEngine createRegexEngine(RegexLanguage regexLanguage, RegexOptions options, TruffleObject fallbackCompiler) {
        RegexCompiler compiler = createRegexCompiler(regexLanguage, options, fallbackCompiler);
        return options.isRegressionTestMode() ? new RegexEngine(compiler, options) : new CachingRegexEngine(compiler, options);
    }

    private static RegexCompiler createRegexCompiler(RegexLanguage regexLanguage, RegexOptions options, TruffleObject fallbackCompiler) {
        if (fallbackCompiler != null) {
            return new RegexCompilerWithFallback(new TRegexCompiler(regexLanguage, options), fallbackCompiler);
        } else {
            return new TRegexCompiler(regexLanguage, options);
        }
    }

}
