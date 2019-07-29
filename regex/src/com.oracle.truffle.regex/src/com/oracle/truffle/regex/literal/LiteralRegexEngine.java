/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.literal;

import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.EmptyEndsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.EmptyEquals;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.EmptyIndexOf;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.EmptyStartsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.EndsWith;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.Equals;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.IndexOfChar;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.IndexOfString;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.RegionMatches;
import com.oracle.truffle.regex.literal.LiteralRegexExecRootNode.StartsWith;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;

/**
 * This regex engine is designed for very simple cases, where the regular expression can be directly
 * translated to common string operations. It will map expressions to simple index checks (
 * {@link EmptyStartsWith}, {@link EmptyEndsWith}, {@link EmptyIndexOf}) or to the following methods
 * of {@link String} (or equivalent nodes in {@link com.oracle.truffle.regex.tregex.nodes.input})
 * whenever possible:
 * <ul>
 * <li>{@link String#isEmpty()}: {@link EmptyEquals}</li>
 * <li>{@link String#indexOf(int)}: {@link IndexOfChar}</li>
 * <li>{@link String#startsWith(String)}: {@link StartsWith}</li>
 * <li>{@link String#endsWith(String)}: {@link EndsWith}</li>
 * <li>{@link String#equals(Object)}: {@link Equals}</li>
 * <li>{@link String#regionMatches(int, String, int, int)}: {@link RegionMatches}</li>
 * </ul>
 */
public final class LiteralRegexEngine {

    public static LiteralRegexExecRootNode createNode(RegexLanguage language, RegexAST ast) {
        if (ast.isLiteralString()) {
            return createLiteralNode(language, ast);
        } else {
            return null;
        }
    }

    private static LiteralRegexExecRootNode createLiteralNode(RegexLanguage language, RegexAST ast) {
        PreCalcResultVisitor preCalcResultVisitor = PreCalcResultVisitor.run(ast, true);
        boolean caret = ast.getRoot().startsWithCaret();
        boolean dollar = ast.getRoot().endsWithDollar();
        if (preCalcResultVisitor.getLiteral().length() == 0) {
            if (caret) {
                if (dollar) {
                    return new EmptyEquals(language, ast, preCalcResultVisitor);
                }
                return new EmptyStartsWith(language, ast, preCalcResultVisitor);
            }
            if (dollar) {
                return new EmptyEndsWith(language, ast, preCalcResultVisitor);
            }
            return new EmptyIndexOf(language, ast, preCalcResultVisitor);
        }
        if (caret) {
            if (dollar) {
                return new Equals(language, ast, preCalcResultVisitor);
            }
            return new StartsWith(language, ast, preCalcResultVisitor);
        }
        if (dollar) {
            return new EndsWith(language, ast, preCalcResultVisitor);
        }
        if (ast.getFlags().isSticky()) {
            return new RegionMatches(language, ast, preCalcResultVisitor);
        }
        if (preCalcResultVisitor.getLiteral().length() == 1) {
            return new IndexOfChar(language, ast, preCalcResultVisitor);
        }
        if (preCalcResultVisitor.getLiteral().length() <= 64) {
            return new IndexOfString(language, ast, preCalcResultVisitor);
        }
        return null;
    }
}
