/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

/**
 * {@link RegexCompiler} is an executable {@link TruffleObject} that compiles regular expressions
 * into {@link CompiledRegexObject}s or compatible {@link TruffleObject}s. It takes the following
 * arguments:
 * <ol>
 * <li>{@link String} {@code pattern}: the source of the regular expression to be compiled</li>
 * <li>{@link String} {@code flags} (optional): a textual representation of the flags to be passed
 * to the compiler (one letter per flag), see {@link RegexFlags} for the supported flags</li>
 * </ol>
 * Executing the {@link RegexCompiler} can also lead to the following exceptions:
 * <ul>
 * <li>{@link RegexSyntaxException}: if the input regular expression is malformed</li>
 * <li>{@link UnsupportedRegexException}: if the input regular expression cannot be compiled by this
 * compiler</li>
 * </ul>
 * <p>
 * {@link RegexCompiler}s are very similar to {@link RegexEngine}s. {@link RegexEngine}s produce
 * {@link RegexObject}s, which contain metadata about the compiled regular expression and lazy
 * access to a {@link CompiledRegexObject}s. When the {@link CompiledRegexObject} of a
 * {@link RegexObject} is needed, the {@link RegexEngine} delegates to a {@link RegexCompiler} to do
 * the actual work. A {@link RegexCompiler} then (eagerly) compiles its input and returns a
 * {@link CompiledRegexObject}. {@link RegexCompiler}s exist because they are easier to compose
 * {@link RegexCompiler}s using combinators such as {@link RegexCompilerWithFallback} and because
 * they are easier to provide by third-party RegExp engines. {@link RegexEngine}s exist because they
 * provide features that are desired by users of {@link RegexLanguage} (e.g. lazy compilation).
 */
public abstract class RegexCompiler implements RegexLanguageObject {

    /**
     * Uses the compiler to try and compile the regular expression described in {@code source}.
     * 
     * @return a {@link CompiledRegexObject} or a compatible {@link TruffleObject}
     * @throws RegexSyntaxException if the engine discovers a syntax error in the regular expression
     * @throws UnsupportedRegexException if the regular expression is not supported by the engine
     */
    public abstract TruffleObject compile(RegexSource source) throws RegexSyntaxException, UnsupportedRegexException;

    public static boolean isInstance(TruffleObject object) {
        return object instanceof RegexCompiler;
    }

    @Override
    public ForeignAccess getForeignAccess() {
        return RegexCompilerMessageResolutionForeign.ACCESS;
    }

    @MessageResolution(receiverType = RegexCompiler.class)
    static class RegexCompilerMessageResolution {

        @Resolve(message = "EXECUTE")
        abstract static class RegexCompilerExecuteNode extends Node {

            public Object access(RegexCompiler receiver, Object[] args) {
                if (!(args.length == 1 || args.length == 2)) {
                    throw ArityException.raise(2, args.length);
                }
                if (!(args[0] instanceof String)) {
                    throw UnsupportedTypeException.raise(args);
                }
                String pattern = (String) args[0];
                String flags = "";
                if (args.length == 2) {
                    if (!(args[1] instanceof String)) {
                        throw UnsupportedTypeException.raise(args);
                    }
                    flags = (String) args[1];
                }
                RegexSource regexSource = new RegexSource(pattern, RegexFlags.parseFlags(flags));
                return receiver.compile(regexSource);
            }
        }

        @Resolve(message = "IS_EXECUTABLE")
        abstract static class RegexCompilerIsExecutableNode extends Node {

            @SuppressWarnings("unused")
            public boolean access(RegexCompiler receiver) {
                return true;
            }
        }
    }
}
