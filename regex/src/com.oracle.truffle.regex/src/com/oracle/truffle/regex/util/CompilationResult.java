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
package com.oracle.truffle.regex.util;

import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.UnsupportedRegexException;

import java.util.function.Supplier;

/**
 * Trying to parse and compile a regular expression can produce one of three results. This class
 * encodes the sum of these three possibilities.
 *
 * <ul>
 * <li>the regular expression is successfully compiled: compiledRegex is not null</li>
 * <li>there is a syntax error in the regular expression: syntaxException is not null</li>
 * <li>the regular expression is not supported by the engine: unsupportedRegexException is not null
 * </li>
 * </ul>
 */
public final class CompilationResult<T> {

    private final T compiledRegex;
    private final RegexSyntaxException syntaxException;
    private final UnsupportedRegexException unsupportedRegexException;

    private CompilationResult(T compiledRegexObject) {
        this.compiledRegex = compiledRegexObject;
        this.syntaxException = null;
        this.unsupportedRegexException = null;
    }

    private CompilationResult(RegexSyntaxException syntaxException) {
        this.compiledRegex = null;
        this.syntaxException = syntaxException;
        this.unsupportedRegexException = null;
    }

    private CompilationResult(UnsupportedRegexException unsupportedRegexException) {
        this.compiledRegex = null;
        this.syntaxException = null;
        this.unsupportedRegexException = unsupportedRegexException;
    }

    public static <T> CompilationResult<T> pack(Supplier<T> compilationTask) {
        try {
            T result = compilationTask.get();
            return new CompilationResult<>(result);
        } catch (RegexSyntaxException e) {
            return new CompilationResult<>(e);
        } catch (UnsupportedRegexException e) {
            return new CompilationResult<>(e);
        }
    }

    public T unpack() throws RegexSyntaxException, UnsupportedRegexException {
        if (compiledRegex != null) {
            assert syntaxException == null;
            assert unsupportedRegexException == null;
            return compiledRegex;
        } else if (syntaxException != null) {
            assert compiledRegex == null;
            assert unsupportedRegexException == null;
            throw syntaxException;
        } else {
            assert compiledRegex == null;
            assert syntaxException == null;
            assert unsupportedRegexException != null;
            throw unsupportedRegexException;
        }
    }
}
